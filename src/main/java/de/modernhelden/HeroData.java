package de.modernhelden;

import org.w3c.dom.*;

import java.util.*;

/** Liest einen Helden aus dem XML-Format der Helden-Software (Export/Speicherformat). */
public final class HeroData {

    public static final class Talent {
        public String name, probe = "", be = "", lernmethode = "", k = "";
        public int wert;
    }

    public static final class Kampfwert {
        public String name;
        public int at, pa;
        public boolean fernkampf;
    }

    public static final class Item {
        public String name, detail = "";
        public Item(String n, String d) { name = n; detail = d == null ? "" : d; }
    }

    public String name = "", rasse = "", kultur = "", profession = "", geschlecht = "";
    public int ap, freieAp;
    public final Map<String, Integer> eig = new LinkedHashMap<>();     // effektive Werte MU..KK, SO
    public final Map<String, Integer> eigMod = new HashMap<>();
    public final Map<String, Integer> eigVal = new HashMap<>();
    public final Map<String, Integer> eigStart = new HashMap<>();      // Startwert bei der Erschaffung
    /** LE, AU, AE, KE, MR: Modifikator (Rasse, Vor-/Nachteile …) und zugekaufte Punkte. */
    public final Map<String, Integer> energieMod = new HashMap<>(), energieKauf = new HashMap<>(), energiePermanent = new HashMap<>();
    public final Map<String, Integer> abgeleitet = new LinkedHashMap<>();
    public final List<Talent> talente = new ArrayList<>();
    public final Map<String, Talent> talentByName = new HashMap<>();
    public final List<Item> sf = new ArrayList<>();
    public final List<Item> vorteile = new ArrayList<>();
    public final List<Item> nachteile = new ArrayList<>();
    public final List<Kampfwert> kampf = new ArrayList<>();
    public final List<Talent> zauber = new ArrayList<>();
    public final List<Item> inventar = new ArrayList<>();
    public String geld = "";
    public double geldSilber;
    /** Auswertung des Ereignisprotokolls (Herkunft von Talentwerten und SF). */
    public Herkunft herkunft = new Herkunft(null);

    private static final String[][] EIG = {
            {"Mut", "MU"}, {"Klugheit", "KL"}, {"Intuition", "IN"}, {"Charisma", "CH"},
            {"Fingerfertigkeit", "FF"}, {"Gewandtheit", "GE"}, {"Konstitution", "KO"}, {"Körperkraft", "KK"},
            {"Sozialstatus", "SO"}};

