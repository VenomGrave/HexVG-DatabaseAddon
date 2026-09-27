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
 * check [db] table %string%
 *
 * Blocks until the check finishes (max 5 s), so a following
 * {@code if db table "x" exists} condition sees the fresh result.
 */
public class EffectCheckTable extends Effect {

    static {
        Skript.registerEffect(EffectCheckTable.class,
                "check [db] table %string%");
    }

    private Expression<String> tableExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        tableExpr = (Expression<String>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String table = tableExpr.getSingle(event);
        if (!SqlIdentifiers.isValid(table)) {
            Skript.warning("[HexVG-DatabaseAddon] Invalid table name for check: " + table);
            return;
        }
        HexVGAddon addon = HexVGAddon.getInstance();
        if (addon == null || addon.getQueryExecutor() == null) return;

        QueryExecutor executor = addon.getQueryExecutor();
        try {
            TableExistsCache.put(table, executor.callBlocking(() -> executor.tableExists(table),
                    QueryExecutor.EFFECT_TIMEOUT_MS));
        } catch (Exception e) {
            TableExistsCache.invalidate(table);
            Skript.warning("[HexVG-DatabaseAddon] Table check failed for '" + table + "': " + e.getMessage());
        }
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "check db table " + tableExpr.toString(event, debug);
    }
}
