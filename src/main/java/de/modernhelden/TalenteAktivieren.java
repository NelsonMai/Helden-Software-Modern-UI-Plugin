package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dialog „Talente aktivieren“ (Reiter Talente → aktivieren) in den modernen Designs:
 * <ul>
 *   <li>oben Suche und Talentgruppen als Knöpfe (wie im Reiter „Talente“) – die Knöpfe stellen das Auswahlfeld
 *       „Talentgruppe“ der Helden-Software um,</li>
 *   <li>darunter die aktivierbaren Talente (Tabelle der Helden-Software, durchsuchbar),</li>
 *   <li>unten AP-Stand und die aktivierten Talente mit „−“ je Zeile.</li>
 * </ul>
 * Die Helden-Software kennt nur „Letztes Talent löschen“ (nimmt das zuletzt aktivierte zurück, samt AP und
 * Verlaufseintrag). „−“ bei einem älteren Eintrag nimmt mit genau diesem Knopf die neueren und den gewählten zurück
 * und aktiviert die neueren danach mit der Aktivieren-Logik der Helden-Software wieder – in derselben Reihenfolge.
 * Der Dialog wird bei jedem Öffnen neu aufgebaut; im Design „Original“ bleibt er unverändert.
 */
final class TalenteAktivieren {

    private TalenteAktivieren() {}

    static final String TITEL = "Talente aktivieren";

