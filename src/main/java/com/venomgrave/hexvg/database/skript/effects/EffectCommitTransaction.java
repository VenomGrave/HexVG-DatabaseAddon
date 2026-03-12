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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        UUID uuid = null;
        if (event instanceof PlayerEvent) {
            Player p = ((PlayerEvent) event).getPlayer();
            if (p != null) uuid = p.getUniqueId();
        }

        final UUID finalUuid = uuid;
        final JavaPlugin plugin = HexVGAddon.getInstance();

        CountDownLatch latch = new CountDownLatch(1);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean success = HexVGAddon.getInstance()
                        .getTransactionManager().commit(finalUuid);
                if (!success) {
                    Skript.warning("[HexVG-DatabaseAddon] No active transaction to commit for: " + finalUuid);
                }
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db commit transaction";
    }
}
