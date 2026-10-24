package de.modernhelden;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hängt sich global in die Oberfläche der Helden-Software ein und zeigt
 * Tooltips für Talente, Sonderfertigkeiten und Vor-/Nachteile – auch in den
 * Original-Dialogen (Steigern, SF-Auswahl, Erschaffung …).
 *
 * Die Original-Tabellen überschreiben ihre Tooltip-Abfrage; deshalb zeigt der
 * Hook ein eigenes Popup an, statt Tooltip-Texte der Komponenten zu verändern.
 */
public final class TooltipHook implements AWTEventListener {

    private static final int DELAY = 450;

    private final TooltipDB db;
    private volatile boolean enabled = true;
    private volatile HeroData hero;
    private volatile TooltipDB.Match lastMatch;
    private boolean installed;
    public volatile java.io.File debugLog;
    private String lastLogged;

    private Popup popup;
    private String pendingKey, shownKey;
    private final Timer showTimer;
    private JComponent pendingComp;
    private Point pendingScreen;
    private String pendingHtml;
    private boolean ttmDisabledByUs;

    public TooltipHook(TooltipDB db) {
        this.db = db;
        showTimer = new Timer(DELAY, e -> showPending());
        showTimer.setRepeats(false);
    }

    public void setHero(HeroData h) { this.hero = h; inventar = null; }

    /** Ausführliche Heldendaten für die Inventar-Tooltips; werden erst beim ersten Bedarf geholt. */
    private java.util.function.Supplier<de.modernhelden.print.SheetData> druckDaten;
    private volatile de.modernhelden.print.SheetData inventar;

    public void setDruckDaten(java.util.function.Supplier<de.modernhelden.print.SheetData> s) { this.druckDaten = s; }

    private de.modernhelden.print.SheetData inventarDaten() {
        if (inventar == null && druckDaten != null && hero != null) {
            try { inventar = druckDaten.get(); } catch (RuntimeException ignore) { }
        }
        return inventar;
    }

    /** Inventartabelle der Helden-Software (Reiter „Inventar / Tiere“): Spalten Anzahl, Name, Art, Gewicht. */
    private static boolean istInventarTabelle(JTable t) {
        TableModel m = t.getModel();
        java.util.Set<String> namen = new java.util.HashSet<>();
        for (int i = 0; i < m.getColumnCount(); i++) namen.add(String.valueOf(m.getColumnName(i)).trim());
        return namen.contains("Anzahl") && namen.contains("Name") && namen.contains("Art") && namen.contains("Gewicht");
    }

    private static String spalte(JTable t, int viewRow, String name) {
        TableModel m = t.getModel();
        int mr = t.convertRowIndexToModel(viewRow);
        for (int i = 0; i < m.getColumnCount(); i++)
            if (name.equals(String.valueOf(m.getColumnName(i)).trim())) return str(m.getValueAt(mr, i));
        return null;
    }

    /** Gegenstandsvorlagen (Liste „Gegenstände“ beim Hinzufügen): Tooltip mit den Werten der Helden-Software. */
    private boolean vorlageTooltip(JList<?> l, MouseEvent e) {
        int i = l.locationToIndex(e.getPoint());
        if (i < 0 || l.getCellBounds(i, i) == null || !l.getCellBounds(i, i).contains(e.getPoint())) return false;
        Object el = l.getModel().getElementAt(i);
        if (!VorlagenInfo.istVorlage(el)) return false;
        String key = System.identityHashCode(l) + "|vorlage|" + i;
        if (key.equals(shownKey)) return true;
        if (popup != null) hidePopupOnly();
        Map<String, String> x = VorlagenInfo.angaben(el);
        String name = VorlagenInfo.name(el);
        // Gegenstände nicht mit gleichnamigen Talenten/SF verknüpfen (z. B. Parierwaffe „Linkhand“)
        TooltipDB.Match m = new TooltipDB.Match(name, null, name);
        lastMatch = m;
        pendingKey = key;
        pendingComp = l;
        pendingHtml = db.html(m, x);
        pendingScreen = e.getLocationOnScreen();
        showTimer.restart();
        return true;
    }

