package de.modernhelden;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
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
                UIManager.put("Component.arc", 8);
                UIManager.put("Button.arc", 8);
                UIManager.put("TextComponent.arc", 6);
                UIManager.put("ScrollBar.showButtons", false);
                UIManager.put("ScrollBar.width", 12);
                UIManager.put("TabbedPane.showTabSeparators", true);
                UIManager.put("Table.showHorizontalLines", true);
                UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
                UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(6, 8, 6, 8));
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
    }

    // Farben für die eigene Ansicht
    public static Color card() { return color("Panel.background", 0.035f); }
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
