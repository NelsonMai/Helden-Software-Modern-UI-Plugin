package de.modernhelden;

import javax.swing.*;
import java.awt.*;

/**
 * „Angaben Bearbeiten“ der Helden-Software hat eine feste Größe (400×600). Mit breiterer Schrift (z. B. unter Windows
 * oder mit Skalierung) ist der Inhalt breiter als der sichtbare Bereich – das Raster quetscht dann die
 * Beschriftungsspalte und schneidet „Augenfarbe“ usw. links ab. In den modernen Designs wird der Dialog beim Öffnen
 * so groß gemacht, wie der Inhalt es braucht (höchstens so groß wie der Bildschirm). Am Inhalt ändert sich nichts.
 */
final class AngabenDialog {

    private AngabenDialog() {}

    static final String TITEL = "Angaben Bearbeiten";

    /** Die Helden-Software verwendet den Dialog wieder – geänderte Dialoge für die Rückkehr zum Original merken. */
    private static final java.util.Map<JDialog, Runnable> ZURUECK = new java.util.WeakHashMap<>();

    /** Beim Wechsel auf „Original“: Beschriftungen und Größe wie vorher. */
    static void zuruecksetzen() {
        for (Runnable r : new java.util.ArrayList<>(ZURUECK.values())) {
            try { r.run(); } catch (RuntimeException ignore) { }
        }
        ZURUECK.clear();
    }

    static void anwenden(JDialog d) {
        if (!TITEL.equalsIgnoreCase(String.valueOf(d.getTitle()))) return;
        JScrollPane sp = ErschaffungsRahmen.erstes(d.getContentPane(), JScrollPane.class);
        if (sp == null || sp.getViewport().getView() == null) return;
        if (d.getRootPane().getClientProperty("modernhelden.angaben") == null) {
            d.getRootPane().putClientProperty("modernhelden.angaben", Boolean.TRUE);
            // Reserve für die Beschriftungen: unter Windows wird die Schrift teils ein paar Pixel breiter gezeichnet
            // als berechnet – ohne Reserve fehlt dann der letzte Buchstabe („Augenfarb…“)
            java.util.Map<JLabel, javax.swing.border.Border> rahmen = new java.util.HashMap<>();
            for (JLabel l : ErschaffungsSchritte.alle(sp.getViewport().getView() instanceof Container
                    ? (Container) sp.getViewport().getView() : d.getContentPane(), JLabel.class)) {
                javax.swing.border.Border b = l.getBorder();
                rahmen.put(l, b);
                javax.swing.border.Border reserve = BorderFactory.createEmptyBorder(0, 4, 0, 4);
                l.setBorder(b == null ? reserve : BorderFactory.createCompoundBorder(b, reserve));
            }
            final Dimension vorher = d.getSize();
            ZURUECK.put(d, () -> {
                for (java.util.Map.Entry<JLabel, javax.swing.border.Border> e : rahmen.entrySet()) e.getKey().setBorder(e.getValue());
                d.setSize(vorher);
                d.getRootPane().putClientProperty("modernhelden.angaben", null);
                d.validate();
            });
        }
        d.validate();
        Dimension inhalt = sp.getViewport().getView().getPreferredSize();
        Dimension sicht = sp.getViewport().getExtentSize();
        int sb = Math.max(sp.getVerticalScrollBar().getPreferredSize().width, 12);
        int mehrBreite = inhalt.width + sb + 8 - sicht.width;
        int mehrHoehe = inhalt.height + 4 - sicht.height;
        GraphicsConfiguration gc = d.getGraphicsConfiguration();
        if (gc == null) return;
        Rectangle schirm = gc.getBounds();
        Insets ri = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int maxB = schirm.width - ri.left - ri.right - 40, maxH = schirm.height - ri.top - ri.bottom - 40;
        Rectangle r = d.getBounds();
        int w = Math.min(maxB, Math.max(r.width, r.width + mehrBreite));
        int h = Math.min(maxH, Math.max(r.height, r.height + mehrHoehe));
        if (w == r.width && h == r.height) return;
        int x = r.x + (r.width - w) / 2, y = r.y + (r.height - h) / 2;
        x = Math.max(schirm.x + ri.left, Math.min(x, schirm.x + schirm.width - ri.right - w));
        y = Math.max(schirm.y + ri.top, Math.min(y, schirm.y + schirm.height - ri.bottom - h));
        d.setBounds(x, y, w, h);
        d.validate();
    }
}
