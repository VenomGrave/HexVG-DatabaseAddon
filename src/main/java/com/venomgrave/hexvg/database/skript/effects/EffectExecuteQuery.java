package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerEvent;

import java.util.UUID;

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
        if (sql == null || sql.trim().isEmpty()) return;

        String[] params = paramsExpr != null ? paramsExpr.getAll(event) : new String[0];

        UUID uuid = null;
        if (event instanceof PlayerEvent) {
            Player p = ((PlayerEvent) event).getPlayer();
            if (p != null) uuid = p.getUniqueId();
        }

        final UUID finalUuid = uuid;

        HexVGAddon.getInstance().getQueryExecutor().executeAsync(sql, params, finalUuid,
                (result, error) -> {
                    if (error != null) {
                        Skript.warning("[HexVG-DatabaseAddon] Query failed: " + error.getMessage()
                                + " | SQL: " + sql);
                        return;
                    }
                    HexVGAddon.getInstance().getResultCache().store(finalUuid, result);
                });
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "execute db query " + sqlExpr.toString(event, debug);
    }
}
