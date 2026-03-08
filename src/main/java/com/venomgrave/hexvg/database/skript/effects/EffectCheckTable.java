package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import com.venomgrave.hexvg.database.util.TableExistsCache;
import org.bukkit.event.Event;

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
        if (table == null || !table.matches("[a-zA-Z0-9_]+")) {
            Skript.warning("[HexVG-DatabaseAddon] Invalid table name for check: " + table);
            return;
        }

        HexVGAddon.getInstance().getQueryExecutor().tableExistsAsync(table,
                (exists, error) -> {
                    if (error != null) {
                        Skript.warning("[HexVG-DatabaseAddon] Table check failed for '"
                                + table + "': " + error.getMessage());
                        return;
                    }
                    TableExistsCache.put(table, exists);
                });
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "check db table " + tableExpr.toString(event, debug);
    }
}
