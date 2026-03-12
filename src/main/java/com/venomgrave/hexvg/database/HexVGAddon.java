package com.venomgrave.hexvg.database;

import com.venomgrave.hexvg.database.database.DatabaseManager;
import com.venomgrave.hexvg.database.database.QueryExecutor;
import com.venomgrave.hexvg.database.database.TransactionManager;
import com.venomgrave.hexvg.database.util.CommandCooldown;
import com.venomgrave.hexvg.database.util.DebugLogger;
import com.venomgrave.hexvg.database.util.PlaceholderCache;
import com.venomgrave.hexvg.database.util.ResultCache;
import com.venomgrave.hexvg.database.placeholder.PlaceholderHook;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class HexVGAddon extends JavaPlugin implements Listener, CommandExecutor {

    private static HexVGAddon instance;

    private DatabaseManager databaseManager;
    private TransactionManager transactionManager;
    private QueryExecutor queryExecutor;
    private ResultCache resultCache;
    private DebugLogger debugLogger;
    private CommandCooldown commandCooldown;
    private PlaceholderCache placeholderCache;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        boolean debugEnabled = getConfig().getBoolean("debug", false);
        this.debugLogger = new DebugLogger(this, debugEnabled);
        this.resultCache = new ResultCache();
        this.databaseManager = new DatabaseManager(this, debugLogger);

        try {
            databaseManager.init(getConfig());
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database connection: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.transactionManager = new TransactionManager(databaseManager, debugLogger);
        this.queryExecutor = new QueryExecutor(databaseManager, transactionManager, debugLogger, this);
        this.commandCooldown = new CommandCooldown();
        this.placeholderCache = new PlaceholderCache();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            PlaceholderHook.register(this);
            getLogger().info("PlaceholderAPI found — expansion registered.");
        }

        if (!registerSkriptElements()) {
            getLogger().warning("Skript not found — Skript syntax will not be available.");
        }

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("hexvgdb") != null) {
            getCommand("hexvgdb").setExecutor(this);
        }

        printBanner(debugEnabled);
    }

    private void printBanner(boolean debugEnabled) {
        String line = "+-----------------------------------------------+";
        getLogger().info(line);
        getLogger().info("|       HexVG-DatabaseAddon  v1.2.0             |");
        getLogger().info("|        VenomGrave Server Plugin               |");
        getLogger().info("+-----------------------------------------------+");
        getLogger().info("|  Database    : " + padRight(databaseManager.getType().name(), 31) + "|");
        getLogger().info("|  Connection  : " + padRight("established", 31) + "|");
        getLogger().info("|  Skript      : " + padRight(isSkriptLoaded() ? "loaded" : "not found", 31) + "|");
        getLogger().info("|  Debug mode  : " + padRight(debugEnabled ? "enabled" : "disabled", 31) + "|");
        getLogger().info("+-----------------------------------------------+");
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private boolean isSkriptLoaded() {
        return getServer().getPluginManager().getPlugin("Skript") != null;
    }

    @Override
    public void onDisable() {
        if (transactionManager != null) transactionManager.closeAll();
        if (resultCache != null) resultCache.clear();
        if (databaseManager != null) databaseManager.close();
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hexvg.database.admin")) {
            sender.sendMessage("§8[§c§lHexVG-DB§8] §cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("debug")) {
            boolean current = debugLogger.isEnabled();
            debugLogger.setEnabled(!current);
            if (!current) {
                sender.sendMessage("§8[§b§lHexVG-DB§8] §fDebug mode §a§lENABLED§f. SQL queries will be logged to console.");
            } else {
                sender.sendMessage("§8[§b§lHexVG-DB§8] §fDebug mode §c§lDISABLED§f.");
            }
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            String dbType = databaseManager != null ? databaseManager.getType().name() : "N/A";
            boolean connected = databaseManager != null && databaseManager.isInitialized();
            boolean debug = debugLogger.isEnabled();

            sender.sendMessage("§8§m                                        ");
            sender.sendMessage("  §b§lHexVG-DatabaseAddon §8— §7Status");
            sender.sendMessage("§8§m                                        ");
            sender.sendMessage("  §7Database   §8» §f" + dbType);
            sender.sendMessage("  §7Connection §8» " + (connected ? "§a● connected" : "§c● disconnected"));
            sender.sendMessage("  §7Debug      §8» " + (debug ? "§aenabled" : "§7disabled"));
            sender.sendMessage("§8§m                                        ");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            boolean debugEnabled = getConfig().getBoolean("debug", false);
            debugLogger.setEnabled(debugEnabled);
            sender.sendMessage("§8[§a§lHexVG-DB§8] §fConfiguration §a§lreloaded§f successfully.");
            return true;
        }

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("  §b§lHexVG-DatabaseAddon §8— §7Commands");
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("  §b/hexvgdb status §8— §7show connection status");
        sender.sendMessage("  §b/hexvgdb debug  §8— §7toggle debug mode");
        sender.sendMessage("  §b/hexvgdb reload §8— §7reload configuration");
        sender.sendMessage("§8§m                                        ");
        return true;
    }

    private boolean registerSkriptElements() {
        try {
            Class.forName("ch.njol.skript.Skript");
        } catch (ClassNotFoundException e) {
            return false;
        }

        try {
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectBeginTransaction");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectCommitTransaction");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectExecuteQuery");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectInsert");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectUpdate");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectDelete");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectCheckTable");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectCreateTable");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectPlayerLock");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectSetPlaceholder");
            Class.forName("com.venomgrave.hexvg.database.skript.expressions.ExprColumnValue");
            Class.forName("com.venomgrave.hexvg.database.skript.expressions.ExprQueryResult");
            Class.forName("com.venomgrave.hexvg.database.skript.expressions.ExprRowCount");
            Class.forName("com.venomgrave.hexvg.database.skript.conditions.CondTableExists");
            Class.forName("com.venomgrave.hexvg.database.skript.conditions.CondTransactionFailed");
            Class.forName("com.venomgrave.hexvg.database.skript.conditions.CondPlayerLocked");
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to register Skript elements: " + e.getMessage());
            return false;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        resultCache.invalidate(event.getPlayer().getUniqueId());
        placeholderCache.invalidate(event.getPlayer().getUniqueId());
    }

    public static HexVGAddon getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public ResultCache getResultCache() {
        return resultCache;
    }

    public PlaceholderCache getPlaceholderCache() {
        return placeholderCache;
    }

    public CommandCooldown getCommandCooldown() {
        return commandCooldown;
    }

    public DebugLogger getDebugLogger() {
        return debugLogger;
    }
}
