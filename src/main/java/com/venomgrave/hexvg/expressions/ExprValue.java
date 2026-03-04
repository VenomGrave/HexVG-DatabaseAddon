package com.venomgrave.hexvg.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * value of %string% from table %string% where %string% with values %objects%
 * value of %string% from table %string%
 *
 * Example:
 *   set {hp} to value of "health" from table "players" where "name = ?" with values "Steve"
 */
public class ExprValue extends SimpleExpression<String> {

    static {
        HexVGDatabaseAddon.getInstance().getSkriptAddon().syntaxRegistry()
                .register(SyntaxRegistry.EXPRESSION, SyntaxInfo.Expression.builder(ExprValue.class, String.class)
                        .addPattern("value of %string% from table %string% where %string% with values %objects%")
                        .addPattern("value of %string% from table %string%")
                        .build());
    }

    private Expression<String> columnName;
    private Expression<String> tableName;
    private Expression<String> whereClause;
    private Expression<Object> values;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        columnName = (Expression<String>) exprs[0];
        tableName = (Expression<String>) exprs[1];
        if (matchedPattern == 0) {
            whereClause = (Expression<String>) exprs[2];
            values = (Expression<Object>) exprs[3];
        }
        return true;
    }

    @Override
    protected String[] get(Event e) {
        String column = columnName.getSingle(e);
        String table = tableName.getSingle(e);
        if (column == null || table == null) return new String[0];
        if (!isValidIdentifier(table) || !isValidIdentifier(column)) {
            HexVGDatabaseAddon.getInstance().getLogger().warning(Messages.INVALID_QUERY.get());
            return new String[0];
        }

        StringBuilder sql = new StringBuilder("SELECT `").append(column)
                .append("` FROM `").append(table).append("`");
        List<Object> params = new ArrayList<>();
        if (whereClause != null) {
            String where = whereClause.getSingle(e);
            if (where != null) {
                ClauseValidator.ClauseResult cr = ClauseValidator.validate(where);
                if (cr != ClauseValidator.ClauseResult.OK) {
                    DBLogger.warning("[HexVG-DB] value blocked - invalid WHERE: " + ClauseValidator.describeResult(cr));
                    return new String[0];
                }
                sql.append(" WHERE ").append(where);
            }
            if (values != null) for (Object v : values.getArray(e)) params.add(v);
        }
        sql.append(" LIMIT 1");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> result = new AtomicReference<>();

        HexVGDatabaseAddon.getInstance().getQueryExecutor().executeScalar(
                sql.toString(), params,
                val -> { result.set(val); latch.countDown(); },
                error -> latch.countDown()
        );

        int timeout = HexVGDatabaseAddon.getInstance().getConfig().getInt("security.query-timeout-seconds", 10);
        try { latch.await(timeout, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }

        Object val = result.get();
        return val == null ? new String[0] : new String[]{String.valueOf(val)};
    }

    @Override public boolean isSingle() { return true; }
    @Override public Class<? extends String> getReturnType() { return String.class; }
    @Override public String toString(Event e, boolean debug) {
        return "value of " + columnName.toString(e, debug) + " from table " + tableName.toString(e, debug);
    }

    private boolean isValidIdentifier(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]+") && name.length() <= 64;
    }
}