package de.modernhelden.update;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Automatische Aktualisierung des Modern-UI-Plugins.
 *
 * Die Helden-Software aktualisiert sich selbst über ihren „autoUpdater“, Plugins aber nicht. Ist dort
 * „automatisch aktualisieren“ eingeschaltet, prüft das Plugin höchstens einmal am Tag das neueste Release
 * auf GitHub, lädt eine neuere Version herunter (SHA-256 aus der GitHub-Antwort, Prüfung des JAR-Inhalts)
 * und ersetzt die Plugin-Datei nach dem Beenden der Helden-Software über einen kleinen Hilfsprozess.
 * Die neue Version ist ab dem nächsten Start aktiv; die bisherige wird als Sicherung aufbewahrt.
 */
public final class PluginUpdate {

    static final String REPO = "NelsonMai/Helden-Software-Modern-UI-Plugin";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String DOWNLOAD_PREFIX = "https://github.com/" + REPO + "/releases/download/";
    private static final String ASSET = "ModernUI-Plugin.jar";
    private static final String PLUGIN_KLASSE = "de.modernhelden.ModernHeldenPlugin.class";
    private static final long MAX_GROESSE = 50L * 1024 * 1024;

    private final File ordner;          // modern-ui/update
    private final File pluginJar;       // die laufende Plugin-Datei (null, wenn nicht als JAR geladen)
    private final String version;
    private final Frame frame;
    private final Properties status = new Properties();
    private static boolean hookAktiv;
    private volatile boolean laeuft;

    /** Ergebnis einer Prüfung, für die Meldung an den Benutzer. */
    public static final class Ergebnis {
        public final boolean neu;           // neue Version heruntergeladen (oder schon bereit)
        public final String text;
        Ergebnis(boolean neu, String text) { this.neu = neu; this.text = text; }
    }

    public PluginUpdate(File baseDir, Class<?> pluginKlasse, String version, Frame frame) {
        this.ordner = new File(baseDir, "update");
        this.pluginJar = jarVon(pluginKlasse);
        this.version = version;
        this.frame = frame;
        laden();
    }

    // ------------------------------------------------------------------ Start

    /** Beim Start: abgeschlossene Aktualisierung melden, ausstehende erneut vormerken, ggf. täglich prüfen. */
    public void beimStart() {
        String aus = status.getProperty("ausstehend");
        if (aus != null) {
            if (vergleiche(version, aus) >= 0) {
                status.remove("ausstehend");
                status.remove("datei");
                speichern();
                final String v = version;
                melden("Modern-UI wurde auf Version " + v + " aktualisiert.", JOptionPane.INFORMATION_MESSAGE, 0);
            } else if (new File(status.getProperty("datei", "")).isFile()) {
                installationVormerken(new File(status.getProperty("datei")));   // letzter Versuch nicht geklappt
            }
        }
        if (!heldenAutoUpdate()) return;
        String heute = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        if (heute.equals(status.getProperty("letztePruefung"))) return;
        Thread t = new Thread(() -> {
            try { Thread.sleep(20_000); } catch (InterruptedException e) { return; }
            Ergebnis e = pruefen();
            if (e.neu) melden(e.text, JOptionPane.INFORMATION_MESSAGE, 0);
        }, "Modern-UI-Update");
        t.setDaemon(true);
        t.start();
    }

    /** Ist in der Helden-Software „automatisch aktualisieren“ eingeschaltet (Voraussetzung für den autoUpdater)? */
    public static boolean heldenAutoUpdate() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> c = Class.forName("helden.framework.Einstellungen", true, cl != null ? cl : PluginUpdate.class.getClassLoader());
            Object e = c.getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(c.getMethod("isAutoUpdate").invoke(e));
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ Prüfen und Herunterladen

