package de.modernhelden;

import helden.plugin.HeldenXMLDatenPlugin3;
import helden.plugin.datenxmlplugin.DatenAustausch3Interface;
import de.modernhelden.print.PrintPreviewPanel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Modern-UI für die Helden-Software (DSA 4.1):
 *  – modernes Erscheinungsbild (FlatLaf hell/dunkel) für die gesamte Software
 *  – Tooltips zu Talenten, Sonderfertigkeiten, Vor- und Nachteilen überall
 *  – eigener Reiter mit moderner Heldenübersicht
 * Alle Funktionen der Helden-Software bleiben unverändert erhalten.
 */
public class ModernHeldenPlugin implements HeldenXMLDatenPlugin3 {

    static final String PLUGIN_NAME = "modernui";
    static final String VERSION = version();

    private DatenAustausch3Interface daten;
    private JFrame frame;
    private final TooltipDB db = new TooltipDB();
    private final TooltipHook hook = new TooltipHook(db);
    private HeroPanel panel;
    private PrintPreviewPanel printPanel;
    private final Map<String, String> props = new HashMap<>();
    private File baseDir;
    private Timer refreshTimer;
    private static boolean themeApplied;
    private static de.modernhelden.update.PluginUpdate update;

    // ------------------------------------------------------------ HeldenPlugin

    @Override public String getType() { return DATEN; }
    @Override public String getMenuName() { return "Modern-UI"; }
    @Override public String getToolTipText() { return "Moderne Oberfläche und Tooltips für Talente und Sonderfertigkeiten"; }
    @Override public ImageIcon getIcon() { return new ImageIcon(icon(16)); }
    @Override public void doWork(JFrame f) { click(); }

    // ------------------------------------------------------------ HeldenXMLDatenPlugin3

    @Override public boolean hatMenu() { return true; }
    @Override public boolean hatTab() { return true; }

    @Override
    public void init(DatenAustausch3Interface d, JFrame f) {
        this.daten = d;
        this.frame = f;
        loadProps();
        String pfad = query("pluginPfad");
        baseDir = new File(pfad != null && !pfad.isEmpty() ? pfad : System.getProperty("user.home"), "modern-ui");
        db.setUserFile(new File(baseDir, "eigene-tooltips.tsv"));
        hook.setEnabled(!"false".equals(props.get("tooltips")));
        hook.install(this::editLastHovered);
        if (new File(baseDir, "debug").isDirectory()) hook.debugLog = new File(new File(baseDir, "debug"), "hook.log");
        if (new File(baseDir, "debug").isDirectory()) IconSwap.debugLog = new File(new File(baseDir, "debug"), "icons.log");
        if (update == null) {
            update = new de.modernhelden.update.PluginUpdate(baseDir, ModernHeldenPlugin.class, VERSION, frame);
            update.beimStart();
        }
        refreshTimer = new Timer(250, e -> refresh());
        refreshTimer.setRepeats(false);
        d.addChangeListener(e -> refreshTimer.restart());
        if (!themeApplied) {
            themeApplied = true;
            final String mode = props.containsKey("theme") ? props.get("theme") : Theme.HELL;
            // Erst umschalten, wenn das Hauptfenster vollständig aufgebaut ist
            final long start = System.currentTimeMillis();
            final int[] stableTicks = {0};
            final Timer t = new Timer(300, null);
            t.addActionListener(e -> {
                boolean ready = frame != null && frame.isShowing();
                stableTicks[0] = ready ? stableTicks[0] + 1 : 0;
                if (stableTicks[0] >= 4 || System.currentTimeMillis() - start > 30000) {
                    t.stop();
                    Theme.apply(mode);
                    installDruckvorschau();
                    refresh();
                }
            });
            t.start();
        }
    }

