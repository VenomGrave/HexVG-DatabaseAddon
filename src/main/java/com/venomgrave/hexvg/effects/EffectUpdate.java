package com.venomgrave.hexvg.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.HexVGDatabaseAddon;
import com.venomgrave.hexvg.util.ClauseValidator;
import com.venomgrave.hexvg.util.Messages;
import org.bukkit.event.Event;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * update table %string% set %string% where %string% with values %objects%
 * update table %string% set %string% with values %objects%
 *
 * Example:
 *   update table "players" set "health = ?" where "name = ?" with values 50, "Steve"
 */
public class EffectUpdate extends Effect {

    static {
        HexVGDatabaseAddon.getInstance().getSkriptAddon().syntaxRegistry()
                .register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffectUpdate.class)
                        .addPattern("update table %string% set %string% where %string% with values %objects%")
                        .addPattern("update table %string% set %string% with values %objects%")
                        .build());
    }

    private Expression<String> tableName;
    private Expression<String> setClause;
    private Expression<String> whereClause;
    private Expression<Object> values;
    private int pattern;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.pattern = matchedPattern;
        tableName = (Expression<String>) exprs[0];
        setClause = (Expression<String>) exprs[1];
        if (matchedPattern == 0) {
            whereClause = (Expression<String>) exprs[2];
            values = (Expression<Object>) exprs[3];
        } else {
            whereClause = null;
            values = (Expression<Object>) exprs[2];
        }
        return true;
    }

    @Override
    protected void execute(Event e) {
        String table = tableName.getSingle(e);
        String set = setClause.getSingle(e);
        if (table == null || set == null) return;
        if (!isValidIdentifier(table)) {
            HexVGDatabaseAddon.getInstance().getLogger().warning(Messages.INVALID_QUERY.get());
            return;
        }

        // Waliduj SET clause
        ClauseValidator.ClauseResult setCr = ClauseValidator.validate(set);
        if (setCr != ClauseValidator.ClauseResult.OK) {
            HexVGDatabaseAddon.getInstance().getLogger().warning(
                    "[HexVG-DB] update blocked - invalid SET: " + ClauseValidator.describeResult(setCr));
            return;
        }

        StringBuilder sql = new StringBuilder("UPDATE `").append(table).append("` SET ").append(set);
        if (whereClause != null) {
            String where = whereClause.getSingle(e);
            if (where != null) {
                ClauseValidator.ClauseResult whereCr = ClauseValidator.validate(where);
                if (whereCr != ClauseValidator.ClauseResult.OK) {
                    HexVGDatabaseAddon.getInstance().getLogger().warning(
                            "[HexVG-DB] update blocked - invalid WHERE: " + ClauseValidator.describeResult(whereCr));
                    return;
                }
                sql.append(" WHERE ").append(where);
            }
        }

        List<Object> params = new ArrayList<>();
        if (values != null) for (Object v : values.getArray(e)) params.add(v);

        HexVGDatabaseAddon.getInstance().getQueryExecutor().executeUpdate(
                sql.toString(), params, affected -> {}, error -> {}
        );
    }

    @Override
    public String toString(Event e, boolean debug) {
        return "update table " + tableName.toString(e, debug);
    }

    private boolean isValidIdentifier(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]+") && name.length() <= 64;
    }
}