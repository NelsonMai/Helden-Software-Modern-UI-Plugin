package de.modernhelden.print;

import de.modernhelden.Geld;

import de.modernhelden.TooltipDB;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Moderner Heldenbogen (A4 hoch). Berechnet das Seitenlayout einmal und kann
 * danach beliebig oft gezeichnet, gedruckt oder als PDF exportiert werden.
 */
public final class ModernSheet implements Pageable, Printable {

    public static final float W = 595.28f, H = 841.89f, M = 34f;
    private static final float BOTTOM = H - M - 16f;

    static final Color INK = new Color(0x1e1e1e), MUTED = new Color(0x777777), LINE = new Color(0xdedede),
            FILL = new Color(0xf6f2ee), ACCENT = new Color(0xa4552a), ACCENT_SOFT = new Color(0xf4e6dc), WHITE = Color.WHITE;

    public static final class Options {
        /** mitgelieferte Kurzbeschreibungen zu SF und Vor-/Nachteilen (für Zauber gibt es keine) */
        public boolean beschreibungen = true;
        /** eigene Notizen; wo noch keine steht, eine leere Linie zum Schreiben */
        public boolean eigeneNotizen = true;
        public boolean metatalente = true;
        public boolean notizseite = true;
        /** Portrait-Feld neben der Charakterbeschreibung (mit Bild oder leer zum Zeichnen/Einkleben) */
        public boolean portrait = true;
        public java.awt.image.BufferedImage portraitBild;
        /** Text für das Feld „Charakterbeschreibung“ (aus einer Textdatei); leer = Linien zum Schreiben */
        public String charakterText;
        /** Seitenverhältnis Breite/Höhe des Portrait-Felds; 0 = wie das Bild (ohne Bild 3:4) */
        public double portraitVerhaeltnis = 0;
    }

    /** Breite des Portrait-Felds zur Höhe: gewähltes Format, sonst das des Bildes (begrenzt auf sinnvolle Werte). */
    private double portraitVerhaeltnis() {
        double v = opt.portraitVerhaeltnis;
        if (v <= 0 && opt.portraitBild != null && opt.portraitBild.getHeight() > 0)
            v = (double) opt.portraitBild.getWidth() / opt.portraitBild.getHeight();
        if (v <= 0) v = 0.75;
        return Math.max(0.5, Math.min(16.0 / 9.0, v));
    }

    private interface Painter { void paint(Graphics2D g, float x, float y, float w, float h); }

    /** Zeile einer Kennzeichen-Legende (eigene Klasse, damit sie beim Umbruch erkennbar bleibt). */
    private static final class LegendenZeile implements Painter {
        final Painter p; final String art;
        LegendenZeile(String art, Painter p) { this.art = art; this.p = p; }
        public void paint(Graphics2D g, float x, float y, float w, float h) { p.paint(g, x, y, w, h); }
    }

    private static final class Placed {
        final Painter p; final float x, y, w, h;
        Placed(Painter p, float x, float y, float w, float h) { this.p = p; this.x = x; this.y = y; this.w = w; this.h = h; }
    }

    private final SheetData d;
    private final TooltipDB db;
    private final Options opt;
    private final List<List<Placed>> pages = new ArrayList<>();
    private final String datum = new SimpleDateFormat("dd.MM.yyyy").format(new Date());
    private final Graphics2D mg;   // nur zum Messen
    private float y;

    public ModernSheet(SheetData d, TooltipDB db, Options opt) {
        this.d = d;
        this.db = db;
        this.opt = opt;
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        mg = img.createGraphics();
        hints(mg);
        newPage();
        build();
        mg.dispose();
    }

    public int pageCount() { return pages.size(); }

    // ================================================================== Inhalt

    private void build() {
        header();
        steckbrief();
        space(10);
        eigenschaften();
        space(8);
        basiswerte();
        space(12);
        energien();
        kampf();
        sonderfertigkeiten();
        vorteile();
        space(8);
        talente();
        inventar();
        if (opt.notizseite) notizen();
        // Magie zuletzt: sie beginnt auf eigener Seite – vorher würde sie hinter den Talenten Platz verschenken
        magieSeite();
        // keine leere Seite am Ende
        if (pages.size() > 1 && pages.get(pages.size() - 1).isEmpty()) pages.remove(pages.size() - 1);
    }

    private void header() {
        final String sub = join(" · ", d.rasse, d.kultur, d.profession);
        // Rasse · Kultur · Profession wird umbrochen statt abgeschnitten
        final List<String> subLines = wrap(sub, F.reg(9.5f), W - 2 * M - 120);
        if (subLines.isEmpty()) subLines.add("");
        final List<String> nameLines = wrap(d.name, F.bold(21), W - 2 * M - 120);
        if (nameLines.isEmpty()) nameLines.add(d.name);
        final float nameExtra = (nameLines.size() - 1) * 24;
        atomic(56 + nameExtra + (subLines.size() - 1) * 12, (g, x, yy, w, h) -> {
            g.setColor(INK);
            g.setFont(F.bold(21));
            float ny = yy + 22;
            if (d.name.trim().isEmpty()) {
                // kein Name hinterlegt: Linie zum Eintragen
                g.setFont(F.bold(6.5f));
                g.setColor(MUTED);
                g.drawString("NAME", x, yy + 8);
                g.setColor(LINE.darker());
                g.draw(new Line2D.Float(x, yy + 24, x + w - 140, yy + 24));
                g.setFont(F.bold(21));
                g.setColor(INK);
            } else {
                for (String l : nameLines) { g.drawString(l, x, ny); ny += 24; }
            }
            g.setFont(F.reg(9.5f));
            g.setColor(MUTED);
            float sy = yy + 38 + nameExtra;
            for (String l : subLines) { g.drawString(l, x, sy); sy += 12; }
            g.setFont(F.bold(15));
            g.setColor(INK);
            right(g, d.apGesamt + " AP", x + w, yy + 22);
            g.setFont(F.reg(9));
            g.setColor(d.apFrei > 0 ? ACCENT : MUTED);
            right(g, d.apFrei + " AP frei", x + w, yy + 37);
            g.setColor(ACCENT);
            g.fill(new Rectangle2D.Float(x, yy + h - 5, w, 2f));
        });
    }

    private void steckbrief() {
        final String[][] f = {
                {"Geschlecht", d.geschlecht}, {"Alter", d.alter.isEmpty() ? "" : d.alter + " Jahre"},
                {"Geburtstag", d.geburtstag}, {"Größe", d.groesse.isEmpty() ? "" : d.groesse + " cm"},
                {"Gewicht", d.gewicht.isEmpty() ? "" : d.gewicht + " Stein (" + d.gewicht + " kg)"},
                {"Haarfarbe", d.haarfarbe}, {"Augenfarbe", d.augenfarbe}, {"Stand", d.stand}, {"Titel", d.titel},
                {"Sozialstatus", String.valueOf(d.akt("sozialstatus"))}};
        // Werte werden umbrochen statt abgeschnitten; jede Reihe so hoch wie ihr längster Eintrag
        final float cwAll = (W - 2 * M) / 5f;
        final List<List<String>> lines = new ArrayList<>();
        for (String[] e : f) {
            List<String> l = wrap(e[1].isEmpty() ? "–" : e[1], F.reg(9.5f), cwAll - 6);
            if (l.isEmpty()) l.add("–");
            lines.add(l);
        }
        final float[] rowH = new float[(f.length + 4) / 5];
        for (int i = 0; i < f.length; i++) rowH[i / 5] = Math.max(rowH[i / 5], 22 + (lines.get(i).size() - 1) * 11.5f);
        float total = 4;
        for (float rh : rowH) total += rh;
        atomic(total, (g, x, yy, w, h) -> {
            float cw = w / 5f, cy = yy + 6;
            for (int row = 0; row < rowH.length; row++) {
                for (int i = row * 5; i < Math.min(f.length, row * 5 + 5); i++) {
                    float cx = x + (i % 5) * cw;
                    g.setFont(F.bold(6.5f));
                    g.setColor(MUTED);
                    g.drawString(f[i][0].toUpperCase(Locale.GERMAN), cx, cy + 6);
                    g.setFont(F.reg(9.5f));
                    g.setColor(INK);
                    float ly = cy + 17;
                    for (String l : lines.get(i)) { g.drawString(l, cx, ly); ly += 11.5f; }
                }
                cy += rowH[row];
            }
        });
    }

    private void eigenschaften() {
        final String[][] e = {{"mut", "MU", "Mut"}, {"klugheit", "KL", "Klugheit"}, {"intuition", "IN", "Intuition"},
                {"charisma", "CH", "Charisma"}, {"fingerfertigkeit", "FF", "Fingerfertigk."}, {"gewandtheit", "GE", "Gewandtheit"},
                {"konstitution", "KO", "Konstitution"}, {"koerperkraft", "KK", "Körperkraft"}};
        atomic(52, (g, x, yy, w, h) -> {
            float gap = 6, tw = (w - gap * (e.length - 1)) / e.length;
            for (int i = 0; i < e.length; i++) {
                SheetData.Wert v = d.wert(e[i][0]);
                float tx = x + i * (tw + gap);
                tile(g, tx, yy, tw, h, e[i][1], v == null ? "–" : String.valueOf(v.akt),
                        e[i][2], true);
            }
        });
    }

    private void basiswerte() {
        final List<String[]> b = new ArrayList<>();
        add(b, "LE", "lebensenergie", "Lebensenergie");
        add(b, "AU", "ausdauer", "Ausdauer");
        if (d.magisch || d.akt("astralenergie") > 0) add(b, "AE", "astralenergie", "Astralenergie");
        if (d.geweiht || d.akt("karmaenergie") > 0) add(b, "KE", "karmaenergie", "Karmaenergie");
        add(b, "MR", "magieresistenz", "Magieresistenz");
        add(b, "INI", "initiative", "Initiative-Basis");
        add(b, "AT", "attacke", "Attacke-Basis");
        add(b, "PA", "parade", "Parade-Basis");
        add(b, "FK", "fernkampf-basis", "Fernkampf");
        add(b, "GS", "geschwindigkeit", "Geschwindigk.");
        if (!d.ausweichen.isEmpty()) b.add(new String[]{"AW", d.ausweichen, "Ausweichen"});
        if (!d.wundschwelle.isEmpty()) b.add(new String[]{"WS", d.wundschwelle, "Wundschwelle"});
        if (b.size() > 11) {
            // viele Kacheln (z. B. mit AE und KE): kürzere Beschriftungen, damit nichts abgeschnitten wird
            Map<String, String> kurz = new HashMap<>();
            kurz.put("Lebensenergie", "Leben"); kurz.put("Astralenergie", "Astral"); kurz.put("Karmaenergie", "Karma");
            kurz.put("Magieresistenz", "Magieres."); kurz.put("Initiative-Basis", "INI-Basis"); kurz.put("Attacke-Basis", "AT-Basis");
            kurz.put("Parade-Basis", "PA-Basis"); kurz.put("Fernkampf", "FK-Basis"); kurz.put("Geschwindigk.", "Geschw.");
            kurz.put("Wundschwelle", "Wundschw."); kurz.put("Ausweichen", "Ausweich.");
            for (String[] t : b) if (kurz.containsKey(t[2])) t[2] = kurz.get(t[2]);
        }
        atomic(44, (g, x, yy, w, h) -> {
            float gap = 5, tw = (w - gap * (b.size() - 1)) / b.size();
            for (int i = 0; i < b.size(); i++)
                tile(g, x + i * (tw + gap), yy, tw, h, b.get(i)[0], b.get(i)[1], b.get(i)[2], false);
        });
    }

    private void add(List<String[]> l, String k, String key, String name) {
        SheetData.Wert w = d.wert(key);
        if (w != null) l.add(new String[]{k, String.valueOf(w.akt), name});
    }

    private void energien() {
        List<String[]> rows = new ArrayList<>();
        energieRow(rows, "Lebensenergie", "lebensenergie");
        energieRow(rows, "Ausdauer", "ausdauer");
        // Astral- und Karmaenergie stehen auf der eigenen Magie-Seite
        heading("Zustand", 40);
        if (opt.notizseite) {
            table(new String[]{"", "Max", "½", "⅓", "¼", "Aktuell", "Notizen"},
                    new float[]{1.5f, .6f, .5f, .5f, .5f, 1.4f, 3.2f},
                    new int[]{0, 1, 1, 1, 1, 1, 0}, rows, true);
        } else {
            // ohne Notizfelder: die Spalte „Aktuell“ nimmt den Platz der Notizen ein
            List<String[]> ohne = new ArrayList<>();
            for (String[] r : rows) ohne.add(Arrays.copyOf(r, 6));
            table(new String[]{"", "Max", "½", "⅓", "¼", "Aktuell"},
                    new float[]{1.5f, .6f, .5f, .5f, .5f, 4.6f},
                    new int[]{0, 1, 1, 1, 1, 1}, ohne, true);
        }
        final String reg = join("   ·   ",
                d.wundschwelle.isEmpty() ? "" : "Wundschwelle " + d.wundschwelle,
                d.leReg.isEmpty() ? "" : "LE-Regeneration " + d.leReg,
                "");
        // Wunden werden immer je Trefferzone im Kampfteil geführt – auch ohne Rüstung
        atomic(22, (g, x, yy, w, h) -> {
            g.setFont(F.reg(8));
            g.setColor(MUTED);
            g.drawString(reg + (reg.isEmpty() ? "" : "   ·   ") + "Wunden je Trefferzone siehe Kampf", x, yy + 14);
        });
        space(6);
    }

