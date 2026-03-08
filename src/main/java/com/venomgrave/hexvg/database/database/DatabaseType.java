package com.venomgrave.hexvg.database.database;

public enum DatabaseType {
    MYSQL,
    SQLITE;

    public static DatabaseType fromString(String value) {
        if (value == null) return SQLITE;
        switch (value.trim().toUpperCase()) {
            case "MYSQL": return MYSQL;
            case "SQLITE": return SQLITE;
            default: return SQLITE;
        }
    }
}
