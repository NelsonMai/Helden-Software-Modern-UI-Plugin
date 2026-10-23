package de.modernhelden;

import org.w3c.dom.Element;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Wertet das Ereignisprotokoll eines Helden aus und erklärt, wie ein Talentwert
 * zustande gekommen ist: Startwert aus Rasse/Kultur/Profession, Steigerungen bei
 * der Erschaffung und spätere Steigerungen (mit Lernmethode, SE, AP und Datum).
 */
public final class Herkunft {

    /** Ein Eintrag aus dem Ereignisprotokoll der Helden-Software. */
    public static final class Ereignis {
        public String text = "", obj = "", alt = "", neu = "", info = "", kommentar = "", ap = "";
        public long time;
        int order;
    }

    /** Alle Ereignisse nach der Erschaffung, die die Helden-Software rückgängig machen kann – neueste zuerst. */
    public List<Ereignis> rueckgaengigMoeglich() {
        List<Ereignis> out = new ArrayList<>();
        for (Ereignis e : ereignisse) if (Rueckgaengig.unterstuetzt(e.text) && e.time > 0) out.add(e);
        Collections.reverse(out);
        return out;
    }

    /**
     * Gibt es nach diesem Ereignis noch ein späteres zum selben Talent/Zauber/zur selben Eigenschaft bzw. SF?
     * Dann muss zuerst das spätere rückgängig gemacht werden.
     */
    public Ereignis spaeteresZuSelbem(Ereignis e) {
        boolean danach = false;
        for (Ereignis x : ereignisse) {
            if (x == e) { danach = true; continue; }
            if (danach && Rueckgaengig.unterstuetzt(x.text) && x.obj.equals(e.obj)) return x;
        }
        return null;
    }

    /** Alle übrigen rückgängig machbaren SF-Ereignisse (neueste zuerst) – mögliche Abhängigkeiten. */
    public List<String> andereSonderfertigkeiten(Ereignis e) {
        List<String> l = new ArrayList<>();
        for (Ereignis x : rueckgaengigMoeglich())
            if (x != e && x.text.startsWith("Sonderfertigkeit") && !l.contains(x.obj)) l.add(x.obj);
        return l;
    }

    /** Datum als Text. */
    public String datum(Ereignis e) { return e.time > 0 ? datum.format(new Date(e.time)) : ""; }

    private final List<Ereignis> ereignisse = new ArrayList<>();
    private final SimpleDateFormat datum = new SimpleDateFormat("dd.MM.yyyy");

    Herkunft(Element ereignisseElement) {
        if (ereignisseElement == null) return;
        int i = 0;
        for (Element e : HeroData.children(ereignisseElement, "ereignis")) {
            Ereignis x = new Ereignis();
            x.text = e.getAttribute("text");
            x.obj = e.getAttribute("obj");
            x.alt = e.getAttribute("Alt");
            x.neu = e.getAttribute("Neu");
            x.info = e.getAttribute("Info");
            x.kommentar = e.getAttribute("kommentar");
            x.ap = e.getAttribute("Abenteuerpunkte");
            try { x.time = Long.parseLong(e.getAttribute("time")); } catch (NumberFormatException ignore) { }
            x.order = i++;
            ereignisse.add(x);
        }
    }

