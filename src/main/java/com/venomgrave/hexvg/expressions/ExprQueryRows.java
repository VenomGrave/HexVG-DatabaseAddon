package com.venomgrave.hexvg.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.HexVGDatabaseAddon;
import com.venomgrave.hexvg.util.DBLogger;
import com.venomgrave.hexvg.util.PermissionChecker;
import org.bukkit.event.Event;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ExprQueryRows extends SimpleExpression<String> {

    static {
        HexVGDatabaseAddon.getInstance().getSkriptAddon().syntaxRegistry()
                .register(SyntaxRegistry.EXPRESSION, SyntaxInfo.Expression.builder(ExprQueryRows.class, String.class)
                        .addPattern("query rows %string%")
                        .build());
    }

    private Expression<String> query;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        query = (Expression<String>) exprs[0];
        return true;
    }

    @Override
    protected String[] get(Event e) {
        if (!PermissionChecker.canUseRawSql(e)) {
            DBLogger.warning("[HexVG-DB] query rows blocked - missing permission hexvg.database.raw");
            return new String[0];
        }

        String sql = query.getSingle(e);
        if (sql == null || sql.isEmpty()) return new String[0];

        String blocked = HexVGDatabaseAddon.getInstance().getQueryExecutor().validateRawSql(sql);
        if (blocked != null) {
            DBLogger.warning("[HexVG-DB] query rows blocked: " + blocked);
            return new String[0];
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Map<String, Object>>> result = new AtomicReference<>();

        HexVGDatabaseAddon.getInstance().getQueryExecutor().executeQuery(
                sql, new ArrayList<>(),
                rows -> { result.set(rows); latch.countDown(); },
                error -> latch.countDown()
        );

        try { latch.await(10, TimeUnit.SECONDS); }
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
    @Override public String toString(Event e, boolean debug) { return "query rows " + query.toString(e, debug); }
}