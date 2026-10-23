package de.modernhelden;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.text.Collator;
import java.util.*;

/**
 * Reiter „Talente“ und „Zauber“ in den modernen Designs: Klick auf einen Spaltenkopf sortiert die Tabelle (Art, Talent, Wert,
 * Lernmethode, Kategorie, Kosten …), ein weiterer Klick kehrt die Reihenfolge um, ein dritter stellt die
 * ursprüngliche Reihenfolge wieder her. Die Helden-Software greift beim Steigern über den Knopf bzw. über die
 * Tabelle selbst auf die Zeilen zu – das funktioniert auch sortiert. Im Design „Original“ wird nicht sortiert.
 */
final class TalenteSortierung {

    private TalenteSortierung() {}

    private static final String MARKE = "modernhelden.talentSortierung";
    /** Tabellen mit ihren Spalten; Spalten mit Knöpfen („Steigern“, „Info“) werden nicht sortiert. */
    private static final String[][] TABELLEN = {
            {"SE", "Art", "Talent", "Wert", "Lernmethode", "Kat.", "Kosten", "Steigern"},
            {"SE", "Zauberfertigkeit", "Komp", "Rep", "Merkmale", "Haus", "Wert", "Steigern", "Lernmethode", "Kosten",
                    "Info", "Quelle", "Lern"}};
    private static final Set<String> KNOEPFE = new HashSet<>(Arrays.asList("Steigern", "Info"));

    static boolean istTalentTabelle(JTable t) { return spalten(t) != null; }

    private static String[] spalten(JTable t) {
        TableModel m = t.getModel();
        naechste:
        for (String[] sp : TABELLEN) {
            if (m.getColumnCount() != sp.length) continue;
            for (int i = 0; i < sp.length; i++) if (!sp[i].equals(m.getColumnName(i))) continue naechste;
            return sp;
        }
        return null;
    }

    static void pruefen(JTable t, boolean modern) {
        Zustand z = (Zustand) t.getClientProperty(MARKE);
        if (z == null) {
            if (!modern || !istTalentTabelle(t)) return;
            z = new Zustand(t);
            t.putClientProperty(MARKE, z);
        }
        if (modern) z.an(); else z.aus();
    }

    private static final class Zustand {
        final JTable t;
        boolean aktiv;
        List<? extends RowSorter.SortKey> sortierung = Collections.emptyList();

        Zustand(JTable t) {
            this.t = t;
            // die Helden-Software setzt beim Heldenwechsel ein neues Modell – Sortierung dafür neu einrichten
            t.addPropertyChangeListener("model", e -> {
                if (!aktiv) return;
                RowSorter<?> rs = t.getRowSorter();
                if (rs != null && rs.getModel() != t.getModel()) t.setRowSorter(null);
                SwingUtilities.invokeLater(this::einrichten);
            });
        }

        void an() {
            if (aktiv) return;
            aktiv = true;
            einrichten();
        }

        void aus() {
            if (!aktiv) return;
            aktiv = false;
            t.setRowSorter(null);
            t.getTableHeader().repaint();
        }

        void einrichten() {
            if (!aktiv || !istTalentTabelle(t)) return;
            RowSorter<?> alt = t.getRowSorter();
            if (alt != null && alt.getModel() == t.getModel()) return;
            TableRowSorter<TableModel> s = new TableRowSorter<>(t.getModel());
            Comparator<Object> c = TalenteSortierung::vergleiche;
            String[] sp = spalten(t);
            for (int i = 0; i < sp.length; i++) {
                s.setComparator(i, c);
                if (KNOEPFE.contains(sp[i])) s.setSortable(i, false);
            }
            s.setSortsOnUpdates(true);
            // Klick 1: aufsteigend, Klick 2: absteigend, Klick 3: ursprüngliche Reihenfolge
            s.setMaxSortKeys(1);
            try { s.setSortKeys(sortierung); } catch (IllegalArgumentException ignore) { }
            s.addRowSorterListener(ev -> {
                if (ev.getType() != javax.swing.event.RowSorterEvent.Type.SORT_ORDER_CHANGED) return;
                if (t.getModel() != s.getModel()) return;   // beim Modellwechsel nicht merken
                sortierung = new ArrayList<>(s.getSortKeys());
            });
            t.setRowSorter(new DreiStufen(s));
            t.getTableHeader().repaint();
        }
    }

