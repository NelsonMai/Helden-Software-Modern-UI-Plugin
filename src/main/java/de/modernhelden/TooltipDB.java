package de.modernhelden;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Tooltip-Datenbank: eingebaute Kurzbeschreibungen (tooltips.tsv im JAR)
 * plus eigene Texte des Nutzers (eigene-tooltips.tsv im Plugin-Ordner).
 */
public final class TooltipDB {

    public static final class Entry {
        public final String typ;      // T, S, V
        public final String name;
        public final String gruppe;
        public final String spalte;
        public final String text;
        public final boolean prefix;
        Entry(String typ, String name, String gruppe, String spalte, String text, boolean prefix) {
            this.typ = typ; this.name = name; this.gruppe = gruppe; this.spalte = spalte; this.text = text; this.prefix = prefix;
        }
        public String typName() {
            if ("T".equals(typ)) return "Talent";
            if ("S".equals(typ)) return "Sonderfertigkeit";
            if ("V".equals(typ)) return "Nachteil".equals(gruppe) ? "Nachteil" : "Vorteil";
            return "";
        }
    }

    private final Map<String, Entry> exact = new HashMap<>();
    private final List<Entry> prefixes = new ArrayList<>();
    private final Map<String, String> userTexts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Optional<Match>> cache = new HashMap<>();
    private File userFile;

    /** Ergebnis einer Suche: der gefundene Eintrag (kann null sein, wenn nur eigener Text existiert). */
    public static final class Match {
        public final String key;      // Name, unter dem eigene Texte gespeichert werden
        public final Entry entry;
        public final String shown;    // angezeigter Name (Originaltext)
        Match(String key, Entry entry, String shown) { this.key = key; this.entry = entry; this.shown = shown; }
    }

    public TooltipDB() {
        try (InputStream in = TooltipDB.class.getResourceAsStream("tooltips.tsv")) {
            if (in != null) load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
        prefixes.sort((a, b) -> b.name.length() - a.name.length());
    }

    private void load(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\t", -1);
            if (p.length < 5) continue;
            String name = p[1].trim();
            boolean prefix = name.endsWith("*");
            if (prefix) name = name.substring(0, name.length() - 1);
            Entry e = new Entry(p[0].trim(), name, p[2].trim(), p[3].trim(), p[4].trim(), prefix);
            if (prefix) prefixes.add(e); else exact.put(norm(name), e);
        }
    }

    public synchronized void setUserFile(File f) {
        this.userFile = f;
        userTexts.clear();
        cache.clear();
        if (f == null || !f.isFile()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String text = line.substring(tab + 1).replace("\\n", "\n").trim();
                if (!text.isEmpty()) userTexts.put(line.substring(0, tab).trim(), text);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public File getUserFile() { return userFile; }

    public synchronized String getUserText(String key) { return userTexts.get(key); }

    public synchronized void setUserText(String key, String text) throws IOException {
        if (text == null || text.trim().isEmpty()) userTexts.remove(key); else userTexts.put(key, text.trim());
        cache.clear();
        if (userFile == null) return;
        File dir = userFile.getParentFile();
        if (dir != null) dir.mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(userFile), StandardCharsets.UTF_8)) {
            w.write("# Eigene Tooltip-Texte (Name<TAB>Text, Zeilenumbruch als \\n). Nur für den privaten Gebrauch.\n");
            for (Map.Entry<String, String> e : userTexts.entrySet()) {
                w.write(e.getKey() + "\t" + e.getValue().replace("\r", "").replace("\n", "\\n") + "\n");
            }
        }
    }

    static String norm(String s) {
        return s.toLowerCase(Locale.GERMAN).replaceAll("\\s+", " ").trim();
    }

    /** Entfernt typische Zusätze wie Werte, Klammern, Doppelpunkt-Erweiterungen. */
    private static List<String> candidates(String raw) {
        List<String> c = new ArrayList<>();
        String s = raw.replaceAll("<[^>]*>", " ").replace(' ', ' ').replaceAll("\\s+", " ").trim();
        if (s.isEmpty() || s.length() > 120) return c;
        c.add(s);
        String noNum = s.replaceAll("[\\s:]*[-+]?\\d+\\s*$", "").trim();
        if (!noNum.equals(s)) c.add(noNum);
        int colon = s.indexOf(": ");
        if (colon > 0) c.add(s.substring(0, colon));
        int par = s.indexOf(" (");
        if (par > 0) c.add(s.substring(0, par));
        return c;
    }

