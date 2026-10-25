package de.modernhelden;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Beim Start: ein Standbild des Hauptfensters liegt als Glass-Pane über allem, während Design und Ansichten
 * umgestellt werden. Weg ist es, sobald sich im Fenster eine Weile nichts mehr am Aufbau ändert (höchstens nach
 * einigen Sekunden). So geht es direkt vom Original ins neue Design, ohne Zwischenbild. Danach kommt die Glass-Pane
 * der Helden-Software unverändert zurück.
 */
final class Startvorhang {

    private static final int RUHE_MS = 300, HOECHSTENS_MS = 4000;

    private final JFrame frame;
    private final Component alteGlasPane;
    private boolean alteSichtbar;
    private final JComponent bild;
    private long letzteAenderung = System.currentTimeMillis();
    private java.awt.event.AWTEventListener lauscher;

    private Startvorhang(JFrame f, Component alt, JComponent b) { frame = f; alteGlasPane = alt; bild = b; }

    static Startvorhang zeigen(JFrame f) {
        try {
            if (f == null || !f.isShowing()) return null;
            JRootPane rp = f.getRootPane();
            int w = rp.getWidth(), h = rp.getHeight();
            if (w <= 0 || h <= 0) return null;
            // in der echten Bildschirmauflösung aufnehmen (Windows-Skalierung 125 %, 150 % …), sonst wirkt das
            // Standbild hochskaliert und verwaschen
            java.awt.geom.AffineTransform skal = f.getGraphicsConfiguration().getDefaultTransform();
            final double sx = skal.getScaleX(), sy = skal.getScaleY();
            BufferedImage img = new BufferedImage((int) Math.ceil(w * sx), (int) Math.ceil(h * sy), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(sx, sy);
            rp.paint(g);
            g.dispose();
            JComponent b = new JComponent() {
                @Override protected void paintComponent(Graphics gr) {
                    // Pixel für Pixel aufs Gerät: Skalierung des Grafikkontexts aufheben
                    Graphics2D g2 = (Graphics2D) gr.create();
                    java.awt.geom.AffineTransform t = g2.getTransform();
                    g2.setTransform(new java.awt.geom.AffineTransform(1, 0, 0, 1, t.getTranslateX(), t.getTranslateY()));
                    g2.drawImage(img, 0, 0, null);
                    g2.dispose();
                }
            };
            b.putClientProperty("modernhelden.own", Boolean.TRUE);
            b.setOpaque(true);
            Startvorhang v = new Startvorhang(f, f.getGlassPane(), b);
            v.alteSichtbar = f.getGlassPane().isVisible();
            f.setGlassPane(b);
            b.setVisible(true);
            rp.paintImmediately(0, 0, w, h);
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Wegnehmen, sobald der Aufbau ruht (keine neuen/entfernten Komponenten mehr im Fenster). */
    void wegNachAufbau() {
        final long start = System.currentTimeMillis();
        lauscher = ev -> {
            if (ev.getSource() instanceof Component && SwingUtilities.getWindowAncestor((Component) ev.getSource()) == frame)
                letzteAenderung = System.currentTimeMillis();
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(lauscher, AWTEvent.CONTAINER_EVENT_MASK);
        Timer t = new Timer(100, null);
        t.addActionListener(e -> {
            long jetzt = System.currentTimeMillis();
            if (jetzt - letzteAenderung >= RUHE_MS || jetzt - start >= HOECHSTENS_MS) {
                t.stop();
                weg();
            }
        });
        t.start();
    }

    private void weg() {
        try {
            Toolkit.getDefaultToolkit().removeAWTEventListener(lauscher);
            bild.setVisible(false);
            if (frame.getGlassPane() == bild) {
                frame.setGlassPane(alteGlasPane);
                alteGlasPane.setVisible(alteSichtbar);
            }
            frame.getRootPane().repaint();
        } catch (RuntimeException ignore) { }
    }
}
