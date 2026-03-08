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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExprQueryResult extends SimpleExpression<Object> {

    static {
        Skript.registerExpression(ExprQueryResult.class, Object.class, ExpressionType.SIMPLE,
                "[all] [db] values of column %string% [from last [db] query [result]]");
    }

    private Expression<String> columnExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        columnExpr = (Expression<String>) exprs[0];
        return true;
    }

    @Override
    protected Object[] get(Event event) {
        String column = columnExpr.getSingle(event);
        if (column == null) return new Object[0];

        UUID uuid = null;
        if (event instanceof PlayerEvent) {
            Player p = ((PlayerEvent) event).getPlayer();
            if (p != null) uuid = p.getUniqueId();
        }

        QueryResult result = HexVGAddon.getInstance().getResultCache().get(uuid);
        if (result == null || !result.hasRows()) return new Object[0];

        List<Object> values = new ArrayList<>();
        for (Map<String, Object> row : result.getRows()) {
            Object val = row.get(column.toLowerCase());
            if (val != null) values.add(TypeNormalizer.normalize(val));
        }
        return values.toArray();
    }

    @Override
    public boolean isSingle() {
        return false;
    }

    @Override
    public Class<Object> getReturnType() {
        return Object.class;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "all values of column " + columnExpr.toString(event, debug) + " from last db query result";
    }
}
