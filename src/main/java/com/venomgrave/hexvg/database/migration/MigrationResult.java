package com.venomgrave.hexvg.database.migration;

import java.util.ArrayList;
import java.util.List;

public class MigrationResult {

    private final String sourceDb;
    private final String targetDb;
    private final List<String> migratedTables = new ArrayList<>();
    private final List<String> failedTables = new ArrayList<>();
    private long totalRowsMigrated = 0;
    private boolean success = false;
    private String errorMessage = null;

    public MigrationResult(String sourceDb, String targetDb) {
        this.sourceDb = sourceDb;
        this.targetDb = targetDb;
    }

    public void addMigratedTable(String table, int rows) {
        migratedTables.add(table);
        totalRowsMigrated += rows;
    }

    public void addFailedTable(String table, String reason) {
        failedTables.add(table + " (" + reason + ")");
    }

    public void setSuccess(boolean success) { this.success = success; }
    public void setErrorMessage(String msg) { this.errorMessage = msg; }

    public String getSourceDb() { return sourceDb; }
    public String getTargetDb() { return targetDb; }
    public List<String> getMigratedTables() { return migratedTables; }
    public List<String> getFailedTables() { return failedTables; }
    public long getTotalRowsMigrated() { return totalRowsMigrated; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}