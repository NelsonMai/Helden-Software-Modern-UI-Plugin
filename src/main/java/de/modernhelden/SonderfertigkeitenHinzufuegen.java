package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Dialog „Sonderfertigkeiten“ (Reiter Allgemein → Sonderfertigkeiten → Hinzufügen) in den modernen Designs.
 * Er besteht aus denselben Tabellen wie der Schritt „Sonderfertigkeiten“ der Erschaffung und bekommt deshalb
 * dieselbe Behandlung: Kopf mit Titel, Suchleiste über allen Unterreitern (mit Trefferzahl je Reiter),
 * sortierbare Spalten, hervorgehobene Überschriften, mehr Platz und ein Fertig-Knopf unten rechts.
 * Hinzugefügt und bezahlt wird weiter über die Knöpfe der Helden-Software; Sortieren und Filtern rechnen
 * Zeilennummern auf den Eintrag um (siehe {@link ErschaffungsTabellen}).
 */
final class SonderfertigkeitenHinzufuegen {
    static final String TITEL = "Sonderfertigkeiten";
    private static final String MARKE = "modernhelden.sfHinzufuegen";

    private SonderfertigkeitenHinzufuegen() { }

    /** Ist das der Hinzufügen-Dialog (und nicht ein anderer Dialog gleichen Titels)? */
    static boolean ist(JDialog d) {
        if (!TITEL.equals(d.getTitle())) return false;
        for (JTable t : ErschaffungsSchritte.alle(d.getContentPane(), JTable.class)) if (ErschaffungsTabellen.ist(t)) return true;
        return false;
    }

