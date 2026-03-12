package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import com.venomgrave.hexvg.database.util.CommandCooldown;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public class EffectPlayerLock extends Effect {

    static {
        Skript.registerEffect(EffectPlayerLock.class,
                "db lock %player%",
                "db unlock %player%");
    }

    private Expression<Player> playerExpr;
    private boolean locking;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        playerExpr = (Expression<Player>) exprs[0];
        locking = matchedPattern == 0;
        return true;
    }

    @Override
    protected void execute(Event event) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return;

        CommandCooldown cooldown = HexVGAddon.getInstance().getCommandCooldown();
        if (locking) {
            cooldown.tryLock(player.getUniqueId());
        } else {
            cooldown.unlock(player.getUniqueId());
        }
    }

    @Override
    public String toString(Event event, boolean debug) {
        return (locking ? "db lock " : "db unlock ") + playerExpr.toString(event, debug);
    }
}