    /** Sucht einen Eintrag zu einem angezeigten Text; null, wenn nichts passt. */
    public synchronized Match find(String raw) {
        if (raw == null) return null;
        Optional<Match> cached = cache.get(raw);
        if (cached != null) return cached.orElse(null);
        Match m = null;
        for (String c : candidates(raw)) {
            if (userTexts.containsKey(c)) { m = new Match(c, exact.get(norm(c)), c); break; }
            Entry e = exact.get(norm(c));
            if (e != null) { m = new Match(e.name, e, c); break; }
        }
        if (m == null) {
            String n = norm(raw.replaceAll("<[^>]*>", " "));
            for (Entry e : prefixes) {
                String pn = norm(e.name);
                if (pn.length() >= 4 && n.startsWith(pn)) {
                    String shown = raw.replaceAll("<[^>]*>", " ").trim();
                    String key = userTexts.containsKey(shown) ? shown : shown;
                    m = new Match(key, e, shown);
                    break;
                }
            }
        }
        if (cache.size() > 5000) cache.clear();
        cache.put(raw, Optional.ofNullable(m));
        return m;
    }

    public Entry lookupEntry(String name) {
        Match m = find(name);
        return m == null ? null : m.entry;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    /**
     * Baut das HTML für einen Tooltip.
     * @param extra zusätzliche Zeilen (z. B. Probe, Wert) – darf null sein
     */
    public String html(Match m, Map<String, String> extra) {
        return html(m, extra, null);
    }

    /**
     * @param verlauf Herkunft des Werts als Zeilen {Beschriftung, Text} – darf null sein
     */
    public String html(Match m, Map<String, String> extra, java.util.List<String[]> verlauf) {
        if (m == null) return null;
        StringBuilder sb = new StringBuilder("<html><div style='width:330px;padding:2px'>");
        sb.append("<b style='font-size:110%'>").append(esc(m.shown)).append("</b>");
        Entry e = m.entry;
        if (e != null) {
            sb.append("<br><span style='color:#8a8a8a'>").append(e.typName());
            if (!e.gruppe.isEmpty() && !e.gruppe.equals(e.typName())) sb.append(" · ").append(esc(e.gruppe));
            if (!e.spalte.isEmpty()) sb.append(" · Spalte ").append(esc(e.spalte));
            sb.append("</span>");
        }
        if (extra != null && !extra.isEmpty()) {
            sb.append("<table cellpadding=0 cellspacing=0 style='margin-top:4px'>");
            for (Map.Entry<String, String> x : extra.entrySet()) {
                if (x.getValue() == null || x.getValue().trim().isEmpty()) continue;
                sb.append("<tr><td style='color:#8a8a8a;padding-right:8px'>").append(esc(x.getKey()))
                  .append("</td><td>").append(esc(x.getValue())).append("</td></tr>");
            }
            sb.append("</table>");
        }
        if (verlauf != null && !verlauf.isEmpty()) {
            sb.append("<div style='margin-top:6px'><b>Herkunft des Werts</b></div>");
            sb.append("<table cellpadding=0 cellspacing=0>");
            for (String[] v : verlauf) {
                sb.append("<tr><td style='color:#8a8a8a;padding-right:8px;white-space:nowrap'>").append(esc(v[0]))
                  .append("</td><td>").append(esc(v[1])).append("</td></tr>");
            }
            sb.append("</table>");
        }
        String user = getUserText(m.key);
        if (user == null && e != null) user = getUserText(e.name);
        if (user != null) {
            sb.append("<div style='margin-top:6px'><b>Eigene Notiz</b><br>").append(esc(user)).append("</div>");
        }
        if (e != null && !e.text.isEmpty()) {
            sb.append("<div style='margin-top:6px'>").append(esc(e.text)).append("</div>");
        }
        sb.append("<div style='margin-top:6px;color:#9a9a9a;font-size:90%'>Strg+Umschalt+T: eigenen Text hinterlegen</div>");
        sb.append("</div></html>");
        return sb.toString();
    }
}
