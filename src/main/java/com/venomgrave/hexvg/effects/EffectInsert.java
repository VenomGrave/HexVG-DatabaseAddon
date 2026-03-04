package com.venomgrave.hexvg.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.HexVGDatabaseAddon;
import com.venomgrave.hexvg.util.Messages;
import org.bukkit.event.Event;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * insert into table %string% values %objects%
 *
 * Example:
 *   insert into table "players" values "Steve", 100
 */
public class EffectInsert extends Effect {

    static {
        HexVGDatabaseAddon.getInstance().getSkriptAddon().syntaxRegistry()
                .register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffectInsert.class)
                        .addPattern("insert into table %string% values %objects%")
                        .build());
    }

    private Expression<String> tableName;
    private Expression<Object> values;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        tableName = (Expression<String>) exprs[0];
        values = (Expression<Object>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event e) {
        String table = tableName.getSingle(e);
        Object[] vals = values.getArray(e);
        if (table == null || vals == null || vals.length == 0) return;
        if (!isValidIdentifier(table)) {
            HexVGDatabaseAddon.getInstance().getLogger().warning(Messages.INVALID_QUERY.get());
            return;
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < vals.length; i++) placeholders.append(i == 0 ? "?" : ",?");

        String sql = "INSERT INTO `" + table + "` VALUES (" + placeholders + ")";
        HexVGDatabaseAddon.getInstance().getQueryExecutor().executeUpdate(
                sql, new ArrayList<>(Arrays.asList(vals)), affected -> {}, error -> {}
        );
    }

    @Override
    public String toString(Event e, boolean debug) {
        return "insert into table " + tableName.toString(e, debug) + " values " + values.toString(e, debug);
    }

    private boolean isValidIdentifier(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]+") && name.length() <= 64;
    }
}