package com.venomgrave.hexvg.database.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Stores whether the last transaction for a given player was rolled back.
 * Reset on each new db begin transaction.
 */
public class TransactionStateCache {

    private static final ConcurrentHashMap<UUID, Boolean> failed = new ConcurrentHashMap<>();
    private static final UUID GLOBAL = new UUID(0, 0);

    public static void setFailed(UUID uuid, boolean value) {
        failed.put(uuid != null ? uuid : GLOBAL, value);
    }

    public static boolean hasFailed(UUID uuid) {
        Boolean val = failed.get(uuid != null ? uuid : GLOBAL);
        return val != null && val;
    }

    public static void clear(UUID uuid) {
        failed.remove(uuid != null ? uuid : GLOBAL);
    }
}