    /**
     * Energie-Anzeige, bei der nie radiert werden muss:
     * – Zahlenleiste max … 0 für einen Marker (Münze, Spielstein, Büroklammer) auf dem aktuellen Wert,
     * – Verlaufsprotokoll, in das nur neue Zeilen geschrieben werden; der unterste Eintrag ist der aktuelle Stand.
     */
    private void energiePool(final String titel, final String kurz, final int max, final String reg, final String anlass) {
        if (max <= 0) return;
        final float pad = 10, titleH = 22;
        final float cell = 15f;                          // Feldhöhe
        final float innerW = W - 2 * M - 2 * pad;
        // höchstens 25 Felder je Reihe, gleichmäßig auf die nötigen Reihen verteilt;
        // jede Reihe füllt die ganze Breite (keine halb leeren Reihen)
        final int maxProReihe = 25;
        final int cells = max + 1;
        final int trackRows = (cells + maxProReihe - 1) / maxProReihe;
        final int basis = cells / trackRows, extra = cells % trackRows;   // die ersten „extra“ Reihen haben ein Feld mehr
        final float trackH = trackRows * (cell + 2.5f);
        final List<String> hint = wrap("Kein Radieren nötig: Lege eine Münze, einen Spielstein oder eine Büroklammer auf den "
                + "aktuellen Wert und verschiebe sie. Jede Änderung zusätzlich unten als neue Zeile eintragen – "
                + "der unterste Eintrag ist der aktuelle Stand, ältere Zeilen bleiben einfach stehen.", F.reg(6.8f), innerW);
        final float hintH = hint.size() * 8.8f + 4;
        final int logRows = 9;                                   // je Spalte, zwei Spalten
        final float logRowH = 14, logH = 13 + logRows * logRowH;
        final float h = titleH + 2 + trackH + 4 + hintH + logH + pad;
        space(2);
        atomic(h, (g, x, yy, w, hh) -> {
            g.setColor(FILL);
            g.fill(new RoundRectangle2D.Float(x, yy, w, hh, 8, 8));
            g.setColor(ACCENT);
            g.fill(new Rectangle2D.Float(x + pad, yy + 8, 2.5f, 10));
            g.setFont(F.bold(10));
            g.setColor(INK);
            g.drawString(titel, x + pad + 7, yy + 17);
            float tx = x + pad + 7 + g.getFontMetrics().stringWidth(titel) + 10;
            g.setFont(F.reg(7.8f));
            g.setColor(MUTED);
            g.drawString("max " + max + " " + kurz + (reg.isEmpty() ? "" : "   ·   " + reg), tx, yy + 17);

            // Zahlenleiste: Maximum oben links, dann absteigend zeilenweise bis 0; Fünfer-Werte hervorgehoben
            float lx = x + pad, ly = yy + titleH + 2;
            final float fgap = 2.5f;
            int v = max;
            for (int row = 0; row < trackRows; row++) {
                int n = basis + (row < extra ? 1 : 0);
                // Feldbreite: Reihe füllt die Breite – bei sehr kleinen Pools (unter 8 Feldern) aber nicht breiter als 1/8
                int teiler = Math.max(n, 8);
                float fw = (w - 2 * pad - fgap * (teiler - 1)) / teiler;
                float cy = ly + row * (cell + 2.5f);
                for (int col = 0; col < n; col++, v--) {
                    float cx = lx + col * (fw + fgap);
                    boolean fuenf = v % 5 == 0;
                    g.setColor(WHITE);
                    g.fill(new RoundRectangle2D.Float(cx, cy, fw, cell - 1.5f, 4, 4));
                    g.setColor(fuenf ? ACCENT : LINE.darker());
                    g.draw(new RoundRectangle2D.Float(cx, cy, fw, cell - 1.5f, 4, 4));
                    g.setFont(fuenf ? F.bold(7f) : F.reg(6.6f));
                    g.setColor(fuenf ? ACCENT : MUTED);
                    center(g, String.valueOf(v), cx, fw, cy + (cell - 1.5f) / 2 + 2.4f);
                }
            }
            g.setFont(F.reg(6.8f));
            g.setColor(MUTED);
            float hy = ly + trackH + 9;
            for (String l : hint) { g.drawString(l, lx, hy); hy += 8.8f; }

            // Verlaufsprotokoll in zwei Spalten
            float py = ly + trackH + 4 + hintH;   // ly enthält bereits den Abstand unter dem Titel
            float gap = 10, pw = (w - 2 * pad - gap) / 2;
            for (int k = 0; k < 2; k++) {
                float px = x + pad + k * (pw + gap);
                float c1 = pw * 0.58f, c2 = pw * 0.19f;
                g.setColor(WHITE);
                g.fill(new RoundRectangle2D.Float(px, py, pw, logH, 6, 6));
                g.setColor(LINE);
                g.draw(new RoundRectangle2D.Float(px, py, pw, logH, 6, 6));
                g.setFont(F.bold(6.2f));
                g.setColor(ACCENT.darker());
                g.drawString(anlass.toUpperCase(Locale.GERMAN), px + 5, py + 9);
                center(g, "− / + " + kurz, px + c1, c2, py + 9);
                center(g, kurz.toUpperCase(Locale.GERMAN) + " DANACH", px + c1 + c2, pw - c1 - c2, py + 9);
                g.setColor(LINE);
                g.draw(new Line2D.Float(px + 3, py + 13, px + pw - 3, py + 13));
                for (int i = 1; i < logRows; i++) {
                    float ry = py + 13 + i * logRowH;
                    g.draw(new Line2D.Float(px + 3, ry, px + pw - 3, ry));
                }
                g.draw(new Line2D.Float(px + c1, py + 3, px + c1, py + logH - 3));
                g.draw(new Line2D.Float(px + c1 + c2, py + 3, px + c1 + c2, py + logH - 3));
            }
        });
    }

    private void energieRow(List<String[]> rows, String name, String key) {
        SheetData.Wert w = d.wert(key);
        if (w == null) return;
        rows.add(new String[]{name, String.valueOf(w.akt), w.has50 ? String.valueOf(w.akt50) : "",
                w.has50 ? String.valueOf(w.akt33) : "", w.has50 ? String.valueOf(w.akt25) : "", "", ""});
    }

    private void kampf() {
        heading("Kampf", 60);
        if (!d.nahkampf.isEmpty()) {
            List<String[]> r = new ArrayList<>();
            List<List<Kennung>> kz = new ArrayList<>();
            Set<Plakette> benutzt = EnumSet.noneOf(Plakette.class);
            for (SheetData.Nahkampfwaffe n : d.nahkampf) {
                r.add(new String[]{n.name, n.talent, n.dk, n.tp, n.tpkk, n.ini, n.wm, n.at, n.pa, n.bf});
                List<Kennung> k = new ArrayList<>();
                if (n.ausdauer) { k.add(new Kennung(Plakette.AUSDAUER, "A")); benutzt.add(Plakette.AUSDAUER); }
                if (!n.schild.isEmpty()) { k.add(new Kennung(Plakette.SCHILD, "S" + n.schild)); benutzt.add(Plakette.SCHILD); }
                kz.add(k);
            }
            subheading("Nahkampfwaffen");
            table(new String[]{"Waffe", "Talent", "DK", "TP", "TP/KK", "INI", "WM", "AT", "PA", "BF"},
                    new float[]{2.4f, 2f, .6f, .9f, .8f, .5f, .8f, .6f, .6f, .5f},
                    new int[]{0, 0, 1, 1, 1, 1, 1, 2, 2, 1}, r, false, null, kz, legendeHoehe(benutzt));
            legende("Nahkampf", benutzt);
            if (!benutzt.isEmpty()) space(3);
        }
        if (!d.fernkampf.isEmpty()) {
            List<String[]> r = new ArrayList<>();
            List<List<Kennung>> kz = new ArrayList<>();
            Set<Plakette> benutzt = EnumSet.noneOf(Plakette.class);
            for (SheetData.Fernkampfwaffe f : d.fernkampf) {
                r.add(new String[]{f.name, f.talent, f.tp, f.reichweite, f.tpmod, f.laden, f.fk});
                List<Kennung> k = new ArrayList<>();
                if (f.jagd) { k.add(new Kennung(Plakette.JAGD, "J")); benutzt.add(Plakette.JAGD); }
                kz.add(k);
            }
            subheading("Fernkampfwaffen");
            table(new String[]{"Waffe", "Talent", "TP", "Reichweite", "TP-Mod.", "Laden", "FK"},
                    new float[]{2.2f, 1.8f, .9f, 1.8f, 1.5f, .6f, .6f},
                    new int[]{0, 0, 1, 1, 1, 1, 2}, r, false, null, kz, legendeHoehe(benutzt));
            legende("Fernkampf", benutzt);
            if (!benutzt.isEmpty()) space(3);
        }
        if (!d.schilde.isEmpty()) {
            List<String[]> r = new ArrayList<>();
            for (SheetData.Schild s : d.schilde) r.add(new String[]{s.name, s.typ, s.ini, s.mod, s.pa, s.bf});
            subheading("Schilde & Parierwaffen");
            table(new String[]{"Name", "Typ", "INI", "WM", "PA", "BF"},
                    new float[]{3f, 1.5f, .6f, .9f, .6f, .5f}, new int[]{0, 0, 1, 1, 2, 1}, r, false);
        }
        ruestung();
        behinderungUndInitiative();
        waffenlos();
        space(6);
        trefferzonenUndWunden(trefferLinks);
        space(6);
    }

    /** Trefferzonen: {Zone, Gezielter Schlag, Zufall W20, Reiter W20, 1. und 2. Wunde, 3. Wunde}. */
    private static final String[][] TREFFERZONEN = {
            {"Kopf", "+4", "19–20", "17–20", "MU, KL, IN, INI-Basis −2; INI −2W6", "+2W6 SP, bewusstlos, Blutverlust"},
            {"Brust", "+6", "15–18", "11–16", "AT, PA, KO, KK −1; +1W6 SP", "bewusstlos, Blutverlust"},
            {"Arme", "+4/+6**", "9–14", "3–10", "AT, PA, KK, FF −2 mit diesem Arm", "Arm handlungsunfähig"},
            {"Bauch", "+4", "7–8", "1–2", "AT, PA, KO, KK, GS, INI-Basis −1; +1W6 SP", "bewusstlos, Blutverlust"},
            {"Beine", "+2", "1–6***", "–", "AT, PA, GE, INI-Basis −2; GS −1", "Sturz, kampfunfähig"}};

    private void ruestung() {
        // Schwertarm = rechts, außer bei Linkshändern
        boolean links = false;
        for (SheetData.Eintrag e : d.vorteile) if (e.name.startsWith("Linkshänder")) links = true;
        final String schwert = "9–14 ger.", schild = "9–14 ung.";
        final String[][] zonenW20 = {
                {"Kopf", "19–20"}, {"Brust", "15–18"}, {"Rücken", "15–18"}, {"Bauch", "7–8"},
                {"R. Arm", links ? schild : schwert}, {"L. Arm", links ? schwert : schild},
                {"R. Bein", "1–6 ger."}, {"L. Bein", "1–6 ung."}};
        final List<String[]> z = new ArrayList<>();          // {Name, RS, W20}
        for (String[] t : zonenW20) {
            String rs = d.zonen.get(t[0]);
            // ohne Zonendaten: ohne Rüstung ist der RS überall 0, sonst unbekannt (einfache RS-Berechnung)
            if (rs == null) rs = d.ruestungen.isEmpty() ? "0" : "–";
            z.add(new String[]{t[0], rs, t[1]});
        }
        final int zonen = z.size();
        String rsGes = !d.rsGesamt.isEmpty() ? d.rsGesamt : d.ruestungen.isEmpty() ? "0" : "";
        if (!rsGes.isEmpty()) z.add(new String[]{"RS", rsGes, "gesamt"});
        final List<String> teile = new ArrayList<>();
        for (SheetData.Ruestung r : d.ruestungen) teile.add(r.name + (r.rs.isEmpty() || "*".equals(r.rs) ? "" : " (RS " + r.rs + ", BE " + r.be + ")"));
        final List<String> lines = wrap(String.join(" · ", teile), F.reg(7.5f), W - 2 * M);
        final float tileH = zonen > 0 ? 40 : 28;
        final float blockH = tileH + 8 + lines.size() * 10;
        subheading("Rüstung & Trefferzonen", blockH);
        atomic(blockH, (g, x, yy, w, h) -> {
            float gap = 4, tw = (w - gap * (z.size() - 1)) / Math.max(1, z.size());
            for (int i = 0; i < z.size(); i++) {
                boolean sum = i >= zonen;
                float tx = x + i * (tw + gap);
                g.setColor(sum ? ACCENT_SOFT : FILL);
                g.fill(new RoundRectangle2D.Float(tx, yy, tw, tileH, 6, 6));
                g.setFont(F.bold(6.5f));
                g.setColor(MUTED);
                center(g, z.get(i)[0].toUpperCase(Locale.GERMAN), tx, tw, yy + 9);
                g.setFont(F.bold(12));
                g.setColor(sum ? ACCENT : INK);
                center(g, z.get(i)[1], tx, tw, yy + 23);
                if (zonen > 0) {
                    String w20 = z.get(i)[2];
                    if (!sum) {
                        g.setColor(WHITE);
                        g.fill(new RoundRectangle2D.Float(tx + 3, yy + 27, tw - 6, 10, 5, 5));
                    }
                    g.setFont(F.bold(6.1f));
                    g.setColor(sum ? MUTED : ACCENT);
                    center(g, sum ? w20 : fit(g, "W20 " + w20, tw - 8), tx, tw, yy + 34.5f);
                }
            }
            float ly = yy + tileH + 10;
            g.setFont(F.reg(7.5f));
            g.setColor(MUTED);
            for (String l : lines) { g.drawString(l, x, ly); ly += 10; }
        });
        trefferLinks = links;
    }

    private boolean trefferLinks;

