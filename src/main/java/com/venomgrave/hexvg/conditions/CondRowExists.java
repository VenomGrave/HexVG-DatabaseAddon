package com.venomgrave.hexvg.conditions;

import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.HexVGDatabaseAddon;
import com.venomgrave.hexvg.util.ClauseValidator;
import com.venomgrave.hexvg.util.DBLogger;
import com.venomgrave.hexvg.util.Messages;
import org.bukkit.event.Event;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * [a] row exist[s] in table %string% where %string% with values %objects%
 * [a] row does(n't| not) exist in table %string% where %string% with values %objects%
 *
 * Example:
 *   if row exists in table "players" where "name = ?" with values "Steve":
 *       send "Gracz istnieje!"
 */
public class CondRowExists extends Condition {

    static {
        HexVGDatabaseAddon.getInstance().getSkriptAddon().syntaxRegistry()
                .register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondRowExists.class)
                        .addPattern("[a] row exist[s] in table %string% where %string% with values %objects%")
                        .addPattern("[a] row does(n't| not) exist in table %string% where %string% with values %objects%")
                        .build());
    }

    private Expression<String> tableName;
    private Expression<String> whereClause;
    private Expression<Object> values;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        tableName = (Expression<String>) exprs[0];
        whereClause = (Expression<String>) exprs[1];
        values = (Expression<Object>) exprs[2];
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event e) {
        String table = tableName.getSingle(e);
        String where = whereClause.getSingle(e);
        if (table == null || where == null) return isNegated();
        if (!isValidIdentifier(table)) {
            HexVGDatabaseAddon.getInstance().getLogger().warning(Messages.INVALID_QUERY.get());
            return isNegated();
        }

        ClauseValidator.ClauseResult cr = ClauseValidator.validate(where);
        if (cr != ClauseValidator.ClauseResult.OK) {
            DBLogger.warning("[HexVG-DB] row exists blocked - invalid WHERE: " + ClauseValidator.describeResult(cr));
            return isNegated();
        }

        String sql = "SELECT 1 FROM `" + table + "` WHERE " + where + " LIMIT 1";
        List<Object> params = new ArrayList<>();
        if (values != null) for (Object v : values.getArray(e)) params.add(v);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean exists = new AtomicBoolean(false);

        HexVGDatabaseAddon.getInstance().getQueryExecutor().executeScalar(
                sql, params,
                val -> { exists.set(val != null); latch.countDown(); },
                error -> latch.countDown()
        );

        int timeout = HexVGDatabaseAddon.getInstance().getConfig().getInt("security.query-timeout-seconds", 10);
        try { latch.await(timeout, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }

        return isNegated() != exists.get();
    }

    @Override
    public String toString(Event e, boolean debug) {
        return "row " + (isNegated() ? "does not exist" : "exists") + " in table " + tableName.toString(e, debug);
    }

    private boolean isValidIdentifier(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]+") && name.length() <= 64;
    }
}