package com.venomgrave.hexvg.database;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Most miedzy HexVG-DatabaseAddon a HexVG-Core.
 *
 * Dziala w pelni przez refleksje — brak hard dependency na Core.
 * DatabaseAddon kompiluje sie i dziala bez Core na classpath.
 */
public class CoreBridge {

    private static final String CORE_PLUGIN      = "HexVG-Core";
    private static final String CORE_CLASS       = "com.venomgrave.hexvg.HexVGCore";
    private static final String EXPECTED_PACKAGE = "com.venomgrave.hexvg";

    private final Logger logger;

    // Trzymamy jako Object — nie importujemy DatabaseService z Core
    private Object  coreDbService = null;
    private Method  methodGetConnection;
    private Method  methodIsConnected;
    private Method  methodGetType;
    private boolean bridgeActive  = false;

    public CoreBridge(Logger logger) {
        this.logger = logger;
    }

    // ── Inicjalizacja ─────────────────────────────────────────────────────

    public boolean tryConnect() {
        Plugin corePlugin = Bukkit.getPluginManager().getPlugin(CORE_PLUGIN);

        if (corePlugin == null || !corePlugin.isEnabled()) {
            logger.info("[HexVG-DB] HexVG-Core nie znaleziony — tryb standalone.");
            return false;
        }

        // Weryfikacja tożsamości — klasa główna musi byc z oczekiwanego package
        String mainClass = corePlugin.getDescription().getMain();
        if (!mainClass.startsWith(EXPECTED_PACKAGE)) {
            logger.severe("[HexVG-DB] BEZPIECZENSTWO: Plugin o nazwie HexVG-Core ma podejrzana klase glowna: "
                    + mainClass + " (oczekiwano package " + EXPECTED_PACKAGE + ") — most zablokowany.");
            return false;
        }

        try {
            Class<?> coreClass = Class.forName(CORE_CLASS);

            // Weryfikacja ClassLoader — klasa musi pochodzic z pluginu Core
            if (!coreClass.getClassLoader().equals(corePlugin.getClass().getClassLoader())) {
                logger.severe("[HexVG-DB] BEZPIECZENSTWO: Niezgodnosc ClassLoader dla HexVG-Core — most zablokowany.");
                return false;
            }

            // getInstance() → getAPI() → getDatabaseService()
            Object coreInstance = coreClass.getMethod("getInstance").invoke(null);
            Object api          = coreClass.getMethod("getAPI").invoke(coreInstance);
            Object dbService    = api.getClass().getMethod("getDatabaseService").invoke(api);

            if (dbService == null) {
                logger.warning("[HexVG-DB] HexVG-Core: getDatabaseService() zwrocil null.");
                return false;
            }

            // Pobierz metody przez refleksje — przechowujemy do pozniejszego uzycia
            Class<?> dbClass = dbService.getClass();
            methodIsConnected   = findMethod(dbClass, "isConnected");
            methodGetConnection = findMethod(dbClass, "getConnection");
            methodGetType       = findMethod(dbClass, "getType");

            if (methodIsConnected == null || methodGetConnection == null || methodGetType == null) {
                logger.warning("[HexVG-DB] HexVG-Core: DatabaseService nie implementuje wymaganych metod.");
                return false;
            }

            // Sprawdz czy polaczenie dziala
            Boolean connected = (Boolean) methodIsConnected.invoke(dbService);
            if (Boolean.TRUE.equals(connected)) {
                this.coreDbService = dbService;
                this.bridgeActive  = true;
                logger.info("[HexVG-DB] Polaczono z HexVG-Core — uzywam wspoldzielonej bazy.");
                logger.info("[HexVG-DB] Typ bazy: " + getDatabaseType());
                return true;
            } else {
                logger.warning("[HexVG-DB] HexVG-Core znaleziony, ale DatabaseService nie jest polaczony.");
                return false;
            }

        } catch (ClassNotFoundException e) {
            logger.info("[HexVG-DB] Klasa HexVG-Core niedostepna — tryb standalone.");
        } catch (Exception e) {
            logger.warning("[HexVG-DB] Blad polaczenia z HexVG-Core: " + e.getMessage());
        }

        return false;
    }

    // ── API ───────────────────────────────────────────────────────────────

    public boolean isActive() {
        if (!bridgeActive || coreDbService == null || methodIsConnected == null) return false;
        try {
            return Boolean.TRUE.equals(methodIsConnected.invoke(coreDbService));
        } catch (Exception e) {
            logger.warning("[HexVG-DB] CoreBridge.isActive() blad: " + e.getMessage());
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        if (!isActive()) throw new IllegalStateException("CoreBridge nie jest aktywny!");
        try {
            return (Connection) methodGetConnection.invoke(coreDbService);
        } catch (Exception e) {
            throw new SQLException("CoreBridge.getConnection() blad: " + e.getMessage(), e);
        }
    }

    public String getDatabaseType() {
        if (!isActive() || methodGetType == null) return null;
        try {
            Object type = methodGetType.invoke(coreDbService);
            return type != null ? type.toString() : null;
        } catch (Exception e) {
            logger.warning("[HexVG-DB] CoreBridge.getDatabaseType() blad: " + e.getMessage());
            return null;
        }
    }

    public void disconnect() {
        if (bridgeActive) logger.info("[HexVG-DB] Rozlaczam most z HexVG-Core.");
        this.coreDbService      = null;
        this.methodGetConnection = null;
        this.methodIsConnected   = null;
        this.methodGetType       = null;
        this.bridgeActive        = false;
    }

    // ── Pomocnicze ────────────────────────────────────────────────────────

    /**
     * Szuka metody po nazwie w klasie i jej interfejsach (glebokie przeszukiwanie).
     */
    private Method findMethod(Class<?> clazz, String name) {
        // Szukaj w samej klasie i interfejsach
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }
}