package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.util.SqlIdentifiers;
import org.bukkit.event.Event;

public class EffectUpdate extends Effect {

    static {
        Skript.registerEffect(EffectUpdate.class,
                "db update [table] %string% set %string% to %string% where %string% = %string%");
    }

    private Expression<String> tableExpr;
    private Expression<String> setColumnExpr;
    private Expression<String> setValueExpr;
    private Expression<String> whereColumnExpr;
    private Expression<String> whereValueExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        tableExpr = (Expression<String>) exprs[0];
        setColumnExpr = (Expression<String>) exprs[1];
        setValueExpr = (Expression<String>) exprs[2];
        whereColumnExpr = (Expression<String>) exprs[3];
        whereValueExpr = (Expression<String>) exprs[4];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String table = tableExpr.getSingle(event);
        String setCol = setColumnExpr.getSingle(event);
        String setValue = setValueExpr.getSingle(event);
        String whereCol = whereColumnExpr.getSingle(event);
        String whereValue = whereValueExpr.getSingle(event);

        if (!SqlIdentifiers.isValid(table)) {
            QueryEffects.fail(event, "[HexVG-DatabaseAddon] Invalid table name for UPDATE: " + table);
            return;
        }
        if (!SqlIdentifiers.isValid(setCol)) {
            QueryEffects.fail(event, "[HexVG-DatabaseAddon] Invalid SET column for UPDATE: " + setCol);
            return;
        }
        if (!SqlIdentifiers.isValid(whereCol)) {
            QueryEffects.fail(event, "[HexVG-DatabaseAddon] Invalid WHERE column for UPDATE: " + whereCol);
            return;
        }

        String sql = "UPDATE " + table + " SET " + setCol + " = ? WHERE " + whereCol + " = ?";
        String[] params = {setValue, whereValue};

        QueryEffects.runAndStore(event, sql, params, "UPDATE", null);
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db update table " + tableExpr.toString(event, debug);
    }
}
