package com.venomgrave.hexvg.database.migration;

import com.venomgrave.hexvg.HexVGDatabaseAddon;
import com.venomgrave.hexvg.database.DatabaseManager;
import com.venomgrave.hexvg.util.DBLogger;
import com.venomgrave.hexvg.util.Messages;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MigrationManager {

    private static final int BATCH_SIZE = 500;

    private final HexVGDatabaseAddon plugin;
    private final DatabaseManager databaseManager;

    public MigrationManager(HexVGDatabaseAddon plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Migrates all tables from sourceDb to targetDb asynchronously.
     * Automatically detects source/target types and converts schema accordingly.
     */
    public void migrate(String sourceDbName, String targetDbName,
                        Consumer<MigrationResult> onComplete,
                        Consumer<String> onError) {

        if (!databaseManager.isConnected(sourceDbName)) {
            onError.accept(Messages.DB_NOT_FOUND.get(sourceDbName));
            return;
        }
        if (!databaseManager.isConnected(targetDbName)) {
            onError.accept(Messages.DB_NOT_FOUND.get(targetDbName));
            return;
        }

        MigrationResult result = new MigrationResult(sourceDbName, targetDbName);

        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection source = databaseManager.getConnection(sourceDbName);
                     Connection target = databaseManager.getConnection(targetDbName)) {

                    boolean sourceIsMySQL = isMySQL(source);
                    boolean targetIsMySQL = isMySQL(target);

                    if (sourceIsMySQL == targetIsMySQL) {
                        onError.accept(Messages.MIGRATION_SAME_TYPE.get(sourceDbName, targetDbName));
                        return;
                    }

                    DBLogger.info(Messages.MIGRATION_START.get(sourceDbName, targetDbName));

                    List<String> tables = getTables(source, sourceIsMySQL);

                    if (tables.isEmpty()) {
                        onError.accept(Messages.MIGRATION_NO_TABLES.get(sourceDbName));
                        return;
                    }

                    target.setAutoCommit(false);

                    for (String table : tables) {
                        try {
                            int rows = migrateTable(source, target, table, sourceIsMySQL, targetIsMySQL);
                            result.addMigratedTable(table, rows);
                            DBLogger.info(Messages.MIGRATION_TABLE_DONE.get(table, rows));
                        } catch (SQLException e) {
                            result.addFailedTable(table, e.getMessage());
                            DBLogger.warning(Messages.MIGRATION_TABLE_FAILED.get(table, e.getMessage()));
                            // Try to rollback only the current table's changes
                            try { target.rollback(); } catch (SQLException ignored) {}
                        }
                    }

                    target.commit();
                    target.setAutoCommit(true);

                    result.setSuccess(result.getFailedTables().isEmpty());
                    DBLogger.info(Messages.MIGRATION_COMPLETE.get(
                            result.getMigratedTables().size(),
                            result.getTotalRowsMigrated(),
                            result.getFailedTables().size()
                    ));
                    onComplete.accept(result);

                } catch (SQLException e) {
                    result.setSuccess(false);
                    result.setErrorMessage(e.getMessage());
                    DBLogger.severe(Messages.MIGRATION_FAILED.get(e.getMessage()));
                    onError.accept(e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private int migrateTable(Connection source, Connection target,
                             String table, boolean sourceIsMySQL, boolean targetIsMySQL) throws SQLException {

        // 1. Read schema from source
        List<ColumnInfo> columns = getColumns(source, table, sourceIsMySQL);
        if (columns.isEmpty()) throw new SQLException("No columns found in table: " + table);

        // 2. Create table in target (drop if exists)
        String createSQL = buildCreateTable(table, columns, targetIsMySQL);
        try (Statement stmt = target.createStatement()) {
            if (targetIsMySQL) {
                stmt.execute("DROP TABLE IF EXISTS `" + table + "`");
            } else {
                stmt.execute("DROP TABLE IF EXISTS \"" + table + "\"");
            }
            stmt.execute(createSQL);
        }

        // 3. Copy data in batches
        String selectSQL = sourceIsMySQL
                ? "SELECT * FROM `" + table + "`"
                : "SELECT * FROM \"" + table + "\"";

        String insertSQL = buildInsertSQL(table, columns, targetIsMySQL);

        int totalRows = 0;

        try (Statement selectStmt = source.createStatement();
             ResultSet rs = selectStmt.executeQuery(selectSQL);
             PreparedStatement insertStmt = target.prepareStatement(insertSQL)) {

            int batchCount = 0;

            while (rs.next()) {
                for (int i = 0; i < columns.size(); i++) {
                    insertStmt.setObject(i + 1, rs.getObject(columns.get(i).getName()));
                }
                insertStmt.addBatch();
                batchCount++;
                totalRows++;

                if (batchCount >= BATCH_SIZE) {
                    insertStmt.executeBatch();
                    batchCount = 0;
                }
            }

            if (batchCount > 0) {
                insertStmt.executeBatch();
            }
        }

        return totalRows;
    }

    private List<String> getTables(Connection conn, boolean isMySQL) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                // Skip internal SQLite tables
                if (!isMySQL && name.startsWith("sqlite_")) continue;
                tables.add(name);
            }
        }
        return tables;
    }

    private List<ColumnInfo> getColumns(Connection conn, String table, boolean isMySQL) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();

        // Get primary keys
        List<String> primaryKeys = new ArrayList<>();
        try (ResultSet pkRs = meta.getPrimaryKeys(null, null, table)) {
            while (pkRs.next()) {
                primaryKeys.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        try (ResultSet rs = meta.getColumns(null, null, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                boolean isPrimary = primaryKeys.contains(name);
                columns.add(new ColumnInfo(name, type, nullable, isPrimary));
            }
        }
        return columns;
    }

    private String buildCreateTable(String table, List<ColumnInfo> columns, boolean targetIsMySQL) {
        StringBuilder sb = new StringBuilder();
        String quote = targetIsMySQL ? "`" : "\"";

        sb.append("CREATE TABLE ").append(quote).append(table).append(quote).append(" (");

        List<String> pkColumns = new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            String convertedType = targetIsMySQL
                    ? TypeMapper.sqliteToMySQL(col.getType())
                    : TypeMapper.mySQLToSQLite(col.getType());

            sb.append("\n  ").append(quote).append(col.getName()).append(quote)
                    .append(" ").append(convertedType);

            if (!col.isNullable()) sb.append(" NOT NULL");
            if (col.isPrimary()) pkColumns.add(col.getName());

            if (i < columns.size() - 1) sb.append(",");
        }

        if (!pkColumns.isEmpty()) {
            sb.append(",\n  PRIMARY KEY (");
            for (int i = 0; i < pkColumns.size(); i++) {
                sb.append(quote).append(pkColumns.get(i)).append(quote);
                if (i < pkColumns.size() - 1) sb.append(", ");
            }
            sb.append(")");
        }

        sb.append("\n)");
        if (targetIsMySQL) sb.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        sb.append(";");

        return sb.toString();
    }

    private String buildInsertSQL(String table, List<ColumnInfo> columns, boolean targetIsMySQL) {
        String quote = targetIsMySQL ? "`" : "\"";
        StringBuilder sb = new StringBuilder("INSERT INTO ")
                .append(quote).append(table).append(quote).append(" (");

        for (int i = 0; i < columns.size(); i++) {
            sb.append(quote).append(columns.get(i).getName()).append(quote);
            if (i < columns.size() - 1) sb.append(", ");
        }

        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            sb.append("?");
            if (i < columns.size() - 1) sb.append(", ");
        }
        sb.append(")");

        return sb.toString();
    }

    private boolean isMySQL(Connection conn) {
        try {
            String url = conn.getMetaData().getURL();
            return url != null && url.startsWith("jdbc:mysql");
        } catch (SQLException e) {
            return false;
        }
    }

    // Inner data class for column metadata
    public static class ColumnInfo {
        private final String name;
        private final String type;
        private final boolean nullable;
        private final boolean primary;

        public ColumnInfo(String name, String type, boolean nullable, boolean primary) {
            this.name = name;
            this.type = type;
            this.nullable = nullable;
            this.primary = primary;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public boolean isNullable() { return nullable; }
        public boolean isPrimary() { return primary; }
    }
}