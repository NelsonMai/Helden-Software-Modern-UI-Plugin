package de.modernhelden;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Angaben zu Gegenstandsvorlagen der Helden-Software (z. B. in der Liste „Gegenstände“ beim Hinzufügen im
 * Reiter „Inventar / Tiere“). Gelesen wird über die Plugin-Hüllen der Helden-Software selbst
 * ({@code helden.plugin.werteplugin…}, {@code helden.plugin.datenplugin…}) – nur lesend, ohne etwas zu ändern.
 * Version-spezifisch (Helden-Software 5.6.0); fehlt etwas, wird es einfach weggelassen.
 */
final class VorlagenInfo {

    private VorlagenInfo() {}

    static java.io.File debugLog;

    private static final String GEGENSTAND = "helden.plugin.datenplugin.impl.DatenPluginGegenstandImpl";
    private static final String NAHKAMPF = "helden.plugin.werteplugin.A.B";
    private static final String FERNKAMPF = "helden.plugin.werteplugin.A.O0oO";
    private static final String PARADE = "helden.plugin.werteplugin.A.class";
    private static final String SCHILD = "helden.plugin.werteplugin.A.float";
    private static final String RUESTUNG = "helden.plugin.werteplugin.A.H";

    /** Ist das Objekt eine Gegenstandsvorlage der Helden-Software? */
    static boolean istVorlage(Object o) {
        return o != null && konstruktor(GEGENSTAND, o) != null;
    }

    /** Name der Vorlage (wie in der Liste angezeigt) oder null. */
    static String name(Object f) {
        Object g = neu(GEGENSTAND, f);
        Object n = g == null ? null : rufe(g, "getName");
        return n == null ? String.valueOf(f) : n.toString();
    }

    /** Preis der Vorlage in Kreuzer, 0 wenn unbekannt. */
    static long preisKreuzer(Object f) {
        Object g = neu(GEGENSTAND, f);
        Object p = g == null ? null : rufe(g, "getPreis");
        return p instanceof Number ? Math.max(0, ((Number) p).longValue()) : 0;
    }

    /** Alle lesbaren Angaben für den Tooltip; null, wenn das Objekt keine Vorlage ist. */
    static Map<String, String> angaben(Object f) {
        Object g = neu(GEGENSTAND, f);
        if (g == null) return null;
        Map<String, String> x = new LinkedHashMap<>();
        List<String> arten = arten(f);
        if (!arten.isEmpty()) x.put("Art", String.join(", ", arten));
        Object gew = rufe(g, "getGewicht");
        if (gew instanceof Number && ((Number) gew).doubleValue() > 0) {
            double unzen = ((Number) gew).doubleValue();
            x.put("Gewicht", InventarInfo.zahl(unzen / 40.0) + " Stein (" + InventarInfo.zahl(unzen) + " Unzen)");
        }
        Object preis = rufe(g, "getPreis");
        if (preis instanceof Number && ((Number) preis).longValue() > 0)
            x.put("Wert", InventarInfo.preisText(((Number) preis).longValue()));

        String a = String.join(" ", arten).toLowerCase(Locale.GERMAN);
        if (a.contains("nahkampfwaffe")) {
            Object w = neu(NAHKAMPF, f);
            if (w != null) {
                log("NK " + f + " tp=" + Arrays.toString((int[]) rufe(w, "getTrefferpunkte"))
                        + " kk=" + Arrays.toString((int[]) rufe(w, "getKoerperkraftzuschlag")));
                x.put("Nahkampf", InventarInfo.verbinde(" · ",
                        tp((int[]) rufe(w, "getTrefferpunkte")),
                        kk((int[]) rufe(w, "getKoerperkraftzuschlag")),
                        wm(rufe(w, "getWmAT"), rufe(w, "getWmPA")),
                        dk((String[]) rufe(w, "getDistanzklasse"))));
            }
        }
        if (a.contains("fernkampfwaffe")) {
            Object w = neu(FERNKAMPF, f);
            if (w != null) {
                log("FK " + f + " tp=" + Arrays.toString((int[]) rufe(w, "getTrefferpunkte")));
                Object laden = rufe(w, "getLaden");
                x.put("Fernkampf", InventarInfo.verbinde(" · ",
                        tp((int[]) rufe(w, "getTrefferpunkte")),
                        liste("Reichweite ", (int[]) rufe(w, "getReichweite")),
                        liste("TP-Mod. ", (int[]) rufe(w, "getTrefferpunkteModifikation")),
                        laden instanceof Number ? "Laden " + laden : ""));
            }
        }
        if (a.contains("schild") || a.contains("parierwaffe") || a.contains("paradewaffe")) {
            Object w = neu(PARADE, f);
            if (w == null) w = neuMitDetail(SCHILD, f, "Schild");
            if (w != null) {
                Object bf = rufe(w, "getBruchfaktor"), ini = rufe(w, "getInitiativeModifikator");
                x.put(a.contains("schild") ? "Schild" : "Parierwaffe", InventarInfo.verbinde(" · ",
                        wm(rufe(w, "getWaffenModifikatorAT"), rufe(w, "getWaffenModifikatorPA")),
                        ini instanceof Number ? "INI " + vorzeichen(((Number) ini).intValue()) : "",
                        bf instanceof Number ? "BF " + bf : ""));
            }
        }
        if (a.contains("rüstung")) {
            Object r = neuMitDetail(RUESTUNG, f, "Rüstung");
            if (r != null) {
                Object rs = rufe(r, "getGesamtSchutz"), be = rufe(r, "getGesammtBehinderung");
                String[] z = new String[8];
                String[] m = {"getKopfSchutz", "getBrustSchutz", "getRueckenSchutz", "getBauchSchutz",
                        "getLinkerArmSchutz", "getRechterArmSchutz", "getLinkesBeinSchutz", "getRechtesBeinSchutz"};
                for (int i = 0; i < m.length; i++) {
                    Object v = rufe(r, m[i]);
                    z[i] = v == null ? "" : v.toString();
                }
                x.put("Rüstung", InventarInfo.verbinde(" · ",
                        rs instanceof Number ? "RS " + rs : "", be instanceof Number ? "BE " + be : "",
                        InventarInfo.zonenKurz(z)));
            }
        }
        Object q = quelle(f);
        if (q != null) x.put("Quelle", q.toString());
        return x;
    }

