package de.modernhelden;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Ersetzt die Symbole der Helden-Software in den modernen Designs durch {@link ModernIcons}
 * und stellt beim Wechsel auf „Original“ die ursprünglichen Bilder wieder her.
 *
 * Erkannt wird ein Symbol am Bildpfad bzw. an der Beschreibung des ImageIcon
 * (z. B. „…/helden/gui/icons/Save.gif“ oder „Basistalent“).
 */
public final class IconSwap {

    private IconSwap() {}

    private static final String ORIG = "modernhelden.icon.orig.";
    private static boolean modern;
    private static javax.swing.Timer timer;
    private static boolean installiert;
    static File debugLog;
    private static final Set<String> geloggt = new HashSet<>();

    /** Originale der statischen Symbole in IconFactory (Feld → Icon). */
    private static final Map<Field, Object> factoryOrig = new HashMap<>();

    public static synchronized void setModern(boolean m) {
        modern = m;
        installieren();
        factory(m);
        alleFenster();
        // nach dem Setzen/Entfernen der Zebra-Hüllen zwischengespeicherte Zellen-Knöpfe erneut angleichen
        Theme.aktualisiereTabellenRenderer();
        if (m) timer.start(); else timer.stop();
    }

    private static void installieren() {
        if (installiert) return;
        installiert = true;
        // neu geöffnete Fenster und Dialoge sofort umstellen
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (ev.getID() == WindowEvent.WINDOW_OPENED && modern) {
                Window w = ((WindowEvent) ev).getWindow();
                // Dialoge der Helden-Software, die in den modernen Designs neu angeordnet werden
                if (w instanceof JDialog) {
                    String t = ((JDialog) w).getTitle();
                    if (HinzufuegenLayout.TITEL.equals(t)) HinzufuegenLayout.anwenden((JDialog) w);
                    else if (GeldboerseLayout.TITEL.equals(t)) GeldboerseLayout.anwenden((JDialog) w);
                    else AusruestungDialoge.anwenden((JDialog) w);   // erkennt die Ausrüstungs-Dialoge am Aufbau
                }
                SwingUtilities.invokeLater(() -> baum(w));
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
        // Erschaffungs-Assistent umbauen, sobald er sichtbar wird – noch bevor er zum ersten Mal gezeichnet ist
        // (WINDOW_OPENED kommt erst danach; sonst blitzt kurz das alte, kleinere Fenster auf)
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!modern || !(ev instanceof java.awt.event.HierarchyEvent)) return;
            java.awt.event.HierarchyEvent he = (java.awt.event.HierarchyEvent) ev;
            if ((he.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) return;
            Component c = he.getChanged();
            if (!(c instanceof Window) || !c.isShowing() || he.getComponent() != c) return;
            try {
                if (ErschaffungsRahmen.DIALOG.equals(c.getClass().getName())) baum(c);
                else if (c instanceof JDialog) {
                    // Dialoge, die neu angeordnet werden, ebenfalls vor dem ersten Zeichnen umbauen
                    // (die Geldbörse regelt das selbst über WINDOW_OPENED)
                    String t = ((JDialog) c).getTitle();
                    if (HinzufuegenLayout.TITEL.equals(t)) HinzufuegenLayout.anwenden((JDialog) c);
                    else if (!GeldboerseLayout.TITEL.equals(t)) AusruestungDialoge.anwenden((JDialog) c);
                    // Symbole und Tabellen sofort umstellen, nicht erst nach dem ersten Bild
                    if (!GeldboerseLayout.TITEL.equals(t)) baum(c);
                }
            } catch (RuntimeException ignore) { }
        }, AWTEvent.HIERARCHY_EVENT_MASK);
        // Reiter und Listen der Helden-Software entstehen teils erst beim Anzeigen: regelmäßig nachziehen
        // während ein Gegenstand gezogen wird nichts anfassen – das Neuzeichnen ließ den Mauszeiger flackern
        timer = new javax.swing.Timer(1500, e -> { if (!HinzufuegenLayout.ziehtGerade) alleFenster(); });
        timer.setRepeats(true);
    }

    private static void alleFenster() {
        for (Window w : Window.getWindows()) {
            if (w.isShowing()) {
                try { baum(w); } catch (RuntimeException ignore) { }
            }
        }
    }

    private static void baum(Component c) {
        if (c instanceof JComponent) komponente((JComponent) c);
        if (c instanceof JMenu) {
            for (Component k : ((JMenu) c).getMenuComponents()) baum(k);
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) baum(k);
        }
    }

    private static void komponente(JComponent c) {
        if (Boolean.TRUE.equals(c.getClientProperty("modernhelden.own"))) return;
        if (c instanceof JTable) {
            TabellenStil.anwenden((JTable) c, modern);
            TalenteSortierung.pruefen((JTable) c, modern);
            zellenSymbole((JTable) c);
        }
        if (c instanceof JTree) {
            geaendert = false;
            baumRenderer(((JTree) c).getCellRenderer());
            if (geaendert) c.repaint();
        }
        if (ErschaffungsLayout.KLASSE.equals(c.getClass().getName())) ErschaffungsLayout.anwenden(c, modern);
        if (c instanceof JRootPane) ErschaffungsRahmen.pruefen((JRootPane) c, modern);
        if (c instanceof JPanel) InventarLayout.pruefen(c, modern);
        if (c instanceof JPanel) AusruestungLayout.pruefen(c, modern);
        if (c instanceof JPanel) KampfwerteLayout.pruefen(c, modern);
        if (c instanceof JPanel) EigenschaftenLayout.pruefen(c, modern);
        if (c instanceof JPanel) AllgemeinLayout.pruefen(c, modern);
        if (c instanceof AbstractButton) {
            AbstractButton b = (AbstractButton) c;
            slot(b, "icon", b.getIcon(), b::setIcon);
            slot(b, "rollover", b.getRolloverIcon(), b::setRolloverIcon);
            slot(b, "pressed", b.getPressedIcon(), b::setPressedIcon);
            slot(b, "selected", b.getSelectedIcon(), b::setSelectedIcon);
        } else if (c instanceof JLabel) {
            JLabel l = (JLabel) c;
            slot(l, "icon", l.getIcon(), l::setIcon);
        } else if (c instanceof JTabbedPane) {
            JTabbedPane t = (JTabbedPane) c;
            for (int i = 0; i < t.getTabCount(); i++) {
                final int idx = i;
                slot(t, "tab" + i, t.getIconAt(i), ic -> t.setIconAt(idx, ic));
            }
        }
    }

    private interface Setter { void set(Icon i); }

    /** Ordner-/Eintragssymbole eines Baum-Renderers (z. B. Gruppenliste, Gruppenvergleich). */
    private static void baumRenderer(Object r) {
        if (!(r instanceof javax.swing.tree.DefaultTreeCellRenderer)) return;
        javax.swing.tree.DefaultTreeCellRenderer d = (javax.swing.tree.DefaultTreeCellRenderer) r;
        slot(d, "baumZu", d.getClosedIcon(), d::setClosedIcon, "ordner");
        slot(d, "baumAuf", d.getOpenIcon(), d::setOpenIcon, "ordnerauf");
        slot(d, "baumBlatt", d.getLeafIcon(), d::setLeafIcon, "eintrag");
    }

    /**
     * Tabellen, deren Zellen-Komponenten nicht im Fensterbaum hängen: eigene Renderer der Helden-Software
     * (z. B. zwischengespeicherte Löschknöpfe im Inventar) und Bäume als Renderer (Gruppenvergleich).
     */
    private static void zellenSymbole(JTable t) {
        // nur neu prüfen, wenn sich Design oder Inhalt geändert haben. Die Helden-Software legt Zellen-Knöpfe je
        // Eintrag an – nach einem Heldenwechsel mit gleich vielen Zeilen sind es also andere Knöpfe.
        long inhalt = 0;
        int zn = Math.min(t.getRowCount(), 300);
        for (int row = 0; row < zn; row++) {
            for (int ci = 0; ci < t.getColumnCount(); ci++) {
                Object v;
                try { v = t.getValueAt(row, ci); } catch (RuntimeException ex) { continue; }
                // Texte/Zahlen nach Inhalt, Objekte (Gegenstände usw.) nach Identität
                int h = v == null ? 0 : v instanceof CharSequence || v instanceof Number || v instanceof Boolean
                        ? v.hashCode() : System.identityHashCode(v);
                inhalt = inhalt * 31 + h;
            }
        }
        String stand = modern + ":" + t.getRowCount() + ":" + t.getColumnCount() + ":" + inhalt;
        if (stand.equals(t.getClientProperty("modernhelden.zellen"))) return;
        t.putClientProperty("modernhelden.zellen", stand);
        geaendert = false;
        java.util.Set<Object> erledigt = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        javax.swing.table.TableColumnModel cm = t.getColumnModel();
        int zeilen = Math.min(t.getRowCount(), 300);
        for (int ci = 0; ci < cm.getColumnCount(); ci++) {
            Object r = cm.getColumn(ci).getCellRenderer();
            if (r instanceof TabellenStil.Zebra) r = ((TabellenStil.Zebra) r).original;
            if (r == null) {
                try { r = t.getDefaultRenderer(t.getColumnClass(ci)); } catch (RuntimeException ex) { continue; }
                if (r instanceof TabellenStil.Zebra) r = ((TabellenStil.Zebra) r).original;
            }
            if (r == null) continue;
            if (r instanceof JTree) { baumRenderer(((JTree) r).getCellRenderer()); continue; }
            if (r instanceof javax.swing.tree.DefaultTreeCellRenderer) { baumRenderer(r); continue; }
            if (r instanceof Component || !(r instanceof javax.swing.table.TableCellRenderer)) continue;
            if (!r.getClass().getName().startsWith("helden.")) continue;     // nur eigene Renderer der Helden-Software
            javax.swing.table.TableCellRenderer tr = (javax.swing.table.TableCellRenderer) r;
            for (int row = 0; row < zeilen; row++) {
                Component k;
                try {
                    k = tr.getTableCellRendererComponent(t, t.getValueAt(row, ci), false, false, row, ci);
                } catch (RuntimeException ex) { continue; }
                if ((k instanceof AbstractButton || k instanceof JLabel) && erledigt.add(k)) komponente((JComponent) k);
                // Zellen aus mehreren Symbolen (z. B. Hauszauber + Leitzauber in der Erschaffung)
                else if (k instanceof JPanel && erledigt.add(k))
                    for (Component kk : ((JPanel) k).getComponents())
                        if ((kk instanceof AbstractButton || kk instanceof JLabel) && erledigt.add(kk)) komponente((JComponent) kk);
            }
        }
        // Renderer-Komponenten werden nur beim Zeichnen benutzt – neu zeichnen, damit die Symbole sofort stimmen
        if (geaendert) t.repaint();
    }

    private static boolean geaendert;

    private static void slot(JComponent c, String name, Icon aktuell, Setter set) {
        slot(c, name, aktuell, set, null);
    }

    /** @param ersatz Symbol für Bilder ohne bekannte Beschreibung (z. B. Ordner aus dem Original-Design), sonst null */
    private static void slot(JComponent c, String name, Icon aktuell, Setter set, String ersatz) {
        String prop = ORIG + name;
        Object orig = c.getClientProperty(prop);
        if (!modern) {
            // zurück zum Original
            if (orig instanceof Icon && aktuell instanceof ModernIcons.VektorIcon) { set.set((Icon) orig); geaendert = true; }
            if (orig != null) c.putClientProperty(prop, null);
            return;
        }
        if (aktuell == null || aktuell instanceof ModernIcons.VektorIcon) return;
        String key = null;
        if (aktuell instanceof ImageIcon) {
            String desc = ((ImageIcon) aktuell).getDescription();
            key = ModernIcons.schluessel(desc);
            if (key == null && ersatz == null) { log(desc, c); return; }
        } else if (ersatz == null) {
            return;
        }
        if (key == null) key = ersatz;
        c.putClientProperty(prop, aktuell);
        int w = aktuell.getIconWidth(), h = aktuell.getIconHeight();
        if (w <= 0 || h <= 0) { w = 16; h = 16; }
        // das 10-px-„X“ im Inventar etwas größer, damit es lesbar bleibt
        if ("minidelete".equals(key) && w < 14) { w = 14; h = 14; }
        set.set(ModernIcons.icon(key, w, h));
        geaendert = true;
    }

    /** Statische Symbole der Helden-Software (z. B. Basistalent, Hauszauber in Listen) austauschen. */
    private static void factory(boolean m) {
        try {
            Class<?> f = Class.forName("helden.gui.icons.IconFactory", true, Thread.currentThread().getContextClassLoader());
            // Die Helden-Software lädt ihre Markierungssymbole erst beim ersten Abruf (z. B. beim Öffnen der
            // Heldenerschaffung). Vorher sind die Felder leer und würden nicht getauscht – also das Laden jetzt anstoßen.
            try { f.getMethod("getConfig").invoke(null); } catch (Throwable ignore) { }
            for (Field fd : f.getDeclaredFields()) {
                if (!Modifier.isStatic(fd.getModifiers()) || !ImageIcon.class.isAssignableFrom(fd.getType())) continue;
                if (Modifier.isFinal(fd.getModifiers())) continue;
                fd.setAccessible(true);
                Object v = fd.get(null);
                if (m) {
                    if (v instanceof ModernIcons.VektorIcon || !(v instanceof ImageIcon)) continue;
                    ImageIcon ic = (ImageIcon) v;
                    String key = ModernIcons.schluessel(ic.getDescription());
                    if (key == null) continue;
                    factoryOrig.put(fd, v);
                    fd.set(null, ModernIcons.icon(key, ic.getIconWidth(), ic.getIconHeight()));
                } else if (factoryOrig.containsKey(fd)) {
                    fd.set(null, factoryOrig.remove(fd));
                }
            }
        } catch (Throwable ignore) {
            // Helden-Software ohne diese Klasse: nur die Komponenten umstellen
        }
    }

    static void debugText(String t) {
        if (debugLog == null || !geloggt.add(t)) return;
        try (java.io.FileWriter w = new java.io.FileWriter(debugLog, true)) { w.write(t + "\n"); } catch (java.io.IOException ignore) { }
    }

    private static void log(String desc, JComponent c) {
        if (debugLog == null || desc == null || !geloggt.add(desc)) return;
        try (java.io.FileWriter w = new java.io.FileWriter(debugLog, true)) {
            w.write("unbekannt: " + desc + " | " + c.getClass().getName() + "\n");
        } catch (java.io.IOException ignore) { }
    }
}
