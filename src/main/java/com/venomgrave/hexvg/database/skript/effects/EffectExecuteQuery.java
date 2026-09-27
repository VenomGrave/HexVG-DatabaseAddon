package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;

public class EffectExecuteQuery extends Effect {

    static {
        Skript.registerEffect(EffectExecuteQuery.class,
                "execute [db] query %string% [with [values] %-strings%]");
    }

    private Expression<String> sqlExpr;
    private Expression<String> paramsExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        sqlExpr = (Expression<String>) exprs[0];
        paramsExpr = exprs[1] != null ? (Expression<String>) exprs[1] : null;
        return true;
    }

    @Override
    protected void execute(Event event) {
        String sql = sqlExpr.getSingle(event);
        if (sql == null || sql.trim().isEmpty()) {
            QueryEffects.fail(event, "[HexVG-DatabaseAddon] Query is empty.");
            return;
        }

        String[] params = paramsExpr != null ? paramsExpr.getAll(event) : new String[0];

        QueryEffects.runAndStore(event, sql, params, "Query", " | SQL: " + sql);
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "execute db query " + sqlExpr.toString(event, debug);
    }
}
