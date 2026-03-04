package com.venomgrave.hexvg.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.HexVGDatabaseAddon;
import com.venomgrave.hexvg.util.DBLogger;
import com.venomgrave.hexvg.util.PermissionChecker;
import org.bukkit.event.Event;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;

public class EffectExecute extends Effect {

    static {
        HexVGDatabaseAddon.getInstance().getSkriptAddon().syntaxRegistry()
                .register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffectExecute.class)
                        .addPattern("execute [sql] query %string%")
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
    protected void execute(Event e) {
        if (!PermissionChecker.canUseRawSql(e)) {
            DBLogger.warning("[HexVG-DB] execute query blocked - missing permission hexvg.database.raw");
            return;
        }

        String sql = query.getSingle(e);
        if (sql == null || sql.isEmpty()) return;

        String blocked = HexVGDatabaseAddon.getInstance().getQueryExecutor().validateRawSql(sql);
        if (blocked != null) {
            DBLogger.warning("[HexVG-DB] execute query blocked: " + blocked);
            return;
        }

        HexVGDatabaseAddon.getInstance().getQueryExecutor().executeUpdate(
                sql, new ArrayList<>(),
                affected -> {},
                error -> DBLogger.severe("[HexVG-DB] execute query error: " + error)
        );
    }

    @Override
    public String toString(Event e, boolean debug) {
        return "execute query " + query.toString(e, debug);
    }
}