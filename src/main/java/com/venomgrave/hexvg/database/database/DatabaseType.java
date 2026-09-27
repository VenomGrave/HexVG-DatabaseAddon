package com.venomgrave.hexvg.database.database;

public enum DatabaseType {
    MYSQL,
    SQLITE;

    public static DatabaseType fromString(String value) {
        if (value == null) return SQLITE;
        switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "MYSQL": return MYSQL;
            case "SQLITE": return SQLITE;
            default: return SQLITE;
        }
    }
}
