package com.venomgrave.hexvg.util;

import com.venomgrave.hexvg.HexVGDatabaseAddon;

public enum Messages {

    // General
    PREFIX("&8[&bHexVG-DB&8] &r", "&8[&bHexVG-DB&8] &r"),
    PLUGIN_ENABLED("&aPlugin enabled successfully.", "&aPlugin został pomyślnie włączony."),
    PLUGIN_DISABLED("&cPlugin disabled.", "&cPlugin został wyłączony."),
    RELOAD_SUCCESS("&aConfiguration reloaded successfully.", "&aKonfiguracja została pomyślnie przeładowana."),
    RELOAD_FAILED("&cFailed to reload configuration.", "&cNie udało się przeładować konfiguracji."),
    NO_PERMISSION("&cYou don't have permission to use this.", "&cNie masz uprawnień do tego."),

    // Skript check
    SKRIPT_NOT_FOUND("&cSkript is not installed or disabled! Plugin will be disabled.", "&cSkript nie jest zainstalowany lub wyłączony! Plugin zostanie wyłączony."),
    SKRIPT_DOWNLOAD("&cDownload Skript: https://github.com/SkriptLang/Skript/releases", "&cPobierz Skript: https://github.com/SkriptLang/Skript/releases"),

    // Database connection
    DB_CONNECTING("&7Connecting to database: &e{0}", "&7Łączenie z bazą danych: &e{0}"),
    DB_CONNECTED("&aConnected to database: &e{0}", "&aPołączono z bazą danych: &e{0}"),
    DB_DISCONNECTED("&cDisconnected from database: &e{0}", "&cRozłączono z bazą danych: &e{0}"),
    DB_CONNECTION_FAILED("&cFailed to connect to database: &e{0} &c- {1}", "&cNie udało się połączyć z bazą danych: &e{0} &c- {1}"),
    DB_NOT_FOUND("&cDatabase not found: &e{0}", "&cNie znaleziono bazy danych: &e{0}"),
    DB_ALREADY_EXISTS("&cDatabase connection already exists: &e{0}", "&cPołączenie z bazą danych już istnieje: &e{0}"),
    DB_RECONNECTING("&eAttempting to reconnect to database: &6{0} &e(attempt {1}/{2})", "&ePróba ponownego połączenia z bazą: &6{0} &e(próba {1}/{2})"),
    DB_RECONNECT_FAILED("&cAll reconnect attempts failed for database: &e{0}", "&cWszystkie próby reconnect nie powiodły się dla bazy: &e{0}"),

    // Query
    QUERY_ERROR("&cQuery error on database &e{0}&c: {1}", "&cBłąd zapytania na bazie &e{0}&c: {1}"),
    QUERY_SUCCESS("&aQuery executed successfully on database: &e{0}", "&aZapytanie zostało wykonane pomyślnie na bazie: &e{0}"),
    INVALID_QUERY("&cInvalid or potentially dangerous query detected.", "&cWykryto nieprawidłowe lub potencjalnie niebezpieczne zapytanie."),

    // Status command
    STATUS_HEADER("&8&m----&r &bHexVG-DB Status &8&m----", "&8&m----&r &bHexVG-DB Status &8&m----"),
    STATUS_ENTRY("&7{0}: {1}", "&7{0}: {1}"),
    STATUS_CONNECTED("&aCONNECTED", "&aPOŁĄCZONY"),
    STATUS_DISCONNECTED("&cDISCONNECTED", "&cROZŁĄCZONY"),
    STATUS_NO_DATABASES("&7No databases configured.", "&7Brak skonfigurowanych baz danych."),

