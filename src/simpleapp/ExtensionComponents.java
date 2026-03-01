package simpleapp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro centralizado de componentes que implementan elementos de extension.
 * Las claves son los local-name de los elementos en el namespace propio.
 */
public final class ExtensionComponents {
    private static final Map<String, ExtensionElementHandler> HANDLERS = new LinkedHashMap<>();

    static { registerDefaults(); }

    private ExtensionComponents() {
    }

    private static void registerDefaults() {
        register("saludo", XsltExtensions::saludo);
        register("changeImageFormat", XsltExtensions::changeImageFormat);
    }

    public static synchronized void register(String name, ExtensionElementHandler handler) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del elemento no puede estar vacio");
        }
        if (handler == null) {
            throw new IllegalArgumentException("El handler no puede ser null");
        }
        HANDLERS.put(name.trim(), handler);
    }

    public static synchronized ExtensionElementHandler get(String name) {
        return HANDLERS.get(name);
    }

    public static synchronized Map<String, ExtensionElementHandler> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(HANDLERS));
    }
}
