package com.venomgrave.hexvg.database;

import com.venomgrave.hexvg.database.database.DatabaseManager;
import com.venomgrave.hexvg.database.database.QueryExecutor;
import com.venomgrave.hexvg.database.util.DebugLogger;
import com.venomgrave.hexvg.database.util.ResultCache;
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
    private QueryExecutor queryExecutor;
    private ResultCache resultCache;
    private DebugLogger debugLogger;

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

        this.queryExecutor = new QueryExecutor(databaseManager, debugLogger, this);

        if (!registerSkriptElements()) {
            getLogger().warning("Skript not found — Skript syntax will not be available.");
        }

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("hexvgdb") != null) {
            getCommand("hexvgdb").setExecutor(this);
        }

        getLogger().info("HexVG-DatabaseAddon enabled. DB type: "
                + databaseManager.getType().name()
                + " | debug=" + debugEnabled);
    }

    @Override
    public void onDisable() {
        if (resultCache != null) resultCache.clear();
        if (databaseManager != null) databaseManager.close();
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hexvg.database.admin")) {
            sender.sendMessage("§cBrak uprawnień.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("debug")) {
            boolean current = debugLogger.isEnabled();
            debugLogger.setEnabled(!current);
            sender.sendMessage("§e[HexVG-DB] Debug mode: " + (!current ? "§awłączony" : "§cwyłączony"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§e[HexVG-DB] Status:");
            sender.sendMessage("§7  Typ bazy: §f" + (databaseManager != null ? databaseManager.getType().name() : "N/A"));
            sender.sendMessage("§7  Połączenie: " + (databaseManager != null && databaseManager.isInitialized() ? "§aaktywne" : "§cbrak"));
            sender.sendMessage("§7  Debug: " + (debugLogger.isEnabled() ? "§awłączony" : "§cwyłączony"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            boolean debugEnabled = getConfig().getBoolean("debug", false);
            debugLogger.setEnabled(debugEnabled);
            sender.sendMessage("§a[HexVG-DB] Konfiguracja przeładowana.");
            return true;
        }

        sender.sendMessage("§e[HexVG-DB] Dostępne komendy:");
        sender.sendMessage("§7  /hexvgdb debug §f— przełącz tryb debug");
        sender.sendMessage("§7  /hexvgdb status §f— status połączenia");
        sender.sendMessage("§7  /hexvgdb reload §f— przeładuj konfigurację");
        return true;
    }

    private boolean registerSkriptElements() {
        try {
            Class.forName("ch.njol.skript.Skript");
        } catch (ClassNotFoundException e) {
            return false;
        }

        try {
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectExecuteQuery");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectInsert");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectUpdate");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectDelete");
            Class.forName("com.venomgrave.hexvg.database.skript.effects.EffectCheckTable");
            Class.forName("com.venomgrave.hexvg.database.skript.expressions.ExprColumnValue");
            Class.forName("com.venomgrave.hexvg.database.skript.expressions.ExprQueryResult");
            Class.forName("com.venomgrave.hexvg.database.skript.expressions.ExprRowCount");
            Class.forName("com.venomgrave.hexvg.database.skript.conditions.CondTableExists");
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to register Skript elements: " + e.getMessage());
            return false;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        resultCache.invalidate(event.getPlayer().getUniqueId());
    }

    public static HexVGAddon getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public ResultCache getResultCache() {
        return resultCache;
    }

    public DebugLogger getDebugLogger() {
        return debugLogger;
    }
}
