package com.ejemplo.plugins.deps;

/**
 * Utilidades compartidas por el plugin principal para forzar una dependencia externa real.
 */
public final class PluginSupport {
    private PluginSupport() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String normalizeFormat(String value) {
        return isBlank(value) ? null : value.trim().toLowerCase();
    }

    public static String buildGreeting(String rawName) {
        String name = isBlank(rawName) ? "coleccion" : rawName.trim();
        return "Hola, " + name + "!";
    }

    public static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