    public static HeroData parse(Document doc, TooltipDB db) {
        HeroData h = new HeroData();
        Element held = first(doc.getDocumentElement(), "held");
        if (held == null && "held".equals(doc.getDocumentElement().getNodeName())) held = doc.getDocumentElement();
        if (held == null) return null;
        h.name = held.getAttribute("name");

        Element basis = first(held, "basis");
        if (basis != null) {
            Element e;
            if ((e = first(basis, "geschlecht")) != null) h.geschlecht = e.getAttribute("name");
            if ((e = first(basis, "rasse")) != null) h.rasse = e.getAttribute("string");
            if ((e = first(basis, "kultur")) != null) h.kultur = e.getAttribute("string");
            Element ausb = first(basis, "ausbildungen");
            if (ausb != null) {
                List<String> p = new ArrayList<>();
                for (Element a : children(ausb, "ausbildung")) p.add(a.getAttribute("string"));
                h.profession = String.join(" / ", p);
            }
            if ((e = first(basis, "abenteuerpunkte")) != null) h.ap = num(e.getAttribute("value"));
            if ((e = first(basis, "freieabenteuerpunkte")) != null) h.freieAp = num(e.getAttribute("value"));
        }

        Map<String, Element> eigEl = new HashMap<>();
        Element eigs = first(held, "eigenschaften");
        if (eigs != null) for (Element e : children(eigs, "eigenschaft")) eigEl.put(e.getAttribute("name"), e);
        for (String[] x : EIG) {
            Element e = eigEl.get(x[0]);
            if (e == null) continue;
            int v = num(e.getAttribute("value")), m = num(e.getAttribute("mod"));
            h.eig.put(x[1], v + m);
            h.eigVal.put(x[1], v);
            h.eigMod.put(x[1], m);
            if (!e.getAttribute("startwert").isEmpty()) h.eigStart.put(x[1], num(e.getAttribute("startwert")));
        }
        for (String[] x : new String[][]{{"Lebensenergie", "LE"}, {"Ausdauer", "AU"}, {"Astralenergie", "AE"},
                {"Karmaenergie", "KE"}, {"Magieresistenz", "MR"}}) {
            Element e = eigEl.get(x[0]);
            if (e == null) continue;
            h.energieMod.put(x[1], num(e.getAttribute("mod")));
            h.energieKauf.put(x[1], num(e.getAttribute("value")));
            // dauerhaft verlorene Punkte (negativ); im Modifikator der Helden-Software schon enthalten
            h.energiePermanent.put(x[1], num(e.getAttribute("permanent")));
        }
        int MU = h.e("MU"), KL = h.e("KL"), IN = h.e("IN"), CH = h.e("CH"), FF = h.e("FF"), GE = h.e("GE"), KO = h.e("KO"), KK = h.e("KK");
        h.abgeleitet.put("LE", round((KO + KO + KK) / 2.0) + sum(eigEl.get("Lebensenergie")));
        h.abgeleitet.put("AU", round((MU + KO + GE) / 2.0) + sum(eigEl.get("Ausdauer")));
        Element ae = eigEl.get("Astralenergie");
        if (ae != null && sum(ae) != 0) h.abgeleitet.put("AE", round((MU + IN + CH) / 2.0) + sum(ae));
        Element ke = eigEl.get("Karmaenergie");
        if (ke != null && sum(ke) != 0) h.abgeleitet.put("KE", sum(ke));
        h.abgeleitet.put("MR", round((MU + KL + KO) / 5.0) + sum(eigEl.get("Magieresistenz")));
        h.abgeleitet.put("INI", round((MU + MU + IN + GE) / 5.0) + num(attr(eigEl.get("ini"), "mod")));
        h.abgeleitet.put("AT", round((MU + GE + KK) / 5.0) + num(attr(eigEl.get("at"), "mod")));
        h.abgeleitet.put("PA", round((IN + GE + KK) / 5.0) + num(attr(eigEl.get("pa"), "mod")));
        h.abgeleitet.put("FK", round((IN + FF + KK) / 5.0) + num(attr(eigEl.get("fk"), "mod")));

        Element tl = first(held, "talentliste");
        if (tl != null) for (Element e : children(tl, "talent")) {
            Talent t = new Talent();
            t.name = e.getAttribute("name");
            t.probe = e.getAttribute("probe").trim();
            t.be = e.getAttribute("be");
            t.lernmethode = e.getAttribute("lernmethode");
            t.k = e.getAttribute("k");
            t.wert = num(e.getAttribute("value"));
            h.talente.add(t);
            h.talentByName.put(t.name, t);
        }

        Element zl = first(held, "zauberliste");
        if (zl != null) for (Element e : children(zl, "zauber")) {
            Talent t = new Talent();
            t.name = e.getAttribute("name");
            String var = e.getAttribute("variante");
            if (!var.isEmpty()) t.name += " (" + var + ")";
            t.probe = e.getAttribute("probe").trim();
            t.lernmethode = e.getAttribute("repraesentation");
            t.wert = num(e.getAttribute("value"));
            h.zauber.add(t);
        }

        Element sfs = first(held, "sf");
        if (sfs != null) for (Element e : children(sfs, "sonderfertigkeit")) {
            List<String> d = new ArrayList<>();
            for (Element sub : children(e, null)) {
                String n = sub.getAttribute("name");
                if (!n.isEmpty() && !e.getAttribute("name").contains(n)) d.add(n);
            }
            h.sf.add(new Item(e.getAttribute("name"), String.join(", ", d)));
        }

        Element vt = first(held, "vt");
        if (vt != null) for (Element e : children(vt, "vorteil")) {
            String n = e.getAttribute("name");
            List<String> d = new ArrayList<>();
            String v = e.getAttribute("value");
            List<Element> ausw = children(e, "auswahl");
            for (int i = ausw.size() - 1; i >= 0; i--) d.add(ausw.get(i).getAttribute("value"));
            if (!v.isEmpty()) d.add(v);
            Item it = new Item(n, String.join(" ", d));
            TooltipDB.Entry en = db == null ? null : db.lookupEntry(n);
            if (en != null && "Nachteil".equals(en.gruppe)) h.nachteile.add(it); else h.vorteile.add(it);
        }

        Element k = first(held, "kampf");
        if (k != null) for (Element e : children(k, "kampfwerte")) {
            Kampfwert kw = new Kampfwert();
            kw.name = e.getAttribute("name");
            Element a = first(e, "attacke"), p = first(e, "parade");
            kw.at = a == null ? 0 : num(a.getAttribute("value"));
            kw.pa = p == null ? 0 : num(p.getAttribute("value"));
            h.kampf.add(kw);
        }
        // Fernkampftalente: FK = FK-Basis + TaW
        for (Talent t : h.talente) {
            TooltipDB.Entry en = db == null ? null : db.lookupEntry(t.name);
            if (en != null && "Fernkampf".equals(en.gruppe) && t.wert > 0) {
                Kampfwert kw = new Kampfwert();
                kw.name = t.name;
                kw.fernkampf = true;
                kw.at = h.abgeleitet.get("FK") + t.wert;
                h.kampf.add(kw);
            }
        }

        Element g = first(held, "gegenstände");
        if (g != null) for (Element e : children(g, "gegenstand")) {
            String anz = e.getAttribute("anzahl");
            String n = e.getAttribute("name");
            Element mod = first(e, "modallgemein");
            if (mod != null && first(mod, "name") != null) n = first(mod, "name").getAttribute("value");
            h.inventar.add(new Item(n, anz.isEmpty() || "1".equals(anz) ? "" : anz + "×"));
        }
        h.herkunft = new Herkunft(first(held, "ereignisse"));
        Element gb = first(held, "geldboerse");
        if (gb != null) {
            // Mittelreich in fester Reihenfolge (Dukaten, Silbertaler, Heller, Kreuzer), danach andere Münzen
            int[] mr = new int[Geld.NAMEN.length];
            List<String> andere = new ArrayList<>();
            double silber = 0;
            for (Element e : children(gb, "muenze")) {
                int a = num(e.getAttribute("anzahl"));
                if (a == 0) continue;
                String w = e.getAttribute("waehrung");
                int i = Geld.index(e.getAttribute("name"));
                if (i >= 0 && (w.isEmpty() || "Mittelreich".equals(w))) { mr[i] += a; silber += a * Geld.WERT_S[i]; }
                else andere.add(a + " " + e.getAttribute("name") + (w.isEmpty() ? "" : " (" + w + ")"));
            }
            List<String> m = new ArrayList<>();
            for (int i = 0; i < mr.length; i++) if (mr[i] != 0) m.add(Geld.anzeige(mr[i], i));
            m.addAll(andere);
            h.geld = String.join(", ", m);
            h.geldSilber = silber;
        }
        return h;
    }

