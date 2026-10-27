package de.modernhelden.print;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.*;

/**
 * Berechnete Heldendaten aus der Plugin-Schnittstelle (Aktion "held", Format XML, Version 3).
 * Enthält alle Werte, die auch die Original-Druckvorschau nutzt.
 */
public final class SheetData {

    public static final class Wert {
        public String name = "";
        public int akt, start, modi, akt50, akt33, akt25;
        public boolean has50;
    }

    public static final class Talent {
        public String name = "", probe = "", probenwerte = "", be = "", bereich = "", spalte = "", spez = "", k = "";
        public String at = "", pa = "";
        public boolean meta;
        /** Vorteil „Meisterhandwerk“ für dieses Talent (laut Helden-Software). */
        public boolean meister;
        /** Leittalent sowie Erleichterung/Erschwernis durch Mirakel (laut Helden-Software). */
        public boolean leit, mirakelPlus, mirakelMinus;
        public int wert;
    }

    public static final class Zauber {
        public String name = "", probe = "", probenwerte = "", spalte = "", merkmale = "", rep = "", anmerkung = "",
                kosten = "", zauberdauer = "", reichweite = "", wirkungsdauer = "", mr = "";
        public int wert;
        public boolean hauszauber, leit;
    }

    public static final class Eintrag {
        public String name = "", bereich = "", bezeichner = "";
        public boolean nachteil, vorteil;
    }

    public static final class Nahkampfwaffe {
        public String name = "", talent = "", dk = "", tp = "", tpkk = "", ini = "", wm = "", at = "", pa = "", bf = "", be = "";
        /** Richtet Ausdauerschaden an (Helden-Software: „(A)“ im Namen). */
        public boolean ausdauer;
        /** Kombiniert mit Schild/Parierwaffe Nr. … (Helden-Software: „(S1)“ im Namen), sonst leer. */
        public String schild = "";
        /** Platz 1–5 im Kampfset (für den Abgleich mit der Ausrüstung). */
        public int nummer;
        /** Beidhändiger Kampf für dieses Waffenpaar (1+2 bzw. 3+4) eingestellt. */
        public boolean beidhaendig;
        /** Hand laut Ausrüstung: „R“, „L“ oder leer, wenn unbekannt. */
        public String hand = "";
    }

    public static final class Fernkampfwaffe {
        public String name = "", talent = "", tp = "", reichweite = "", tpmod = "", fk = "", laden = "";
        /** Als Jagdwaffe eingestellt (vom Plugin aus den Heldendaten ergänzt). */
        public boolean jagd;
    }

    public static final class Schild {
        public String name = "", typ = "", ini = "", mod = "", pa = "", bf = "";
    }

    public static final class Ruestung {
        public String name = "", rs = "", be = "";
        /** Schutz je Zone (Kopf, Brust, …) in der Reihenfolge von {@link #ZONEN}; leer, wenn nicht angegeben. */
        public final String[] zonen = new String[ZONEN.length];
    }

    public static final String[][] ZONEN = {{"kopf", "Kopf"}, {"brust", "Brust"}, {"ruecken", "Rücken"}, {"bauch", "Bauch"},
            {"linkerarm", "L. Arm"}, {"rechterarm", "R. Arm"}, {"linkesbein", "L. Bein"}, {"rechtesbein", "R. Bein"}};

    public static final class Gegenstand {
        public String name = "", anzahl = "", gewichtStein = "", art = "";
        public double unzen;
        public boolean angelegt;
        /** Einzelgewicht in Unzen, Preise in Kreuzer (wie in der Helden-Software). */
        public double einzelUnzen;
        public long einzelpreis, gesamtpreis;
        public String quelle = "", details = "";
    }

    public static final class Muenze {
        public String name = "", kurz = "", waehrung = "";
        public int anzahl;
        /** Wert eines Stücks in Silbertalern */
        public double wertS;
    }

