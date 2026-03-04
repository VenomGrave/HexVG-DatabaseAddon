package com.venomgrave.hexvg;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAddon;
import com.venomgrave.hexvg.commands.HexVGCommand;
import com.venomgrave.hexvg.database.DatabaseManager;
import com.venomgrave.hexvg.database.QueryExecutor;
import com.venomgrave.hexvg.database.migration.MigrationManager;
import com.venomgrave.hexvg.util.DBLogger;
import com.venomgrave.hexvg.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Map;

public class HexVGDatabaseAddon extends JavaPlugin {

    private static HexVGDatabaseAddon instance;
    private DatabaseManager databaseManager;
    private QueryExecutor queryExecutor;
    private MigrationManager migrationManager;
    private SkriptAddon skriptAddon;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        DBLogger.init();

        // Sprawdzenie czy Skript jest zainstalowany i włączony
        org.bukkit.plugin.Plugin skriptPlugin = Bukkit.getPluginManager().getPlugin("Skript");
        if (skriptPlugin == null || !skriptPlugin.isEnabled()) {
            DBLogger.severe(Messages.SKRIPT_NOT_FOUND.get());
            DBLogger.severe(Messages.SKRIPT_DOWNLOAD.get());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        databaseManager = new DatabaseManager(this);
        queryExecutor = new QueryExecutor(this, databaseManager);
        migrationManager = new MigrationManager(this, databaseManager);
        databaseManager.loadDatabases();

        boolean skriptOk = registerSkriptSyntax();

        HexVGCommand cmd = new HexVGCommand(this);
        getCommand("hexdb").setExecutor(cmd);
        getCommand("hexdb").setTabCompleter(cmd);

        printStartupSummary(skriptOk);
    }

    private boolean registerSkriptSyntax() {
        try {
            skriptAddon = Skript.registerAddon(this);
            skriptAddon.loadClasses("com.venomgrave.hexvg", "effects", "expressions", "conditions");
            return true;
        } catch (IOException e) {
            DBLogger.severe("Failed to register Skript syntax: " + e.getMessage());
            return false;
        }
    }

    private void printStartupSummary(boolean skriptOk) {
        Map<String, Boolean> statusMap = databaseManager.getStatusMap();
        boolean anyConnected = statusMap.values().stream().anyMatch(v -> v);
        boolean hasWarnings = !anyConnected || !skriptOk;

        DBLogger.info(Messages.STARTUP_HEADER.get());
        DBLogger.info(Messages.STARTUP_VERSION.get(getDescription().getVersion()));
        DBLogger.info(Messages.STARTUP_AUTHOR.get());
        DBLogger.info(Messages.STARTUP_SEPARATOR.get());
        DBLogger.info(skriptOk ? Messages.STARTUP_SKRIPT_OK.get() : Messages.STARTUP_SKRIPT_FAIL.get());

        // Tryb: Core bridge lub standalone
        if (databaseManager.isCoreBridgeActive()) {
            DBLogger.info("[HexVG-DB] Mode: CORE BRIDGE (" + databaseManager.getCoreBridgeType() + ")");
        } else {
            DBLogger.info("[HexVG-DB] Mode: STANDALONE");
        }

        DBLogger.info(Messages.STARTUP_DB_HEADER.get());

        if (statusMap.isEmpty()) {
            DBLogger.info(Messages.STARTUP_DB_NONE.get());
        } else {
            for (Map.Entry<String, Boolean> entry : statusMap.entrySet()) {
                String type = databaseManager.getDatabaseType(entry.getKey());
                if (entry.getValue()) {
                    DBLogger.info(Messages.STARTUP_DB_OK.get(entry.getKey(), type));
                } else {
                    DBLogger.info(Messages.STARTUP_DB_FAIL.get(entry.getKey()));
                }
            }
        }

        if (!anyConnected) {
            DBLogger.info(Messages.STARTUP_SEPARATOR.get());
            DBLogger.warning(Messages.STARTUP_WARN_NO_DB.get());
            DBLogger.warning(Messages.STARTUP_WARN_HINT.get());
        }

        DBLogger.info(Messages.STARTUP_SEPARATOR.get());
        DBLogger.info(hasWarnings ? Messages.STARTUP_FOOTER_WARN.get() : Messages.STARTUP_FOOTER_OK.get());
        DBLogger.info(Messages.STARTUP_HEADER.get());
    }

    @Override
    public void onDisable() {
        if (queryExecutor != null) queryExecutor.shutdown();
        if (databaseManager != null) databaseManager.disconnectAll();
        DBLogger.close();
        DBLogger.info(Messages.PLUGIN_DISABLED.get());
    }

    public void reloadPlugin() {
        reloadConfig();
        DBLogger.reload();
        databaseManager.reload();
    }

    public static HexVGDatabaseAddon getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public QueryExecutor getQueryExecutor() { return queryExecutor; }
    public MigrationManager getMigrationManager() { return migrationManager; }
    public SkriptAddon getSkriptAddon() { return skriptAddon; }
}