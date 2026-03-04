package com.venomgrave.hexvg.database;

public class DatabaseConfig {

    private final String type;

    // SQLite
    private final String file;

    // MySQL
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    // Pool
    private final int maximumPoolSize;
    private final int minimumIdle;
    private final long connectionTimeoutMs;
    private final long idleTimeoutMs;
    private final long maxLifetimeMs;

    // SQLite constructor
    public DatabaseConfig(String file) {
        this.type = "sqlite";
        this.file = file;
        this.host = null;
        this.port = 0;
        this.database = null;
        this.username = null;
        this.password = null;
        this.maximumPoolSize = 1;
        this.minimumIdle = 1;
        this.connectionTimeoutMs = 10000;
        this.idleTimeoutMs = 600000;
        this.maxLifetimeMs = 1800000;
    }

    // MySQL constructor
    public DatabaseConfig(String host, int port, String database,
                          String username, String password,
                          int maximumPoolSize, int minimumIdle,
                          long connectionTimeoutMs, long idleTimeoutMs, long maxLifetimeMs) {
        this.type = "mysql";
        this.file = null;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.maximumPoolSize = maximumPoolSize;
        this.minimumIdle = minimumIdle;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.idleTimeoutMs = idleTimeoutMs;
        this.maxLifetimeMs = maxLifetimeMs;
    }

    public String getType() { return type; }
    public String getFile() { return file; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public int getMinimumIdle() { return minimumIdle; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public long getIdleTimeoutMs() { return idleTimeoutMs; }
    public long getMaxLifetimeMs() { return maxLifetimeMs; }
}