    public String name = "", rasse = "", kultur = "", profession = "", geschlecht = "", geburtstag = "", alter = "",
            groesse = "", gewicht = "", haarfarbe = "", augenfarbe = "", stand = "", titel = "", wundschwelle = "",
            leReg = "", aspReg = "", aussehen = "", familie = "", stufe = "", bildPfad = "";
    public int apGesamt, apFrei;
    public boolean magisch, geweiht;
    public final Map<String, Wert> werte = new LinkedHashMap<>();
    public final List<Talent> talente = new ArrayList<>();
    /** In den Druckeinstellungen der Helden-Software ist für diesen Helden „Metatalente“ eingeschaltet. */
    public boolean metaAktiv;
    public final List<Zauber> zauber = new ArrayList<>();
    public final List<Eintrag> vorteile = new ArrayList<>();
    public final List<Eintrag> sf = new ArrayList<>();
    public final List<Nahkampfwaffe> nahkampf = new ArrayList<>();
    /** Nummer des gedruckten Kampfsets (1-basiert), 0 wenn unbekannt. */
    public int kampfset;
    public final List<Fernkampfwaffe> fernkampf = new ArrayList<>();
    public final List<Schild> schilde = new ArrayList<>();
    public final List<Ruestung> ruestungen = new ArrayList<>();
    public final List<Gegenstand> inventar = new ArrayList<>();
    public final List<String> notizen = new ArrayList<>();
    public final List<Muenze> muenzen = new ArrayList<>();
    public final Map<String, String> zonen = new LinkedHashMap<>();
    public String rsGesamt = "", beGesamt = "", ausweichen = "", iniKampf = "", gsInklBe = "", geld = "";
    public String raufenAt = "", raufenPa = "", raufenTp = "", ringenAt = "", ringenPa = "", ringenTp = "";

