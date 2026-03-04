package com.venomgrave.hexvg.database.migration;

/**
 * Maps SQL types between SQLite and MySQL dialects.
 */
public class TypeMapper {

    /**
     * Converts a SQLite column type to MySQL equivalent.
     */
    public static String sqliteToMySQL(String sqliteType) {
        if (sqliteType == null) return "TEXT";
        String t = sqliteType.trim().toUpperCase();

        if (t.startsWith("INTEGER") || t.equals("INT") || t.equals("TINYINT")
                || t.equals("SMALLINT") || t.equals("MEDIUMINT") || t.equals("BIGINT")
                || t.equals("UNSIGNED BIG INT") || t.equals("INT2") || t.equals("INT8")) {
            return "BIGINT";
        }
        if (t.startsWith("VARCHAR") || t.startsWith("NVARCHAR") || t.startsWith("CHARACTER")
                || t.startsWith("VARYING CHARACTER") || t.startsWith("NCHAR")) {
            // Extract length if present, default 255
            int len = extractLength(sqliteType, 255);
            return "VARCHAR(" + len + ")";
        }
        if (t.equals("TEXT") || t.equals("CLOB")) {
            return "LONGTEXT";
        }
        if (t.equals("BLOB") || t.isEmpty()) {
            return "LONGBLOB";
        }
        if (t.startsWith("REAL") || t.startsWith("DOUBLE") || t.startsWith("FLOAT")) {
            return "DOUBLE";
        }
        if (t.startsWith("NUMERIC") || t.startsWith("DECIMAL")) {
            return "DECIMAL(20,6)";
        }
        if (t.equals("BOOLEAN")) {
            return "TINYINT(1)";
        }
        if (t.equals("DATE")) {
            return "DATE";
        }
        if (t.equals("DATETIME") || t.equals("TIMESTAMP")) {
            return "DATETIME";
        }

        // Fallback
        return "LONGTEXT";
    }

    /**
     * Converts a MySQL column type to SQLite equivalent.
     */
    public static String mySQLToSQLite(String mysqlType) {
        if (mysqlType == null) return "TEXT";
        String t = mysqlType.trim().toUpperCase();

        if (t.startsWith("INT") || t.startsWith("TINYINT") || t.startsWith("SMALLINT")
                || t.startsWith("MEDIUMINT") || t.startsWith("BIGINT")) {
            return "INTEGER";
        }
        if (t.startsWith("VARCHAR") || t.startsWith("CHAR") || t.startsWith("NVARCHAR")) {
            return "TEXT";
        }
        if (t.startsWith("TEXT") || t.startsWith("TINYTEXT")
                || t.startsWith("MEDIUMTEXT") || t.startsWith("LONGTEXT")) {
            return "TEXT";
        }
        if (t.startsWith("BLOB") || t.startsWith("TINYBLOB")
                || t.startsWith("MEDIUMBLOB") || t.startsWith("LONGBLOB") || t.startsWith("BINARY")) {
            return "BLOB";
        }
        if (t.startsWith("FLOAT") || t.startsWith("DOUBLE") || t.startsWith("REAL")) {
            return "REAL";
        }
        if (t.startsWith("DECIMAL") || t.startsWith("NUMERIC")) {
            return "NUMERIC";
        }
        if (t.startsWith("BOOL")) {
            return "INTEGER";
        }
        if (t.startsWith("DATE") || t.startsWith("DATETIME") || t.startsWith("TIMESTAMP")) {
            return "TEXT";
        }

        return "TEXT";
    }

    private static int extractLength(String type, int defaultLen) {
        try {
            int start = type.indexOf('(');
            int end = type.indexOf(')');
            if (start != -1 && end != -1) {
                return Integer.parseInt(type.substring(start + 1, end).trim());
            }
        } catch (NumberFormatException ignored) {}
        return defaultLen;
    }
}