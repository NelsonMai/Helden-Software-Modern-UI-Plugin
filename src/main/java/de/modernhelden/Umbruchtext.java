package de.modernhelden;

import javax.swing.*;
import java.awt.*;

/** Text wie ein Label, der bei Platzmangel umbricht (Höhe passt sich der Breite an). */
final class Umbruchtext extends JTextArea {
    int letzteBreite = -1;
    Umbruchtext(String text) {
        super();
        setLineWrap(true);
        setWrapStyleWord(true);
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        // rechts etwas Luft für Schriften mit Überhang (z. B. Segoe UI unter Windows)
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        setHighlighter(null);
        setCursor(Cursor.getDefaultCursor());
        // sonst rollt die umgebende Liste beim Füllen zur Schreibmarke
        if (getCaret() instanceof javax.swing.text.DefaultCaret)
            ((javax.swing.text.DefaultCaret) getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        setText(text);
    }
    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(40, d.height);
    }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height); }
    @Override public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        // neue Breite → andere Zeilenzahl: Zeile neu anordnen lassen
        if (w != letzteBreite) {
            letzteBreite = w;
            SwingUtilities.invokeLater(() -> {
                Container p = getParent();
                if (p != null) { p.invalidate(); if (p.getParent() != null) p.getParent().revalidate(); }
            });
        }
    }
}

/** HTML-Text (z. B. Regeln), der sich der verfügbaren Breite anpasst statt eine feste Breite zu erzwingen. */
final class HtmlText extends JLabel {
    int letzteBreite = -1;
    HtmlText(String html) { super(html); setVerticalAlignment(TOP); setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6)); }
    private int breite() {
        if (getWidth() > 0) return getWidth();
        for (Container p = getParent(); p != null; p = p.getParent()) if (p.getWidth() > 0) return p.getWidth() - 60;
        return 500;
    }
    @Override public Dimension getPreferredSize() {
        Object o = getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
        if (!(o instanceof javax.swing.text.View)) return super.getPreferredSize();
        javax.swing.text.View v = (javax.swing.text.View) o;
        Insets i = getInsets();
        int w = Math.max(100, breite() - i.left - i.right - 10);   // Reserve: lieber eine Zeile mehr als abgeschnitten
        v.setSize(w, 0);
        int h = (int) Math.ceil(v.getPreferredSpan(javax.swing.text.View.Y_AXIS));
        return new Dimension(100, h + i.top + i.bottom);
    }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        if (w != letzteBreite) {
            letzteBreite = w;
            SwingUtilities.invokeLater(() -> { invalidate(); Container p = getParent(); if (p != null) p.revalidate(); });
        }
    }
}