    /** Tabelle der Trefferzonen mit Zufallswurf, Reiter, gezieltem Schlag und Wundfolgen. */
    private void trefferzonenUndWunden(final boolean linkshaender) {
        final float[] rel = {0.8f, 0.62f, 0.72f, 0.58f, 3.1f, 2.15f, 0.8f};
        final String[] head = {"Zone", "G.S.*", "Zufall", "Reiter", "Erste und zweite Wunde", "Dritte Wunde", "Wunden"};
        final int[] align = {0, 1, 1, 1, 0, 0, 1};
        final float rowH = 14, headH = 14;
        final String[] fuss = {
                "*) Gezielter Schlag; Ansage jeweils +2.",
                "**) Ansage +4 für den Schwertarm, +6 für den Schildarm. Zufall: gerade = Schwertarm, ungerade = Schildarm"
                        + (linkshaender ? " (Linkshänder: Schwertarm links)." : " (Schwertarm rechts)."),
                "***) Zufall: gerade = rechtes Bein, ungerade = linkes Bein.   Rücken wird wie die Brust (Torso) ausgewürfelt."};
        float h = 14 + headH + TREFFERZONEN.length * rowH + 4 + fuss.length * 9.5f;
        if (y + h > BOTTOM) newPage();
        atomic(h, (g, x, yy, w, hh) -> {
            float sum = 0;
            for (float r : rel) sum += r;
            float[] cx = new float[rel.length + 1];
            cx[0] = x;
            for (int i = 0; i < rel.length; i++) cx[i + 1] = cx[i] + rel[i] / sum * w;
            g.setFont(F.bold(7));
            g.setColor(MUTED);
            g.drawString("TREFFERZONEN & WUNDEN", x, yy + 9);
            float ty = yy + 14;
            g.setColor(ACCENT_SOFT);
            g.fill(new RoundRectangle2D.Float(x, ty, w, headH, 6, 6));
            g.setFont(F.bold(6.3f));
            g.setColor(ACCENT.darker());
            for (int i = 0; i < head.length; i++) cell(g, head[i].toUpperCase(Locale.GERMAN), cx[i], cx[i + 1] - cx[i], ty + 9.8f, align[i]);
            float ry = ty + headH;
            for (int r = 0; r < TREFFERZONEN.length; r++) {
                String[] z = TREFFERZONEN[r];
                if (r % 2 == 1) { g.setColor(FILL); g.fill(new Rectangle2D.Float(x, ry, w, rowH)); }
                float base = ry + rowH - 4;
                for (int c = 0; c < z.length; c++) {
                    g.setFont(c == 0 ? F.bold(8) : c >= 4 ? F.reg(7.2f) : F.reg(7.8f));
                    g.setColor(c == 0 ? INK : c == 5 ? ACCENT.darker() : new Color(0x444444));
                    cell(g, z[c], cx[c], cx[c + 1] - cx[c], base, align[c]);
                }
                // drei Kästchen zum Abstreichen der Wunden in dieser Zone
                int boxes = 3;
                float bs = 7, bg = 3, bx = cx[6] + ((cx[7] - cx[6]) - (boxes * bs + (boxes - 1) * bg)) / 2;
                g.setColor(LINE.darker());
                for (int i = 0; i < boxes; i++)
                    g.draw(new RoundRectangle2D.Float(bx + i * (bs + bg), ry + (rowH - bs) / 2, bs, bs, 2, 2));
                ry += rowH;
            }
            g.setFont(F.reg(6.6f));
            g.setColor(MUTED);
            float fy = ry + 10;
            for (String f : fuss) { g.drawString(fit(g, f, w), x, fy); fy += 9.5f; }
        });
    }

    /** Gesamt-Behinderung der angelegten Rüstung und Initiative-Berechnung je Waffe. */
    private void behinderungUndInitiative() {
        // ohne Angabe der Software: keine Rüstung = BE 0; mit Rüstung unbekannt (Feld zum Eintragen)
        String beText = !d.beGesamt.isEmpty() ? d.beGesamt : d.ruestungen.isEmpty() ? "0" : "";
        final int be = SheetData.num(beText);
        final boolean hatBe = !beText.isEmpty();
        final int basis = d.akt("initiative");
        final List<String[]> rows = new ArrayList<>();   // {Waffe, Rechnung, Ergebnis}
        for (SheetData.Nahkampfwaffe n : d.nahkampf) {
            int mod = SheetData.num(n.ini);
            rows.add(new String[]{n.name, basis + " − " + (hatBe ? be : "BE") + " " + vz(mod),
                    hatBe ? (basis - be + mod) + " + 1W6" : "____ + 1W6"});
        }
        // Fernkampfwaffen haben in der Helden-Software keinen INI-Modifikator – eine gemeinsame Zeile genügt
        if (!d.fernkampf.isEmpty())
            rows.add(new String[]{"Fernkampfwaffen", basis + " − " + (hatBe ? be : "BE") + " + 0",
                    hatBe ? (basis - be) + " + 1W6" : "____ + 1W6"});
        rows.add(new String[]{"Waffenlos", basis + " − " + (hatBe ? be : "BE") + " + 0",
                hatBe ? (basis - be) + " + 1W6" : "____ + 1W6"});
        final List<String> schild = new ArrayList<>();
        for (SheetData.Schild sc : d.schilde) {
            int mod = SheetData.num(sc.ini);
            if (mod != 0) schild.add("mit " + sc.name + " zusätzlich " + vz(mod).replace(" ", ""));
        }
        final float nameW = (W - 2 * M) * 0.42f - 16;
        final List<List<String>> nameLines = new ArrayList<>();
        float rowsH = 0;
        for (String[] rw : rows) {
            List<String> l = wrap(rw[0], F.bold(8), nameW);
            if (l.isEmpty()) l.add(rw[0]);
            nameLines.add(l);
            rowsH += 12.5f + (l.size() - 1) * 9.5f;
        }
        final List<String> schildZeilen = new ArrayList<>();
        for (String sz : schild) schildZeilen.addAll(wrap(sz, F.reg(7), W - 2 * M - 20));
        space(4);
        // Bei sehr vielen Waffen über mehrere Seiten verteilen: je Seite ein Kasten, ab dem zweiten mit „(Fortsetzung)“
        final float kopfH = 22, fussH = 2;
        int start = 0;
        boolean erster = true;
        while (start < rows.size()) {
            float frei = BOTTOM - y;
            float schildH = 11 * schildZeilen.size();
            // passen hier nicht wenigstens Kopf und zwei Zeilen hin, auf der nächsten Seite beginnen
            float min = kopfH + fussH + 12.5f * Math.min(2, rows.size() - start);
            if (y > M + 1 && frei < min) { newPage(); frei = BOTTOM - y; }
            int ende = start;
            float zeilenH = 0;
            while (ende < rows.size()) {
                float rh = 12.5f + (nameLines.get(ende).size() - 1) * 9.5f;
                boolean letzte = ende == rows.size() - 1;
                if (kopfH + zeilenH + rh + (letzte ? schildH : 0) + fussH > frei && ende > start) break;
                zeilenH += rh;
                ende++;
            }
            final int von = start, bis = ende;
            final boolean fortsetzung = !erster, mitSchild = bis == rows.size();
            float h = kopfH + zeilenH + (mitSchild ? schildH : 0) + fussH;
            atomic(h, (g, x, yy, w, hh) -> {
                g.setColor(FILL);
                g.fill(new RoundRectangle2D.Float(x, yy, w, hh, 8, 8));
                g.setFont(F.bold(6.8f));
                g.setColor(MUTED);
                g.drawString(fortsetzung ? "INITIATIVE IM KAMPF (FORTSETZUNG)" : "INITIATIVE IM KAMPF", x + 10, yy + 14);
                if (!fortsetzung) {
                    g.setFont(F.reg(7.2f));
                    g.drawString("INI-Basis − BE + Waffen-/Schild-Mod. + 1W6", x + 104, yy + 14);
                }
                // kompakte Plakette: Behinderung der angelegten Rüstung
                String label = "BE durch Rüstung", wert = hatBe ? String.valueOf(be) : "–";
                g.setFont(F.bold(6.8f));
                float lw = g.getFontMetrics().stringWidth(label);
                g.setFont(F.bold(9));
                float vw = g.getFontMetrics().stringWidth(wert);
                float pw = lw + vw + 22, px = x + w - pw - 8, py = yy + 5;
                g.setColor(ACCENT_SOFT);
                g.fill(new RoundRectangle2D.Float(px, py, pw, 13, 13, 13));
                g.setFont(F.bold(6.8f));
                g.setColor(ACCENT.darker());
                g.drawString(label, px + 8, py + 9.3f);
                g.setFont(F.bold(9));
                g.setColor(ACCENT);
                g.drawString(wert, px + 8 + lw + 6, py + 9.8f);

                float ly = yy + 30;
                float c1 = x + 10, c2 = x + w * 0.42f, c3 = x + w - 10;
                for (int ri = von; ri < bis; ri++) {
                    String[] r = rows.get(ri);
                    g.setFont(F.bold(8));
                    g.setColor(INK);
                    float nly = ly;
                    for (String l : nameLines.get(ri)) { g.drawString(l, c1, nly); nly += 9.5f; }
                    g.setFont(F.reg(7.6f));
                    g.setColor(MUTED);
                    g.drawString(r[1], c2, ly);
                    g.setFont(F.bold(8.5f));
                    g.setColor(INK);
                    right(g, "= " + r[2], c3, ly);
                    ly += 12.5f + (nameLines.get(ri).size() - 1) * 9.5f;
                }
                if (mitSchild) {
                    g.setFont(F.reg(7));
                    g.setColor(ACCENT);
                    for (String sline : schildZeilen) { g.drawString(sline, c1, ly); ly += 11; }
                }
            });
            start = bis;
            erster = false;
        }
    }

    private static String vz(int v) { return v < 0 ? "− " + (-v) : "+ " + v; }

    private void waffenlos() {
        final List<String[]> items = new ArrayList<>();
        if (!d.raufenAt.isEmpty()) items.add(new String[]{"Raufen", "AT " + d.raufenAt + " · PA " + d.raufenPa + " · TP " + d.raufenTp});
        if (!d.ringenAt.isEmpty()) items.add(new String[]{"Ringen", "AT " + d.ringenAt + " · PA " + d.ringenPa + " · TP " + d.ringenTp});
        if (!d.gsInklBe.isEmpty()) items.add(new String[]{"GS inkl. BE", d.gsInklBe});
        if (items.isEmpty()) return;
        space(4);
        atomic(24, (g, x, yy, w, h) -> {
            g.setColor(FILL);
            g.fill(new RoundRectangle2D.Float(x, yy, w, h, 8, 8));
            float cx = x + 10;
            for (String[] it : items) {
                g.setFont(F.bold(8));
                g.setColor(INK);
                g.drawString(it[0], cx, yy + 15);
                cx += g.getFontMetrics().stringWidth(it[0]) + 5;
                g.setFont(F.reg(8));
                g.setColor(MUTED);
                g.drawString(it[1], cx, yy + 15);
                cx += g.getFontMetrics().stringWidth(it[1]) + 18;
            }
        });
    }

    private void sonderfertigkeiten() {
        if (d.sf.isEmpty()) return;
        heading("Sonderfertigkeiten", 30);
        List<ColItem> items = new ArrayList<>();
        float colW = colWidth();
        for (SheetData.Eintrag e : d.sf) items.addAll(entryItems(e.name, colW));
        columns(items, "Sonderfertigkeiten");
        space(6);
    }

    private void vorteile() {
        if (d.vorteile.isEmpty()) return;
        boolean vor = false, nach = false;
        for (SheetData.Eintrag e : d.vorteile) { if (e.nachteil) nach = true; else vor = true; }
        // nur eine Art vorhanden: keine leere Spalte mit Überschrift, sondern beide Spalten nutzen
        String titel = vor && nach ? "Vorteile & Nachteile" : vor ? "Vorteile" : "Nachteile";
        heading(titel, 30);
        List<ColItem> items = new ArrayList<>();
        float colW = colWidth();
        if (vor) {
            if (nach) items.add(labelItem("Vorteile"));
            for (SheetData.Eintrag e : d.vorteile) if (!e.nachteil) items.addAll(entryItems(e.name, colW));
        }
        if (vor && nach) items.add(ColItem.BREAK);
        if (nach) {
            if (vor) items.add(labelItem("Nachteile"));
            for (SheetData.Eintrag e : d.vorteile) if (e.nachteil) items.addAll(entryItems(e.name, colW));
        }
        columns(items, titel);
    }

    /** Eigener Text oder Kurzbeschreibung zu einem Namen, sonst "". */
    /** Eigene Notiz zum Eintrag (Tooltip-Text bearbeiten …); leer, wenn keine. */
    private String eigeneNotiz(String name) {
        if (db == null) return "";
        TooltipDB.Match m = db.find(name);
        if (m == null) return "";
        String own = db.getUserText(m.key);
        if (own == null && m.entry != null) own = db.getUserText(m.entry.name);
        return own == null ? "" : own.trim();
    }

    /** Mitgelieferte Kurzbeschreibung zum Eintrag; leer, wenn keine. */
    private String grundBeschreibung(String name) {
        if (db == null) return "";
        TooltipDB.Match m = db.find(name);
        String t = m != null && m.entry != null ? m.entry.text : "";
        return t == null ? "" : t.trim();
    }

    /** Text zu einem Eintrag nach den Optionen: der eigene Text ersetzt die Kurzbeschreibung. */
    private String[] eintragText(String name) {
        String own = opt.eigeneNotizen ? eigeneNotiz(name) : "";
        String desc = opt.beschreibungen ? grundBeschreibung(name) : "";
        return new String[]{own.isEmpty() ? desc : own, opt.eigeneNotizen && own.isEmpty() ? "ja" : ""};
    }

    /** So viele Beschreibungszeilen passen in einen Block; längere Texte werden in Folgeblöcke geteilt. */
    private static final int BLOCK_ZEILEN = 14;

