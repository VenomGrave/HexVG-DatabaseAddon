package com.venomgrave.hexvg.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * rows from table %string% where %string% with values %objects%
 * rows from table %string%
 *
 * Example:
 *   set {rows::*} to rows from table "players" where "name = ?" with values "Steve"
 */
public class ExprRows extends SimpleExpression<String> {

    static {
        HexVGDatabaseAddon.getInstance().getSkriptAddon().syntaxRegistry()
                .register(SyntaxRegistry.EXPRESSION, SyntaxInfo.Expression.builder(ExprRows.class, String.class)
                        .addPattern("rows from table %string% where %string% with values %objects%")
                        .addPattern("rows from table %string%")
                        .build());
    }

    private Expression<String> tableName;
    private Expression<String> whereClause;
    private Expression<Object> values;
    private int pattern;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.pattern = matchedPattern;
        tableName = (Expression<String>) exprs[0];
        if (matchedPattern == 0) {
            whereClause = (Expression<String>) exprs[1];
            values = (Expression<Object>) exprs[2];
        }
        return true;
    }

    @Override
    protected String[] get(Event e) {
        String table = tableName.getSingle(e);
        if (table == null) return new String[0];
        if (!isValidIdentifier(table)) {
            HexVGDatabaseAddon.getInstance().getLogger().warning(Messages.INVALID_QUERY.get());
            return new String[0];
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM `").append(table).append("`");
        List<Object> params = new ArrayList<>();
        if (whereClause != null) {
            String where = whereClause.getSingle(e);
            if (where != null) {
                ClauseValidator.ClauseResult cr = ClauseValidator.validate(where);
                if (cr != ClauseValidator.ClauseResult.OK) {
                    DBLogger.warning("[HexVG-DB] rows blocked - invalid WHERE: " + ClauseValidator.describeResult(cr));
                    return new String[0];
                }
                sql.append(" WHERE ").append(where);
            }
            if (values != null) for (Object v : values.getArray(e)) params.add(v);
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Map<String, Object>>> result = new AtomicReference<>();

        HexVGDatabaseAddon.getInstance().getQueryExecutor().executeQuery(
                sql.toString(), params,
                rows -> { result.set(rows); latch.countDown(); },
                error -> latch.countDown()
        );

        int timeout = HexVGDatabaseAddon.getInstance().getConfig().getInt("security.query-timeout-seconds", 10);
        try { latch.await(timeout, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }

        List<Map<String, Object>> rows = result.get();
        if (rows == null || rows.isEmpty()) return new String[0];

        List<String> output = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            StringBuilder rowStr = new StringBuilder();
            for (Map.Entry<String, Object> col : row.entrySet()) {
                if (rowStr.length() > 0) rowStr.append(",");
                rowStr.append(col.getKey()).append("=").append(col.getValue());
            }
            output.add(rowStr.toString());
        }
        return output.toArray(new String[0]);
    }

    @Override public boolean isSingle() { return false; }
    @Override public Class<? extends String> getReturnType() { return String.class; }
    @Override public String toString(Event e, boolean debug) {
        return "rows from table " + tableName.toString(e, debug);
    }

    private boolean isValidIdentifier(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]+") && name.length() <= 64;
    }
}