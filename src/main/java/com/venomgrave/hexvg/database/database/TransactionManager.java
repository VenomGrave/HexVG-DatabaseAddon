package com.venomgrave.hexvg.database.database;

import com.venomgrave.hexvg.database.util.DebugLogger;
import com.venomgrave.hexvg.database.util.TransactionStateCache;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionManager {

    private final ConcurrentHashMap<UUID, Connection> activeTransactions = new ConcurrentHashMap<>();
    private final DatabaseManager databaseManager;
    private final DebugLogger debug;
    private static final UUID GLOBAL = new UUID(0, 0);

    public TransactionManager(DatabaseManager databaseManager, DebugLogger debug) {
        this.databaseManager = databaseManager;
        this.debug = debug;
    }

    public boolean begin(UUID uuid) throws SQLException {
        UUID key = uuid != null ? uuid : GLOBAL;
        if (activeTransactions.containsKey(key)) {
            debug.log("[TRANSACTION] Already active for: " + key);
            return false;
        }
        TransactionStateCache.clear(uuid);
        Connection conn = databaseManager.getConnection();
        conn.setAutoCommit(false);
        activeTransactions.put(key, conn);
        debug.log("[TRANSACTION] BEGIN for: " + key);
        return true;
    }

    public boolean commit(UUID uuid) {
        UUID key = uuid != null ? uuid : GLOBAL;
        Connection conn = activeTransactions.remove(key);
        if (conn == null) {
            debug.log("[TRANSACTION] No active transaction to commit for: " + key);
            return false;
        }
        try {
            conn.commit();
            TransactionStateCache.setFailed(uuid, false);
            debug.log("[TRANSACTION] COMMIT for: " + key);
            return true;
        } catch (SQLException e) {
            debug.log("[TRANSACTION] COMMIT failed for: " + key + " — " + e.getMessage());
            rollbackConnection(conn, uuid);
            return false;
        } finally {
            closeQuietly(conn);
        }
    }

    public boolean rollback(UUID uuid) {
        UUID key = uuid != null ? uuid : GLOBAL;
        Connection conn = activeTransactions.remove(key);
        if (conn == null) return false;
        rollbackConnection(conn, uuid);
        closeQuietly(conn);
        return true;
    }

    public Connection getTransactionConnection(UUID uuid) {
        UUID key = uuid != null ? uuid : GLOBAL;
        return activeTransactions.get(key);
    }

    public boolean hasTransaction(UUID uuid) {
        UUID key = uuid != null ? uuid : GLOBAL;
        return activeTransactions.containsKey(key);
    }

    public void autoRollback(UUID uuid, String reason) {
        UUID key = uuid != null ? uuid : GLOBAL;
        Connection conn = activeTransactions.remove(key);
        if (conn != null) {
            debug.log("[TRANSACTION] AUTO ROLLBACK for: " + key + " reason: " + reason);
            rollbackConnection(conn, uuid);
            closeQuietly(conn);
        }
    }

    public void closeAll() {
        for (UUID key : activeTransactions.keySet()) {
            Connection conn = activeTransactions.remove(key);
            if (conn != null) {
                rollbackConnection(conn, key);
                closeQuietly(conn);
            }
        }
    }

    private void rollbackConnection(Connection conn, UUID uuid) {
        UUID key = uuid != null ? uuid : GLOBAL;
        try {
            conn.rollback();
            TransactionStateCache.setFailed(uuid, true);
            debug.log("[TRANSACTION] ROLLBACK for: " + key);
        } catch (SQLException e) {
            debug.log("[TRANSACTION] ROLLBACK failed for: " + key + " — " + e.getMessage());
        }
    }

    private void closeQuietly(Connection conn) {
        try {
            if (!conn.isClosed()) {
                conn.setAutoCommit(true);
                conn.close();
            }
        } catch (SQLException ignored) {}
    }
}