    @Override
    public JComponent getPanel() {
        if (panel == null) {
            panel = new HeroPanel(db, this::editTooltip);
            panel.setAbhaengige((sf, spaeter) -> Rueckgaengig.abhaengige(frame, sf, spaeter));
            panel.setRueckgaengig(e -> {
                String f = Rueckgaengig.ausfuehren(frame, e.time, e.text);
                refresh();
                return f;
            });
            SwingUtilities.invokeLater(this::refresh);
        }
        return panel;
    }

    @Override
    public void click() {
        refresh();
    }

    @Override
    public ArrayList<JComponent> getUntermenus() {
        ArrayList<JComponent> l = new ArrayList<>();
        ButtonGroup g = new ButtonGroup();
        String[][] modes = {{Theme.HELL, "Design: Modern hell"}, {Theme.DUNKEL, "Design: Modern dunkel"}, {Theme.ORIGINAL, "Design: Original"}};
        String cur = props.containsKey("theme") ? props.get("theme") : Theme.HELL;
        for (final String[] m : modes) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(m[1], m[0].equals(cur));
            it.addActionListener(e -> {
                Theme.apply(m[0]);
                saveProp("theme", m[0]);
                refresh();
            });
            g.add(it);
            l.add(it);
        }
        l.add(new JSeparator());
        final JCheckBoxMenuItem tips = new JCheckBoxMenuItem("Tooltips für Talente & Sonderfertigkeiten", hook.isEnabled());
        tips.addActionListener(e -> {
            hook.setEnabled(tips.isSelected());
            saveProp("tooltips", String.valueOf(tips.isSelected()));
        });
        l.add(tips);
        JMenuItem edit = new JMenuItem("Tooltip-Text des zuletzt gezeigten Eintrags bearbeiten …");
        edit.setAccelerator(KeyStroke.getKeyStroke("ctrl shift T"));
        edit.addActionListener(e -> editLastHovered());
        l.add(edit);
        JMenuItem folder = new JMenuItem("Ordner mit eigenen Tooltip-Texten öffnen");
        folder.addActionListener(e -> {
            try {
                baseDir.mkdirs();
                Desktop.getDesktop().open(baseDir);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Ordner: " + baseDir.getAbsolutePath());
            }
        });
        l.add(folder);
        l.add(new JSeparator());
        JMenuItem upd = new JMenuItem("Nach Updates suchen …");
        upd.addActionListener(e -> updateSuchen());
        l.add(upd);
        JMenuItem about = new JMenuItem("Über Modern-UI …");
        about.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                "<html><b>Modern-UI " + VERSION + "</b><br><br>"
                        + "Moderne Darstellung und Tooltips für die Helden-Software.<br>"
                        + "Die Kurzbeschreibungen sind eigene Zusammenfassungen, keine Regeltexte.<br>"
                        + "Maßgeblich sind die Regelbücher und die Berechnungen der Helden-Software.<br><br>"
                        + "Eigene Tooltip-Texte: " + db.getUserFile() + "<br>"
                        + "Automatische Aktualisierung: " + (de.modernhelden.update.PluginUpdate.heldenAutoUpdate()
                            ? "ein (folgt der Einstellung der Helden-Software)" : "aus (in der Helden-Software ausgeschaltet)") + "<br>"
                        + "Design-Bibliothek: FlatLaf (Apache-Lizenz 2.0)</html>",
                "Modern-UI", JOptionPane.INFORMATION_MESSAGE));
        l.add(about);
        return l;
    }

    /** Manuelle Prüfung über das Menü – unabhängig von der automatischen Prüfung. */
    private void updateSuchen() {
        if (update == null) return;
        if (update.laeuft()) {
            JOptionPane.showMessageDialog(frame, "Die Prüfung läuft bereits.", "Modern-UI", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final Cursor alt = frame != null ? frame.getCursor() : null;
        if (frame != null) frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new Thread(() -> {
            final de.modernhelden.update.PluginUpdate.Ergebnis r = update.pruefen();
            SwingUtilities.invokeLater(() -> {
                if (frame != null) frame.setCursor(alt);
                String text = r.text;
                if (!de.modernhelden.update.PluginUpdate.heldenAutoUpdate() && !r.neu && !text.startsWith("Update-Prüfung"))
                    text = "<html>" + text + "<br><br>Die automatische tägliche Prüfung ist aus, weil in der Helden-Software<br>"
                            + "„automatisch aktualisieren“ ausgeschaltet ist.</html>";
                JOptionPane.showMessageDialog(frame, text, "Modern-UI",
                        text.startsWith("Update-Prüfung") ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
            });
        }, "Modern-UI-Update-manuell").start();
    }

    // ------------------------------------------------------------ Logik

    private void refresh() {
        HeroData h = null;
        try {
            Object res = exec("exportHeld", "id", "selected");
            if (res instanceof Document) {
                h = HeroData.parse((Document) res, db);
                debugDump("exportHeld", (Document) res);
                if (baseDir != null && new File(baseDir, "debug").isDirectory()) {
                    for (String v : new String[]{"1", "2", "3", "4"}) {
                        try {
                            Object o = exec("held", "id", "selected", "format", "xml", "version", v);
                            if (o instanceof Document) debugDump("held-v" + v, (Document) o);
                        } catch (Exception ignore) { }
                    }
                }
            }
        } catch (Exception e) {
            // kein Held ausgewählt
        }
        hook.setHero(h);
        if (panel != null) panel.showHero(h);
        installDruckvorschau();
        if (printPanel != null) printPanel.markDirty();
    }

    /** Ersetzt den Inhalt des Reiters „Druckvorschau“ durch die moderne Vorschau (Original bleibt per Umschalter erreichbar). */
    private void installDruckvorschau() {
        if (frame == null || "false".equals(props.get("druck.ersetzen"))) return;
        try {
            if (printPanel == null) {
                printPanel = new PrintPreviewPanel(db, this::heldDaten, props, this::saveProp);
            }
            JTabbedPane tabs = findTabs(frame.getContentPane());
            if (tabs == null) return;
            int idx = tabs.indexOfTab("Druckvorschau");
            if (idx < 0) return;
            Component comp = tabs.getComponentAt(idx);
            if (comp == printPanel) return;
            tabs.setComponentAt(idx, printPanel);
            if (comp instanceof JComponent) printPanel.setOriginal((JComponent) comp);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private static JTabbedPane findTabs(Container c) {
        for (Component ch : c.getComponents()) {
            if (ch instanceof JTabbedPane && ((JTabbedPane) ch).indexOfTab("Druckvorschau") >= 0) return (JTabbedPane) ch;
            if (ch instanceof Container) {
                JTabbedPane t = findTabs((Container) ch);
                if (t != null) return t;
            }
        }
        return null;
    }

    /**
     * Liest bzw. setzt die Druckeinstellung „Metatalente drucken“ des Helden in der Helden-Software.
     * Die Software berechnet Meta-Talente nur, wenn sie aktiv ist; das Plugin schaltet sie
     * daher nur für die Dauer der Abfrage ein und stellt den alten Wert wieder her.
     * @param heldenkey Schlüssel des Helden
     * @param wert null = nur lesen
     * @return bisheriger Wert oder null, falls nicht verfügbar
     */
    /** Berechnete Heldendaten (inkl. Waffen, Rüstung, Kampfwerte) über die Plugin-Schnittstelle. */
    private Document heldDaten() {
        try {
            Object sel = exec("selectedHeld");
            if (!(sel instanceof Document)) return null;   // kein Held ausgewählt
            Object o = exec("held", "id", "selected", "format", "xml", "version", "3");
            return o instanceof Document ? (Document) o : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void editLastHovered() {
        TooltipDB.Match m = hook.getLastMatch();
        if (m == null) {
            JOptionPane.showMessageDialog(frame, "Fahre zuerst mit der Maus über ein Talent, eine Sonderfertigkeit, einen Vor-/Nachteil oder einen Zauber.",
                    "Modern-UI", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        editTooltip(m);
    }

    private void editTooltip(TooltipDB.Match m) {
        String key = m.entry != null && db.getUserText(m.key) == null ? m.entry.name : m.key;
        String current = db.getUserText(key);
        JTextArea area = new JTextArea(current == null ? "" : current, 10, 44);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        area.addAncestorListener(new javax.swing.event.AncestorListener() {
            public void ancestorAdded(javax.swing.event.AncestorEvent e) { SwingUtilities.invokeLater(area::requestFocusInWindow); }
            public void ancestorRemoved(javax.swing.event.AncestorEvent e) { }
            public void ancestorMoved(javax.swing.event.AncestorEvent e) { }
        });
        String info = "<html><div style='width:380px'><b>" + key + "</b><br>Eigener Text für den Tooltip (nur für deinen privaten Gebrauch).<br>"
                + "Leer lassen, um den eigenen Text zu entfernen.";
        if (m.entry != null && !m.entry.text.isEmpty()) info += "<br><br><i>Kurzbeschreibung:</i> " + m.entry.text;
        p.add(new JLabel(info + "</div></html>"), BorderLayout.NORTH);
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        int r = JOptionPane.showConfirmDialog(frame, p, "Tooltip-Text bearbeiten", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        try {
            db.setUserText(key, area.getText());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Konnte nicht speichern: " + e.getMessage(), "Modern-UI", JOptionPane.ERROR_MESSAGE);
        }
        refresh();
    }

    // ------------------------------------------------------------ Datenaustausch

    private Object exec(String action, String... attrs) throws Exception {
        Document req = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element a = req.createElement("action");
        a.setAttribute("action", action);
        for (int i = 0; i + 1 < attrs.length; i += 2) a.setAttribute(attrs[i], attrs[i + 1]);
        req.appendChild(a);
        return daten.exec(req);
    }

    private String query(String action) {
        try {
            Object o = exec(action);
            if (o instanceof Document) return ((Document) o).getDocumentElement().getAttribute("value");
        } catch (Exception ignore) { }
        return null;
    }

    private void loadProps() {
        try {
            Object o = exec("listProperties", "pluginName", PLUGIN_NAME);
            if (!(o instanceof Document)) return;
            for (Node n = ((Document) o).getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element)) continue;
                String k = ((Element) n).getAttribute("key");
                int dot = k.lastIndexOf('.');
                props.put(dot >= 0 ? k.substring(dot + 1) : k, ((Element) n).getAttribute("value"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveProp(String key, String value) {
        props.put(key, value);
        try {
            Document req = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element a = req.createElement("action");
            a.setAttribute("action", "saveProperties");
            a.setAttribute("pluginName", PLUGIN_NAME);
            for (Map.Entry<String, String> e : props.entrySet()) {
                Element p = req.createElement("prop");
                p.setAttribute("key", e.getKey());
                p.setAttribute("value", e.getValue());
                a.appendChild(p);
            }
            req.appendChild(a);
            daten.exec(req);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void debugDump(String name, Document d) {
        if (baseDir == null || !new File(baseDir, "debug").isDirectory()) return;
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.transform(new DOMSource(d), new StreamResult(new File(new File(baseDir, "debug"), name + ".xml")));
        } catch (Exception ignore) { }
    }

    private static String version() {
        Package p = ModernHeldenPlugin.class.getPackage();
        String v = p == null ? null : p.getImplementationVersion();
        return v == null ? "dev" : v;
    }

    static Image icon(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xa4552a));
        g.fillRoundRect(0, 0, s, s, s / 3, s / 3);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(1.5f, s / 10f)));
        int m = s / 4;
        g.drawPolyline(new int[]{m, m, s / 2, s - m, s - m}, new int[]{s - m, m, s / 2 + 1, m, s - m}, 5);
        g.dispose();
        return img;
    }
}
