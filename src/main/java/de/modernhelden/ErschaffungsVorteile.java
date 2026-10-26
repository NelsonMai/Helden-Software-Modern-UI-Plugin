package de.modernhelden;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Erschaffung, Schritt Vor- und Nachteile: Bereits gewählte Einträge nicht erneut anbieten.
 * <p>
 * Die Helden-Software blendet einen gewählten Vor-/Nachteil in der Liste der möglichen über eine Ausschlussliste aus
 * ({@code addExclude} beim Hinzufügen). Baut sie die Liste neu auf – etwa beim Wechsel „Mögliche“ ↔ „Auch ungeeignete“
 * –, geht diese Ausschlussliste verloren und die gewählten Einträge lassen sich ein zweites Mal hinzufügen. Hier wird
 * der Ausschluss mit derselben Methode der Helden-Software wiederhergestellt – nach derselben Regel wie beim
 * Hinzufügen: selbst gewählte Einträge ohne Auswahl-Zusatz (Einträge wie „Begabung für [Talent]“ dürfen mehrfach
 * vorkommen). Am Helden ändert sich nichts, nur an der angebotenen Liste.
 */
final class ErschaffungsVorteile {

    private ErschaffungsVorteile() {}

    private static final String MIT_ZUSATZ = "helden.framework.Object.thissuper";
    private static final String TABDEF = "TabDefMoeglicheVorteileErschaffung";

    static void doppelteAusblenden(JDialog dialog) {
        try {
            Object hsw = hswVorteile(dialog);
            Object moeglich = ErschaffungsRahmen.rufe(hsw, "getMoegliche");
            Object gewaehlt = ErschaffungsRahmen.rufe(hsw, "getVorteile");
            if (moeglich == null || !(gewaehlt instanceof List)) return;
            Method istAuto = methode(hsw, "isAuto");
            Method ausgeschlossen = methode(moeglich, "isExcluded");
            Method ausschliessen = null;
            for (Method m : moeglich.getClass().getMethods())
                if (m.getName().equals("addExclude") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Object.class) ausschliessen = m;
            if (istAuto == null || ausgeschlossen == null || ausschliessen == null) return;
            Class<?> mitZusatz;
            try {
                mitZusatz = Class.forName(MIT_ZUSATZ, false, hsw.getClass().getClassLoader());
            } catch (ClassNotFoundException e) {
                return;
            }
            boolean geaendert = false;
            for (Object k : (List<?>) gewaehlt) {
                if (k == null || mitZusatz.isInstance(k)) continue;
                if (Boolean.TRUE.equals(istAuto.invoke(hsw, k))) continue;
                if (Boolean.TRUE.equals(ausgeschlossen.invoke(moeglich, k))) continue;
                ausschliessen.invoke(moeglich, k);
                geaendert = true;
            }
            if (geaendert) neuZeigen(dialog.getContentPane());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignore) {
            // niemals die Erschaffung stören
        }
    }

    /** Die Vor-/Nachteil-Daten des Erschaffungs-Assistenten (beim Eingeben/Editieren: keine, dort null). */
    static Object hswVorteile(Component c) {
        Window w = c instanceof Window ? (Window) c : SwingUtilities.getWindowAncestor(c);
        return ErschaffungsRahmen.rufe(ErschaffungsRahmen.rufe(w, "getWerkezug"), "getHswVorteile");
    }

    private static Method methode(Object o, String name) {
        for (Method m : o.getClass().getMethods())
            if (m.getName().equals(name) && m.getParameterCount() == 1) return m;
        return null;
    }

    /** Tabellen der möglichen Vor-/Nachteile neu lesen lassen. */
    private static void neuZeigen(Container c) {
        for (Component k : c.getComponents()) {
            if (ErschaffungsTabellen.ist(k)) {
                JTable t = (JTable) k;
                Object def = ErschaffungsRahmen.rufe(t.getModel(), "getTabDef");
                boolean eingabe = "Vorteile".equals(t.getName()) || "Nachteile".equals(t.getName());
                if (def != null && (def.getClass().getSimpleName().equals(TABDEF) || eingabe) && t.getModel() instanceof AbstractTableModel)
                    ((AbstractTableModel) t.getModel()).fireTableDataChanged();
            }
            if (k instanceof Container) neuZeigen((Container) k);
        }
    }

