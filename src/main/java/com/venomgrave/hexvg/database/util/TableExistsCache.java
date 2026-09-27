package com.venomgrave.hexvg.database.util;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class TableExistsCache {

    private static final ConcurrentHashMap<String, Boolean> cache = new ConcurrentHashMap<>();

    public static Boolean get(String tableName) {
        return cache.get(tableName.toLowerCase(Locale.ROOT));
    }

    public static void put(String tableName, boolean exists) {
        cache.put(tableName.toLowerCase(Locale.ROOT), exists);
    }

    public static void invalidate(String tableName) {
        cache.remove(tableName.toLowerCase(Locale.ROOT));
    }

    public static void clear() {
        cache.clear();
    }
}