    public static SheetData parse(Document doc) {
        Element root = doc.getDocumentElement();
        SheetData d = new SheetData();
        d.metaAktiv = "true".equalsIgnoreCase(text(child(root, "config"), "metatalente"));
        Element a = child(root, "angaben");
        d.name = text(a, "name");
        d.geschlecht = text(a, "geschlecht");
        d.geburtstag = text(a, "geburtstag");
        d.alter = text(a, "alter");
        d.groesse = text(a, "groesse");
        d.gewicht = text(a, "gewicht");
        d.haarfarbe = text(a, "haarfarbe");
        d.augenfarbe = text(a, "augenfarbe");
        d.stand = text(a, "stand");
        d.titel = text(a, "titel");
        d.bildPfad = text(a, "bildPfad");
        d.rasse = text(a, "rasse");
        d.kultur = text(a, "kultur");
        d.wundschwelle = text(a, "wundschwelle");
        d.leReg = text(a, "leregeneration");
        d.aspReg = text(a, "aspregeneration");
        d.magisch = "true".equals(text(a, "magisch"));
        d.geweiht = "true".equals(text(a, "geweiht"));
        Element prof = child(a, "profession");
        d.profession = text(prof, "text");
        if (d.profession.isEmpty()) d.profession = text(prof, "textkurz");
        Element ap = child(a, "ap");
        d.apGesamt = num(text(ap, "gesamt"));
        d.apFrei = num(text(ap, "frei"));
        d.stufe = text(child(a, "stufe41"), "akt");
        d.aussehen = text(child(a, "aussehen"), "text");
        if (d.aussehen.isEmpty()) d.aussehen = joinChildren(child(a, "aussehen"), " ");
        d.familie = text(child(a, "familie"), "text");
        if (d.familie.isEmpty()) d.familie = joinChildren(child(a, "familie"), " ");
        Element notes = child(a, "notizen");
        if (notes != null) for (Element n : children(notes)) {
            String t = n.getTextContent().trim();
            if (!t.isEmpty() && !t.equals("Notizen")) d.notizen.add(t);
        }

        Element eig = child(root, "eigenschaften");
        if (eig != null) for (Element e : children(eig)) {
            Wert w = new Wert();
            w.name = text(e, "name");
            w.start = num(text(e, "start"));
            w.modi = num(text(e, "modi"));
            String akt = text(e, "akt");
            w.akt = akt.isEmpty() ? w.start + w.modi : num(akt);
            w.has50 = !text(e, "akt50").isEmpty();
            w.akt50 = num(text(e, "akt50"));
            w.akt33 = num(text(e, "akt33"));
            w.akt25 = num(text(e, "akt25"));
            d.werte.put(e.getNodeName(), w);
        }

        Element vt = child(root, "vorteile");
        if (vt != null) for (Element e : children(vt)) {
            Eintrag x = new Eintrag();
            x.name = text(e, "namemitkommentar");
            if (x.name.isEmpty()) x.name = text(e, "name");
            x.bezeichner = text(e, "bezeichner");
            x.nachteil = "true".equals(text(e, "istnachteil"));
            x.vorteil = "true".equals(text(e, "istvorteil"));
            d.vorteile.add(x);
        }
        Element sfs = child(root, "sonderfertigkeiten");
        if (sfs != null) for (Element e : children(sfs)) {
            Eintrag x = new Eintrag();
            x.name = text(e, "namemitkommentar");
            if (x.name.isEmpty()) x.name = text(e, "nameausfuehrlich");
            x.bereich = text(e, "bereich");
            d.sf.add(x);
        }

        Element tl = child(root, "talentliste");
        if (tl != null) for (Element e : children(tl)) {
            Talent t = new Talent();
            // „Ansitzjagd incl Waffe“ der Helden-Software in üblicher Schreibweise: „Ansitzjagd inkl. Waffe“
            t.name = text(e, "name").replaceAll("\\bincl\\.?(?=\\s)", "inkl.");
            t.probe = text(e, "probe");
            t.probenwerte = text(e, "probenwerte");
            // Ritualkenntnis u. ä. haben keine Eigenschaftsprobe („--/--/--“ mit Platzhalterwerten)
            if (t.probe.replace("/", "").replace("-", "").trim().isEmpty()) { t.probe = "–"; t.probenwerte = ""; }
            t.be = text(e, "behinderung");
            t.bereich = text(e, "bereich");
            t.spalte = text(e, "lernkomplexität");
            t.spez = text(e, "spezialisierungen");
            t.k = text(e, "sprachkomplexität");
            t.meta = "true".equals(text(e, "metatalent"));
            t.meister = "true".equals(text(e, "meisterhandwerk"));
            t.leit = "true".equals(text(e, "leittalent"));
            t.mirakelPlus = "true".equals(text(e, "mirakelplus"));
            t.mirakelMinus = "true".equals(text(e, "mirakelminus"));
            t.at = text(e, "at");
            t.pa = text(e, "pa");
            t.wert = num(text(e, "wert"));
            if (!t.name.isEmpty()) d.talente.add(t);
        }

        Element zl = child(root, "zauberliste");
        if (zl != null) for (Element e : children(zl)) {
            Zauber z = new Zauber();
            z.name = first(e, "nameausfuehrlich", "name");
            z.probe = text(e, "probe");
            z.probenwerte = text(e, "probenwerte");
            z.wert = num(first(e, "wert", "zfw"));
            z.spalte = first(e, "lernkomplexität", "komplexität");
            z.merkmale = first(e, "merkmale", "merkmal");
            z.rep = first(e, "repräsentation", "repraesentation", "rep");
            z.anmerkung = first(e, "anmerkung", "anmerkungen", "zauberkommentar");
            z.kosten = text(e, "kosten");
            z.zauberdauer = text(e, "zauberdauer");
            z.reichweite = text(e, "reichweite");
            z.wirkungsdauer = text(e, "wirkungsdauer");
            z.hauszauber = "true".equals(text(e, "hauszauber"));
            z.leit = "true".equals(text(e, "leittalent"));
            z.mr = text(e, "mr");
            if (!z.name.isEmpty()) d.zauber.add(z);
        }

        Element g = child(root, "gegenstaende");
        if (g != null) for (Element e : children(g)) {
            Gegenstand x = new Gegenstand();
            x.name = first(e, "name", "grundlage");
            String anz = text(e, "anzahl");
            x.anzahl = anz.isEmpty() ? "1" : anz;
            double unzen = dbl(text(e, "gesamtgewicht"));
            x.unzen = unzen;
            x.gewichtStein = unzen > 0 ? fmt(unzen / 40.0) : "";
            x.angelegt = "true".equals(text(e, "angelegt"));
            x.art = text(e, "arten");
            x.einzelUnzen = dbl(text(e, "gewicht"));
            x.einzelpreis = Math.round(dbl(text(e, "einzelpreis")));
            x.gesamtpreis = Math.round(dbl(text(e, "gesamtpreis")));
            x.quelle = text(e, "quelle");
            x.details = text(e, "details");
            d.inventar.add(x);
        }
        Element m = child(root, "münzen");
        if (m != null) {
            List<String> l = new ArrayList<>();
            for (Element e : children(m)) {
                int n = num(text(e, "anzahl"));
                if (n != 0) l.add(n + " " + text(e, "namekurz"));
                Muenze mz = new Muenze();
                mz.name = text(e, "name");
                mz.kurz = text(e, "namekurz");
                mz.waehrung = text(e, "waehrung");
                mz.anzahl = n;
                mz.wertS = dbl(text(e, "wertinsilberstuecken"));
                if (n != 0) d.muenzen.add(mz);
            }
            d.geld = String.join(" · ", l);
        }

        Element ks = pickKampfset(child(root, "kampfsets"), child(root, "config"));
        if (ks != null) {
            try { d.kampfset = Integer.parseInt(ks.getAttribute("nr").trim()); } catch (NumberFormatException ignore) { }
            Element r = child(ks, "raufen");
            d.raufenAt = text(r, "at"); d.raufenPa = text(r, "pa"); d.raufenTp = text(r, "tp");
            Element ri = child(ks, "ringen");
            d.ringenAt = text(ri, "at"); d.ringenPa = text(ri, "pa"); d.ringenTp = text(ri, "tp");
            d.ausweichen = text(ks, "ausweichen");
            d.iniKampf = text(ks, "ini");
            d.gsInklBe = text(ks, "geschwindigkeitinklbe");
            Element rz = child(ks, "ruestungzonen");
            Element re = child(ks, "ruestungeinfach");
            if (rz != null && !text(rz, "gesamtschutz").isEmpty()) {
                String[][] z = {{"kopf", "Kopf"}, {"brust", "Brust"}, {"ruecken", "Rücken"}, {"bauch", "Bauch"},
                        {"linkerarm", "L. Arm"}, {"rechterarm", "R. Arm"}, {"linkesbein", "L. Bein"}, {"rechtesbein", "R. Bein"}};
                for (String[] zz : z) d.zonen.put(zz[1], text(rz, zz[0]));
                d.rsGesamt = text(rz, "gesamtzonenschutz");
                d.beGesamt = text(rz, "behinderung");
            } else if (re != null) {
                d.rsGesamt = text(re, "gesamt");
                d.beGesamt = text(re, "behinderung");
            }
            for (Element w : children(child(ks, "nahkampfwaffen"))) {
                Nahkampfwaffe n = new Nahkampfwaffe();
                n.name = text(w, "name");
                n.talent = text(w, "waffentalent");
                n.dk = text(w, "dk");
                n.tp = first(w, "tpinkl", "tp");
                n.tpkk = text(w, "tpkk");
                n.ini = text(w, "ini");
                n.wm = text(w, "wm").replace(" ", "");
                n.at = text(w, "at");
                n.pa = text(w, "pa");
                n.bf = text(w, "bfakt");
                n.be = text(w, "be");
                try { n.nummer = Integer.parseInt(text(w, "nummer").trim()); } catch (NumberFormatException ignore) { }
                n.beidhaendig = "true".equals(text(w, "bh"));
                // Kennzeichnungen, die die Helden-Software an den Namen hängt: „(A)“ Ausdauerschaden, „(S1)“ mit Schild Nr. 1
                java.util.regex.Matcher km;
                while ((km = java.util.regex.Pattern.compile("^(.*\\S)\\s+\\((A|S(\\d+))\\)$").matcher(n.name)).matches()) {
                    if ("A".equals(km.group(2))) n.ausdauer = true; else n.schild = km.group(3);
                    n.name = km.group(1);
                }
                if (!n.name.isEmpty()) d.nahkampf.add(n);
            }
            for (Element w : children(child(ks, "fernkampfwaffen"))) {
                Fernkampfwaffe f = new Fernkampfwaffe();
                f.name = text(w, "name");
                f.talent = text(w, "kampftalent");
                f.tp = text(w, "tp");
                f.reichweite = text(w, "reichweite").replace(" ", "");
                f.tpmod = text(w, "tpmod").replace(" ", "");
                f.fk = text(w, "at");
                f.laden = text(w, "ladezeit");
                f.jagd = "true".equals(text(w, "jagdwaffe"));
                if (!f.name.isEmpty()) d.fernkampf.add(f);
            }
            for (Element w : children(child(ks, "schilder"))) {
                Schild s = new Schild();
                s.name = text(w, "name");
                s.typ = text(w, "typ");
                s.ini = text(w, "ini");
                s.mod = text(w, "mod").replace(" ", "");
                s.pa = text(w, "pa");
                s.bf = text(w, "bfakt");
                if (!s.name.isEmpty()) d.schilde.add(s);
            }
            for (Element w : children(child(ks, "ruestungen"))) {
                Ruestung r2 = new Ruestung();
                r2.name = text(w, "name");
                r2.rs = first(w, "gesamtzonenschutz", "rs");
                r2.be = first(w, "behinderung", "be");
                for (int zi = 0; zi < ZONEN.length; zi++) r2.zonen[zi] = text(w, ZONEN[zi][0]);
                if (!r2.name.isEmpty()) d.ruestungen.add(r2);
            }
        }
        return d;
    }