    /**
     * Zeilen für den Tooltip, z. B.
     * "Start (Rasse/Kultur/Profession)|4", "Erschaffung|4 → 5 · 22 AP", "19.10.2026|5 → 6 · Selbststudium · 34 AP".
     * Jede Zeile ist {Beschriftung, Text}.
     */
    public List<String[]> talent(String name, int aktuellerWert) {
        List<String[]> out = new ArrayList<>();
        if (name == null || ereignisse.isEmpty()) return out;

        // 1) Kauf/Steigerung während der Erschaffung: text="TALENT", obj="Name: Wert", kommentar="xx AP"
        List<int[]> erschaffung = new ArrayList<>();   // {neu, ap}
        for (Ereignis e : ereignisse) {
            if (!"TALENT".equals(e.text)) continue;
            int p = e.obj.lastIndexOf(": ");
            if (p < 0 || !e.obj.substring(0, p).equals(name)) continue;
            erschaffung.add(new int[]{num(e.obj.substring(p + 2)), num(e.kommentar.replace("AP", ""))});
        }
        // 2) Steigerungen/Senkungen nach der Erschaffung
        List<Ereignis> spaeter = new ArrayList<>();
        for (Ereignis e : ereignisse) {
            if (!name.equals(e.obj)) continue;
            String t = e.text.toLowerCase(Locale.GERMAN);
            if (t.contains("talent") && (t.contains("steigern") || t.contains("senken") || t.contains("aktivier")))
                spaeter.add(e);
        }
        // 3) Wahl bei der Profession (z. B. Krieger: eine von mehreren Waffen)
        String auswahl = null;
        for (Ereignis e : ereignisse)
            if ("Auswahl".equals(e.obj) && name.equals(e.text)) auswahl = e.neu;

        if (erschaffung.isEmpty() && spaeter.isEmpty() && auswahl == null) {
            out.add(new String[]{"Startwert", "aus Rasse, Kultur und Profession (keine Steigerung protokolliert)"});
            return out;
        }

        // Startwert ermitteln
        Integer start = null;
        boolean aktiviertBeiErschaffung = false;
        if (!erschaffung.isEmpty()) {
            int first = erschaffung.get(0)[0];
            if (first == 0 && erschaffung.get(0)[1] > 0) aktiviertBeiErschaffung = true;
            else start = first - 1;
        } else if (!spaeter.isEmpty()) {
            start = num(first(spaeter.get(0).alt));
        }

        if (aktiviertBeiErschaffung) {
            out.add(new String[]{"Start", "nicht in Rasse/Kultur/Profession enthalten"});
        } else if (start != null) {
            out.add(new String[]{"Start", start + "  (Rasse, Kultur, Profession)"});
        }
        if (auswahl != null) out.add(new String[]{"Profession", "Auswahl: " + auswahl + " (im Startwert enthalten)"});

        if (!erschaffung.isEmpty()) {
            int sum = 0;
            for (int[] s : erschaffung) sum += s[1];
            int von = aktiviertBeiErschaffung ? 0 : erschaffung.get(0)[0] - 1;
            int bis = erschaffung.get(erschaffung.size() - 1)[0];
            String txt = aktiviertBeiErschaffung && bis == 0 ? "aktiviert" :
                    (aktiviertBeiErschaffung ? "aktiviert, " : "") + von + " → " + bis;
            out.add(new String[]{"Erschaffung", txt + (sum > 0 ? " · " + sum + " AP" : "")});
        }

        for (Ereignis e : spaeter) {
            String alt = first(e.alt), neu = first(e.neu);
            StringBuilder sb = new StringBuilder();
            sb.append(alt.isEmpty() ? "?" : alt).append(" → ").append(neu.isEmpty() ? "?" : neu);
            String lern = lernText(e.info);
            if (!lern.isEmpty()) sb.append(" · ").append(lern);
            int ap = -num(e.ap);
            if (ap != 0) sb.append(" · ").append(ap).append(" AP");
            out.add(new String[]{e.time > 0 ? datum.format(new Date(e.time)) : "Später", sb.toString()});
        }
        return out;
    }

    /** Herkunft einer Sonderfertigkeit oder eines Vor-/Nachteils. */
    public List<String[]> sonderfertigkeit(String name) {
        List<String[]> out = new ArrayList<>();
        if (name == null) return out;
        for (Ereignis e : ereignisse) {
            if (!e.text.toLowerCase(Locale.GERMAN).startsWith("sonderfertigkeit")) continue;
            if (!e.obj.equals(name) && !name.startsWith(e.obj)) continue;
            int ap = -num(e.ap);
            out.add(new String[]{e.time > 0 ? datum.format(new Date(e.time)) : "Erworben",
                    e.text + (ap != 0 ? " · " + ap + " AP" : "")});
        }
        if (out.isEmpty() && !ereignisse.isEmpty())
            out.add(new String[]{"Erworben", "bei der Erschaffung (Rasse, Kultur, Profession oder Kauf)"});
        return out;
    }

