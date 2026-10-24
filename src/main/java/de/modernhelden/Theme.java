package de.modernhelden;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/** Schaltet das Erscheinungsbild der gesamten Helden-Software um. */
public final class Theme {

    public static final String HELL = "hell", DUNKEL = "dunkel", ORIGINAL = "original";

    private static LookAndFeel originalLaf;
    private static String current = ORIGINAL;

    private Theme() {}

    public static String current() { return current; }

    public static boolean isModern() { return !ORIGINAL.equals(current); }

    public static boolean isDark() { return DUNKEL.equals(current); }

    public static void apply(String mode) {
        if (mode == null) mode = HELL;
        if (originalLaf == null) originalLaf = UIManager.getLookAndFeel();
        System.setProperty("flatlaf.useNativeLibrary", "false");   // keine nativen DLLs entpacken
        try {
            if (ORIGINAL.equals(mode)) {
                if (originalLaf != null) UIManager.setLookAndFeel(originalLaf);
            } else {
                Map<String, String> extra = new HashMap<>();
                extra.put("@accentColor", DUNKEL.equals(mode) ? "#d08b4c" : "#a4552a");
                FlatLaf.setGlobalExtraDefaults(extra);
                FlatLaf laf = DUNKEL.equals(mode) ? new FlatDarkLaf() : new FlatLightLaf();
                UIManager.setLookAndFeel(laf);
                // Plugin wird über einen eigenen ClassLoader geladen – Swing muss die UI-Klassen dort finden
                UIManager.getLookAndFeelDefaults().put("ClassLoader", Theme.class.getClassLoader());
                UIDefaults ld = UIManager.getLookAndFeelDefaults();   // nur für dieses Design, nicht global
                ld.put("Component.arc", 8);
                ld.put("Button.arc", 8);
                ld.put("TextComponent.arc", 6);
                ld.put("ScrollBar.showButtons", false);
                ld.put("ScrollBar.width", 12);
                ld.put("TabbedPane.showTabSeparators", true);
                ld.put("Table.showHorizontalLines", true);
                ld.put("Table.intercellSpacing", new Dimension(0, 1));
                ld.put("ToolTip.border", BorderFactory.createEmptyBorder(6, 8, 6, 8));
                felderUnterscheidbar(DUNKEL.equals(mode));
            }
            current = mode;
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        for (Window w : Window.getWindows()) {
            try {
                SwingUtilities.updateComponentTreeUI(w);
            } catch (RuntimeException ex) {
                // Komponente wird gerade noch aufgebaut – später erneut versuchen
                final Window win = w;
                SwingUtilities.invokeLater(() -> {
                    try { SwingUtilities.updateComponentTreeUI(win); } catch (RuntimeException ignore) { }
                });
            }
            w.repaint();
        }
        // erst nach dem Baum-Update, damit die Tabellen ihre neuen Renderer schon haben
        SwingUtilities.invokeLater(Theme::aktualisiereTabellenRenderer);
        // eigene Symbole nur in den modernen Designs
        final boolean modernJetzt = isModern();
        SwingUtilities.invokeLater(() -> IconSwap.setModern(modernJetzt));
    }

    /**
     * Farben, mit denen sich Felder, Zeilen und Spalten deutlicher voneinander abheben:
     * Zebra-Streifen, sichtbare Gitterlinien, lesbare Markierung, klar abgesetzte Eingabefelder.
     */
    private static void felderUnterscheidbar(boolean dunkel) {
        // nur in die Standardwerte des aktiven FlatLaf-Designs schreiben – das Original-Design bleibt unberührt
        UIDefaults d = UIManager.getLookAndFeelDefaults();
        if (dunkel) {
            d.put("Table.background", new ColorUIResource(0x45484a));
            d.put("Table.alternateRowColor", new ColorUIResource(0x3a3d3f));
            d.put("Table.gridColor", new ColorUIResource(0x5a5e61));
            d.put("Table.selectionBackground", new ColorUIResource(0x8a5532));
            d.put("Table.selectionForeground", new ColorUIResource(Color.WHITE));
            d.put("Table.selectionInactiveBackground", new ColorUIResource(0x6a4630));
            d.put("Table.selectionInactiveForeground", new ColorUIResource(Color.WHITE));
            d.put("TableHeader.background", new ColorUIResource(0x34373a));
            d.put("TableHeader.separatorColor", new ColorUIResource(0x5a5e61));
            d.put("TableHeader.bottomSeparatorColor", new ColorUIResource(0x6e7377));
            d.put("Component.borderColor", new ColorUIResource(0x666b70));
            d.put("TextField.background", new ColorUIResource(0x2f3133));
            d.put("FormattedTextField.background", new ColorUIResource(0x2f3133));
            d.put("TextField.inactiveBackground", new ColorUIResource(0x3c3f41));
            d.put("ComboBox.background", new ColorUIResource(0x2f3133));
            d.put("List.background", new ColorUIResource(0x45484a));
            d.put("ScrollPane.background", new ColorUIResource(0x45484a));
        } else {
            d.put("Table.background", new ColorUIResource(Color.WHITE));
            d.put("Table.alternateRowColor", new ColorUIResource(0xf6f1eb));
            d.put("Table.gridColor", new ColorUIResource(0xe2dbd2));
            d.put("Table.selectionBackground", new ColorUIResource(0xf1d6c3));
            d.put("Table.selectionForeground", new ColorUIResource(0x1e1e1e));
            d.put("Table.selectionInactiveBackground", new ColorUIResource(0xf5e4d8));
            d.put("Table.selectionInactiveForeground", new ColorUIResource(0x1e1e1e));
            d.put("TableHeader.background", new ColorUIResource(0xece6de));
            d.put("TableHeader.separatorColor", new ColorUIResource(0xd6cec4));
            d.put("TableHeader.bottomSeparatorColor", new ColorUIResource(0xc9bfb3));
            d.put("Component.borderColor", new ColorUIResource(0xbdb5ab));
            d.put("TextField.background", new ColorUIResource(Color.WHITE));
            d.put("FormattedTextField.background", new ColorUIResource(Color.WHITE));
            d.put("TextField.inactiveBackground", new ColorUIResource(0xeeebe7));
            d.put("ComboBox.background", new ColorUIResource(Color.WHITE));
        }
        d.put("Table.showVerticalLines", true);
        d.put("Table.showHorizontalLines", true);
        d.put("Table.intercellSpacing", new Dimension(1, 1));
        d.put("Component.focusWidth", 1);
    }

    /**
     * Manche Tabellen der Helden-Software (z. B. „Info“-Knöpfe bei Vor-/Nachteilen und
     * Sonderfertigkeiten) erzeugen ihre Zellen-Komponenten einmal und heben sie dauerhaft
     * auf. Diese Komponenten hängen nicht im Fensterbaum und behielten sonst das Design,
     * das beim ersten Zeichnen aktiv war – dadurch mischten sich mehrere Designs.
     */
    public static void aktualisiereTabellenRenderer() {
        for (Window w : Window.getWindows()) {
            try { tabellenIn(w); } catch (RuntimeException ignore) { }
        }
    }

    private static void tabellenIn(Component c) {
        if (c instanceof javax.swing.JTable) aktualisiere((javax.swing.JTable) c);
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) tabellenIn(k);
        }
    }

    private static void aktualisiere(javax.swing.JTable t) {
        java.util.Set<Component> erledigt =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        javax.swing.table.TableColumnModel cm = t.getColumnModel();
        for (int ci = 0; ci < cm.getColumnCount(); ci++) {
            javax.swing.table.TableColumn col = cm.getColumn(ci);
            Object r = col.getCellRenderer(), e = col.getCellEditor();
            if (r instanceof TabellenStil.Zebra) {
                // Hülle für Zebra-Streifen: der eigentliche Renderer steckt darin
                Object innen = ((TabellenStil.Zebra) r).original;
                if (innen instanceof Component) {
                    if (erledigt.add((Component) innen)) SwingUtilities.updateComponentTreeUI((Component) innen);
                    continue;
                }
                e = e == innen ? r : e;
                if (innen == null) r = null;     // Hülle um den Standard-Renderer der Tabelle
            }
            // ohne eigenen Spalten-Renderer gilt der Standard-Renderer der Tabelle für diesen Spaltentyp –
            // auch der kann Komponenten zwischenspeichern (z. B. „+1“-Knöpfe in der Talentliste)
            if (r == null) {
                r = t.getDefaultRenderer(t.getColumnClass(ci));
                if (e == null) e = t.getDefaultEditor(t.getColumnClass(ci));
            }
            // Komponenten-Renderer aktualisiert JTable.updateUI bereits selbst
            if (r == null || r instanceof Component) continue;
            javax.swing.table.TableCellRenderer tr = (javax.swing.table.TableCellRenderer) r;
            boolean auchEditor = e == r;   // gleicher Cache für Anzeige und Bearbeitung
            // über alle Zeilen des Modells – auch die, die gerade ausgefiltert sind (Suche, Talentgruppen);
            // deren zwischengespeicherte Knöpfe behielten sonst das alte Design
            javax.swing.table.TableModel m = t.getModel();
            int mc = t.convertColumnIndexToModel(ci);
            for (int mr = 0; mr < m.getRowCount(); mr++) {
                Component k;
                try {
                    int vr = t.getRowSorter() == null ? mr : t.convertRowIndexToView(mr);
                    k = tr.getTableCellRendererComponent(t, m.getValueAt(mr, mc), false, false, Math.max(vr, 0), ci);
                } catch (RuntimeException ex) { continue; }
                if (k != null && erledigt.add(k)) SwingUtilities.updateComponentTreeUI(k);
            }
            if (auchEditor && t.isEditing() && t.getEditorComponent() != null) {
                SwingUtilities.updateComponentTreeUI(t.getEditorComponent());
            }
        }
        if (!erledigt.isEmpty()) t.repaint();
    }

    // Farben für die eigene Ansicht
    public static Color card() { return color("Panel.background", 0.035f); }

    /** Hintergrund für Zeile i einer Liste/Tabelle (Zebra-Streifen in den modernen Designs). */
    public static Color zeile(int i) {
        Color basis = UIManager.getColor("Table.background");
        Color alt = UIManager.getColor("Table.alternateRowColor");
        if (basis == null) basis = Color.WHITE;
        if (alt == null) alt = card();
        return i % 2 == 1 ? alt : basis;
    }

    /** Linien zwischen Zeilen, Spalten und um Kacheln. */
    public static Color trenner() {
        Color c = isModern() ? UIManager.getColor("Table.gridColor") : null;
        return c != null ? c : card().darker();
    }

    /** Hintergrund von Gruppen-Überschriften in Tabellen. */
    public static Color kopfzeile() {
        Color c = isModern() ? UIManager.getColor("TableHeader.background") : null;
        return c != null ? c : card();
    }

    /** Hintergrund der Werte-Kacheln: hebt sich deutlich vom Fenster ab. */
    public static Color kachel() {
        Color c = isModern() ? UIManager.getColor("Table.background") : null;
        return c != null ? c : card();
    }
    public static Color muted() {
        if (!isModern()) return new Color(0x6b5a3e);   // gut lesbar auf dem Pergament-Design
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : Color.GRAY;
    }
    public static Color accent() {
        Color c = UIManager.getColor("Component.accentColor");
        if (c == null) c = UIManager.getColor("Component.focusColor");
        return c != null ? c : new Color(0xa4552a);
    }

    private static Color color(String key, float shift) {
        Color b = UIManager.getColor(key);
        if (b == null) b = Color.WHITE;
        boolean dark = (b.getRed() + b.getGreen() + b.getBlue()) / 3 < 128;
        int d = Math.round(255 * shift) * (dark ? 1 : -1);
        return new Color(clamp(b.getRed() + d), clamp(b.getGreen() + d), clamp(b.getBlue() + d));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
