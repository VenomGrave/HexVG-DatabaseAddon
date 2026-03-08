package com.venomgrave.hexvg.database.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class DebugLogger {

    private final JavaPlugin plugin;
    private volatile boolean enabled;

    public DebugLogger(JavaPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void log(String message) {
        if (!enabled) return;
        plugin.getLogger().info("[DEBUG] " + message);
    }

    public void log(String message, Throwable throwable) {
        if (!enabled) return;
        plugin.getLogger().log(Level.INFO, "[DEBUG] " + message, throwable);
    }

    public void query(String sql, int paramCount, long elapsedMs, int rowsAffected) {
        if (!enabled) return;
        plugin.getLogger().info("[DEBUG] [QUERY] (" + elapsedMs + "ms) params=" + paramCount
                + " rows=" + rowsAffected + " | " + truncateSql(sql));
    }

    public void queryError(String sql, int paramCount, Throwable throwable) {
        if (!enabled) return;
        plugin.getLogger().log(Level.INFO,
                "[DEBUG] [QUERY ERROR] params=" + paramCount + " | " + truncateSql(sql),
                throwable);
    }

    private String truncateSql(String sql) {
        if (sql == null) return "null";
        String trimmed = sql.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
    }
}
