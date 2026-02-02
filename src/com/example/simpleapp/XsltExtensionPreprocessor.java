package com.example.simpleapp;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Preprocesa hojas XSLT que usen un namespace propio para elementos de extension.
 * Genera una clase puente compatible con Xalan y una copia de la XSLT con el namespace esperado.
 */
public final class XsltExtensionPreprocessor {
    public static final String CUSTOM_EXTENSION_NAMESPACE = "urn:app-extension";
    private static final String GENERATED_PACKAGE = "com.example.simpleapp.generated";
    private static final String GENERATED_SIMPLE_CLASS = "AppExtensionBridge";
    private static final String GENERATED_FQN = GENERATED_PACKAGE + "." + GENERATED_SIMPLE_CLASS;

    private XsltExtensionPreprocessor() {
    }

    public static PreprocessedXslt preprocessIfNeeded(Path xsltPath) throws Exception {
        if (xsltPath == null || !Files.exists(xsltPath)) {
            return null;
        }
        Document doc = parse(xsltPath);
        Element root = doc.getDocumentElement();
        if (root == null) {
            return null;
        }

        String prefix = findExtensionPrefix(root, CUSTOM_EXTENSION_NAMESPACE);
        if (prefix == null) {
            return null;
        }

        Set<String> elements = collectExtensionElements(root, prefix);
        if (elements.isEmpty()) {
            return null;
        }
        checkHandlers(elements);

        Path workDir = Files.createTempDirectory("xslt_ext_bridge_");
        Path rewrittenXslt = workDir.resolve(deriveXsltName(xsltPath));
        rewriteNamespace(doc, prefix, rewrittenXslt);

        Path javaFile = writeBridgeSource(elements, workDir);
        Path classesDir = workDir.resolve("classes");
        compileBridge(javaFile, classesDir);
        ClassLoader loader = buildClassLoader(classesDir);

        return new PreprocessedXslt(rewrittenXslt, loader, workDir);
    }

    private static Document parse(Path xsltPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        try (InputStream in = Files.newInputStream(xsltPath)) {
            return db.parse(in);
        }
    }

    private static String findExtensionPrefix(Element root, String targetNamespace) {
        if (root == null || targetNamespace == null) return null;
        for (int i = 0; i < root.getAttributes().getLength(); i++) {
            Node attr = root.getAttributes().item(i);
            if (!(attr instanceof Attr)) continue;
            String name = attr.getNodeName();
            String value = attr.getNodeValue();
            if (name != null && name.startsWith("xmlns:") && targetNamespace.equals(value)) {
                return name.substring("xmlns:".length());
            }
        }
        return null;
    }

    private static Set<String> collectExtensionElements(Node node, String prefix) {
        Set<String> names = new LinkedHashSet<>();
        walk(node, prefix, names);
        return names;
    }

