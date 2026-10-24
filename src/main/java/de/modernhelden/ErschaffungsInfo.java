package de.modernhelden;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Was eine Rasse, Kultur oder Profession mitbringt – gelesen aus dem Objekt der Helden-Software
 * (Eigenschaftsänderungen, Vor-/Nachteile, Talente, Sonderfertigkeiten, Empfehlungen).
 * Die Methoden werden per Reflexion gesucht, weil die Klassen der Helden-Software verschleiert sind;
 * fehlt etwas, bleibt der Abschnitt einfach weg.
 */
final class ErschaffungsInfo {

    private ErschaffungsInfo() {}

    /** Abschnitte als {Titel, Text}. */
    static List<String[]> lesen(Object mod) {
        List<String[]> l = new ArrayList<>();
        abschnitt(l, "Eigenschaften", eigenschaften(mod));
        abschnitt(l, "Vor- und Nachteile", liste(ErschaffungsRahmen.rufe(mod, "getVorteile")));
        abschnitt(l, "Talente", talente(ErschaffungsRahmen.rufe(mod, "getTalente")));
        abschnitt(l, "Zauber", talente(ErschaffungsRahmen.rufe(mod, "getZauberfertigkeiten")));
        abschnitt(l, "Sonderfertigkeiten", liste(ErschaffungsRahmen.rufe(mod, "getSonderfertigkeiten")));
        abschnitt(l, "Verbilligte Sonderfertigkeiten", liste(ErschaffungsRahmen.rufe(mod, "getVerbilligteSonderfertigkeiten")));
        abschnitt(l, "Empfohlene Vor- und Nachteile", liste(ErschaffungsRahmen.rufe(mod, "getEmpfohleneVorteile")));
        abschnitt(l, "Ungeeignete Vor- und Nachteile", liste(ErschaffungsRahmen.rufe(mod, "getUngeeigneteVorteile")));
        Object min = ErschaffungsRahmen.rufe(mod, "getMinGroesse"), max = ErschaffungsRahmen.rufe(mod, "getMaxGroesse");
        if (min instanceof Integer && max instanceof Integer && (Integer) max > 0)
            abschnitt(l, "Größe", min + "–" + max + " cm");
        return l;
    }

    private static void abschnitt(List<String[]> l, String titel, String text) {
        if (text != null && !text.trim().isEmpty()) l.add(new String[]{titel, text.trim()});
    }

    /** Änderungen an MU … KK, LE, AU, AE, MR usw. über getModifikator(Eigenschaft). */
    private static String eigenschaften(Object mod) {
        Method m = null;
        for (Method k : mod.getClass().getMethods())
            if (k.getName().equals("getModifikator") && k.getParameterCount() == 1) { m = k; break; }
        if (m == null) return null;
        Class<?> art = m.getParameterTypes()[0];
        StringBuilder sb = new StringBuilder();
        java.util.Set<Object> gesehen = new java.util.HashSet<>();
        for (Field f : art.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != art) continue;
            try {
                Object e = f.get(null);
                if (e == null || !gesehen.add(e)) continue;
                Object w = m.invoke(mod, e);
                if (!(w instanceof Integer) || (Integer) w == 0) continue;
                int v = (Integer) w;
                if (sb.length() > 0) sb.append(", ");
                sb.append(kurz(e.toString())).append(' ').append(v > 0 ? "+" + v : "−" + (-v));
            } catch (ReflectiveOperationException | RuntimeException ignore) { }
        }
        return sb.toString();
    }

    private static String kurz(String eigenschaft) {
        switch (eigenschaft) {
            case "Mut": return "MU";
            case "Klugheit": return "KL";
            case "Intuition": return "IN";
            case "Charisma": return "CH";
            case "Fingerfertigkeit": return "FF";
            case "Gewandtheit": return "GE";
            case "Konstitution": return "KO";
            case "Körperkraft": return "KK";
            case "Lebensenergie": return "LE";
            case "Ausdauer": return "AU";
            case "Astralenergie": return "AE";
            case "Karmaenergie": return "KE";
            case "Magieresistenz": return "MR";
            case "Sozialstatus": return "SO";
            default: return eigenschaft;
        }
    }

    /** Talentwerte: die Sammlung hat eine Methode ohne Parameter, die eine Map Talent → Wert liefert. */
    private static String talente(Object sammlung) {
        if (sammlung == null) return null;
        for (Method m : sammlung.getClass().getMethods()) {
            if (m.getParameterCount() != 0 || !Map.class.isAssignableFrom(m.getReturnType())) continue;
            try {
                Map<?, ?> map = (Map<?, ?>) m.invoke(sammlung);
                if (map == null) continue;
                StringBuilder sb = new StringBuilder();
                List<String> teile = new ArrayList<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    Object w = e.getValue();
                    String n = String.valueOf(e.getKey());
                    if (w instanceof Integer && (Integer) w != 0) n += " " + ((Integer) w > 0 ? "+" + w : "−" + (-(Integer) w));
                    teile.add(n);
                }
                java.util.Collections.sort(teile);
                for (String t : teile) { if (sb.length() > 0) sb.append(", "); sb.append(t); }
                return sb.toString();
            } catch (ReflectiveOperationException | RuntimeException ignore) { }
        }
        return liste(sammlung);
    }

    private static String liste(Object o) {
        if (o == null) return null;
        Iterable<?> it = null;
        if (o instanceof Iterable) it = (Iterable<?>) o;
        else if (o instanceof Object[]) it = java.util.Arrays.asList((Object[]) o);
        if (it == null) {
            String t = o.toString();
            return t.contains("@") ? null : t;
        }
        StringBuilder sb = new StringBuilder();
        for (Object e : it) {
            if (e == null) continue;
            String t = e.toString().trim();
            if (t.isEmpty() || t.contains("@")) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(t);
        }
        return sb.toString();
    }

    static String text(Collection<?> c) { return liste(c); }
}
