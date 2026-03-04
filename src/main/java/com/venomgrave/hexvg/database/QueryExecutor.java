package com.venomgrave.hexvg.database;

import com.venomgrave.hexvg.HexVGDatabaseAddon;
import com.venomgrave.hexvg.util.DBLogger;
import com.venomgrave.hexvg.util.Messages;
import com.venomgrave.hexvg.util.SqlValidator;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryExecutor {

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?:INTO|FROM|UPDATE|JOIN)\\s+[`]?([a-zA-Z0-9_]+)[`]?",
            Pattern.CASE_INSENSITIVE
    );

    private final HexVGDatabaseAddon plugin;
    private final DatabaseManager databaseManager;
    private final QueryCache cache;
    private final QueryQueue queue;
    private final SqlValidator validator;

    private final int maxRows;
    private final int queryTimeoutSeconds;

    public QueryExecutor(HexVGDatabaseAddon plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;

        boolean cacheEnabled  = plugin.getConfig().getBoolean("cache.enabled", true);
        long cacheTtl         = plugin.getConfig().getLong("cache.ttl-ms", 5000);
        boolean queueEnabled  = plugin.getConfig().getBoolean("queue.enabled", true);
        int queueSize         = plugin.getConfig().getInt("queue.max-size", 500);
        boolean allowRawWrite = plugin.getConfig().getBoolean("security.allow-raw-write", true);
        boolean allowRawRead  = plugin.getConfig().getBoolean("security.allow-raw-read", true);

        this.maxRows             = plugin.getConfig().getInt("security.max-rows", 1000);
        this.queryTimeoutSeconds = plugin.getConfig().getInt("security.query-timeout-seconds", 10);

        this.cache     = new QueryCache(cacheEnabled, cacheTtl);
        this.queue     = new QueryQueue(queueEnabled, queueSize);
        this.validator = new SqlValidator(allowRawWrite, allowRawRead);
    }

    /** Waliduje surowe SQL. Zwraca null jesli OK, komunikat bledu jesli zablokowane. */
    public String validateRawSql(String sql) {
        SqlValidator.ValidationResult result = validator.validate(sql);
        if (result == SqlValidator.ValidationResult.OK) return null;
        return SqlValidator.describeResult(result);
    }

    // SELECT - wiele wierszy
    public void executeQuery(String sql, List<Object> params,
                             Consumer<List<Map<String, Object>>> onSuccess,
                             Consumer<String> onError) {
        DBLogger.debugQuery("default", sql);

        String cacheKey = cache.buildKey(sql, params);
        List<Map<String, Object>> cached = cache.get(cacheKey);
        if (cached != null) { onSuccess.accept(cached); return; }

        queue.enqueue(() -> {
            if (!databaseManager.isConnected()) { onError.accept(Messages.DB_NOT_FOUND.get("default")); return; }
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = buildStatement(conn, sql, params)) {

                stmt.setQueryTimeout(queryTimeoutSeconds);
                stmt.setMaxRows(maxRows);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
                        rows.add(row);
                        if (rows.size() >= maxRows) {
                            DBLogger.warning("[HexVG-DB] Row limit hit (" + maxRows + "): " + sql.substring(0, Math.min(80, sql.length())));
                            break;
                        }
                    }
                    cache.put(cacheKey, rows);
                    onSuccess.accept(rows);
                }
            } catch (SQLException e) {
                DBLogger.severe(Messages.QUERY_ERROR.get("default", e.getMessage()));
                onError.accept(e.getMessage());
            }
        });
    }

    // INSERT / UPDATE / DELETE / CREATE / DDL
    public void executeUpdate(String sql, List<Object> params,
                              Consumer<Integer> onSuccess,
                              Consumer<String> onError) {
        DBLogger.debugQuery("default", sql);

        queue.enqueue(() -> {
            if (!databaseManager.isConnected()) { onError.accept(Messages.DB_NOT_FOUND.get("default")); return; }
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = buildStatement(conn, sql, params)) {

                stmt.setQueryTimeout(queryTimeoutSeconds);
                int affected = stmt.executeUpdate();
                String table = extractTableName(sql);
                if (table != null) cache.invalidateTable(table);
                onSuccess.accept(affected);

            } catch (SQLException e) {
                DBLogger.severe(Messages.QUERY_ERROR.get("default", e.getMessage()));
                onError.accept(e.getMessage());
            }
        }, QueryQueue.Priority.NORMAL);
    }

    // SELECT - pojedyncza wartosc
    public void executeScalar(String sql, List<Object> params,
                              Consumer<Object> onSuccess,
                              Consumer<String> onError) {
        DBLogger.debugQuery("default", sql);

        String cacheKey = cache.buildKey(sql, params);
        List<Map<String, Object>> cached = cache.get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            onSuccess.accept(cached.get(0).values().iterator().next());
            return;
        }

        queue.enqueue(() -> {
            if (!databaseManager.isConnected()) { onError.accept(Messages.DB_NOT_FOUND.get("default")); return; }
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = buildStatement(conn, sql, params)) {

                stmt.setQueryTimeout(queryTimeoutSeconds);
                stmt.setMaxRows(1);

                try (ResultSet rs = stmt.executeQuery()) {
                    Object val = rs.next() ? rs.getObject(1) : null;
                    if (val != null) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("value", val);
                        List<Map<String, Object>> single = new ArrayList<>();
                        single.add(row);
                        cache.put(cacheKey, single);
                    }
                    onSuccess.accept(val);
                }
            } catch (SQLException e) {
                DBLogger.severe(Messages.QUERY_ERROR.get("default", e.getMessage()));
                onError.accept(e.getMessage());
            }
        });
    }

    private PreparedStatement buildStatement(Connection conn, String sql, List<Object> params) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(sql);
        if (params != null) for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
        return stmt;
    }

    private String extractTableName(String sql) {
        Matcher m = TABLE_PATTERN.matcher(sql);
        return m.find() ? m.group(1) : null;
    }

    public void invalidateCache(String tableName) { cache.invalidateTable(tableName); }
    public void clearCache()                       { cache.invalidateAll(); }
    public int  getCacheSize()                     { return cache.size(); }
    public int  getQueueSize()                     { return queue.queueSize(); }

    public void shutdown() { cache.shutdown(); queue.shutdown(); }
}