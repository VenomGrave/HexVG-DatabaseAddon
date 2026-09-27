package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import com.venomgrave.hexvg.database.database.QueryExecutor;
import com.venomgrave.hexvg.database.util.SqlIdentifiers;
import com.venomgrave.hexvg.database.util.TableExistsCache;
import org.bukkit.event.Event;

/**
 * db ensure table %string% with query %string%
 *
 * Checks if the table exists and creates it if it doesn't.
 * Blocks until the operation completes (max 10 s) — no "wait X ticks" needed.
 *
 * Example:
 *   db ensure table "players" with query "CREATE TABLE IF NOT EXISTS players (uuid VARCHAR(36) PRIMARY KEY, coins INT DEFAULT 0)"
 */
public class EffectCreateTable extends Effect {

    private static final long TIMEOUT_MS = 10_000;

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
        if (!SqlIdentifiers.isValid(table)) {
            Skript.warning("[HexVG-DatabaseAddon] Invalid table name for ensure table: " + table);
            return;
        }
        if (query == null || query.trim().isEmpty()) {
            Skript.warning("[HexVG-DatabaseAddon] Missing CREATE query for table: " + table);
            return;
        }
        HexVGAddon addon = HexVGAddon.getInstance();
        if (addon == null || addon.getQueryExecutor() == null) return;

        QueryExecutor executor = addon.getQueryExecutor();
        try {
            executor.callBlocking(() -> executor.ensureTable(table, query), TIMEOUT_MS);
            TableExistsCache.put(table, true);
        } catch (Exception e) {
            TableExistsCache.invalidate(table);
            Skript.warning("[HexVG-DatabaseAddon] Failed to ensure table '" + table + "': " + e.getMessage());
        }
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db ensure table " + tableExpr.toString(event, debug)
                + " with query " + queryExpr.toString(event, debug);
    }
}
