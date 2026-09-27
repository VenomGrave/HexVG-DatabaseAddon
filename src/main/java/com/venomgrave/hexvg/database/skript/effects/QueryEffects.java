package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import com.venomgrave.hexvg.database.HexVGAddon;
import com.venomgrave.hexvg.database.database.QueryResult;
import com.venomgrave.hexvg.database.skript.SkriptEvents;
import org.bukkit.event.Event;

import java.util.UUID;

/** Shared body of the query effects (execute/insert/update/delete). */
final class QueryEffects {

    private QueryEffects() {}

    /**
     * Runs the query, blocking until it finishes (so any surrounding transaction sees the
     * write before the next effect), and stores the result as the event's
     * "last db query result". On failure an EMPTY result is stored — never keep the
     * previous one, or the script would act on stale data.
     */
    static void runAndStore(Event event, String sql, String[] params, String label, String errorSuffix) {
        HexVGAddon addon = HexVGAddon.getInstance();
        if (addon == null || addon.getQueryExecutor() == null) return;

        UUID uuid = SkriptEvents.playerUuid(event);
        QueryResult result;
        try {
            result = addon.getQueryExecutor().executeBlocking(sql, params, uuid);
        } catch (Exception e) {
            Skript.warning("[HexVG-DatabaseAddon] " + label + " failed: " + e.getMessage()
                    + (errorSuffix != null ? errorSuffix : ""));
            result = QueryResult.empty();
        }
        addon.getResultCache().store(uuid, result);
    }

    /**
     * Invalid input: warn, clear the event's last result (same reason as above) and roll
     * back an open transaction — a skipped statement must not let commit save the rest.
     */
    static void fail(Event event, String message) {
        Skript.warning(message);
        HexVGAddon addon = HexVGAddon.getInstance();
        if (addon == null || addon.getQueryExecutor() == null) return;
        UUID uuid = SkriptEvents.playerUuid(event);
        addon.getResultCache().store(uuid, QueryResult.empty());
        addon.getQueryExecutor().abortTransactionAsync(uuid, "Invalid statement: " + message);
    }
}