    static void anwenden(JDialog d) {
        if (Boolean.TRUE.equals(d.getRootPane().getClientProperty(MARKE)) || !ist(d)) return;
        Container cp = d.getContentPane();
        if (cp.getComponentCount() != 1 || !(cp.getComponent(0) instanceof JPanel)) return;
        JPanel huelle = (JPanel) cp.getComponent(0);
        if (!(huelle.getLayout() instanceof BorderLayout)) return;
        BorderLayout bl = (BorderLayout) huelle.getLayout();
        Component mitte = bl.getLayoutComponent(BorderLayout.CENTER);
        Component unten = bl.getLayoutComponent(BorderLayout.SOUTH);
        if (!(mitte instanceof JComponent) || bl.getLayoutComponent(BorderLayout.NORTH) != null) return;
        d.getRootPane().putClientProperty(MARKE, Boolean.TRUE);

        // Überschriften, sortierbare Tabellen, Tooltips – wie im Erschaffungs-Schritt
        ErschaffungsSchritte.stil(huelle, (JComponent) mitte);

        // ---- Kopf: Titel, Erklärung, Suchleiste
        JPanel kopf = new JPanel(new BorderLayout(0, 2));
        kopf.setOpaque(false);
        kopf.setBorder(new EmptyBorder(10, 14, 6, 14));
        JLabel titel = new JLabel("Sonderfertigkeit hinzufügen");
        titel.setFont(titel.getFont().deriveFont(Font.BOLD, titel.getFont().getSize2D() + 5f));
        JLabel erkl = new JLabel("Mit + auswählen; die Kosten werden sofort von den freien AP abgezogen. "
                + "Mit − unten wieder zurücknehmen.");
        erkl.setForeground(Theme.muted());
        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.setOpaque(false);
        text.add(titel, BorderLayout.NORTH);
        text.add(erkl, BorderLayout.CENTER);
        kopf.add(text, BorderLayout.NORTH);
        ErschaffungsSchritte.Suchleiste leiste = new ErschaffungsSchritte.Suchleiste(huelle, "Sonderfertigkeiten");
        kopf.add(leiste, BorderLayout.SOUTH);
        for (JComponent c : new JComponent[]{kopf, titel, erkl, text}) InventarLayout.eigen(c);
        huelle.add(kopf, BorderLayout.NORTH);
        ((JComponent) mitte).setBorder(BorderFactory.createCompoundBorder(((JComponent) mitte).getBorder(), new EmptyBorder(0, 8, 0, 8)));

        // ---- unten: „Ok“ der Helden-Software als Fertig-Knopf rechts
        if (unten instanceof AbstractButton) {
            AbstractButton ok = (AbstractButton) unten;
            huelle.remove(ok);
            ok.setText("Fertig");
            Color a = Theme.accent();
            ok.putClientProperty("FlatLaf.style", String.format(
                    "background: #%02x%02x%02x; foreground: #ffffff; font: bold", a.getRed(), a.getGreen(), a.getBlue()));
            ok.setMargin(new Insets(4, 22, 4, 22));
            JPanel leisteUnten = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            leisteUnten.setOpaque(false);
            leisteUnten.setBorder(new EmptyBorder(8, 14, 10, 14));
            leisteUnten.add(ok);
            InventarLayout.eigen(leisteUnten);
            huelle.add(leisteUnten, BorderLayout.SOUTH);
        }

        // Überschriften „Mögliche …“ / „Gewählte …“ fett
        for (JLabel l : ErschaffungsSchritte.alle((Container) mitte, JLabel.class)) {
            String t = String.valueOf(l.getText());
            if (t.startsWith("Mögliche") || t.startsWith("Gewählte")) l.setFont(l.getFont().deriveFont(Font.BOLD));
        }
        for (JTable t : ErschaffungsSchritte.alle((Container) mitte, JTable.class)) spalten(t);

        groesse(d);
        // Auswahl oben und Gewähltes unten etwa gleich groß
        for (JSplitPane sp : ErschaffungsSchritte.alle((Container) mitte, JSplitPane.class))
            if (sp.getOrientation() == JSplitPane.VERTICAL_SPLIT) { sp.setResizeWeight(0.62); SwingUtilities.invokeLater(() -> sp.setDividerLocation(0.62)); }

        // Kategorien/Treffer nachziehen, wenn sich die Tabellen ändern (Hinzufügen verschiebt Einträge)
        leiste.aktualisieren();
        Runnable nach = () -> SwingUtilities.invokeLater(() -> {
            for (JTable t : leiste.tabellen()) if (t.getClientProperty(MARKE) == null) {
                t.putClientProperty(MARKE, Boolean.TRUE);
                ErschaffungsTabellen.einrichten(t, r -> ErschaffungsSchritte.rueck(huelle, r));
                spalten(t);
                t.getModel().addTableModelListener(e -> leiste.aktualisieren());
            }
            leiste.aktualisieren();
        });
        for (JTable t : ErschaffungsSchritte.alle((Container) mitte, JTable.class)) {
            t.putClientProperty(MARKE, Boolean.TRUE);
            t.getModel().addTableModelListener(e -> nach.run());
        }
        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> { if (d.isShowing()) nach.run(); });
        timer.start();
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { timer.stop(); }
            @Override public void windowDeactivated(java.awt.event.WindowEvent e) { if (!d.isShowing()) timer.stop(); }
        });
        d.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentHidden(java.awt.event.ComponentEvent e) { timer.stop(); }
        });
        SwingUtilities.invokeLater(leiste.feld::requestFocusInWindow);
        huelle.revalidate();
        huelle.repaint();
    }

    /** Namensspalte breit genug für „Waffenloser Kampfstil: …“, Nebenspalten schmaler. */
    private static void spalten(JTable t) {
        javax.swing.table.TableColumnModel cm = t.getColumnModel();
        int n = cm.getColumnCount();
        for (int i = 0; i < n; i++) {
            javax.swing.table.TableColumn c = cm.getColumn(i);
            String k = String.valueOf(c.getHeaderValue()).trim();
            int b;
            if (k.equals("Sonderfertigkeit")) b = 260;
            else if (k.equals("SE")) b = 34;
            else if (k.equals("AP") || k.equals("Kosten")) b = 56;
            else if (k.equals("Lernmethode")) b = 110;
            else if (i == 0 && k.isEmpty()) b = 24;
            else if (i == n - 1 && k.isEmpty()) b = 56;
            else b = 100;
            c.setPreferredWidth(b);
        }
    }

    private static void groesse(JDialog dialog) {
        GraphicsConfiguration gc = dialog.getGraphicsConfiguration();
        if (gc == null) return;
        Rectangle s = gc.getBounds();
        Insets i = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int w = Math.min(820, s.width - i.left - i.right - 40), h = Math.min(660, s.height - i.top - i.bottom - 40);
        Rectangle r = dialog.getBounds();
        w = Math.max(w, r.width); h = Math.max(h, r.height);
        int x = Math.max(s.x + i.left, Math.min(r.x + (r.width - w) / 2, s.x + s.width - i.right - w));
        int y = Math.max(s.y + i.top, Math.min(r.y + (r.height - h) / 2, s.y + s.height - i.bottom - h));
        dialog.setBounds(x, y, w, h);
    }
}
