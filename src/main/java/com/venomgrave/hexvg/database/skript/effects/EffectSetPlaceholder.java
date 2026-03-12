package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public class EffectSetPlaceholder extends Effect {

    static {
        Skript.registerEffect(EffectSetPlaceholder.class,
                "db set placeholder %string% to %string% for %player%");
    }

    private Expression<String> keyExpr;
    private Expression<String> valueExpr;
    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        keyExpr   = (Expression<String>) exprs[0];
        valueExpr = (Expression<String>) exprs[1];
        playerExpr = (Expression<Player>) exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String key   = keyExpr.getSingle(event);
        String value = valueExpr.getSingle(event);
        Player player = playerExpr.getSingle(event);
        if (key == null || value == null || player == null) return;

        HexVGAddon.getInstance().getPlaceholderCache().set(player.getUniqueId(), key, value);
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db set placeholder " + keyExpr.toString(event, debug)
                + " to " + valueExpr.toString(event, debug)
                + " for " + playerExpr.toString(event, debug);
    }
}
