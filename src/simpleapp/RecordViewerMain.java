package simpleapp;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class RecordViewerMain {
    private static final String PREF_LAST_DIR = "recordViewerLastDir";
    private final Preferences prefs = Preferences.userNodeForPackage(RecordViewerMain.class);

    private final JFrame frame = new JFrame("Visor Externo - Fichas Genericas");
    private final JLabel sourceLabel = new JLabel("Sin fichero cargado");
    private final DefaultListModel<RecordItem> recordListModel = new DefaultListModel<>();
    private final JList<RecordItem> recordList = new JList<>(recordListModel);
    private final JLabel titleLabel = new JLabel("Sin ficha");
    private final JLabel metaLabel = new JLabel("Carga un ZIP de ficha o un XML records/record.");
    private final JLabel imageLabel = new JLabel("Sin imagen", SwingConstants.CENTER);
    private final JTextArea summaryArea = new JTextArea();
    private final DefaultTableModel fieldModel = new ReadOnlyTableModel("Campo", "Ruta", "Valor");
    private final DefaultTableModel resourceModel = new ReadOnlyTableModel("Tipo", "Nombre", "Destino");
    private final JTable fieldTable = new JTable(fieldModel);
    private final JTable resourceTable = new JTable(resourceModel);

    private File lastChooserDir;
    private Path currentSource;
    private Path currentArchive;
    private Path extractedDir;
    private Document currentDocument;

    public RecordViewerMain() {
        loadLastChooserDir();

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton openBtn = new JButton("Abrir ficha");
        JButton exportAllBtn = new JButton("Exportar ficha completa");
        toolbar.add(openBtn);
        toolbar.add(exportAllBtn);
        toolbar.addSeparator();
        toolbar.add(sourceLabel);
        content.add(toolbar, BorderLayout.NORTH);

        recordList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane left = new JScrollPane(recordList);
        left.setBorder(BorderFactory.createTitledBorder("Records"));

        JPanel hero = new JPanel(new BorderLayout(12, 12));
        hero.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xD8, 0xCC, 0xBF)),
                new EmptyBorder(12, 12, 12, 12)));
        imageLabel.setPreferredSize(new Dimension(250, 340));
        imageLabel.setOpaque(true);
        imageLabel.setBackground(new Color(0xEE, 0xE7, 0xDD));
        hero.add(imageLabel, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 28f));
        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setRows(5);
        summaryArea.setOpaque(false);
        summaryArea.setBorder(null);
        info.add(titleLabel);
        info.add(Box.createVerticalStrut(6));
        info.add(metaLabel);
        info.add(Box.createVerticalStrut(12));
        info.add(summaryArea);
        hero.add(info, BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Campos", panelForTable(fieldTable, "Fields"));
        tabs.addTab("Recursos", panelForTable(resourceTable, "Resources"));

        JPanel right = new JPanel(new BorderLayout(10, 10));
        right.add(hero, BorderLayout.NORTH);
        right.add(tabs, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.28);
        content.add(split, BorderLayout.CENTER);

        openBtn.addActionListener(e -> chooseAndLoad());
        exportAllBtn.addActionListener(e -> exportCurrentDocument());
        recordList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showRecord(recordList.getSelectedValue());
        });

        frame.setContentPane(content);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1080, 700);
        frame.setLocationRelativeTo(null);
    }

    private JPanel panelForTable(JTable table, String title) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder(title));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void chooseAndLoad() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Abrir XML o ZIP");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("XML o ZIP", "xml", "zip"));
        if (lastChooserDir != null) chooser.setCurrentDirectory(lastChooserDir);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            loadRecords(chooser.getSelectedFile().toPath());
        }
    }

    private void loadRecords(Path source) {
        try {
            clearExtractedDir();
            rememberDirectory(source);
            Path xml = source;
            currentArchive = null;
            if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                extractedDir = Files.createTempDirectory("records_viewer_zip_");
                ZipUtils.extractZip(source, extractedDir);
                xml = findFirstXml(extractedDir);
                if (xml == null) throw new IllegalArgumentException("No se encontro XML dentro del ZIP.");
                currentArchive = source;
            }
            currentSource = xml;
            currentDocument = XmlUtils.parse(xml);
            recordListModel.clear();
            for (RecordItem item : parseRecords(currentDocument)) recordListModel.addElement(item);
            sourceLabel.setText(currentArchive != null
                    ? currentArchive.toAbsolutePath() + " -> " + currentSource.getFileName()
                    : currentSource.toAbsolutePath().toString());
            if (!recordListModel.isEmpty()) recordList.setSelectedIndex(0);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "No se pudo cargar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path findFirstXml(Path dir) throws Exception {
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .findFirst().orElse(null);
        }
    }

    private List<RecordItem> parseRecords(Document doc) {
        List<RecordItem> items = new ArrayList<>();
        Element root = doc.getDocumentElement();
        if ("record".equals(root.getTagName())) {
            items.add(parseRecord(root));
            return items;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "record".equals(child.getNodeName())) {
                items.add(parseRecord((Element) child));
            }
        }
        return items;
    }

    private RecordItem parseRecord(Element recordEl) {
        RecordItem record = new RecordItem();
        record.id = recordEl.getAttribute("id");
        record.type = recordEl.getAttribute("type");
        record.element = recordEl;
        record.title = childText(recordEl, "title");
        if (record.title.isBlank()) record.title = record.id;
        NodeList children = recordEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) child;
            if ("field".equals(el.getTagName())) {
                record.fields.add(new FieldItem(el.getAttribute("label"), el.getAttribute("path"), el.getTextContent().trim()));
            } else if ("resource".equals(el.getTagName())) {
                record.resources.add(new ResourceItem(el.getAttribute("type"), el.getAttribute("name"), el.getTextContent().trim()));
            }
        }
        return record;
    }

    private void showRecord(RecordItem record) {
        clearTable(fieldModel);
        clearTable(resourceModel);
        if (record == null) return;
        titleLabel.setText(record.title);
        metaLabel.setText("id=" + record.id + " | type=" + record.type + " | resources=" + record.resources.size());
        summaryArea.setText(buildSummary(record));
        updateImage(record);
        for (FieldItem field : record.fields) fieldModel.addRow(new Object[]{field.label, field.path, field.value});
        for (ResourceItem resource : record.resources) {
            resourceModel.addRow(new Object[]{resource.type, resource.name, resource.target});
        }
    }

    private String buildSummary(RecordItem record) {
        for (FieldItem field : record.fields) {
            String path = field.path.toLowerCase(Locale.ROOT);
            if (path.endsWith("/sinopsis") || path.endsWith("/descripcion") || path.endsWith("/nota") || path.endsWith("/resumen")) {
                return field.value;
            }
        }
        return !record.fields.isEmpty() ? record.fields.get(0).value : "Sin resumen.";
    }

    private void updateImage(RecordItem record) {
        imageLabel.setIcon(null);
        imageLabel.setText("Sin imagen");
        for (String preferred : List.of("poster", "retrato", "caricatura", "imagen")) {
            for (ResourceItem resource : record.resources) {
                if (preferred.equalsIgnoreCase(resource.name)) {
                    Path resolved = resolveResourcePath(resource);
                    if (resolved != null && Files.exists(resolved)) {
                        ImageIcon icon = new ImageIcon(resolved.toString());
                        Image scaled = icon.getImage().getScaledInstance(250, 340, Image.SCALE_SMOOTH);
                        imageLabel.setText("");
                        imageLabel.setIcon(new ImageIcon(scaled));
                    }
                    return;
                }
            }
        }
    }

    private Path resolveResourcePath(ResourceItem resource) {
        String target = resource.target != null ? resource.target.trim() : "";
        if (target.isBlank()) return null;
        if (target.toLowerCase(Locale.ROOT).startsWith("file:/")) return Paths.get(URI.create(target));
        Path base = currentSource != null && currentSource.getParent() != null ? currentSource.getParent() : Paths.get(".");
        return base.resolve(target).normalize();
    }

    private void exportCurrentDocument() {
        if (currentDocument == null) return;
        exportNode(currentDocument, "records.xml", getAllLocalResources());
    }

    private void exportNode(Node node, String defaultName, Set<Path> resources) {
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Exportar ficha");
            chooser.setSelectedFile(new File(defaultName));
            var xmlFilter = new javax.swing.filechooser.FileNameExtensionFilter("XML", "xml");
            var zipFilter = new javax.swing.filechooser.FileNameExtensionFilter("ZIP", "zip");
            chooser.addChoosableFileFilter(xmlFilter);
            chooser.addChoosableFileFilter(zipFilter);
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(defaultName.toLowerCase(Locale.ROOT).endsWith(".zip") ? zipFilter : xmlFilter);
            if (lastChooserDir != null) chooser.setCurrentDirectory(lastChooserDir);
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;

            File selected = chooser.getSelectedFile();
            Path target = selected.toPath();
            rememberDirectory(target);
            String selectedName = target.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean zipSelected = chooser.getFileFilter() == zipFilter;
            boolean asZip = selectedName.endsWith(".zip") || (zipSelected && !selectedName.endsWith(".xml"));
            if (asZip) {
                if (!selectedName.endsWith(".zip")) {
                    target = target.resolveSibling(target.getFileName().toString() + ".zip");
                }
                exportAsZip(node, target, defaultName, resources);
            } else {
                if (!selectedName.endsWith(".xml")) {
                    target = target.resolveSibling(target.getFileName().toString() + ".xml");
                }
                Files.write(target, XmlUtils.nodeToPrettyString(node).getBytes(StandardCharsets.UTF_8));
            }
            sourceLabel.setText("Exportado: " + target.toAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "No se pudo exportar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportAsZip(Node node, Path target, String xmlEntryName, Set<Path> resources) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            zos.putNextEntry(new ZipEntry(xmlEntryName));
            zos.write(XmlUtils.nodeToPrettyString(node).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            Path base = currentSource != null && currentSource.getParent() != null ? currentSource.getParent().normalize() : null;
            for (Path resource : resources) {
                if (resource == null || !Files.exists(resource)) continue;
                String entryName = base != null && resource.normalize().startsWith(base)
                        ? base.relativize(resource.normalize()).toString().replace('\\', '/')
                        : resource.getFileName().toString();
                zos.putNextEntry(new ZipEntry(entryName));
                try (var in = Files.newInputStream(resource)) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }

    private Set<Path> getAllLocalResources() {
        List<RecordItem> items = new ArrayList<>();
        for (int i = 0; i < recordListModel.size(); i++) items.add(recordListModel.get(i));
        return getLocalResources(items);
    }

    private Set<Path> getLocalResources(List<RecordItem> records) {
        Set<Path> resources = new LinkedHashSet<>();
        for (RecordItem record : records) {
            for (ResourceItem resource : record.resources) {
                String lower = resource.target.toLowerCase(Locale.ROOT);
                if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:/")) continue;
                Path resolved = resolveResourcePath(resource);
                if (resolved != null && Files.exists(resolved)) resources.add(resolved);
            }
        }
        return resources;
    }

    private String childText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }

    private void loadLastChooserDir() {
        String saved = prefs.get(PREF_LAST_DIR, null);
        if (saved == null || saved.isBlank()) return;
        File dir = new File(saved);
        if (dir.isDirectory()) lastChooserDir = dir;
    }

    private void persistLastChooserDir() {
        if (lastChooserDir == null) return;
        prefs.put(PREF_LAST_DIR, lastChooserDir.getAbsolutePath());
        try {
            prefs.flush();
        } catch (Exception ignored) {
            // Si el backend de preferencias falla, la app sigue funcionando.
        }
    }

    private void clearExtractedDir() {
        if (extractedDir != null) {
            try { XmlUtils.deleteDirectoryRecursively(extractedDir); } catch (Exception ignored) {}
            extractedDir = null;
        }
    }

    private void rememberDirectory(Path path) {
        if (path == null) return;
        Path absolute = path.toAbsolutePath().normalize();
        Path dir = Files.isDirectory(absolute) ? absolute : absolute.getParent();
        if (dir == null) return;
        File file = dir.toFile();
        if (!file.isDirectory()) return;
        lastChooserDir = file;
        persistLastChooserDir();
    }

    private void clearTable(DefaultTableModel model) {
        while (model.getRowCount() > 0) model.removeRow(0);
    }

    private void showUI() {
        frame.setVisible(true);
    }

    private static class RecordItem {
        private String id;
        private String type;
        private String title;
        private Element element;
        private final List<FieldItem> fields = new ArrayList<>();
        private final List<ResourceItem> resources = new ArrayList<>();

        @Override
        public String toString() {
            return title + (type == null || type.isBlank() ? "" : " [" + type + "]");
        }
    }

    private record FieldItem(String label, String path, String value) {}
    private record ResourceItem(String type, String name, String target) {}

    private static class ReadOnlyTableModel extends DefaultTableModel {
        private ReadOnlyTableModel(Object... columnNames) {
            super(columnNames, 0);
        }
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            RecordViewerMain viewer = new RecordViewerMain();
            if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
                viewer.loadRecords(Paths.get(args[0]));
            }
            viewer.showUI();
        });
    }
}
