package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/**
 * Dialog „Nachteile senken“ der Helden-Software in den modernen Designs: größer, mit Suche, Filtern nach Art des
 * Senkens und sortierbaren Spalten. Gesenkt wird weiter über die Knöpfe der Helden-Software – deren Knopf-Zelle
 * merkt sich den Knopf je Nachteil (nicht je Zeilennummer), Sortieren und Filtern ändern also nichts daran,
 * welcher Nachteil gesenkt wird.
 */
final class NachteileSenken {
    static final String TITEL = "Nachteile senken";
    private static final String MARKE = "modernhelden.nachteileSenken";

    private NachteileSenken() { }

    /** Arten, wie die Helden-Software einen Nachteil senkt (Text ihres Knopfes). */
    private enum Art {
        ALLE("Alle"), SE("−1 mit SE"), EINS("−1 ohne SE"), ABBAUEN("Abbauen");
        final String name;
        Art(String name) { this.name = name; }
    }

    static void anwenden(JDialog d) {
        if (Boolean.TRUE.equals(d.getRootPane().getClientProperty(MARKE))) return;
        d.getRootPane().putClientProperty(MARKE, Boolean.TRUE);
        JTable tab = null;
        ArrayDeque<Component> q = new ArrayDeque<>();
        q.add(d.getContentPane());
        while (!q.isEmpty() && tab == null) {
            Component k = q.poll();
            if (k instanceof JTable) tab = (JTable) k;
            else if (k instanceof Container) for (Component x : ((Container) k).getComponents()) q.add(x);
        }
        JComponent kopf = null;
        if (tab != null && tab.getColumnCount() == 3) {
            try { kopf = new Aufbau(tab).kopf; } catch (RuntimeException e) { kopf = null; }
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tab);
            Container halter = sp == null ? null : sp.getParent();
            if (kopf != null && halter != null && halter.getLayout() instanceof BorderLayout
                    && ((BorderLayout) halter.getLayout()).getLayoutComponent(BorderLayout.NORTH) == null)
                halter.add(kopf, BorderLayout.NORTH);
            else kopf = null;
        }
        groesse(d, tab, kopf);
    }

    /** So groß, dass möglichst alle Nachteile ohne Blättern sichtbar sind (höchstens 80 % des Hauptfensters). */
    private static void groesse(JDialog d, JTable tab, JComponent kopf) {
        Window owner = d.getOwner();
        Rectangle bereich = owner != null && owner.isShowing() ? owner.getBounds()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int zeilen = tab == null ? 12 : Math.max(tab.getModel().getRowCount(), 6);
        int zeilenHoehe = tab == null ? 20 : tab.getRowHeight();
        Insets r = d.getInsets();
        int hoch = r.top + r.bottom + 30 + zeilen * zeilenHoehe + 60 + (kopf == null ? 0 : kopf.getPreferredSize().height);
        int h = Math.max(d.getHeight(), Math.min(hoch, (int) (bereich.height * 0.8)));
        int w = Math.max(d.getWidth(), Math.min(780, (int) (bereich.width * 0.9)));
        d.setSize(w, h);
        d.setLocationRelativeTo(owner);
        if (tab != null && tab.getColumnCount() == 3) {
            TableColumnModel cm = tab.getColumnModel();
            cm.getColumn(0).setPreferredWidth(330);
            cm.getColumn(1).setPreferredWidth(70);
            cm.getColumn(2).setPreferredWidth(320);
        }
        d.validate();
    }

    private static final class Aufbau {
        final JTable tab;
        final JTextField suche = new JTextField();
        final JPanel chips = new JPanel(new TalenteSortierung.Umbruch(4, 4));
        final JLabel zahl = new JLabel();
        final JPanel kopf = new JPanel(new BorderLayout(10, 0));
        final TableRowSorter<TableModel> sortierer;
        Art art = Art.ALLE;
        String letzteZaehlung = "";

        Aufbau(JTable tab) {
            this.tab = tab;
            sortierer = new TableRowSorter<TableModel>(tab.getModel()) {
                // die Helden-Software baut ihr Modell nach jedem Senken neu auf – Zeilenmeldungen notfalls als „alles neu“
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
                // Klick 1: aufsteigend, Klick 2: absteigend, Klick 3: ursprüngliche Reihenfolge
                @Override public void toggleSortOrder(int column) {
                    if (!isSortable(column)) return;
                    List<? extends SortKey> k = getSortKeys();
                    if (!k.isEmpty() && k.get(0).getColumn() == column && k.get(0).getSortOrder() == SortOrder.DESCENDING) setSortKeys(null);
                    else super.toggleSortOrder(column);
                }
            };
            sortierer.setMaxSortKeys(1);
            sortierer.setSortable(0, true);
            sortierer.setComparator(0, TalenteSortierung::vergleiche);
            sortierer.setSortable(1, true);
            sortierer.setComparator(1, TalenteSortierung::vergleiche);
            sortierer.setSortable(2, false);
            tab.setRowSorter(sortierer);
            tab.getTableHeader().setReorderingAllowed(false);

            suche.putClientProperty("JTextField.placeholderText", "Nachteile suchen …");
            suche.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
            suche.putClientProperty("JTextField.showClearButton", true);
            suche.setPreferredSize(new Dimension(220, suche.getPreferredSize().height));
            suche.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
            });
            tab.getModel().addTableModelListener(e -> SwingUtilities.invokeLater(() -> { chipsNeu(); filtern(); }));

            kopf.setOpaque(false);
            kopf.setBorder(new EmptyBorder(8, 8, 8, 8));
            chips.setOpaque(false);
            zahl.setForeground(Theme.muted());
            kopf.add(suche, BorderLayout.WEST);
            kopf.add(chips, BorderLayout.CENTER);
            kopf.add(zahl, BorderLayout.EAST);
            for (JComponent c : new JComponent[]{kopf, chips, zahl, suche}) InventarLayout.eigen(c);
            chipsNeu();
            filtern();
            SwingUtilities.invokeLater(suche::requestFocusInWindow);
        }

        /** Art einer Modellzeile – am Text des Knopfes, den die Helden-Software für diesen Nachteil zeigt. */
        private Art art(int modellZeile) {
            Object wert = tab.getModel().getValueAt(modellZeile, 2);
            if (wert == null) return Art.ABBAUEN;
            String text = "";
            try {
                TableCellRenderer r = tab.getColumnModel().getColumn(tab.convertColumnIndexToView(2)).getCellRenderer();
                if (r == null) r = tab.getDefaultRenderer(Object.class);
                Component k = r.getTableCellRendererComponent(tab, wert, false, false, 0, 2);
                if (k instanceof AbstractButton) text = String.valueOf(((AbstractButton) k).getText());
                else if (k instanceof JLabel) text = String.valueOf(((JLabel) k).getText());
            } catch (RuntimeException ignore) { }
            if (text.contains("Spezielle")) return Art.SE;
            if (text.trim().equals("-1") || text.trim().equals("−1")) return Art.EINS;
            return Art.ABBAUEN;
        }

        /** Filter-Knöpfe mit Anzahl; nur Arten, die bei diesem Helden vorkommen. */
        private void chipsNeu() {
            int[] n = new int[Art.values().length];
            TableModel m = tab.getModel();
            for (int i = 0; i < m.getRowCount(); i++) n[art(i).ordinal()]++;
            n[Art.ALLE.ordinal()] = m.getRowCount();
            StringBuilder sb = new StringBuilder();
            for (int x : n) sb.append(x).append(',');
            sb.append(art).append(Theme.current());
            if (sb.toString().equals(letzteZaehlung)) return;
            letzteZaehlung = sb.toString();
            if (art != Art.ALLE && n[art.ordinal()] == 0) art = Art.ALLE;
            chips.removeAll();
            ButtonGroup g = new ButtonGroup();
            for (Art a : Art.values()) {
                if (a != Art.ALLE && n[a.ordinal()] == 0) continue;
                JToggleButton b = chip(a == Art.ALLE ? "Alle" : a.name + " " + n[a.ordinal()], a == art);
                b.addActionListener(e -> { art = a; letzteZaehlung = ""; chipsNeu(); filtern(); });
                g.add(b);
                chips.add(b);
            }
            chips.revalidate();
            chips.repaint();
        }

        private void filtern() {
            final String s = suche.getText().trim().toLowerCase(Locale.GERMAN);
            final Art a = art;
            sortierer.setRowFilter(s.isEmpty() && a == Art.ALLE ? null : new RowFilter<TableModel, Integer>() {
                @Override public boolean include(Entry<? extends TableModel, ? extends Integer> e) {
                    if (!s.isEmpty() && !String.valueOf(e.getValue(0)).toLowerCase(Locale.GERMAN).contains(s)) return false;
                    return a == Art.ALLE || art(e.getIdentifier()) == a;
                }
            });
            int alle = tab.getModel().getRowCount(), sichtbar = tab.getRowCount();
            zahl.setText(sichtbar == alle ? alle + " Nachteile" : sichtbar + " von " + alle);
        }

        private static JToggleButton chip(String text, boolean an) {
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
    }
}
