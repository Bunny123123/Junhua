package com.example.simpleapp;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Punto único para resolver clases usadas en extensiones XSLT.
 * Detecta prefijos java:/xalan:// en la hoja de estilos, carga las clases con el classloader
 * de la aplicación y mantiene un registro ordenado para su inspección futura.
 */
public final class TransformationClassResolver {
    private static final TransformationClassResolver INSTANCE = new TransformationClassResolver();

    private final Set<String> resolved = Collections.synchronizedSet(new LinkedHashSet<>());
    private final ClassLoader loader = TransformationClassResolver.class.getClassLoader();

    private TransformationClassResolver() {
    }

    public static TransformationClassResolver getInstance() {
        return INSTANCE;
    }

    public void reset() {
        resolved.clear();
    }

    public List<String> snapshotResolvedClassNames() {
        synchronized (resolved) {
            return new ArrayList<>(resolved);
        }
    }

    /**
     * Explora la hoja XSLT para localizar namespaces de extensión que apunten a clases Java
     * y las carga una única vez.
     */
    public void resolveFromXslt(Path xsltPath) throws Exception {
        if (xsltPath == null || !Files.exists(xsltPath)) {
            return;
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        try (InputStream in = Files.newInputStream(xsltPath)) {
            Document xsltDoc = db.parse(in);
            Element root = xsltDoc.getDocumentElement();
            if (root != null) {
                walk(root);
            }
        }
    }

    private void walk(Node node) throws ClassNotFoundException {
        if (node == null) return;
        inspectNamespaceAttributes(node.getAttributes());
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                walk(child);
            }
        }
    }

    private void inspectNamespaceAttributes(NamedNodeMap attrs) throws ClassNotFoundException {
        if (attrs == null) return;
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            if (attr == null) continue;
            String name = attr.getNodeName();
            if (name == null || !name.toLowerCase().startsWith("xmlns")) {
                continue;
            }
            String value = attr.getNodeValue();
            String className = extractClassName(value);
            if (className != null && !className.isEmpty()) {
                resolveAndRegister(className);
            }
        }
    }

    private void resolveAndRegister(String className) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className, false, loader);
        resolved.add(clazz.getName());
    }

    private String extractClassName(String uri) {
        if (uri == null) return null;
        String trimmed = uri.trim();
        if (trimmed.startsWith("java:")) {
            return trimmed.substring("java:".length());
        }
        String xalanHttp = "http://xml.apache.org/xalan/java/";
        if (trimmed.startsWith(xalanHttp)) {
            return trimmed.substring(xalanHttp.length());
        }
        String xalanScheme = "xalan://";
        if (trimmed.startsWith(xalanScheme)) {
            return trimmed.substring(xalanScheme.length());
        }
        return null;
    }
}
