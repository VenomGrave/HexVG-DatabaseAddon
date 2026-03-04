package com.venomgrave.hexvg.commands;

import com.venomgrave.hexvg.HexVGDatabaseAddon;
import com.venomgrave.hexvg.database.migration.MigrationResult;
import com.venomgrave.hexvg.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class HexVGCommand implements CommandExecutor, TabCompleter {

    private final HexVGDatabaseAddon plugin;
    private final AtomicBoolean migrationRunning = new AtomicBoolean(false);

    public HexVGCommand(HexVGDatabaseAddon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hexvg.admin")) {
            sender.sendMessage(Messages.getPrefix() + Messages.NO_PERMISSION.get());
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload"  -> handleReload(sender);
            case "status"  -> handleStatus(sender);
            case "migrate" -> handleMigrate(sender, args);
            case "cache"   -> handleCache(sender, args);
            default        -> sendUsage(sender);
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.reloadPlugin();
            sender.sendMessage(Messages.getPrefix() + Messages.RELOAD_SUCCESS.get());
        } catch (Exception e) {
            sender.sendMessage(Messages.getPrefix() + Messages.RELOAD_FAILED.get());
        }
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(Messages.STATUS_HEADER.get());

        // Tryb pracy: Core Bridge lub Standalone
        if (plugin.getDatabaseManager().isCoreBridgeActive()) {
            sender.sendMessage(Messages.colorize(
                    "&7  Mode&8: &bCORE BRIDGE &7(połączenie z HexVG-Core)"
            ));
            sender.sendMessage(Messages.colorize(
                    "&7  Typ bazy&8: &e" + plugin.getDatabaseManager().getCoreBridgeType()
            ));
        } else {
            sender.sendMessage(Messages.colorize("&7  Mode&8: &7STANDALONE"));
        }

        Map<String, Boolean> statusMap = plugin.getDatabaseManager().getStatusMap();
        if (statusMap.isEmpty()) {
            sender.sendMessage(Messages.STATUS_NO_DATABASES.get());
            return;
        }

        // Baza danych
        for (Map.Entry<String, Boolean> entry : statusMap.entrySet()) {
            String statusStr = entry.getValue()
                    ? Messages.STATUS_CONNECTED.get()
                    : Messages.STATUS_DISCONNECTED.get();
            sender.sendMessage(Messages.STATUS_ENTRY.get(entry.getKey(), statusStr));
        }

        // Cache
        boolean cacheEnabled = plugin.getConfig().getBoolean("cache.enabled", true);
        long cacheTtl        = plugin.getConfig().getLong("cache.ttl-ms", 5000);
        int cacheSize        = plugin.getQueryExecutor().getCacheSize();
        sender.sendMessage(Messages.colorize(
                "&7  Cache&8: " + (cacheEnabled ? "&a✔ włączony" : "&c✘ wyłączony") +
                        " &8| &7TTL: &e" + cacheTtl + "ms" +
                        " &8| &7Wpisów: &e" + cacheSize
        ));

        // Kolejka
        boolean queueEnabled = plugin.getConfig().getBoolean("queue.enabled", true);
        int queuePending     = plugin.getQueryExecutor().getQueueSize();
        int queueMax         = plugin.getConfig().getInt("queue.max-size", 500);
        sender.sendMessage(Messages.colorize(
                "&7  Kolejka&8: " + (queueEnabled ? "&a✔ włączona" : "&c✘ wyłączona") +
                        " &8| &7Oczekujące: &e" + queuePending + "&8/&7" + queueMax
        ));
    }

    private void handleCache(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.colorize("&7Użycie: &e/hexdb cache <clear>"));
            return;
        }
        if (args[1].equalsIgnoreCase("clear")) {
            plugin.getQueryExecutor().clearCache();
            sender.sendMessage(Messages.getPrefix() + Messages.colorize("&aCache wyczyszczony!"));
        }
    }

    private void handleMigrate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.colorize("&7Użycie: &e/hexdb migrate <source> <target>"));
            return;
        }

        String source = args[1];
        String target = args[2];

        if (source.equals(target)) {
            sender.sendMessage(Messages.getPrefix() + Messages.MIGRATION_SAME_TYPE.get(source, target));
            return;
        }

        if (!migrationRunning.compareAndSet(false, true)) {
            sender.sendMessage(Messages.getPrefix() + Messages.MIGRATION_ALREADY_RUNNING.get());
            return;
        }

        sender.sendMessage(Messages.getPrefix() + Messages.MIGRATION_START.get(source, target));

        plugin.getMigrationManager().migrate(
                source, target,
                result -> {
                    migrationRunning.set(false);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        boolean canSend = !(sender instanceof org.bukkit.entity.Player)
                                || ((org.bukkit.entity.Player) sender).isOnline();
                        if (!canSend) return;
                        sender.sendMessage(Messages.getPrefix() + Messages.MIGRATION_COMPLETE.get(
                                result.getMigratedTables().size(),
                                result.getTotalRowsMigrated(),
                                result.getFailedTables().size()
                        ));
                        if (!result.getFailedTables().isEmpty()) {
                            sender.sendMessage(Messages.colorize("&cNieudane tabele:"));
                            result.getFailedTables().forEach(t ->
                                    sender.sendMessage(Messages.colorize("&c  - " + t)));
                        }
                    });
                },
                error -> {
                    migrationRunning.set(false);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            sender.sendMessage(Messages.getPrefix() + Messages.MIGRATION_FAILED.get(error)));
                }
        );
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Messages.colorize("&7Użycie: &e/hexdb <reload|status|cache clear|migrate <source> <target>>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("hexvg.admin")) return new ArrayList<>();

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("reload", "status", "cache", "migrate"));
            completions.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("cache")) return List.of("clear");
            if (args[0].equalsIgnoreCase("migrate")) {
                List<String> names = new ArrayList<>(plugin.getDatabaseManager().getDatabaseNames());
                names.removeIf(s -> !s.startsWith(args[1]));
                return names;
            }
        }

        return new ArrayList<>();
    }
}