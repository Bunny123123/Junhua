package com.ejemplo.plugins;

import com.ejemplo.plugins.deps.PluginSupport;
import org.apache.xalan.extensions.XSLProcessorContext;
import org.apache.xalan.templates.ElemExtensionCall;
import org.apache.xml.utils.PrefixResolver;
import org.apache.xpath.XPath;
import org.apache.xpath.XPathContext;
import org.apache.xpath.objects.XObject;

import javax.xml.transform.TransformerException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Plugin externo: incluye handlers de saludo, conversion de imagen y una extension demo.
 */
public final class MiPlugin {
    private MiPlugin() {
    }

    public static void saludo(XSLProcessorContext context, ElemExtensionCall element)
            throws TransformerException {
        String destino = resolveNombre(context, element);
        String resultado = PluginSupport.buildGreeting(destino);
        emitText(context, resultado, "No se pudo emitir el saludo");
    }

    public static void changeImageFormat(XSLProcessorContext context, ElemExtensionCall element)
            throws TransformerException {
        boolean debug = Boolean.getBoolean("xslt.ext.debug");
        String format = PluginSupport.normalizeFormat(readAttribute("format", context, element));
        if (PluginSupport.isBlank(format)) {
            throw new TransformerException("El atributo 'format' es obligatorio en changeImageFormat");
        }

        String sourcePath = PluginSupport.firstNonBlank(
                readAttribute("src", context, element),
                readAttribute("path", context, element),
                evaluateSelectAttribute(readAttribute("select", context, element), context, element),
                contextNodeText(context)
        );
        if (PluginSupport.isBlank(sourcePath)) {
            throw new TransformerException("No se pudo determinar la ruta de la imagen (use src/path o select)");
        }

        Path baseDir = resolveBaseDir(context);
        Path input = resolvePath(sourcePath, baseDir);
        if (debug) {
            System.out.println("[mi-plugin] changeImageFormat source=" + sourcePath);
            System.out.println("[mi-plugin] changeImageFormat input=" + input);
        }
        if (!Files.exists(input)) {
            throw new TransformerException("No se encontro el fichero de imagen: " + input);
        }

        Path output = resolveOutputPath(readAttribute("output", context, element),
                readAttribute("suffix", context, element), input, format, baseDir);
        if (debug) {
            System.out.println("[mi-plugin] changeImageFormat output=" + output);
        }

        convertImage(input, output, format, debug);
        emitText(context, emitPath(output, baseDir), "No se pudo emitir la ruta convertida");
    }

    public static void ejecutar(XSLProcessorContext context, ElemExtensionCall element)
            throws TransformerException {
        String text = readAttr(element, "text", context);
        if (PluginSupport.isBlank(text)) {
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

    private static void convertImage(Path input, Path output, String format, boolean debug)
            throws TransformerException {
        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new TransformerException("No se pudo preparar la carpeta destino", e);
        }

        List<String[]> commands = buildMagickCommands(input, output);
        TransformerException lastError = null;
        for (String[] command : commands) {
            try {
                if (debug) {
                    System.out.println("[mi-plugin] trying command: " + String.join(" ", command));
                }
                runMagickCommand(command, format);
                return;
            } catch (TransformerException ex) {
                lastError = ex;
            }
        }
        if (lastError != null) {
            try {
                convertWithImageIO(input, output, format);
                return;
            } catch (Exception ignored) {
                throw lastError;
            }
        }
        throw new TransformerException("No se encontro un comando valido de ImageMagick para convertir la imagen");
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

    private static void runMagickCommand(String[] command, String format)
            throws TransformerException {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new TransformerException("No se pudo ejecutar ImageMagick (comando '" + command[0] + "')", e);
        }

        String log;
        try (InputStream stream = process.getInputStream()) {
            log = readProcessOutput(stream);
        } catch (Exception e) {
            process.destroyForcibly();
            throw new TransformerException("Fallo al leer salida de ImageMagick", e);
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

    private static String resolveMagickExecutable() {
        String configured = System.getProperty("xslt.magick");
        if (!PluginSupport.isBlank(configured)) {
            return configured.trim();
        }
        return "magick";
    }

    private static void convertWithImageIO(Path input, Path output, String format) throws Exception {
        String fmt = PluginSupport.normalizeFormat(format);
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

    private static String resolveNombre(XSLProcessorContext context, ElemExtensionCall element)
            throws TransformerException {
        String nombre = readAttribute("nombre", context, element);
        if (PluginSupport.isBlank(nombre)) {
            nombre = evaluateSelectAttribute(readAttribute("select", context, element), context, element);
        }
        return nombre;
    }

    private static String readAttr(ElemExtensionCall element, String name, XSLProcessorContext context)
            throws TransformerException {
        if (element == null || name == null || context == null) return null;
        org.w3c.dom.Node sourceNode = context.getContextNode();
        return element.getAttribute(name, sourceNode, context.getTransformer());
    }

    private static String readAttribute(String name, XSLProcessorContext context, ElemExtensionCall element)
            throws TransformerException {
        if (PluginSupport.isBlank(name) || context == null || element == null) {
            return null;
        }
        org.w3c.dom.Node sourceNode = context.getContextNode();
        return element.getAttribute(name, sourceNode, context.getTransformer());
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
                if (!PluginSupport.isBlank(docUri)) {
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

    private static Path resolveOutputPath(String outputAttr, String suffixAttr, Path input,
                                          String format, Path baseDir) {
        if (!PluginSupport.isBlank(outputAttr)) {
            return resolvePath(outputAttr, baseDir);
        }
        String fileName = input.getFileName() != null ? input.getFileName().toString() : "imagen";
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        String suffix = PluginSupport.isBlank(suffixAttr) ? "-converted" : suffixAttr;
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

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase(Locale.ROOT).contains("win");
    }

    private static void emitText(XSLProcessorContext context, String text, String errorMessage)
            throws TransformerException {
        try {
            context.outputToResultTree(context.getStylesheet(), text);
        } catch (TransformerException e) {
            throw e;
        } catch (Exception e) {
            throw new TransformerException(errorMessage, e);
        }
    }

    private static String evaluateSelectAttribute(String expression, XSLProcessorContext context,
                                                  ElemExtensionCall element)
            throws TransformerException {
        if (PluginSupport.isBlank(expression)) {
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