    private static Element pickKampfset(Element sets, Element config) {
        if (sets == null) return null;
        boolean zone = !"einfach".equalsIgnoreCase(text(config, "rsmodell"));
        Element fallback = null;
        for (Element k : children(sets)) {
            if (!"true".equals(k.getAttribute("inbenutzung"))) continue;
            if (fallback == null) fallback = k;
            if ("true".equals(k.getAttribute("tzm")) == zone) return k;
        }
        return fallback;
    }

    /**
     * Ergänzt die Hand der Nahkampfwaffen aus den Heldendaten („exportHeld“): Einträge „nkwaffeN“ des gedruckten
     * Kampfsets tragen dort „hand“ = rechts/links. Die Druckdaten selbst enthalten die Hand nicht.
     */
    public void handAusExport(Document export) {
        if (export == null || kampfset <= 0) return;
        org.w3c.dom.NodeList nl = export.getElementsByTagName("heldenausruestung");
        for (Nahkampfwaffe n : nahkampf) {
            if (n.nummer <= 0) continue;
            for (int i = 0; i < nl.getLength(); i++) {
                Element e = (Element) nl.item(i);
                if (!("nkwaffe" + n.nummer).equals(e.getAttribute("name"))
                        || !String.valueOf(kampfset - 1).equals(e.getAttribute("set"))) continue;
                String h = e.getAttribute("hand");
                n.hand = "links".equals(h) ? "L" : "rechts".equals(h) ? "R" : "";
            }
        }
    }

