package com.venomgrave.hexvg.database.skript.conditions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public class CondPlayerLocked extends Condition {

    static {
        Skript.registerCondition(CondPlayerLocked.class,
                "%player% is db locked",
                "%player% is not db locked");
    }

    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        playerExpr = (Expression<Player>) exprs[0];
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return isNegated();
        boolean locked = HexVGAddon.getInstance().getCommandCooldown().isLocked(player.getUniqueId());
        return isNegated() != locked;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return playerExpr.toString(event, debug) + (isNegated() ? " is not db locked" : " is db locked");
    }
}
