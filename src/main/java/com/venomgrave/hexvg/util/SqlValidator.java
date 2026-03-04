package com.venomgrave.hexvg.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Walidator zapytan SQL.
 * Blokuje niebezpieczne operacje niezaleznie od konfiguracji.
 */
public class SqlValidator {

    private static final Set<String> ALWAYS_BLOCKED = new HashSet<>(Arrays.asList(
            "GRANT", "REVOKE", "SHUTDOWN", "LOAD_FILE", "INTO OUTFILE", "INTO DUMPFILE", "LOAD DATA"
    ));

    private static final Pattern SYSTEM_DB_PATTERN = Pattern.compile(
            "(?i)\\b(mysql|information_schema|performance_schema|sys)\\s*[.`]"
    );

    private static final Pattern DROP_DB_PATTERN = Pattern.compile(
            "(?i)\\bDROP\\s+(DATABASE|SCHEMA)\\b"
    );

    private static final Set<String> READ_PREFIXES = new HashSet<>(Arrays.asList(
            "SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN"
    ));

    private static final Set<String> WRITE_PREFIXES = new HashSet<>(Arrays.asList(
            "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER",
            "DROP", "TRUNCATE", "RENAME", "REPLACE"
    ));

    public enum ValidationResult {
        OK,
        BLOCKED_ALWAYS,
        BLOCKED_SYSTEM_DB,
        BLOCKED_DROP_DB,
        BLOCKED_WRITE,
        BLOCKED_READ,
        UNKNOWN_STATEMENT
    }

    private final boolean allowWrite;
    private final boolean allowRawRead;

    public SqlValidator(boolean allowWrite, boolean allowRawRead) {
        this.allowWrite = allowWrite;
        this.allowRawRead = allowRawRead;
    }

    public ValidationResult validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) return ValidationResult.UNKNOWN_STATEMENT;

        String normalized = sql.trim().toUpperCase();

        for (String blocked : ALWAYS_BLOCKED) {
            if (normalized.contains(blocked)) return ValidationResult.BLOCKED_ALWAYS;
        }

        if (SYSTEM_DB_PATTERN.matcher(sql).find()) return ValidationResult.BLOCKED_SYSTEM_DB;
        if (DROP_DB_PATTERN.matcher(sql).find())   return ValidationResult.BLOCKED_DROP_DB;

        String firstWord = normalized.split("\\s+")[0];

        if (READ_PREFIXES.contains(firstWord))  return allowRawRead ? ValidationResult.OK : ValidationResult.BLOCKED_READ;
        if (WRITE_PREFIXES.contains(firstWord)) return allowWrite   ? ValidationResult.OK : ValidationResult.BLOCKED_WRITE;

        return ValidationResult.UNKNOWN_STATEMENT;
    }

    public static String describeResult(ValidationResult result) {
        switch (result) {
            case OK:                return "OK";
            case BLOCKED_ALWAYS:    return "query contains a permanently blocked keyword (GRANT, REVOKE, LOAD DATA, etc.)";
            case BLOCKED_SYSTEM_DB: return "access to system databases (mysql, information_schema) is not allowed";
            case BLOCKED_DROP_DB:   return "DROP DATABASE / DROP SCHEMA is not allowed";
            case BLOCKED_WRITE:     return "write operations are disabled in config (security.allow-raw-write)";
            case BLOCKED_READ:      return "read operations are disabled in config (security.allow-raw-read)";
            default:                return "unknown or unsupported SQL statement type";
        }
    }
}