    /** Tooltip für eine Zeile der Inventartabelle; true, wenn einer angezeigt wird. */
    private boolean inventarTooltip(JTable t, MouseEvent e) {
        int row = t.rowAtPoint(e.getPoint());
        if (row < 0) return false;
        // über einem ausgegrauten „−“/„✕“ zeigt die Tabelle ihren eigenen Hinweis (siehe InventarLayout.sperrHinweis)
        if (Theme.isModern() && InventarLayout.sperrHinweis(t, row, t.columnAtPoint(e.getPoint())) != null) {
            hide();
            return true;
        }
        String name = spalte(t, row, "Name");
        if (name == null) return false;
        de.modernhelden.print.SheetData sd = inventarDaten();
        de.modernhelden.print.SheetData.Gegenstand g = InventarInfo.finde(sd, name);
        if (g == null) return false;
        String key = System.identityHashCode(t) + "|inv|" + row + "|" + name;
        if (key.equals(shownKey)) return true;
        if (popup != null) hidePopupOnly();
        Map<String, String> x = new LinkedHashMap<>();
        InventarInfo.werte(sd, g, x);
        TooltipDB.Match m = new TooltipDB.Match(g.name, null, g.name);   // nicht mit gleichnamigen SF verknüpfen
        lastMatch = m;
        pendingKey = key;
        pendingComp = t;
        pendingHtml = db.html(m, x);
        pendingScreen = e.getLocationOnScreen();
        showTimer.restart();
        return true;
    }
    public void setEnabled(boolean b) { enabled = b; if (!b) hide(); }
    public boolean isEnabled() { return enabled; }
    public TooltipDB.Match getLastMatch() { return lastMatch; }

    private Runnable editAction;

