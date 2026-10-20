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
        public boolean beschreibungen = true;
        public boolean metatalente = true;
        public boolean notizseite = true;
    }

    private interface Painter { void paint(Graphics2D g, float x, float y, float w, float h); }

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
        zauber();
        inventar();
        if (opt.notizseite) notizen();
    }

    private void header() {
        final String sub = join(" · ", d.rasse, d.kultur, d.profession);
        atomic(56, (g, x, yy, w, h) -> {
            g.setColor(INK);
            g.setFont(F.bold(21));
            drawFit(g, d.name, x, yy + 22, w - 120);
            g.setFont(F.reg(9.5f));
            g.setColor(MUTED);
            drawFit(g, sub, x, yy + 38, w - 120);
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
        atomic(48, (g, x, yy, w, h) -> {
            float cw = w / 5f;
            for (int i = 0; i < f.length; i++) {
                float cx = x + (i % 5) * cw, cy = yy + 6 + (i / 5) * 22;
                g.setFont(F.bold(6.5f));
                g.setColor(MUTED);
                g.drawString(f[i][0].toUpperCase(Locale.GERMAN), cx, cy + 6);
                g.setFont(F.reg(9.5f));
                g.setColor(INK);
                drawFit(g, f[i][1].isEmpty() ? "–" : f[i][1], cx, cy + 17, cw - 6);
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
        add(b, "FK", "fernkampf-basis", "Fernkampf-Basis");
        add(b, "GS", "geschwindigkeit", "Geschwindigkeit");
        if (!d.ausweichen.isEmpty()) b.add(new String[]{"AW", d.ausweichen, "Ausweichen"});
        if (!d.wundschwelle.isEmpty()) b.add(new String[]{"WS", d.wundschwelle, "Wundschwelle"});
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
        if (d.magisch || d.akt("astralenergie") > 0) energieRow(rows, "Astralenergie", "astralenergie");
        if (d.geweiht || d.akt("karmaenergie") > 0) energieRow(rows, "Karmaenergie", "karmaenergie");
        heading("Zustand", 40);
        table(new String[]{"", "Max", "½", "⅓", "¼", "Aktuell", "Notizen"},
                new float[]{1.5f, .6f, .5f, .5f, .5f, 1.4f, 3.2f},
                new int[]{0, 1, 1, 1, 1, 1, 0}, rows, true);
        final String reg = join("   ·   ",
                d.wundschwelle.isEmpty() ? "" : "Wundschwelle " + d.wundschwelle,
                d.leReg.isEmpty() ? "" : "LE-Regeneration " + d.leReg,
                d.aspReg.isEmpty() ? "" : "AsP-Regeneration " + d.aspReg);
        final boolean zonenWunden = !d.zonen.isEmpty();
        atomic(22, (g, x, yy, w, h) -> {
            float tx = x;
            if (!zonenWunden) {
                g.setFont(F.bold(8));
                g.setColor(INK);
                g.drawString("Wunden", x, yy + 14);
                for (int i = 0; i < 8; i++) {
                    g.setColor(LINE.darker());
                    g.draw(new RoundRectangle2D.Float(x + 40 + i * 14, yy + 5, 10, 10, 3, 3));
                }
                tx = x + 40 + 8 * 14 + 12;
            }
            g.setFont(F.reg(8));
            g.setColor(MUTED);
            g.drawString(reg + (zonenWunden ? "   ·   Wunden je Trefferzone siehe Kampf" : ""), tx, yy + 14);
        });
        space(6);
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
            for (SheetData.Nahkampfwaffe n : d.nahkampf)
                r.add(new String[]{n.name, n.talent, n.dk, n.tp, n.tpkk, n.ini, n.wm, n.at, n.pa, n.bf});
            subheading("Nahkampfwaffen");
            table(new String[]{"Waffe", "Talent", "DK", "TP", "TP/KK", "INI", "WM", "AT", "PA", "BF"},
                    new float[]{2.4f, 2f, .6f, .9f, .8f, .5f, .8f, .6f, .6f, .5f},
                    new int[]{0, 0, 1, 1, 1, 1, 1, 2, 2, 1}, r, false);
        }
        if (!d.fernkampf.isEmpty()) {
            List<String[]> r = new ArrayList<>();
            for (SheetData.Fernkampfwaffe f : d.fernkampf)
                r.add(new String[]{f.name, f.talent, f.tp, f.reichweite, f.tpmod, f.laden, f.fk});
            subheading("Fernkampfwaffen");
            table(new String[]{"Waffe", "Talent", "TP", "Reichweite", "TP-Mod.", "Laden", "FK"},
                    new float[]{2.2f, 1.8f, .9f, 1.8f, 1.5f, .6f, .6f},
                    new int[]{0, 0, 1, 1, 1, 1, 2}, r, false);
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
        if (!d.zonen.isEmpty()) { space(6); trefferzonenUndWunden(trefferLinks); }
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
        if (d.zonen.isEmpty() && d.rsGesamt.isEmpty() && d.ruestungen.isEmpty()) return;
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
            if (rs != null) z.add(new String[]{t[0], rs, t[1]});
        }
        final int zonen = z.size();
        if (!d.rsGesamt.isEmpty()) z.add(new String[]{"RS", d.rsGesamt, "gesamt"});
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
        final int be = SheetData.num(d.beGesamt);
        final boolean hatBe = !d.beGesamt.isEmpty();
        final int basis = d.akt("initiative");
        final List<String[]> rows = new ArrayList<>();   // {Waffe, Rechnung, Ergebnis}
        for (SheetData.Nahkampfwaffe n : d.nahkampf) {
            int mod = SheetData.num(n.ini);
            rows.add(new String[]{n.name, basis + " − " + be + " " + vz(mod), (basis - be + mod) + " + 1W6"});
        }
        rows.add(new String[]{"Waffenlos", basis + " − " + be + " + 0", (basis - be) + " + 1W6"});
        final List<String> schild = new ArrayList<>();
        for (SheetData.Schild sc : d.schilde) {
            int mod = SheetData.num(sc.ini);
            if (mod != 0) schild.add("mit " + sc.name + " zusätzlich " + vz(mod).replace(" ", ""));
        }
        float h = 22 + rows.size() * 12.5f + (schild.isEmpty() ? 0 : 11 * schild.size()) + 2;
        space(4);
        atomic(h, (g, x, yy, w, hh) -> {
            g.setColor(FILL);
            g.fill(new RoundRectangle2D.Float(x, yy, w, hh, 8, 8));
            g.setFont(F.bold(6.8f));
            g.setColor(MUTED);
            g.drawString("INITIATIVE IM KAMPF", x + 10, yy + 14);
            g.setFont(F.reg(7.2f));
            g.drawString("INI-Basis − BE + Waffen-/Schild-Mod. + 1W6", x + 104, yy + 14);
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
            for (String[] r : rows) {
                g.setFont(F.bold(8));
                g.setColor(INK);
                drawFit(g, r[0], c1, ly, c2 - c1 - 6);
                g.setFont(F.reg(7.6f));
                g.setColor(MUTED);
                g.drawString(r[1], c2, ly);
                g.setFont(F.bold(8.5f));
                g.setColor(INK);
                right(g, "= " + r[2], c3, ly);
                ly += 12.5f;
            }
            g.setFont(F.reg(7));
            g.setColor(ACCENT);
            for (String sline : schild) { g.drawString(sline, c1, ly); ly += 11; }
        });
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
        for (SheetData.Eintrag e : d.sf) items.add(entryItem(e.name, colW, null));
        columns(items, "Sonderfertigkeiten");
        space(6);
    }

    private void vorteile() {
        if (d.vorteile.isEmpty()) return;
        heading("Vorteile & Nachteile", 30);
        List<ColItem> items = new ArrayList<>();
        float colW = colWidth();
        items.add(labelItem("Vorteile"));
        for (SheetData.Eintrag e : d.vorteile) if (!e.nachteil) items.add(entryItem(e.name, colW, null));
        items.add(ColItem.BREAK);
        items.add(labelItem("Nachteile"));
        for (SheetData.Eintrag e : d.vorteile) if (e.nachteil) items.add(entryItem(e.name, colW, null));
        columns(items, "Vorteile & Nachteile");
    }

    /** Eigener Text oder Kurzbeschreibung zu einem Namen, sonst "". */
    private String beschreibung(String name) {
        if (db == null) return "";
        TooltipDB.Match m = db.find(name);
        if (m == null) return "";
        String own = db.getUserText(m.key);
        if (own == null && m.entry != null) own = db.getUserText(m.entry.name);
        String t = own != null ? own : (m.entry != null ? m.entry.text : "");
        return t == null ? "" : t.trim();
    }

    private ColItem entryItem(final String name, float colW, String extra) {
        String desc = opt.beschreibungen ? beschreibung(name) : "";
        final List<String> lines = desc.isEmpty() ? Collections.<String>emptyList() : wrap(desc, F.reg(7), colW - 10);
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
        heading("Talente", 40);
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
                items.add(new ColItem(12.5f, false, (g, x, yy, w, h) -> talentRow(g, t, kampf, sprache, zebra, x, yy, w, h)));
            }
            items.add(new ColItem(6, false, (g, x, yy, w, h) -> { }));
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

    private void talentRow(Graphics2D g, SheetData.Talent t, boolean kampf, boolean sprache, boolean zebra,
                           float x, float yy, float w, float h) {
        if (zebra) {
            g.setColor(FILL);
            g.fill(new Rectangle2D.Float(x, yy, w, h));
        }
        float[] c = talentCols(w);
        for (int i = 0; i < c.length; i += 2) c[i] += x;
        float base = yy + h - 3.6f;
        g.setFont(F.reg(8));
        g.setColor(INK);
        String name = t.name;
        float nameW = g.getFontMetrics().stringWidth(name);
        drawFit(g, name, c[0] + 3, base, c[1] - 4);
        if (!t.spez.isEmpty() && nameW + 12 < c[1]) {
            g.setFont(F.reg(6.5f));
            g.setColor(ACCENT);
            drawFit(g, t.spez, c[0] + 3 + nameW + 4, base, c[1] - nameW - 10);
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

    private void zauber() {
        if (d.zauber.isEmpty()) return;
        space(4);
        heading("Zauber", 40);
        List<String[]> rows = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (SheetData.Zauber z : d.zauber) {
            rows.add(new String[]{z.name, z.probe, z.probenwerte, z.merkmale, z.rep, z.spalte, String.valueOf(z.wert)});
            // Anmerkung aus der Helden-Software + eigener Text (wie bei SF und Vor-/Nachteilen)
            List<String> n = new ArrayList<>();
            if (!z.anmerkung.isEmpty()) n.add(z.anmerkung);
            String own = opt.beschreibungen ? beschreibung(z.name) : "";
            if (!own.isEmpty() && !own.equals(z.anmerkung)) n.add(own);
            notes.add(String.join(" – ", n));
        }
        table(new String[]{"Zauber", "Probe", "Werte", "Merkmale", "Rep.", "Sp.", "ZfW"},
                new float[]{2.7f, 1f, 1f, 1.7f, 1.45f, .4f, .5f}, new int[]{0, 1, 1, 0, 1, 1, 2}, rows, false, notes);
    }

    private void inventar() {
        space(4);
        heading("Inventar", 40);
        List<ColItem> items = new ArrayList<>();
        int i = 0;
        for (final SheetData.Gegenstand gg : d.inventar) {
            final boolean zebra = (i++ % 2) == 1;
            items.add(new ColItem(12.5f, false, (g, x, yy, w, h) -> {
                if (zebra) { g.setColor(FILL); g.fill(new Rectangle2D.Float(x, yy, w, h)); }
                float base = yy + h - 3.6f;
                g.setFont(F.reg(8));
                g.setColor(INK);
                drawFit(g, gg.name, x + 3, base, w - (gg.angelegt ? 132 : 98));
                g.setFont(F.reg(7.2f));
                g.setColor(gg.angelegt ? ACCENT : MUTED);
                if (gg.angelegt) g.drawString("angelegt", x + w - 126, base);
                g.setColor(MUTED);
                right(g, "1".equals(gg.anzahl) ? "" : gg.anzahl + "×", x + w - 66, base);
                right(g, gg.gewichtStein.isEmpty() ? "" : gg.gewichtStein + " St. (" + gg.gewichtStein + " kg)", x + w - 3, base);
            }));
        }
        columns(items, "Inventar");
        double unzen = 0;
        for (SheetData.Gegenstand gg : d.inventar) unzen += gg.unzen;
        if (unzen > 0) {
            final String st = SheetData.fmt(unzen / 40.0);
            atomic(13, (g, x, yy, w, h) -> {
                g.setFont(F.reg(7.5f));
                g.setColor(MUTED);
                right(g, "Gesamtgewicht Inventar: " + st + " Stein (" + st + " kg)  ·  1 Stein = 40 Unzen = 1 kg", x + w - 3, yy + 9.5f);
            });
        }
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
        atomic(fremd.isEmpty() ? 50 : 61, (g, x, yy, w, h) -> {
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
            if (!fremd.isEmpty()) {
                g.setColor(INK);
                drawFit(g, "Andere Währungen (nicht im Gesamtwert): " + fremd, x + 72, yy + 54, w - 82);
            }
        });
    }

    private void notizen() {
        space(8);
        heading("Notizen", 80);
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

    /** Umrandetes Schreibfeld für Persönlichkeit, Ziele, Eigenheiten usw. */
    private void charakterbeschreibung() {
        final int zeilen = 8;
        final float lineH = 17;
        final float h = 26 + zeilen * lineH + 6;
        if (y + h > BOTTOM) newPage();
        atomic(h, (g, x, yy, w, hh) -> {
            g.setColor(WHITE);
            g.fill(new RoundRectangle2D.Float(x, yy, w, hh, 8, 8));
            g.setColor(LINE);
            g.draw(new RoundRectangle2D.Float(x, yy, w, hh, 8, 8));
            g.setColor(ACCENT);
            g.fill(new Rectangle2D.Float(x + 10, yy + 7, 2.5f, 10));
            g.setFont(F.bold(9));
            g.setColor(INK);
            g.drawString("Charakterbeschreibung", x + 18, yy + 15.5f);
            g.setFont(F.reg(7));
            g.setColor(MUTED);
            right(g, "Persönlichkeit · Ziele & Motive · Ängste · Eigenheiten · Beziehungen", x + w - 10, yy + 15.5f);
            g.setColor(LINE);
            for (int i = 1; i <= zeilen; i++) {
                float ly = yy + 24 + i * lineH;
                g.draw(new Line2D.Float(x + 12, ly, x + w - 12, ly));
            }
        });
    }

    private void paragraph(final String label, String text) {
        final List<String> lines = wrap(text, F.reg(8.5f), W - 2 * M);
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

    /** @param notes optional je Zeile ein Notiztext, der unter der Zeile über die volle Breite steht */
    private void table(final String[] head, final float[] rel, final int[] align, List<String[]> rows, final boolean writeLines,
                       final List<String> notes) {
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
            final int n = Math.max(1, nameLines.size());
            String note = notes != null && ri < notes.size() ? notes.get(ri) : null;
            ri++;
            final List<String> noteLines = note == null || note.isEmpty()
                    ? Collections.<String>emptyList() : wrap(note, F.reg(7), W - 2 * M - 20);
            final float rh = rowH + (n - 1) * lineH + (noteLines.isEmpty() ? 0 : noteLines.size() * noteH + 2);
            if (y + rh > BOTTOM) { newPage(); place(headH, header); }
            final boolean zebra = (i++ % 2) == 1;
            place(rh, (g, x, yy, w, h) -> {
                if (zebra && !writeLines) { g.setColor(FILL); g.fill(new Rectangle2D.Float(x, yy, w, h)); }
                float cx = x;
                float base = yy + rowH - 4;   // alle Werte auf der ersten Zeile
                for (int c = 0; c < r.length; c++) {
                    float cwid = cw[c] * w;
                    g.setFont(c == 0 ? F.bold(8) : F.reg(8));
                    g.setColor(c == 0 || align[c] == 2 ? INK : new Color(0x444444));
                    if (c == 0 && n > 1) {
                        float ly = base;
                        for (String l : nameLines) { g.drawString(l, cx + 4, ly); ly += lineH; }
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
        ColItem(float h, boolean keepNext, Painter p) { this.h = h; this.keepNext = keepNext; this.p = p; }
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
        for (int i = 0; i < items.size(); i++) {
            ColItem it = items.get(i);
            if (it == ColItem.BREAK) {
                if (col == 0) { col = 1; maxY = Math.max(maxY, y); y = top; }
                continue;
            }
            float need = it.h;
            if (it.keepNext && i + 1 < items.size() && items.get(i + 1) != ColItem.BREAK) need += items.get(i + 1).h;
            if (col == 0 && target != Float.MAX_VALUE && y - top + need / 2 > target && y > top) {
                maxY = Math.max(maxY, y);
                col = 1;
                y = top;
            }
            if (y + need > BOTTOM) {
                maxY = Math.max(maxY, y);
                if (col == 0) { col = 1; y = top; }
                else {
                    newPage();
                    col = 0;
                    if (titel != null) place(18, fortsetzung(titel).p);
                    top = y; maxY = y;
                }
            }
            pages.get(pages.size() - 1).add(new Placed(it.p, M + col * (colW + gap), y, colW, it.h));
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
        g.drawString(d.name + "  ·  Heldenbogen  ·  " + datum, M, fy);
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
