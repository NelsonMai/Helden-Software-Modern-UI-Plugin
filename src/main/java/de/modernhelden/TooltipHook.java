package de.modernhelden;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
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

    public void setHero(HeroData h) { this.hero = h; }
    public void setEnabled(boolean b) { enabled = b; if (!b) hide(); }
    public boolean isEnabled() { return enabled; }
    public TooltipDB.Match getLastMatch() { return lastMatch; }

    public synchronized void install(final Runnable editAction) {
        if (installed) return;
        installed = true;
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
            if (!enabled || id != MouseEvent.MOUSE_MOVED) return;
            MouseEvent e = (MouseEvent) ev;
            Component c = e.getComponent();
            if (!(c instanceof JComponent) || c instanceof JToolTip) { hide(); return; }
            handle((JComponent) c, e);
        } catch (RuntimeException ignore) {
            // niemals die Original-Oberfläche stören
        }
    }

    private void handle(JComponent c, MouseEvent e) {
        if (Boolean.TRUE.equals(c.getClientProperty("modernhelden.own"))) { hide(); return; }
        // Abenteuerpunkte-Felder im Reiter „Allgemein“: Herkunft der AP erklären
        if (c instanceof javax.swing.text.JTextComponent && hero != null) {
            String lab = labelVor(c);
            if (lab != null && (lab.startsWith("Abenteuerpunkte") || lab.startsWith("AP Guthaben") || lab.startsWith("AP-Guthaben"))) {
                HeroData h = hero;
                String key = System.identityHashCode(c) + "|AP";
                if (key.equals(shownKey)) return;
                if (popup != null) hidePopupOnly();
                pendingKey = key;
                pendingComp = c;
                pendingHtml = TooltipDB.htmlInfo("Abenteuerpunkte: " + h.ap, "Woher die AP kommen und wofür sie ausgegeben wurden",
                        h.herkunft.abenteuerpunkte(h.ap, h.freieAp));
                pendingScreen = e.getLocationOnScreen();
                showTimer.restart();
                return;
            }
        }
        String text = textAt(c, e.getPoint());
        TooltipDB.Match m = text == null ? null : db.find(text);
        if (m == null && text != null && hero != null) {
            // Zauber haben keine mitgelieferte Beschreibung – trotzdem Werte und eigene Notiz zeigen
            HeroData.Talent z = hero.zauber(text);
            if (z != null) m = new TooltipDB.Match(z.name, null, z.name);
        }
        log(c, text, m);
        if (m == null) { hide(); return; }
        lastMatch = m;
        String key = System.identityHashCode(c) + "|" + m.shown;
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
        if (t != null) return h.herkunft.talent(t.name, t.wert);
        for (HeroData.Item it : h.sf) if (it.name.equals(m.shown)) return h.herkunft.sonderfertigkeit(it.name);
        return null;
    }

    private Map<String, String> extra(TooltipDB.Match m) {
        HeroData h = hero;
        Map<String, String> x = new LinkedHashMap<>();
        if (h == null) return x;
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
            if (!t.lernmethode.isEmpty()) x.put("Lernen", t.lernmethode);
        }
        return x;
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
