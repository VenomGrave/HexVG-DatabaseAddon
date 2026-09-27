package com.venomgrave.hexvg.database.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple lock to prevent players from running the same command
 * multiple times before the previous execution finishes.
 *
 * Locks expire after {@link #LOCK_TIMEOUT_MS}, so a script that stops before
 * {@code db unlock} (error, forgotten unlock) can't lock a player out forever.
 */
public class CommandCooldown {

    public static final long LOCK_TIMEOUT_MS = 30_000;

    private final ConcurrentHashMap<UUID, Long> locked = new ConcurrentHashMap<>();
    private static final UUID GLOBAL = new UUID(0, 0);

    public boolean tryLock(UUID uuid) {
        UUID key = uuid != null ? uuid : GLOBAL;
        long now = System.currentTimeMillis();
        boolean[] acquired = {false};
        locked.compute(key, (k, since) -> {
            if (since == null || now - since >= LOCK_TIMEOUT_MS) {
                acquired[0] = true;
                return now;
            }
            return since;
        });
        return acquired[0];
    }

    public void unlock(UUID uuid) {
        UUID key = uuid != null ? uuid : GLOBAL;
        locked.remove(key);
    }

    public boolean isLocked(UUID uuid) {
        UUID key = uuid != null ? uuid : GLOBAL;
        Long since = locked.get(key);
        if (since == null) return false;
        if (System.currentTimeMillis() - since >= LOCK_TIMEOUT_MS) {
            locked.remove(key, since);
            return false;
        }
        return true;
    }
}
