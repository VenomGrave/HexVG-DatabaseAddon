package com.venomgrave.hexvg.database.database;

import com.venomgrave.hexvg.database.util.DebugLogger;
import com.venomgrave.hexvg.database.util.TransactionStateCache;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionManager {

    /** Transactions open longer than this are rolled back by {@link #expireStale()}. */
    public static final long MAX_TRANSACTION_MS = 30_000;
    /** How long queries stay refused after an abort (enough for the rest of the trigger). */
    public static final long ABORT_MARK_MS = 10_000;

    public enum CommitStatus { COMMITTED, ROLLED_BACK, NO_TRANSACTION }

    private record ActiveTx(Connection conn, long startedAt) {}

    private static final UUID GLOBAL = new UUID(0, 0);

    private final ConcurrentHashMap<UUID, ActiveTx> activeTransactions = new ConcurrentHashMap<>();
    /**
     * Keys whose transaction was rolled back (query error, timeout, quit) but not
     * yet closed by the script's commit/begin. Queries for these keys are refused, so the
     * rest of the script cannot silently continue in auto-commit mode (partial writes).
     */
    private final ConcurrentHashMap<UUID, Long> abortedTransactions = new ConcurrentHashMap<>();
    private final DatabaseManager databaseManager;
    private final DebugLogger debug;

    public TransactionManager(DatabaseManager databaseManager, DebugLogger debug) {
        this.databaseManager = databaseManager;
        this.debug = debug;
    }

    private static UUID key(UUID uuid) {
        return uuid != null ? uuid : GLOBAL;
    }

    public boolean begin(UUID uuid) throws SQLException {
        UUID key = key(uuid);
        if (activeTransactions.containsKey(key)) {
            debug.log("[TRANSACTION] Already active for: " + key);
            return false;
        }
        Connection conn = databaseManager.getConnection();
        try {
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            closeQuietly(conn);
            throw e;
        }
        if (activeTransactions.putIfAbsent(key, new ActiveTx(conn, System.currentTimeMillis())) != null) {
            closeQuietly(conn);
            debug.log("[TRANSACTION] Already active for: " + key);
            return false;
        }
        abortedTransactions.remove(key);
        TransactionStateCache.clear(uuid);
        debug.log("[TRANSACTION] BEGIN for: " + key);
        return true;
    }

    public CommitStatus commit(UUID uuid) {
        UUID key = key(uuid);
        ActiveTx tx = activeTransactions.remove(key);
        if (tx == null) {
            if (abortedTransactions.remove(key) != null) {
                debug.log("[TRANSACTION] Commit skipped — transaction was rolled back for: " + key);
                return CommitStatus.ROLLED_BACK;
            }
            debug.log("[TRANSACTION] No active transaction to commit for: " + key);
            return CommitStatus.NO_TRANSACTION;
        }
        try {
            tx.conn().commit();
            TransactionStateCache.setFailed(uuid, false);
            debug.log("[TRANSACTION] COMMIT for: " + key);
            return CommitStatus.COMMITTED;
        } catch (SQLException e) {
            debug.log("[TRANSACTION] COMMIT failed for: " + key + " — " + e.getMessage());
            rollbackConnection(tx.conn(), uuid);
            return CommitStatus.ROLLED_BACK;
        } finally {
            closeQuietly(tx.conn());
        }
    }

    /** Rolls back without marking the key as aborted (used when the script never got the transaction). */
    public boolean rollback(UUID uuid) {
        ActiveTx tx = activeTransactions.remove(key(uuid));
        if (tx == null) return false;
        rollbackAndClose(tx.conn(), uuid);
        return true;
    }

    public Connection getTransactionConnection(UUID uuid) {
        ActiveTx tx = activeTransactions.get(key(uuid));
        return tx != null ? tx.conn() : null;
    }

    public boolean hasTransaction(UUID uuid) {
        return activeTransactions.containsKey(key(uuid));
    }

    public boolean isAborted(UUID uuid) {
        Long since = abortedTransactions.get(key(uuid));
        return since != null && System.currentTimeMillis() - since < ABORT_MARK_MS;
    }

    /**
     * Detaches the active transaction and marks the key as aborted (cheap, never blocks).
     * The returned connection must be passed to {@link #rollbackAndClose}.
     */
    public Connection detachForAbort(UUID uuid, String reason) {
        UUID key = key(uuid);
        ActiveTx tx = activeTransactions.remove(key);
        if (tx == null) return null;
        abortedTransactions.put(key, System.currentTimeMillis());
        TransactionStateCache.setFailed(uuid, true);
        debug.log("[TRANSACTION] AUTO ROLLBACK for: " + key + " reason: " + reason);
        return tx.conn();
    }

    public void autoRollback(UUID uuid, String reason) {
        Connection conn = detachForAbort(uuid, reason);
        if (conn != null) rollbackAndClose(conn, uuid);
    }

    /**
     * Rolls back transactions that stayed open too long and forgets old "aborted" marks.
     * Expiry does not mark the key as aborted — the script that opened it is long gone
     * (stop/error before commit), and GLOBAL would otherwise block every non-player query.
     */
    public void expireStale() {
        long now = System.currentTimeMillis();
        activeTransactions.forEach((key, tx) -> {
            if (now - tx.startedAt() > MAX_TRANSACTION_MS && activeTransactions.remove(key, tx)) {
                debug.log("[TRANSACTION] EXPIRED after " + MAX_TRANSACTION_MS + "ms for: " + key);
                rollbackAndClose(tx.conn(), key);
            }
        });
        abortedTransactions.entrySet().removeIf(e -> now - e.getValue() > ABORT_MARK_MS);
    }

    public void closeAll() {
        for (UUID key : activeTransactions.keySet()) {
            ActiveTx tx = activeTransactions.remove(key);
            if (tx != null) rollbackAndClose(tx.conn(), key);
        }
        abortedTransactions.clear();
    }

    public void rollbackAndClose(Connection conn, UUID uuid) {
        rollbackConnection(conn, uuid);
        closeQuietly(conn);
    }

    private void rollbackConnection(Connection conn, UUID uuid) {
        UUID key = key(uuid);
        try {
            conn.rollback();
            debug.log("[TRANSACTION] ROLLBACK for: " + key);
        } catch (SQLException e) {
            debug.log("[TRANSACTION] ROLLBACK failed for: " + key + " — " + e.getMessage());
        }
        TransactionStateCache.setFailed(uuid, true);
    }

    private void closeQuietly(Connection conn) {
        // Separate try blocks: a failing setAutoCommit must not leak the pooled connection.
        try {
            if (!conn.isClosed()) conn.setAutoCommit(true);
        } catch (SQLException ignored) {}
        try {
            conn.close();
        } catch (SQLException ignored) {}
    }
}
