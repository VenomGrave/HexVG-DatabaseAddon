package com.venomgrave.hexvg.database.util;

import com.venomgrave.hexvg.database.database.QueryResult;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResultCache {

    private static final int MAX_ENTRIES = 1000;

    private final ConcurrentHashMap<UUID, QueryResult> playerCache = new ConcurrentHashMap<>();
    private volatile QueryResult globalResult = QueryResult.empty();

    public void store(UUID uuid, QueryResult result) {
        if (result == null) result = QueryResult.empty();
        if (uuid == null) {
            globalResult = result;
            return;
        }
        if (playerCache.size() >= MAX_ENTRIES && !playerCache.containsKey(uuid)) {
            // Evict instead of refusing — a full cache must not make every new
            // player read an empty/stale result.
            Iterator<UUID> it = playerCache.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        playerCache.put(uuid, result);
    }

    /**
     * A player without a stored result gets an empty one — never the global slot,
     * which may hold data queried in another context.
     */
    public QueryResult get(UUID uuid) {
        if (uuid == null) return globalResult;
        return playerCache.getOrDefault(uuid, QueryResult.empty());
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