    static void anwenden(JDialog d) {
        if (d.getRootPane().getClientProperty("modernhelden.talAkt") != null) return;
        JTable tabelle = null;
        JList<?> liste = null;
        JComboBox<?> gruppe = null;
        AbstractButton loeschen = null;
        List<JTextField> felder = new ArrayList<>();
        JLabel lGuthaben = null, lEingesetzt = null;
        for (Component k : ErschaffungsSchritte.alle(d.getContentPane(), Component.class)) {
            if (k instanceof JTable && tabelle == null) tabelle = (JTable) k;
            else if (k instanceof JList && liste == null) liste = (JList<?>) k;
            else if (k instanceof JComboBox && gruppe == null) gruppe = (JComboBox<?>) k;
            else if (k instanceof AbstractButton && "Letztes Talent löschen".equals(((AbstractButton) k).getText())) loeschen = (AbstractButton) k;
            else if (k instanceof JTextField && SwingUtilities.getAncestorOfClass(JComboBox.class, k) == null) felder.add((JTextField) k);
            else if (k instanceof JLabel && "AP-Guthaben".equals(((JLabel) k).getText())) lGuthaben = (JLabel) k;
            else if (k instanceof JLabel && "Eingesetzte AP".equals(((JLabel) k).getText())) lEingesetzt = (JLabel) k;
        }
        if (tabelle == null || liste == null || gruppe == null || loeschen == null || felder.size() < 2 || lGuthaben == null
                || lEingesetzt == null || !(liste.getModel() instanceof DefaultListModel)) return;
        JPanel mitte = (JPanel) SwingUtilities.getAncestorOfClass(JPanel.class, gruppe);
        JScrollPane tabScroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tabelle);
        if (mitte == null || tabScroll == null) return;
        // Steuerung der Helden-Software (hört auf „Letztes Talent löschen“, „+“ und die Talentgruppe)
        Object steuerung = null;
        for (java.awt.event.ActionListener al : loeschen.getActionListeners())
            if (!al.getClass().getName().startsWith("java") && !al.getClass().getName().startsWith("de.")) steuerung = al;
        if (steuerung == null) return;
        // Feldreihenfolge im Raster: AP-Guthaben (Spalte 1), Eingesetzte AP (Spalte 3)
        JTextField guthaben = felder.get(0), eingesetzt = felder.get(1);
        if (mitte.getLayout() instanceof GridBagLayout) {
            GridBagLayout gb = (GridBagLayout) mitte.getLayout();
            for (JTextField f : felder) {
                if (gb.getConstraints(f).gridx == 1) guthaben = f;
                if (gb.getConstraints(f).gridx == 3) eingesetzt = f;
            }
        }
        d.getRootPane().putClientProperty("modernhelden.talAkt", Boolean.TRUE);
        new Aufbau(d, mitte, tabelle, tabScroll, liste, gruppe, loeschen, steuerung, guthaben, eingesetzt, lGuthaben, lEingesetzt);
    }

    private static final class Aufbau {
        final JDialog dialog;
        final JTable tabelle;
        final JComboBox<?> gruppe;
        final AbstractButton loeschen;
        final Object steuerung;
        final DefaultListModel<?> gewaehlt;
        final JTextField suche = new JTextField();
        final JPanel chips = new JPanel(new TalenteSortierung.Umbruch(4, 4));
        final JLabel zahl = new JLabel();
        final AbstractTableModel aktiviertModell;
        final TableRowSorter<TableModel> sortierer;
        boolean baueChips;

        Aufbau(JDialog d, JPanel mitte, JTable tab, JScrollPane tabScroll, JList<?> liste, JComboBox<?> grp, AbstractButton loe,
               Object st, JTextField guthaben, JTextField eingesetzt, JLabel lGuthaben, JLabel lEingesetzt) {
            dialog = d; tabelle = tab; gruppe = grp; loeschen = loe; steuerung = st;
            gewaehlt = (DefaultListModel<?>) liste.getModel();

            // ---- Suche in der Tabelle der aktivierbaren Talente (Spalte „Talent“)
            sortierer = new TableRowSorter<TableModel>(tab.getModel()) {
                // die Helden-Software meldet nach dem Aktivieren eine Zeile als geändert, die es nicht mehr gibt
                @Override public void rowsUpdated(int a, int b) {
                    try { super.rowsUpdated(a, b); } catch (IndexOutOfBoundsException e) { allRowsChanged(); }
                }
                @Override public void rowsUpdated(int a, int b, int c) {
                    try { super.rowsUpdated(a, b, c); } catch (IndexOutOfBoundsException e) { allRowsChanged(); }
                }
                @Override public void rowsInserted(int a, int b) {
                    try { super.rowsInserted(a, b); } catch (IndexOutOfBoundsException e) { allRowsChanged(); }
                }
                @Override public void rowsDeleted(int a, int b) {
                    try { super.rowsDeleted(a, b); } catch (IndexOutOfBoundsException e) { allRowsChanged(); }
                }
            };
            for (int i = 0; i < tab.getModel().getColumnCount(); i++) sortierer.setSortable(i, false);
            tab.setRowSorter(sortierer);
            suche.putClientProperty("JTextField.placeholderText", "Talente suchen …");
            suche.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
            suche.putClientProperty("JTextField.showClearButton", true);
            suche.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
            });
            tab.getModel().addTableModelListener(e -> SwingUtilities.invokeLater(this::aktualisieren));
            spalten(tab);

            // ---- Kopf: Suche, Talentgruppen, Anzahl
            JPanel kopf = new JPanel(new BorderLayout(10, 0));
            kopf.setOpaque(false);
            suche.setPreferredSize(new Dimension(220, suche.getPreferredSize().height));
            JPanel suchZeile = new JPanel(new BorderLayout(8, 0));
            suchZeile.setOpaque(false);
            JPanel suchHalter = new JPanel(new BorderLayout());   // Suchfeld oben, nicht auf die Knopfleiste gestreckt
            suchHalter.setOpaque(false);
            suchHalter.add(suche, BorderLayout.NORTH);
            InventarLayout.eigen(suchHalter);
            suchZeile.add(suchHalter, BorderLayout.WEST);
            chips.setOpaque(false);
            suchZeile.add(chips, BorderLayout.CENTER);
            zahl.setForeground(Theme.muted());
            zahl.setVerticalAlignment(SwingConstants.TOP);
            zahl.setBorder(new EmptyBorder(4, 0, 0, 0));
            suchZeile.add(zahl, BorderLayout.EAST);
            kopf.add(suchZeile, BorderLayout.CENTER);

            // ---- unten: AP-Stand und aktivierte Talente
            JPanel ap = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            ap.setOpaque(false);
            guthaben.setColumns(8);
            eingesetzt.setColumns(8);
            ap.add(lGuthaben);
            ap.add(guthaben);
            ap.add(Box.createHorizontalStrut(12));
            ap.add(lEingesetzt);
            ap.add(eingesetzt);

            aktiviertModell = new AbstractTableModel() {
                final String[] k = {"Aktiviertes Talent", "Probe", "AP", ""};
                @Override public int getRowCount() { return gewaehlt.size(); }
                @Override public int getColumnCount() { return k.length; }
                @Override public String getColumnName(int c) { return k[c]; }
                @Override public Object getValueAt(int r, int c) {
                    Object t = gewaehlt.get(r);
                    switch (c) {
                        case 0: return String.valueOf(t);
                        case 1: return String.valueOf(ErschaffungsRahmen.rufe(t, "getTalentprobe"));
                        case 2: { Integer n = kosten(t); return n == null ? "" : n + " AP"; }
                        default: return "−";
                    }
                }
            };
            gewaehlt.addListDataListener(new javax.swing.event.ListDataListener() {
                public void intervalAdded(javax.swing.event.ListDataEvent e) { aktiviertModell.fireTableDataChanged(); }
                public void intervalRemoved(javax.swing.event.ListDataEvent e) { aktiviertModell.fireTableDataChanged(); }
                public void contentsChanged(javax.swing.event.ListDataEvent e) { aktiviertModell.fireTableDataChanged(); }
            });
            JTable aktiviert = new JTable(aktiviertModell) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            aktiviert.setRowHeight(tab.getRowHeight());
            aktiviert.getTableHeader().setReorderingAllowed(false);
            aktiviert.getColumnModel().getColumn(0).setPreferredWidth(260);
            aktiviert.getColumnModel().getColumn(1).setPreferredWidth(110);
            aktiviert.getColumnModel().getColumn(2).setPreferredWidth(60);
            aktiviert.getColumnModel().getColumn(3).setPreferredWidth(100);
            aktiviert.getColumnModel().getColumn(3).setMinWidth(80);
            aktiviert.getColumnModel().getColumn(3).setMaxWidth(110);
            aktiviert.getColumnModel().getColumn(3).setCellRenderer(new KnopfZelle());
            aktiviert.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int r = aktiviert.rowAtPoint(e.getPoint()), c = aktiviert.columnAtPoint(e.getPoint());
                    if (r >= 0 && c == 3 && SwingUtilities.isLeftMouseButton(e)) entfernen(r);
                }
            });
            aktiviert.setToolTipText("„−“ nimmt das Talent wieder zurück (die AP werden erstattet)");
            JScrollPane aktScroll = new JScrollPane(aktiviert);
            aktScroll.setPreferredSize(new Dimension(100, 170));
            JLabel ueber = new JLabel("Aktivierte Talente");
            ueber.setFont(ueber.getFont().deriveFont(Font.BOLD));
            JPanel unten = new JPanel(new BorderLayout(0, 6));
            unten.setOpaque(false);
            JPanel unterKopf = new JPanel(new BorderLayout());
            unterKopf.setOpaque(false);
            unterKopf.add(ueber, BorderLayout.WEST);
            unterKopf.add(ap, BorderLayout.EAST);
            unten.add(unterKopf, BorderLayout.NORTH);
            unten.add(aktScroll, BorderLayout.CENTER);

            // ---- neu anordnen
            mitte.removeAll();
            mitte.setLayout(new BorderLayout(0, 10));
            mitte.setBorder(new EmptyBorder(10, 12, 6, 12));
            mitte.add(kopf, BorderLayout.NORTH);
            mitte.add(tabScroll, BorderLayout.CENTER);
            mitte.add(unten, BorderLayout.SOUTH);
            for (JComponent c : new JComponent[]{kopf, suchZeile, chips, zahl, suche, ap, unten, unterKopf, ueber, aktScroll, aktiviert})
                InventarLayout.eigen(c);

            groesse();
            aktualisieren();
            mitte.revalidate();
            mitte.repaint();
            SwingUtilities.invokeLater(suche::requestFocusInWindow);
        }

        private void spalten(JTable t) {
            javax.swing.table.TableColumnModel cm = t.getColumnModel();
            int[] b = {34, 260, 110, 40, 50, 70};
            for (int i = 0; i < b.length && i < cm.getColumnCount(); i++) cm.getColumn(i).setPreferredWidth(b[i]);
            if (cm.getColumnCount() > 5) cm.getColumn(5).setHeaderValue("Aktivieren");
        }

        private void groesse() {
            GraphicsConfiguration gc = dialog.getGraphicsConfiguration();
            if (gc == null) return;
            Rectangle s = gc.getBounds();
            Insets i = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int w = Math.min(820, s.width - i.left - i.right - 40), h = Math.min(760, s.height - i.top - i.bottom - 40);
            Rectangle r = dialog.getBounds();
            if (w <= r.width && h <= r.height) return;
            w = Math.max(w, r.width); h = Math.max(h, r.height);
            int x = Math.max(s.x + i.left, Math.min(r.x + (r.width - w) / 2, s.x + s.width - i.right - w));
            int y = Math.max(s.y + i.top, Math.min(r.y + (r.height - h) / 2, s.y + s.height - i.bottom - h));
            dialog.setBounds(x, y, w, h);
        }

        private void filtern() {
            final String q = suche.getText().trim().toLowerCase(Locale.GERMAN);
            sortierer.setRowFilter(q.isEmpty() ? null : new RowFilter<TableModel, Integer>() {
                @Override public boolean include(Entry<? extends TableModel, ? extends Integer> e) {
                    return String.valueOf(e.getValue(1)).toLowerCase(Locale.GERMAN).contains(q);
                }
            });
            anzahl();
        }

        /** Gruppen-Knöpfe aus dem Auswahlfeld „Talentgruppe“, mit Anzahl der aktivierbaren Talente. */
        private void aktualisieren() {
            if (baueChips) return;
            baueChips = true;
            try {
                Map<String, Integer> anzahl = gruppenAnzahl();
                chips.removeAll();
                Object gewaehlteGruppe = gruppe.getSelectedItem();
                for (int i = 0; i < gruppe.getItemCount(); i++) {
                    final Object g = gruppe.getItemAt(i);
                    String name = String.valueOf(g);
                    boolean alle = name.endsWith("Alle");
                    Integer n = alle ? null : anzahl.get(name);
                    if (!alle && (n == null || n == 0)) continue;
                    JToggleButton b = chip(alle ? "Alle" : name + " " + n, g.equals(gewaehlteGruppe));
                    b.addActionListener(e -> {
                        gruppe.setSelectedItem(g);   // die Helden-Software filtert ihre Tabelle selbst
                        aktualisieren();
                    });
                    chips.add(b);
                }
                chips.revalidate();
                chips.repaint();
                anzahl();
            } finally {
                baueChips = false;
            }
        }

        /** Anzahl je Talentgruppe: über alle Gruppen des Auswahlfelds, gezählt in der Tabelle bei „Alle“. */
        private Map<String, Integer> gruppenAnzahl() {
            Map<String, Integer> m = new LinkedHashMap<>();
            TableModel tm = tabelle.getModel();
            Object vorher = gruppe.getSelectedItem();
            Object alle = null;
            for (int i = 0; i < gruppe.getItemCount(); i++) if (String.valueOf(gruppe.getItemAt(i)).endsWith("Alle")) alle = gruppe.getItemAt(i);
            boolean umgestellt = false;
            if (alle != null && !alle.equals(vorher)) {
                // still auf „Alle“ zählen, ohne dass jemand die Umschaltung sieht
                umgestellt = true;
                ErschaffungsRahmen.rufeMit(tm, "o00000", String.class, String.valueOf(alle));
            }
            for (int r = 0; r < tm.getRowCount(); r++) {
                Object t = tm.getValueAt(r, tm.getColumnCount() - 1);
                Object art = ErschaffungsRahmen.rufe(t, "getArt");
                if (art != null) m.merge(String.valueOf(art), 1, Integer::sum);
            }
            if (umgestellt) ErschaffungsRahmen.rufeMit(tm, "o00000", String.class, String.valueOf(vorher));
            return m;
        }

        private void anzahl() {
            int alle = tabelle.getModel().getRowCount(), sichtbar = tabelle.getRowCount();
            zahl.setText(sichtbar == alle ? alle + " Einträge" : sichtbar + " von " + alle);
        }

        private JToggleButton chip(String text, boolean an) {
            JToggleButton b = new JToggleButton(text, an);
            b.setFocusable(false);
            b.putClientProperty("JButton.buttonType", "roundRect");
            b.putClientProperty("modernhelden.own", true);
            b.setMargin(new Insets(1, 8, 1, 8));
            Color a = Theme.accent();
            b.putClientProperty("FlatLaf.style", String.format(
                    "selectedBackground: #%02x%02x%02x; selectedForeground: #ffffff", a.getRed(), a.getGreen(), a.getBlue()));
            return b;
        }

        /** AP, die das Aktivieren gekostet hat (merkt sich die Steuerung der Helden-Software je Talent). */
        private Integer kosten(Object talent) {
            for (Field f : steuerung.getClass().getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = ((Map<?, ?>) f.get(steuerung)).get(talent);
                    if (v instanceof Integer) return (Integer) v;
                } catch (ReflectiveOperationException | RuntimeException ignore) { }
            }
            return null;
        }

        /**
         * Talent an Position {@code k} (0 = zuletzt aktiviert) zurücknehmen: mit „Letztes Talent löschen“ die neueren
         * und dieses zurücknehmen, dann die neueren mit der Aktivieren-Logik der Helden-Software wieder aktivieren.
         */
        private void entfernen(int k) {
            if (k < 0 || k >= gewaehlt.size()) return;
            List<Object> neuere = new ArrayList<>();
            List<Boolean> se = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                Object t = gewaehlt.get(i);
                neuere.add(t);
                se.add(seMarkiert(t));
            }
            Method aktivieren = neuere.isEmpty() ? null : aktivierenMethode(neuere.get(0));
            if (!neuere.isEmpty() && aktivieren == null) {
                // ohne die Aktivieren-Logik nicht umsortieren – dann geht nur das zuletzt aktivierte
                JOptionPane.showMessageDialog(dialog, "Hier lässt sich nur das zuletzt aktivierte Talent zurücknehmen.");
                return;
            }
            for (int i = 0; i <= k; i++) loeschen.doClick(0);
            for (int i = neuere.size() - 1; i >= 0; i--) {
                try {
                    aktivieren.invoke(steuerung, neuere.get(i), se.get(i));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    break;
                }
            }
            aktiviertModell.fireTableDataChanged();
            aktualisieren();
        }

        /** Stand der SE-Markierung, die die Tabelle der Helden-Software je Talent führt. */
        private boolean seMarkiert(Object talent) {
            for (Method m : tabelle.getModel().getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getReturnType() == boolean.class && m.getParameterTypes()[0].isInstance(talent)) {
                    try { return (Boolean) m.invoke(tabelle.getModel(), talent); } catch (ReflectiveOperationException | RuntimeException ignore) { }
                }
            }
            return false;
        }

        /** Die Aktivieren-Logik der Steuerung (Talent, SE) – dieselbe, die „+“ nach der AP-Prüfung aufruft. */
        private Method aktivierenMethode(Object talent) {
            for (Method m : steuerung.getClass().getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[1] == boolean.class && p[0].isInstance(talent) && m.getReturnType() == void.class) {
                    m.setAccessible(true);
                    return m;
                }
            }
            return null;
        }
    }

    /** „−“ als kleiner Knopf in der Zelle. */
    private static final class KnopfZelle extends JButton implements javax.swing.table.TableCellRenderer {
        KnopfZelle() {
            super("−");
            setMargin(new Insets(0, 12, 0, 12));
            setFont(getFont().deriveFont(Font.BOLD));
            putClientProperty("JButton.buttonType", "roundRect");
        }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean f, int r, int c) { return this; }
    }
}
