package com.venomgrave.hexvg.database.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores placeholder values per player per key.
 * Updated by Skript scripts via db set placeholder effect.
 * Read synchronously by PlaceholderAPI on the main thread.
 */
public class PlaceholderCache {

    // Map<playerUUID, Map<placeholderKey, value>>
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, String>> cache = new ConcurrentHashMap<>();

    public void set(UUID uuid, String key, String value) {
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public String get(UUID uuid, String key) {
        ConcurrentHashMap<String, String> playerCache = cache.get(uuid);
        if (playerCache == null) return "";
        return playerCache.getOrDefault(key, "");
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    public void clear() {
        cache.clear();
    }
}
