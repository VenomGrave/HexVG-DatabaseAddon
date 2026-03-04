package com.venomgrave.hexvg.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.venomgrave.hexvg.HexVGDatabaseAddon;
import com.venomgrave.hexvg.util.DBLogger;
import com.venomgrave.hexvg.util.Messages;
import com.venomgrave.hexvg.util.PermissionChecker;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.concurrent.atomic.AtomicBoolean;

public class EffectMigrate extends Effect {

    private static final AtomicBoolean MIGRATION_RUNNING = new AtomicBoolean(false);

    static {
        HexVGDatabaseAddon.getInstance().getSkriptAddon().syntaxRegistry()
                .register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffectMigrate.class)
                        .addPattern("migrate storage to %string%")
                        .build());
    }

    private Expression<String> targetType;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        targetType = (Expression<String>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event e) {
        // Migracja jest operacja krytyczna — wymaga hexvg.admin, nie tylko hexvg.database.use
        if (!PermissionChecker.hasPermission(e, PermissionChecker.PERM_ADMIN)) {
            DBLogger.warning("[HexVG-DB] migrate storage blocked - missing permission hexvg.admin");
            return;
        }

        // Dodatkowe zabezpieczenie: migracja zablokowana gdy Core Bridge aktywny.
        // Migracja w trybie Core jest niemozliwa bo polaczenie nalezy do Core.
        if (HexVGDatabaseAddon.getInstance().getDatabaseManager().isCoreBridgeActive()) {
            DBLogger.warning("[HexVG-DB] migrate storage blocked - database is managed by HexVG-Core. "
                    + "Perform migration via HexVG-Core commands.");
            return;
        }

        String target = targetType.getSingle(e);
        if (target == null) return;

        target = target.toLowerCase();
        if (!target.equals("mysql") && !target.equals("sqlite")) {
            DBLogger.warning("[HexVG-DB] migrate storage: invalid target '" + target + "'. Use 'mysql' or 'sqlite'.");
            return;
        }

        String currentType = HexVGDatabaseAddon.getInstance()
                .getDatabaseManager().getDatabaseType("default").toLowerCase();
        if (currentType.equals(target)) {
            DBLogger.warning(Messages.MIGRATION_SAME_TYPE.get(currentType, target));
            return;
        }

        if (!MIGRATION_RUNNING.compareAndSet(false, true)) {
            DBLogger.warning(Messages.MIGRATION_ALREADY_RUNNING.get());
            return;
        }

        final String finalTarget = target;
        HexVGDatabaseAddon.getInstance().getMigrationManager().migrate(
                "default", finalTarget,
                result -> {
                    MIGRATION_RUNNING.set(false);
                    String msg = Messages.getPrefix() + Messages.MIGRATION_COMPLETE.get(
                            result.getMigratedTables().size(),
                            result.getTotalRowsMigrated(),
                            result.getFailedTables().size()
                    );
                    Bukkit.getScheduler().runTask(HexVGDatabaseAddon.getInstance(), () ->
                            Bukkit.getOnlinePlayers().stream()
                                    .filter(p -> p.hasPermission("hexvg.admin"))
                                    .forEach(p -> p.sendMessage(msg))
                    );
                },
                error -> {
                    MIGRATION_RUNNING.set(false);
                    DBLogger.severe(Messages.MIGRATION_FAILED.get(error));
                }
        );
    }

    @Override
    public String toString(Event e, boolean debug) {
        return "migrate storage to " + targetType.toString(e, debug);
    }
}