    /**
     * Eintrag (SF, Vor-/Nachteil) mit Beschreibung. Sehr lange eigene Notizen werden in mehrere
     * Blöcke geteilt, damit sie über Spalten und Seiten weiterlaufen statt über den Rand hinaus.
     */
    private List<ColItem> entryItems(final String name, float colW) {
        String[] et = eintragText(name);
        String desc = et[0];
        List<String> alle = desc.isEmpty() ? Collections.<String>emptyList() : wrap(desc, F.reg(7), colW - 10);
        List<ColItem> out = new ArrayList<>();
        out.add(entryItem(name, colW, alle.subList(0, Math.min(BLOCK_ZEILEN, alle.size()))));
        for (int i = BLOCK_ZEILEN; i < alle.size(); i += BLOCK_ZEILEN) {
            final List<String> teil = alle.subList(i, Math.min(i + BLOCK_ZEILEN, alle.size()));
            out.add(new ColItem(teil.size() * 8.6f + 2, false, (g, x, yy, w, hh) -> {
                g.setColor(LINE.darker());
                g.fill(new Rectangle2D.Float(x, yy, 1f, hh - 2));   // dünne Linie: gehört zum Eintrag darüber
                g.setFont(F.reg(7));
                g.setColor(MUTED);
                float ly = yy + 6.5f;
                for (String l : teil) { g.drawString(l, x + 7, ly); ly += 8.6f; }
            }));
        }
        // keine Schreiblinien unter Sonderfertigkeiten und Vor-/Nachteilen (nur bei Zaubern)
        return out;
    }

    private ColItem entryItem(final String name, float colW, final List<String> lines) {
        // Lange Namen werden umbrochen statt abgeschnitten
        final List<String> nameLines = wrap(name, F.bold(8.5f), colW - 8);
        if (nameLines.isEmpty()) nameLines.add(name);
        final float nameH = 10.5f * nameLines.size();
        float h = 2.5f + nameH + lines.size() * 8.6f + (lines.isEmpty() ? 0 : 2);
        return new ColItem(h, false, (g, x, yy, w, hh) -> {
            g.setColor(ACCENT);
            g.fill(new Rectangle2D.Float(x, yy + 4, 2f, 7 + 10.5f * (nameLines.size() - 1)));
            g.setFont(F.bold(8.5f));
            g.setColor(INK);
            float ny = yy + 10.5f;
            for (String n : nameLines) { g.drawString(n, x + 7, ny); ny += 10.5f; }
            g.setFont(F.reg(7));
            g.setColor(MUTED);
            float ly = yy + nameH + 9;
            for (String l : lines) { g.drawString(l, x + 7, ly); ly += 8.6f; }
        });
    }

    private ColItem labelItem(final String label) {
        return new ColItem(16, true, (g, x, yy, w, h) -> {
            g.setFont(F.bold(7));
            g.setColor(MUTED);
            g.drawString(label.toUpperCase(Locale.GERMAN), x, yy + 10);
        });
    }

    private void talente() {
        boolean irgendein = false;
        for (SheetData.Talent t : d.talente) if (!t.meta || opt.metatalente) { irgendein = true; break; }
        if (!irgendein) return;
        heading("Talente", 40);
        final Set<Plakette> benutzt = EnumSet.noneOf(Plakette.class);
        Map<String, List<SheetData.Talent>> groups = new LinkedHashMap<>();
        for (SheetData.Talent t : d.talente) {
            if (t.meta && !opt.metatalente) continue;
            String g = t.meta ? "Meta-Talente" : (t.bereich.isEmpty() ? "Sonstige" : t.bereich);
            groups.computeIfAbsent(g, k -> new ArrayList<>()).add(t);
        }
        List<SheetData.Talent> meta = groups.remove("Meta-Talente");
        if (meta != null) groups.put("Meta-Talente", meta);
        List<ColItem> items = new ArrayList<>();
        for (Map.Entry<String, List<SheetData.Talent>> e : groups.entrySet()) {
            final String title = gruppenTitel(e.getKey());
            final boolean kampf = "Kampf".equals(e.getKey());
            final boolean sprache = e.getKey().startsWith("Sprach") || e.getKey().startsWith("Schrift");
            items.add(new ColItem(20, true, (g, x, yy, w, h) -> {
                g.setFont(F.bold(8.5f));
                g.setColor(ACCENT);
                g.drawString(title, x, yy + 12);
                g.setFont(F.bold(5.8f));
                g.setColor(MUTED);
                float[] c = talentCols(w);
                for (int k = 0; k < c.length; k += 2) c[k] += x;
                if (kampf) { center(g, "AT/FK", c[2], c[3], yy + 12); center(g, "PA", c[4], c[5], yy + 12); }
                else {
                    g.drawString("PROBE", c[2] + 2, yy + 12);
                    right(g, sprache ? "KOMPL." : "WERTE", c[2] + c[3] + c[5] - 4, yy + 12);
                }
                center(g, "BE", c[6], c[7], yy + 12);
                center(g, "SP", c[8], c[9], yy + 12);
                right(g, "TAW", c[10] + c[11], yy + 12);
                g.setColor(LINE);
                g.draw(new Line2D.Float(x, yy + h - 3, x + w, yy + h - 3));
            }));
            int i = 0;
            for (final SheetData.Talent t : e.getValue()) {
                final boolean zebra = (i++ % 2) == 1;
                // lange Talentnamen umbrechen statt abschneiden
                final float nameBreite = talentCols(colWidth())[1];
                // Kennzeichen (Meisterhandwerk, Leittalent, Mirakel) hinter dem Namen – passen sie nicht, eine eigene Zeile darunter
                final List<Plakette> pl = new ArrayList<>();
                if (t.meister) pl.add(Plakette.MH);
                if (t.leit) pl.add(Plakette.LEIT);
                if (t.mirakelPlus) pl.add(Plakette.MIRAKEL_PLUS);
                if (t.mirakelMinus) pl.add(Plakette.MIRAKEL_MINUS);
                benutzt.addAll(pl);
                final float tag = pl.isEmpty() ? 0 : plBreite(pl) + 4;
                final Font nf = F.reg(8);
                final List<String> nl = wrap(t.name, nf, nameBreite - 4);
                if (nl.isEmpty()) nl.add(t.name);
                float letzte = mg.getFontMetrics(nf).stringWidth(nl.get(nl.size() - 1));
                final boolean mhUnten = !pl.isEmpty() && letzte + 4 + tag > nameBreite - 4;
                // Spezialisierung: hinter Name bzw. Kürzel, wenn sie dort vollständig passt – sonst eigene Zeile(n) darunter
                final List<String> sl = new ArrayList<>();
                if (!t.spez.isEmpty()) {
                    float rest = mhUnten ? nameBreite - 6 - tag - 10 : nameBreite - letzte - tag - 10;
                    if (mg.getFontMetrics(F.reg(6.5f)).stringWidth(t.spez) > rest)
                        sl.addAll(wrap(t.spez, F.reg(6.5f), nameBreite - 10));
                }
                items.add(new ColItem(12.5f + (nl.size() - 1) * 9.5f + (mhUnten ? 9 : 0) + sl.size() * 7.5f, false,
                        (g, x, yy, w, h) -> talentRow(g, t, pl, nl, sl, mhUnten, kampf, sprache, zebra, x, yy, w, h)));
            }
            items.add(new ColItem(6, false, (g, x, yy, w, h) -> { }));
        }
        final List<List<Plakette>> leg = legendeZeilen(benutzt, colWidth());
        if (!leg.isEmpty()) {
            // Abstand nach der letzten Gruppe entfällt; die Legende bleibt an der letzten Talentzeile hängen,
            // damit sie nie allein oben in einer Spalte oder auf einer neuen Seite steht
            items.remove(items.size() - 1);
            ColItem letzte = items.remove(items.size() - 1);
            items.add(new ColItem(letzte.h, true, letzte.p));
            items.add(new ColItem(4 + leg.size() * 12, false, new LegendenZeile("Talente", (g, x, yy, w, h) -> {
                for (int k = 0; k < leg.size(); k++) legendeZeichnen(g, leg.get(k), x, yy + 13 + k * 12);
            })));
        }
        columns(items, "Talente");
    }

    private static String gruppenTitel(String b) {
        switch (b) {
            case "Kampf": return "Kampftalente";
            case "Körperlich": return "Körperliche Talente";
            case "Gesellschaft": return "Gesellschaft";
            case "Natur": return "Naturtalente";
            case "Wissen": return "Wissenstalente";
            case "Sprachen": return "Sprachen";
            case "Schriften": return "Schriften";
            case "Handwerk": return "Handwerkstalente";
            case "Gaben": return "Gaben";
            default: return b;
        }
    }

    /** x-Start und Breite der Talentspalten: name, probe/AT, (PA), BE, Spalte, TaW */
    private static float[] talentCols(float w) {
        float taw = 18, sp = 16, be = 30, werte = 98;
        float name = w - taw - sp - be - werte;
        float x0 = 0;
        return new float[]{x0, name, x0 + name, werte / 2, x0 + name + werte / 2, werte / 2,
                x0 + name + werte, be, x0 + name + werte + be, sp, x0 + name + werte + be + sp, taw};
    }

    // ------------------------------------------------------------------ Kennzeichen (Plaketten)

    /** Kleine farbige Kürzel, wie sie der Heldenbogen der Helden-Software als „(L)“, „(M+)“, „(A)“ usw. zeigt. */
    enum Plakette {
        MH("MH", ACCENT, "Meisterhandwerk"),
        LEIT("L", new Color(0x4a6587), "Leittalent"),
        MIRAKEL_PLUS("M+", new Color(0x3d8a50), "Mirakel (erleichtert)"),
        MIRAKEL_MINUS("M\u2212", new Color(0xb3403a), "Mirakel (erschwert)"),
        HAUSZAUBER("HZ", new Color(0x7a4f9a), "Hauszauber"),
        LEITZAUBER("L", new Color(0x4a6587), "Leitzauber"),
        JAGD("J", new Color(0x2d7a7e), "Jagdwaffe"),
        AUSDAUER("A", new Color(0x7b7b7b), "Ausdauerschaden"),
        SCHILD("S", new Color(0x7b7b7b), "kombiniert mit Schild/Parierwaffe Nr. 1");

        final String text; final Color farbe; final String bedeutung;
        Plakette(String text, Color farbe, String bedeutung) { this.text = text; this.farbe = farbe; this.bedeutung = bedeutung; }
    }

    /** Eine Plakette mit eigenem Text (z. B. „S2“), sonst wie die Vorlage. */
    private static final class Kennung {
        final Plakette art; final String text;
        Kennung(Plakette art, String text) { this.art = art; this.text = text; }
    }

    /** Schrift der Kürzel – eine Stufe kleiner als die Spezialisierung. */
    private static Font mhSchrift() { return F.reg(5.5f); }

    private float plBreite(String text) { return mg.getFontMetrics(mhSchrift()).stringWidth(text) + 5; }

    private float plBreite(List<Plakette> l) { return kBreite(kennungen(l)); }

    private float kBreite(List<Kennung> l) {
        float w = 0;
        for (Kennung k : l) w += plBreite(k.text);
        return w + Math.max(0, l.size() - 1) * 2;
    }

    private static List<Kennung> kennungen(List<Plakette> l) {
        List<Kennung> k = new ArrayList<>();
        for (Plakette p : l) k.add(new Kennung(p, p.text));
        return k;
    }

    /** Zeichnet ein Kürzel als kleine Plakette auf der Grundlinie; liefert das rechte Ende. */
    private float plZeichnen(Graphics2D g, float x, float base, String text, Color farbe) {
        float w = plBreite(text);
        g.setColor(farbe);
        g.fill(new RoundRectangle2D.Float(x, base - 6.1f, w, 7.6f, 3.5f, 3.5f));
        g.setFont(mhSchrift());
        g.setColor(WHITE);
        g.drawString(text, x + 2.5f, base - 0.5f);
        return x + w;
    }