    private static void walk(Node node, String prefix, Set<String> names) {
        if (node == null) return;
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String p = node.getPrefix();
            String local = node.getLocalName();
            if (prefix.equals(p) && local != null) {
                names.add(local);
            }
        }
        Node child = node.getFirstChild();
        while (child != null) {
            walk(child, prefix, names);
            child = child.getNextSibling();
        }
    }

    private static void checkHandlers(Set<String> elements) {
        List<String> missing = new ArrayList<>();
        for (String name : elements) {
            if (ExtensionComponents.get(name) == null) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No hay componentes para: " + missing);
        }
    }

    private static void rewriteNamespace(Document doc, String prefix, Path output) throws Exception {
        Element root = doc.getDocumentElement();
        boolean replaced = false;
        for (int i = 0; i < root.getAttributes().getLength(); i++) {
            Node attr = root.getAttributes().item(i);
            if (!(attr instanceof Attr)) continue;
            String name = attr.getNodeName();
            if (name != null && name.equals("xmlns:" + prefix)) {
                attr.setNodeValue(buildXalanNamespace(GENERATED_FQN));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            throw new IllegalStateException("No se pudo reemplazar el namespace del prefijo " + prefix);
        }
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        try (var out = Files.newOutputStream(output)) {
            t.transform(new DOMSource(doc), new StreamResult(out));
        }
    }

    private static String buildXalanNamespace(String className) {
        return "http://xml.apache.org/xalan/java/" + className;
    }

    private static String deriveXsltName(Path original) {
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : ".xsl";
        return base + "-xalan" + ext;
    }

    private static Path writeBridgeSource(Set<String> elements, Path workDir) throws IOException {
        Path pkgDir = workDir.resolve(GENERATED_PACKAGE.replace('.', '/'));
        Files.createDirectories(pkgDir);
        Path javaFile = pkgDir.resolve(GENERATED_SIMPLE_CLASS + ".java");
        Files.writeString(javaFile, buildBridgeSource(elements), StandardCharsets.UTF_8);
        return javaFile;
    }

    private static String buildBridgeSource(Set<String> elements) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        sb.append("import org.apache.xalan.extensions.XSLProcessorContext;\n");
        sb.append("import org.apache.xalan.templates.ElemExtensionCall;\n");
        sb.append("import javax.xml.transform.TransformerException;\n");
        sb.append("import com.example.simpleapp.ExtensionComponents;\n");
        sb.append("import com.example.simpleapp.ExtensionElementHandler;\n\n");
        sb.append("public final class ").append(GENERATED_SIMPLE_CLASS).append(" {\n");
        sb.append("  private ").append(GENERATED_SIMPLE_CLASS).append("() {}\n\n");
        sb.append("  private static ExtensionElementHandler handler(String name) throws TransformerException {\n");
        sb.append("    ExtensionElementHandler h = ExtensionComponents.get(name);\n");
        sb.append("    if (h == null) {\n");
        sb.append("      throw new TransformerException(\"No hay handler para \" + name);\n");
        sb.append("    }\n");
        sb.append("    return h;\n");
        sb.append("  }\n\n");
        for (String name : elements) {
            sb.append("  public static void ").append(name)
              .append("(XSLProcessorContext ctx, ElemExtensionCall elem) throws TransformerException {\n");
            sb.append("    handler(\"").append(name).append("\").invoke(ctx, elem);\n");
            sb.append("  }\n\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static void compileBridge(Path javaFile, Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Se necesita un JDK (SystemJavaCompiler no disponible)");
        }
        Files.createDirectories(classesDir);
        List<String> options = new ArrayList<>();
        options.add("-classpath");
        options.add(System.getProperty("java.class.path"));
        options.add("-d");
        options.add(classesDir.toString());
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends javax.tools.JavaFileObject> units =
                    fileManager.getJavaFileObjects(javaFile.toFile());
            try (var err = new PrintWriter(System.err)) {
                JavaCompiler.CompilationTask task = compiler.getTask(err, fileManager, null, options, null, units);
                Boolean ok = task.call();
                if (ok == null || !ok) {
                    throw new IllegalStateException("Fallo al compilar la clase puente generada");
                }
            }
        }
    }

    private static ClassLoader buildClassLoader(Path classesDir) throws Exception {
        URL url = classesDir.toUri().toURL();
        return new URLClassLoader(new URL[]{url}, XsltExtensionPreprocessor.class.getClassLoader());
    }

    /**
     * Resultado del preprocesado: xslt reescrita y classloader con la clase puente generada.
     */
    public static final class PreprocessedXslt {
        private final Path xsltPath;
        private final ClassLoader classLoader;
        private final Path workDir;

        public PreprocessedXslt(Path xsltPath, ClassLoader classLoader, Path workDir) {
            this.xsltPath = xsltPath;
            this.classLoader = classLoader;
            this.workDir = workDir;
        }

        public Path getXsltPath() {
            return xsltPath;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public void cleanup() {
            try {
                XmlUtils.deleteDirectoryRecursively(workDir);
            } catch (Exception ignored) {
            }
        }
    }
}
