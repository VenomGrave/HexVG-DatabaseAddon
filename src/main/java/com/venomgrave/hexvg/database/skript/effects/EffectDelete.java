package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.util.SqlIdentifiers;
import org.bukkit.event.Event;

public class EffectDelete extends Effect {

    static {
        Skript.registerEffect(EffectDelete.class,
                "db delete from [table] %string% where %string% = %string%");
    }

    private Expression<String> tableExpr;
    private Expression<String> whereColumnExpr;
    private Expression<String> whereValueExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        tableExpr = (Expression<String>) exprs[0];
        whereColumnExpr = (Expression<String>) exprs[1];
        whereValueExpr = (Expression<String>) exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String table = tableExpr.getSingle(event);
        String whereCol = whereColumnExpr.getSingle(event);
        String whereValue = whereValueExpr.getSingle(event);

        if (!SqlIdentifiers.isValid(table)) {
            QueryEffects.fail(event, "[HexVG-DatabaseAddon] Invalid table name for DELETE: " + table);
            return;
        }
        if (!SqlIdentifiers.isValid(whereCol)) {
            QueryEffects.fail(event, "[HexVG-DatabaseAddon] Invalid WHERE column for DELETE: " + whereCol);
            return;
        }

        String sql = "DELETE FROM " + table + " WHERE " + whereCol + " = ?";
        String[] params = {whereValue};

        QueryEffects.runAndStore(event, sql, params, "DELETE", null);
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db delete from table " + tableExpr.toString(event, debug);
    }
}
