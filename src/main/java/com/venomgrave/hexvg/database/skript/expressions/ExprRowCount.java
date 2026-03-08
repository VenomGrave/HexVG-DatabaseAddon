package com.venomgrave.hexvg.database.skript.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import com.venomgrave.hexvg.database.database.QueryResult;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerEvent;

import java.util.UUID;

public class ExprRowCount extends SimpleExpression<Long> {

    static {
        Skript.registerExpression(ExprRowCount.class, Long.class, ExpressionType.SIMPLE,
                "[db] row count of last [db] query [result]",
                "[db] [number of] rows [in] last [db] query [result]");
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    protected Long[] get(Event event) {
        UUID uuid = null;
        if (event instanceof PlayerEvent) {
            Player p = ((PlayerEvent) event).getPlayer();
            if (p != null) uuid = p.getUniqueId();
        }

        QueryResult result = HexVGAddon.getInstance().getResultCache().get(uuid);
        if (result == null) return new Long[]{0L};

        return new Long[]{(long) result.getRowCount()};
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<Long> getReturnType() {
        return Long.class;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "row count of last db query result";
    }
}