    public int e(String k) { Integer v = eig.get(k); return v == null ? 0 : v; }

    /** "AT 17 · PA 12" bzw. "FK 19" für Kampftalente, sonst null. */
    public String kampfwerteText(String talent) {
        for (Kampfwert k : kampf) {
            if (!k.name.equals(talent)) continue;
            return k.fernkampf ? "FK " + k.at : "AT " + k.at + " · PA " + k.pa;
        }
        return null;
    }

    public Talent talent(String name) { return name == null ? null : talentByName.get(name); }

    /**
     * Meta-Talente (Ansitzjagd, Wachehalten …) stehen nicht in den Heldendaten; die Helden-Software berechnet sie
     * nur für den Druck – und nur, wenn sie in ihren Druckeinstellungen eingeschaltet sind. Sie werden hier aus den
     * Druckdaten ergänzt (Name in beiden Schreibweisen auffindbar: „incl“ wie im Reiter „Talente“, „inkl.“).
     */
    /**
     * Vor- und Nachteile so einordnen, wie die Helden-Software sie führt („istnachteil“ in ihren Druckdaten).
     * Ohne diese Angabe bleibt die Einordnung über die Tooltip-Liste (unbekannte Einträge gelten als Vorteil).
     */
    public void vorNachteile(de.modernhelden.print.SheetData sd) {
        if (sd == null || sd.vorteile.isEmpty()) return;
        Map<String, Boolean> istNachteil = new HashMap<>();
        for (de.modernhelden.print.SheetData.Eintrag e : sd.vorteile) {
            if (!e.nachteil && !e.vorteil) continue;
            if (!e.bezeichner.isEmpty()) istNachteil.put(e.bezeichner, e.nachteil);
        }
        if (istNachteil.isEmpty()) return;
        List<Item> alle = new ArrayList<>(vorteile);
        alle.addAll(nachteile);
        List<Item> v = new ArrayList<>(), n = new ArrayList<>();
        for (Item it : alle) {
            Boolean nt = istNachteil.get(it.name);
            boolean warNachteil = nachteile.contains(it);
            if (nt == null ? warNachteil : nt) n.add(it); else v.add(it);
        }
        vorteile.clear();
        vorteile.addAll(v);
        nachteile.clear();
        nachteile.addAll(n);
    }

