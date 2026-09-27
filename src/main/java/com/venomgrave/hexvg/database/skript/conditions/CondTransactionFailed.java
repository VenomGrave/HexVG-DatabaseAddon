package com.venomgrave.hexvg.database.skript.conditions;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.skript.SkriptEvents;
import com.venomgrave.hexvg.database.util.TransactionStateCache;
import org.bukkit.event.Event;

import java.util.UUID;

public class CondTransactionFailed extends Condition {

    static {
        Skript.registerCondition(CondTransactionFailed.class,
                "last db transaction failed",
                "last db transaction succeeded");
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        UUID uuid = SkriptEvents.playerUuid(event);

        boolean failed = TransactionStateCache.hasFailed(uuid);
        return isNegated() != failed;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return isNegated() ? "last db transaction succeeded" : "last db transaction failed";
    }
}