    // ------------------------------------------------------------------ Formatierung

    /** {Würfelanzahl, Würfelseiten, Zuschlag} → „2W+3“ (W6 als „W“). */
    private static String tp(int[] t) {
        if (t == null || t.length < 3) return "";
        String w = t[1] == 6 ? "W" : "W" + t[1];
        return "TP " + t[0] + w + (t[2] > 0 ? "+" + t[2] : t[2] < 0 ? String.valueOf(t[2]) : "");
    }

    private static String kk(int[] k) {
        return k == null || k.length < 2 || k[0] == 0 ? "" : "TP/KK " + k[0] + "/" + k[1];
    }

    private static String wm(Object at, Object pa) {
        return at instanceof Number && pa instanceof Number ? "WM " + at + "/" + pa : "";
    }

    private static String dk(String[] d) {
        if (d == null || d.length == 0) return "";
        // „Handgemenge“, „Nah“, „Stangenwaffe“, „Pike“ → H, N, S, P (wie im Heldenbogen)
        StringBuilder sb = new StringBuilder("DK ");
        for (String s : d) if (s != null && !s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0)));
        return sb.toString();
    }

    private static String liste(String titel, int[] v) {
        if (v == null || v.length == 0) return "";
        StringBuilder sb = new StringBuilder(titel);
        for (int i = 0; i < v.length; i++) sb.append(i > 0 ? "/" : "").append(v[i]);
        return sb.toString();
    }

    private static String vorzeichen(int v) { return v > 0 ? "+" + v : String.valueOf(v); }

    // ------------------------------------------------------------------ Reflexion

    /** Arten der Vorlage (Nahkampfwaffe, Rüstung, …): die einzige Sammlung von Aufzählungswerten. */
    private static List<String> arten(Object f) {
        List<String> l = new ArrayList<>();
        for (Method m : f.getClass().getMethods()) {
            if (m.getParameterCount() != 0 || !Collection.class.isAssignableFrom(m.getReturnType())) continue;
            try {
                Object c = m.invoke(f);
                if (!(c instanceof Collection) || ((Collection<?>) c).isEmpty()) continue;
                boolean enums = true;
                for (Object o : (Collection<?>) c) enums &= o instanceof Enum;
                if (!enums) continue;
                for (Object o : (Collection<?>) c) l.add(o.toString());
                return l;
            } catch (Exception ignore) { }
        }
        return l;
    }

    /** Detailobjekt einer Art (z. B. Rüstungswerte): Methode (Aufzählung, int). */
    private static Object detail(Object f, String art) {
        for (Method m : f.getClass().getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 2 || !p[0].isEnum() || p[1] != int.class) continue;
            for (Object e : p[0].getEnumConstants()) {
                if (!art.equalsIgnoreCase(e.toString())) continue;
                try { return m.invoke(f, e, 0); } catch (Exception ignore) { }
            }
        }
        return null;
    }

    private static Object quelle(Object f) {
        try {
            Method m = f.getClass().getMethod("super");
            Object q = m.invoke(f);
            return q != null && q.toString().matches(".+:\\s*\\d+.*") ? q : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Constructor<?> konstruktor(String klasse, Object arg) {
        try {
            Class<?> k = Class.forName(klasse, true, lader());
            for (Constructor<?> c : k.getConstructors())
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0].isInstance(arg)) return c;
        } catch (Throwable ignore) { }
        return null;
    }

    private static Object neu(String klasse, Object arg) {
        Constructor<?> c = konstruktor(klasse, arg);
        if (c == null) return null;
        try { return c.newInstance(arg); } catch (Throwable t) { return null; }
    }

    /** Hülle mit Konstruktor (Detailobjekt, Name), z. B. für Rüstungen und Schilde. */
    private static Object neuMitDetail(String klasse, Object f, String art) {
        Object d = detail(f, art);
        if (d == null) return null;
        try {
            Class<?> k = Class.forName(klasse, true, lader());
            for (Constructor<?> c : k.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 2 && p[0].isInstance(d) && p[1] == String.class) return c.newInstance(d, String.valueOf(f));
            }
        } catch (Throwable ignore) { }
        return null;
    }

    private static Object rufe(Object o, String methode) {
        try {
            Method m = o.getClass().getMethod(methode);
            return m.invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static ClassLoader lader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : VorlagenInfo.class.getClassLoader();
    }

    private static void log(String t) {
        if (debugLog == null) return;
        try (java.io.FileWriter w = new java.io.FileWriter(debugLog, true)) { w.write(t + "\n"); } catch (java.io.IOException ignore) { }
    }
}