    public void metaTalente(de.modernhelden.print.SheetData sd) {
        if (sd == null || !sd.metaAktiv) return;
        for (de.modernhelden.print.SheetData.Talent st : sd.talente) {
            if (!st.meta || talentByName.containsKey(st.name)) continue;
            Talent t = new Talent();
            t.name = st.name;
            t.probe = st.probe;
            t.be = st.be;
            t.wert = st.wert;
            t.lernmethode = "berechnet";
            talente.add(t);
            talentByName.put(t.name, t);
            talentByName.put(t.name.replace("inkl.", "incl"), t);
        }
    }

    /** Zauber des Helden zu einem angezeigten Namen (auch mit Zusätzen wie Variante oder Wert); sonst null. */
    public Talent zauber(String shown) {
        if (shown == null) return null;
        String t = shown.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        for (Talent z : zauber) if (z.name.equalsIgnoreCase(t)) return z;
        for (Talent z : zauber) {
            String n = z.name;
            if (t.length() > n.length() && t.regionMatches(true, 0, n, 0, n.length())) {
                char c = t.charAt(n.length());
                if (c == ' ' || c == '(' || c == ':' || c == ',') return z;
            }
        }
        return null;
    }

    /** "(GE/GE/KK)" -> "GE 14 · GE 14 · KK 15" */
    public String probeMitWerten(String probe) {
        if (probe == null) return "";
        String p = probe.replace("(", "").replace(")", "").trim();
        if (p.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : p.split("/")) {
            s = s.trim();
            if (sb.length() > 0) sb.append(" · ");
            sb.append(s);
            if (eig.containsKey(s)) sb.append(' ').append(eig.get(s));
        }
        return sb.toString();
    }

    private static int round(double d) { return (int) Math.floor(d + 0.5); }

    private static int sum(Element e) {
        if (e == null) return 0;
        return num(e.getAttribute("value")) + num(e.getAttribute("mod"));
    }

    private static String attr(Element e, String a) { return e == null ? "" : e.getAttribute(a); }

    static int num(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    static Element first(Element parent, String name) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element && n.getNodeName().equals(name)) return (Element) n;
        return null;
    }

    static List<Element> children(Element parent, String name) {
        List<Element> l = new ArrayList<>();
        if (parent == null) return l;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element && (name == null || n.getNodeName().equals(name))) l.add((Element) n);
        return l;
    }
}
