package helden.plugin.datenxmlplugin;

/*
 * Platzhalter NUR zum Kompilieren (wird nicht ins Plugin-JAR gepackt).
 * Zur Laufzeit stellt die Helden-Software die echte Schnittstelle bereit.
 */
public interface DatenAustausch3Interface {
    void addChangeListener(javax.swing.event.ChangeListener listener);

    Object exec(org.w3c.dom.Document request);
}
