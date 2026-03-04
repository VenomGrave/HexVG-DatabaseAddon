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
 * delete from table %string% where %string% with values %objects%
 * delete from table %string%
 *
 * Example:
 *   delete from table "players" where "name = ?" with values "Steve"
 */
public class EffectDelete extends Effect {

    static {
        HexVGDatabaseAddon.getInstance().getSkriptAddon().syntaxRegistry()
                .register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffectDelete.class)
                        .addPattern("delete from table %string% where %string% with values %objects%")
                        .addPattern("delete from table %string%")
                        .build());
    }

    private Expression<String> tableName;
    private Expression<String> whereClause;
    private Expression<Object> values;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        tableName = (Expression<String>) exprs[0];
        if (matchedPattern == 0) {
            whereClause = (Expression<String>) exprs[1];
            values = (Expression<Object>) exprs[2];
        }
        return true;
    }

    @Override
    protected void execute(Event e) {
        String table = tableName.getSingle(e);
        if (table == null) return;
        if (!isValidIdentifier(table)) {
            HexVGDatabaseAddon.getInstance().getLogger().warning(Messages.INVALID_QUERY.get());
            return;
        }

        StringBuilder sql = new StringBuilder("DELETE FROM `").append(table).append("`");
        List<Object> params = new ArrayList<>();

        if (whereClause != null) {
            String where = whereClause.getSingle(e);
            if (where != null) {
                ClauseValidator.ClauseResult cr = ClauseValidator.validate(where);
                if (cr != ClauseValidator.ClauseResult.OK) {
                    HexVGDatabaseAddon.getInstance().getLogger().warning(
                            "[HexVG-DB] delete blocked - invalid WHERE: " + ClauseValidator.describeResult(cr));
                    return;
                }
                sql.append(" WHERE ").append(where);
            }
            if (values != null) for (Object v : values.getArray(e)) params.add(v);
        }

        HexVGDatabaseAddon.getInstance().getQueryExecutor().executeUpdate(
                sql.toString(), params, affected -> {}, error -> {}
        );
    }

    @Override
    public String toString(Event e, boolean debug) {
        return "delete from table " + tableName.toString(e, debug);
    }

    private boolean isValidIdentifier(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]+") && name.length() <= 64;
    }
}