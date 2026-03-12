package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import com.venomgrave.hexvg.database.util.TableExistsCache;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * db ensure table %string% with query %string%
 *
 * Checks if the table exists and creates it if it doesn't.
 * Blocks until the operation completes — no "wait X ticks" needed.
 *
 * Example:
 *   db ensure table "players" with query "CREATE TABLE IF NOT EXISTS players (uuid VARCHAR(36) PRIMARY KEY, coins INT DEFAULT 0)"
 */
public class EffectCreateTable extends Effect {

    static {
        Skript.registerEffect(EffectCreateTable.class,
                "db ensure table %string% with [query] %string%");
    }

    private Expression<String> tableExpr;
    private Expression<String> queryExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        tableExpr = (Expression<String>) exprs[0];
        queryExpr = (Expression<String>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String table = tableExpr.getSingle(event);
        String query = queryExpr.getSingle(event);
        if (table == null || query == null) return;

        final JavaPlugin plugin = HexVGAddon.getInstance();
        CountDownLatch latch = new CountDownLatch(1);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = HexVGAddon.getInstance().getDatabaseManager().getConnection()) {
                // Check if table already exists
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
                    if (rs.next()) {
                        TableExistsCache.put(table, true);
                        return; // already exists
                    }
                }
                // Create the table
                try (var stmt = conn.prepareStatement(query)) {
                    stmt.executeUpdate();
                }
                TableExistsCache.put(table, true);
                HexVGAddon.getInstance().getDebugLogger().log("[CREATE TABLE] Created table: " + table);
            } catch (Exception e) {
                HexVGAddon.getInstance().getDebugLogger().log("[CREATE TABLE] Failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db ensure table " + tableExpr.toString(event, debug)
                + " with query " + queryExpr.toString(event, debug);
    }
}