    /** Sortierer, der beim dritten Klick auf denselben Spaltenkopf die Sortierung aufhebt. */
    private static final class DreiStufen extends RowSorter<TableModel> {
        final TableRowSorter<TableModel> s;

        DreiStufen(TableRowSorter<TableModel> s) {
            this.s = s;
            s.addRowSorterListener(e -> {
                // Ereignisse des inneren Sortierers weiterreichen
                if (e.getType() == javax.swing.event.RowSorterEvent.Type.SORT_ORDER_CHANGED) fireSortOrderChanged();
                else fireRowSorterChanged(null);
            });
        }

        @Override public TableModel getModel() { return s.getModel(); }
        @Override public void toggleSortOrder(int column) {
            List<? extends SortKey> k = s.getSortKeys();
            if (!k.isEmpty() && k.get(0).getColumn() == column && k.get(0).getSortOrder() == SortOrder.DESCENDING) {
                s.setSortKeys(null);
            } else {
                s.toggleSortOrder(column);
            }
        }
        @Override public int convertRowIndexToModel(int index) { return s.convertRowIndexToModel(index); }
        // Die Helden-Software meldet teils eine Zeile zu viel („Zeilen 0 bis Anzahl geändert“) – ohne Sortierung
        // unschädlich, mit Sortierung muss sie abgefangen werden
        @Override public int convertRowIndexToView(int index) {
            return index < 0 || index >= s.getModelRowCount() ? -1 : s.convertRowIndexToView(index);
        }
        @Override public void setSortKeys(List<? extends SortKey> keys) { s.setSortKeys(keys); }
        @Override public List<? extends SortKey> getSortKeys() { return s.getSortKeys(); }
        @Override public int getViewRowCount() { return s.getViewRowCount(); }
        @Override public int getModelRowCount() { return s.getModelRowCount(); }
        @Override public void modelStructureChanged() { s.modelStructureChanged(); }
        @Override public void allRowsChanged() { s.allRowsChanged(); }
        @Override public void rowsInserted(int a, int b) { s.rowsInserted(a, b); }
        @Override public void rowsDeleted(int a, int b) { s.rowsDeleted(a, b); }
        @Override public void rowsUpdated(int a, int b) {
            int n = s.getModelRowCount();
            if (n == 0) return;
            s.rowsUpdated(Math.min(Math.max(a, 0), n - 1), Math.min(Math.max(b, 0), n - 1));
        }
        @Override public void rowsUpdated(int a, int b, int c) {
            int n = s.getModelRowCount();
            if (n == 0) return;
            s.rowsUpdated(Math.min(Math.max(a, 0), n - 1), Math.min(Math.max(b, 0), n - 1), c);
        }
    }

    private static final Collator TEXT = Collator.getInstance(Locale.GERMAN);

    /** Zahlen nach Wert, Texte alphabetisch; leere Zellen (z. B. Kosten bei Meta-Talenten) immer ans Ende. */
    static int vergleiche(Object a, Object b) {
        boolean la = leer(a), lb = leer(b);
        if (la || lb) return la == lb ? 0 : la ? 1 : -1;
        Double x = zahl(a), y = zahl(b);
        if (x != null && y != null) return Double.compare(x, y);
        if (a instanceof Boolean && b instanceof Boolean) return Boolean.compare((Boolean) a, (Boolean) b);
        return TEXT.compare(String.valueOf(a), String.valueOf(b));
    }

    private static boolean leer(Object o) { return o == null || String.valueOf(o).trim().isEmpty(); }

    private static Double zahl(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.valueOf(String.valueOf(o).trim()); } catch (NumberFormatException e) { return null; }
    }
}