    public synchronized void install(final Runnable editAction) {
        if (installed) return;
        installed = true;
        this.editAction = editAction;
        Toolkit.getDefaultToolkit().addAWTEventListener(this,
                AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        ToolTipManager tm = ToolTipManager.sharedInstance();
        tm.setInitialDelay(DELAY);
        tm.setDismissDelay(60000);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_T
                    && e.isControlDown() && e.isShiftDown()) {
                hide();
                editAction.run();
                return true;
            }
            return false;
        });
    }

    @Override
    public void eventDispatched(AWTEvent ev) {
        try {
            int id = ev.getID();
            if (id == MouseEvent.MOUSE_CLICKED) { doppelklick((MouseEvent) ev); return; }
            if (id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_WHEEL || id == KeyEvent.KEY_PRESSED
                    || id == MouseEvent.MOUSE_DRAGGED) {
                hide();
                if (id == MouseEvent.MOUSE_WHEEL) {
                    // Beim Scrollen auch einen normalen Swing-Tooltip schließen, sonst bleibt er stehen
                    ToolTipManager tm = ToolTipManager.sharedInstance();
                    if (tm.isEnabled()) { tm.setEnabled(false); tm.setEnabled(true); }
                }
                return;
            }
            // beim Ziehen eines Gegenstands keine Tooltips – sie lägen unter dem Mauszeiger und störten das Ablegen
            if (HinzufuegenLayout.ziehtGerade) { hide(); return; }
            if (!enabled || id != MouseEvent.MOUSE_MOVED) return;
            MouseEvent e = (MouseEvent) ev;
            Component c = e.getComponent();
            if (!(c instanceof JComponent) || c instanceof JToolTip) { hide(); return; }
            handle((JComponent) c, e);
        } catch (RuntimeException ignore) {
            // niemals die Original-Oberfläche stören
        }
    }

    /**
     * Doppelklick auf einen Eintrag öffnet die eigene Notiz – aber nur dort, wo die Helden-Software
     * selbst nichts mit einem Doppelklick macht (eigene Maus-Logik oder bearbeitbare Zelle).
     */
    private void doppelklick(MouseEvent e) {
        if (!enabled || e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e) || e.isConsumed()) return;
        Component comp = e.getComponent();
        if (!(comp instanceof JComponent) || comp instanceof JToolTip) return;
        JComponent c = (JComponent) comp;
        // markierte Zellen eigener Ansichten (z. B. „Mut“ im Reiter Eigenschaften): Notiz zu diesem Eintrag
        for (Component d = SwingUtilities.getDeepestComponentAt(c, e.getX(), e.getY()); d != null; d = d.getParent()) {
            Object f = d instanceof JComponent ? ((JComponent) d).getClientProperty(TOOLTIP_FUER) : null;
            if (f instanceof String) {
                TooltipDB.Match m = db.find((String) f);
                if (m == null || editAction == null) return;
                lastMatch = m;
                hide();
                SwingUtilities.invokeLater(editAction);
                return;
            }
            if (d == c) break;
        }
        if (Boolean.TRUE.equals(c.getClientProperty("modernhelden.own"))) return;   // eigener Reiter regelt das selbst
        if (!(c instanceof JTable || c instanceof JList || c instanceof JTree || c instanceof JLabel)) return;
        String belegt = eigeneDoppelklickLogik(c, e.getPoint());
        String text = textAt(c, e.getPoint());
        TooltipDB.Match m = text == null ? null : db.find(text);
        if (m == null && text != null && hero != null) {
            HeroData.Talent z = hero.zauber(text);
            if (z != null) m = new TooltipDB.Match(z.name, null, z.name);
        }
        log(c, "Doppelklick: " + text + (belegt != null ? " [belegt: " + belegt + "]" : ""), m);
        if (m == null || belegt != null || editAction == null) return;
        lastMatch = m;
        hide();
        SwingUtilities.invokeLater(editAction);
    }

    /** Liefert einen Grund, wenn die Komponente Doppelklicks selbst auswertet; sonst null. */
    private static String eigeneDoppelklickLogik(JComponent c, Point p) {
        if (c instanceof JTable) {
            JTable t = (JTable) c;
            int row = t.rowAtPoint(p), col = t.columnAtPoint(p);
            if (row >= 0 && col >= 0 && t.isCellEditable(row, col)) return "bearbeitbare Zelle";
        }
        if (c instanceof JTree && ((JTree) c).isEditable()) return "bearbeitbarer Baum";
        for (java.awt.event.MouseListener l : c.getMouseListeners()) {
            String n = l.getClass().getName();
            if (!n.startsWith("javax.") && !n.startsWith("java.") && !n.startsWith("com.formdev.")
                    && !n.startsWith("sun.") && !n.startsWith("de.modernhelden.")) return n;
        }
        return null;
    }

    /** Eigene Ansichten markieren Zellen damit, für welchen Eintrag (z. B. „Mut“) der Tooltip gezeigt wird. */
    static final String TOOLTIP_FUER = "modernhelden.tooltipFuer";

    /** Setzt die Markierung auf die Komponenten – sie zeigen dann denselben Tooltip wie die Helden-Ansichten. */
    static void tooltipFuer(String name, JComponent... cs) {
        for (JComponent k : cs) if (k != null) { k.putClientProperty(TOOLTIP_FUER, name); k.setToolTipText(null); }
    }

    /** Markierung für eigene Ansichten: Tooltip „Herkunft der Abenteuerpunkte“. */
    static final String AP_HERKUNFT = "\u0000AP";

    /** Woher die AP kommen und wofür sie ausgegeben wurden. */
    private void apTooltip(JComponent c, MouseEvent e) {
        HeroData h = hero;
        if (h == null) { hide(); return; }
        String key = System.identityHashCode(c) + "|AP";
        if (key.equals(shownKey)) return;
        if (popup != null) hidePopupOnly();
        pendingKey = key;
        pendingComp = c;
        pendingHtml = TooltipDB.htmlInfo("Abenteuerpunkte: " + h.ap, "Woher die AP kommen und wofür sie ausgegeben wurden",
                h.herkunft.abenteuerpunkte(h.ap, h.freieAp));
        pendingScreen = e.getLocationOnScreen();
        showTimer.restart();
    }

    private void handle(JComponent c, MouseEvent e) {
        String fest = null;
        for (Component d = SwingUtilities.getDeepestComponentAt(c, e.getX(), e.getY()); d != null; d = d.getParent()) {
            if (d instanceof JComponent && ((JComponent) d).getClientProperty(TOOLTIP_FUER) instanceof String) {
                fest = (String) ((JComponent) d).getClientProperty(TOOLTIP_FUER);
                c = (JComponent) d;
                break;
            }
            if (d == c) break;
        }
        if (AP_HERKUNFT.equals(fest)) { apTooltip(c, e); return; }
        if (fest != null) { zeige(c, e, fest, "fest|" + fest); return; }
        if (Boolean.TRUE.equals(c.getClientProperty("modernhelden.own"))) { hide(); return; }
        // Abenteuerpunkte-Felder im Reiter „Allgemein“: Herkunft der AP erklären
        if (c instanceof javax.swing.text.JTextComponent && hero != null) {
            String lab = labelVor(c);
            if (lab != null && (lab.startsWith("Abenteuerpunkte") || lab.startsWith("AP Guthaben") || lab.startsWith("AP-Guthaben"))) {
                apTooltip(c, e);
                return;
            }
        }
        if (c instanceof JTable && hero != null && istInventarTabelle((JTable) c)) {
            if (inventarTooltip((JTable) c, e)) return;
        }
        if (c instanceof JList && vorlageTooltip((JList<?>) c, e)) return;
        String text = textAt(c, e.getPoint());
        // Wertfelder neben „Mut:“, „Lebensenergie:“ … erklären die Eigenschaft bzw. den Basiswert
        if (text == null && c instanceof javax.swing.text.JTextComponent) {
            String lab = labelVor(c);
            TooltipDB.Match em = lab == null ? null : db.find(lab);
            if (em != null && em.entry != null && "E".equals(em.entry.typ)) text = lab;
        }
        // Beschriftungen ohne eigene Mauslogik bekommen keine Mausereignisse (die gehen an den Container) –
        // für Eigenschaften und Basiswerte das Label unter dem Mauszeiger selbst suchen
        if (text == null && !(c instanceof javax.swing.text.JTextComponent)) {
            Component d = SwingUtilities.getDeepestComponentAt(c, e.getX(), e.getY());
            if (d instanceof JLabel && d != c) {
                String lt = ((JLabel) d).getText();
                TooltipDB.Match em = lt == null ? null : db.find(lt);
                if (em != null && em.entry != null && "E".equals(em.entry.typ)) { text = lt; c = (JComponent) d; }
            }
        }
        zeige(c, e, text, null);
    }

    private void zeige(JComponent c, MouseEvent e, String text, String festerSchluessel) {
        TooltipDB.Match m = text == null ? null : db.find(text);
        if (m == null && text != null && hero != null) {
            // Zauber haben keine mitgelieferte Beschreibung – trotzdem Werte und eigene Notiz zeigen
            HeroData.Talent z = hero.zauber(text);
            if (z != null) m = new TooltipDB.Match(z.name, null, z.name);
        }
        log(c, text, m);
        if (m == null) { hide(); return; }
        lastMatch = m;
        String key = festerSchluessel != null ? festerSchluessel : System.identityHashCode(c) + "|" + m.shown;
        if (key.equals(shownKey)) return;           // Tooltip steht bereits
        if (popup != null) hidePopupOnly();
        Map<String, String> x = extra(m);
        String own = c.getToolTipText(e);
        if (own != null && !own.trim().isEmpty() && !own.contains("modernhelden"))
            x.put("Helden-Software", own.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim());
        pendingKey = key;
        pendingComp = c;
        pendingHtml = db.html(m, x, verlauf(m));
        pendingScreen = e.getLocationOnScreen();
        showTimer.restart();
    }

    private void showPending() {
        if (pendingComp == null || !pendingComp.isShowing()) return;
        JToolTip tip = pendingComp.createToolTip();
        tip.setTipText(pendingHtml);
        Dimension d = tip.getPreferredSize();
        Rectangle screen = pendingComp.getGraphicsConfiguration().getBounds();
        int x = pendingScreen.x + 14, y = pendingScreen.y + 20;
        if (x + d.width > screen.x + screen.width) x = Math.max(screen.x, screen.x + screen.width - d.width - 4);
        if (y + d.height > screen.y + screen.height) y = Math.max(screen.y, pendingScreen.y - d.height - 8);
        ToolTipManager tm = ToolTipManager.sharedInstance();
        if (tm.isEnabled()) { tm.setEnabled(false); ttmDisabledByUs = true; }   // Original-Tooltip ausblenden
        popup = PopupFactory.getSharedInstance().getPopup(pendingComp, tip, x, y);
        popup.show();
        shownKey = pendingKey;
    }

    private void hidePopupOnly() {
        if (popup != null) { popup.hide(); popup = null; }
        shownKey = null;
    }

    private void hide() {
        showTimer.stop();
        pendingKey = null;
        pendingComp = null;
        hidePopupOnly();
        if (ttmDisabledByUs) { ToolTipManager.sharedInstance().setEnabled(true); ttmDisabledByUs = false; }
    }

    private void log(JComponent c, String text, TooltipDB.Match m) {
        if (debugLog == null) return;
        String line = c.getClass().getName() + " | " + text + " | " + (m == null ? "-" : m.key);
        if (line.equals(lastLogged)) return;
        lastLogged = line;
        try (java.io.FileWriter w = new java.io.FileWriter(debugLog, true)) { w.write(line + "\n"); } catch (java.io.IOException ignore) { }
    }

    private java.util.List<String[]> verlauf(TooltipDB.Match m) {
        HeroData h = hero;
        if (h == null) return null;
        HeroData.Talent t = h.talent(m.shown);
        if (t == null && m.entry != null) t = h.talent(m.entry.name);
        if (t != null && "berechnet".equals(t.lernmethode)) return null;   // Meta-Talent: wird berechnet, nicht gesteigert
        if (t != null) return h.herkunft.talent(t.name, t.wert);
        for (HeroData.Item it : h.sf) if (it.name.equals(m.shown)) return h.herkunft.sonderfertigkeit(it.name);
        return null;
    }

    private Map<String, String> extra(TooltipDB.Match m) {
        HeroData h = hero;
        Map<String, String> x = new LinkedHashMap<>();
        if (h == null) return x;
        if (m.entry != null && "E".equals(m.entry.typ)) {
            String k = KUERZEL.get(m.entry.name);
            Integer v = k == null ? null : h.eig.containsKey(k) ? h.eig.get(k) : h.abgeleitet.get(k);
            if (v != null) x.put("Wert", String.valueOf(v));
            return x;
        }
        HeroData.Talent t = h.talent(m.shown);
        if (t == null && m.entry != null) t = h.talent(m.entry.name);
        HeroData.Talent z = t == null ? h.zauber(m.shown) : null;
        if (z != null) {
            x.put("Art", "Zauber");
            x.put("Probe", h.probeMitWerten(z.probe));
            x.put("ZfW", String.valueOf(z.wert));
            if (!z.lernmethode.isEmpty()) x.put("Repräsentation", z.lernmethode);
            return x;
        }
        if (t != null) {
            String kw = h.kampfwerteText(t.name);
            if (kw != null) x.put("Werte", kw); else x.put("Probe", h.probeMitWerten(t.probe));
            x.put("TaW", String.valueOf(t.wert));
            if (!t.be.isEmpty()) x.put("BE", t.be);
            if ("berechnet".equals(t.lernmethode)) x.put("Art", "Meta-Talent (berechnet)");
            else if (!t.lernmethode.isEmpty()) x.put("Lernen", t.lernmethode);
        }
        return x;
    }

    /** Eigenschaften und Basiswerte → Kürzel in HeroData (eig bzw. abgeleitet). */
    static final Map<String, String> KUERZEL = new HashMap<>();
    static {
        String[][] k = {{"Mut", "MU"}, {"Klugheit", "KL"}, {"Intuition", "IN"}, {"Charisma", "CH"}, {"Fingerfertigkeit", "FF"},
                {"Gewandtheit", "GE"}, {"Konstitution", "KO"}, {"Körperkraft", "KK"}, {"Sozialstatus", "SO"},
                {"Lebensenergie", "LE"}, {"Ausdauer", "AU"}, {"Astralenergie", "AE"}, {"Karmaenergie", "KE"},
                {"Magieresistenz", "MR"}, {"Initiative", "INI"}, {"Attacke", "AT"}, {"Parade", "PA"}, {"Fernkampf-Basis", "FK"}};
        for (String[] p : k) KUERZEL.put(p[0], p[1]);
    }

    /** Beschriftung eines Eingabefelds: per labelFor oder das JLabel direkt davor im selben Container. */
    static String labelVor(Component c) {
        Container p = c.getParent();
        if (p == null) return null;
        String vorher = null;
        for (Component k : p.getComponents()) {
            if (k instanceof JLabel && ((JLabel) k).getLabelFor() == c) return ((JLabel) k).getText();
        }
        for (Component k : p.getComponents()) {
            if (k == c) return vorher;
            if (k instanceof JLabel) vorher = ((JLabel) k).getText();
            else if (k instanceof javax.swing.text.JTextComponent) vorher = null;
        }
        return null;
    }

    /** Ermittelt den sichtbaren Text unter dem Mauszeiger. */
    static String textAt(JComponent c, Point p) {
        if (c instanceof JTable) {
            JTable t = (JTable) c;
            int row = t.rowAtPoint(p), col = t.columnAtPoint(p);
            if (row < 0 || col < 0) return null;
            String s = str(t.getValueAt(row, col));
            if (s == null || s.length() <= 2 || s.matches("[-+\\d\\s/().,]+")) {
                // Zahlen- oder Info-Spalte: Namen aus der ersten Textspalte der Zeile nehmen
                TableModel tm = t.getModel();
                int mr = t.convertRowIndexToModel(row);
                for (int i = 0; i < tm.getColumnCount(); i++) {
                    String o = str(tm.getValueAt(mr, i));
                    if (o != null && o.length() > 2 && !o.matches("[-+\\d\\s/().,]+")) return o;
                }
            }
            return s;
        }
        if (c instanceof JList) {
            JList<?> l = (JList<?>) c;
            int i = l.locationToIndex(p);
            if (i < 0 || !l.getCellBounds(i, i).contains(p)) return null;
            return str(l.getModel().getElementAt(i));
        }
        if (c instanceof JTree) {
            JTree t = (JTree) c;
            TreePath path = t.getPathForLocation(p.x, p.y);
            return path == null ? null : str(path.getLastPathComponent());
        }
        if (c instanceof JLabel) return ((JLabel) c).getText();
        if (c instanceof AbstractButton && !(c instanceof JMenuItem)) return ((AbstractButton) c).getText();
        return null;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString();
        if (s.matches(".*@[0-9a-f]{4,}$")) return null;   // Objekt ohne sinnvolles toString()
        s = s.replaceAll("<[^>]*>", " ").trim();
        return s.isEmpty() ? null : s;
    }
}