    // Startup summary
    STARTUP_HEADER("&8&m+==========[ &bHexVG-DatabaseAddon &8&m]==========+", "&8&m+==========[ &bHexVG-DatabaseAddon &8&m]==========+"),
    STARTUP_VERSION("&7  Version&8: &e{0}", "&7  Wersja&8:  &e{0}"),
    STARTUP_AUTHOR("&7  Author&8:  &eVenomGrave", "&7  Autor&8:   &eVenomGrave"),
    STARTUP_SEPARATOR("&8&m+==========================================+", "&8&m+==========================================+"),
    STARTUP_DB_HEADER("&7  Databases&8:", "&7  Bazy danych&8:"),
    STARTUP_DB_OK("&7    [&a✔&7] &e{0} &7(&a{1}&7)", "&7    [&a✔&7] &e{0} &7(&a{1}&7)"),
    STARTUP_DB_FAIL("&7    [&c✘&7] &e{0} &7(&cdisconnected&7)", "&7    [&c✘&7] &e{0} &7(&crozłączony&7)"),
    STARTUP_DB_NONE("&7    &c⚠ No databases configured!", "&7    &c⚠ Brak skonfigurowanych baz danych!"),
    STARTUP_SKRIPT_OK("&7  Skript&8:  &a✔ Syntax registered", "&7  Skript&8:  &a✔ Zarejestrowano składnię"),
    STARTUP_SKRIPT_FAIL("&7  Skript&8:  &c✘ Syntax registration failed!", "&7  Skript&8:  &c✘ Błąd rejestracji składni!"),
    STARTUP_WARN_NO_DB("&c  ⚠ WARNING: No database is connected!", "&c  ⚠ UWAGA: Żadna baza danych nie jest połączona!"),
    STARTUP_WARN_HINT("&7  Check config.yml and connection details.", "&7  Sprawdź config.yml i dane połączenia."),
    STARTUP_FOOTER_OK("&a  Plugin started successfully!", "&a  Plugin uruchomiony pomyślnie!"),
    STARTUP_FOOTER_WARN("&e  Plugin started with warnings.", "&e  Plugin uruchomiony z ostrzeżeniami."),

    // Migration
    MIGRATION_START("&7Starting migration from &e{0} &7to &e{1}&7...", "&7Rozpoczynam migrację z &e{0} &7do &e{1}&7..."),
    MIGRATION_COMPLETE("&aMigration complete! Tables: &e{0}&a, Rows: &e{1}&a, Failed: &c{2}", "&aMigracja zakończona! Tabele: &e{0}&a, Wiersze: &e{1}&a, Błędy: &c{2}"),
    MIGRATION_FAILED("&cMigration failed: {0}", "&cMigracja nie powiodła się: {0}"),
    MIGRATION_TABLE_DONE("&7Table &e{0} &7migrated (&e{1} &7rows)", "&7Tabela &e{0} &7przeniesiona (&e{1} &7wierszy)"),
    MIGRATION_TABLE_FAILED("&cFailed to migrate table &e{0}&c: {1}", "&cNie udało się przenieść tabeli &e{0}&c: {1}"),
    MIGRATION_SAME_TYPE("&cSource &e{0} &cand target &e{1} &care the same database type!", "&cŹródło &e{0} &ci cel &e{1} &csą tego samego typu bazy danych!"),
    MIGRATION_NO_TABLES("&cNo tables found in source database: &e{0}", "&cBrak tabel w źródłowej bazie danych: &e{0}"),
    MIGRATION_ALREADY_RUNNING("&cA migration is already running. Please wait.", "&cMigracja jest już w toku. Proszę czekać."),
    MIGRATION_SUCCESS_SKRIPT("Migration from {0} to {1} completed! {2} tables, {3} rows.", "Migracja z {0} do {1} zakończona! {2} tabele, {3} wierszy."),

    // Debug
    DEBUG_QUERY("&7[DEBUG] Executing query on &e{0}&7: {1}", "&7[DEBUG] Wykonuję zapytanie na &e{0}&7: {1}");

    private final String english;
    private final String polish;

    Messages(String english, String polish) {
        this.english = english;
        this.polish = polish;
    }

    public String get(Object... args) {
        String lang = HexVGDatabaseAddon.getInstance().getConfig().getString("language", "en");
        String message = lang.equalsIgnoreCase("pl") ? polish : english;

        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }

        return colorize(message);
    }

    public static String colorize(String text) {
        return text.replace("&", "\u00A7");
    }

    public static String getPrefix() {
        return PREFIX.get();
    }
}