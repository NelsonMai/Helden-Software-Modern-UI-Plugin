package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;
import java.util.List;

/**
 * Reiter „Eigenschaften“ in den modernen Designs: Eigenschaften mit Startwert, Steigerungskosten und Wirkung,
 * Energien und Magieresistenz mit Rechenweg, Kampf-Basiswerte sowie Sozialstatus und Gilde.
 *
 * Gesteigert wird weiter von der Helden-Software: Die eigenen Knöpfe, Häkchen und Felder lösen nur deren
 * Knöpfe, Häkchen und Felder aus. Ihre Ansicht bleibt unsichtbar im Fenster; im Design „Original“ wird sie
 * wieder angezeigt.
 */
final class EigenschaftenLayout {

    private EigenschaftenLayout() {}

    private static final String MARKE = "modernhelden.eigenschaften";
    private static final Map<JComponent, Zustand> alle = new WeakHashMap<>();

    /** Name in der Helden-Software → Kürzel, Grundlage für Formeln. */
    private static final String[][] EIGENSCHAFTEN = {
            {"Mut", "MU"}, {"Klugheit", "KL"}, {"Intuition", "IN"}, {"Charisma", "CH"},
            {"Fingerfertigkeit", "FF"}, {"Gewandtheit", "GE"}, {"Konstitution", "KO"}, {"Körperkraft", "KK"}};

    /** Name → Kürzel, Formel-Eigenschaften und Teiler der Grundwerte. */
    private static final class Energie {
        final String name, kuerzel, zweck;
        final String[] eig;
        final int teiler;
        Energie(String name, String kuerzel, String[] eig, int teiler, String zweck) {
            this.name = name; this.kuerzel = kuerzel; this.eig = eig; this.teiler = teiler; this.zweck = zweck;
        }
    }

    private static final Energie[] ENERGIEN = {
            new Energie("Lebensenergie", "LE", new String[]{"KO", "KO", "KK"}, 2, "Wie viel Schaden der Held verkraftet"),
            new Energie("Ausdauer", "AU", new String[]{"MU", "KO", "GE"}, 2, "Wie lange der Held körperliche Anstrengung durchhält"),
            new Energie("Astralenergie", "AE", new String[]{"MU", "IN", "CH"}, 2, "Kraft für Zauber und Rituale"),
            new Energie("Karmaenergie", "KE", null, 0, "Kraft für Liturgien geweihter Helden"),
            new Energie("Magieresistenz", "MR", new String[]{"MU", "KL", "KO"}, 5, "Schutz gegen Zauber, die auf den Helden wirken"),
    };

    static void pruefen(JComponent c, boolean modern) {
        Object o = c.getClientProperty(MARKE);
        Zustand z = o instanceof Zustand ? (Zustand) o : null;
        if (z == null) {
            if (!modern || !(c instanceof JPanel) || !(c.getParent() instanceof JViewport) || !istEigenschaften((JPanel) c)) return;
            z = new Zustand((JPanel) c, (JViewport) c.getParent());
            c.putClientProperty(MARKE, z);
            alle.put(c, z);
        }
        if (modern) z.modern(); else z.original();
    }

    /** Heldendaten haben sich geändert (Plugin-Aktualisierung). */
    static void aktualisieren() {
        for (Zustand z : alle.values()) if (z.aktiv) z.spaeter();
    }

    private static boolean istEigenschaften(JPanel p) {
        if (!(p.getLayout() instanceof GridBagLayout)) return false;
        boolean mut = false, steigern = false;
        for (Component k : p.getComponents()) {
            if (k instanceof JTextField && "Mut".equals(k.getName())) mut = true;
            if (k instanceof JButton && "Mut".equals(k.getName())) steigern = true;
        }
        return mut && steigern;
    }

    // ------------------------------------------------------------------ Daten aus der Ansicht der Helden-Software

    /** Eine Zeile der Helden-Ansicht: Wertfeld, SE-Häkchen, Steigern-Knopf und Kostenanzeige. */
    private static final class Zeile {
        String name;
        JTextField feld;
        JCheckBox se;
        AbstractButton knopf;
        JLabel kosten;
    }

