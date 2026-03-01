package simpleapp;

import org.apache.xalan.extensions.XSLProcessorContext;
import org.apache.xalan.templates.ElemExtensionCall;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Carga un registro de elementos de extension desde plugins.xml.
 * Cada entrada mapea: nombre de elemento XSLT -> clase/metodo Java.
 */
public final class PluginRegistryLoader {
    private static final String CONFIG_PROPERTY = "xslt.plugins.config";
    private static final String BUNDLE_DIR_PROPERTY = "xslt.plugins.bundle.dir";
    private static final Path DEFAULT_CONFIG = Paths.get("plugins", "plugins.xml");
    private static volatile boolean defaultLoadAttempted;
    private static volatile LoadReport lastDefaultReport =
            LoadReport.skipped(DEFAULT_CONFIG, "No se intento cargar");

    private PluginRegistryLoader() {
    }

    public static LoadReport loadFromDefaultConfig() {
        Path configPath = resolveConfigPath();
        if (configPath == null || !Files.exists(configPath)) {
            return LoadReport.skipped(configPath, "No se encontro plugins.xml");
        }
        try {
            return loadFromConfig(configPath);
        } catch (Exception ex) {
            return LoadReport.failed(configPath, ex.getMessage());
        }
    }

    public static synchronized LoadReport ensureDefaultLoaded() {
        if (!defaultLoadAttempted) {
            lastDefaultReport = loadFromDefaultConfig();
            defaultLoadAttempted = true;
        }
        return lastDefaultReport;
    }

    public static LoadReport loadFromConfig(Path configPath) throws Exception {
        if (configPath == null) {
            throw new IllegalArgumentException("configPath no puede ser null");
        }
        if (!Files.exists(configPath)) {
            throw new IllegalArgumentException("No existe el fichero: " + configPath.toAbsolutePath());
        }

        Document doc = parse(configPath);
        Element root = doc.getDocumentElement();
        if (root == null) {
            return LoadReport.skipped(configPath, "plugins.xml vacio");
        }

        Path baseDir = configPath.toAbsolutePath().getParent();
        Path bundleDir = resolveBundleDir(root, baseDir);
        Set<URL> jarUrls = new LinkedHashSet<>();
        if (bundleDir != null) {
            addBundleDirJars(bundleDir.toString(), baseDir, jarUrls);
        }
        addBundleEntries(root, baseDir, jarUrls);

        List<PluginEntry> entries = parsePluginEntries(root, baseDir, bundleDir, jarUrls);
        ClassLoader pluginLoader = buildClassLoader(jarUrls);

        List<String> registered = new ArrayList<>();
        for (PluginEntry entry : entries) {
            ExtensionElementHandler handler = createHandler(entry, pluginLoader);
            ExtensionComponents.register(entry.elementName, handler);
            String method = !isBlank(entry.methodName) ? entry.methodName : entry.elementName;
            registered.add(entry.elementName + " -> " + entry.className + "#" + method);
        }

        return LoadReport.ok(configPath, registered, jarUrls.size());
    }

