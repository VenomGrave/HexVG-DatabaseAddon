package com.venomgrave.hexvg.database.util;

import java.util.regex.Pattern;

/**
 * Validation of table/column names that are concatenated into SQL.
 * 64 = MySQL identifier length limit.
 */
public final class SqlIdentifiers {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,64}");

    private SqlIdentifiers() {}

    public static boolean isValid(String name) {
        return name != null && IDENTIFIER.matcher(name).matches();
    }
}
