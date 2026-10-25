package de.modernhelden;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.lang.reflect.Method;
import java.text.Collator;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tabellen im Erschaffungs-Assistenten (Eigenschaften, Vor-/Nachteile, Sonderfertigkeiten, Talente, Zauber, Kosten):
 * per Klick auf den Spaltenkopf sortierbar (auf, ab, ursprünglich) und über eine Suchleiste filterbar.
 * <p>
 * Die Tabellen der Helden-Software arbeiten an einigen Stellen mit der Zeilennummer statt mit dem Eintrag:
 * Auswahl einer Zeile und Zusatz-Tooltips. Beides wird hier auf die Zeile im Modell umgerechnet, damit
 * Sortieren und Filtern nie den falschen Eintrag treffen. Knöpfe, Spinner und Auswahllisten hängen ohnehin am
 * Eintrag selbst.
 */
final class ErschaffungsTabellen {

    static final String KLASSE = "helden.gui.erschaffung.dialoge.tabellenDialog.ErschaffungsJTable";
    private static final String MARKE = "modernhelden.erschaffungsTabelle";
    private static final Collator TEXT = Collator.getInstance(Locale.GERMAN);
    private static final Pattern ZAHL = Pattern.compile("^\\s*([-−+]?\\d+(?:[.,]\\d+)?)");

    private ErschaffungsTabellen() {}

    static boolean ist(Component c) { return c instanceof JTable && KLASSE.equals(c.getClass().getName()); }

    /** Sortierung, korrigierte Auswahl und Tooltips einrichten; die Rücknahme wird über {@code rueck} gemerkt. */
    /**
     * Die Zellen-Editoren der Helden-Software melden das Ende der Bearbeitung nie, und für leere Zellen ist der
     * Editor ein leeres Label. Die Tabelle bleibt dann im Bearbeitungsmodus, und dieser Editor verbietet jede neue
     * Zeilenauswahl – nur noch die Knöpfe reagieren. Deshalb: Klick in eine andere Zelle beendet eine laufende
     * Bearbeitung zuerst (Werte übernimmt die Helden-Software ohnehin sofort beim Ändern).
     */
    private static boolean bearbeitenBeenden;

