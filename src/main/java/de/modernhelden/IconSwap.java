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
                SwingUtilities.invokeLater(() -> baum(w));
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
        // Reiter und Listen der Helden-Software entstehen teils erst beim Anzeigen: regelmäßig nachziehen
        timer = new javax.swing.Timer(1500, e -> alleFenster());
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
        if (c instanceof JTable) TabellenStil.anwenden((JTable) c, modern);
        if (ErschaffungsLayout.KLASSE.equals(c.getClass().getName())) ErschaffungsLayout.anwenden(c, modern);
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

    private static void slot(JComponent c, String name, Icon aktuell, Setter set) {
        String prop = ORIG + name;
        Object orig = c.getClientProperty(prop);
        if (!modern) {
            // zurück zum Original
            if (orig instanceof Icon && aktuell instanceof ModernIcons.VektorIcon) set.set((Icon) orig);
            if (orig != null) c.putClientProperty(prop, null);
            return;
        }
        if (aktuell == null || aktuell instanceof ModernIcons.VektorIcon) return;
        if (!(aktuell instanceof ImageIcon)) return;
        String desc = ((ImageIcon) aktuell).getDescription();
        String key = ModernIcons.schluessel(desc);
        if (key == null) { log(desc, c); return; }
        c.putClientProperty(prop, aktuell);
        set.set(ModernIcons.icon(key, aktuell.getIconWidth(), aktuell.getIconHeight()));
    }

    /** Statische Symbole der Helden-Software (z. B. Basistalent, Hauszauber in Listen) austauschen. */
    private static void factory(boolean m) {
        try {
            Class<?> f = Class.forName("helden.gui.icons.IconFactory", true, Thread.currentThread().getContextClassLoader());
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