    private static final class Daten {
        final Map<String, Zeile> zeilen = new LinkedHashMap<>();
        JComboBox<?> gilde;

        Zeile z(String name) { return zeilen.get(name); }
        String text(String name) {
            Zeile z = zeilen.get(name);
            return z == null || z.feld == null ? "" : String.valueOf(z.feld.getText()).trim();
        }
        Integer zahl(String name) {
            String t = text(name);
            try { return t.isEmpty() ? null : Integer.valueOf(t); } catch (NumberFormatException e) { return null; }
        }
    }

    private static Daten lesen(JPanel p) {
        Daten d = new Daten();
        GridBagLayout gb = (GridBagLayout) p.getLayout();
        // je Zeile und Seite (links Spalten 0–4, rechts 5–6) die Bestandteile sammeln
        Map<String, List<Component>> teile = new LinkedHashMap<>();
        for (Component k : p.getComponents()) {
            GridBagConstraints c = gb.getConstraints(k);
            String schluessel = (c.gridx >= 5 ? "R" : "L") + c.gridy;
            teile.computeIfAbsent(schluessel, s -> new ArrayList<>()).add(k);
        }
        for (List<Component> l : teile.values()) {
            Zeile z = new Zeile();
            for (Component k : l) {
                if (k instanceof JTextField) { z.feld = (JTextField) k; z.name = k.getName(); }
                else if (k instanceof JComboBox) d.gilde = (JComboBox<?>) k;
                else if (k instanceof AbstractButton) z.knopf = (AbstractButton) k;
                else if (k instanceof JLabel && k.getName() != null) z.kosten = (JLabel) k;
                else if (k instanceof JPanel)
                    for (Component i : ((JPanel) k).getComponents()) if (i instanceof JCheckBox) z.se = (JCheckBox) i;
            }
            if (z.name != null) d.zeilen.put(z.name, z);
        }
        return d;
    }

    // ------------------------------------------------------------------ Zustand je Reiter

    private static final class Zustand {
        final JPanel original;
        final JViewport viewport;
        final Ansicht ansicht = new Ansicht();
        JPanel huelle;
        boolean aktiv;
        final Set<Component> angebunden = Collections.newSetFromMap(new WeakHashMap<>());
        final javax.swing.Timer spaeter = new javax.swing.Timer(120, e -> neuAufbauen());
        String letzterStand;

        Zustand(JPanel original, JViewport vp) {
            this.original = original;
            this.viewport = vp;
            spaeter.setRepeats(false);
            original.addContainerListener(new ContainerListener() {
                public void componentAdded(ContainerEvent e) { spaeter(); }
                public void componentRemoved(ContainerEvent e) { spaeter(); }
            });
        }

        void spaeter() { if (aktiv) spaeter.restart(); }

        void modern() {
            if (aktiv) return;
            aktiv = true;
            huelle = new KampfwerteLayout.Huelle(ansicht);
            huelle.setOpaque(false);
            InventarLayout.eigen(huelle);
            huelle.add(ansicht, BorderLayout.CENTER);
            original.setVisible(false);
            huelle.add(original, BorderLayout.SOUTH);
            viewport.setView(huelle);
            SwingUtilities.updateComponentTreeUI(ansicht);
            letzterStand = null;
            neuAufbauen();
        }

        void original() {
            if (!aktiv) return;
            aktiv = false;
            spaeter.stop();
            huelle.remove(original);
            original.setVisible(true);
            viewport.setView(original);
            viewport.revalidate();
            viewport.repaint();
        }