    private static void bearbeitenBeendenEinrichten() {
        if (bearbeitenBeenden) return;
        bearbeitenBeenden = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (ev.getID() != java.awt.event.MouseEvent.MOUSE_PRESSED) return;
            java.awt.event.MouseEvent e = (java.awt.event.MouseEvent) ev;
            if (!(e.getComponent() instanceof JTable)) return;
            JTable t = (JTable) e.getComponent();
            if (t.getClientProperty(MARKE) == null || !t.isEditing()) return;
            int r = t.rowAtPoint(e.getPoint()), c = t.columnAtPoint(e.getPoint());
            if (r == t.getEditingRow() && c == t.getEditingColumn()) return;
            try { t.removeEditor(); } catch (RuntimeException ignore) { }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    static void einrichten(JTable t, Consumer<Runnable> rueck) {
        if (t.getClientProperty(MARKE) != null) return;
        t.putClientProperty(MARKE, Boolean.TRUE);
        bearbeitenBeendenEinrichten();
        Object tabDef = ErschaffungsRahmen.rufe(t.getModel(), "getTabDef");

        RowSorter<? extends TableModel> alterSortierer = t.getRowSorter();
        Sortierer s = new Sortierer(t);
        t.setRowSorter(s);

        // Zeilenauswahl: die Helden-Software erwartet die Zeile im Modell
        List<ListSelectionListener> fremd = new ArrayList<>();
        if (t.getSelectionModel() instanceof javax.swing.DefaultListSelectionModel)
            for (ListSelectionListener l : ((javax.swing.DefaultListSelectionModel) t.getSelectionModel()).getListSelectionListeners())
                if (l.getClass().getName().endsWith("TableSelectionListener")) fremd.add(l);
        ListSelectionListener eigen = e -> {
            if (e.getSource() != t.getSelectionModel() || !t.getRowSelectionAllowed() || e.getValueIsAdjusting()) return;
            int n = t.getSelectedRow();
            if (n < 0 || tabDef == null) return;
            try {
                int m = t.convertRowIndexToModel(n);
                Method sel = tabDef.getClass().getMethod("setSelectedRow", int.class);
                sel.invoke(tabDef, m);
            } catch (ReflectiveOperationException | RuntimeException ignore) { }
        };
        for (ListSelectionListener l : fremd) {
            t.getSelectionModel().removeListSelectionListener(l);
            t.getColumnModel().getSelectionModel().removeListSelectionListener(l);
        }
        if (!fremd.isEmpty()) t.getSelectionModel().addListSelectionListener(eigen);

        if (t.getClientProperty(GRUND) != null) filtern(t, (String) t.getClientProperty(LETZTE_SUCHE), (String) t.getClientProperty(LETZTE_KATEGORIE));

        // Tooltips je Zeile aus dem Modell statt aus der Ansicht
        t.putClientProperty(TooltipHook.TABELLEN_TIP, (java.util.function.Function<Point, String>) p -> tip(t, tabDef, p));
        ToolTipManager.sharedInstance().unregisterComponent(t);

        rueck.accept(() -> {
            t.setRowSorter(alterSortierer);
            if (!fremd.isEmpty()) {
                t.getSelectionModel().removeListSelectionListener(eigen);
                for (ListSelectionListener l : fremd) {
                    t.getSelectionModel().addListSelectionListener(l);
                    t.getColumnModel().getSelectionModel().addListSelectionListener(l);
                }
            }
            t.putClientProperty(TooltipHook.TABELLEN_TIP, null);
            ToolTipManager.sharedInstance().registerComponent(t);
            t.putClientProperty(MARKE, null);
            t.putClientProperty("modernhelden.gefiltert", null);
        });
    }

    /** Tooltip der Helden-Software für die Zelle unter dem Mauszeiger, mit der richtigen Zeile im Modell. */
    private static String tip(JTable t, Object tabDef, Point p) {
        int r = t.rowAtPoint(p), c = t.columnAtPoint(p);
        if (r < 0 || c < 0) return null;
        StringBuilder sb = new StringBuilder();
        Object v = t.getValueAt(r, c);
        if (v != null && !(v instanceof Component)) {
            String text = v.toString();
            FontMetrics fm = t.getFontMetrics(t.getFont());
            if (t.getColumnModel().getColumn(c).getWidth() < fm.stringWidth(text)) sb.append(text).append("<br>");
        }
        Object def = spalte(tabDef, t.convertColumnIndexToModel(c));
        if (def != null) {
            try {
                Object extra = def.getClass().getMethod("getToolTip", int.class).invoke(def, t.convertRowIndexToModel(r));
                if (extra != null) sb.append(extra.toString().replaceFirst("(?i)^<html>", ""));
            } catch (ReflectiveOperationException | RuntimeException ignore) { }
        }
        String s = sb.toString().replaceAll("(?i)<html>|<body>|</body>|</html>", "").trim();
        return s.isEmpty() ? null : "<html>" + s;
    }

    private static Object spalte(Object tabDef, int modellSpalte) {
        Object l = ErschaffungsRahmen.rufe(tabDef, "getSpaltenDefinition");
        if (l instanceof List && modellSpalte >= 0 && modellSpalte < ((List<?>) l).size()) return ((List<?>) l).get(modellSpalte);
        return null;
    }

    // ------------------------------------------------------------------ Sortieren

    /** Auf – ab – ursprüngliche Reihenfolge; robust gegen zu große Zeilenbereiche in Änderungsmeldungen. */
    static final class Sortierer extends TableRowSorter<TableModel> {
        final JTable t;
        Sortierer(JTable t) {
            super(t.getModel());
            this.t = t;
            for (int c = 0; c < t.getModel().getColumnCount(); c++) {
                final int spalte = c;
                String art = rendererArt(t, c);
                if (art.equals("CellButton") || art.equals("CellJPanel") || t.getModel().getColumnName(c).trim().isEmpty()) {
                    setSortable(c, false);
                    continue;
                }
                setComparator(c, (a, b) -> vergleiche(schluessel(t, spalte, a), schluessel(t, spalte, b)));
            }
        }
        @Override public void toggleSortOrder(int column) {
            if (!isSortable(column)) return;
            List<? extends SortKey> k = getSortKeys();
            if (!k.isEmpty() && k.get(0).getColumn() == column && k.get(0).getSortOrder() == SortOrder.DESCENDING) setSortKeys(null);
            else super.toggleSortOrder(column);
        }
        @Override public int convertRowIndexToView(int index) {
            return index < 0 || index >= getModelRowCount() ? -1 : super.convertRowIndexToView(index);
        }
        @Override public void rowsUpdated(int a, int b) {
            int n = getModelRowCount();
            if (n == 0) return;
            super.rowsUpdated(Math.min(Math.max(a, 0), n - 1), Math.min(Math.max(b, 0), n - 1));
        }
        @Override public void rowsUpdated(int a, int b, int c) {
            int n = getModelRowCount();
            if (n == 0) return;
            super.rowsUpdated(Math.min(Math.max(a, 0), n - 1), Math.min(Math.max(b, 0), n - 1), c);
        }
        @Override public void rowsDeleted(int a, int b) {
            try { super.rowsDeleted(a, b); } catch (IndexOutOfBoundsException e) { allRowsChanged(); }
        }
        @Override public void rowsInserted(int a, int b) {
            try { super.rowsInserted(a, b); } catch (IndexOutOfBoundsException e) { allRowsChanged(); }
        }
    }

    /** Renderer der Helden-Software (ohne die Zebra-Hülle des Plugins). */
    private static TableCellRenderer renderer(JTable t, int modellSpalte) {
        int v = t.convertColumnIndexToView(modellSpalte);
        if (v < 0) return null;
        TableCellRenderer r = t.getColumnModel().getColumn(v).getCellRenderer();
        while (r instanceof TabellenStil.Zebra) r = ((TabellenStil.Zebra) r).original;
        return r;
    }

    private static String rendererArt(JTable t, int modellSpalte) {
        TableCellRenderer r = renderer(t, modellSpalte);
        return r == null ? "" : r.getClass().getSimpleName();
    }

    /**
     * Sortier- und Suchschlüssel einer Zelle. Spinner, Auswahllisten und Häkchen hängen am Eintrag; ihr Wert
     * steckt in der Komponente, die der Renderer für diesen Eintrag liefert.
     */
    static Object schluessel(JTable t, int modellSpalte, Object v) {
        if (v == null) return null;
        String art = rendererArt(t, modellSpalte);
        if (art.equals("CellSpinbox") || art.equals("CellComboBox") || art.equals("CellCheckBox") || art.equals("CellVorteile")) {
            int sp = t.convertColumnIndexToView(modellSpalte);
            try {
                Component k = renderer(t, modellSpalte).getTableCellRendererComponent(t, v, false, false, 0, sp);
                Object w = wert(k);
                if (w != null) return w;
            } catch (RuntimeException ignore) { }
        }
        if (v instanceof Component) return wert((Component) v);
        return v;
    }

    private static Object wert(Component k) {
        if (k instanceof JSpinner) return ((JSpinner) k).getValue();
        if (k instanceof JComboBox) { Object s = ((JComboBox<?>) k).getSelectedItem(); return s == null ? "" : s.toString(); }
        if (k instanceof AbstractButton && !(k instanceof JButton)) return ((AbstractButton) k).isSelected();
        if (k instanceof javax.swing.text.JTextComponent) return ((javax.swing.text.JTextComponent) k).getText();
        if (k instanceof JLabel) return ((JLabel) k).getText();
        if (k instanceof Container) {
            for (Component c : ((Container) k).getComponents()) {
                Object w = wert(c);
                if (w != null && !"".equals(w)) return w;
            }
        }
        return null;
    }

    /** Zahlen (auch „75 AP / 2“, „5 GP“) nach Wert, sonst alphabetisch; Leeres ans Ende. */
    static int vergleiche(Object a, Object b) {
        boolean la = leer(a), lb = leer(b);
        if (la || lb) return la == lb ? 0 : la ? 1 : -1;
        if (a instanceof Boolean && b instanceof Boolean) return Boolean.compare((Boolean) b, (Boolean) a);
        Double x = zahl(a), y = zahl(b);
        if (x != null && y != null) return Double.compare(x, y);
        if (x != null) return -1;
        if (y != null) return 1;
        return TEXT.compare(String.valueOf(a).replaceAll("<[^>]*>", "").trim(), String.valueOf(b).replaceAll("<[^>]*>", "").trim());
    }

    private static boolean leer(Object o) { return o == null || String.valueOf(o).replaceAll("<[^>]*>", "").trim().isEmpty(); }

    private static Double zahl(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        Matcher m = ZAHL.matcher(String.valueOf(o));
        if (!m.find()) return null;
        try { return Double.valueOf(m.group(1).replace('−', '-').replace(',', '.')); } catch (NumberFormatException e) { return null; }
    }

    // ------------------------------------------------------------------ Suchen und filtern

    /** Spalte „Kategorie“ (Talente) im Modell, sonst -1. */
    static int kategorieSpalte(JTable t) {
        for (int c = 0; c < t.getModel().getColumnCount(); c++)
            if ("Kategorie".equalsIgnoreCase(t.getModel().getColumnName(c).trim())) return c;
        return -1;
    }

    /** Fester Zeilenfilter einer Tabelle ({@code IntPredicate} über die Zeile im Modell), z. B. „nur Nachteile“. */
    static final String GRUND = "modernhelden.grundfilter";
    private static final String LETZTE_SUCHE = "modernhelden.letzteSuche", LETZTE_KATEGORIE = "modernhelden.letzteKategorie";

    /** Festen Zeilenfilter setzen (null entfernt ihn); Suche und Kategorie bleiben. */
    static void grundfilter(JTable t, java.util.function.IntPredicate p) {
        t.putClientProperty(GRUND, p);
        filtern(t, (String) t.getClientProperty(LETZTE_SUCHE), (String) t.getClientProperty(LETZTE_KATEGORIE));
    }

    /** Zeilen im Modell, die der feste Zeilenfilter zulässt (für die Anzeige „x von y“). */
    static int grundAnzahl(JTable t) {
        Object g = t.getClientProperty(GRUND);
        int n = t.getModel().getRowCount();
        if (!(g instanceof java.util.function.IntPredicate)) return n;
        int z = 0;
        for (int r = 0; r < n; r++) {
            try { if (((java.util.function.IntPredicate) g).test(r)) z++; } catch (RuntimeException ignore) { z++; }
        }
        return z;
    }

    /** Filter für Suchtext (in allen Textspalten) und gewählte Kategorie, zusammen mit dem festen Zeilenfilter. */
    static void filtern(JTable t, String suche, String kategorie) {
        t.putClientProperty(LETZTE_SUCHE, suche);
        t.putClientProperty(LETZTE_KATEGORIE, kategorie);
        if (!(t.getRowSorter() instanceof Sortierer)) return;
        Sortierer s = (Sortierer) t.getRowSorter();
        String q = suche == null ? "" : suche.trim().toLowerCase(Locale.GERMAN);
        int kat = kategorieSpalte(t);
        Object g = t.getClientProperty(GRUND);
        java.util.function.IntPredicate grund = g instanceof java.util.function.IntPredicate ? (java.util.function.IntPredicate) g : null;
        if (q.isEmpty() && (kategorie == null || kat < 0) && grund == null) { s.setRowFilter(null); return; }
        s.setRowFilter(new RowFilter<TableModel, Integer>() {
            @Override public boolean include(Entry<? extends TableModel, ? extends Integer> e) {
                if (grund != null) {
                    try { if (!grund.test(e.getIdentifier())) return false; } catch (RuntimeException ignore) { }
                }
                if (kategorie != null && kat >= 0 && !kategorie.equals(String.valueOf(e.getValue(kat)).trim())) return false;
                if (q.isEmpty()) return true;
                for (int c = 0; c < e.getValueCount(); c++) {
                    Object v = e.getValue(c);
                    if (v == null || v instanceof Component) continue;
                    if (v.toString().replaceAll("<[^>]*>", " ").toLowerCase(Locale.GERMAN).contains(q)) return true;
                }
                return false;
            }
        });
    }

    /** Kategorien mit Anzahl über alle Tabellen (in Reihenfolge des ersten Auftretens). */
    static Map<String, Integer> kategorien(List<JTable> tabellen) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (JTable t : tabellen) {
            int k = kategorieSpalte(t);
            if (k < 0) continue;
            TableModel mo = t.getModel();
            for (int r = 0; r < mo.getRowCount(); r++) {
                Object v;
                try { v = mo.getValueAt(r, k); } catch (RuntimeException e) { continue; }
                if (v == null || String.valueOf(v).trim().isEmpty()) continue;
                m.merge(String.valueOf(v).trim(), 1, Integer::sum);
            }
        }
        return m;
    }
}
