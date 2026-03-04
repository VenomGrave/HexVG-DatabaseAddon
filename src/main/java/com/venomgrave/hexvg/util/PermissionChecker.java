package com.venomgrave.hexvg.util;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.lang.reflect.Method;

/**
 * Sprawdza uprawnienia gracza wywolujacego zdarzenie Skript.
 * Jesli zdarzenie nie pochodzi od gracza (np. on load, konsola) - przepuszcza.
 */
public class PermissionChecker {

    public static final String PERM_USE    = "hexvg.database.use";
    public static final String PERM_RAW    = "hexvg.database.raw";
    public static final String PERM_ADMIN  = "hexvg.admin";

    /**
     * Zwraca gracza powiazanego ze zdarzeniem lub null jesli brak (konsola, automatyzacja).
     */
    public static Player getPlayer(Event event) {
        if (event == null) return null;
        try {
            Method method = event.getClass().getMethod("getPlayer");
            Object result = method.invoke(event);
            if (result instanceof Player) return (Player) result;
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Sprawdza czy gracz ma uprawnienie.
     * Jesli event nie pochodzi od gracza - zwraca true (serwer/konsola/automat).
     */
    public static boolean hasPermission(Event event, String permission) {
        Player player = getPlayer(event);
        if (player == null) return true; // konsola/automatyzacja - zawsze OK
        return player.hasPermission(permission);
    }

    /**
     * Sprawdza uprawnienie hexvg.database.use (podstawowe uzycie skladni).
     */
    public static boolean canUseDatabase(Event event) {
        return hasPermission(event, PERM_USE);
    }

    /**
     * Sprawdza uprawnienie hexvg.database.raw (execute query, query rows, query value).
     */
    public static boolean canUseRawSql(Event event) {
        return hasPermission(event, PERM_RAW);
    }
}