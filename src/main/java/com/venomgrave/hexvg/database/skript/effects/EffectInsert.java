package com.venomgrave.hexvg.database.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.database.HexVGAddon;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerEvent;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public class EffectInsert extends Effect {

    static {
        Skript.registerEffect(EffectInsert.class,
                "db insert into [table] %string% columns %strings% values %strings%");
    }

    private Expression<String> tableExpr;
    private Expression<String> columnsExpr;
    private Expression<String> valuesExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        tableExpr = (Expression<String>) exprs[0];
        columnsExpr = (Expression<String>) exprs[1];
        valuesExpr = (Expression<String>) exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String table = tableExpr.getSingle(event);
        if (table == null || !table.matches("[a-zA-Z0-9_]+")) {
            Skript.warning("[HexVG-DatabaseAddon] Invalid table name for INSERT: " + table);
            return;
        }

        String[] columns = columnsExpr.getAll(event);
        String[] values = valuesExpr.getAll(event);

        if (columns == null || columns.length == 0) {
            Skript.warning("[HexVG-DatabaseAddon] INSERT requires at least one column.");
            return;
        }
        if (values == null || values.length == 0) {
            Skript.warning("[HexVG-DatabaseAddon] INSERT requires at least one value.");
            return;
        }
        if (columns.length != values.length) {
            Skript.warning("[HexVG-DatabaseAddon] INSERT column count (" + columns.length
                    + ") != value count (" + values.length + ").");
            return;
        }
        for (String col : columns) {
            if (!col.matches("[a-zA-Z0-9_]+")) {
                Skript.warning("[HexVG-DatabaseAddon] Invalid column name: " + col);
                return;
            }
        }

        String columnList = String.join(", ", columns);
        String placeholders = Arrays.stream(values).map(v -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")";

        UUID uuid = null;
        if (event instanceof PlayerEvent) {
            Player p = ((PlayerEvent) event).getPlayer();
            if (p != null) uuid = p.getUniqueId();
        }

        final UUID finalUuid = uuid;

        HexVGAddon.getInstance().getQueryExecutor().executeAsync(sql, values, finalUuid,
                (result, error) -> {
                    if (error != null) {
                        Skript.warning("[HexVG-DatabaseAddon] INSERT failed: " + error.getMessage());
                        return;
                    }
                    HexVGAddon.getInstance().getResultCache().store(finalUuid, result);
                });
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db insert into table " + tableExpr.toString(event, debug);
    }
}
