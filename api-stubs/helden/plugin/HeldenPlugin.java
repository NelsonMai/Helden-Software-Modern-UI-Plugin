package helden.plugin;

/*
 * Platzhalter NUR zum Kompilieren (wird nicht ins Plugin-JAR gepackt).
 * Zur Laufzeit stellt die Helden-Software die echte Schnittstelle bereit.
 * Enthält ausschließlich die Signaturen der öffentlichen Plugin-Schnittstelle.
 */
public interface HeldenPlugin {
    String SIMPLE = "simple execute";

    void doWork(javax.swing.JFrame frame);

    javax.swing.ImageIcon getIcon();

    String getMenuName();

    String getToolTipText();

    String getType();
}
