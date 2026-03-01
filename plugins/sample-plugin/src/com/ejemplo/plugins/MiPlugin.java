package com.ejemplo.plugins;

import org.apache.xalan.extensions.XSLProcessorContext;
import org.apache.xalan.templates.ElemExtensionCall;

import javax.xml.transform.TransformerException;

/**
 * Plugin externo de ejemplo para demostrar carga dinamica por JAR.
 */
public final class MiPlugin {
    private MiPlugin() {
    }

    public static void ejecutar(XSLProcessorContext context, ElemExtensionCall element)
            throws TransformerException {
        String text = readAttr(element, "text", context);
        if (isBlank(text)) {
            text = "Plugin externo activo";
        }
        String upper = readAttr(element, "uppercase", context);
        if ("true".equalsIgnoreCase(upper)) {
            text = text.toUpperCase();
        }

        try {
            context.outputToResultTree(context.getStylesheet(), text);
        } catch (TransformerException e) {
            throw e;
        } catch (Exception e) {
            throw new TransformerException("No se pudo emitir la salida del plugin externo", e);
        }
    }

    private static String readAttr(ElemExtensionCall element, String name, XSLProcessorContext context)
            throws TransformerException {
        if (element == null || name == null || context == null) return null;
        org.w3c.dom.Node sourceNode = context.getContextNode();
        return element.getAttribute(name, sourceNode, context.getTransformer());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
