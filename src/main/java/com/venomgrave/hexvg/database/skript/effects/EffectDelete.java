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

public class EffectDelete extends Effect {

    static {
        Skript.registerEffect(EffectDelete.class,
                "db delete from [table] %string% where %string% = %string%");
    }

    private Expression<String> tableExpr;
    private Expression<String> whereColumnExpr;
    private Expression<String> whereValueExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult) {
        tableExpr = (Expression<String>) exprs[0];
        whereColumnExpr = (Expression<String>) exprs[1];
        whereValueExpr = (Expression<String>) exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String table = tableExpr.getSingle(event);
        String whereCol = whereColumnExpr.getSingle(event);
        String whereValue = whereValueExpr.getSingle(event);

        if (table == null || !table.matches("[a-zA-Z0-9_]+")) {
            Skript.warning("[HexVG-DatabaseAddon] Invalid table name for DELETE: " + table);
            return;
        }
        if (whereCol == null || !whereCol.matches("[a-zA-Z0-9_]+")) {
            Skript.warning("[HexVG-DatabaseAddon] Invalid WHERE column for DELETE: " + whereCol);
            return;
        }

        String sql = "DELETE FROM " + table + " WHERE " + whereCol + " = ?";
        String[] params = {whereValue};

        UUID uuid = null;
        if (event instanceof PlayerEvent) {
            Player p = ((PlayerEvent) event).getPlayer();
            if (p != null) uuid = p.getUniqueId();
        }

        final UUID finalUuid = uuid;

        HexVGAddon.getInstance().getQueryExecutor().executeAsync(sql, params, finalUuid,
                (result, error) -> {
                    if (error != null) {
                        Skript.warning("[HexVG-DatabaseAddon] DELETE failed: " + error.getMessage());
                        return;
                    }
                    HexVGAddon.getInstance().getResultCache().store(finalUuid, result);
                });
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "db delete from table " + tableExpr.toString(event, debug);
    }
}
