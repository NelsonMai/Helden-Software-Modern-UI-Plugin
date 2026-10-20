package de.modernhelden;

import java.util.Locale;

/** Mittelreichische Münzen: Reihenfolge, Werte und Umrechnung. */
public final class Geld {

    private Geld() {}

    /** Name, Kürzel, Wert in Silbertalern – in absteigender Reihenfolge. */
    public static final String[] NAMEN = {"Dukat", "Silbertaler", "Heller", "Kreuzer"};
    public static final String[] PLURAL = {"Dukaten", "Silbertaler", "Heller", "Kreuzer"};
    public static final String[] KURZ = {"D", "S", "H", "K"};
    public static final double[] WERT_S = {10, 1, 0.1, 0.01};

    public static final String UMRECHNUNG = "1 Dukat = 10 Silbertaler = 100 Heller = 1000 Kreuzer";

    /** Index in {@link #NAMEN} oder -1 für andere Münzen/Währungen. */
    public static int index(String name) {
        if (name == null) return -1;
        String n = name.trim().toLowerCase(Locale.GERMAN);
        for (int i = 0; i < NAMEN.length; i++) {
            String a = NAMEN[i].toLowerCase(Locale.GERMAN), p = PLURAL[i].toLowerCase(Locale.GERMAN);
            if (n.equals(a) || n.equals(p) || n.equals(KURZ[i].toLowerCase(Locale.GERMAN))) return i;
        }
        if (n.startsWith("silber")) return 1;
        return -1;
    }

    public static String anzeige(int anzahl, int idx) {
        return anzahl + " " + (anzahl == 1 ? NAMEN[idx] : PLURAL[idx]);
    }

    /** Betrag in Silbertalern, z. B. „320,7“. */
    public static String silber(double s) {
        if (Math.abs(s - Math.rint(s)) < 0.0005) return String.valueOf((long) Math.rint(s));
        return String.format(Locale.GERMAN, "%.2f", s).replaceAll("0+$", "").replaceAll(",$", "");
    }
}
