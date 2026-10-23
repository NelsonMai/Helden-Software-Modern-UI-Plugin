package de.modernhelden;

import javax.swing.*;
import java.awt.*;

/**
 * Hauptreiter der Helden-Software in den modernen Designs: Der Plugin-Reiter heißt „Kompaktansicht“, der
 * Verlauf bekommt einen eigenen Reiter. Im Design „Original“ bleibt alles wie in der Helden-Software
 * („Plugin: Modern-UI“, Verlauf als Unterreiter).
 *
 * Die Helden-Software spricht ihre eigenen Reiter und die Plugin-Reiter über deren Position an. Der Verlauf
 * wird deshalb nur ans Ende angehängt, so dass sich keine bestehende Position verschiebt.
 */
final class HauptReiter {

    private HauptReiter() {}

    static final String KOMPAKT = "Kompaktansicht";
    static final String VERLAUF = "Verlauf";

    static void pruefen(HeroPanel panel, String pluginTitel) {
        if (panel == null) return;
        Container p = panel.getParent();
        if (!(p instanceof JTabbedPane)) return;
        JTabbedPane tabs = (JTabbedPane) p;
        boolean modern = Theme.isModern();
        int i = tabs.indexOfComponent(panel);
        if (i >= 0) {
            String titel = modern ? KOMPAKT : pluginTitel;
            if (!titel.equals(tabs.getTitleAt(i))) tabs.setTitleAt(i, titel);
        }
        JComponent v = panel.verlaufReiter();
        int j = tabs.indexOfComponent(v);
        if (modern && j < 0) {
            panel.setVerlaufExtern(true);
            tabs.addTab(VERLAUF, v);
        } else if (!modern && j >= 0) {
            if (tabs.getSelectedIndex() == j && i >= 0) tabs.setSelectedIndex(i);
            tabs.removeTabAt(j);
            panel.setVerlaufExtern(false);
        } else {
            panel.setVerlaufExtern(modern);
        }
    }
}
