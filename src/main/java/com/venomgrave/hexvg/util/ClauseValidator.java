package com.venomgrave.hexvg.util;

import java.util.regex.Pattern;

/**
 * Walidator klauzul WHERE i SET w zapytaniach SQL.
 *
 * Nie blokuje wszystkiego — to jest niemozliwe bez pelnego parsera SQL.
 * Blokuje najbardziej oczywiste przypadki naduzywania:
 *   - zagniezdzone SELECT (subqueries)
 *   - komentarze SQL (-- i /* )
 *   - sredniki (wielokrotne zapytania)
 *   - UNION / INTERSECT / EXCEPT
 *   - dostep do systemowych tabel
 */
public class ClauseValidator {

    // Subquery
    private static final Pattern SUBQUERY = Pattern.compile(
            "(?i)\\bSELECT\\b"
    );

    // Komentarze SQL
    private static final Pattern SQL_COMMENT = Pattern.compile(
            "--|\\/\\*|\\*\\/"
    );

    // Srednik - wielokrotne zapytania
    private static final Pattern SEMICOLON = Pattern.compile(";");

    // UNION / INTERSECT / EXCEPT - laczy wyniki zapytan
    private static final Pattern SET_OPERATIONS = Pattern.compile(
            "(?i)\\b(UNION|INTERSECT|EXCEPT)\\b"
    );

    // Dostep do systemowych baz przez WHERE
    private static final Pattern SYSTEM_DB = Pattern.compile(
            "(?i)\\b(mysql|information_schema|performance_schema|sys)\\s*[.`]"
    );

    public enum ClauseResult {
        OK,
        BLOCKED_SUBQUERY,
        BLOCKED_COMMENT,
        BLOCKED_SEMICOLON,
        BLOCKED_SET_OPERATION,
        BLOCKED_SYSTEM_DB
    }

    /**
     * Waliduje klauzule WHERE lub SET.
     */
    public static ClauseResult validate(String clause) {
        if (clause == null || clause.trim().isEmpty()) return ClauseResult.OK;

        if (SUBQUERY.matcher(clause).find())      return ClauseResult.BLOCKED_SUBQUERY;
        if (SQL_COMMENT.matcher(clause).find())   return ClauseResult.BLOCKED_COMMENT;
        if (SEMICOLON.matcher(clause).find())     return ClauseResult.BLOCKED_SEMICOLON;
        if (SET_OPERATIONS.matcher(clause).find()) return ClauseResult.BLOCKED_SET_OPERATION;
        if (SYSTEM_DB.matcher(clause).find())     return ClauseResult.BLOCKED_SYSTEM_DB;

        return ClauseResult.OK;
    }

    public static String describeResult(ClauseResult result) {
        switch (result) {
            case OK:                    return "OK";
            case BLOCKED_SUBQUERY:      return "subqueries (SELECT inside WHERE/SET) are not allowed";
            case BLOCKED_COMMENT:       return "SQL comments (-- or /**/) are not allowed";
            case BLOCKED_SEMICOLON:     return "semicolons (multiple statements) are not allowed";
            case BLOCKED_SET_OPERATION: return "UNION/INTERSECT/EXCEPT are not allowed in WHERE/SET";
            case BLOCKED_SYSTEM_DB:     return "access to system databases in WHERE/SET is not allowed";
            default:                    return "unknown validation error";
        }
    }
}