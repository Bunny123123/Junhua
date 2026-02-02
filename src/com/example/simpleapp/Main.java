package com.example.simpleapp;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Main {
    private final JFrame frame = new JFrame("Procesador XML/XSLT - Colecciones");
    private final JLabel status = new JLabel("Listo");
    private final JTextArea previewArea = new JTextArea();
    private final JTextArea nodeDetailArea = new JTextArea();
    private final JTree tree = new JTree(new DefaultMutableTreeNode("Colección"));
    private final JButton exportBtn = new JButton("Exportar");

    private File selectedZip;
    private File selectedXslt;
    private Path extractedDir;
    private Document collectionDoc;
    private String lastResultText;

    public Main() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Top toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton openZipBtn = new JButton("Elegir ZIP");
        JButton openXsltBtn = new JButton("Elegir XSLT");
        JButton transformBtn = new JButton("Transformar");
        JButton clearBtn = new JButton("Limpiar");
        toolbar.add(openZipBtn);
        toolbar.add(openXsltBtn);
        toolbar.add(transformBtn);
        toolbar.add(exportBtn);
        toolbar.addSeparator();
        toolbar.add(clearBtn);
        content.add(toolbar, BorderLayout.NORTH);

        // Split: left tree + detail, right preview
        previewArea.setEditable(false);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane right = new JScrollPane(previewArea);
        JScrollPane treeScroll = new JScrollPane(tree);
        nodeDetailArea.setEditable(false);
        nodeDetailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        nodeDetailArea.setLineWrap(false);
        nodeDetailArea.setText("Selecciona un nodo para ver su detalle XML.");
        JScrollPane detailScroll = new JScrollPane(nodeDetailArea);
        detailScroll.setBorder(BorderFactory.createTitledBorder("Detalle del nodo"));
        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScroll, detailScroll);
        leftSplit.setResizeWeight(0.7);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, right);
        split.setResizeWeight(0.35);
        content.add(split, BorderLayout.CENTER);

        // Status bar
        status.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        content.add(status, BorderLayout.SOUTH);

        // Menu
        JMenuBar menuBar = new JMenuBar();
        JMenu archivo = new JMenu("Archivo");
        JMenuItem salir = new JMenuItem("Salir");
        salir.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        salir.addActionListener(e -> frame.dispose());
        archivo.add(salir);
        menuBar.add(archivo);
        frame.setJMenuBar(menuBar);

        // Actions
        openZipBtn.addActionListener(e -> chooseZip());
        openXsltBtn.addActionListener(e -> chooseXslt());
        transformBtn.addActionListener(e -> transform());
        clearBtn.addActionListener(e -> clearState());
        exportBtn.setEnabled(false);
        exportBtn.addActionListener(e -> export());
        tree.addTreeSelectionListener(e -> showSelectedNodeDetails());

        frame.setContentPane(content);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);
    }

    private void chooseZip() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar ZIP de colección");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("ZIP", "zip"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            selectedZip = chooser.getSelectedFile();
            status.setText("ZIP seleccionado: " + selectedZip.getName());
            loadZip(selectedZip);
        }
    }

    private void chooseXslt() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar hoja XSLT");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("XSL/XSLT", "xsl", "xslt"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            selectedXslt = chooser.getSelectedFile();
            status.setText("XSLT seleccionado: " + selectedXslt.getName());
        }
    }

    private void loadZip(File zip) {
        try {
            clearExtractedDir();
            XsltExtensions.resetConvertedFiles();
            extractedDir = Files.createTempDirectory("coleccion_zip_");
            ZipUtils.extractZip(zip.toPath(), extractedDir);
            // Heurística: buscar el primer .xml como descripción de colección
            Path xml = Files.walk(extractedDir)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".xml"))
                    .findFirst()
                    .orElse(null);
            if (xml == null) {
                setError("No se encontró ningún XML dentro del ZIP");
                return;
            }
            collectionDoc = XmlUtils.parse(xml);
            XmlUtils.validateCollection(collectionDoc);
            previewArea.setText(XmlUtils.toPrettyString(collectionDoc));
            buildTree(collectionDoc);
            lastResultText = null;
            exportBtn.setEnabled(false);
            status.setText("Coleccion valida: " + xml.getFileName());
        } catch (Exception ex) {
            setError("Error al cargar ZIP: " + ex.getMessage());
            lastResultText = null;
            exportBtn.setEnabled(false);
            nodeDetailArea.setText("Selecciona un nodo para ver su detalle XML.");
        }
    }

    private void transform() {
        if (collectionDoc == null) {
            setError("Primero carga un ZIP con la colección");
            return;
        }
        if (selectedXslt == null) {
            setError("Selecciona una hoja de transformación XSLT");
            return;
        }
        XsltExtensions.resetConvertedFiles();
        try {
            Document result = XmlUtils.transform(collectionDoc, selectedXslt.toPath());
            boolean validated = false;
            if (XmlUtils.isCollectionDocument(result)) {
                XmlUtils.validateCollection(result);
                validated = true;
            }
            String formatted = XmlUtils.toPrettyString(result);
            previewArea.setText(formatted);
            lastResultText = formatted;
            exportBtn.setEnabled(true);
            if (validated) {
                status.setText("Transformacion valida segun XSD");
            } else {
                String rootName = result.getDocumentElement() != null
                        ? result.getDocumentElement().getNodeName()
                        : "desconocido";
                status.setText("Transformacion aplicada (sin validacion, raiz: " + rootName + ")");
            }
        } catch (Exception ex) {
            setError("Error al transformar: " + ex.getMessage());
            lastResultText = null;
            exportBtn.setEnabled(false);
        }
    }

    private void clearState() {
        selectedZip = null;
        selectedXslt = null;
        collectionDoc = null;
        lastResultText = null;
        previewArea.setText("");
        tree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Colección")));
        clearExtractedDir();
        XsltExtensions.resetConvertedFiles();
        exportBtn.setEnabled(false);
        nodeDetailArea.setText("Selecciona un nodo para ver su detalle XML.");
        status.setText("Listo");
    }

    private void clearExtractedDir() {
        if (extractedDir != null) {
            try { XmlUtils.deleteDirectoryRecursively(extractedDir); } catch (Exception ignored) {}
            extractedDir = null;
        }
    }

    private void buildTree(Document doc) {
        Element root = doc.getDocumentElement();
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new NodeInfo(root.getTagName(), root));

        NodeList objects = root.getElementsByTagName("o");
        for (int i = 0; i < objects.getLength(); i++) {
            Element o = (Element) objects.item(i);
            String id = o.getAttribute("id");
            DefaultMutableTreeNode oNode = new DefaultMutableTreeNode(
                    new NodeInfo("o:" + (id.isEmpty() ? "(sin id)" : id), o));

            // metadatos: primer hijo distinto de rs/rels
            DefaultMutableTreeNode metaNode = new DefaultMutableTreeNode("metadatos");
            for (Node child = o.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    String name = child.getNodeName();
                    if (!"rs".equals(name) && !"rels".equals(name)) {
                        metaNode.add(new DefaultMutableTreeNode(new NodeInfo(name, child)));
                    }
                }
            }
            if (metaNode.getChildCount() > 0) oNode.add(metaNode);

            // recursos
            NodeList rsList = o.getElementsByTagName("rs");
            if (rsList.getLength() > 0) {
                Element rs = (Element) rsList.item(0);
                DefaultMutableTreeNode rsNode = new DefaultMutableTreeNode(new NodeInfo("recursos", rs));
                for (Node child = rs.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element el = (Element) child;
                        String name = el.getAttribute("name");
                        String label = el.getTagName() + (name.isEmpty() ? "" : ("(" + name + ")"));
                        rsNode.add(new DefaultMutableTreeNode(new NodeInfo(label, el)));
                    }
                }
                oNode.add(rsNode);
            }

            // relaciones
            NodeList relsList = o.getElementsByTagName("rels");
            if (relsList.getLength() > 0) {
                Element rels = (Element) relsList.item(0);
                DefaultMutableTreeNode relsNode = new DefaultMutableTreeNode(new NodeInfo("relaciones", rels));
                for (Node child = rels.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() == Node.ELEMENT_NODE && "rel".equals(child.getNodeName())) {
                        Element el = (Element) child;
                        String ref = el.getAttribute("ref");
                        String name = el.getAttribute("name");
                        if (ref.isEmpty()) {
                            // tolerar ejemplo con atributo id en lugar de ref
                            ref = el.getAttribute("id");
                        }
                        String label = "rel:" + (name.isEmpty() ? "?" : name) + "->" + (ref.isEmpty() ? "?" : ref);
                        relsNode.add(new DefaultMutableTreeNode(new NodeInfo(label, el)));
                    }
                }
                oNode.add(relsNode);
            }

            rootNode.add(oNode);
        }

        tree.setModel(new DefaultTreeModel(rootNode));
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        if (tree.getRowCount() > 0) {
            tree.setSelectionRow(0);
            showSelectedNodeDetails();
        }
    }

    private void showSelectedNodeDetails() {
        if (tree.getSelectionPath() == null) {
            nodeDetailArea.setText("Selecciona un nodo para ver su detalle XML.");
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getSelectionPath().getLastPathComponent();
        Object userObject = node.getUserObject();
        if (userObject instanceof NodeInfo info && info.node != null) {
            try {
                nodeDetailArea.setText(XmlUtils.nodeToPrettyString(info.node));
            } catch (Exception ex) {
                nodeDetailArea.setText("No se pudo mostrar el nodo: " + ex.getMessage());
            }
        } else if (userObject != null) {
            nodeDetailArea.setText(userObject.toString());
        } else {
            nodeDetailArea.setText("");
        }
    }

    private void setError(String msg) {
        status.setText(msg);
        JOptionPane.showMessageDialog(frame, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void export() {
        if (lastResultText == null) {
            setError("No hay ningún resultado transformado para exportar");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar resultado transformado");
        chooser.setSelectedFile(new File("resultado.zip"));
        chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("ZIP (coleccion transformada)", "zip"));
        chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("HTML o XML", "html", "htm", "xml"));
        chooser.setFileFilter(chooser.getChoosableFileFilters()[0]);
        if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            try {
                Path targetPath = target.toPath();
                boolean asZip = target.getName().toLowerCase(Locale.ROOT).endsWith(".zip");
                if (asZip) {
                    exportAsZip(targetPath);
                } else {
                    Files.write(targetPath, lastResultText.getBytes(StandardCharsets.UTF_8));
                }
                status.setText("Resultado exportado: " + target.getName());
            } catch (Exception ex) {
                setError("No se pudo exportar: " + ex.getMessage());
            }
        }
    }

    private void exportAsZip(Path target) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            // Resultado transformado
            zos.putNextEntry(new ZipEntry("transformed.xml"));
            zos.write(lastResultText.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Recursos convertidos para inspeccionar el cambio de formato
            if (extractedDir != null && Files.isDirectory(extractedDir)) {
                Set<Path> filesToZip = new LinkedHashSet<>(XsltExtensions.snapshotConvertedFiles());
                try (var stream = Files.walk(extractedDir)) {
                    for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (name.contains("-converted") || isResourcesPath(file)) {
                            filesToZip.add(file);
                        }
                    }
                }
                for (Path file : filesToZip) {
                    if (file == null || !Files.exists(file)) continue;
                    String entryName = buildZipEntryName(file);
                    if (entryName.isEmpty()) continue;
                    ZipEntry entry = new ZipEntry(entryName);
                    zos.putNextEntry(entry);
                    try (var in = Files.newInputStream(file)) {
                        in.transferTo(zos);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    private boolean isResourcesPath(Path file) {
        Path normalized = file.normalize();
        for (Path part : normalized) {
            String name = part != null && part.getFileName() != null
                    ? part.getFileName().toString().toLowerCase(Locale.ROOT)
                    : "";
            if ("resources".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String buildZipEntryName(Path file) {
        Path normalized = file.normalize();
        if (extractedDir != null) {
            try {
                Path base = extractedDir.normalize();
                if (normalized.startsWith(base)) {
                    return base.relativize(normalized).toString().replace('\\', '/');
                }
            } catch (Exception ignored) {
                // si falla la relativizacion, cae al nombre simple
            }
        }
        Path name = normalized.getFileName();
        return name != null ? name.toString() : "";
    }

    private void showUI() { frame.setVisible(true); }

    private static class NodeInfo {
        private final String label;
        private final Node node;

        private NodeInfo(String label, Node node) {
            this.label = label;
            this.node = node;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new Main().showUI());
    }
}
