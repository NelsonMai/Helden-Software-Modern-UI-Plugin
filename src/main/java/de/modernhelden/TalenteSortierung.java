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

    // ---------------------------------------------------------------- eigene Tabellen (Reiter „Plugin: Modern-UI“)

    /** Macht eine eigene Tabelle per Spaltenkopf sortierbar (aufsteigend, absteigend, ursprünglich). */
    static void sortierbar(JTable t) {
        TableRowSorter<TableModel> s = new TableRowSorter<>(t.getModel());
        for (int i = 0; i < t.getModel().getColumnCount(); i++) s.setComparator(i, TalenteSortierung::vergleiche);
        s.setMaxSortKeys(1);
        t.setRowSorter(new DreiStufen(s));
    }

    /**
     * Sortierer für Tabellen mit Gruppen-Kopfzeilen (z. B. Talente nach Talentgruppe): sortiert nur innerhalb der
     * Gruppen, die Kopfzeilen und die Reihenfolge der Gruppen bleiben erhalten.
     */
    static void sortierbarInGruppen(JTable t, java.util.function.IntPredicate kopfzeile) {
        t.setRowSorter(new GruppenSortierer(t.getModel(), kopfzeile));
    }

    private static final class GruppenSortierer extends RowSorter<TableModel> {
        final TableModel m;
        final java.util.function.IntPredicate kopf;
        List<SortKey> keys = Collections.emptyList();
        int[] v2m = new int[0], m2v = new int[0];

        GruppenSortierer(TableModel m, java.util.function.IntPredicate kopf) {
            this.m = m;
            this.kopf = kopf;
            sortieren(false);
        }

        void sortieren(boolean melden) {
            int[] alt = v2m;
            int n = m.getRowCount();
            Integer[] idx = new Integer[n];
            final int[] gruppe = new int[n];
            int g = -1;
            for (int i = 0; i < n; i++) {
                idx[i] = i;
                if (kopf.test(i)) g = i;
                gruppe[i] = g;
            }
            if (!keys.isEmpty()) {
                final int c = keys.get(0).getColumn();
                final int dir = keys.get(0).getSortOrder() == SortOrder.DESCENDING ? -1 : 1;
                Arrays.sort(idx, (a, b) -> {
                    if (gruppe[a] != gruppe[b]) return Integer.compare(gruppe[a], gruppe[b]);
                    boolean ka = kopf.test(a), kb = kopf.test(b);
                    if (ka || kb) return ka == kb ? 0 : ka ? -1 : 1;
                    Object x = m.getValueAt(a, c), y = m.getValueAt(b, c);
                    // leere Zellen bleiben auch absteigend am Ende
                    if (leer(x) || leer(y)) return vergleiche(x, y);
                    return dir * vergleiche(x, y);
                });
            }
            v2m = new int[n];
            m2v = new int[n];
            for (int i = 0; i < n; i++) { v2m[i] = idx[i]; m2v[idx[i]] = i; }
            if (melden) fireRowSorterChanged(alt);
        }

        @Override public TableModel getModel() { return m; }
        @Override public void toggleSortOrder(int column) {
            List<SortKey> k;
            if (keys.isEmpty() || keys.get(0).getColumn() != column) k = Collections.singletonList(new SortKey(column, SortOrder.ASCENDING));
            else if (keys.get(0).getSortOrder() == SortOrder.ASCENDING) k = Collections.singletonList(new SortKey(column, SortOrder.DESCENDING));
            else k = Collections.emptyList();
            setSortKeys(k);
        }
        @Override public void setSortKeys(List<? extends SortKey> k) {
            keys = k == null ? Collections.emptyList() : new ArrayList<>(k);
            fireSortOrderChanged();
            sortieren(true);
        }
        @Override public List<? extends SortKey> getSortKeys() { return keys; }
        @Override public int convertRowIndexToModel(int i) { return i < 0 || i >= v2m.length ? -1 : v2m[i]; }
        @Override public int convertRowIndexToView(int i) { return i < 0 || i >= m2v.length ? -1 : m2v[i]; }
        @Override public int getViewRowCount() { return v2m.length; }
        @Override public int getModelRowCount() { return m.getRowCount(); }
        @Override public void modelStructureChanged() { sortieren(true); }
        @Override public void allRowsChanged() { sortieren(true); }
        @Override public void rowsInserted(int a, int b) { sortieren(true); }
        @Override public void rowsDeleted(int a, int b) { sortieren(true); }
        @Override public void rowsUpdated(int a, int b) { sortieren(true); }
        @Override public void rowsUpdated(int a, int b, int c) { sortieren(true); }
    }

    private static final java.util.regex.Pattern DATUM = java.util.regex.Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");

    private static final Collator TEXT = Collator.getInstance(Locale.GERMAN);

    /** Zahlen nach Wert, Texte alphabetisch; leere Zellen (z. B. Kosten bei Meta-Talenten) immer ans Ende. */
    static int vergleiche(Object a, Object b) {
        boolean la = leer(a), lb = leer(b);
        if (la || lb) return la == lb ? 0 : la ? 1 : -1;
        Double x = zahl(a), y = zahl(b);
        if (x == null || y == null) { x = datum(a); y = datum(b); }
        if (x != null && y != null) return Double.compare(x, y);
        if (a instanceof Boolean && b instanceof Boolean) return Boolean.compare((Boolean) a, (Boolean) b);
        return TEXT.compare(String.valueOf(a), String.valueOf(b));
    }

    /** „TT.MM.JJJJ“ als sortierbare Zahl JJJJMMTT. */
    private static Double datum(Object o) {
        java.util.regex.Matcher d = DATUM.matcher(String.valueOf(o).trim());
        if (!d.matches()) return null;
        return Double.valueOf(d.group(3) + String.format("%02d%02d", Integer.parseInt(d.group(2)), Integer.parseInt(d.group(1))));
    }

    private static boolean leer(Object o) { return o == null || String.valueOf(o).trim().isEmpty(); }

    private static Double zahl(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.valueOf(String.valueOf(o).trim()); } catch (NumberFormatException e) { return null; }
    }
}