    private float kZeichnen(Graphics2D g, float x, float base, List<Kennung> l) {
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) x += 2;
            x = plZeichnen(g, x, base, l.get(i).text, l.get(i).art.farbe);
        }
        return x;
    }

    private float plZeichnen(Graphics2D g, float x, float base, List<Plakette> l) { return kZeichnen(g, x, base, kennungen(l)); }

    private float legendeEintrag(Plakette p) {
        return plBreite(p == Plakette.SCHILD ? "S1" : p.text) + 3 + mg.getFontMetrics(F.reg(7)).stringWidth("= " + p.bedeutung) + 12;
    }

    /** Verteilt die benutzten Kennzeichen auf Legendenzeilen der Breite w. */
    private List<List<Plakette>> legendeZeilen(Collection<Plakette> benutzt, float w) {
        List<List<Plakette>> z = new ArrayList<>();
        List<Plakette> cur = new ArrayList<>();
        float x = 3;
        for (Plakette p : benutzt) {
            float b = legendeEintrag(p);
            if (!cur.isEmpty() && x + b > w) { z.add(cur); cur = new ArrayList<>(); x = 3; }
            cur.add(p);
            x += b;
        }
        if (!cur.isEmpty()) z.add(cur);
        return z;
    }

    private void legendeZeichnen(Graphics2D g, List<Plakette> zeile, float x, float base) {
        float tx = x + 3;
        for (Plakette p : zeile) {
            tx = plZeichnen(g, tx, base, p == Plakette.SCHILD ? "S1" : p.text, p.farbe);
            g.setFont(F.reg(7));
            g.setColor(MUTED);
            String t = "= " + p.bedeutung;
            g.drawString(t, tx + 3, base);
            tx += 3 + g.getFontMetrics().stringWidth(t) + 12;
        }
    }

    /** Höhe der Legende über die volle Breite (0, wenn nichts zu erklären ist). */
    private float legendeHoehe(Collection<Plakette> benutzt) { return legendeZeilen(benutzt, W - 2 * M).size() * 12; }

    /** Legende über die volle Breite (unter Tabellen) – als ein Block, der nicht zerteilt wird. */
    private void legende(String art, Collection<Plakette> benutzt) {
        final List<List<Plakette>> leg = legendeZeilen(benutzt, W - 2 * M);
        if (leg.isEmpty()) return;
        atomic(leg.size() * 12, new LegendenZeile(art, (g, x, yy, w, h) -> {
            for (int k = 0; k < leg.size(); k++) legendeZeichnen(g, leg.get(k), x, yy + 9 + k * 12);
        }));
    }

    private void talentRow(Graphics2D g, SheetData.Talent t, List<Plakette> pl, List<String> nameLines, List<String> spezLines, boolean mhUnten, boolean kampf,
                           boolean sprache, boolean zebra, float x, float yy, float w, float h) {
        if (zebra) {
            g.setColor(FILL);
            g.fill(new Rectangle2D.Float(x, yy, w, h));
        }
        float[] c = talentCols(w);
        for (int i = 0; i < c.length; i += 2) c[i] += x;
        float base = yy + 12.5f - 3.6f;   // Werte immer auf der ersten Zeile
        g.setFont(F.reg(8));
        g.setColor(INK);
        float nb = base;
        for (String l : nameLines) { g.drawString(l, c[0] + 3, nb); nb += 9.5f; }
        String last = nameLines.get(nameLines.size() - 1);
        float nameW = g.getFontMetrics().stringWidth(last);
        float zeile = nb - 9.5f;                       // Grundlinie der letzten Namenszeile
        float hinten = c[0] + 3 + nameW + 4;           // dort geht es hinter dem Namen weiter
        if (!pl.isEmpty()) {
            if (mhUnten) { zeile += 9; hinten = plZeichnen(g, c[0] + 9, zeile, pl) + 4; }
            else hinten = plZeichnen(g, hinten, zeile, pl) + 4;
        }
        if (!t.spez.isEmpty()) {
            g.setFont(F.reg(6.5f));
            g.setColor(ACCENT);
            if (spezLines.isEmpty()) {
                g.drawString(t.spez, hinten, zeile);   // passt vollständig hinter Name bzw. Kürzel
            } else {
                float sb = zeile + 7.5f;
                for (String l : spezLines) { g.drawString(l, c[0] + 9, sb); sb += 7.5f; }
            }
        }
        g.setFont(F.reg(7.2f));
        g.setColor(MUTED);
        if (kampf) {
            g.setColor(INK);
            center(g, t.at, c[2], c[3], base);
            center(g, t.pa, c[4], c[5], base);
        } else {
            // Probe: Eigenschaften (z. B. GE/KO/KK) und rechts daneben die aktuellen Werte
            g.setFont(F.bold(7.4f));
            g.setColor(INK);
            g.drawString(t.probe.replace(" ", ""), c[2] + 2, base);
            g.setFont(F.reg(7.2f));
            g.setColor(MUTED);
            String rechts = sprache ? t.k : t.probenwerte;
            right(g, rechts, c[2] + c[3] + c[5] - 4, base);
        }
        g.setColor(MUTED);
        center(g, t.be, c[6], c[7], base);
        center(g, t.spalte, c[8], c[9], base);
        g.setFont(F.bold(8.5f));
        g.setColor(INK);
        right(g, String.valueOf(t.wert), c[10] + c[11] - 3, base);
    }

    /** Eigene Seite für Astralenergie und Zauber (bzw. Karmaenergie) – nur, wenn der Held so etwas besitzt. */
    private void magieSeite() {
        boolean asp = d.magisch || d.akt("astralenergie") > 0 || !d.zauber.isEmpty();
        boolean kap = d.geweiht || d.akt("karmaenergie") > 0;
        if (!asp && !kap) return;
        if (y > M + 1) newPage();
        if (asp) {
            heading("Magie", 120);
            energiePool("Astralenergie", "AsP", d.akt("astralenergie"),
                    d.aspReg.isEmpty() ? "" : "Regeneration " + d.aspReg, "Zauber / Anlass");
        }
        // Karma vor der Zauberliste, damit die Linien für weitere Zauber den Platz nicht vorher aufbrauchen
        if (kap) {
            space(6);
            heading("Karma", 120);
            energiePool("Karmaenergie", "KaP", d.akt("karmaenergie"), "", "Liturgie / Anlass");
        }
        if (asp) zauberUebersicht();
    }

    /** Zauberübersicht: Probe, ZfW, AsP-Kosten (Feld zum Eintragen, falls leer), Eckdaten und Notizen. */
    private void zauberUebersicht() {
        if (d.zauber.isEmpty()) {
            // zauberkundig, aber noch keine Zauber: Linien zum Eintragen
            if (!opt.notizseite) return;
            space(8);
            subheading("Zauber & Notizen", 40);
            while (y + 17 <= BOTTOM) {
                atomic(17, (g, x, yy, w, h) -> {
                    g.setColor(LINE);
                    g.draw(new Line2D.Float(x, yy + h - 2, x + w, yy + h - 2));
                });
            }
            return;
        }
        space(8);
        subheading("Zauber", 60);
        final float fullW = W - 2 * M;
        final float cName = 0.36f, cProbe = 0.20f, cZfw = 0.08f, cKost = 0.36f;
        // Spaltenköpfe (werden auf Folgeseiten wiederholt)
        final Painter kopf = (g, x, yy, w, h) -> {
            g.setColor(ACCENT_SOFT);
            g.fill(new RoundRectangle2D.Float(x, yy, w, h, 6, 6));
            g.setFont(F.bold(6.4f));
            g.setColor(ACCENT.darker());
            g.drawString("ZAUBER", x + 6, yy + 9);
            g.drawString("PROBE", x + w * cName + 4, yy + 9);
            center(g, "ZFW", x + w * (cName + cProbe), w * cZfw, yy + 9);
            g.drawString("ASP-KOSTEN", x + w * (cName + cProbe + cZfw) + 6, yy + 9);
        };
        place(13, kopf);
        final float kostW = fullW * cKost - 10;
        final Set<Plakette> benutzt = EnumSet.noneOf(Plakette.class);
        Set<Plakette> alle = EnumSet.noneOf(Plakette.class);
        for (SheetData.Zauber z : d.zauber) {
            if (z.hauszauber) alle.add(Plakette.HAUSZAUBER);
            if (z.leit) alle.add(Plakette.LEITZAUBER);
        }
        // die Legende bleibt mit dem letzten Zauber auf einer Seite
        final float legH = alle.isEmpty() ? 0 : 3 + legendeHoehe(alle);
        int idx = 0;
        for (final SheetData.Zauber z : d.zauber) {
            final boolean zebra = (idx++ % 2) == 1;
            final List<String> nameLines = wrap(z.name, F.bold(9), fullW * cName - 10);
            if (nameLines.isEmpty()) nameLines.add(z.name);
            // Hauszauber / Leitzauber als Plakette hinter dem Namen, sonst in eigener Zeile darunter
            final List<Plakette> pl = new ArrayList<>();
            if (z.hauszauber) pl.add(Plakette.HAUSZAUBER);
            if (z.leit) pl.add(Plakette.LEITZAUBER);
            benutzt.addAll(pl);
            final boolean plUnten = !pl.isEmpty() && mg.getFontMetrics(F.bold(9)).stringWidth(nameLines.get(nameLines.size() - 1))
                    + 5 + plBreite(pl) > fullW * cName - 10;
            // Eckdaten in einer Zeile
            List<String> eck = new ArrayList<>();
            if (!z.merkmale.isEmpty()) eck.add("Merkmale " + z.merkmale);
            if (!z.rep.isEmpty()) eck.add("Rep. " + z.rep);
            if (!z.spalte.isEmpty()) eck.add("Sp. " + z.spalte);
            if (!z.mr.isEmpty()) eck.add("MR " + z.mr);
            if (!z.zauberdauer.isEmpty()) eck.add("Zauberdauer " + z.zauberdauer);
            if (!z.reichweite.isEmpty()) eck.add("Reichweite " + z.reichweite);
            if (!z.wirkungsdauer.isEmpty()) eck.add("Wirkungsdauer " + z.wirkungsdauer);
            final List<String> eckLines = wrap(String.join("  ·  ", eck), F.reg(7), fullW - 16);
            // Notizen: Anmerkung der Helden-Software + eigener Text
            // eigene Notizen (Anmerkung in der Helden-Software, eigener Tooltip-Text) vor der Kurzbeschreibung
            List<String> n = new ArrayList<>();
            if (opt.eigeneNotizen) {
                if (!z.anmerkung.isEmpty()) n.add(z.anmerkung);
                String own = eigeneNotiz(z.name);
                if (!own.isEmpty() && !own.equals(z.anmerkung)) n.add(own);
            }
            final boolean eigeneLeer = n.isEmpty();
            if (n.isEmpty() && opt.beschreibungen) {
                String desc = grundBeschreibung(z.name);
                if (!desc.isEmpty()) n.add(desc);
            }
            final List<String> alleNotiz = n.isEmpty() ? new ArrayList<String>() : wrap(String.join(" – ", n), F.reg(7.4f), fullW - 22);
            final List<String> noteLines = alleNotiz.subList(0, Math.min(20, alleNotiz.size()));
            final boolean leerNotiz = noteLines.isEmpty();
            // leere Schreiblinie, wenn eigene Notizen eingeschaltet sind, aber noch keine dasteht
            final boolean schreiblinie = eigeneLeer && opt.eigeneNotizen;
            // lange Kostenangaben umbrechen; das Feld wächst mit
            final List<String> kostLines = z.kosten.isEmpty() ? new ArrayList<String>() : wrap(z.kosten, F.reg(7.6f), kostW - 10);
            final float kostH = 14 + Math.max(0, kostLines.size() - 1) * 9;
            final float topH = Math.max(Math.max(20, 6 + (nameLines.size() + (plUnten ? 1 : 0)) * 11 + 2), 4 + kostH + 4);
            final float h = topH + eckLines.size() * 9
                    + (leerNotiz ? (schreiblinie ? 15 : 2) : noteLines.size() * 9.5f + 4 + (schreiblinie ? 13 : 0)) + 4;
            float anhang = idx == d.zauber.size() && alleNotiz.size() <= 20 ? legH : 0;
            if (y + h + anhang > BOTTOM) {
                newPage();
                place(18, fortsetzung("Zauber").p);
                place(13, kopf);
            }
            atomic(h, (g, x, yy, w, hh) -> {
                if (zebra) { g.setColor(FILL); g.fill(new Rectangle2D.Float(x, yy, w, hh)); }
                float base = yy + 14;
                g.setFont(F.bold(9));
                g.setColor(INK);
                float ny = base;
                for (String l : nameLines) { g.drawString(l, x + 6, ny); ny += 11; }
                if (!pl.isEmpty()) {
                    if (plUnten) plZeichnen(g, x + 12, ny - 2, pl);
                    else plZeichnen(g, x + 6 + g.getFontMetrics().stringWidth(nameLines.get(nameLines.size() - 1)) + 5, ny - 11 - 0.5f, pl);
                }
                g.setColor(INK);
                float px = x + w * cName + 4;
                g.setFont(F.bold(8));
                g.drawString(z.probe, px, base);
                g.setFont(F.reg(7.6f));
                g.setColor(MUTED);
                g.drawString(z.probenwerte, px + g.getFontMetrics().stringWidth(z.probe) + 14, base);
                g.setFont(F.bold(10));
                g.setColor(INK);
                center(g, String.valueOf(z.wert), x + w * (cName + cProbe), w * cZfw, base + 0.5f);
                // Kostenfeld: Wert der Helden-Software oder leer zum einmaligen Eintragen
                float kx = x + w * (cName + cProbe + cZfw) + 4, kw = w * cKost - 10;
                g.setColor(WHITE);
                g.fill(new RoundRectangle2D.Float(kx, yy + 4, kw, kostH, 5, 5));
                g.setColor(LINE.darker());
                g.draw(new RoundRectangle2D.Float(kx, yy + 4, kw, kostH, 5, 5));
                if (!kostLines.isEmpty()) {
                    g.setFont(F.reg(7.6f));
                    g.setColor(INK);
                    float ky = yy + 13.8f;
                    for (String l : kostLines) { g.drawString(l, kx + 5, ky); ky += 9; }
                } else {
                    g.setFont(F.reg(6.4f));
                    g.setColor(LINE.darker());
                    right(g, "AsP", kx + kw - 5, yy + 13.6f);
                }
                float ly = yy + topH + 6;
                g.setFont(F.reg(7));
                g.setColor(MUTED);
                for (String l : eckLines) { g.drawString(l, x + 6, ly); ly += 9; }
                if (leerNotiz && !schreiblinie) {
                    // keine Notiz und Notizen ausgeschaltet: nichts zeichnen
                } else if (leerNotiz) {
                    g.setFont(F.reg(6.4f));
                    g.drawString("Notiz", x + 6, ly + 4);
                    g.setColor(LINE);
                    g.draw(new Line2D.Float(x + 28, ly + 5, x + w - 6, ly + 5));
                } else {
                    g.setColor(ACCENT);
                    g.fill(new Rectangle2D.Float(x + 7, ly - 5.5f, 1.6f, noteLines.size() * 9.5f - 1.5f));
                    g.setFont(F.reg(7.4f));
                    g.setColor(new Color(0x444444));
                    for (String l : noteLines) { g.drawString(l, x + 14, ly + 1); ly += 9.5f; }
                    // Beschreibung steht da, eigene Notiz fehlt noch: Linie dafür
                    if (schreiblinie) {
                        g.setFont(F.reg(6.4f));
                        g.setColor(MUTED);
                        g.drawString("Notiz", x + 6, ly + 6);
                        g.setColor(LINE);
                        g.draw(new Line2D.Float(x + 28, ly + 7, x + w - 6, ly + 7));
                    }
                }
            });
            // sehr lange Notizen: Rest in Folgeblöcken, die über Seiten weiterlaufen
            for (int k = 20; k < alleNotiz.size(); ) {
                int passt = (int) Math.floor((BOTTOM - y - 6) / 9.5f);
                // letzter Teil des letzten Zaubers: Platz für die Legende lassen
                if (idx == d.zauber.size() && k + passt >= alleNotiz.size() && legH > 0)
                    passt = Math.min(passt, (int) Math.floor((BOTTOM - y - 6 - legH) / 9.5f));
                if (passt < 3) {
                    newPage();
                    place(18, fortsetzung("Zauber").p);
                    place(13, kopf);
                    continue;
                }
                final List<String> teil = alleNotiz.subList(k, Math.min(k + passt, alleNotiz.size()));
                k += teil.size();
                atomic(teil.size() * 9.5f + 6, (g, x, yy, w, hh) -> {
                    if (zebra) { g.setColor(FILL); g.fill(new Rectangle2D.Float(x, yy, w, hh)); }
                    g.setColor(ACCENT);
                    g.fill(new Rectangle2D.Float(x + 7, yy + 1, 1.6f, teil.size() * 9.5f - 1.5f));
                    g.setFont(F.reg(7.4f));
                    g.setColor(new Color(0x444444));
                    float ly = yy + 7.5f;
                    for (String l : teil) { g.drawString(l, x + 14, ly); ly += 9.5f; }
                });
            }
        }
        if (!benutzt.isEmpty()) { space(3); legende("Zauber", benutzt); }
        // Platz für weitere Zauber – nur den Rest der Seite nutzen, keine neue Seite dafür anfangen
        space(6);
        if (!opt.notizseite || y + 14 + 3 * 17 > BOTTOM) return;
        subheading("Weitere Zauber & Notizen", 40);
        while (y + 17 <= BOTTOM) {
            atomic(17, (g, x, yy, w, h) -> {
                g.setColor(LINE);
                g.draw(new Line2D.Float(x, yy + h - 2, x + w, yy + h - 2));
            });
        }
    }

    private void inventar() {
        space(4);
        heading("Inventar", 40);
        List<ColItem> items = new ArrayList<>();
        int i = 0;
        final float invColW = colWidth();
        double unzenVorab = 0;
        for (SheetData.Gegenstand gg : d.inventar) unzenVorab += gg.unzen;
        for (final SheetData.Gegenstand gg : d.inventar) {
            final boolean zebra = (i++ % 2) == 1;
            // lange Namen umbrechen; Anzahl, Gewicht und „angelegt“ bleiben auf der ersten Zeile
            final List<String> nl = wrap(gg.name, F.reg(8), invColW - (gg.angelegt ? 132 : 98));
            if (nl.isEmpty()) nl.add(gg.name);
            items.add(new ColItem(12.5f + (nl.size() - 1) * 9.5f, false, (g, x, yy, w, h) -> {
                if (zebra) { g.setColor(FILL); g.fill(new Rectangle2D.Float(x, yy, w, h)); }
                float base = yy + 12.5f - 3.6f;
                g.setFont(F.reg(8));
                g.setColor(INK);
                float nb = base;
                for (String l : nl) { g.drawString(l, x + 3, nb); nb += 9.5f; }
                g.setFont(F.reg(7.2f));
                g.setColor(gg.angelegt ? ACCENT : MUTED);
                if (gg.angelegt) g.drawString("angelegt", x + w - 126, base);
                g.setColor(MUTED);
                right(g, "1".equals(gg.anzahl) ? "" : gg.anzahl + "×", x + w - 66, base);
                right(g, gg.gewichtStein.isEmpty() ? "" : gg.gewichtStein + " St. (" + gg.gewichtStein + " kg)", x + w - 3, base);
            }));
        }
        // leere Zeilen zum Eintragen weiterer Gegenstände (gehören zu den Notizfeldern), höchstens 14: Sie füllen
        // nur den Platz auf der Seite, lassen Raum für das Gesamtgewicht und beginnen nie eine neue Seite
        final float reserve = unzenVorab > 0 ? 11 : 0;
        if (opt.notizseite) {
            for (int k = 0; k < 14; k++) {
                final boolean zebra = (i++ % 2) == 1;
                items.add(new ColItem(14f, false, (g, x, yy, w, h) -> {
                    if (zebra) { g.setColor(FILL); g.fill(new Rectangle2D.Float(x, yy, w, h)); }
                    g.setColor(LINE.darker());
                    float base = yy + h - 3;
                    g.draw(new Line2D.Float(x + 3, base, x + w - 72, base));
                    g.draw(new Line2D.Float(x + w - 62, base, x + w - 30, base));
                    g.setFont(F.reg(6.5f));
                    g.setColor(MUTED);
                    right(g, "St.", x + w - 3, base - 1);
                }).alsFuell(reserve));
            }
        }
        // Gesamtgewicht als letzte Zeile des Inventars (auf derselben Seite wie mindestens ein Gegenstand)
        if (unzenVorab > 0) {
            final String st = SheetData.fmt(unzenVorab / 40.0);
            items.add(new ColItem(11, false, (g, x, yy, w, h) -> {
                g.setFont(F.reg(7.5f));
                g.setColor(MUTED);
                right(g, fit(g, "Gesamtgewicht: " + st + " Stein (" + st + " kg)  ·  1 Stein = 40 Unzen = 1 kg", w - 3), x + w - 3, yy + 8.5f);
            }).alsAbschluss());
        }
        columns(items, "Inventar");
        space(4);
        geldboerse();
    }

    /** Geldbörse in der Reihenfolge Dukaten – Silbertaler – Heller – Kreuzer, mit Umrechnung. */
    private void geldboerse() {
        final int[] mr = new int[Geld.NAMEN.length];
        final List<String> andere = new ArrayList<>();
        double silber = 0;
        for (SheetData.Muenze m : d.muenzen) {
            int i = Geld.index(m.name);
            boolean mittelreich = m.waehrung.isEmpty() || "Mittelreich".equals(m.waehrung);
            if (i >= 0 && mittelreich) { mr[i] += m.anzahl; silber += m.anzahl * Geld.WERT_S[i]; }
            else {
                // fremde Währungen werden nicht in den Gesamtwert eingerechnet (Wechselkurse schwanken)
                andere.add(m.anzahl + " " + m.name + (m.waehrung.isEmpty() ? "" : " (" + m.waehrung + ")"));
            }
        }
        final String summe = silber > 0 ? Geld.silber(silber) + " S" : "";
        final String fremd = String.join(" · ", andere);
        final List<String> fremdZeilen = fremd.isEmpty() ? new ArrayList<String>()
                : wrap("Andere Währungen (nicht im Gesamtwert): " + fremd, F.reg(7.2f), W - 2 * M - 82);
        atomic(50 + fremdZeilen.size() * 11, (g, x, yy, w, h) -> {
            g.setColor(ACCENT_SOFT);
            g.fill(new RoundRectangle2D.Float(x, yy, w, h, 8, 8));
            g.setFont(F.bold(8.5f));
            g.setColor(ACCENT);
            g.drawString("Geldbörse", x + 10, yy + 22);
            // vier Felder zum Eintragen/Ablesen
            float fx = x + 72, fw = (w - 72 - 96) / 4f;
            for (int i = 0; i < Geld.NAMEN.length; i++) {
                float bx = fx + i * fw;
                g.setFont(F.bold(6.3f));
                g.setColor(MUTED);
                g.drawString(Geld.PLURAL[i].toUpperCase(Locale.GERMAN) + " (" + Geld.KURZ[i] + ")", bx + 2, yy + 11);
                g.setColor(WHITE);
                g.fill(new RoundRectangle2D.Float(bx, yy + 14, fw - 8, 17, 6, 6));
                g.setColor(LINE);
                g.draw(new RoundRectangle2D.Float(bx, yy + 14, fw - 8, 17, 6, 6));
                if (mr[i] != 0) {
                    g.setFont(F.bold(10));
                    g.setColor(INK);
                    center(g, String.valueOf(mr[i]), bx, fw - 8, yy + 26.5f);
                }
            }
            // Gesamtwert
            float sx = x + w - 92;
            g.setFont(F.bold(6.3f));
            g.setColor(MUTED);
            g.drawString("GESAMT IN SILBERTALERN", sx, yy + 11);
            g.setColor(WHITE);
            g.fill(new RoundRectangle2D.Float(sx, yy + 14, 84, 17, 6, 6));
            g.setColor(LINE);
            g.draw(new RoundRectangle2D.Float(sx, yy + 14, 84, 17, 6, 6));
            if (!summe.isEmpty()) {
                g.setFont(F.bold(10));
                g.setColor(INK);
                center(g, summe, sx, 84, yy + 26.5f);
            }
            g.setFont(F.reg(7.2f));
            g.setColor(MUTED);
            g.drawString("Umrechnung: " + Geld.UMRECHNUNG + "   ·   1 D = 10 S,  1 S = 10 H,  1 H = 10 K", x + 72, yy + 43);
            g.setColor(INK);
            float fy = yy + 54;
            for (String l : fremdZeilen) { g.drawString(l, x + 72, fy); fy += 11; }
        });
    }

    private void notizen() {
        space(8);
        // folgt direkt das Feld für die Charakterbeschreibung, muss es mit der Überschrift auf die Seite passen
        boolean text = !d.aussehen.isEmpty() || !d.familie.isEmpty();
        heading("Notizen", text ? 80 : 26 + 8 * 17 + 6);
        if (!d.aussehen.isEmpty()) paragraph("Aussehen", d.aussehen);
        if (!d.familie.isEmpty()) paragraph("Familie & Herkunft", d.familie);
        charakterbeschreibung();
        space(6);
        if (!d.notizen.isEmpty() || y + 30 <= BOTTOM) {
            atomic(14, (g, x, yy, w, h) -> {
                g.setFont(F.bold(7));
                g.setColor(MUTED);
                g.drawString("WEITERE NOTIZEN", x, yy + 9);
            });
        }
        for (String n : d.notizen) paragraph(null, n);
        while (y + 18 <= BOTTOM) {
            atomic(18, (g, x, yy, w, h) -> {
                g.setColor(LINE);
                g.draw(new Line2D.Float(x, yy + h - 2, x + w, yy + h - 2));
            });
        }
    }

    /**
     * Umrandetes Schreibfeld für Persönlichkeit, Ziele, Eigenheiten usw. Mit hinterlegtem Text (Optionen →
     * Charakterbeschreibung öffnen/schreiben) steht dieser auf den Linien. Längerer Text bleibt im Rahmen: unter dem
     * Portrait wird das Feld so breit wie die Seite, und was nicht auf die Seite passt, steht auf der Folgeseite in
     * einem Rahmen „Charakterbeschreibung (Fortsetzung)“.
     */
    private void charakterbeschreibung() {
        final int grundZeilen = 8;
        final float lineH = 17;
        final float grundH = 26 + grundZeilen * lineH + 6;
        final boolean mitPortrait = opt.portrait;
        // Breite aus dem Seitenverhältnis (3:4, 9:16, 16:9 …); höchstens gut die Hälfte, damit Platz zum Schreiben bleibt
        final double verhaeltnis = portraitVerhaeltnis();
        final float wAll0 = W - 2 * M;
        final float portraitW0 = mitPortrait ? (float) Math.round(Math.min(grundH * verhaeltnis, wAll0 * 0.57f)) : 0;
        final Font textFont = F.reg(9);
        // Text aufbereiten: Tabulatoren zu Leerzeichen, Zeichen ohne Glyphe (z. B. Emoji) weglassen, höchstens eine
        // Leerzeile zwischen Absätzen
        List<String> absaetze = new ArrayList<>();
        if (opt.charakterText != null && !opt.charakterText.trim().isEmpty()) {
            String roh = opt.charakterText.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < roh.length(); ) {
                int cp = roh.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == '\n' || (!Character.isISOControl(cp) && textFont.canDisplay(cp)
                        && Character.getType(cp) != Character.FORMAT)) sb.appendCodePoint(cp);
            }
            boolean leer = false;
            for (String z : sb.toString().trim().split("\n", -1)) {
                String t = z.trim().replaceAll(" {2,}", " ");
                if (t.isEmpty()) { if (!leer && !absaetze.isEmpty()) absaetze.add(""); leer = true; }
                else { absaetze.add(t); leer = false; }
            }
        }
        // Breite im Feld: neben dem Portrait schmaler; darunter bzw. auf Folgeseiten die volle Breite
        final float breitFeld = (mitPortrait ? wAll0 - portraitW0 - 10 : wAll0) - 24, breitVoll = wAll0 - 24;
        List<String> text = new ArrayList<>();
        List<int[]> herkunft = new ArrayList<>();   // {Absatz, Zeichenposition im Absatz}
        for (int ai = 0; ai < absaetze.size(); ai++) {
            String abs = absaetze.get(ai);
            if (abs.isEmpty()) { text.add(""); herkunft.add(new int[]{ai, 0}); continue; }
            int pos = 0;
            for (String z : wrap(abs, textFont, breitFeld)) {
                text.add(z);
                herkunft.add(new int[]{ai, pos});
                // Position im Absatz weiterzählen; hart getrennte Wörter enden auf „-“, das im Text nicht vorkommt
                if (abs.startsWith(z, pos)) pos += z.length();
                else if (z.endsWith("-") && abs.startsWith(z.substring(0, z.length() - 1), pos)) pos += z.length() - 1;
                while (pos < abs.length() && abs.charAt(pos) == ' ') pos++;
            }
        }
        // Neben dem Portrait stehen die ersten Zeilen schmal; alles danach wird über die volle Breite neu umbrochen
        // und bleibt im selben Rahmen (das Feld wird unter dem Portrait breiter).
        final int schmal = mitPortrait ? Math.min(grundZeilen, text.size()) : 0;
        final List<String> voll = new ArrayList<>();
        if (!mitPortrait) voll.addAll(text);
        else if (text.size() > grundZeilen) {
            int[] start = herkunft.get(grundZeilen);
            for (int ai = start[0]; ai < absaetze.size(); ai++) {
                String abs = absaetze.get(ai);
                if (abs.isEmpty()) { voll.add(""); continue; }
                String teil = ai == start[0] ? abs.substring(Math.min(start[1], abs.length())) : abs;
                if (!teil.isEmpty()) voll.addAll(wrap(teil, textFont, breitVoll));
            }
            if (!voll.isEmpty() && voll.get(0).isEmpty()) voll.remove(0);
        }
        final float abstand = 10;   // Luft zwischen Portrait-Unterkante und den breiten Zeilen
        // erste Seite: so viele Zeilen, wie Platz ist; so bleibt die Überschrift „Notizen“ beim Feld
        float frei = BOTTOM - y - 32;
        if (frei < grundZeilen * lineH) { newPage(); frei = BOTTOM - y - 32; }
        int platz = (int) Math.floor((frei - (mitPortrait && !voll.isEmpty() ? abstand : 0)) / lineH);
        final int breitHier = Math.max(0, Math.min(voll.size(), platz - (mitPortrait ? grundZeilen : 0)));
        final int zeilenSchmal = mitPortrait ? grundZeilen : 0;
        final int zeilenBreit = mitPortrait ? breitHier : Math.max(grundZeilen, breitHier);
        final List<String> schmalText = new ArrayList<>(text.subList(0, schmal));
        final List<String> breitText = new ArrayList<>(voll.subList(0, breitHier));
        final float zusatz = mitPortrait && zeilenBreit > 0 ? abstand : 0;
        final float h = 26 + (zeilenSchmal + zeilenBreit) * lineH + zusatz + 6;
        final boolean leer = text.isEmpty();
        atomic(h, (g, x, yy, wAll, hh) -> {
            float portraitW = mitPortrait ? (float) Math.round(Math.min(grundH * verhaeltnis, wAll * 0.57f)) : 0;
            float w = mitPortrait ? wAll - portraitW - 10 : wAll;
            // Rahmen: neben dem Portrait schmal, darunter (bei langem Text) über die volle Breite
            java.awt.geom.Area form = new java.awt.geom.Area(new RoundRectangle2D.Float(x, yy, w, hh, 8, 8));
            if (mitPortrait && zeilenBreit > 0) {
                float unten = yy + grundH + 4;
                form.add(new java.awt.geom.Area(new RoundRectangle2D.Float(x, unten, wAll, yy + hh - unten, 8, 8)));
                form.add(new java.awt.geom.Area(new Rectangle2D.Float(x, unten, w, yy + hh - unten - 8)));
            }
            g.setColor(WHITE);
            g.fill(form);
            g.setColor(LINE);
            g.draw(form);
            kopf(g, x, yy, w, "Charakterbeschreibung", leer);
            float ly = yy + 24;
            for (int i = 0; i < zeilenSchmal + zeilenBreit; i++) {
                boolean breit = i >= zeilenSchmal;
                if (breit && i == zeilenSchmal) ly += zusatz;
                ly += lineH;
                String z = breit ? (i - zeilenSchmal < breitText.size() ? breitText.get(i - zeilenSchmal) : "")
                                 : (i < schmalText.size() ? schmalText.get(i) : "");
                schreibzeile(g, x, breit ? wAll : w, ly, z, textFont);
            }
            if (mitPortrait) portrait(g, x + wAll - portraitW, yy, portraitW, grundH);
        });
        // was nicht mehr auf die Seite passt: auf den Folgeseiten in einem eigenen Rahmen weiter
        int i = breitHier;
        while (i < voll.size()) {
            int passt = (int) Math.floor((BOTTOM - y - 32) / lineH);
            if (passt < 3) { newPage(); continue; }
            final List<String> teil = new ArrayList<>(voll.subList(i, Math.min(voll.size(), i + passt)));
            i += teil.size();
            atomic(26 + teil.size() * lineH + 6, (g, x, yy, w, hh) -> {
                g.setColor(WHITE);
                g.fill(new RoundRectangle2D.Float(x, yy, w, hh, 8, 8));
                g.setColor(LINE);
                g.draw(new RoundRectangle2D.Float(x, yy, w, hh, 8, 8));
                kopf(g, x, yy, w, "Charakterbeschreibung (Fortsetzung)", false);
                float ly = yy + 24;
                for (String z : teil) { ly += lineH; schreibzeile(g, x, w, ly, z, textFont); }
            });
        }
    }

    /** Titel des Charakterfelds; leer mit Stichworten rechts (gekürzt oder weggelassen, wenn es eng wird). */
    private void kopf(Graphics2D g, float x, float yy, float w, String titel, boolean stichworte) {
        g.setColor(ACCENT);
        g.fill(new Rectangle2D.Float(x + 10, yy + 7, 2.5f, 10));
        g.setFont(F.bold(9));
        g.setColor(INK);
        g.drawString(titel, x + 18, yy + 15.5f);
        float titelEnde = x + 18 + g.getFontMetrics().stringWidth(titel) + 12;
        if (!stichworte) return;
        g.setFont(F.reg(7));
        g.setColor(MUTED);
        String[] stufen = {"Persönlichkeit · Ziele & Motive · Ängste · Eigenheiten · Beziehungen",
                "Persönlichkeit · Ziele · Ängste · Eigenheiten", "Persönlichkeit · Ziele · Ängste", ""};
        String stich = "";
        for (String s2 : stufen) { stich = s2; if (g.getFontMetrics().stringWidth(stich) <= x + w - 10 - titelEnde) break; }
        if (!stich.isEmpty()) right(g, stich, x + w - 10, yy + 15.5f);
    }

    /** Eine Linie im Charakterfeld, ggf. mit Text darauf. */
    private void schreibzeile(Graphics2D g, float x, float w, float ly, String z, Font f) {
        g.setColor(LINE);
        g.draw(new Line2D.Float(x + 12, ly, x + w - 12, ly));
        if (z != null && !z.isEmpty()) {
            g.setFont(f);
            g.setColor(INK);
            g.drawString(z, x + 12, ly - 3.5f);
        }
    }

    /** Portrait-Rahmen: hinterlegtes Bild (zugeschnitten, mittig) oder leeres Feld zum Zeichnen/Einkleben. */
    private void portrait(Graphics2D g, float x, float yy, float w, float h) {
        RoundRectangle2D.Float box = new RoundRectangle2D.Float(x, yy, w, h, 8, 8);
        java.awt.image.BufferedImage img = opt.portraitBild;
        if (img != null) {
            Shape alt = g.getClip();
            g.clip(box);
            // füllend skalieren und mittig zuschneiden
            double s = Math.max(w / img.getWidth(), h / img.getHeight());
            double iw = img.getWidth() * s, ih = img.getHeight() * s;
            Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(img, (int) Math.round(x + (w - iw) / 2), (int) Math.round(yy + (h - ih) / 2),
                    (int) Math.round(iw), (int) Math.round(ih), null);
            if (interp != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
            g.setClip(alt);
            g.setColor(LINE);
            g.draw(box);
        } else {
            g.setColor(WHITE);
            g.fill(box);
            g.setColor(LINE.darker());
            g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{3, 3}, 0));
            g.draw(box);
            g.setStroke(new BasicStroke(1f));
            g.setFont(F.bold(8));
            g.setColor(MUTED);
            center(g, "Portrait", x, w, yy + h / 2 - 2);
            g.setFont(F.reg(6.5f));
            center(g, "zeichnen oder einkleben", x, w, yy + h / 2 + 9);
        }
    }

    private void paragraph(final String label, String text) {
        List<String> alle = wrap(text, F.reg(8.5f), W - 2 * M);
        // lange Texte seitenweise teilen: jeder Block nutzt den Platz, der auf der Seite noch frei ist
        int i = 0;
        boolean erster = true;
        do {
            float kopf = erster && label != null ? 12 : 0;
            int passt = (int) Math.floor((BOTTOM - y - 4 - kopf) / 11);
            if (passt < 3 && y > M + 1) { newPage(); continue; }
            int n = Math.max(1, Math.min(passt, alle.size() - i));
            paragraphBlock(erster ? label : null, alle.subList(Math.min(i, alle.size()), Math.min(i + n, alle.size())));
            i += n;
            erster = false;
        } while (i < alle.size());
    }

    private void paragraphBlock(final String label, final List<String> lines) {
        atomic((label == null ? 0 : 12) + lines.size() * 11 + 4, (g, x, yy, w, h) -> {
            float ly = yy;
            if (label != null) {
                g.setFont(F.bold(7));
                g.setColor(MUTED);
                g.drawString(label.toUpperCase(Locale.GERMAN), x, ly + 8);
                ly += 12;
            }
            g.setFont(F.reg(8.5f));
            g.setColor(INK);
            for (String l : lines) { ly += 11; g.drawString(l, x, ly - 2); }
        });
    }

    // ================================================================== Bausteine

    private void tile(Graphics2D g, float x, float yy, float w, float h, String k, String v, String sub, boolean primary) {
        g.setColor(primary ? FILL : WHITE);
        g.fill(new RoundRectangle2D.Float(x, yy, w, h, 8, 8));
        if (!primary) {
            g.setColor(LINE);
            g.draw(new RoundRectangle2D.Float(x, yy, w, h, 8, 8));
        }
        if (primary) {
            g.setColor(ACCENT);
            g.fill(new Rectangle2D.Float(x + 8, yy + h - 2.5f, w - 16, 2.5f));
        }
        g.setFont(F.bold(6.8f));
        g.setColor(MUTED);
        center(g, k, x, w, yy + 10);
        g.setFont(F.bold(primary ? 17 : 14));
        g.setColor(INK);
        center(g, v, x, w, yy + (primary ? 30 : 27));
        g.setFont(F.reg(5.4f));
        g.setColor(MUTED);
        String s = fit(g, sub, w - 4);
        center(g, s, x, w, yy + h - (primary ? 7 : 6));
    }

    private void heading(final String title, float minFollow) {
        if (y > M + 1 && y + 24 + minFollow > BOTTOM) newPage();
        place(24, (g, x, yy, w, h) -> {
            g.setFont(F.bold(11.5f));
            g.setColor(INK);
            g.drawString(title, x, yy + 15);
            float tw = g.getFontMetrics().stringWidth(title);
            g.setColor(LINE);
            g.draw(new Line2D.Float(x + tw + 8, yy + 11, x + w, yy + 11));
        });
    }

    private void subheading(final String title) { subheading(title, 30); }

    /** Zwischenüberschrift, die nie allein am Seitenende stehen bleibt. */
    private void subheading(final String title, float minFollow) {
        if (y + 14 + minFollow > BOTTOM) newPage();
        place(14, (g, x, yy, w, h) -> {
            g.setFont(F.bold(7));
            g.setColor(MUTED);
            g.drawString(title.toUpperCase(Locale.GERMAN), x, yy + 9);
        });
    }

    private void table(final String[] head, final float[] rel, final int[] align, List<String[]> rows, final boolean writeLines) {
        table(head, rel, align, rows, writeLines, null);
    }

    private void table(final String[] head, final float[] rel, final int[] align, List<String[]> rows, final boolean writeLines,
                       final List<String> notes) {
        table(head, rel, align, rows, writeLines, notes, null);
    }

    /**
     * @param notes optional je Zeile ein Notiztext, der unter der Zeile über die volle Breite steht
     * @param kennz optional je Zeile Kennzeichen (Plaketten) hinter dem Namen in der ersten Spalte
     */
    private void table(final String[] head, final float[] rel, final int[] align, List<String[]> rows, final boolean writeLines,
                       final List<String> notes, final List<List<Kennung>> kennz) {
        table(head, rel, align, rows, writeLines, notes, kennz, 0);
    }

    /** @param anhang Höhe eines Blocks direkt nach der Tabelle (z. B. Legende), der mit der letzten Zeile zusammenbleibt */
    private void table(final String[] head, final float[] rel, final int[] align, List<String[]> rows, final boolean writeLines,
                       final List<String> notes, final List<List<Kennung>> kennz, float anhang) {
        final float rowH = writeLines ? 17 : 13.5f, headH = 14;
        float sum = 0;
        for (float r : rel) sum += r;
        final float[] cw = new float[rel.length];
        for (int i = 0; i < rel.length; i++) cw[i] = rel[i] / sum;
        final Painter header = (g, x, yy, w, h) -> {
            g.setColor(ACCENT_SOFT);
            g.fill(new RoundRectangle2D.Float(x, yy, w, h, 6, 6));
            g.setFont(F.bold(6.5f));
            g.setColor(ACCENT.darker());
            float cx = x;
            for (int i = 0; i < head.length; i++) {
                float c = cw[i] * w;
                cell(g, head[i].toUpperCase(Locale.GERMAN), cx, c, yy + 9.8f, align[i]);
                cx += c;
            }
        };
        if (y + headH + rowH > BOTTOM) newPage();
        place(headH, header);
        int i = 0;
        final float lineH = 9.5f;
        final float noteH = 8.6f;
        int ri = 0;
        for (final String[] r : rows) {
            // Erste Spalte (Name) bei Bedarf umbrechen statt abschneiden
            final List<String> nameLines = align[0] == 0 ? wrap(r[0], F.bold(8), cw[0] * (W - 2 * M) - 8) : new ArrayList<String>();
            final List<Kennung> kz = kennz != null && ri < kennz.size() && kennz.get(ri) != null
                    ? kennz.get(ri) : Collections.<Kennung>emptyList();
            if (!kz.isEmpty() && nameLines.isEmpty()) nameLines.add(r[0]);
            // Kennzeichen hinter dem Namen; passen sie nicht mehr, in eine eigene Zeile darunter
            final boolean kzUnten = !kz.isEmpty() && mg.getFontMetrics(F.bold(8)).stringWidth(nameLines.get(nameLines.size() - 1))
                    + 4 + kBreite(kz) > cw[0] * (W - 2 * M) - 8;
            final int n = Math.max(1, nameLines.size()) + (kzUnten ? 1 : 0);
            final boolean selbst = n > 1 || !kz.isEmpty();
            String note = notes != null && ri < notes.size() ? notes.get(ri) : null;
            ri++;
            final List<String> noteLines = note == null || note.isEmpty()
                    ? Collections.<String>emptyList() : wrap(note, F.reg(7), W - 2 * M - 20);
            final float rh = rowH + (n - 1) * lineH + (noteLines.isEmpty() ? 0 : noteLines.size() * noteH + 2);
            // letzte Zeile: nachfolgende Legende samt Abstand nach der Tabelle mitnehmen
            float bleibt = ri == rows.size() && anhang > 0 ? anhang + 4 : 0;
            if (y + rh + bleibt > BOTTOM) { newPage(); place(headH, header); }
            final boolean zebra = (i++ % 2) == 1;
            place(rh, (g, x, yy, w, h) -> {
                if (zebra && !writeLines) { g.setColor(FILL); g.fill(new Rectangle2D.Float(x, yy, w, h)); }
                float cx = x;
                float base = yy + rowH - 4;   // alle Werte auf der ersten Zeile
                for (int c = 0; c < r.length; c++) {
                    float cwid = cw[c] * w;
                    g.setFont(c == 0 ? F.bold(8) : F.reg(8));
                    g.setColor(c == 0 || align[c] == 2 ? INK : new Color(0x444444));
                    if (c == 0 && selbst) {
                        float ly = base;
                        for (String l : nameLines) { g.drawString(l, cx + 4, ly); ly += lineH; }
                        if (!kz.isEmpty()) {
                            float lw = g.getFontMetrics().stringWidth(nameLines.get(nameLines.size() - 1));
                            if (kzUnten) kZeichnen(g, cx + 10, ly - 1, kz);
                            else kZeichnen(g, cx + 4 + lw + 4, ly - lineH, kz);
                        }
                    } else {
                        cell(g, r[c], cx, cwid, base, align[c]);
                    }
                    if (writeLines && c > 0 && r[c].isEmpty()) {
                        g.setColor(LINE);
                        g.draw(new Line2D.Float(cx + 4, yy + h - 3, cx + cwid - 4, yy + h - 3));
                    }
                    cx += cwid;
                }
                if (!noteLines.isEmpty()) {
                    float ny = base + (n - 1) * lineH + noteH + 1;
                    g.setColor(ACCENT);
                    g.fill(new Rectangle2D.Float(x + 6, ny - 6.5f, 1.5f, noteLines.size() * noteH - 1));
                    g.setFont(F.reg(7));
                    g.setColor(MUTED);
                    for (String l : noteLines) { g.drawString(l, x + 12, ny); ny += noteH; }
                }
                if (writeLines) {
                    g.setColor(LINE);
                    g.draw(new Line2D.Float(x, yy + h, x + w, yy + h));
                }
            });
        }
        space(4);
    }

    private static void cell(Graphics2D g, String s, float x, float w, float base, int align) {
        s = fit(g, s, w - 6);
        int sw = g.getFontMetrics().stringWidth(s);
        float tx = align == 0 ? x + 4 : align == 1 ? x + (w - sw) / 2 : x + w - 4 - sw;
        g.drawString(s, tx, base);
    }

    // ================================================================== Spalten-Fluss

    private static final class ColItem {
        static final ColItem BREAK = new ColItem(0, false, null);
        final float h; final boolean keepNext; final Painter p;
        /** Füllzeile (z. B. leere Inventarzeile): nur, solange Platz ist – löst nie eine neue Seite aus */
        boolean fuell;
        /** Platz, der unter Füllzeilen frei bleiben muss (z. B. für die Gesamtgewicht-Zeile) */
        float reserve;
        ColItem(float h, boolean keepNext, Painter p) { this.h = h; this.keepNext = keepNext; this.p = p; }
        ColItem alsFuell(float reserve) { fuell = true; this.reserve = reserve; return this; }
        /** Abschlusszeile (z. B. Gesamtgewicht): rutscht nie allein auf eine neue Seite, sondern nimmt den
         *  letzten Eintrag davor mit */
        boolean abschluss;
        ColItem alsAbschluss() { abschluss = true; return this; }
    }

    private static float colWidth() { return (W - 2 * M - 16) / 2f; }

    private void columns(List<ColItem> items) { columns(items, null); }

    /** Fortsetzungs-Überschrift auf Folgeseiten, z. B. „Sonderfertigkeiten (Fortsetzung)“. */
    private ColItem fortsetzung(final String titel) {
        return new ColItem(18, false, (g, x, yy, w, h) -> {
            g.setFont(F.bold(8));
            g.setColor(MUTED);
            g.drawString(titel + " (Fortsetzung)", x, yy + 11);
        });
    }

    private void columns(List<ColItem> items, String titel) {
        float colW = colWidth(), gap = 16;
        int col = 0;
        float top = y, maxY = y;
        // Passt alles auf die aktuelle Seite, werden die Spalten ausgeglichen gefüllt
        boolean hasBreak = items.contains(ColItem.BREAK);
        float total = 0;
        for (ColItem it : items) total += it.h;
        float target = (!hasBreak && total / 2 + 30 <= BOTTOM - y) ? total / 2 : Float.MAX_VALUE;
        boolean fuellAus = false;
        int ersteFuellWeg = -1;     // erste Füllzeile, die auf dieser Seite keinen Platz mehr fand
        float linksUnten = 0;       // Unterkante der linken Spalte (für die bündige Abschlusszeile)
        ColItem letzter = null;
        for (int i = 0; i < items.size(); i++) {
            ColItem it = items.get(i);
            if (it == ColItem.BREAK) {
                if (col == 0) { col = 1; maxY = Math.max(maxY, y); linksUnten = y; y = top; }
                continue;
            }
            float need = it.h;
            if (it.keepNext && i + 1 < items.size() && items.get(i + 1) != ColItem.BREAK) need += items.get(i + 1).h;
            // an den Vorgänger gebunden (z. B. Legende an die letzte Zeile): beim Ausgleichen nicht davon trennen
            boolean gebunden = i > 0 && items.get(i - 1).keepNext && items.get(i - 1) != ColItem.BREAK;
            if (col == 0 && !gebunden && !it.abschluss && target != Float.MAX_VALUE && y - top + need / 2 > target && y > top) {
                maxY = Math.max(maxY, y);
                linksUnten = y;
                col = 1;
                y = top;
            }
            // Füllzeilen nicht auf eine neue Seite tragen: was nicht mehr passt, entfällt
            if (it.fuell && (fuellAus || col == 1 && y + need + it.reserve > BOTTOM)) {
                if (!fuellAus) ersteFuellWeg = i;
                fuellAus = true;
                continue;
            }
            if (y + need > BOTTOM) {
                maxY = Math.max(maxY, y);
                if (col == 0) { col = 1; linksUnten = y; y = top; }
                else {
                    // Abschlusszeile nicht allein: den letzten echten Eintrag dieser Seite mitnehmen
                    Placed mit = null;
                    float mitH = 0;
                    if (it.abschluss && letzter != null && !letzter.fuell) {
                        List<Placed> seite = pages.get(pages.size() - 1);
                        mit = seite.remove(seite.size() - 1);
                        mitH = letzter.h;
                    }
                    newPage();
                    linksUnten = 0;
                    col = 0;
                    if (titel != null) place(18, fortsetzung(titel).p);
                    top = y; maxY = y;
                    // passt der Rest auf diese Seite, auch hier beide Spalten ausgeglichen füllen
                    float rest = 0;
                    boolean restBreak = false;
                    for (int k = i; k < items.size(); k++) {
                        if (items.get(k) == ColItem.BREAK) restBreak = true; else rest += items.get(k).h;
                    }
                    target = (!restBreak && rest / 2 + 30 <= BOTTOM - y) ? rest / 2 : Float.MAX_VALUE;
                    if (mit != null) {
                        pages.get(pages.size() - 1).add(new Placed(mit.p, M, y, colW, mitH));
                        y += mitH;
                        maxY = Math.max(maxY, y);
                        // die auf der vorigen Seite weggefallenen Leerzeilen kommen hier wieder (vor die Abschlusszeile)
                        if (ersteFuellWeg >= 0) {
                            float rest2 = mitH;
                            for (int k = ersteFuellWeg; k < items.size(); k++) rest2 += items.get(k).h;
                            target = rest2 / 2 + 30 <= BOTTOM - top ? rest2 / 2 : Float.MAX_VALUE;
                            i = ersteFuellWeg - 1;
                            ersteFuellWeg = -1;
                            fuellAus = false;
                            letzter = null;
                            continue;
                        }
                    }
                }
            }
            // Abschlusszeile rechts bündig mit der letzten Zeile der linken Spalte
            if (it.abschluss && col == 1 && linksUnten - it.h > y && linksUnten <= BOTTOM) y = linksUnten - it.h;
            pages.get(pages.size() - 1).add(new Placed(it.p, M + col * (colW + gap), y, colW, it.h));
            letzter = it;
            y += it.h;
            maxY = Math.max(maxY, y);
        }
        y = maxY;
    }

    // ================================================================== Seitenverwaltung

    private void newPage() {
        pages.add(new ArrayList<>());
        y = M;
    }

    private void space(float h) { y = Math.min(y + h, BOTTOM); }

    private void atomic(float h, Painter p) {
        if (y > M + 1 && y + h > BOTTOM) newPage();
        place(h, p);
    }

    private void place(float h, Painter p) {
        pages.get(pages.size() - 1).add(new Placed(p, M, y, W - 2 * M, h));
        y += h;
    }

    /** Zeichnet Seite i in Seitenkoordinaten (1 Einheit = 1 pt). */
    public void paintPage(Graphics2D g, int index) {
        hints(g);
        g.setColor(WHITE);
        g.fill(new Rectangle2D.Float(0, 0, W, H));
        for (Placed p : pages.get(index)) p.p.paint(g, p.x, p.y, p.w, p.h);
        // Fußzeile
        g.setFont(F.reg(6.5f));
        g.setColor(MUTED);
        float fy = H - M + 6;
        g.setColor(LINE);
        g.draw(new Line2D.Float(M, fy - 9, W - M, fy - 9));
        g.setColor(MUTED);
        g.drawString((d.name.isEmpty() ? "" : d.name + "  ·  ") + "Heldenbogen  ·  " + datum, M, fy);
        right(g, "Seite " + (index + 1) + " / " + pages.size(), W - M, fy);
    }

    // ================================================================== Druck-Schnittstellen

    @Override public int getNumberOfPages() { return pages.size(); }

    @Override public PageFormat getPageFormat(int pageIndex) {
        PageFormat pf = new PageFormat();
        Paper p = new Paper();
        p.setSize(W, H);
        p.setImageableArea(0, 0, W, H);
        pf.setPaper(p);
        pf.setOrientation(PageFormat.PORTRAIT);
        return pf;
    }

    @Override public Printable getPrintable(int pageIndex) { return this; }

    @Override public int print(Graphics graphics, PageFormat pf, int pageIndex) {
        if (pageIndex >= pages.size()) return NO_SUCH_PAGE;
        Graphics2D g = (Graphics2D) graphics.create();
        paintPage(g, pageIndex);
        g.dispose();
        return PAGE_EXISTS;
    }

    // ================================================================== Text-Helfer

    static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private List<String> wrap(String text, Font f, float width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return out;
        FontMetrics fm = mg.getFontMetrics(f);
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.trim().split("\\s+")) {
                // einzelnes Wort länger als die Zeile: hart umbrechen
                while (fm.stringWidth(word) > width && word.length() > 1) {
                    int cut = word.length() - 1;
                    while (cut > 1 && fm.stringWidth(word.substring(0, cut) + "-") > width) cut--;
                    if (line.length() > 0) { out.add(line.toString()); line = new StringBuilder(); }
                    out.add(word.substring(0, cut) + "-");
                    word = word.substring(cut);
                }
                String cand = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(cand) > width && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else line = new StringBuilder(cand);
            }
            if (line.length() > 0) out.add(line.toString());
        }
        return out;
    }

    static String fit(Graphics2D g, String s, float w) {
        if (s == null) return "";
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= w) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    static void drawFit(Graphics2D g, String s, float x, float base, float w) { g.drawString(fit(g, s, w), x, base); }

    static void right(Graphics2D g, String s, float rx, float base) {
        g.drawString(s, rx - g.getFontMetrics().stringWidth(s), base);
    }

    static void center(Graphics2D g, String s, float x, float w, float base) {
        if (s == null) return;
        g.drawString(s, x + (w - g.getFontMetrics().stringWidth(s)) / 2f, base);
    }

    static String signed(int v) { return v > 0 ? "+" + v : String.valueOf(v); }

    static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    /** Schriften: moderne Systemschrift, mit Rückfall auf die Java-Standardschrift. */
    static final class F {
        private static String family;
        private static final Map<String, Font> cache = new HashMap<>();

        static synchronized String family() {
            if (family == null) {
                Set<String> av = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
                for (String f : new String[]{"Segoe UI", "Inter", "Helvetica Neue", "Arial", "Liberation Sans", "DejaVu Sans"})
                    if (av.contains(f)) { family = f; break; }
                if (family == null) family = Font.SANS_SERIF;
            }
            return family;
        }

        static synchronized Font get(int style, float size) {
            String k = style + ":" + size;
            Font f = cache.get(k);
            if (f == null) {
                f = new Font(family(), style, 10).deriveFont(size);
                cache.put(k, f);
            }
            return f;
        }

        static Font reg(float s) { return get(Font.PLAIN, s); }
        static Font bold(float s) { return get(Font.BOLD, s); }
    }
}
