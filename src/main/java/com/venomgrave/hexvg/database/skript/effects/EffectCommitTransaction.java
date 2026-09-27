package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import com.venomgrave.hexvg.database.database.TransactionManager.CommitStatus;
import com.venomgrave.hexvg.database.skript.SkriptEvents;
import org.bukkit.event.Event;

import java.util.UUID;

public class EffectCommitTransaction extends Effect {

    static {
        Skript.registerEffect(EffectCommitTransaction.class,
                "db commit transaction");
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    protected void execute(Event event) {
        HexVGAddon addon = HexVGAddon.getInstance();
        if (addon == null || addon.getQueryExecutor() == null) return;

        UUID uuid = SkriptEvents.playerUuid(event);
        try {
            CommitStatus status = addon.getQueryExecutor().commitTransaction(uuid);
            if (status == CommitStatus.NO_TRANSACTION) {
                Skript.warning("[HexVG-DatabaseAddon] No active transaction to commit for: "
                        + (uuid != null ? uuid : "GLOBAL"));
            } else if (status == CommitStatus.ROLLED_BACK) {
                Skript.warning("[HexVG-DatabaseAddon] Transaction was rolled back - changes discarded.");
            }
        } catch (Exception e) {
            Skript.warning("[HexVG-DatabaseAddon] Commit failed: " + e.getMessage());
        }
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db commit transaction";
    }
}
