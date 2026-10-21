package de.modernhelden;

import de.modernhelden.print.SheetData;

import java.util.*;

/**
 * Angaben zu Gegenständen des Inventars (Gewicht, Wert, Kampf- und Rüstungswerte aus dem aktiven Kampfset).
 * Gemeinsam genutzt vom Reiter „Plugin: Modern-UI“ → Inventar und den Tooltips im Reiter „Inventar / Tiere“.
 */
final class InventarInfo {

    private InventarInfo() {}

    /** Gegenstand mit diesem Namen (erster Treffer) oder null. */
    static SheetData.Gegenstand finde(SheetData sd, String name) {
        if (sd == null || name == null) return null;
        String n = name.trim();
        for (SheetData.Gegenstand g : sd.inventar) if (g.name.equals(n)) return g;
        for (SheetData.Gegenstand g : sd.inventar) if (g.name.equalsIgnoreCase(n)) return g;
        return null;
    }

    /** Kampf- und Rüstungswerte eines Gegenstands aus dem aktiven Kampfset; füllt nebenbei die Tooltip-Angaben. */
    static String werte(SheetData sd, SheetData.Gegenstand g,
                                          Map<String, String> tip) {
        List<String> teile = new ArrayList<>();
        tip.put("Art", g.art.equalsIgnoreCase("sonst") ? "Sonstiges" : g.art);
        int anz = Math.max(1, num(g.anzahl));
        tip.put("Anzahl", String.valueOf(anz));
        if (g.einzelUnzen > 0) tip.put("Gewicht", (anz > 1 ? "je " + zahl(g.einzelUnzen / 40.0) + " Stein, zusammen " : "")
                + zahl(g.unzen / 40.0) + " Stein (" + zahl(g.unzen) + " Unzen)");
        if (g.einzelpreis > 0) tip.put("Wert", (anz > 1 ? "je " + preisText(g.einzelpreis) + ", zusammen " : "") + preisText(g.gesamtpreis));
        tip.put("Angelegt", g.angelegt ? "ja" : "nein");
        for (SheetData.Nahkampfwaffe n : sd.nahkampf) {
            if (!n.name.equals(g.name)) continue;
            String t = join(" · ", n.tp.isEmpty() ? "" : "TP " + n.tp, n.tpkk.isEmpty() ? "" : "TP/KK " + n.tpkk,
                    "AT " + n.at + " / PA " + n.pa, n.ini.isEmpty() ? "" : "INI " + n.ini, n.wm.isEmpty() ? "" : "WM " + n.wm,
                    n.bf.isEmpty() ? "" : "BF " + n.bf, n.dk.isEmpty() ? "" : "DK " + n.dk);
            teile.add(t);
            tip.put("Nahkampf", t + (n.talent.isEmpty() ? "" : " (" + n.talent + ")"));
            break;
        }
        for (SheetData.Fernkampfwaffe f : sd.fernkampf) {
            if (!f.name.equals(g.name)) continue;
            String t = join(" · ", f.tp.isEmpty() ? "" : "TP " + f.tp, f.fk.isEmpty() ? "" : "FK " + f.fk,
                    f.reichweite.isEmpty() ? "" : "Reichweite " + f.reichweite);
            teile.add(t);
            tip.put("Fernkampf", join(" · ", t, f.tpmod.isEmpty() ? "" : "TP-Mod. " + f.tpmod,
                    f.laden.isEmpty() ? "" : "Laden " + f.laden) + (f.talent.isEmpty() ? "" : " (" + f.talent + ")"));
            break;
        }
        for (SheetData.Schild s : sd.schilde) {
            if (!s.name.equals(g.name)) continue;
            String t = join(" · ", s.pa.isEmpty() ? "" : "PA " + s.pa, s.mod.isEmpty() ? "" : "WM " + s.mod,
                    s.ini.isEmpty() ? "" : "INI " + s.ini, s.bf.isEmpty() ? "" : "BF " + s.bf);
            teile.add(t);
            tip.put(s.typ.isEmpty() ? "Schild" : s.typ, t);
            break;
        }
        for (SheetData.Ruestung ru : sd.ruestungen) {
            if (!ru.name.equals(g.name)) continue;
            String t = join(" · ", ru.rs.isEmpty() || "*".equals(ru.rs) ? "" : "RS " + ru.rs,
                    ru.be.isEmpty() || "*".equals(ru.be) ? "" : "BE " + ru.be);
            List<String> zonen = new ArrayList<>();
            for (int i = 0; i < ru.zonen.length; i++)
                if (ru.zonen[i] != null && !ru.zonen[i].isEmpty() && !"0".equals(ru.zonen[i])) zonen.add(SheetData.ZONEN[i][1] + " " + ru.zonen[i]);
            String kurz = zonenKurz(ru.zonen);
            if (!kurz.isEmpty()) t = join(" · ", t, kurz);
            teile.add(t);
            tip.put("Rüstung", t);
            if (!zonen.isEmpty()) tip.put("Zonen", String.join(", ", zonen));
            break;
        }
        boolean kampfgut = g.art.toLowerCase(Locale.GERMAN).matches(".*(waffe|rüstung|schild).*");
        if (teile.isEmpty() && kampfgut) tip.put("Hinweis", "nicht im aktiven Kampfset");
        if (!g.details.isEmpty()) tip.put("Details", g.details);
        if (!g.quelle.isEmpty()) tip.put("Quelle", g.quelle);
        return teile.isEmpty() ? (kampfgut ? "nicht im aktiven Kampfset" : "") : String.join("  |  ", teile);
    }

