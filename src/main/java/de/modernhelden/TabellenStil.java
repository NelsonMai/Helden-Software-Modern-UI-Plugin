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
            if (c == null || sel) return c;
            // Knöpfe in Zellen (z. B. „+1“, „i“) nicht umfärben
            if (c instanceof AbstractButton && !(c instanceof JCheckBox)) return c;
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
    }
}
