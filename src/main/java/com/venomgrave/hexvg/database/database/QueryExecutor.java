package com.venomgrave.hexvg.database.database;

import com.venomgrave.hexvg.database.util.DebugLogger;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public class QueryExecutor {

    private static final int MAX_PARAMS = 512;

    private final DatabaseManager manager;
    private final TransactionManager transactionManager;
    private final DebugLogger debug;
    private final Executor asyncExecutor;

    public QueryExecutor(DatabaseManager manager, TransactionManager transactionManager,
                         DebugLogger debug, JavaPlugin plugin) {
        this.manager = manager;
        this.transactionManager = transactionManager;
        this.debug = debug;
        this.asyncExecutor = task -> plugin.getServer().getScheduler()
                .runTaskAsynchronously(plugin, task);
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

        final int paramCount = params != null ? params.length : 0;
        final boolean inTransaction = transactionManager.hasTransaction(playerUuid);

        asyncExecutor.execute(() -> {
            // If inside a transaction, use the transaction's connection
            Connection txConn = inTransaction
                    ? transactionManager.getTransactionConnection(playerUuid)
                    : null;

            long start = System.currentTimeMillis();

            try {
                Connection conn = txConn != null ? txConn : manager.getConnection();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    if (params != null) {
                        for (int i = 0; i < params.length; i++) {
                            stmt.setString(i + 1, params[i]);
                        }
                    }

                    boolean isSelect = stmt.execute();
                    QueryResult result;

                    if (isSelect) {
                        try (ResultSet rs = stmt.getResultSet()) {
                            result = QueryResult.fromResultSet(rs);
                        }
                    } else {
                        result = QueryResult.fromUpdate(stmt.getUpdateCount());
                    }

                    long elapsed = System.currentTimeMillis() - start;
                    debug.query(sql, paramCount, elapsed,
                            isSelect ? result.getRowCount() : result.getAffectedRows());

                    callback.accept(result, null);

                } finally {
                    // Only close connection if NOT inside a transaction
                    if (txConn == null) {
                        conn.close();
                    }
                }

            } catch (SQLException e) {
                debug.queryError(sql, paramCount, e);
                // Auto rollback if inside a transaction
                if (inTransaction) {
                    transactionManager.autoRollback(playerUuid,
                            "Query failed: " + e.getMessage());
                }
                callback.accept(null, e);
            }
        });
    }

    public void tableExistsAsync(String tableName, BiConsumer<Boolean, Exception> callback) {
        if (tableName == null || !tableName.matches("[a-zA-Z0-9_]+")) {
            callback.accept(false, null);
            return;
        }

        asyncExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            try (Connection conn = manager.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
                    boolean found = rs.next();
                    if (!found) {
                        try (ResultSet rs2 = meta.getTables(null, null,
                                tableName.toUpperCase(), new String[]{"TABLE"})) {
                            found = rs2.next();
                        }
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    debug.log("[TABLE CHECK] (" + elapsed + "ms) table=" + tableName + " exists=" + found);
                    callback.accept(found, null);
                }
            } catch (SQLException e) {
                debug.queryError("TABLE EXISTS: " + tableName, 0, e);
                callback.accept(false, e);
            }
        });
    }
}
