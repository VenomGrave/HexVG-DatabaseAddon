package com.venomgrave.hexvg.database.database;

import com.venomgrave.hexvg.database.util.DebugLogger;
import com.venomgrave.hexvg.database.util.SqlIdentifiers;
import com.venomgrave.hexvg.database.util.TransactionStateCache;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class QueryExecutor {

    private static final int MAX_PARAMS = 512;
    /** How long a Skript effect waits for its database operation. */
    public static final long EFFECT_TIMEOUT_MS = 5000;
    /** JDBC statement timeout — below EFFECT_TIMEOUT_MS so the driver gives up first. */
    private static final int QUERY_TIMEOUT_SECONDS = 4;

    private final DatabaseManager manager;
    private final TransactionManager transactionManager;
    private final DebugLogger debug;
    /**
     * Own worker pool instead of the Bukkit async scheduler: Paper starts async tasks only
     * on the next tick's heartbeat, so an effect blocking the main thread while waiting
     * for such a task would always hit its timeout (server freeze).
     */
    private final ExecutorService executor;

    public QueryExecutor(DatabaseManager manager, TransactionManager transactionManager,
                         DebugLogger debug, int threads) {
        this.manager = manager;
        this.transactionManager = transactionManager;
        this.debug = debug;
        AtomicInteger counter = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(Math.max(2, threads), task -> {
            Thread t = new Thread(task, "HexVG-DB-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public void executeAsync(String sql, String[] params, UUID playerUuid,
                             BiConsumer<QueryResult, Exception> callback) {
        if (sql == null || sql.trim().isEmpty()) {
            callback.accept(null, new IllegalArgumentException("SQL query cannot be empty."));
            return;
        }
        if (params != null && params.length > MAX_PARAMS) {
            callback.accept(null, new IllegalArgumentException(
                    "Too many parameters: " + params.length + " (max " + MAX_PARAMS + ")."));
            return;
        }

        final boolean inTransaction = transactionManager.hasTransaction(playerUuid);
        if (!inTransaction && transactionManager.isAborted(playerUuid)) {
            callback.accept(null, new SQLException("Transaction was rolled back - query skipped "
                    + "until 'db commit transaction' or 'db begin transaction'."));
            return;
        }

        try {
            executor.execute(() -> run(sql, params, playerUuid, inTransaction, callback));
        } catch (RejectedExecutionException e) {
            callback.accept(null, e);
        }
    }

    private void run(String sql, String[] params, UUID playerUuid, boolean inTransaction,
                     BiConsumer<QueryResult, Exception> callback) {
        final int paramCount = params != null ? params.length : 0;
        long start = System.currentTimeMillis();
        QueryResult result;

        try {
            // If inside a transaction, use the transaction's connection
            Connection txConn = null;
            if (inTransaction) {
                txConn = transactionManager.getTransactionConnection(playerUuid);
                if (txConn == null) {
                    throw new SQLException("Transaction ended before the query ran (rolled back or expired).");
                }
            }

            Connection conn = txConn != null ? txConn : manager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                for (int i = 0; i < paramCount; i++) {
                    stmt.setString(i + 1, params[i]);
                }

                boolean isSelect = stmt.execute();
                if (isSelect) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        result = QueryResult.fromResultSet(rs);
                    }
                } else {
                    result = QueryResult.fromUpdate(stmt.getUpdateCount());
                }

                debug.query(sql, paramCount, System.currentTimeMillis() - start,
                        isSelect ? result.getRowCount() : result.getAffectedRows());
            } finally {
                // Only close connection if NOT inside a transaction
                if (txConn == null) conn.close();
            }
        } catch (Exception e) {
            debug.queryError(sql, paramCount, e);
            // Auto rollback if inside a transaction
            if (inTransaction) {
                transactionManager.autoRollback(playerUuid, "Query failed: " + e.getMessage());
            }
            callback.accept(null, e);
            return;
        }
        callback.accept(result, null);
    }

    /**
     * Runs the query and waits for it (max {@link #EFFECT_TIMEOUT_MS}). On timeout inside a
     * transaction, the transaction is rolled back so a later commit can't persist half of it.
     */
    public QueryResult executeBlocking(String sql, String[] params, UUID playerUuid) throws Exception {
        CompletableFuture<QueryResult> future = new CompletableFuture<>();
        executeAsync(sql, params, playerUuid, (result, error) -> {
            if (error != null) future.completeExceptionally(error);
            else future.complete(result);
        });
        try {
            return await(future, EFFECT_TIMEOUT_MS);
        } catch (SQLTimeoutException e) {
            abortTransactionAsync(playerUuid, "Query timed out");
            throw e;
        }
    }

    public boolean beginTransaction(UUID playerUuid) throws Exception {
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                return transactionManager.begin(playerUuid);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
        try {
            return await(future, EFFECT_TIMEOUT_MS);
        } catch (SQLTimeoutException e) {
            // The script has already given up on this transaction — undo it if it opens late.
            future.thenAccept(started -> {
                if (started) transactionManager.rollback(playerUuid);
            });
            throw e;
        }
    }

    public TransactionManager.CommitStatus commitTransaction(UUID playerUuid) throws Exception {
        try {
            return callBlocking(() -> transactionManager.commit(playerUuid), EFFECT_TIMEOUT_MS);
        } catch (Exception e) {
            // Outcome unknown — report failure rather than let the script assume success.
            TransactionStateCache.setFailed(playerUuid, true);
            throw e;
        }
    }

    /** Rolls back the player's open transaction off the calling thread (e.g. on quit). */
    public void abortTransactionAsync(UUID playerUuid, String reason) {
        Connection conn = transactionManager.detachForAbort(playerUuid, reason);
        if (conn == null) return;
        try {
            executor.execute(() -> transactionManager.rollbackAndClose(conn, playerUuid));
        } catch (RejectedExecutionException e) {
            transactionManager.rollbackAndClose(conn, playerUuid);
        }
    }

    public <T> T callBlocking(Callable<T> task, long timeoutMs) throws Exception {
        return await(executor.submit(task), timeoutMs);
    }

    private static <T> T await(Future<T> future, long timeoutMs) throws Exception {
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof Exception ex) throw ex;
            throw e;
        } catch (TimeoutException e) {
            throw new SQLTimeoutException("Database operation timed out after " + timeoutMs + " ms.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    public void tableExistsAsync(String tableName, BiConsumer<Boolean, Exception> callback) {
        if (!SqlIdentifiers.isValid(tableName)) {
            callback.accept(false, null);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    callback.accept(tableExists(tableName), null);
                } catch (SQLException e) {
                    callback.accept(false, e);
                }
            });
        } catch (RejectedExecutionException e) {
            callback.accept(false, e);
        }
    }

    public boolean tableExists(String tableName) throws SQLException {
        if (!SqlIdentifiers.isValid(tableName)) return false;
        long start = System.currentTimeMillis();
        try (Connection conn = manager.getConnection()) {
            boolean found = tableExists(conn, tableName);
            debug.log("[TABLE CHECK] (" + (System.currentTimeMillis() - start) + "ms) table="
                    + tableName + " exists=" + found);
            return found;
        } catch (SQLException e) {
            debug.queryError("TABLE EXISTS: " + tableName, 0, e);
            throw e;
        }
    }

    /**
     * Creates the table with the given (trusted, raw) SQL unless it already exists.
     * @return true if the table was created, false if it already existed
     */
    public boolean ensureTable(String tableName, String createSql) throws SQLException {
        try (Connection conn = manager.getConnection()) {
            if (tableExists(conn, tableName)) return false;
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.execute(createSql);
            }
            debug.log("[CREATE TABLE] Created table: " + tableName);
            return true;
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        // MySQL: restrict to the configured database — a null catalog searches every
        // database on the server and would report tables that live elsewhere.
        String catalog = conn.getCatalog();
        String[] candidates = {tableName, tableName.toLowerCase(Locale.ROOT), tableName.toUpperCase(Locale.ROOT)};
        for (String candidate : candidates) {
            try (ResultSet rs = meta.getTables(catalog, null, candidate, new String[]{"TABLE"})) {
                if (rs.next()) return true;
            }
        }
        return false;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
