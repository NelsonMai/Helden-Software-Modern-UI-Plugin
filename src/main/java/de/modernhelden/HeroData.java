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
        Element gb = first(held, "geldboerse");
        if (gb != null) {
            List<String> m = new ArrayList<>();
            for (Element e : children(gb, "muenze")) {
                int a = num(e.getAttribute("anzahl"));
                if (a != 0) m.add(a + " " + e.getAttribute("name"));
            }
            h.geld = String.join(", ", m);
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
