package com.venomgrave.hexvg.database.util;

import com.venomgrave.hexvg.database.database.QueryResult;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResultCache {

    private static final int MAX_ENTRIES = 1000;

    private final ConcurrentHashMap<UUID, QueryResult> playerCache = new ConcurrentHashMap<>();
    private volatile QueryResult globalResult = QueryResult.empty();

    public void store(UUID uuid, QueryResult result) {
        if (uuid == null) {
            globalResult = result;
            return;
        }
        if (playerCache.size() >= MAX_ENTRIES && !playerCache.containsKey(uuid)) {
            return;
        }
        playerCache.put(uuid, result);
    }

    public QueryResult get(UUID uuid) {
        if (uuid != null && playerCache.containsKey(uuid)) {
            return playerCache.get(uuid);
        }
        return globalResult;
    }

    public void invalidate(UUID uuid) {
        if (uuid != null) {
            playerCache.remove(uuid);
        }
    }

    public void clear() {
        playerCache.clear();
        globalResult = QueryResult.empty();
    }
}