    public Wert wert(String key) { return werte.get(key); }

    public int akt(String key) { Wert w = werte.get(key); return w == null ? 0 : w.akt; }

    // ------------------------------------------------------------ XML-Helfer

    static Element child(Element p, String name) {
        if (p == null) return null;
        for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element && n.getNodeName().equals(name)) return (Element) n;
        return null;
    }

    static List<Element> children(Element p) {
        List<Element> l = new ArrayList<>();
        if (p == null) return l;
        for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element) l.add((Element) n);
        return l;
    }

    static String text(Element p, String name) {
        Element c = child(p, name);
        if (c == null) return "";
        // nur direkter Text, keine Kindelemente
        StringBuilder sb = new StringBuilder();
        for (Node n = c.getFirstChild(); n != null; n = n.getNextSibling())
            if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) sb.append(n.getNodeValue());
        return sb.toString().trim();
    }

    static String first(Element p, String... names) {
        for (String n : names) {
            String t = text(p, n);
            if (!t.isEmpty()) return t;
        }
        return "";
    }

    static String joinChildren(Element p, String sep) {
        List<String> l = new ArrayList<>();
        for (Element e : children(p)) {
            String t = e.getTextContent().trim();
            if (!t.isEmpty()) l.add(t);
        }
        return String.join(sep, l);
    }

    static int num(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    static double dbl(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    static String fmt(double d) {
        if (Math.abs(d - Math.rint(d)) < 0.001) return String.valueOf((long) Math.rint(d));
        return String.format(Locale.GERMAN, "%.2f", d).replaceAll("0+$", "").replaceAll(",$", "");
    }
}
