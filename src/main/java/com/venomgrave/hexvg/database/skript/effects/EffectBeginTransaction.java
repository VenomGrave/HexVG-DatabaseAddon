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
        UUID uuid = null;
        if (event instanceof PlayerEvent) {
            Player p = ((PlayerEvent) event).getPlayer();
            if (p != null) uuid = p.getUniqueId();
        }

        final UUID finalUuid = uuid;
        final JavaPlugin plugin = HexVGAddon.getInstance();

        // Use a latch so Skript waits until the connection is actually open
        // before proceeding to the next query inside the transaction.
        // Max wait: 5 seconds.
        CountDownLatch latch = new CountDownLatch(1);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean started = HexVGAddon.getInstance()
                        .getTransactionManager().begin(finalUuid);
                if (!started) {
                    Skript.warning("[HexVG-DatabaseAddon] Transaction already active for: " + finalUuid);
                }
            } catch (Exception e) {
                Skript.warning("[HexVG-DatabaseAddon] Failed to begin transaction: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        try {
            // Block the Skript thread (which is already async after "wait X ticks")
            // until the connection is ready. This is safe because Skript's
            // wait effect already moves execution off the main server thread.
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db begin transaction";
    }
}
