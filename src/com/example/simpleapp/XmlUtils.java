package com.example.simpleapp;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class XmlUtils {
    private static final Path DEFAULT_COLLECTION_SCHEMA = Paths.get("samples", "collection.xsd");
    private static Schema collectionSchema;

    public static Document parse(Path xmlPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setIgnoringComments(true);
        dbf.setCoalescing(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(xmlPath.toFile());
        try {
            doc.setDocumentURI(xmlPath.toUri().toString());
        } catch (Exception ignored) {
            // Si no se puede fijar, se sigue sin URI (afectara a elementos de extension que resuelvan rutas relativas)
        }
        return doc;
    }

    public static String toPrettyString(Document doc) throws Exception {
        return nodeToPrettyString(doc);
    }

    public static String nodeToPrettyString(Node node) throws Exception {
        if (node == null) return "";
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        if (!(node instanceof Document)) {
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        }
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(node), new StreamResult(sw));
        return sw.toString();
    }

    public static Document transform(Document source, Path xsltPath) throws Exception {
        XsltExtensionPreprocessor.PreprocessedXslt preprocessed =
                XsltExtensionPreprocessor.preprocessIfNeeded(xsltPath);
        Path effectiveXslt = preprocessed != null ? preprocessed.getXsltPath() : xsltPath;

        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        ClassLoader bridgeCl = preprocessed != null ? preprocessed.getClassLoader() : null;
        if (bridgeCl != null) {
            Thread.currentThread().setContextClassLoader(bridgeCl);
        }

        TransformerFactory tf = newXsltFactory(bridgeCl);
        TransformationClassResolver resolver = TransformationClassResolver.getInstance();
        resolver.reset();
        resolver.resolveFromXslt(effectiveXslt);
        try (InputStream in = Files.newInputStream(effectiveXslt)) {
            Transformer transformer = tf.newTransformer(new StreamSource(in));
            DOMResult result = new DOMResult();
            transformer.transform(new DOMSource(source), result);
            return (Document) result.getNode();
        } finally {
            if (bridgeCl != null) {
                Thread.currentThread().setContextClassLoader(originalCl);
            }
            if (preprocessed != null) {
                preprocessed.cleanup();
            }
        }
    }

    public static boolean isCollectionDocument(Document doc) {
        if (doc == null || doc.getDocumentElement() == null) return false;
        String local = doc.getDocumentElement().getLocalName();
        String name = local != null ? local : doc.getDocumentElement().getNodeName();
        return "dc".equals(name);
    }

    public static void validateCollection(Document doc) throws Exception {
        if (!isCollectionDocument(doc)) {
            throw new IllegalArgumentException("El documento no tiene raiz 'dc'");
        }
        Validator validator = getCollectionSchema().newValidator();
        validator.validate(new DOMSource(doc));
    }

    public static Path getDefaultCollectionSchemaPath() {
        return DEFAULT_COLLECTION_SCHEMA;
    }

    private static synchronized Schema getCollectionSchema() throws Exception {
        if (collectionSchema == null) {
            if (!Files.exists(DEFAULT_COLLECTION_SCHEMA)) {
                throw new IllegalStateException("No se encontro el esquema en " +
                        DEFAULT_COLLECTION_SCHEMA.toAbsolutePath());
            }
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            collectionSchema = sf.newSchema(DEFAULT_COLLECTION_SCHEMA.toFile());
        }
        return collectionSchema;
    }

    public static void deleteDirectoryRecursively(Path dir) throws Exception {
        if (dir == null) return;
        // Handle files first, then directories
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }

    private static TransformerFactory newXsltFactory() {
        return newXsltFactory(XmlUtils.class.getClassLoader());
    }

    private static TransformerFactory newXsltFactory(ClassLoader loader) {
        ClassLoader effective = loader != null ? loader : XmlUtils.class.getClassLoader();
        try {
            return TransformerFactory.newInstance(
                    "org.apache.xalan.processor.TransformerFactoryImpl",
                    effective);
        } catch (TransformerFactoryConfigurationError error) {
            try {
                return TransformerFactory.newInstance(null, effective);
            } catch (TransformerFactoryConfigurationError fallback) {
                return TransformerFactory.newInstance();
            }
        }
    }
}
