package com.venomgrave.hexvg.database;

import com.venomgrave.hexvg.util.DBLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cache dla wyników zapytań SELECT.
 * Klucz = SQL + parametry, wartość = wynik + timestamp wygaśnięcia.
 * Automatycznie czyści wygasłe wpisy co 60 sekund.
 */
public class QueryCache {

    private static final long DEFAULT_TTL_MS = 5_000; // 5 sekund domyślnie

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HexVG-CacheCleaner");
        t.setDaemon(true);
        return t;
    });

    private long ttlMs;
    private boolean enabled;

    public QueryCache(boolean enabled, long ttlMs) {
        this.enabled = enabled;
        this.ttlMs = ttlMs > 0 ? ttlMs : DEFAULT_TTL_MS;

        // Co 60 sekund usuwa wygasłe wpisy
        cleaner.scheduleAtFixedRate(this::evictExpired, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Generuje klucz cache z SQL i parametrów.
     */
    public String buildKey(String sql, List<Object> params) {
        return sql + "|" + (params != null ? params.toString() : "[]");
    }

    /**
     * Zwraca wynik z cache lub null jeśli brak / wygasł.
     */
    public List<Map<String, Object>> get(String key) {
        if (!enabled) return null;
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(key);
            return null;
        }
        DBLogger.debug("Cache HIT: " + key.substring(0, Math.min(60, key.length())));
        return entry.rows;
    }

    /**
     * Zapisuje wynik do cache.
     */
    public void put(String key, List<Map<String, Object>> rows) {
        if (!enabled) return;
        cache.put(key, new CacheEntry(rows, System.currentTimeMillis() + ttlMs));
        DBLogger.debug("Cache PUT: " + key.substring(0, Math.min(60, key.length())));
    }

    /**
     * Invaliduje wszystkie wpisy zawierające nazwę tabeli.
     * Wywoływane po INSERT/UPDATE/DELETE na danej tabeli.
     */
    public void invalidateTable(String tableName) {
        if (!enabled) return;
        int removed = 0;
        for (String key : new ArrayList<>(cache.keySet())) {
            if (key.toLowerCase().contains(tableName.toLowerCase())) {
                cache.remove(key);
                removed++;
            }
        }
        if (removed > 0) {
            DBLogger.debug("Cache INVALIDATE table=" + tableName + " removed=" + removed);
        }
    }

    /**
     * Czyści cały cache.
     */
    public void invalidateAll() {
        int size = cache.size();
        cache.clear();
        DBLogger.debug("Cache CLEAR all entries=" + size);
    }

    /**
     * Usuwa wygasłe wpisy.
     */
    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> now > e.getValue().expiresAt);
    }

    public void shutdown() {
        cleaner.shutdown();
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTtlMs(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    private static class CacheEntry {
        final List<Map<String, Object>> rows;
        final long expiresAt;

        CacheEntry(List<Map<String, Object>> rows, long expiresAt) {
            this.rows = rows;
            this.expiresAt = expiresAt;
        }
    }
}