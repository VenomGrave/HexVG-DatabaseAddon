package com.venomgrave.hexvg.util;

import com.venomgrave.hexvg.HexVGDatabaseAddon;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DBLogger {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static PrintWriter fileWriter;
    private static boolean debugEnabled = false;
    private static boolean logToFile = false;

    public static void init() {
        HexVGDatabaseAddon plugin = HexVGDatabaseAddon.getInstance();
        debugEnabled = plugin.getConfig().getBoolean("debug.enabled", false);
        logToFile = plugin.getConfig().getBoolean("debug.log-to-file", true);

        if (logToFile) {
            String logPath = plugin.getConfig().getString("debug.log-file", "logs/hexvg-db-debug.log");
            File logFile = new File(plugin.getDataFolder(), logPath);

            try {
                logFile.getParentFile().mkdirs();
                fileWriter = new PrintWriter(new FileWriter(logFile, true), true);
                writeToFile("[INFO] HexVG-DatabaseAddon logger initialized.");
            } catch (IOException e) {
                Bukkit.getLogger().warning("[HexVG-DB] Could not initialize log file: " + e.getMessage());
            }
        }
    }

    public static void close() {
        if (fileWriter != null) {
            fileWriter.close();
            fileWriter = null;
        }
    }

    private static final String PREFIX_INFO  = Messages.colorize("&8[&bHexVG-DB&8] &r");
    private static final String PREFIX_WARN  = Messages.colorize("&8[&eHexVG-DB&8] &e");
    private static final String PREFIX_ERROR = Messages.colorize("&8[&cHexVG-DB&8] &c");
    private static final String PREFIX_DEBUG = Messages.colorize("&8[&7HexVG-DB&8][&7DEBUG&8] &7");

    public static void info(String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX_INFO + Messages.colorize(message));
        if (logToFile) writeToFile("[INFO] " + stripColor(message));
    }

    public static void warning(String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX_WARN + Messages.colorize(message));
        if (logToFile) writeToFile("[WARN] " + stripColor(message));
    }

    public static void severe(String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX_ERROR + Messages.colorize(message));
        if (logToFile) writeToFile("[ERROR] " + stripColor(message));
    }

    public static void debug(String message) {
        if (!debugEnabled) return;
        Bukkit.getConsoleSender().sendMessage(PREFIX_DEBUG + Messages.colorize(message));
        if (logToFile) writeToFile("[DEBUG] " + stripColor(message));
    }

    public static void debugQuery(String dbName, String query) {
        if (!debugEnabled) return;
        String msg = Messages.DEBUG_QUERY.get(dbName, query);
        Bukkit.getConsoleSender().sendMessage(PREFIX_DEBUG + Messages.colorize(msg));
        if (logToFile) writeToFile("[DEBUG][QUERY] db=" + dbName + " query=" + query);
    }

    private static void writeToFile(String message) {
        if (fileWriter == null) return;
        String timestamp = DATE_FORMAT.format(new Date());
        fileWriter.println("[" + timestamp + "] " + message);
    }

    private static String stripColor(String text) {
        return text.replaceAll("&[0-9a-fA-FrRkKlLmMnNoO]", "").replaceAll("\u00A7[0-9a-fA-FrRkKlLmMnNoO]", "");
    }

    public static void reload() {
        close();
        init();
    }
}