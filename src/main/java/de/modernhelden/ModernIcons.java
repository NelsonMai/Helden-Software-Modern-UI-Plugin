package de.modernhelden;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Eigene, schlichte Linien-Symbole für die modernen Designs. Jedes Symbol wird als Vektorgrafik
 * gezeichnet (scharf bei jeder Bildschirmskalierung) und übernimmt die Farben des aktiven Designs.
 * Die Zeichnungen arbeiten in einem 24×24-Raster und werden auf die Größe des Originals skaliert.
 */
public final class ModernIcons {

    private ModernIcons() {}

    /** Zeichnet ein Symbol im 24er-Raster. fg = Linienfarbe, accent = Akzent, sem = Bedeutungsfarbe. */
    interface Zeichnung { void paint(Graphics2D g, Color fg, Color accent); }

    private static final Map<String, Zeichnung> SYMBOLE = new HashMap<>();
    private static final Map<String, Color> FARBE = new HashMap<>();

    // Bedeutungsfarben (in beiden Designs gut sichtbar)
    static final Color GRUEN = new Color(0x3f9a5a), ROT = new Color(0xd0473f), GELB = new Color(0xd99a1e),
            BLAU = new Color(0x3d7cc9), LILA = new Color(0x8a63c4), GRAU = new Color(0x8c8c8c);

    /** Ermittelt aus Bildpfad bzw. Beschreibung des Originals den Symbolnamen; null = unbekannt. */
    public static String schluessel(String beschreibung) {
        if (beschreibung == null) return null;
        String d = beschreibung.replace('\\', '/');
        int slash = d.lastIndexOf('/');
        String n = slash >= 0 ? d.substring(slash + 1) : d;
        n = n.replaceAll("(?i)\\.(gif|png|jpg)$", "").replace("2X", "");
        if (n.startsWith("helden_icon_")) n = n.substring(12).replaceAll("\\d+$", "");
        String k = n.toLowerCase(Locale.ROOT);
        // Beschreibungen der Markierungs-Symbole (IconFactory)
        switch (k) {
            case "basistalent": return "basis";
            case "breitgefächerte bildung": return "bgb";
            case "leittalent/leitzauber": return "leittalent";
            case "hilfe": return "question";
            case "config": return "guiuse";
            default: break;
        }
        return SYMBOLE.containsKey(k) ? k : null;
    }

    public static boolean bekannt(String key) { return key != null && SYMBOLE.containsKey(key); }

    /** Symbol in der Größe des Originals; zeichnet bei jedem Aufruf in den aktuellen Designfarben. */
    public static ImageIcon icon(String key, int w, int h) { return new VektorIcon(key, w, h); }

    /** ImageIcon-Unterklasse, damit Code der Helden-Software, der ImageIcon erwartet, weiter funktioniert. */
    static final class VektorIcon extends ImageIcon {
        final String key;
        final int w, h;
        private Image cache;
        private Color cacheFg;

        VektorIcon(String key, int w, int h) {
            this.key = key;
            this.w = w;
            this.h = h;
            setDescription("modernhelden:" + key);
        }

        @Override public int getIconWidth() { return w; }
        @Override public int getIconHeight() { return h; }

