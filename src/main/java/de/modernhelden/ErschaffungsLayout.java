package de.modernhelden;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

/**
 * Erschaffungsdialog: Die Helden-Software legt das Zusatzfeld mit GP/AP unter der Reiterleiste
 * mit festen Werten ab (20 px je Reiter, Breite aus der ersten Messung). In den modernen Designs
 * sind die Reiter höher – das Feld lag dann über den Reitern und dem Inhalt.
 * Hier wird es in den modernen Designs direkt unter dem letzten Reiter und in der Breite der
 * Reiterleiste platziert; im Original-Design bleibt die Berechnung der Helden-Software unverändert.
 */
final class ErschaffungsLayout {

    static final String KLASSE = "helden.gui.erschaffung.dialoge.ExtTabbedPane";
    private static final String MARKE = "modernhelden.zusatzLayout";
    /** Reiterhöhe in den modernen Designs: so hoch wie möglich, aber GP/AP müssen darunter Platz haben. */
    private static final int REITER_MAX = 24, REITER_MIN = 18;

    private ErschaffungsLayout() {}

    static void anwenden(JComponent ext, boolean modern) {
        JTabbedPane tp = null;
        JComponent zusatz = null;
        for (Component k : ext.getComponents()) {
            if (k instanceof JTabbedPane) tp = (JTabbedPane) k;
            else if (k instanceof JPanel) zusatz = (JComponent) k;
        }
        if (tp == null || zusatz == null) return;
        final JTabbedPane reiter = tp;
        final JComponent feld = zusatz;
        if (ext.getClientProperty(MARKE) == null) {
            ComponentListener l = new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) {
                    // nach der eigenen Berechnung der Helden-Software korrigieren
                    if (Theme.isModern()) SwingUtilities.invokeLater(() -> platzieren(ext, reiter, feld));
                }
            };
            ext.putClientProperty(MARKE, l);
            ext.addComponentListener(l);
            reiter.addContainerListener(new java.awt.event.ContainerAdapter() {   // Reiter kommen/gehen
                @Override public void componentAdded(java.awt.event.ContainerEvent e) { neu(); }
                @Override public void componentRemoved(java.awt.event.ContainerEvent e) { neu(); }
                private void neu() { if (Theme.isModern()) SwingUtilities.invokeLater(() -> platzieren(ext, reiter, feld)); }
            });
        }
        if (modern) {
            platzieren(ext, reiter, feld);
        } else if (reiter.getClientProperty("JTabbedPane.tabHeight") != null) {
            reiter.putClientProperty("JTabbedPane.tabHeight", null);
            reiter.putClientProperty("JTabbedPane.tabInsets", null);
            // Originalberechnung der Helden-Software erneut auslösen
            ComponentEvent ev = new ComponentEvent(ext, ComponentEvent.COMPONENT_RESIZED);
            for (ComponentListener l : ext.getComponentListeners())
                if (l != ext.getClientProperty(MARKE)) l.componentResized(ev);
        }
    }

    private static void platzieren(JComponent ext, JTabbedPane reiter, JComponent feld) {
        if (!Theme.isModern() || reiter.getUI() == null) return;
        int n = reiter.getTabCount();
        if (n == 0 || ext.getHeight() <= 0) return;
        // Reiter so flach machen, dass die sichtbaren GP/AP-Felder darunter vollständig Platz haben
        int bedarf = feld.getPreferredSize().height + 18;
        int hoehe = Math.max(REITER_MIN, Math.min(REITER_MAX, (ext.getHeight() - bedarf) / n));
        if (!Integer.valueOf(hoehe).equals(reiter.getClientProperty("JTabbedPane.tabHeight"))) {
            // FlatLaf macht Reiter nie flacher als Schrift + Innenabstand – den Abstand daher mit verkleinern
            int schrift = reiter.getFontMetrics(reiter.getFont()).getHeight();
            int v = Math.max(0, Math.min(4, (hoehe - schrift) / 2));
            reiter.putClientProperty("JTabbedPane.tabInsets", new Insets(v, 12, v, 12));
            reiter.putClientProperty("JTabbedPane.tabHeight", hoehe);
            reiter.revalidate();
        }
        reiter.doLayout();
        Rectangle unten = null;
        int breite = 0;
        for (int i = 0; i < n; i++) {
            Rectangle r;
            try { r = reiter.getUI().getTabBounds(reiter, i); } catch (RuntimeException ex) { continue; }
            if (r == null || r.height <= 0) continue;
            if (unten == null || r.y + r.height > unten.y + unten.height) unten = r;
            breite = Math.max(breite, r.x + r.width);
        }
        if (unten == null || breite <= 0) return;
        Point p = SwingUtilities.convertPoint(reiter, 0, unten.y + unten.height, ext);
        int y = p.y + 8;
        int x = 5;
        int w = Math.max(40, breite - 2 * x);
        int h = Math.max(0, ext.getHeight() - y - 10);
        Rectangle neu = new Rectangle(x, y, w, h);
        if (!neu.equals(feld.getBounds())) {
            feld.setBounds(neu);
            feld.revalidate();
            ext.repaint();
        }
    }
}
