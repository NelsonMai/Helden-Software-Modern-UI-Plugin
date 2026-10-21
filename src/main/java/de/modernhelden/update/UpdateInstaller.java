package de.modernhelden.update;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Eigenständiger Hilfsprozess: ersetzt nach dem Beenden der Helden-Software die Plugin-Datei
 * durch die heruntergeladene neue Version. Unter Windows ist die Datei gesperrt, solange die
 * Helden-Software läuft – deshalb wird bis zu drei Minuten lang wiederholt.
 *
 * Aufruf: java -cp &lt;neu.jar&gt; de.modernhelden.update.UpdateInstaller &lt;neu.jar&gt; &lt;ziel.jar&gt; &lt;sicherung.jar&gt; &lt;log&gt;
 */
public final class UpdateInstaller {

    private UpdateInstaller() {}

    public static void main(String[] a) {
        if (a.length < 4) return;
        File neu = new File(a[0]), ziel = new File(a[1]), sicherung = new File(a[2]), log = new File(a[3]);
        log(log, "Start: " + neu + " -> " + ziel);
        long ende = System.currentTimeMillis() + 180_000L;
        boolean gesichert = false;
        IOException letzter = null;
        while (System.currentTimeMillis() < ende) {
            try {
                if (!neu.isFile()) { log(log, "Neue Datei fehlt – Abbruch"); return; }
                if (!gesichert && ziel.isFile()) {
                    Files.copy(ziel.toPath(), sicherung.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    gesichert = true;
                }
                // erst neben das Ziel kopieren, dann austauschen – so bleibt nie eine halbe Datei zurück
                File tmp = new File(ziel.getParentFile(), ziel.getName() + ".neu");
                Files.copy(neu.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(tmp.toPath(), ziel.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomar) {
                    Files.move(tmp.toPath(), ziel.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                Files.deleteIfExists(neu.toPath());
                log(log, "OK");
                return;
            } catch (IOException ex) {
                letzter = ex;   // Datei noch gesperrt – die Helden-Software beendet sich gerade
                new File(ziel.getParentFile(), ziel.getName() + ".neu").delete();
                try { Thread.sleep(1000); } catch (InterruptedException ie) { return; }
            }
        }
        log(log, "FEHLER: " + letzter);
    }

    private static void log(File f, String t) {
        try (FileWriter w = new FileWriter(f, true)) {
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "  " + t + System.lineSeparator());
        } catch (IOException ignore) { }
    }
}
