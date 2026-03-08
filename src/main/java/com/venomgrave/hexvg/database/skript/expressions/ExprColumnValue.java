package com.venomgrave.hexvg.database.skript.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import com.venomgrave.hexvg.database.database.QueryResult;
import com.venomgrave.hexvg.database.util.TypeNormalizer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerEvent;

import java.util.UUID;

public class ExprColumnValue extends SimpleExpression<Object> {

    static {
        Skript.registerExpression(ExprColumnValue.class, Object.class, ExpressionType.SIMPLE,
                "[db] column %string% from row %number% [of last [db] query [result]]");
    }

    private Expression<String> columnExpr;
    private Expression<Number> rowExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        columnExpr = (Expression<String>) exprs[0];
        rowExpr = (Expression<Number>) exprs[1];
        return true;
    }

    @Override
    protected Object[] get(Event event) {
        String column = columnExpr.getSingle(event);
        Number rowNum = rowExpr.getSingle(event);

        if (column == null || rowNum == null) return new Object[0];

        int rowIndex = rowNum.intValue() - 1;

        UUID uuid = null;
        if (event instanceof PlayerEvent) {
            Player p = ((PlayerEvent) event).getPlayer();
            if (p != null) uuid = p.getUniqueId();
        }

        QueryResult result = HexVGAddon.getInstance().getResultCache().get(uuid);
        if (result == null) return new Object[0];

        Object value = result.getValue(rowIndex, column);
        if (value == null) return new Object[0];

        return new Object[]{TypeNormalizer.normalize(value)};
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<Object> getReturnType() {
        return Object.class;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "column " + columnExpr.toString(event, debug) + " from row " + rowExpr.toString(event, debug);
    }
}
