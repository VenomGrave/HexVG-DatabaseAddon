package com.venomgrave.hexvg.database.placeholder;

import com.venomgrave.hexvg.database.HexVGAddon;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion for HexVG-DatabaseAddon.
 *
 * Available placeholders:
 *   %hexvgdb_<key>%        — returns the cached value for the given key
 *   %hexvgdb_connected%    — "true" / "false" based on DB connection status
 *   %hexvgdb_locked%       — "true" / "false" based on player lock status
 *
 * Values for custom keys are set from Skript using:
 *   db set placeholder "coins" to "%{coins::%uuid of player%}%" for player
 */
public class HexVGExpansion extends PlaceholderExpansion {

    private final HexVGAddon plugin;

    public HexVGExpansion(HexVGAddon plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "hexvgdb";
    }

    @Override
    public @NotNull String getAuthor() {
        return "VenomGrave";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("connected")) {
            return plugin.getDatabaseManager() != null
                    && plugin.getDatabaseManager().isInitialized() ? "true" : "false";
        }

        if (params.equalsIgnoreCase("locked")) {
            if (player == null || player.getUniqueId() == null) return "false";
            return plugin.getCommandCooldown().isLocked(player.getUniqueId()) ? "true" : "false";
        }

        // Custom key set by Skript
        if (player == null || player.getUniqueId() == null) return "";
        return plugin.getPlaceholderCache().get(player.getUniqueId(), params);
    }
}