        @Override public synchronized void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.translate(x, y);
                zeichne(g, key, w, h, c != null && !c.isEnabled());
            } finally {
                g.dispose();
            }
        }

        /** Für Code, der das Bild direkt nutzt (z. B. graue „deaktiviert“-Variante). */
        @Override public synchronized Image getImage() {
            Color fg = vordergrund();
            if (cache == null || !fg.equals(cacheFg)) {
                int s = 2;   // doppelte Auflösung für saubere Kanten
                BufferedImage img = new BufferedImage(Math.max(1, w * s), Math.max(1, h * s), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.scale(s, s);
                zeichne(g, key, w, h, false);
                g.dispose();
                cache = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                cacheFg = fg;
            }
            return cache;
        }
    }

    static Color vordergrund() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : Color.DARK_GRAY;
    }

    static void zeichne(Graphics2D g, String key, int w, int h, boolean aus) {
        Zeichnung z = SYMBOLE.get(key);
        if (z == null) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float s = Math.min(w, h) / 24f;
        g.translate((w - 24 * s) / 2, (h - 24 * s) / 2);
        g.scale(s, s);
        Color fg = vordergrund();
        Color accent = Theme.accent();
        if (aus) {
            Color dis = UIManager.getColor("Label.disabledForeground");
            fg = dis != null ? dis : Color.GRAY;
            accent = fg;
        }
        // kleine Symbole etwas kräftiger zeichnen, damit sie nicht verschwinden
        float strich = s < 0.8f ? 2.2f : 1.8f;
        g.setStroke(new BasicStroke(strich, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        z.paint(g, fg, accent);
    }

    // ------------------------------------------------------------------ Hilfen

    private static void def(String key, Zeichnung z) { SYMBOLE.put(key, z); }

    private static RoundRectangle2D rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r, r);
    }

    private static void linie(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private static Path2D pfad(double... p) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i + 1 < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        return path;
    }

    /** Gefüllter Kreis mit Symbol in Weiß – für Status- und Markierungssymbole. */
    private static void plakette(Graphics2D g, Color farbe, Zeichnung innen) {
        g.setColor(farbe);
        g.fill(new Ellipse2D.Double(2, 2, 20, 20));
        Stroke alt = g.getStroke();
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        innen.paint(g, Color.WHITE, Color.WHITE);
        g.setStroke(alt);
    }

    private static void buchstabe(Graphics2D g, String b, float size) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size)));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(b, 12 - fm.stringWidth(b) / 2f, 12 + fm.getAscent() / 2f - fm.getDescent() / 2f - 0.5f);
    }

    private static void warnDreieck(Graphics2D g, Color farbe) {
        Path2D t = pfad(12, 3, 22, 20.5, 2, 20.5);
        t.closePath();
        g.setColor(farbe);
        g.fill(t);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        linie(g, 12, 9, 12, 14);
        g.fill(new Ellipse2D.Double(10.8, 16.3, 2.4, 2.4));
    }

    static {
        // ---------------- Werkzeugleiste
        def("new", (g, fg, a) -> {            // neues Dokument mit Plus
            g.setColor(fg);
            g.draw(pfad(14, 3, 6, 3, 6, 21, 18, 21, 18, 7, 14, 3, 14, 7, 18, 7));
            g.setColor(a);
            linie(g, 12, 11, 12, 17);
            linie(g, 9, 14, 15, 14);
        });
        def("open", (g, fg, a) -> {           // Ordner
            g.setColor(fg);
            Path2D p = pfad(3, 19, 3, 6, 9, 6, 11, 8, 19, 8, 19, 10);
            g.draw(p);
            g.setColor(a);
            Path2D f = pfad(3, 19, 6, 11, 22, 11, 19, 19);
            f.closePath();
            g.draw(f);
        });
        def("save", (g, fg, a) -> {           // Diskette
            g.setColor(fg);
            g.draw(pfad(5, 3, 17, 3, 21, 7, 21, 21, 3, 21, 3, 5));
            g.draw(new Arc2D.Double(3, 3, 4, 4, 90, 90, Arc2D.OPEN));
            g.draw(rr(7, 3, 9, 6, 1));
            g.setColor(a);
            g.draw(rr(7, 13, 10, 8, 1.5));
        });
        def("print", (g, fg, a) -> {
            g.setColor(fg);
            g.draw(pfad(7, 9, 7, 3, 17, 3, 17, 9));
            g.draw(rr(3, 9, 18, 9, 3));
            g.setColor(a);
            g.draw(rr(7, 14, 10, 7, 1));
            g.setColor(fg);
            g.fill(new Ellipse2D.Double(16.2, 11.2, 1.8, 1.8));
        });
        def("delete", (g, fg, a) -> {         // X in Rot
            g.setColor(ROT);
            linie(g, 6, 6, 18, 18);
            linie(g, 18, 6, 6, 18);
        });
        def("minidelete", SYMBOLE.get("delete"));
        def("door", (g, fg, a) -> {           // Beenden: Tür mit Pfeil
            g.setColor(fg);
            g.draw(pfad(14, 7, 14, 3, 4, 3, 4, 21, 14, 21, 14, 17));
            g.setColor(a);
            linie(g, 9, 12, 21, 12);
            g.draw(pfad(17, 8, 21, 12, 17, 16));
        });
        def("question", (g, fg, a) -> {       // Hilfe
            g.setColor(a);
            g.draw(new Ellipse2D.Double(2.5, 2.5, 19, 19));
            g.setColor(fg);
            Path2D q = new Path2D.Double();
            q.moveTo(9, 9.5);
            q.curveTo(9, 5.5, 15.5, 5.5, 15, 9.5);
            q.curveTo(14.7, 12, 12, 11.8, 12, 14.2);
            g.draw(q);
            g.fill(new Ellipse2D.Double(10.9, 16.4, 2.2, 2.2));
        });
        def("question-small", SYMBOLE.get("question"));
        def("guihelp", SYMBOLE.get("question"));
        def("about", (g, fg, a) -> {          // Rückmeldung / Über: Sprechblase
            g.setColor(fg);
            Path2D b = new Path2D.Double();
            b.append(rr(3, 4, 18, 13, 4), false);
            g.draw(b);
            g.draw(pfad(8, 17, 7, 21, 12, 17));
            g.setColor(a);
            g.fill(new Ellipse2D.Double(7.5, 9.5, 2, 2));
            g.fill(new Ellipse2D.Double(11, 9.5, 2, 2));
            g.fill(new Ellipse2D.Double(14.5, 9.5, 2, 2));
        });
        def("duplicate", (g, fg, a) -> {      // Held kopieren: zwei Blätter
            g.setColor(fg);
            g.draw(rr(8, 8, 13, 13, 3));
            g.setColor(a);
            g.draw(pfad(16, 8, 16, 5, 13, 3, 6, 3, 3, 6, 3, 13, 5, 16, 8, 16));
        });
        def("export", (g, fg, a) -> {         // Export: Kasten mit Pfeil nach oben
            g.setColor(fg);
            g.draw(pfad(4, 13, 4, 20, 20, 20, 20, 13));
            g.setColor(a);
            linie(g, 12, 3, 12, 15);
            g.draw(pfad(7.5, 7.5, 12, 3, 16.5, 7.5));
        });
        def("refresh", (g, fg, a) -> {
            g.setColor(fg);
            g.draw(new Arc2D.Double(4, 4, 16, 16, 30, 270, Arc2D.OPEN));
            g.setColor(a);
            g.draw(pfad(20, 3.5, 20, 9, 14.5, 9));
        });
        def("rotcwright", SYMBOLE.get("refresh"));
        def("zoom", (g, fg, a) -> {
            g.setColor(fg);
            g.draw(new Ellipse2D.Double(3.5, 3.5, 12, 12));
            g.setColor(a);
            linie(g, 14, 14, 20.5, 20.5);
        });
        def("world", (g, fg, a) -> {
            g.setColor(fg);
            g.draw(new Ellipse2D.Double(3, 3, 18, 18));
            g.setColor(a);
            g.draw(new Ellipse2D.Double(8, 3, 8, 18));
            linie(g, 3.5, 12, 20.5, 12);
        });
        def("draw", (g, fg, a) -> {           // Stift
            g.setColor(fg);
            g.draw(pfad(4, 20, 5, 15, 16, 4, 20, 8, 9, 19, 4, 20));
            g.setColor(a);
            linie(g, 13.5, 6.5, 17.5, 10.5);
        });
        def("editbcard", (g, fg, a) -> {      // Karte bearbeiten
            g.setColor(fg);
            g.draw(rr(3, 5, 18, 14, 3));
            linie(g, 7, 10, 13, 10);
            linie(g, 7, 14, 11, 14);
            g.setColor(a);
            g.draw(pfad(14, 17, 14.5, 14.5, 19.5, 9.5, 21.5, 11.5, 16.5, 16.5, 14, 17));
        });
        def("fingerright", (g, fg, a) -> {    // Hinweis-Pfeil
            g.setColor(a);
            linie(g, 4, 12, 19, 12);
            g.draw(pfad(14, 7, 19, 12, 14, 17));
        });
        def("user", (g, fg, a) -> {
            g.setColor(fg);
            g.draw(new Ellipse2D.Double(8, 3.5, 8, 8));
            g.draw(new Arc2D.Double(4.5, 13, 15, 14, 0, 180, Arc2D.OPEN));
        });
        def("usersplus", (g, fg, a) -> {
            SYMBOLE.get("user").paint(g, fg, a);
            g.setColor(GRUEN);
            linie(g, 19, 3, 19, 9);
            linie(g, 16, 6, 22, 6);
        });
        def("usersminus", (g, fg, a) -> {
            SYMBOLE.get("user").paint(g, fg, a);
            g.setColor(ROT);
            linie(g, 16, 6, 22, 6);
        });
        def("chain", (g, fg, a) -> {          // verknüpft
            g.setColor(fg);
            g.draw(rr(2.5, 8.5, 11, 7, 7));
            g.setColor(a);
            g.draw(rr(10.5, 8.5, 11, 7, 7));
        });
        def("openchain", (g, fg, a) -> {      // getrennt
            g.setColor(fg);
            g.draw(rr(2, 8.5, 9, 7, 7));
            g.setColor(a);
            g.draw(rr(13, 8.5, 9, 7, 7));
            g.setColor(fg);
            linie(g, 12, 4, 12, 6.5);
            linie(g, 12, 17.5, 12, 20);
        });
        def("up", (g, fg, a) -> { g.setColor(a); linie(g, 12, 20, 12, 5); g.draw(pfad(6, 11, 12, 5, 18, 11)); });
        def("down", (g, fg, a) -> { g.setColor(a); linie(g, 12, 4, 12, 19); g.draw(pfad(6, 13, 12, 19, 18, 13)); });
        def("vcrback", (g, fg, a) -> { g.setColor(fg); g.draw(pfad(15, 5, 8, 12, 15, 19)); });
        def("vcrforward", (g, fg, a) -> { g.setColor(fg); g.draw(pfad(9, 5, 16, 12, 9, 19)); });
        def("vcrstop", (g, fg, a) -> { g.setColor(a); g.fill(rr(6, 6, 12, 12, 3)); });
        def("check", (g, fg, a) -> { g.setColor(GRUEN); g.draw(pfad(4.5, 12.5, 9.5, 17.5, 19.5, 6.5)); });
        def("automatisch", (g, fg, a) -> {    // Zahnrad mit „A“ – automatisch
            zahnrad(g, fg);
            g.setColor(a);
            g.fill(new Ellipse2D.Double(9.5, 9.5, 5, 5));
        });
        def("guiuse", (g, fg, a) -> {         // Einstellungen
            zahnrad(g, fg);
            g.setColor(a);
            g.draw(new Ellipse2D.Double(9, 9, 6, 6));
        });
        def("passiv", (g, fg, a) -> {         // ruhend: Mond
            g.setColor(fg);
            Area mond = new Area(new Ellipse2D.Double(4, 4, 16, 16));
            mond.subtract(new Area(new Ellipse2D.Double(9, 1, 16, 16)));
            g.draw(mond);
        });

        // ---------------- Meldungen
        def("caution", (g, fg, a) -> warnDreieck(g, GELB));
        def("warn", SYMBOLE.get("caution"));
        def("error", (g, fg, a) -> plakette(g, ROT, (gg, f2, a2) -> { linie(gg, 8.5, 8.5, 15.5, 15.5); linie(gg, 15.5, 8.5, 8.5, 15.5); }));
        def("guierror", SYMBOLE.get("error"));
        def("inform", (g, fg, a) -> plakette(g, BLAU, (gg, f2, a2) -> {
            linie(gg, 12, 11, 12, 17);
            gg.fill(new Ellipse2D.Double(10.8, 6.3, 2.4, 2.4));
        }));

        // ---------------- Markierungen in Talent-/Zauberlisten (IconFactory)
        def("basis", (g, fg, a) -> plakette(g, new Color(0x8a6a3b), (gg, f2, a2) -> buchstabe(gg, "B", 12)));
        def("bgb", (g, fg, a) -> plakette(g, new Color(0x2f8a6b), (gg, f2, a2) -> buchstabe(gg, "G", 12)));
        def("empfohlen", (g, fg, a) -> plakette(g, GRUEN, (gg, f2, a2) -> gg.draw(pfad(7.5, 12.5, 10.5, 15.5, 16.5, 9))));
        def("hauszauber", (g, fg, a) -> plakette(g, LILA, (gg, f2, a2) -> {
            Path2D h = pfad(7, 12, 12, 7.5, 17, 12, 15.5, 12, 15.5, 16.5, 8.5, 16.5, 8.5, 12);
            h.closePath();
            gg.fill(h);
        }));
        def("leittalent", (g, fg, a) -> plakette(g, BLAU, (gg, f2, a2) -> buchstabe(gg, "L", 12)));
        def("ruhend", (g, fg, a) -> plakette(g, GRAU, (gg, f2, a2) -> {
            linie(gg, 10, 8.5, 10, 15.5);
            linie(gg, 14, 8.5, 14, 15.5);
        }));
        def("ungeeignet", (g, fg, a) -> plakette(g, ROT, (gg, f2, a2) -> linie(gg, 7.5, 12, 16.5, 12)));
        def("verbilligt", (g, fg, a) -> plakette(g, GELB, (gg, f2, a2) -> buchstabe(gg, "%", 11)));
        def("veteran", (g, fg, a) -> plakette(g, new Color(0x2f6f9a), (gg, f2, a2) -> buchstabe(gg, "V", 12)));
        def("warnung", (g, fg, a) -> plakette(g, new Color(0xe0632a), (gg, f2, a2) -> {
            linie(gg, 12, 7, 12, 13);
            gg.fill(new Ellipse2D.Double(10.8, 15.3, 2.4, 2.4));
        }));
    }

    private static void zahnrad(Graphics2D g, Color fg) {
        g.setColor(fg);
        Area rad = new Area(new Ellipse2D.Double(5, 5, 14, 14));
        for (int i = 0; i < 8; i++) {
            AffineTransform t = AffineTransform.getRotateInstance(Math.PI / 4 * i, 12, 12);
            rad.add(new Area(t.createTransformedShape(new Rectangle2D.Double(10.5, 2.5, 3, 4))));
        }
        g.draw(rad);
    }
}
