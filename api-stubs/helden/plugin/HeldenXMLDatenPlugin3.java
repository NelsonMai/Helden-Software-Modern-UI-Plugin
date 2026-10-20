package helden.plugin;

import helden.plugin.datenxmlplugin.DatenAustausch3Interface;

/*
 * Platzhalter NUR zum Kompilieren (wird nicht ins Plugin-JAR gepackt).
 * Zur Laufzeit stellt die Helden-Software die echte Schnittstelle bereit.
 */
public interface HeldenXMLDatenPlugin3 extends HeldenPlugin {
    String DATEN = "DatenXMLPlugin3";

    void click();

    javax.swing.JComponent getPanel();

    java.util.ArrayList<javax.swing.JComponent> getUntermenus();

    boolean hatMenu();

    boolean hatTab();

    void init(DatenAustausch3Interface daten, javax.swing.JFrame frame);
}
