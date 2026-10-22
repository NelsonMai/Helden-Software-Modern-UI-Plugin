package de.modernhelden;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Nur im Debug-Modus (Ordner „debug“ im Plugin-Verzeichnis): Legt man dort die Datei „baum.anfordern“ an,
 * schreibt das Plugin den Komponentenbaum aller offenen Fenster nach „debug/baum.log“ und löscht die Anforderung.
 * Dient der Analyse von Fenstern der Helden-Software; ändert nichts.
 */
final class DebugBaum {

    private DebugBaum() {}

    static File ordner;

    static void pruefen() {
        if (ordner == null) return;
        File anf = new File(ordner, "baum.anfordern");
        if (!anf.isFile()) return;
        anf.delete();
        StringBuilder sb = new StringBuilder();
        for (Window w : Window.getWindows()) {
            if (w.isShowing()) knoten(sb, w, 0);
        }
        try (FileWriter fw = new FileWriter(new File(ordner, "baum.log"))) {
            fw.write(sb.toString());
        } catch (IOException ignore) { }
    }

    private static void knoten(StringBuilder sb, Component c, int tiefe) {
        for (int i = 0; i < tiefe; i++) sb.append("  ");
        sb.append(c.getClass().getName());
        Class<?> sup = c.getClass().getSuperclass();
        if (sup != null && !sup.getName().startsWith("java")) sb.append(" <").append(sup.getName()).append('>');
        Rectangle b = c.getBounds();
        sb.append(" b=").append(b.x).append(',').append(b.y).append(' ').append(b.width).append('x').append(b.height);
        if (c.getName() != null) sb.append(" name=").append(c.getName());
        if (c.getParent() != null && c.getParent().getLayout() instanceof GridBagLayout) {
            GridBagConstraints g = ((GridBagLayout) c.getParent().getLayout()).getConstraints(c);
            sb.append(" gb=").append(g.gridx).append(',').append(g.gridy).append('/').append(g.gridwidth).append('x').append(g.gridheight);
        }
        if (!c.isVisible()) sb.append(" INVISIBLE");
        if (c instanceof Container && ((Container) c).getLayout() != null)
            sb.append(" L=").append(((Container) c).getLayout().getClass().getSimpleName());
        if (c instanceof AbstractButton) {
            AbstractButton bt = (AbstractButton) c;
            sb.append(" \"").append(bt.getText()).append('"');
            for (java.awt.event.ActionListener al : bt.getActionListeners()) sb.append(" AL=").append(al.getClass().getName());
            if (bt.getAction() != null) sb.append(" ACTION=").append(bt.getAction().getClass().getName());
        } else if (c instanceof JLabel) {
            sb.append(" \"").append(((JLabel) c).getText()).append('"');
        } else if (c instanceof JTextField) {
            sb.append(" text=\"").append(((JTextField) c).getText()).append('"');
        } else if (c instanceof JComboBox) {
            JComboBox<?> cb = (JComboBox<?>) c;
            sb.append(" model=").append(cb.getModel().getClass().getName()).append(" n=").append(cb.getItemCount())
                    .append(" sel=").append(cb.getSelectedItem());
        } else if (c instanceof JList) {
            JList<?> l = (JList<?>) c;
            sb.append(" model=").append(l.getModel().getClass().getName()).append(" n=").append(l.getModel().getSize());
            if (l.getModel().getSize() > 0) {
                Object o = l.getModel().getElementAt(0);
                sb.append(" erstes=").append(o == null ? "null" : o.getClass().getName() + ":" + o);
            }
            sb.append(" renderer=").append(l.getCellRenderer().getClass().getName());
        } else if (c instanceof JTable) {
            JTable t = (JTable) c;
            sb.append(" model=").append(t.getModel().getClass().getName()).append(" rows=").append(t.getRowCount()).append(" cols=");
            for (int i = 0; i < t.getColumnCount(); i++) {
                sb.append('[').append(t.getColumnName(i)).append(':').append(t.getColumnClass(i).getSimpleName());
                Object r = t.getColumnModel().getColumn(i).getCellRenderer();
                if (r instanceof TabellenStil.Zebra) r = ((TabellenStil.Zebra) r).original;
                if (r != null) sb.append(" R=").append(r.getClass().getName());
                Object e = t.getColumnModel().getColumn(i).getCellEditor();
                if (e != null) sb.append(" E=").append(e.getClass().getName());
                sb.append(']');
            }
            if (t.getRowCount() > 0) {
                sb.append(" zeile0=");
                for (int i = 0; i < t.getColumnCount(); i++) {
                    Object v = t.getValueAt(0, i);
                    sb.append(v == null ? "null" : v.getClass().getSimpleName() + ":" + v).append(" | ");
                }
            }
        } else if (c instanceof JTabbedPane) {
            JTabbedPane tp = (JTabbedPane) c;
            for (int i = 0; i < tp.getTabCount(); i++) sb.append(" tab=\"").append(tp.getTitleAt(i)).append('"');
        }
        if (c instanceof Frame) sb.append(" title=\"").append(((Frame) c).getTitle()).append('"');
        if (c instanceof Dialog) sb.append(" title=\"").append(((Dialog) c).getTitle()).append('"').append(((Dialog) c).isModal() ? " modal" : "");
        sb.append('\n');
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) knoten(sb, k, tiefe + 1);
    }
}