    /** Geschützte Zonen kurz, z. B. „Kopf 3“ oder „Brust/Rücken/Bauch 4, Arme 3, Beine 2“. */
    static String zonenKurz(String[] z) {
        String[] n = new String[z.length];
        for (int i = 0; i < z.length; i++) n[i] = z[i] == null || z[i].isEmpty() || "0".equals(z[i]) ? null : z[i];
        List<String> teile = new ArrayList<>();
        // Rumpf (Brust, Rücken, Bauch) zusammenfassen, wenn gleich
        if (n[1] != null && n[1].equals(n[2]) && n[1].equals(n[3])) {
            if (n[0] != null) teile.add("Kopf " + n[0]);
            teile.add("Rumpf " + n[1]);
        } else {
            String[] namen = {"Kopf", "Brust", "Rücken", "Bauch"};
            for (int i = 0; i < 4; i++) if (n[i] != null) teile.add(namen[i] + " " + n[i]);
        }
        paar(teile, n[4], n[5], "Arme", "L. Arm", "R. Arm");
        paar(teile, n[6], n[7], "Beine", "L. Bein", "R. Bein");
        return teile.isEmpty() ? "" : "Zonen: " + String.join(", ", teile);
    }

    private static void paar(List<String> teile, String l, String r, String beide, String ln, String rn) {
        if (l != null && l.equals(r)) teile.add(beide + " " + l);
        else {
            if (l != null) teile.add(ln + " " + l);
            if (r != null) teile.add(rn + " " + r);
        }
    }

    static String stein(double unzen) { return zahl(unzen / 40.0); }

    /** Zahl mit bis zu zwei Nachkommastellen, deutsch formatiert. */
    static String zahl(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.005) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.GERMAN, "%.2f", v).replaceAll("0+$", "").replaceAll(",$", "");
    }

    /** Preis in Kreuzer → „4 D 5 S“, „7 H“ usw. (1 D = 10 S = 100 H = 1000 K). */
    static String preisText(long kreuzer) {
        long[] w = {1000, 100, 10, 1};
        StringBuilder sb = new StringBuilder();
        long rest = kreuzer;
        for (int i = 0; i < w.length; i++) {
            long n = rest / w[i];
            rest %= w[i];
            if (n > 0) sb.append(sb.length() > 0 ? " " : "").append(n).append(" ").append(Geld.KURZ[i]);
        }
        return sb.length() == 0 ? "0 K" : sb.toString();
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }
}
