package com.venomgrave.hexvg.database;

import com.venomgrave.hexvg.HexVGDatabaseAddon;
import com.venomgrave.hexvg.util.DBLogger;
import com.venomgrave.hexvg.util.Messages;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {

    private static final String DEFAULT = "default";

    private final HexVGDatabaseAddon plugin;
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, DatabaseConfig>   configs     = new ConcurrentHashMap<>();
    private BukkitRunnable reconnectTask;

    // Most do HexVG-Core (null jesli Core niedostepny)
    private final CoreBridge coreBridge;

    public DatabaseManager(HexVGDatabaseAddon plugin) {
        this.plugin     = plugin;
        this.coreBridge = new CoreBridge(plugin.getLogger());
    }

    // ── Inicjalizacja ─────────────────────────────────────────────────────

    public void loadDatabases() {
        // 1. Najpierw sprawdź czy Core jest dostępny
        if (coreBridge.tryConnect()) {
            // Core połączony — nie ładujemy własnego HikariCP
            DBLogger.info("[HexVG-DB] Konfiguracja bazy pominięta — używam połączenia z HexVG-Core.");
            return;
        }

        // 2. Standalone mode — ładujemy własne połączenie z config.yml
        ConfigurationSection storage = plugin.getConfig().getConfigurationSection("storage");
        if (storage == null) {
            DBLogger.warning("No 'storage' section found in config.yml!");
            return;
        }

        String type = storage.getString("type", "sqlite").toLowerCase();
        DatabaseConfig config;

        if (type.equals("mysql")) {
            ConfigurationSection mysql = storage.getConfigurationSection("mysql");
            if (mysql == null) {
                DBLogger.warning("No 'storage.mysql' section found in config.yml!");
                return;
            }
            ConfigurationSection pool = mysql.getConfigurationSection("pool");
            config = new DatabaseConfig(
                    mysql.getString("host", "127.0.0.1"),
                    mysql.getInt("port", 3306),
                    mysql.getString("database", "minecraft"),
                    mysql.getString("username", "root"),
                    mysql.getString("password", ""),
                    pool != null ? pool.getInt("maximumPoolSize", 10) : 10,
                    pool != null ? pool.getInt("minimumIdle", 1) : 1,
                    pool != null ? pool.getLong("connectionTimeoutMs", 10000) : 10000,
                    pool != null ? pool.getLong("idleTimeoutMs", 600000) : 600000,
                    pool != null ? pool.getLong("maxLifetimeMs", 1800000) : 1800000
            );
        } else {
            ConfigurationSection sqlite = storage.getConfigurationSection("sqlite");
            String file = sqlite != null ? sqlite.getString("file", "skript.db") : "skript.db";
            config = new DatabaseConfig(file);
        }

        configs.put(DEFAULT, config);
        connect(DEFAULT, config);
        startReconnectTask();
    }

    // ── Połączenie ────────────────────────────────────────────────────────

    public boolean connect(String name, DatabaseConfig config) {
        DBLogger.info(Messages.DB_CONNECTING.get(name));

        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setPoolName("HexVG-" + name);
            hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
            hikariConfig.setMinimumIdle(config.getMinimumIdle());
            hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
            hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
            hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());
            hikariConfig.setLeakDetectionThreshold(60000);

            if (config.getType().equals("mysql")) {
                hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
                hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getHost() + ":" + config.getPort()
                        + "/" + config.getDatabase()
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&autoReconnect=false");
                hikariConfig.setUsername(config.getUsername());
                hikariConfig.setPassword(config.getPassword());
                hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
                hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
                hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            } else {
                File dbFile = new File(plugin.getDataFolder(), config.getFile());
                dbFile.getParentFile().mkdirs();
                hikariConfig.setDriverClassName("org.sqlite.JDBC");
                hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                hikariConfig.setMaximumPoolSize(1);
            }

            HikariDataSource dataSource = new HikariDataSource(hikariConfig);

            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(5)) {
                    dataSources.put(name, dataSource);
                    DBLogger.info(Messages.DB_CONNECTED.get(name));
                    return true;
                }
            }

            dataSource.close();
            DBLogger.severe(Messages.DB_CONNECTION_FAILED.get(name, "Connection test failed."));
            return false;

        } catch (Exception e) {
            DBLogger.severe(Messages.DB_CONNECTION_FAILED.get(name, e.getMessage()));
            return false;
        }
    }

    public void disconnect(String name) {
        HikariDataSource ds = dataSources.remove(name);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            DBLogger.info(Messages.DB_DISCONNECTED.get(name));
        }
    }

    public void disconnectAll() {
        stopReconnectTask();
        // Własne połączenia — zamykamy
        for (String name : new HashSet<>(dataSources.keySet())) {
            disconnect(name);
        }
        // Most do Core — tylko dezaktywujemy, nie zamykamy (Core jest właścicielem)
        coreBridge.disconnect();
    }

    // ── Pobieranie połączenia ─────────────────────────────────────────────

    /**
     * Zwraca połączenie — z Core jeśli most aktywny, własne HikariCP jeśli standalone.
     */
    public Connection getConnection(String name) throws SQLException {
        // Jeśli most aktywny — zawsze używaj Core (ignoruj name, jest tylko "default")
        if (coreBridge.isActive()) {
            return coreBridge.getConnection();
        }

        HikariDataSource ds = dataSources.get(name);
        if (ds == null || ds.isClosed()) {
            throw new SQLException("Database '" + name + "' is not connected.");
        }
        return ds.getConnection();
    }

    public Connection getConnection() throws SQLException {
        return getConnection(DEFAULT);
    }

    // ── Status ────────────────────────────────────────────────────────────

    public boolean isConnected(String name) {
        if (coreBridge.isActive()) return true;
        HikariDataSource ds = dataSources.get(name);
        return ds != null && !ds.isClosed();
    }

    public boolean isConnected() {
        return isConnected(DEFAULT);
    }

    public boolean isCoreBridgeActive() {
        return coreBridge.isActive();
    }

    public String getCoreBridgeType() {
        return coreBridge.getDatabaseType();
    }

    public Set<String> getDatabaseNames() {
        return Collections.unmodifiableSet(configs.keySet());
    }

    public String getDatabaseType(String name) {
        // Jeśli most aktywny — typ pochodzi z Core
        if (coreBridge.isActive()) {
            String type = coreBridge.getDatabaseType();
            return type != null ? type : "CORE";
        }
        DatabaseConfig config = configs.get(name);
        return config != null ? config.getType().toUpperCase() : "UNKNOWN";
    }

    public Map<String, Boolean> getStatusMap() {
        // Jeśli most aktywny — pokaż jako "core" w statusie
        if (coreBridge.isActive()) {
            Map<String, Boolean> status = new LinkedHashMap<>();
            status.put("core (HexVG-Core)", true);
            return status;
        }
        Map<String, Boolean> status = new LinkedHashMap<>();
        for (String name : configs.keySet()) {
            status.put(name, isConnected(name));
        }
        return status;
    }

    // ── Reconnect task (tylko standalone) ────────────────────────────────

    private void startReconnectTask() {
        if (!plugin.getConfig().getBoolean("reconnect.enabled", true)) return;

        int intervalSeconds = plugin.getConfig().getInt("reconnect.interval-seconds", 30);
        int maxAttempts     = plugin.getConfig().getInt("reconnect.max-attempts", 5);
        Map<String, Integer> attemptCounts = new ConcurrentHashMap<>();

        reconnectTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Reconnect task działa tylko w standalone mode
                if (coreBridge.isActive()) return;

                for (Map.Entry<String, DatabaseConfig> entry : configs.entrySet()) {
                    String name = entry.getKey();
                    if (!isConnected(name)) {
                        int attempts = attemptCounts.getOrDefault(name, 0);
                        if (attempts >= maxAttempts) {
                            DBLogger.severe(Messages.DB_RECONNECT_FAILED.get(name));
                            continue;
                        }
                        attemptCounts.put(name, attempts + 1);
                        DBLogger.warning(Messages.DB_RECONNECTING.get(name, attempts + 1, maxAttempts));
                        if (connect(name, entry.getValue())) {
                            attemptCounts.remove(name);
                        }
                    } else {
                        attemptCounts.remove(name);
                    }
                }
            }
        };

        reconnectTask.runTaskTimerAsynchronously(plugin, intervalSeconds * 20L, intervalSeconds * 20L);
    }

    private void stopReconnectTask() {
        if (reconnectTask != null) {
            reconnectTask.cancel();
            reconnectTask = null;
        }
    }

    public void reload() {
        disconnectAll();
        configs.clear();
        loadDatabases();
    }
}