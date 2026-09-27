package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import com.venomgrave.hexvg.database.skript.SkriptEvents;
import org.bukkit.event.Event;

import java.util.UUID;

/**
 * db begin transaction
 *
 * Blocks until the transaction connection is open (max 5 s). If the transaction can't
 * be started (one is already active for this player, DB error, timeout) the rest of the
 * trigger is NOT executed — otherwise its writes would run outside a transaction or
 * inside another trigger's transaction (double-spend).
 */
public class EffectBeginTransaction extends Effect {

    static {
        Skript.registerEffect(EffectBeginTransaction.class,
                "db begin transaction");
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    protected void execute(Event event) {
        begin(event);
    }

    @Override
    protected TriggerItem walk(Event event) {
        if (!begin(event)) return null; // stop the trigger
        return getNext();
    }

    private boolean begin(Event event) {
        HexVGAddon addon = HexVGAddon.getInstance();
        if (addon == null || addon.getQueryExecutor() == null) return false;

        UUID uuid = SkriptEvents.playerUuid(event);
        try {
            if (addon.getQueryExecutor().beginTransaction(uuid)) return true;
            Skript.warning("[HexVG-DatabaseAddon] Transaction already active for: "
                    + (uuid != null ? uuid : "GLOBAL") + " - trigger stopped.");
        } catch (Exception e) {
            Skript.warning("[HexVG-DatabaseAddon] Failed to begin transaction: " + e.getMessage()
                    + " - trigger stopped.");
        }
        return false;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db begin transaction";
    }
}
