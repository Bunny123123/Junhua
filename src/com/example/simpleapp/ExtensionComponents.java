package com.example.simpleapp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro centralizado de componentes que implementan elementos de extension.
 * Las claves son los local-name de los elementos en el namespace propio.
 */
public final class ExtensionComponents {
    private static final Map<String, ExtensionElementHandler> HANDLERS;

    static {
        Map<String, ExtensionElementHandler> map = new LinkedHashMap<>();
        map.put("saludo", XsltExtensions::saludo);
        map.put("changeImageFormat", XsltExtensions::changeImageFormat);
        HANDLERS = Collections.unmodifiableMap(map);
    }

    private ExtensionComponents() {
    }

    public static ExtensionElementHandler get(String name) {
        return HANDLERS.get(name);
    }

    public static Map<String, ExtensionElementHandler> snapshot() {
        return HANDLERS;
    }
}
