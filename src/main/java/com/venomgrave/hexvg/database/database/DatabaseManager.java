package com.venomgrave.hexvg.database.database;

import com.venomgrave.hexvg.database.util.DebugLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private HikariDataSource dataSource;
    private DatabaseType type;
    private final JavaPlugin plugin;
    private final DebugLogger debug;

    public DatabaseManager(JavaPlugin plugin, DebugLogger debug) {
        this.plugin = plugin;
        this.debug = debug;
    }

    public void init(FileConfiguration config) throws SQLException {
        String raw = config.getString("database.type", "SQLITE");
        this.type = DatabaseType.fromString(raw);

        debug.log("[DB INIT] Type selected: " + type.name());

        HikariConfig hikari = new HikariConfig();
        hikari.setConnectionTimeout(10000);
        hikari.setIdleTimeout(600000);
        hikari.setMaxLifetime(1800000);
        hikari.setPoolName("HexVGDatabase-Pool");

        if (type == DatabaseType.MYSQL) {
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "venomgrave");
            String username = config.getString("database.mysql.username", "root");
            String password = config.getString("database.mysql.password", "");
            int poolSize = config.getInt("database.mysql.pool-size", 5);

            if (host.isEmpty() || database.isEmpty() || username.isEmpty()) {
                throw new SQLException("MySQL configuration is incomplete.");
            }

            hikari.setMaximumPoolSize(Math.max(1, Math.min(poolSize, 20)));
            hikari.setMinimumIdle(1);
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&characterEncoding=UTF-8&autoReconnect=true");
            hikari.setUsername(username);
            hikari.setPassword(password);
            hikari.addDataSourceProperty("cachePrepStmts", "true");
            hikari.addDataSourceProperty("prepStmtCacheSize", "250");
            hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            debug.log("[DB INIT] MySQL target: " + host + ":" + port + "/" + database
                    + " user=" + username + " pool-size=" + hikari.getMaximumPoolSize());
        } else {
            File dbFile = new File(plugin.getDataFolder(), config.getString("database.sqlite.file", "database.db"));
            hikari.setMaximumPoolSize(1);
            hikari.setMinimumIdle(1);
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.addDataSourceProperty("journal_mode", "WAL");
            hikari.addDataSourceProperty("synchronous", "NORMAL");

            debug.log("[DB INIT] SQLite file: " + dbFile.getAbsolutePath());
        }

        HikariDataSource newDataSource = new HikariDataSource(hikari);
        try (Connection testConn = newDataSource.getConnection()) {
            if (!testConn.isValid(3)) {
                newDataSource.close();
                throw new SQLException("Database connection test failed.");
            }
        } catch (SQLException e) {
            newDataSource.close();
            throw e;
        }

        this.dataSource = newDataSource;
        debug.log("[DB INIT] Connection test passed.");
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is not initialized or has been closed.");
        }
        return dataSource.getConnection();
    }

    public DatabaseType getType() {
        return type;
    }

    public boolean isInitialized() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            debug.log("[DB CLOSE] Closing connection pool.");
            dataSource.close();
        }
    }
}
