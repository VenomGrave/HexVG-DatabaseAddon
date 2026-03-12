package com.venomgrave.hexvg.database.placeholder;

import com.venomgrave.hexvg.database.HexVGAddon;

/**
 * Wrapper that isolates PlaceholderAPI classes from the main classloader.
 * Called only after confirming PlaceholderAPI is present on the server.
 * This prevents NoClassDefFoundError when PAPI is not installed.
 */
public class PlaceholderHook {

    public static void register(HexVGAddon plugin) {
        new HexVGExpansion(plugin).register();
    }
}