        void anbinden(Daten d) {
            DocumentListener dl = new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { spaeter(); }
                public void removeUpdate(DocumentEvent e) { spaeter(); }
                public void changedUpdate(DocumentEvent e) { spaeter(); }
            };
            List<Component> neu = new ArrayList<>();
            for (Zeile z : d.zeilen.values())
                for (Component c : new Component[]{z.feld, z.se, z.knopf, z.kosten}) if (c != null) neu.add(c);
            if (d.gilde != null) neu.add(d.gilde);
            for (Component c : neu) {
                if (!angebunden.add(c)) continue;
                if (c instanceof JTextField) ((JTextField) c).getDocument().addDocumentListener(dl);
                else if (c instanceof JLabel) c.addPropertyChangeListener("text", e -> spaeter());
                else {
                    c.addPropertyChangeListener("enabled", e -> spaeter());
                    if (c instanceof JCheckBox) ((JCheckBox) c).addItemListener(e -> spaeter());
                    if (c instanceof JComboBox) ((JComboBox<?>) c).addItemListener(e -> spaeter());
                    if (c instanceof AbstractButton) c.addPropertyChangeListener("ToolTipText", e -> spaeter());
                }
            }
        }

        void neuAufbauen() {
            if (!aktiv) return;
            Daten d = lesen(original);
            anbinden(d);
            HeroData h = InventarLayout.held();
            String stand = stand(d, h);
            if (stand.equals(letzterStand) && ansicht.getComponentCount() > 0) return;
            letzterStand = stand;
            if (DebugBaum.ordner != null) System.err.println("[Eigenschaften] Ansicht neu aufgebaut");
            Point pos = viewport.getViewPosition();
            ansicht.aufbauen(d, h);
            viewport.revalidate();
            viewport.repaint();
            SwingUtilities.invokeLater(() -> viewport.setViewPosition(pos));
        }
    }

    private static String stand(Daten d, HeroData h) {
        StringBuilder sb = new StringBuilder();
        sb.append(h == null ? "" : h.name).append('|').append(Theme.current());
        if (h != null) sb.append('|').append(h.eig).append(h.eigStart).append(h.abgeleitet).append(h.energieMod).append(h.energieKauf);
        for (Zeile z : d.zeilen.values()) {
            sb.append('|').append(z.name).append(':').append(z.feld == null ? "" : z.feld.getText());
            if (z.se != null) sb.append(z.se.isSelected() ? 'S' : 's').append(z.se.isEnabled() ? '1' : '0');
            if (z.knopf != null) sb.append(z.knopf.isEnabled() ? 'K' : 'k').append(z.knopf.getToolTipText());
            if (z.kosten != null) sb.append('/').append(z.kosten.getText());
        }
        if (d.gilde != null) sb.append("|G").append(d.gilde.getSelectedItem()).append(d.gilde.isEnabled());
        return sb.toString();
    }

    // ------------------------------------------------------------------ Ansicht

    private static final class Ansicht extends JPanel implements Scrollable {
        Ansicht() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setBorder(new EmptyBorder(12, 12, 12, 12));
            putClientProperty("modernhelden.own", true);
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 200; }
        public boolean getScrollableTracksViewportWidth() {
            return !(getParent() instanceof JViewport) || getParent().getWidth() >= getMinimumSize().width;
        }
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
        }

        void aufbauen(Daten d, HeroData h) {
            removeAll();
            add(KampfwerteLayout.links(eigenschaften(d, h)));
            add(Box.createVerticalStrut(14));
            add(KampfwerteLayout.links(energien(d, h)));
            add(Box.createVerticalStrut(14));
            add(KampfwerteLayout.links(kampfBasis(d, h)));
            add(Box.createVerticalStrut(14));
            add(KampfwerteLayout.links(stand(d)));
            add(Box.createVerticalStrut(14));
            add(KampfwerteLayout.links(regeln()));
            revalidate();
            repaint();
        }
    }

    // ------------------------------------------------------------------ Eigenschaften

    private static JComponent eigenschaften(Daten d, HeroData h) {
        JPanel k = KampfwerteLayout.karte("Eigenschaften");
        JPanel t = KampfwerteLayout.tabelle();
        t.setOpaque(false);
        InventarLayout.eigen(t);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 4, 12);
        String[] kopf = {"Eigenschaft", "", "Wert", "Start", "SE", "Steigern", "Kosten", "Fließt ein in", ""};
        for (int i = 0; i < kopf.length; i++) {
            c.gridx = i;
            c.weightx = i == kopf.length - 1 ? 1 : 0;
            boolean rechts = i == 2 || i == 3 || i == 6;
            c.anchor = rechts ? GridBagConstraints.EAST : i == 4 || i == 5 ? GridBagConstraints.CENTER : GridBagConstraints.WEST;
            t.add(KampfwerteLayout.kopfzelle(kopf[i], rechts), c);
        }
        for (String[] e : EIGENSCHAFTEN) {
            Zeile z = d.z(e[0]);
            if (z == null) continue;
            c.gridy++;
            Integer wert = d.zahl(e[0]);
            Integer start = null;
            if (h != null && h.eigStart.get(e[1]) != null)
                start = h.eigStart.get(e[1]) + (h.eigMod.get(e[1]) == null ? 0 : h.eigMod.get(e[1]));
            String wirkung = wirkung(e[1]);
            String tip = "<html><b>" + e[0] + " (" + e[1] + ")</b><br>Wert " + (wert == null ? "–" : wert)
                    + (start == null ? "" : " · Startwert " + start + (wert != null && wert > start ? " · seit der Erschaffung +" + (wert - start) : ""))
                    + "<br>Fließt ein in: " + wirkung + "</html>";
            zeile(t, c, e[0], e[1], z, wert, start, wirkung, tip);
        }
        k.add(t, BorderLayout.CENTER);
        return k;
    }

    private static void zeile(JPanel t, GridBagConstraints c, String name, String kuerzel, Zeile z, Integer wert,
                              Integer start, String spalteText, String tip) {
        c.insets = new Insets(3, 0, 3, 12);
        c.weightx = 0;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        JLabel n = new JLabel(name);
        n.setFont(n.getFont().deriveFont(Font.BOLD));
        InventarLayout.eigen(n);
        t.add(n, c);

        c.gridx = 1;
        JLabel kz = KampfwerteLayout.klein(kuerzel);
        InventarLayout.eigen(kz);
        t.add(kz, c);

        c.gridx = 2;
        c.anchor = GridBagConstraints.EAST;
        JLabel wl = KampfwerteLayout.wert(wert == null ? "–" : String.valueOf(wert), true, null);
        t.add(wl, c);

        c.gridx = 3;
        JLabel st = KampfwerteLayout.wert(start == null ? "" : String.valueOf(start), false, tip);
        st.setForeground(Theme.muted());
        t.add(st, c);

        c.gridx = 4;
        c.anchor = GridBagConstraints.CENTER;
        t.add(seHaken(z, name), c);

        c.gridx = 5;
        t.add(steigern(z, name, wert), c);

        c.gridx = 6;
        c.anchor = GridBagConstraints.EAST;
        t.add(kosten(z), c);

        c.gridx = 7;
        c.anchor = GridBagConstraints.WEST;
        JLabel w = KampfwerteLayout.klein(spalteText);
        InventarLayout.eigen(w);
        t.add(w, c);
        // dieselben Tooltips wie in der Kompaktansicht und in der Ansicht der Helden-Software
        TooltipHook.tooltipFuer(name, n, kz, wl, st);
        // „Fließt ein in“ und „Rechenweg“ bleiben ohne Tooltip
    }

    /** Wofür eine Eigenschaft gebraucht wird – aus den Formeln der Grundwerte. */
    static String wirkung(String k) {
        List<String> l = new ArrayList<>();
        for (Energie e : ENERGIEN) {
            if (e.eig == null) continue;
            int n = 0;
            for (String x : e.eig) if (x.equals(k)) n++;
            if (n > 0) l.add(e.kuerzel + (n > 1 ? " (×" + n + ")" : ""));
        }
        String[][] kampf = {{"AT", "MU", "GE", "KK"}, {"PA", "IN", "GE", "KK"}, {"FK", "IN", "FF", "KK"}, {"INI", "MU", "MU", "IN", "GE"}};
        for (String[] b : kampf) {
            int n = 0;
            for (int i = 1; i < b.length; i++) if (b[i].equals(k)) n++;
            if (n > 0) l.add(b[0] + (n > 1 ? " (×" + n + ")" : ""));
        }
        return l.isEmpty() ? "Proben" : String.join(", ", l);
    }

    private static JComponent seHaken(Zeile z, String name) {
        JCheckBox b = new JCheckBox();
        b.setOpaque(false);
        b.setFocusable(false);
        InventarLayout.eigen(b);
        if (z.se == null) { b.setVisible(false); return b; }
        b.setSelected(z.se.isSelected());
        b.setEnabled(z.se.isEnabled());
        b.setToolTipText(z.se.isSelected()
                ? "Spezielle Erfahrung für " + name + " eingetragen – der nächste Steigerungsschritt ist günstiger. Klicken zum Entfernen."
                : "Spezielle Erfahrung (SE) für " + name + " eintragen – macht den nächsten Steigerungsschritt günstiger.");
        // die Helden-Software ändert das Häkchen selbst und rechnet die Kosten neu
        b.addActionListener(e -> {
            b.setSelected(z.se.isSelected());
            z.se.doClick(0);
        });
        return b;
    }

    private static JComponent steigern(Zeile z, String name, Integer wert) {
        JButton b = new JButton("Steigern");
        b.setFocusable(false);
        b.putClientProperty("JButton.buttonType", "roundRect");
        InventarLayout.eigen(b);
        if (z.knopf == null) { b.setVisible(false); return b; }
        b.setEnabled(z.knopf.isEnabled());
        String grund = z.knopf.getToolTipText();
        String kosten = z.kosten == null ? "" : String.valueOf(z.kosten.getText()).trim();
        if (!z.knopf.isEnabled() && (grund == null || grund.isEmpty()) && "Astralenergie".equals(name))
            // die Helden-Software sperrt den Zukauf, solange AE dauerhaft verloren ist
            b.setToolTipText("Erst die dauerhaft verlorene Astralenergie zurückkaufen, dann kann wieder zugekauft werden.");
        else if (!z.knopf.isEnabled())
            b.setToolTipText(grund != null && !grund.isEmpty() ? name + ": " + grund : name + " kann nicht gesteigert werden.");
        else
            b.setToolTipText(name + (wert == null ? "" : " von " + wert + " auf " + (wert + 1)) + " steigern"
                    + (kosten.isEmpty() ? "" : " (" + kosten + ")"));
        b.addActionListener(e -> z.knopf.doClick(0));
        return b;
    }

    private static JLabel kosten(Zeile z) {
        String text = z.kosten == null ? "" : String.valueOf(z.kosten.getText()).trim();
        boolean max = z.knopf != null && !z.knopf.isEnabled() && "Maximum erreicht".equals(z.knopf.getToolTipText());
        JLabel l = new JLabel(max ? "Maximum" : text, SwingConstants.RIGHT);
        if (max) l.setForeground(Theme.muted());
        l.setBorder(new EmptyBorder(0, 2, 0, 4));
        InventarLayout.eigen(l);
        return l;
    }

    // ------------------------------------------------------------------ Energien und MR

    private static JComponent energien(Daten d, HeroData h) {
        JPanel k = KampfwerteLayout.karte("Lebensenergie, Ausdauer, Astralenergie und Magieresistenz");
        JPanel t = KampfwerteLayout.tabelle();
        t.setOpaque(false);
        InventarLayout.eigen(t);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 4, 12);
        String[] kopf = {"Name", "", "Wert", "", "SE", "Zukaufen", "Kosten", "Rechenweg", ""};
        for (int i = 0; i < kopf.length; i++) {
            c.gridx = i;
            c.weightx = i == kopf.length - 1 ? 1 : 0;
            boolean rechts = i == 2 || i == 6;
            c.anchor = rechts ? GridBagConstraints.EAST : i == 4 || i == 5 ? GridBagConstraints.CENTER : GridBagConstraints.WEST;
            t.add(KampfwerteLayout.kopfzelle(kopf[i], rechts), c);
        }
        for (Energie e : ENERGIEN) {
            Zeile z = d.z(e.name);
            if (z == null) continue;
            Integer wert = d.zahl(e.name);
            // AE und KE nur bei Helden, die sie haben
            if (wert == null && (e.kuerzel.equals("AE") || e.kuerzel.equals("KE"))) continue;
            c.gridy++;
            String weg = rechenweg(e, h, wert, e.kuerzel.equals("AE") ? verlorenAe(d) : null);
            String tip = "<html><div style='width:340px'><b>" + e.name + " (" + e.kuerzel + ")</b><br>" + e.zweck + ".<br><br>"
                    + (weg.isEmpty() ? "" : weg + "<br>")
                    + (e.eig == null ? "Wird nicht mit AP gesteigert."
                    : "Steigt eine der Eigenschaften, steigt der Grundwert mit. Zukaufen ist nur begrenzt möglich.")
                    + "</div></html>";
            zeile(t, c, e.name, e.kuerzel, z, wert, null, weg, tip);
            if (e.kuerzel.equals("AE")) verloreneAe(t, c, d);
        }
        k.add(t, BorderLayout.CENTER);
        return k;
    }

    /** Zeile für dauerhaft verlorene Astralenergie mit „Zurückkaufen“ – bei Helden mit AE immer (ohne Verlust ausgegraut). */
    private static void verloreneAe(JPanel t, GridBagConstraints c, Daten d) {
        Zeile z = d.z("Astralenergie-permanent");
        if (z == null || z.feld == null) return;
        String v = String.valueOf(z.feld.getText()).trim();
        c.gridy++;
        c.insets = new Insets(0, 0, 3, 12);
        c.gridx = 0;
        c.weightx = 0;
        c.anchor = GridBagConstraints.WEST;
        String tip = "Dauerhaft verlorene Astralenergie (z. B. durch bestimmte Rituale) kann mit AP zurückgekauft werden.";
        JLabel n = KampfwerteLayout.klein("dauerhaft verloren");
        n.setToolTipText(tip);
        InventarLayout.eigen(n);
        c.gridwidth = 2;
        t.add(n, c);
        c.gridwidth = 1;
        c.gridx = 2;
        c.anchor = GridBagConstraints.EAST;
        t.add(KampfwerteLayout.wert(v.isEmpty() ? "0" : v, false, tip), c);
        c.gridx = 5;
        c.anchor = GridBagConstraints.CENTER;
        JButton b = new JButton("Zurückkaufen");
        b.setFocusable(false);
        b.putClientProperty("JButton.buttonType", "roundRect");
        InventarLayout.eigen(b);
        if (z.knopf == null) b.setVisible(false);
        else {
            b.setEnabled(z.knopf.isEnabled());
            b.setToolTipText(z.knopf.isEnabled() ? "Einen Punkt dauerhaft verlorener AE zurückkaufen" : "Keine dauerhaft verlorene AE.");
            b.addActionListener(e -> z.knopf.doClick(0));
        }
        t.add(b, c);
        c.gridx = 6;
        c.anchor = GridBagConstraints.EAST;
        t.add(kosten(z), c);
        c.anchor = GridBagConstraints.WEST;
    }

    /** Dauerhaft verlorene AE laut Feld der Helden-Software (leer = 0); null, wenn es das Feld nicht gibt. */
    private static Integer verlorenAe(Daten d) {
        if (d.z("Astralenergie-permanent") == null) return null;
        Integer v = d.zahl("Astralenergie-permanent");
        return v == null ? 0 : v;
    }

    /** „(KO 12 + KO 12 + KK 11) / 2 = 17,5 → 18 · Modifikator +5 · zugekauft +2“. */
    static String rechenweg(Energie e, HeroData h, Integer wert, Integer verloren) {
        if (h == null) return "";
        Integer mod = h.energieMod.get(e.kuerzel), kauf = h.energieKauf.get(e.kuerzel);
        // dauerhaft verlorene AE getrennt zeigen (die Helden-Software zählt sie in den Modifikator);
        // der aktuelle Stand kommt aus ihrem Feld, weil „Zurückkaufen“ die Heldendaten nicht sofort neu liefert
        Integer perm = h.energiePermanent.get(e.kuerzel);
        if (mod != null && perm != null) mod = mod - perm;
        if (verloren == null) verloren = perm;
        if (e.eig == null) {
            if (wert == null) return "";
            return kauf != null && kauf != 0 ? "durch Weihe und Liturgiekenntnis" : "";
        }
        StringBuilder sb = new StringBuilder("(");
        int summe = 0;
        for (int i = 0; i < e.eig.length; i++) {
            Integer v = h.eig.get(e.eig[i]);
            if (v == null) return "";
            summe += v;
            if (i > 0) sb.append(" + ");
            sb.append(e.eig[i]).append(' ').append(v);
        }
        double roh = summe / (double) e.teiler;
        long grund = Math.round(roh);
        sb.append(") / ").append(e.teiler).append(" = ").append(InventarLayout.zahl(roh));
        if (roh != grund) sb.append(" → ").append(grund);
        long gesamt = grund;
        if (mod != null && mod != 0) { sb.append(" · Modifikator ").append(vz(mod)); gesamt += mod; }
        if (kauf != null && kauf != 0) { sb.append(" · zugekauft ").append(vz(kauf)); gesamt += kauf; }
        if (verloren != null && verloren != 0) { sb.append(" · dauerhaft verloren ").append(vz(verloren)); gesamt += verloren; }
        if (wert != null && wert != gesamt) sb.append(" · weitere ").append(vz((int) (wert - gesamt)));
        return sb.toString();
    }

    private static String vz(int v) { return v >= 0 ? "+" + v : "−" + (-v); }

    // ------------------------------------------------------------------ Kampf-Basiswerte

    private static JComponent kampfBasis(Daten d, HeroData h) {
        JPanel k = KampfwerteLayout.karte("Kampf-Basiswerte");
        JPanel reihe = new KampfwerteLayout.Kacheln(10);
        reihe.add(KampfwerteLayout.basis("AT-Basis", d.zahl("Attacke"), h, new String[]{"MU", "GE", "KK"}, "Grundlage aller Attacke-Werte im Nahkampf"));
        reihe.add(KampfwerteLayout.basis("PA-Basis", d.zahl("Parade"), h, new String[]{"IN", "GE", "KK"}, "Grundlage aller Parade-Werte und des Ausweichens"));
        reihe.add(KampfwerteLayout.basis("FK-Basis", d.zahl("Fernkampf-Basis"), h, new String[]{"IN", "FF", "KK"}, "Grundlage aller Fernkampfwerte"));
        reihe.add(KampfwerteLayout.basis("INI-Basis", d.zahl("Initiative"), h, new String[]{"MU", "MU", "IN", "GE"},
                "Initiative-Basiswert: Grundlage der Reihenfolge im Kampf; dazu kommen 1W6 sowie Waffen- und Rüstungsmodifikatoren"));
        k.add(reihe, BorderLayout.CENTER);
        k.add(KampfwerteLayout.klein("Die Verteilung der Kampftalente auf Attacke und Parade steht im Reiter „Kampfwerte“."), BorderLayout.SOUTH);
        return k;
    }

    // ------------------------------------------------------------------ Sozialstatus und Gilde

    private static JComponent stand(Daten d) {
        JPanel k = KampfwerteLayout.karte("Stand");
        JPanel t = KampfwerteLayout.tabelle();
        t.setOpaque(false);
        InventarLayout.eigen(t);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(3, 0, 3, 12);
        c.anchor = GridBagConstraints.WEST;

        Zeile so = d.z("Sozialstatus");
        if (so != null && so.feld != null) {
            c.gridx = 0;
            JLabel n = new JLabel("Sozialstatus");
            n.setFont(n.getFont().deriveFont(Font.BOLD));
            InventarLayout.eigen(n);
            TooltipHook.tooltipFuer("Sozialstatus", n);
            t.add(n, c);
            c.gridx = 1;
            t.add(sozialstatus(so.feld), c);
            c.gridx = 2;
            c.weightx = 1;
            t.add(KampfwerteLayout.klein("Gesellschaftlicher Rang (höchstens 21). Kostet keine AP; Änderungen stehen im Verlauf."), c);
            c.weightx = 0;
            c.gridy++;
        }
        if (d.gilde != null) {
            c.gridx = 0;
            JLabel n = new JLabel("Gildenzugehörigkeit");
            n.setFont(n.getFont().deriveFont(Font.BOLD));
            InventarLayout.eigen(n);
            t.add(n, c);
            c.gridx = 1;
            t.add(gilde(d.gilde), c);
            c.gridx = 2;
            c.weightx = 1;
            t.add(KampfwerteLayout.klein(d.gilde.isEnabled() ? "Weiße, graue oder schwarze Gilde"
                    : "Nur für Gildenmagier und Lehrmeister wählbar."), c);
            c.weightx = 0;
        }
        k.add(t, BorderLayout.CENTER);
        return k;
    }

    /** Eigenes Feld; übernommen wird über das Feld der Helden-Software (Enter oder Verlassen des Feldes). */
    private static JComponent sozialstatus(JTextField orig) {
        JTextField f = new JTextField(String.valueOf(orig.getText()), 3) {
            @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        };
        f.setHorizontalAlignment(SwingConstants.RIGHT);
        f.setToolTipText("Sozialstatus eingeben und mit Enter übernehmen");
        InventarLayout.eigen(f);
        Runnable uebernehmen = () -> {
            String neu = f.getText().trim();
            if (neu.equals(String.valueOf(orig.getText()).trim())) return;
            orig.setText(neu);
            orig.postActionEvent();
            // die Helden-Software setzt bei ungültiger Eingabe den alten Wert zurück
            SwingUtilities.invokeLater(() -> f.setText(String.valueOf(orig.getText())));
        };
        f.addActionListener(e -> uebernehmen.run());
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { uebernehmen.run(); }
        });
        return f;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JComponent gilde(JComboBox orig) {
        DefaultComboBoxModel m = new DefaultComboBoxModel();
        for (int i = 0; i < orig.getItemCount(); i++) m.addElement(orig.getItemAt(i));
        JComboBox b = new JComboBox(m);
        b.setSelectedItem(orig.getSelectedItem());
        b.setEnabled(orig.isEnabled());
        InventarLayout.eigen(b);
        b.addActionListener(e -> {
            Object neu = b.getSelectedItem();
            if (neu != null && !neu.equals(orig.getSelectedItem())) orig.setSelectedItem(neu);
        });
        return b;
    }

    // ------------------------------------------------------------------ Regeln

    private static JComponent regeln() {
        JPanel k = KampfwerteLayout.karte("So wird gerechnet");
        JLabel l = new JLabel("<html><div style='width:820px'>"
                + "• <b>Steigern:</b> Jeder Schritt erhöht den Wert um 1; die Kosten zeigt die Spalte „Kosten“. "
                + "Höhere Werte werden teurer.<br>"
                + "• <b>SE:</b> Eine Spezielle Erfahrung macht den nächsten Schritt günstiger und wird danach verbraucht.<br>"
                + "• <b>Grenzen:</b> Eigenschaften lassen sich nur bis zu einem Höchstwert steigern, LE, AU, AE und MR "
                + "nur begrenzt zukaufen – dann steht dort „Maximum“.<br>"
                + "• <b>Grundwerte:</b> LE, AU, AE, MR und die Kampf-Basiswerte werden aus den Eigenschaften berechnet "
                + "(kaufmännisch gerundet) und steigen mit ihnen.<br>"
                + "• Gesteigert wird über die Helden-Software; dauerhaft wird die Änderung erst beim Speichern des Helden."
                + "</div></html>");
        InventarLayout.eigen(l);
        k.add(l, BorderLayout.CENTER);
        return k;
    }
}