    // ------------------------------------------------------------------ Gewählte getrennt nach Vor- und Nachteilen

    private static final String GETEILT = "modernhelden.vnGeteilt";

    /**
     * Die Liste „Automatische und gewählte Vor- und Nachteile“ in zwei Tabellen nebeneinander zeigen: links die
     * Vorteile, rechts die Nachteile. Die zweite Tabelle legt die Helden-Software selbst an (ihr Konstruktor mit
     * derselben Tabellendefinition) – Knöpfe, Spinner und Auswahllisten sind also ihre eigenen und arbeiten auf
     * denselben Daten. Beide Tabellen zeigen per festem Zeilenfilter nur ihren Teil.
     */
    static void teilen(JPanel huelle, JComponent mitte) {
        for (JTable t : ErschaffungsSchritte.alle(mitte, JTable.class)) {
            if (!ErschaffungsTabellen.ist(t) || t.getClientProperty(GETEILT) != null) continue;
            Object def = ErschaffungsRahmen.rufe(t.getModel(), "getTabDef");
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, t);
            if (sp == null || !(sp.getParent() instanceof JPanel) || !(sp.getParent().getLayout() instanceof GridBagLayout)) continue;
            // Erschaffen: Name der Tabellendefinition; Eingeben/Editieren: Überschrift über der Tabelle
            boolean gewaehlte = String.valueOf(ErschaffungsRahmen.rufe(def, "getGlobalname")).startsWith("Automatische und gewählte Vor");
            for (Component k : sp.getParent().getComponents())
                if (k instanceof JLabel && String.valueOf(((JLabel) k).getText()).startsWith("Automatische und gewählte Vor")) gewaehlte = true;
            if (!gewaehlte) continue;
            Object hsw = hswVorteile(huelle);
            final JTable tab = t;
            java.util.function.IntPredicate nachteil = r -> istNachteil(hsw, tab, r);
            // zweite Tabelle über den Konstruktor der Helden-Software (ErschaffungsJTable(TabellenDefinition))
            JTable zweite = null;
            try {
                for (java.lang.reflect.Constructor<?> c : t.getClass().getConstructors())
                    if (c.getParameterCount() == 1 && c.getParameterTypes()[0].isInstance(def)) zweite = (JTable) c.newInstance(def);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                zweite = null;
            }
            if (zweite == null) continue;
            t.putClientProperty(GETEILT, Boolean.TRUE);
            final JTable z2 = zweite;
            z2.putClientProperty(GETEILT, Boolean.TRUE);
            z2.setRowHeight(t.getRowHeight());

            // die Helden-Software aktualisiert nur ihre eigene Tabelle – die zweite zieht mit
            // sofort (nicht erst später), sonst wird die zweite Tabelle kurz mit der alten Zeilenzahl gezeichnet
            javax.swing.event.TableModelListener mit = e -> {
                if (z2.isEditing()) z2.removeEditor();
                z2.clearSelection();
                Object m = z2.getModel();
                boolean fertig = false;
                try { m.getClass().getMethod("aktualisiereAlles").invoke(m); fertig = true; } catch (ReflectiveOperationException | RuntimeException ignore) { }
                if (!fertig && m instanceof AbstractTableModel) ((AbstractTableModel) m).fireTableDataChanged();
                // Spinner/Auswahllisten der Zeilen nachziehen, wenn die Helden-Software mit ihrer Tabelle fertig ist
                SwingUtilities.invokeLater(() -> { ErschaffungsRahmen.rufe(z2, "aktualisieren"); z2.repaint(); });
            };
            t.getModel().addTableModelListener(mit);

            Container p = sp.getParent();
            GridBagConstraints gbc = ((GridBagLayout) p.getLayout()).getConstraints(sp);
            JPanel geteilt = new JPanel(new GridLayout(1, 2, 10, 0));
            geteilt.setOpaque(false);
            InventarLayout.eigen(geteilt);
            p.remove(sp);
            JScrollPane sp2 = new JScrollPane(z2);
            geteilt.add(seite("Vorteile", sp));
            geteilt.add(seite("Nachteile", sp2));
            p.add(geteilt, gbc);
            int[] breiten = breiten(t);
            breiten(z2);
            // Spaltenkopf je Seite: „Vorteil“ bzw. „Nachteil“ statt „Vor-/Nachteil“
            Runnable kopfZurueck = ueberschrift(t, "Vorteil");
            ueberschrift(z2, "Nachteil");
            ErschaffungsTabellen.grundfilter(t, r -> !nachteil.test(r));
            ErschaffungsTabellen.grundfilter(z2, nachteil);
            p.revalidate();
            p.repaint();

            ErschaffungsSchritte.rueck(huelle, () -> {
                t.getModel().removeTableModelListener(mit);
                kopfZurueck.run();
                for (int i = 0; i < breiten.length && i < t.getColumnModel().getColumnCount(); i++)
                    t.getColumnModel().getColumn(i).setPreferredWidth(breiten[i]);
                ErschaffungsTabellen.grundfilter(t, null);
                Container q = geteilt.getParent();
                if (q != null) {
                    q.remove(geteilt);
                    sp.getParent().remove(sp);
                    q.add(sp, gbc);
                    q.revalidate();
                    q.repaint();
                }
                t.putClientProperty(GETEILT, null);
            });
        }
    }

    /** Kopf der Namensspalte („Vor-/Nachteil“) umbenennen – nur in der Ansicht; liefert die Rücknahme. */
    private static Runnable ueberschrift(JTable t, String neu) {
        javax.swing.table.TableColumnModel cm = t.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            javax.swing.table.TableColumn c = cm.getColumn(i);
            Object alt = c.getHeaderValue();
            if (!String.valueOf(alt).trim().startsWith("Vor-/Nachteil")) continue;
            c.setHeaderValue(neu);
            if (t.getTableHeader() != null) t.getTableHeader().repaint();
            return () -> { c.setHeaderValue(alt); if (t.getTableHeader() != null) t.getTableHeader().repaint(); };
        }
        return () -> { };
    }

    /**
     * Mögliche Vor-/Nachteile: die Namensspalte so breit, dass auch „Akademische Ausbildung (Gelehrter)“ ganz zu
     * sehen ist; dafür wird die (meist leere) Spalte vor „GP“ schmaler.
     */
    static void spaltenOben(JPanel huelle, JComponent mitte) {
        for (JTable t : ErschaffungsSchritte.alle(mitte, JTable.class)) {
            if (!ErschaffungsTabellen.ist(t) || t.getClientProperty("modernhelden.vnSpalten") != null) continue;
            Object def = ErschaffungsRahmen.rufe(t.getModel(), "getTabDef");
            boolean eingabe = "Vorteile".equals(t.getName()) || "Nachteile".equals(t.getName());
            if (def == null || !(def.getClass().getSimpleName().equals(TABDEF) || eingabe)) continue;
            javax.swing.table.TableColumnModel cm = t.getColumnModel();
            int name = -1, gp = -1;
            for (int i = 0; i < cm.getColumnCount(); i++) {
                String h = String.valueOf(cm.getColumn(i).getHeaderValue()).trim();
                if (h.startsWith("Vor-/Nachteil")) name = i;
                if (h.equals("GP")) gp = i;
            }
            if (name < 0) continue;
            if (gp < 1 || gp - 1 == name) {
                // Eingeben/Editieren: keine GP-Spalte – nur den Namen breit genug machen
                if (!eingabe) continue;
                t.putClientProperty("modernhelden.vnSpalten", Boolean.TRUE);
                javax.swing.table.TableColumn n = cm.getColumn(name);
                int altN = n.getPreferredWidth();
                n.setPreferredWidth(Math.max(altN, t.getFontMetrics(t.getFont()).stringWidth("Akademische Ausbildung (Gelehrter)") + 24));
                ErschaffungsSchritte.rueck(huelle, () -> { n.setPreferredWidth(altN); t.putClientProperty("modernhelden.vnSpalten", null); });
                continue;
            }
            t.putClientProperty("modernhelden.vnSpalten", Boolean.TRUE);
            javax.swing.table.TableColumn n = cm.getColumn(name), vor = cm.getColumn(gp - 1);
            int altN = n.getPreferredWidth(), altV = vor.getPreferredWidth();
            int soll = t.getFontMetrics(t.getFont()).stringWidth("Akademische Ausbildung (Gelehrter)") + 24;
            n.setPreferredWidth(Math.max(altN, soll));
            vor.setPreferredWidth(Math.min(altV, 40));
            ErschaffungsSchritte.rueck(huelle, () -> {
                n.setPreferredWidth(altN);
                vor.setPreferredWidth(altV);
                t.putClientProperty("modernhelden.vnSpalten", null);
            });
        }
    }

    /**
     * Spaltenbreiten für die halbe Breite: Name breit, Wert mittel, die Zusatz-Auswahlen (nur bei Einträgen mit
     * mehreren Angaben belegt), Kosten und Knopf schmal. Liefert die bisherigen Breiten.
     */
    private static boolean hatNachteilFrage(Object o) {
        try {
            return o.getClass().getMethod("\u00d600000").getReturnType() == boolean.class;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static int[] breiten(JTable t) {
        javax.swing.table.TableColumnModel cm = t.getColumnModel();
        int[] alt = new int[cm.getColumnCount()];
        // Erschaffen: Name, Wert, zwei Zusatz-Auswahlen, Kosten, Knopf; Eingeben: Name, Wert, Knopf
        int[] neu = alt.length >= 6 ? new int[]{180, 66, 18, 18, 60, 30} : alt.length == 3 ? new int[]{220, 90, 30} : new int[]{180};
        for (int i = 0; i < alt.length; i++) {
            alt[i] = cm.getColumn(i).getPreferredWidth();
            if (i < neu.length) cm.getColumn(i).setPreferredWidth(neu[i]);
        }
        return alt;
    }

    private static JComponent seite(String titel, JScrollPane sp) {
        JPanel s = new JPanel(new BorderLayout(0, 4));
        s.setOpaque(false);
        InventarLayout.eigen(s);
        JLabel l = new JLabel(titel);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        InventarLayout.eigen(l);
        s.add(l, BorderLayout.NORTH);
        s.add(sp, BorderLayout.CENTER);
        return s;
    }

    /** Ist der gewählte Eintrag in dieser Zeile (Modell) ein Nachteil? */
    private static boolean istNachteil(Object hsw, JTable t, int zeile) {
        Object k = null;
        Object l = ErschaffungsRahmen.rufe(hsw, "getVorteile");
        if (l instanceof List && zeile >= 0 && zeile < ((List<?>) l).size()) k = ((List<?>) l).get(zeile);
        // ohne Werkzeug: der Eintrag selbst steht in den Zellen der Zeile (Spalte mit Knopf/Wert)
        for (int c = 1; k == null && zeile >= 0 && zeile < t.getModel().getRowCount() && c < t.getModel().getColumnCount(); c++) {
            Object v = t.getModel().getValueAt(zeile, c);
            if (v != null && !(v instanceof String) && hatNachteilFrage(v)) k = v;
        }
        if (k == null) return false;
        // dieselbe Einteilung wie bei den Reitern „Vorteile“/„Nachteile“ der möglichen: die Liste der Helden-Software
        // fragt dafür den Eintrag selbst (boolesche Methode ohne Parameter, in 5.6.0 „\u00d600000“)
        try {
            Method m = k.getClass().getMethod("\u00d600000");
            if (m.getReturnType() == boolean.class) return (Boolean) m.invoke(k);
        } catch (ReflectiveOperationException | RuntimeException ignore) { }
        return false;
    }
}
