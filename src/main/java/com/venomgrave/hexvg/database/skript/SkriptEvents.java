package com.venomgrave.hexvg.database.skript;

import ch.njol.skript.command.CommandEvent;
import ch.njol.skript.registrations.EventValues;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerEvent;

import java.util.UUID;

/**
 * Resolves the player an event belongs to — the key for per-player results,
 * transactions and transaction state. {@code null} means "no player" (GLOBAL slot).
 *
 * Skript commands ({@code command /x:}) fire {@link CommandEvent}, which is NOT a
 * {@link PlayerEvent}; other player-related events (death, inventory click, block
 * break, ...) are resolved through Skript's own {@code event-player} value.
 */
public final class SkriptEvents {

    private static volatile boolean eventValuesAvailable = true;

    private SkriptEvents() {}

    public static UUID playerUuid(Event event) {
        Player player = player(event);
        return player != null ? player.getUniqueId() : null;
    }

    private static Player player(Event event) {
        if (event == null) return null;
        if (event instanceof PlayerEvent playerEvent) return playerEvent.getPlayer();
        if (event instanceof CommandEvent commandEvent) {
            return commandEvent.getSender() instanceof Player p ? p : null;
        }
        if (eventValuesAvailable) {
            try {
                return EventValues.getEventValue(event, Player.class, 0);
            } catch (LinkageError e) {
                // Skript API changed — fall back to PlayerEvent/CommandEvent only.
                eventValuesAvailable = false;
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }
}
