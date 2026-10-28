package de.modernhelden;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Eigene Vektor-Symbole für den Reiter „Eigenschaften“ (Eigenschaften, Energien, Kartenköpfe).
 * Gezeichnet auf einem 24×24-Raster in einer Farbe, damit sie in Hell und Dunkel gleich gut wirken.
 */
final class EigenschaftenSymbole implements Icon {

    private final String key;
    private final int groesse;
    private final Color farbe;

    EigenschaftenSymbole(String key, int groesse, Color farbe) {
        this.key = key;
        this.groesse = groesse;
        this.farbe = farbe;
    }

    @Override public int getIconWidth() { return groesse; }
    @Override public int getIconHeight() { return groesse; }

    @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.translate(x, y);
            zeichne(g, key, groesse, farbe);
        } finally {
            g.dispose();
        }
    }

    static void zeichne(Graphics2D g, String key, int groesse, Color farbe) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float s = groesse / 24f;
        g.scale(s, s);
        g.setColor(farbe);
        g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (key) {
            case "MU": flamme(g); break;
            case "KL": buch(g); break;
            case "IN": auge(g); break;
            case "CH": sprechblase(g); break;
            case "FF": hand(g); break;
            case "GE": feder(g); break;
            case "KO": case "LE": herz(g); break;
            case "KK": hantel(g); break;
            case "AU": blitz(g); break;
            case "AE": funkeln(g); break;
            case "MR": schild(g); break;
            case "KE": sonne(g); break;
            case "stern": kompass(g); break;
            case "kampf": schwerter(g); break;
            case "person": person(g); break;
            case "liste": liste(g); break;
            case "ap": ap(g); break;
            case "info": info(g); break;
            case "kosten": muenzen(g); break;
            case "grenze": grenze(g); break;
            case "rechnen": rechnen(g); break;
            case "speichern": speichern(g); break;
            case "minus": minus(g); break;
            case "bogen": bogen(g); break;
            default: break;
        }
    }

    private static Path2D pfad(double... p) {
        Path2D.Double d = new Path2D.Double();
        d.moveTo(p[0], p[1]);
        for (int i = 2; i + 1 < p.length; i += 2) d.lineTo(p[i], p[i + 1]);
        return d;
    }

    private static void flamme(Graphics2D g) {
        Path2D f = new Path2D.Double();
        f.moveTo(12, 2.5);
        f.curveTo(13.5, 7, 19, 9.5, 19, 15);
        f.curveTo(19, 19, 15.8, 21.5, 12, 21.5);
        f.curveTo(8.2, 21.5, 5, 19, 5, 15);
        f.curveTo(5, 11.5, 7.5, 9.5, 8.5, 7.5);
        f.curveTo(9.2, 9.5, 10, 10.5, 11, 11);
        f.curveTo(11, 7.5, 11.2, 5, 12, 2.5);
        f.closePath();
        g.fill(f);
        Color alt = g.getColor();
        g.setColor(innen(alt));
        Path2D k = new Path2D.Double();
        k.moveTo(12, 12.5);
        k.curveTo(13, 14.5, 15, 15.5, 15, 17.5);
        k.curveTo(15, 19.2, 13.6, 20, 12, 20);
        k.curveTo(10.4, 20, 9, 19.2, 9, 17.5);
        k.curveTo(9, 15.5, 11.2, 14.5, 12, 12.5);
        k.closePath();
        g.fill(k);
        g.setColor(alt);
    }

    /** Aussparung: Hintergrund des Symbols (für zweifarbige Formen). */
    private static Color innen(Color c) {
        Color b = UIManager.getColor("Panel.background");
        if (b == null) b = Color.WHITE;
        return new Color(b.getRed(), b.getGreen(), b.getBlue(), 200);
    }

    private static void buch(Graphics2D g) {
        Path2D l = new Path2D.Double();
        l.moveTo(12, 6.5);
        l.curveTo(9.5, 4.8, 6, 4.5, 3, 5.2);
        l.lineTo(3, 18.5);
        l.curveTo(6, 17.8, 9.5, 18.2, 12, 20);
        l.curveTo(14.5, 18.2, 18, 17.8, 21, 18.5);
        l.lineTo(21, 5.2);
        l.curveTo(18, 4.5, 14.5, 4.8, 12, 6.5);
        l.closePath();
        g.draw(l);
        g.draw(new Line2D.Double(12, 6.5, 12, 20));
        g.draw(new Line2D.Double(5.5, 9, 9.5, 9.6));
        g.draw(new Line2D.Double(5.5, 12.5, 9.5, 13.1));
        g.draw(new Line2D.Double(14.5, 9.6, 18.5, 9));
        g.draw(new Line2D.Double(14.5, 13.1, 18.5, 12.5));
    }

    private static void auge(Graphics2D g) {
        Path2D a = new Path2D.Double();
        a.moveTo(2, 12);
        a.curveTo(5, 6.5, 8.5, 5, 12, 5);
        a.curveTo(15.5, 5, 19, 6.5, 22, 12);
        a.curveTo(19, 17.5, 15.5, 19, 12, 19);
        a.curveTo(8.5, 19, 5, 17.5, 2, 12);
        a.closePath();
        g.draw(a);
        g.fill(new Ellipse2D.Double(8.2, 8.2, 7.6, 7.6));
        Color alt = g.getColor();
        g.setColor(innen(alt));
        g.fill(new Ellipse2D.Double(10.6, 9.6, 2.4, 2.4));
        g.setColor(alt);
    }

    private static void sprechblase(Graphics2D g) {
        Path2D b = new Path2D.Double();
        b.append(new RoundRectangle2D.Double(3, 4, 18, 13, 7, 7), false);
        g.draw(b);
        g.fill(pfad(7.5, 16.5, 7, 21, 11.5, 16.8));
        for (int i = 0; i < 3; i++) g.fill(new Ellipse2D.Double(7.4 + i * 3.8, 9.6, 2.2, 2.2));
    }

    private static void hand(Graphics2D g) {
        // vier Finger, Daumen und Handfläche als gefüllte Flächen
        double[][] finger = {{6.2, 5.5, 9.5}, {9.4, 3.2, 11.8}, {12.6, 3.8, 11.2}, {15.8, 6, 9}};
        for (double[] f : finger) g.fill(new RoundRectangle2D.Double(f[0], f[1], 2.8, f[2], 2.8, 2.8));
        Path2D d = new Path2D.Double();
        d.moveTo(18.6, 13.5);
        d.curveTo(19.5, 12, 20.5, 10.8, 21.6, 11.3);
        d.curveTo(22.3, 11.7, 21.8, 13.4, 21, 14.8);
        d.lineTo(19.2, 17.6);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(d);
        g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        p.moveTo(6.2, 12);
        p.lineTo(19.2, 12);
        p.lineTo(19.2, 16.5);
        p.curveTo(19.2, 19.8, 16.8, 22, 13.5, 22);
        p.lineTo(11.5, 22);
        p.curveTo(8.4, 22, 6.2, 19.8, 6.2, 16.8);
        p.closePath();
        g.fill(p);
    }

    private static void feder(Graphics2D g) {
        Path2D f = new Path2D.Double();
        f.moveTo(20.5, 3);
        f.curveTo(13, 3.5, 7.5, 8, 6.5, 16);
        f.curveTo(9, 15.5, 11, 15, 12.5, 14);
        f.lineTo(10.5, 13.8);
        f.curveTo(12.5, 13.2, 14.5, 12.5, 15.8, 11.2);
        f.lineTo(13.8, 10.9);
        f.curveTo(16, 10, 17.8, 8.6, 18.8, 7);
        f.curveTo(19.8, 5.5, 20.3, 4.2, 20.5, 3);
        f.closePath();
        g.fill(f);
        g.draw(new Line2D.Double(17, 6.5, 4, 21));
    }

    private static void herz(Graphics2D g) {
        Path2D h = new Path2D.Double();
        h.moveTo(12, 21);
        h.curveTo(5, 16, 2.5, 12.5, 2.5, 8.8);
        h.curveTo(2.5, 5.7, 4.8, 3.5, 7.5, 3.5);
        h.curveTo(9.5, 3.5, 11, 4.6, 12, 6.3);
        h.curveTo(13, 4.6, 14.5, 3.5, 16.5, 3.5);
        h.curveTo(19.2, 3.5, 21.5, 5.7, 21.5, 8.8);
        h.curveTo(21.5, 12.5, 19, 16, 12, 21);
        h.closePath();
        g.fill(h);
    }

    private static void hantel(Graphics2D g) {
        g.fill(new RoundRectangle2D.Double(2, 9, 2.6, 6, 1.4, 1.4));
        g.fill(new RoundRectangle2D.Double(4.6, 6, 3.4, 12, 1.6, 1.6));
        g.fill(new RoundRectangle2D.Double(16, 6, 3.4, 12, 1.6, 1.6));
        g.fill(new RoundRectangle2D.Double(19.4, 9, 2.6, 6, 1.4, 1.4));
        g.fill(new Rectangle2D.Double(8, 10.8, 8, 2.4));
    }

    private static void blitz(Graphics2D g) {
        g.fill(pfad(13.5, 2, 4.5, 13.5, 11, 13.5, 9.5, 22, 19.5, 9.5, 12.8, 9.5, 13.5, 2));
    }

    private static void funkeln(Graphics2D g) {
        g.fill(stern4(11, 12.5, 8.5, 2.2));
        g.fill(stern4(18.5, 5.2, 3.2, 0.9));
    }

    private static Shape stern4(double cx, double cy, double r, double t) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - r);
        p.curveTo(cx + t * 0.4, cy - t, cx + t, cy - t * 0.4, cx + r, cy);
        p.curveTo(cx + t, cy + t * 0.4, cx + t * 0.4, cy + t, cx, cy + r);
        p.curveTo(cx - t * 0.4, cy + t, cx - t, cy + t * 0.4, cx - r, cy);
        p.curveTo(cx - t, cy - t * 0.4, cx - t * 0.4, cy - t, cx, cy - r);
        p.closePath();
        return p;
    }

    private static void schild(Graphics2D g) {
        Path2D s = new Path2D.Double();
        s.moveTo(12, 2.5);
        s.lineTo(20, 5.5);
        s.curveTo(20, 13, 17.5, 18.5, 12, 21.5);
        s.curveTo(6.5, 18.5, 4, 13, 4, 5.5);
        s.closePath();
        g.draw(s);
        Path2D i = new Path2D.Double();
        i.moveTo(12, 6);
        i.lineTo(16.8, 7.8);
        i.curveTo(16.6, 12.6, 15, 16, 12, 18);
        i.closePath();
        g.fill(i);
    }

    private static void sonne(Graphics2D g) {
        g.fill(new Ellipse2D.Double(7.5, 7.5, 9, 9));
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            g.draw(new Line2D.Double(12 + Math.cos(a) * 7, 12 + Math.sin(a) * 7, 12 + Math.cos(a) * 10, 12 + Math.sin(a) * 10));
        }
    }

    private static void kompass(Graphics2D g) {
        g.fill(stern4(12, 12, 10, 2.6));
        AffineTransform alt = g.getTransform();
        g.rotate(Math.PI / 4, 12, 12);
        g.fill(stern4(12, 12, 6.5, 1.4));
        g.setTransform(alt);
    }

    private static void schwerter(Graphics2D g) {
        for (int s = -1; s <= 1; s += 2) {
            AffineTransform alt = g.getTransform();
            g.rotate(s * Math.PI / 4, 12, 12);
            g.fill(pfad(11, 2, 13, 2, 13, 15, 12, 16.5, 11, 15));
            g.fill(new RoundRectangle2D.Double(8, 15.5, 8, 1.8, 1, 1));
            g.fill(new Rectangle2D.Double(11.1, 17, 1.8, 3.5));
            g.fill(new Ellipse2D.Double(10.8, 20.2, 2.4, 2.4));
            g.setTransform(alt);
        }
    }

    private static void person(Graphics2D g) {
        g.fill(new Ellipse2D.Double(7.8, 2.5, 8.4, 8.4));
        Path2D k = new Path2D.Double();
        k.moveTo(3.5, 21.5);
        k.curveTo(3.5, 16, 7.2, 12.8, 12, 12.8);
        k.curveTo(16.8, 12.8, 20.5, 16, 20.5, 21.5);
        k.closePath();
        g.fill(k);
    }

    private static void liste(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(3.5, 3, 17, 18, 3, 3));
        for (int i = 0; i < 3; i++) {
            g.fill(new Ellipse2D.Double(6.4, 7.1 + i * 4.4, 2, 2));
            g.draw(new Line2D.Double(10.5, 8.1 + i * 4.4, 17.5, 8.1 + i * 4.4));
        }
    }

    private static void ap(Graphics2D g) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + Math.PI / 5 * i, r = i % 2 == 0 ? 10 : 4.2;
            double x = 12 + Math.cos(a) * r, y = 12.6 + Math.sin(a) * r;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        g.fill(p);
    }

    private static void info(Graphics2D g) {
        g.draw(new Ellipse2D.Double(3, 3, 18, 18));
        g.fill(new Ellipse2D.Double(10.8, 6.6, 2.4, 2.4));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(12, 11, 12, 17));
    }

    private static void muenzen(Graphics2D g) {
        // Münzstapel: drei flache Scheiben übereinander
        for (int i = 0; i < 3; i++) {
            double y = 15 - i * 4.6;
            g.fill(new Rectangle2D.Double(4, y, 16, 3));
            g.fill(new Ellipse2D.Double(4, y + 1, 16, 4));
            Color alt = g.getColor();
            g.setColor(innen(alt));
            g.draw(new Ellipse2D.Double(4, y - 2, 16, 4));
            g.setColor(alt);
            g.fill(new Ellipse2D.Double(4.8, y - 1.6, 14.4, 3.2));
        }
    }

    private static void grenze(Graphics2D g) {
        g.draw(new Line2D.Double(4, 4.5, 20, 4.5));
        g.fill(pfad(12, 7.5, 18, 14, 14.4, 14, 14.4, 20.5, 9.6, 20.5, 9.6, 14, 6, 14, 12, 7.5));
    }

    private static void rechnen(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(3.5, 3.5, 17, 17, 3, 3));
        g.draw(new Line2D.Double(12, 3.5, 12, 20.5));
        g.draw(new Line2D.Double(3.5, 12, 20.5, 12));
        g.draw(new Line2D.Double(6, 7.8, 9.6, 7.8));
        g.draw(new Line2D.Double(7.8, 6, 7.8, 9.6));
        g.draw(new Line2D.Double(14.4, 7.8, 18, 7.8));
        g.draw(new Line2D.Double(6.4, 14.8, 9.2, 17.6));
        g.draw(new Line2D.Double(9.2, 14.8, 6.4, 17.6));
        g.draw(new Line2D.Double(14.4, 15, 18, 15));
        g.draw(new Line2D.Double(14.4, 17.4, 18, 17.4));
    }

    private static void speichern(Graphics2D g) {
        Path2D d = new Path2D.Double();
        d.moveTo(4, 4);
        d.lineTo(16.5, 4);
        d.lineTo(20, 7.5);
        d.lineTo(20, 20);
        d.lineTo(4, 20);
        d.closePath();
        g.draw(d);
        g.draw(new Rectangle2D.Double(8, 4, 7, 4.5));
        g.fill(new RoundRectangle2D.Double(7.5, 12.5, 9, 5, 1.5, 1.5));
    }

    private static void bogen(Graphics2D g) {
        Path2D b = new Path2D.Double();
        b.moveTo(7, 2.5);
        b.curveTo(16.5, 6, 16.5, 18, 7, 21.5);
        g.draw(b);
        g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(7, 2.5, 7, 21.5));
        g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(3, 12, 19, 12));
        g.fill(pfad(22, 12, 17.5, 9.2, 17.5, 14.8));
        g.draw(new Line2D.Double(3, 12, 1.5, 9.5));
        g.draw(new Line2D.Double(3, 12, 1.5, 14.5));
    }

    private static void minus(Graphics2D g) {
        g.draw(new Line2D.Double(6, 12, 18, 12));
    }
}