    private static Path resolveConfigPath() {
        String configured = System.getProperty(CONFIG_PROPERTY);
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured.trim());
        }
        return DEFAULT_CONFIG;
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(path.toFile());
    }

    private static Path resolveBundleDir(Element root, Path baseDir) {
        String configured = System.getProperty(BUNDLE_DIR_PROPERTY);
        if (isBlank(configured)) {
            configured = value(root, "bundleDir");
        }
        if (isBlank(configured)) {
            return null;
        }
        return resolvePath(configured, baseDir);
    }

    private static void addBundleDirJars(String rawPath, Path baseDir, Set<URL> jarUrls) throws Exception {
        if (isBlank(rawPath)) return;
        Path dir = resolvePath(rawPath, baseDir);
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> {
                        try {
                            jarUrls.add(p.toUri().toURL());
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    private static void addBundleEntries(Element root, Path baseDir, Set<URL> jarUrls) {
        NodeList bundles = root.getElementsByTagName("bundle");
        for (int i = 0; i < bundles.getLength(); i++) {
            Node node = bundles.item(i);
            if (!(node instanceof Element bundle)) continue;
            String path = firstNonBlank(
                    value(bundle, "path"),
                    value(bundle, "jar"),
                    normalize(bundle.getTextContent())
            );
            if (isBlank(path)) continue;
            try {
                Path resolved = resolvePath(path, baseDir);
                if (Files.exists(resolved) && Files.isRegularFile(resolved)) {
                    jarUrls.add(resolved.toUri().toURL());
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static List<PluginEntry> parsePluginEntries(Element root, Path baseDir,
                                                        Path bundleDir, Set<URL> jarUrls) throws Exception {
        List<PluginEntry> entries = new ArrayList<>();
        NodeList plugins = root.getElementsByTagName("plugin");
        for (int i = 0; i < plugins.getLength(); i++) {
            Node node = plugins.item(i);
            if (!(node instanceof Element plugin)) continue;

            String elementName = firstNonBlank(
                    value(plugin, "element"),
                    value(plugin, "elem"),
                    value(plugin, "name")
            );
            String className = firstNonBlank(
                    value(plugin, "class"),
                    value(plugin, "clazz")
            );
            String methodName = firstNonBlank(
                    value(plugin, "method"),
                    value(plugin, "handler")
            );
            String jarPath = firstNonBlank(
                    value(plugin, "jar"),
                    value(plugin, "bundle")
            );

            if (isBlank(elementName) || isBlank(className)) {
                throw new IllegalArgumentException("Plugin invalido en " + configPathHint(root)
                        + ": se requieren 'element' y 'class'");
            }

            if (!isBlank(jarPath)) {
                Path resolvedJar = resolveJarPath(jarPath, bundleDir, baseDir);
                if (!Files.exists(resolvedJar)) {
                    throw new IllegalArgumentException("No existe el jar del plugin '" + elementName + "': "
                            + resolvedJar.toAbsolutePath());
                }
                jarUrls.add(resolvedJar.toUri().toURL());
            }

            entries.add(new PluginEntry(elementName.trim(), className.trim(), normalize(methodName)));
        }
        return entries;
    }

    private static String configPathHint(Element root) {
        Node owner = root != null ? root.getOwnerDocument() : null;
        String uri = owner != null ? owner.getBaseURI() : null;
        return uri != null ? uri : "plugins.xml";
    }

    private static ClassLoader buildClassLoader(Set<URL> jarUrls) {
        if (jarUrls == null || jarUrls.isEmpty()) {
            return PluginRegistryLoader.class.getClassLoader();
        }
        URL[] urls = jarUrls.toArray(new URL[0]);
        return new URLClassLoader(urls, PluginRegistryLoader.class.getClassLoader());
    }

    private static ExtensionElementHandler createHandler(PluginEntry entry, ClassLoader loader) throws Exception {
        Class<?> clazz = Class.forName(entry.className, true, loader);
        if (ExtensionElementHandler.class.isAssignableFrom(clazz)
                && ("invoke".equals(entry.methodName) || isBlank(entry.methodName))) {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            return (ExtensionElementHandler) instance;
        }

        String methodName = isBlank(entry.methodName) ? entry.elementName : entry.methodName;
        Method method = clazz.getMethod(methodName, XSLProcessorContext.class, ElemExtensionCall.class);
        Object target = Modifier.isStatic(method.getModifiers()) ? null : clazz.getDeclaredConstructor().newInstance();
        return (context, element) -> invokeMethod(entry, method, target, context, element);
    }

    private static void invokeMethod(PluginEntry entry, Method method, Object target,
                                     XSLProcessorContext context, ElemExtensionCall element)
            throws TransformerException {
        try {
            method.invoke(target, context, element);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TransformerException te) {
                throw te;
            }
            throw new TransformerException("Error ejecutando plugin '" + entry.elementName + "'", cause);
        } catch (Exception e) {
            throw new TransformerException("No se pudo invocar plugin '" + entry.elementName + "'", e);
        }
    }

    private static String value(Element element, String name) {
        if (element == null || name == null) return null;
        String attr = normalize(element.getAttribute(name));
        if (!isBlank(attr)) return attr;
        NodeList children = element.getElementsByTagName(name);
        if (children.getLength() > 0) {
            Node node = children.item(0);
            if (node != null) {
                String content = normalize(node.getTextContent());
                if (!isBlank(content)) {
                    return content;
                }
            }
        }
        return null;
    }

    private static Path resolvePath(String rawPath, Path baseDir) {
        Path path = Paths.get(rawPath.trim());
        if (path.isAbsolute()) {
            return path.normalize();
        }
        if (baseDir != null) {
            return baseDir.resolve(path).normalize();
        }
        return path.toAbsolutePath().normalize();
    }

    private static Path resolveJarPath(String rawPath, Path bundleDir, Path baseDir) {
        Path raw = Paths.get(rawPath.trim());
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        if (bundleDir != null && raw.getNameCount() == 1) {
            return bundleDir.resolve(raw).normalize();
        }
        return resolvePath(rawPath, baseDir);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String normalize(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static final class PluginEntry {
        private final String elementName;
        private final String className;
        private final String methodName;

        private PluginEntry(String elementName, String className, String methodName) {
            this.elementName = elementName;
            this.className = className;
            this.methodName = methodName;
        }
    }

    public static final class LoadReport {
        private final Path configPath;
        private final boolean loaded;
        private final int jarCount;
        private final List<String> entries;
        private final String message;

        private LoadReport(Path configPath, boolean loaded, int jarCount, List<String> entries, String message) {
            this.configPath = configPath;
            this.loaded = loaded;
            this.jarCount = jarCount;
            this.entries = entries;
            this.message = message;
        }

        public static LoadReport ok(Path configPath, List<String> entries, int jarCount) {
            return new LoadReport(configPath, true, jarCount, entries, null);
        }

        public static LoadReport skipped(Path configPath, String message) {
            return new LoadReport(configPath, false, 0, List.of(), message);
        }

        public static LoadReport failed(Path configPath, String message) {
            return new LoadReport(configPath, false, 0, List.of(), message);
        }

        public String summary() {
            String path = configPath != null ? configPath.toString() : "(sin ruta)";
            if (loaded) {
                return "Plugins cargados desde " + path + ": " + entries.size()
                        + " (jars=" + jarCount + ")";
            }
            return "Plugins no cargados desde " + path + ": " + message;
        }

        public boolean isLoaded() {
            return loaded;
        }

        public List<String> entries() {
            return entries;
        }
    }
}
