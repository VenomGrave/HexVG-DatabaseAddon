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

import java.util.UUID;

public class EffectUpdate extends Effect {

    static {
        Skript.registerEffect(EffectUpdate.class,
                "db update [table] %string% set %string% to %string% where %string% = %string%");
    }

    private Expression<String> tableExpr;
    private Expression<String> setColumnExpr;
    private Expression<String> setValueExpr;
    private Expression<String> whereColumnExpr;
    private Expression<String> whereValueExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        tableExpr = (Expression<String>) exprs[0];
        setColumnExpr = (Expression<String>) exprs[1];
        setValueExpr = (Expression<String>) exprs[2];
        whereColumnExpr = (Expression<String>) exprs[3];
        whereValueExpr = (Expression<String>) exprs[4];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String table = tableExpr.getSingle(event);
        String setCol = setColumnExpr.getSingle(event);
        String setValue = setValueExpr.getSingle(event);
        String whereCol = whereColumnExpr.getSingle(event);
        String whereValue = whereValueExpr.getSingle(event);

        if (table == null || !table.matches("[a-zA-Z0-9_]+")) {
            Skript.warning("[HexVG-DatabaseAddon] Invalid table name for UPDATE: " + table);
            return;
        }
        if (setCol == null || !setCol.matches("[a-zA-Z0-9_]+")) {
            Skript.warning("[HexVG-DatabaseAddon] Invalid SET column for UPDATE: " + setCol);
            return;
        }
        if (whereCol == null || !whereCol.matches("[a-zA-Z0-9_]+")) {
            Skript.warning("[HexVG-DatabaseAddon] Invalid WHERE column for UPDATE: " + whereCol);
            return;
        }

        String sql = "UPDATE " + table + " SET " + setCol + " = ? WHERE " + whereCol + " = ?";
        String[] params = {setValue, whereValue};

        UUID uuid = null;
        if (event instanceof PlayerEvent) {
            Player p = ((PlayerEvent) event).getPlayer();
            if (p != null) uuid = p.getUniqueId();
        }

        final UUID finalUuid = uuid;

        HexVGAddon.getInstance().getQueryExecutor().executeAsync(sql, params, finalUuid,
                (result, error) -> {
                    if (error != null) {
                        Skript.warning("[HexVG-DatabaseAddon] UPDATE failed: " + error.getMessage());
                        return;
                    }
                    HexVGAddon.getInstance().getResultCache().store(finalUuid, result);
                });
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db update table " + tableExpr.toString(event, debug);
    }
}
