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
    @Override public String getToolTipText() { return "Moderne Oberfläche, Heldenübersicht und erweiterte Tooltips"; }
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
        hook.setDruckDaten(() -> {
            Document doc = heldDaten();
            return doc == null ? null : de.modernhelden.print.SheetData.parse(doc);
        });
        if (new File(baseDir, "debug").isDirectory()) hook.debugLog = new File(new File(baseDir, "debug"), "hook.log");
        if (new File(baseDir, "debug").isDirectory()) IconSwap.debugLog = new File(new File(baseDir, "debug"), "icons.log");
        if (new File(baseDir, "debug").isDirectory()) VorlagenInfo.debugLog = new File(new File(baseDir, "debug"), "vorlagen.log");
        if (new File(baseDir, "debug").isDirectory()) {
            DebugBaum.ordner = new File(baseDir, "debug");
            new javax.swing.Timer(1000, e -> { if (!HinzufuegenLayout.ziehtGerade) DebugBaum.pruefen(); }).start();
        }
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
                    // Standbild über das Fenster, solange umgebaut wird: sonst sieht man kurz die Original-Ansichten
                    // im neuen Erscheinungsbild, bevor die modernen Ansichten stehen
                    Startvorhang v = Theme.ORIGINAL.equals(mode) ? null : Startvorhang.zeigen(frame);
                    Theme.apply(mode);
                    installDruckvorschau();
                    refresh();
                    if (v != null) v.wegNachAufbau();
                }
            });
            t.start();
        }
    }

    @Override
    public JComponent getPanel() {
        if (panel == null) {
            panel = new HeroPanel(db, this::editTooltip);
            panel.setTooltipsAn(hook::isEnabled);
            panel.setAbhaengige((sf, spaeter) -> Rueckgaengig.abhaengige(frame, sf, spaeter));
            panel.setDruckDaten(() -> {
                Document d = heldDaten();
                return d == null ? null : de.modernhelden.print.SheetData.parse(d);
            });
            panel.setRueckgaengig(e -> {
                String f = Rueckgaengig.ausfuehren(frame, e.time, e.text);
                panelStand = null;
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
        final JCheckBoxMenuItem tips = new JCheckBoxMenuItem("Erweiterte Tooltips anzeigen", hook.isEnabled());
        tips.addActionListener(e -> {
            hook.setEnabled(tips.isSelected());
            saveProp("tooltips", String.valueOf(tips.isSelected()));
            // Reiter „Plugin: Modern-UI“ neu aufbauen, damit der Schalter auch dort sofort gilt
            if (panel != null) refresh();
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

    /**
     * Stand der Daten, aus denen die Kompaktansicht zuletzt aufgebaut wurde. Die Helden-Software meldet auch beim
     * bloßen Reiterwechsel eine Änderung; ohne diesen Vergleich würde der sichtbare Reiter jedes Mal neu aufgebaut
     * (Flackern).
     */
    private String panelStand;

    private static String text(Document d) {
        try {
            javax.xml.transform.Transformer t = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            java.io.StringWriter w = new java.io.StringWriter();
            t.transform(new DOMSource(d), new StreamResult(w));
            // die Signatur ist bei jedem Export neu und sagt nichts über Änderungen am Helden
            return w.toString().replaceAll("(?s)<SignatureValue>.*?</SignatureValue>", "");
        } catch (Exception e) {
            return null;
        }
    }

    private void refresh() {
        HeroData h = null;
        String stand = null;
        try {
            Object res = exec("exportHeld", "id", "selected");
            if (res instanceof Document) {
                stand = text((Document) res);
                h = HeroData.parse((Document) res, db);
                try {
                    Document v3 = heldDaten();
                    if (v3 != null) {
                        de.modernhelden.print.SheetData sd = de.modernhelden.print.SheetData.parse(v3);
                        h.metaTalente(sd);
                        h.vorNachteile(sd);
                    }
                } catch (RuntimeException ignore) { }
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
        InventarLayout.setHero(h);
        AusruestungLayout.daten = this::heldDaten;
        AusruestungLayout.export = () -> {
            try {
                Object o = exec("exportHeld", "id", "selected");
                return o instanceof Document ? (Document) o : null;
            } catch (Exception e) {
                return null;
            }
        };
        AusruestungLayout.aktualisieren();
        KampfwerteLayout.aktualisieren();
        EigenschaftenLayout.aktualisieren();
        AllgemeinLayout.db = db;
        AllgemeinLayout.aktualisieren();
        if (panel != null) {
            String neu = stand == null ? null : stand + "|" + Theme.current() + "|" + hook.isEnabled();
            if (neu == null || !neu.equals(panelStand)) panel.showHero(h);
            panelStand = neu;
        }
        HauptReiter.pruefen(panel, "Plugin: " + getMenuName());
        installDruckvorschau();
        if (printPanel != null) printPanel.markDirty();
    }

    /** Ersetzt den Inhalt des Reiters „Druckvorschau“ durch die moderne Vorschau (Original bleibt per Umschalter erreichbar). */
    private void installDruckvorschau() {
        if (frame == null || "false".equals(props.get("druck.ersetzen"))) return;
        try {
            if (printPanel == null) {
                printPanel = new PrintPreviewPanel(db, this::heldDaten, props, this::saveProp);
                PrintPreviewPanel.export = () -> {
                    try {
                        Object o = exec("exportHeld", "id", "selected");
                        return o instanceof Document ? (Document) o : null;
                    } catch (Exception e) {
                        return null;
                    }
                };
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
            if (!(o instanceof Document)) return null;
            jagdwaffeErgaenzen((Document) o);
            return (Document) o;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Die Druckdaten kennen die Jagdwaffe nicht; die Helden-Software speichert sie nur im Heldenformat
     * ({@code <heldenausruestung name="jagtwaffe" nummer="Fernkampfwaffe 1…" set="Kampfset ab 0"/>}).
     * Sie wird hier als {@code <jagdwaffe>true</jagdwaffe>} an die passende Fernkampfwaffe gehängt.
     */
    private void jagdwaffeErgaenzen(Document v3) {
        try {
            Object e = exec("exportHeld", "id", "selected");
            if (!(e instanceof Document)) return;
            org.w3c.dom.NodeList l = ((Document) e).getElementsByTagName("heldenausruestung");
            String nummer = null, set = null;
            for (int i = 0; i < l.getLength(); i++) {
                org.w3c.dom.Element a = (org.w3c.dom.Element) l.item(i);
                if ("jagtwaffe".equals(a.getAttribute("name")) || "jagdwaffe".equals(a.getAttribute("name"))) {
                    nummer = a.getAttribute("nummer"); set = a.getAttribute("set");
                }
            }
            if (nummer == null || nummer.isEmpty() || "0".equals(nummer)) return;
            String nr = String.valueOf(Integer.parseInt(set.isEmpty() ? "0" : set) + 1);
            org.w3c.dom.NodeList sets = v3.getElementsByTagName("kampfset");
            for (int i = 0; i < sets.getLength(); i++) {
                org.w3c.dom.Element ks = (org.w3c.dom.Element) sets.item(i);
                if (!nr.equals(ks.getAttribute("nr"))) continue;
                org.w3c.dom.NodeList fk = ks.getElementsByTagName("fernkampfwaffe");
                for (int j = 0; j < fk.getLength(); j++) {
                    org.w3c.dom.Element w = (org.w3c.dom.Element) fk.item(j);
                    org.w3c.dom.NodeList n = w.getElementsByTagName("nummer");
                    if (n.getLength() > 0 && nummer.equals(n.item(0).getTextContent().trim())) {
                        org.w3c.dom.Element j2 = v3.createElement("jagdwaffe");
                        j2.setTextContent("true");
                        w.appendChild(j2);
                    }
                }
            }
        } catch (Exception ignore) { }
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

    /**
     * Name, unter dem der eigene Text gespeichert wird: der volle Name des Eintrags („Liturgie: Eidsegen“), damit er
     * nicht für alle Einträge des allgemeinen Eintrags („Liturgie:“) gilt. Hängt hinter dem allgemeinen Namen nur eine
     * Stufe („Aberglaube: 6“), bleibt es beim allgemeinen Namen – sonst ginge der Text beim Steigern verloren.
     */
    private String notizSchluessel(TooltipDB.Match m) {
        if (m.entry == null || m.key.equals(m.entry.name) || db.getUserText(m.key) != null) return m.key;
        String rest = m.key.length() > m.entry.name.length() ? m.key.substring(m.entry.name.length()) : "";
        return rest.replaceAll("[\\s:()0-9+\\-–]", "").isEmpty() ? m.entry.name : m.key;
    }

    private void editTooltip(TooltipDB.Match m) {
        String key = notizSchluessel(m);
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
        panelStand = null;
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
                // Die Helden-Software liefert die Schlüssel mit Vorsilbe „plugin.modernui.“ – nur diese abtrennen,
                // Punkte im eigentlichen Schlüssel (z. B. „druck.zoom“) gehören dazu
                String k = ((Element) n).getAttribute("key");
                String vorsilbe = "plugin." + PLUGIN_NAME + ".";
                if (k.startsWith(vorsilbe)) k = k.substring(vorsilbe.length());
                props.put(k, ((Element) n).getAttribute("value"));
            }
            altlastenUebernehmen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Bis 1.16.9 wurde beim Laden alles bis zum letzten Punkt abgeschnitten („druck.zoom“ → „zoom“,
     * „druck.portrait.bild.Merlin“ → „Merlin“) und so beim nächsten Speichern abgelegt. Solche Einträge
     * werden einmalig unter ihrem richtigen Namen übernommen, sofern es den richtigen noch nicht gibt.
     */
    private void altlastenUebernehmen() {
        boolean geaendert = false;
        for (String k : new String[]{"beschreibungen", "ersetzen", "meta", "modus", "notizen", "pdfordner", "portrait", "zoom"}) {
            String v = props.get(k);
            if (v != null && !props.containsKey("druck." + k)) { props.put("druck." + k, v); geaendert = true; }
        }
        for (Map.Entry<String, String> e : new ArrayList<>(props.entrySet())) {
            String k = e.getKey(), v = e.getValue();
            // eigenes Portrait je Held: Schlüssel ohne Punkt, Wert ein Bildpfad
            if (k.indexOf('.') < 0 && v != null && v.matches("(?i).+\\.(png|jpe?g|gif|bmp)")
                    && !props.containsKey("druck.portrait.bild." + k)) {
                props.put("druck.portrait.bild." + k, v);
                geaendert = true;
            }
        }
        if (geaendert) saveProp("druck.migriert", "1");
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