    /**
     * Woher die Abenteuerpunkte kommen und wofür sie ausgegeben wurden.
     * Vergaben stehen im Protokoll als „Ereignis eingeben“ (Abenteuerpunkte &gt; 0) bzw. als direkte Änderung
     * „Abenteuerpunkte“ (Alt → Neu); der Rest des Gesamtwerts stammt aus der Erschaffung.
     */
    public List<String[]> abenteuerpunkte(int gesamt, int frei) {
        List<String[]> zugang = new ArrayList<>();
        int summeZugang = 0, manuell = 0;
        Map<String, Integer> ausgaben = new LinkedHashMap<>();
        int summeAusgaben = 0;
        for (Ereignis e : ereignisse) {
            int ap = num(e.ap);
            if ("Abenteuerpunkte".equals(e.text) && !e.alt.isEmpty() && !e.neu.isEmpty()) {
                manuell += num(e.neu) - num(e.alt);          // direkte Änderung des Gesamtwerts
                continue;
            }
            if (ap == 0) continue;
            boolean vergabe = e.text.startsWith("Ereignis") || e.obj.startsWith("Abenteuerpunkte");
            if (vergabe) {
                summeZugang += ap;
                String grund = e.kommentar.replaceAll("\\s*Gesamt AP:.*$", "").trim();
                if (grund.isEmpty()) grund = e.obj.isEmpty() ? "Abenteuerpunkte" : e.obj;
                zugang.add(new String[]{e.time > 0 ? datum.format(new Date(e.time)) : "Ereignis",
                        (ap > 0 ? "+" : "−") + Math.abs(ap) + " AP · " + grund});
            } else if (ap < 0) {
                String t = e.text.toLowerCase(Locale.GERMAN);
                String k = t.contains("zauber") ? "Zauber" : t.contains("sonderfertigkeit") ? "Sonderfertigkeiten"
                        : t.contains("eigenschaft") ? "Eigenschaften" : t.contains("talent") ? "Talente"
                        : t.contains("vorteil") || t.contains("nachteil") ? "Vor-/Nachteile"
                        : t.contains("liturgie") ? "Liturgien" : "Sonstiges";
                ausgaben.merge(k, -ap, Integer::sum);
                summeAusgaben += -ap;
            }
        }
        List<String[]> out = new ArrayList<>();
        int start = gesamt - summeZugang - manuell;
        out.add(new String[]{"Erschaffung", start + " AP"});
        out.addAll(zugang);
        if (manuell != 0) out.add(new String[]{"Von Hand", (manuell > 0 ? "+" : "−") + Math.abs(manuell) + " AP (Gesamtwert direkt geändert)"});
        out.add(new String[]{"Gesamt", gesamt + " AP"});
        int ausgegeben = gesamt - frei;
        int beiErschaffung = Math.max(0, ausgegeben - summeAusgaben);
        out.add(new String[]{"Ausgegeben", ausgegeben + " AP"});
        if (beiErschaffung > 0 && summeAusgaben > 0) out.add(new String[]{"  bei Erschaffung", beiErschaffung + " AP"});
        for (Map.Entry<String, Integer> a : ausgaben.entrySet())
            out.add(new String[]{"  " + a.getKey(), a.getValue() + " AP"});
        out.add(new String[]{"Frei", frei + " AP"});
        return out;
    }

    static String lernText(String info) {
        if (info == null || info.trim().isEmpty()) return "";
        List<String> teile = new ArrayList<>();
        for (String p : info.split(",")) {
            p = p.trim();
            if (p.equals("SE")) teile.add("Spezielle Erfahrung");
            else if (!p.isEmpty()) teile.add(p);
        }
        return String.join(", ", teile);
    }

    private static String first(String s) {
        if (s == null) return "";
        int i = s.indexOf(';');
        return (i >= 0 ? s.substring(0, i) : s).trim();
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}
