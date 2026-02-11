package simpleapp;

import org.apache.xalan.extensions.XSLProcessorContext;
import org.apache.xalan.templates.ElemExtensionCall;
import org.apache.xpath.XPath;
import org.apache.xpath.XPathContext;
import org.apache.xpath.objects.XObject;
import org.apache.xml.utils.PrefixResolver;

import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Funciones auxiliares expuestas a las hojas XSLT mediante namespaces de extension.
 * Con JAXP/Xalan pueden invocarse con xmlns:app="java:simpleapp.XsltExtensions".
 */
public final class XsltExtensions {
    private static final Set<Path> convertedFiles = Collections.synchronizedSet(new LinkedHashSet<>());

    private XsltExtensions() {
    }

    /**
     * Simple saludo reutilizable desde XSLT para pruebas/demo.
     */
    public static String saludar(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            limpio = "coleccion";
        }
        return "Hola, " + limpio + "!";
    }

    /**
     * Extension element handler para <app:saludo/>. Emite directamente el saludo al resultado.
     * Permite atributos AVT "select" o "nombre" para indicar el destinatario.
     */
    public static void saludo(XSLProcessorContext context, ElemExtensionCall element)
            throws TransformerException {
        String destino = resolveNombre(context, element);
        String resultado = saludar(destino);
        try {
            context.outputToResultTree(context.getStylesheet(), resultado);
        } catch (TransformerException e) {
            throw e;
        } catch (Exception e) {
            throw new TransformerException("No se pudo escribir el saludo", e);
        }
    }

    /**
     * Extension element handler para <app:changeImageFormat/>.
     * Convierte la imagen indicada a otro formato mediante ImageMagick y escribe en la salida el path del nuevo archivo.
     *
     * Atributos soportados:
     * - format (obligatorio): formato destino (png, jpg, webp segun disponibilidad de ImageMagick).
     * - src/path (opcional): ruta al fichero de imagen. Si falta, se usa el texto del nodo de contexto.
     * - select (opcional): expresion XPath que devuelve la ruta; se evalua si no se aporto src/path.
     * - output (opcional): ruta del fichero resultante. Si falta, se crea junto al origen con sufijo "-converted".
     * - suffix (opcional): sufijo para el nombre si se usa la ruta derivada por defecto.
     */
    public static void changeImageFormat(XSLProcessorContext context, ElemExtensionCall element)
            throws TransformerException {
        boolean debug = Boolean.getBoolean("xslt.ext.debug");
        if (debug) {
            System.out.println("[xslt.ext] changeImageFormat invoked");
        }
        String format = normalizeFormat(readAttribute("format", context, element));
        if (isBlank(format)) {
            throw new TransformerException("El atributo 'format' es obligatorio en change-image-format");
        }

        String sourcePath = firstNonBlank(
                readAttribute("src", context, element),
                readAttribute("path", context, element),
                evaluateSelectAttribute(readAttribute("select", context, element), context, element),
                contextNodeText(context)
        );
        if (isBlank(sourcePath)) {
            throw new TransformerException("No se pudo determinar la ruta de la imagen (use src/path o select)");
        }

        Path baseDir = resolveBaseDir(context);
        Path input = resolvePath(sourcePath, baseDir);
        if (debug) {
            System.out.println("[xslt.ext] source=" + sourcePath);
            System.out.println("[xslt.ext] baseDir=" + baseDir);
            System.out.println("[xslt.ext] input=" + input);
        }
        if (!Files.exists(input)) {
            throw new TransformerException("No se encontro el fichero de imagen: " + input);
        }

        Path output = resolveOutputPath(readAttribute("output", context, element),
                readAttribute("suffix", context, element), input, format, baseDir);
        if (debug) {
            System.out.println("[xslt.ext] output=" + output);
        }

        convertImageWithMagick(input, output, format, debug);
        if (debug) {
            boolean exists = Files.exists(output);
            long size = 0L;
            try { size = exists ? Files.size(output) : 0L; } catch (Exception ignored) {}
            System.out.println("[xslt.ext] converted exists=" + exists + " size=" + size);
        }
        registerConvertedFile(output);

        try {
            String emitted = emitPath(output, baseDir);
            writeTextToResult(context, emitted);
            if (debug) {
                System.out.println("[xslt.ext] changeImageFormat output: " + emitted);
            }
        } catch (TransformerException e) {
            throw e;
        } catch (Exception e) {
            throw new TransformerException("No se pudo escribir la ruta convertida", e);
        }
    }

    private static void convertImageWithMagick(Path input, Path output, String format, boolean debug)
            throws TransformerException {
        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new TransformerException("No se pudo preparar la carpeta destino para la imagen", e);
        }

        List<String[]> commands = buildMagickCommands(input, output);
        TransformerException lastError = null;
        String lastLog = null;
        for (String[] command : commands) {
            try {
                if (debug) {
                    System.out.println("[xslt.ext] trying command: " + String.join(" ", command));
                }
                lastLog = runMagickCommand(command, format);
                if (debug && lastLog != null && !lastLog.isBlank()) {
                    System.out.println("[xslt.ext] magick output: " + lastLog.trim());
                }
                return;
            } catch (TransformerException e) {
                lastError = e;
                if (debug) {
                    System.out.println("[xslt.ext] command failed: " + e.getMessage());
                    if (lastLog != null && !lastLog.isBlank()) {
                        System.out.println("[xslt.ext] magick output: " + lastLog.trim());
                    }
                }
            }
        }
        if (lastError != null) {
            // Fallback: intento con ImageIO (sin ImageMagick)
            try {
                convertWithImageIO(input, output, format);
                return;
            } catch (Exception e) {
                throw lastError;
            }
        }
        throw new TransformerException("No se encontro un comando valido de ImageMagick para convertir la imagen.");
    }

    private static List<String[]> buildMagickCommands(Path input, Path output) {
        List<String[]> commands = new ArrayList<>();
        String magickExe = resolveMagickExecutable();
        commands.add(new String[]{magickExe, input.toString(), output.toString()});
        if (!isWindows()) {
            commands.add(new String[]{"convert", input.toString(), output.toString()});
        }
        return commands;
    }

    private static String runMagickCommand(String[] command, String format) throws TransformerException {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new TransformerException("No se pudo ejecutar ImageMagick (comando '" + command[0]
                    + "'). Asegura que esta instalado y en PATH.", e);
        } catch (Exception e) {
            throw new TransformerException("Error al lanzar ImageMagick (comando '" + command[0] + "')", e);
        }

        String log;
        try (InputStream stream = process.getInputStream()) {
            log = readProcessOutput(stream);
        } catch (Exception e) {
            process.destroyForcibly();
            throw new TransformerException("Fallo al leer la salida de ImageMagick", e);
        }

        try {
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TransformerException("ImageMagick tardo demasiado en convertir la imagen");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new TransformerException("ImageMagick devolvio codigo " + exitCode
                        + " al convertir a '" + format + "': " + log);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransformerException("La conversion de imagen fue interrumpida", e);
        }
        return log;
    }

    private static String readProcessOutput(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase(Locale.ROOT).contains("win");
    }

    private static String resolveMagickExecutable() {
        String configured = System.getProperty("xslt.magick");
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        return "magick";
    }

    private static void convertWithImageIO(Path input, Path output, String format) throws Exception {
        String fmt = normalizeFormat(format);
        if ("jpg".equals(fmt)) {
            fmt = "jpeg";
        }
        BufferedImage img = ImageIO.read(input.toFile());
        if (img == null) {
            throw new IllegalArgumentException("ImageIO no pudo leer la imagen: " + input);
        }
        boolean ok = ImageIO.write(img, fmt, output.toFile());
        if (!ok) {
            throw new IllegalArgumentException("ImageIO no soporta escribir formato: " + fmt);
        }
    }

    private static void registerConvertedFile(Path output) {
        if (output == null) return;
        convertedFiles.add(output.normalize());
    }

    private static void writeTextToResult(XSLProcessorContext context, String text)
            throws TransformerException {
        if (text == null) return;
        try {
            context.outputToResultTree(context.getStylesheet(), text);
            return;
        } catch (Exception ignored) {
        }
        try {
            org.apache.xml.serializer.SerializationHandler handler =
                    context.getTransformer().getResultTreeHandler();
            char[] chars = text.toCharArray();
            handler.characters(chars, 0, chars.length);
        } catch (Exception e) {
            throw new TransformerException("No se pudo escribir en el arbol de resultado", e);
        }
    }

    public static void resetConvertedFiles() {
        convertedFiles.clear();
    }

    public static List<Path> snapshotConvertedFiles() {
        synchronized (convertedFiles) {
            return new ArrayList<>(convertedFiles);
        }
    }

    private static String resolveNombre(XSLProcessorContext context, ElemExtensionCall element)
            throws TransformerException {
        String nombre = readAttribute("nombre", context, element);
        if (isBlank(nombre)) {
            nombre = evaluateSelectAttribute(readAttribute("select", context, element),
                    context, element);
        }
        return nombre;
    }

    private static String readAttribute(String name, XSLProcessorContext context, ElemExtensionCall element)
            throws TransformerException {
        if (name == null) {
            return null;
        }
        org.w3c.dom.Node sourceNode = context.getContextNode();
        return element.getAttribute(name, sourceNode, context.getTransformer());
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static String firstNonBlank(String... options) {
        if (options == null) return null;
        for (String option : options) {
            if (!isBlank(option)) {
                return option;
            }
        }
        return null;
    }

    private static String normalizeFormat(String format) {
        return isBlank(format) ? null : format.trim().toLowerCase(Locale.ROOT);
    }

    private static String contextNodeText(XSLProcessorContext context) {
        org.w3c.dom.Node node = context != null ? context.getContextNode() : null;
        return node != null ? node.getTextContent() : null;
    }

    private static Path resolveBaseDir(XSLProcessorContext context) {
        try {
            org.w3c.dom.Node node = context != null ? context.getContextNode() : null;
            if (node != null && node.getOwnerDocument() != null) {
                String docUri = node.getOwnerDocument().getDocumentURI();
                if (!isBlank(docUri)) {
                    try {
                        Path docPath = Paths.get(java.net.URI.create(docUri));
                        return docPath.getParent();
                    } catch (Exception ignored) {
                        // cae al siguiente intento
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return Paths.get("").toAbsolutePath();
    }

    private static Path resolvePath(String rawPath, Path baseDir) {
        Path path = Paths.get(rawPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        if (baseDir != null) {
            return baseDir.resolve(rawPath).normalize();
        }
        return path.toAbsolutePath().normalize();
    }

    private static Path resolveOutputPath(String outputAttr, String suffixAttr, Path input, String format, Path baseDir) {
        if (!isBlank(outputAttr)) {
            return resolvePath(outputAttr, baseDir);
        }
        String fileName = input.getFileName() != null ? input.getFileName().toString() : "imagen";
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        String suffix = isBlank(suffixAttr) ? "-converted" : suffixAttr;
        String outputName = baseName + suffix + "." + format;
        Path parent = input.getParent();
        return (parent != null ? parent.resolve(outputName) : Paths.get(outputName)).normalize();
    }

    private static String emitPath(Path output, Path baseDir) {
        if (output == null) return "";
        Path normalizedBase = baseDir != null ? baseDir.normalize() : null;
        try {
            if (normalizedBase != null && output.normalize().startsWith(normalizedBase)) {
                return normalizedBase.relativize(output.normalize()).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
            // si falla la relativizacion, se devuelve la ruta absoluta
        }
        return output.toString();
    }

    private static String evaluateSelectAttribute(String expression, XSLProcessorContext context,
                                                  ElemExtensionCall element)
            throws TransformerException {
        if (isBlank(expression)) {
            return null;
        }
        org.w3c.dom.Node contextNode = context.getContextNode();
        if (contextNode == null) {
            throw new TransformerException("No hay nodo de contexto para evaluar '" + expression + "'");
        }
        PrefixResolver resolver = element instanceof PrefixResolver ? (PrefixResolver) element : null;
        try {
            XPath xpath = new XPath(expression, element, resolver, XPath.SELECT,
                    context.getTransformer().getErrorListener());
            XPathContext xctxt = context.getTransformer().getXPathContext();
            int nodeHandle = xctxt.getDTMHandleFromNode(contextNode);
            XObject result = xpath.execute(xctxt, nodeHandle, resolver);
            return result != null ? result.str() : null;
        } catch (TransformerException e) {
            throw e;
        } catch (Exception e) {
            throw new TransformerException("Error al evaluar la expresion '" + expression + "'", e);
        }
    }
}
