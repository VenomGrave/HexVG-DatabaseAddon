package com.venomgrave.hexvg.database.util;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple lock to prevent players from running the same command
 * multiple times before the previous execution finishes.
 */
public class CommandCooldown {

    private final Set<UUID> locked = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final UUID GLOBAL = new UUID(0, 0);

    public boolean tryLock(UUID uuid) {
        UUID key = uuid != null ? uuid : GLOBAL;
        return locked.add(key);
    }

    public void unlock(UUID uuid) {
        UUID key = uuid != null ? uuid : GLOBAL;
        locked.remove(key);
    }

    public boolean isLocked(UUID uuid) {
        UUID key = uuid != null ? uuid : GLOBAL;
        return locked.contains(key);
    }
}
