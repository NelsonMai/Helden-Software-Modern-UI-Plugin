package de.modernhelden;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;

/**
 * Zebra-Streifen und Gitterlinien für die Tabellen der Helden-Software in den modernen Designs.
 *
 * Die Original-Tabellen nutzen eigene Renderer, die ihren Hintergrund selbst setzen. Jede Spalte
 * bekommt deshalb eine dünne Hülle um ihren Renderer, die nur „neutrale“ Zellen (Hintergrund wie
 * die Tabelle) jeder zweiten Zeile einfärbt. Bewusst gefärbte Zellen und Knöpfe bleiben unverändert.
 * Beim Wechsel auf „Original“ werden die ursprünglichen Renderer wiederhergestellt.
 */
final class TabellenStil {

    private TabellenStil() {}

    private static final String GITTER = "modernhelden.gitter.orig";

    static void anwenden(JTable t, boolean modern) {
        if (Boolean.TRUE.equals(t.getClientProperty("modernhelden.own"))) return;
        TableColumnModel cm = t.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            TableColumn col = cm.getColumn(i);
            TableCellRenderer r = col.getCellRenderer();
            if (modern) {
                if (r instanceof Zebra) continue;
                col.setCellRenderer(new Zebra(r));       // r == null: Standard-Renderer der Tabelle
            } else if (r instanceof Zebra) {
                col.setCellRenderer(((Zebra) r).original);
            }
        }
        if (modern) {
            if (t.getClientProperty(GITTER) == null) {
                t.putClientProperty(GITTER, new boolean[]{t.getShowHorizontalLines(), t.getShowVerticalLines()});
            }
            Color grid = UIManager.getColor("Table.gridColor");
            if (grid != null && !grid.equals(t.getGridColor())) t.setGridColor(grid);
            if (!t.getShowHorizontalLines()) t.setShowHorizontalLines(true);
            if (!t.getShowVerticalLines()) t.setShowVerticalLines(true);
        } else {
            Object o = t.getClientProperty(GITTER);
            if (o instanceof boolean[]) {
                boolean[] b = (boolean[]) o;
                t.setShowHorizontalLines(b[0]);
                t.setShowVerticalLines(b[1]);
                t.putClientProperty(GITTER, null);
            }
        }
        t.repaint();
    }

    /** Hülle um den eigentlichen Renderer. */
    static final class Zebra implements TableCellRenderer {
        final TableCellRenderer original;

        Zebra(TableCellRenderer original) { this.original = original; }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            TableCellRenderer r = original != null ? original : t.getDefaultRenderer(t.getColumnClass(col));
            Component c = r.getTableCellRendererComponent(t, v, sel, focus, row, col);
            gruppe(t, c, row, col);
            if (c == null && ErschaffungsTabellen.ist(t)) c = leer(t);
            if (c != null && sel) return markiert(t, c);
            if (c == null) return c;
            // Knöpfe in Zellen (z. B. „+1“, „i“) nicht umfärben
            if (c instanceof AbstractButton && !(c instanceof JCheckBox)) {
                if (Boolean.TRUE.equals(((JComponent) c).getClientProperty("modernhelden.warDeckend"))) {
                    ((JComponent) c).setOpaque(true);
                    ((JComponent) c).putClientProperty("modernhelden.warDeckend", null);
                }
                // ausgegrautes „−“/„✕“ im Inventar: erklären, warum
                if (InventarLayout.istInventarTabelle(t)) ((AbstractButton) c).setToolTipText(InventarLayout.sperrHinweis(t, row, col));
                return c;
            }
            Color alt = UIManager.getColor("Table.alternateRowColor");
            Color basis = t.getBackground();
            if (alt == null || basis == null) return c;
            Color bg = c.getBackground();
            boolean neutral = bg == null || bg.equals(basis) || bg.equals(alt) || bg.equals(Color.WHITE)
                    || bg instanceof javax.swing.plaf.UIResource;
            if (!neutral) return c;                    // bewusst gefärbt: so lassen
            // als UIResource setzen, damit ein späterer Designwechsel die Farbe wieder ersetzen darf
            Color neu = row % 2 == 1 ? alt : basis;
            c.setBackground(neu instanceof javax.swing.plaf.UIResource ? neu : new javax.swing.plaf.ColorUIResource(neu));
            if (c instanceof JComponent) ((JComponent) c).setOpaque(true);
            return c;
        }

        /**
         * Erschaffung: Spinner, Auswahllisten, Knöpfe und leere Zellen der Helden-Software färben die gewählte Zeile
         * nicht ein – die Zeile leuchtete nur teilweise. Sie liegen deshalb auf einer Fläche in der Auswahlfarbe
         * (das Bedienelement selbst bleibt, wie es ist).
         */
        /** Zelle ohne Inhalt (die Helden-Software liefert dann gar nichts): leere Fläche, die Zebra und Auswahl färben. */
        private static Component leer(JTable t) {
            Object o = t.getClientProperty("modernhelden.leereZelle");
            if (o instanceof JPanel) return (JPanel) o;
            JPanel p = new JPanel();
            p.setOpaque(true);
            t.putClientProperty("modernhelden.leereZelle", p);
            return p;
        }

        private static Component markiert(JTable t, Component c) {
            if (!ErschaffungsTabellen.ist(t) || c instanceof javax.swing.table.DefaultTableCellRenderer || !(c instanceof JComponent)) return c;
            // dasselbe Element dient der Helden-Software auch als Editor – während der Bearbeitung nicht anfassen
            if (c.getParent() == t || c == t.getEditorComponent()) return c;
            Object o = t.getClientProperty("modernhelden.auswahlFlaeche");
            JPanel f;
            if (o instanceof JPanel) f = (JPanel) o;
            else {
                f = new JPanel(new BorderLayout());
                f.setOpaque(true);
                f.putClientProperty("modernhelden.own", Boolean.TRUE);
                t.putClientProperty("modernhelden.auswahlFlaeche", f);
            }
            // nur umhängen, wenn nötig (jedes Hinzufügen löst Container-Ereignisse aus)
            if (f.getComponentCount() != 1 || f.getComponent(0) != c) {
                f.removeAll();
                f.add(c, BorderLayout.CENTER);
            }
            f.setBackground(t.getSelectionBackground());
            // leere Zellen (Fläche ohne Bedienelement) durchscheinen lassen; ohne Auswahl macht die Zebra-Färbung
            // sie wieder deckend
            if ((c instanceof JPanel || c instanceof JLabel) && ((JComponent) c).getComponentCount() == 0) ((JComponent) c).setOpaque(false);
            // Knöpfe („+“, „-“) füllen sonst ihre ganze Zelle weiß – für die markierte Zeile durchscheinend
            if (c instanceof AbstractButton && !(c instanceof JCheckBox) && c.isOpaque()) {
                ((JComponent) c).setOpaque(false);
                ((JComponent) c).putClientProperty("modernhelden.warDeckend", Boolean.TRUE);
            }
            f.add(c, BorderLayout.CENTER);
            return f;
        }

        /**
         * Gruppierte Tabelle (Client-Property {@link InventarLayout#GRUPPE} = Spalte): Beginnt eine neue Gruppe,
         * bekommt die Zeile oben eine Trennlinie; in der Gruppenspalte steht der Wert nur in der ersten Zeile.
         */
        private static void gruppe(JTable t, Component c, int row, int col) {
            Object g = t.getClientProperty(InventarLayout.GRUPPE);
            if (!(g instanceof Integer) || !(c instanceof JLabel)) return;
            int gs = t.convertColumnIndexToView((Integer) g);
            if (gs < 0 || row >= t.getRowCount()) return;
            Object hier = t.getValueAt(row, gs);
            boolean neu = row == 0 || !java.util.Objects.equals(hier, t.getValueAt(row - 1, gs));
            JLabel l = (JLabel) c;
            if (col == gs) {
                if (!neu) l.setText("");
                else l.setFont(l.getFont().deriveFont(Font.BOLD));
            }
            if (neu && row > 0) {
                Color linie = UIManager.getColor("Component.accentColor");
                l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 0, 0, linie != null ? linie : Color.GRAY), l.getBorder()));
            }
        }
    }
}