    /** Prüft auf GitHub und lädt eine neuere Version herunter. Läuft im Hintergrund (nicht im EDT aufrufen). */
    public synchronized Ergebnis pruefen() {
        if (pluginJar == null) return new Ergebnis(false, "Das Plugin wurde nicht aus einer JAR-Datei geladen – keine automatische Aktualisierung möglich.");
        laeuft = true;
        try {
            status.setProperty("letztePruefung", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            speichern();
            String api = apiUrl();
            Object j = Json.parse(lesen(api));
            if (!(j instanceof Map)) throw new IOException("unerwartete Antwort");
            Map<?, ?> rel = (Map<?, ?>) j;
            if (Boolean.TRUE.equals(rel.get("draft")) || Boolean.TRUE.equals(rel.get("prerelease")))
                return new Ergebnis(false, "Modern-UI ist aktuell (Version " + version + ").");
            String tag = String.valueOf(rel.get("tag_name"));
            String online = versionAus(tag);
            if (online == null || vergleiche(online, version) <= 0)
                return new Ergebnis(false, "Modern-UI ist aktuell (Version " + version + ").");
            String aus = status.getProperty("ausstehend");
            if (aus != null && vergleiche(aus, online) >= 0 && new File(status.getProperty("datei", "")).isFile())
                return new Ergebnis(true, bereitText(aus));

            Map<?, ?> asset = null;
            Object assets = rel.get("assets");
            if (assets instanceof List) for (Object o : (List<?>) assets)
                if (o instanceof Map && ASSET.equals(((Map<?, ?>) o).get("name"))) asset = (Map<?, ?>) o;
            if (asset == null) throw new IOException("Release " + tag + " enthält keine " + ASSET);
            String url = String.valueOf(asset.get("browser_download_url"));
            if (!url.startsWith(downloadPrefix(api))) throw new IOException("unerwartete Download-Adresse");
            String digest = asset.get("digest") instanceof String ? (String) asset.get("digest") : null;

            ordner.mkdirs();
            File ziel = new File(ordner, "ModernUI-Plugin-" + online + ".jar");
            File tmp = new File(ordner, ziel.getName() + ".laden");
            String sha = herunterladen(url, tmp);
            if (digest != null && digest.startsWith("sha256:") && !digest.substring(7).equalsIgnoreCase(sha)) {
                tmp.delete();
                throw new IOException("Prüfsumme stimmt nicht – Download verworfen");
            }
            String jarVersion = pruefeJar(tmp);
            if (vergleiche(jarVersion, version) <= 0) {
                tmp.delete();
                throw new IOException("heruntergeladene Datei ist nicht neuer (" + jarVersion + ")");
            }
            if (ziel.exists()) ziel.delete();
            if (!tmp.renameTo(ziel)) throw new IOException("Datei konnte nicht umbenannt werden");
            // ältere, nicht installierte Downloads aufräumen
            File[] alt = ordner.listFiles((d, n) -> n.startsWith("ModernUI-Plugin-") && n.endsWith(".jar") && !n.equals(ziel.getName()));
            if (alt != null) for (File f : alt) f.delete();
            status.setProperty("ausstehend", jarVersion);
            status.setProperty("datei", ziel.getAbsolutePath());
            speichern();
            installationVormerken(ziel);
            return new Ergebnis(true, bereitText(jarVersion));
        } catch (Exception ex) {
            // abgebrochene oder verworfene Downloads nicht liegen lassen
            File[] rest = ordner.listFiles((d, n) -> n.endsWith(".laden"));
            if (rest != null) for (File f : rest) f.delete();
            return new Ergebnis(false, "Update-Prüfung fehlgeschlagen: " + ex.getMessage());
        } finally {
            laeuft = false;
        }
    }

    private String bereitText(String v) {
        return "<html>Modern-UI <b>" + v + "</b> wurde heruntergeladen (installiert: " + version + ").<br>"
                + "Die neue Version wird beim Beenden der Helden-Software installiert<br>und ist ab dem nächsten Start aktiv.</html>";
    }

    private String herunterladen(String url, File ziel) throws Exception {
        HttpURLConnection c = verbinden(url, "application/octet-stream");
        long laenge = c.getContentLengthLong();
        if (laenge > MAX_GROESSE) throw new IOException("Datei zu groß");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long gesamt = 0;
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(ziel)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                gesamt += n;
                if (gesamt > MAX_GROESSE) throw new IOException("Datei zu groß");
                md.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Ist es wirklich dieses Plugin (inkl. Installationshelfer)? Liefert die enthaltene Version. */
    private static String pruefeJar(File f) throws IOException {
        try (JarFile jar = new JarFile(f, true)) {
            Manifest m = jar.getManifest();
            if (m == null) throw new IOException("kein Manifest");
            String klasse = m.getMainAttributes().getValue("HeldenPluginClass");
            String v = m.getMainAttributes().getValue("Implementation-Version");
            if (!PLUGIN_KLASSE.equals(klasse)) throw new IOException("kein Modern-UI-Plugin");
            if (jar.getEntry("de/modernhelden/ModernHeldenPlugin.class") == null
                    || jar.getEntry("de/modernhelden/update/UpdateInstaller.class") == null)
                throw new IOException("unvollständige Plugin-Datei");
            if (v == null || versionAus(v) == null) throw new IOException("keine Versionsangabe");
            return versionAus(v);
        }
    }

    private static String lesen(String url) throws IOException {
        HttpURLConnection c = verbinden(url, "application/vnd.github+json");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
                if (b.size() > 2_000_000) throw new IOException("Antwort zu groß");
            }
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static HttpURLConnection verbinden(String url, String accept) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(60_000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("User-Agent", "Modern-UI-Plugin");
        int code = c.getResponseCode();
        if (code == 404) throw new IOException("noch kein Release veröffentlicht");
        if (code / 100 != 2) throw new IOException("Server antwortet mit " + code);
        return c;
    }

    // ------------------------------------------------------------------ Installation nach dem Beenden

    private synchronized void installationVormerken(File datei) {
        if (hookAktiv || pluginJar == null || !datei.isFile()) return;
        hookAktiv = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                File neu = new File(status.getProperty("datei", ""));   // die zuletzt heruntergeladene Version
                if (!neu.isFile()) return;
                File log = new File(ordner, "update.log");
                File sicherung = new File(ordner, "ModernUI-Plugin-vorher.jar");
                List<String> cmd = new ArrayList<>(Arrays.asList(javaProgramm(), "-cp", neu.getAbsolutePath(),
                        UpdateInstaller.class.getName(), neu.getAbsolutePath(), pluginJar.getAbsolutePath(),
                        sicherung.getAbsolutePath(), log.getAbsolutePath()));
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(ordner);
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
                pb.start();
            } catch (Throwable ignore) {
                // beim nächsten Start wird erneut versucht
            }
        }, "Modern-UI-Update-Installation"));
    }

    private static String javaProgramm() {
        File bin = new File(System.getProperty("java.home"), "bin");
        for (String n : new String[]{"javaw.exe", "java.exe", "java"}) {
            File f = new File(bin, n);
            if (f.isFile()) return f.getAbsolutePath();
        }
        return "java";
    }

    // ------------------------------------------------------------------ Hilfen

    public boolean laeuft() { return laeuft; }

    /** Für Tests: Debug-Ordner kann eine andere Adresse vorgeben (modern-ui/debug/update-api.txt). */
    private String apiUrl() {
        File f = new File(new File(ordner.getParentFile(), "debug"), "update-api.txt");
        if (f.isFile()) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String l = r.readLine();
                if (l != null && !l.trim().isEmpty()) return l.trim();
            } catch (IOException ignore) { }
        }
        return API;
    }

    private static String downloadPrefix(String api) {
        if (API.equals(api)) return DOWNLOAD_PREFIX;
        int i = api.indexOf("/", api.indexOf("//") + 2);
        return i > 0 ? api.substring(0, i + 1) : api;   // Testserver: gleicher Host
    }

    /** Nicht-modale Meldung – erst, wenn das Hauptfenster sichtbar ist (beim Start bis zu 60 s warten). */
    private void melden(final String text, final int typ, final int schliessenNachMs) {
        final long bis = System.currentTimeMillis() + 60_000L;
        final javax.swing.Timer warte = new javax.swing.Timer(500, null);
        final int[] stabil = {0};
        warte.addActionListener(ev -> {
            // erst wenn das Fenster ein paar Sekunden sichtbar ist – dann ist auch das Design umgeschaltet
            stabil[0] = frame == null || frame.isShowing() ? stabil[0] + 1 : 0;
            if (stabil[0] < 6 && System.currentTimeMillis() < bis) return;
            warte.stop();
            JOptionPane p = new JOptionPane(text, typ);
            JDialog d = p.createDialog(frame, "Modern-UI");
            d.setModal(false);
            SwingUtilities.updateComponentTreeUI(d);
            d.pack();
            d.setLocationRelativeTo(frame);
            if (schliessenNachMs > 0) {
                javax.swing.Timer t = new javax.swing.Timer(schliessenNachMs, e -> d.dispose());
                t.setRepeats(false);
                t.start();
            }
            d.setVisible(true);
        });
        SwingUtilities.invokeLater(warte::start);
    }

    private static File jarVon(Class<?> k) {
        try {
            File f = new File(k.getProtectionDomain().getCodeSource().getLocation().toURI());
            return f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar") ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** „v.1.15.2“, „v1.16.0“, „1.16“ → „1.15.2“ usw. */
    static String versionAus(String s) {
        if (s == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(\\.\\d+)*").matcher(s);
        return m.find() ? m.group() : null;
    }

    static int vergleiche(String a, String b) {
        String[] x = versionAus(a) == null ? new String[0] : versionAus(a).split("\\.");
        String[] y = versionAus(b) == null ? new String[0] : versionAus(b).split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length ? Long.parseLong(x[i]) : 0, q = i < y.length ? Long.parseLong(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private void laden() {
        File f = new File(ordner, "status.properties");
        if (!f.isFile()) return;
        try (InputStream in = new FileInputStream(f)) { status.load(in); } catch (IOException ignore) { }
    }

    private void speichern() {
        ordner.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(ordner, "status.properties"))) {
            status.store(out, "Modern-UI Aktualisierung");
        } catch (IOException ignore) { }
    }
}
