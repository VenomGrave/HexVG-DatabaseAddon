package com.venomgrave.hexvg.database.skript.conditions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import com.venomgrave.hexvg.database.util.TableExistsCache;
import org.bukkit.event.Event;

public class CondTableExists extends Condition {

    static {
        Skript.registerCondition(CondTableExists.class,
                "[db] table %string% exists",
                "[db] table %string% doesn't exist");
    }

    private Expression<String> tableExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        tableExpr = (Expression<String>) exprs[0];
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        String table = tableExpr.getSingle(event);
        if (table == null || !table.matches("[a-zA-Z0-9_]+")) return isNegated();

        Boolean cached = TableExistsCache.get(table);
        if (cached == null) {
            Skript.warning("[HexVG-DatabaseAddon] Table '" + table + "' not yet checked. "
                    + "Use 'check db table' effect before this condition.");
            return isNegated();
        }

        return isNegated() != cached;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db table " + tableExpr.toString(event, debug) + (isNegated() ? " doesn't exist" : " exists");
    }
}
