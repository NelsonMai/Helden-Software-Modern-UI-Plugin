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
        /** Zeitpunkt der letzten „Eigenschaft steigern“ je Eigenschaft (für „−“), -1 = keine. */
        final Map<String, Long> zurueck = new HashMap<>();
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
            Window w = SwingUtilities.getWindowAncestor(original);
            Frame f = w instanceof Frame ? (Frame) w : Rueckgaengig.hauptfenster();
            for (String[] e : EIGENSCHAFTEN)
                d.zurueck.put(e[0], f == null ? -1L : Rueckgaengig.letzteEigenschaftsSteigerung(f, e[0]));
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
        if (h != null) sb.append('|').append(h.eig).append(h.eigStart).append(h.abgeleitet).append(h.energieMod).append(h.energieKauf)
                .append('|').append(h.ap).append('/').append(h.freieAp);
        sb.append('|').append(d.zurueck);
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

    /** Aufklappzustand der Hinweis-Bereiche (bleibt beim Neuaufbau erhalten). */
    private static boolean rechenwegOffen = true, hilfeOffen = true;

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
        /** Unterhalb dieser Breite scrollt die Ansicht waagerecht, darüber ordnen sich die Karten um. */
        static final int MIN_BREITE = 700;
        public boolean getScrollableTracksViewportWidth() {
            return !(getParent() instanceof JViewport) || getParent().getWidth() >= MIN_BREITE;
        }
        /**
         * Die Hülle im Viewport folgt der Fensterbreite, solange sie mindestens so breit ist; erst darunter wird
         * waagerecht gescrollt. Die gewünschte Breite ist die Mindestbreite – die Karten ordnen sich nach der
         * tatsächlichen Breite an (nebeneinander oder untereinander).
         */
        @Override public Dimension getMinimumSize() { return new Dimension(MIN_BREITE, super.getMinimumSize().height); }
        @Override public Dimension getPreferredSize() { return new Dimension(MIN_BREITE, super.getPreferredSize().height); }
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
        }

        void aufbauen(Daten d, HeroData h) {
            removeAll();
            if (h != null && h.ap > 0) {
                add(KampfwerteLayout.links(abenteuerpunkte(h)));
                add(Box.createVerticalStrut(12));
            }
            add(KampfwerteLayout.links(eigenschaften(d, h)));
            add(Box.createVerticalStrut(12));
            JPanel rechts = new JPanel();
            rechts.setOpaque(false);
            rechts.setLayout(new BoxLayout(rechts, BoxLayout.Y_AXIS));
            InventarLayout.eigen(rechts);
            rechts.add(KampfwerteLayout.links(stand(d)));
            rechts.add(Box.createVerticalStrut(12));
            JComponent hilfe = KampfwerteLayout.links(regeln());
            rechts.add(hilfe);
            JPanel unten = new JPanel(new Spalten(12, new double[]{1, 1, 0.9}));
            unten.setOpaque(false);
            InventarLayout.eigen(unten);
            unten.add(energien(d, h));
            unten.add(kampfBasis(d, h));
            unten.add(mindestens(rechts, 360));
            add(KampfwerteLayout.links(unten));
            revalidate();
            repaint();
        }
    }

    // ------------------------------------------------------------------ Bausteine

    /** Fläche mit runden Ecken (Füllung und Rand), Inhalt per Layout. */
    static class Rund extends JPanel {
        Color fuellung, rand;
        final int bogen;
        Rund(LayoutManager l, Color fuellung, Color rand, int bogen) {
            super(l);
            this.fuellung = fuellung;
            this.rand = rand;
            this.bogen = bogen;
            setOpaque(false);
            InventarLayout.eigen(this);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (fuellung != null) { g.setColor(fuellung); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, bogen, bogen); }
            if (rand != null) { g.setColor(rand); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, bogen, bogen); }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    /** Runde Plakette mit Symbol. */
    static final class Plakette implements Icon {
        final int groesse;
        final Color grund, farbe;
        final String symbol;
        Plakette(String symbol, int groesse, Color grund, Color farbe) {
            this.symbol = symbol; this.groesse = groesse; this.grund = grund; this.farbe = farbe;
        }
        public int getIconWidth() { return groesse; }
        public int getIconHeight() { return groesse; }
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (grund != null) { g.setColor(grund); g.fillOval(x, y, groesse - 1, groesse - 1); }
            int s = Math.round(groesse * (grund == null ? 1f : 0.58f));
            g.translate(x + (groesse - s) / 2.0, y + (groesse - s) / 2.0);
            EigenschaftenSymbole.zeichne(g, symbol, s, farbe);
            g.dispose();
        }
    }

    static Color mische(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Hervorhebung wie in der Druckvorschau: überall dieselbe Akzentfarbe (Rostbraun). */
    static Color farbe(String k) { return Theme.accent(); }

    static Color vordergrund() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : Color.DARK_GRAY;
    }

    /** Kachel wie in der Druckvorschau: warmes Beige, feiner Rand, Akzentbalken unten. */
    static Rund kachel(Color f, LayoutManager l) {
        Color basis = Theme.kachel();
        Rund r = new Rund(l, mische(basis, Theme.accent(), Theme.isDark() ? 0.07 : 0.045), Theme.trenner(), 12) {
            @Override protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.accent());
                g.fillRoundRect(14, getHeight() - 5, getWidth() - 28, 3, 3, 3);
                g.dispose();
            }
        };
        r.setBorder(new EmptyBorder(10, 12, 16, 12));
        return r;
    }

    /** Karte mit runden Ecken, Plakette, Titel und optionalem Zähler-Chip; Inhalt kommt in die Mitte. */
    static Rund karte(String symbol, String titel, String chip, JComponent rechts) {
        Rund k = new Rund(new BorderLayout(0, 12), Theme.card(), Theme.trenner(), 16) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        k.setBorder(new EmptyBorder(14, 16, 16, 16));
        k.add(kopf(symbol, titel, chip, rechts), BorderLayout.NORTH);
        return k;
    }

    static JComponent kopf(String symbol, String titel, String chip, JComponent rechts) {
        JPanel kopf = new JPanel(new BorderLayout(12, 0));
        kopf.setOpaque(false);
        InventarLayout.eigen(kopf);
        JPanel l = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        l.setOpaque(false);
        InventarLayout.eigen(l);
        JLabel t = InventarLayout.titel(titel);
        t.setFont(t.getFont().deriveFont(t.getFont().getSize2D() + 2f));
        t.setIcon(new Plakette(symbol, 34, Theme.accent(), Color.WHITE));
        t.setIconTextGap(12);
        InventarLayout.eigen(t);
        l.add(t);
        if (chip != null) {
            l.add(Box.createHorizontalStrut(12));
            l.add(chip(chip));
        }
        JPanel mitte = new JPanel(new GridBagLayout());   // Titelzeile senkrecht mittig
        mitte.setOpaque(false);
        InventarLayout.eigen(mitte);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;
        mitte.add(l, c);
        kopf.add(mitte, BorderLayout.CENTER);
        if (rechts != null) kopf.add(rechts, BorderLayout.EAST);
        return kopf;
    }

    static JComponent chip(String text) {
        Rund r = new Rund(new BorderLayout(), mische(Theme.kachel(), Theme.accent(), 0.08), mische(Theme.kachel(), Theme.accent(), 0.3), 999);
        r.setBorder(new EmptyBorder(2, 10, 3, 10));
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 1f));
        InventarLayout.eigen(l);
        r.add(l);
        JPanel h = new JPanel(new GridBagLayout());
        h.setOpaque(false);
        InventarLayout.eigen(h);
        h.add(r);
        return h;
    }

    static JLabel fett(String t, float plus) {
        JLabel l = new JLabel(t);
        l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() + plus));
        InventarLayout.eigen(l);
        return l;
    }

    static JLabel leise(String t) {
        JLabel l = KampfwerteLayout.klein(t);
        InventarLayout.eigen(l);
        return l;
    }

    /** Runder Knopf (−/+). */
    static JButton rundknopf(String text) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setPreferredSize(new Dimension(36, 30));
        b.setFont(b.getFont().deriveFont(Font.BOLD, b.getFont().getSize2D() + 3f));
        b.putClientProperty("FlatLaf.style", "arc: 999");
        InventarLayout.eigen(b);
        return b;
    }

    /** Knopf wie die Tabellenköpfe der Druckvorschau: zartes Akzent-Beige mit Akzentschrift. */
    static JButton farbknopf(String text, Color f) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        Color a = Theme.accent();
        Color grund = mische(Theme.kachel(), a, Theme.isDark() ? 0.22 : 0.12), rand = mische(Theme.kachel(), a, 0.45);
        Color schrift = Theme.isDark() ? mische(a, Color.WHITE, 0.45) : a;
        b.putClientProperty("FlatLaf.style", String.format("arc: 10; background: %s; borderColor: %s; foreground: %s; hoverBackground: %s; font: bold",
                hex(grund), hex(rand), hex(schrift), hex(mische(grund, a, 0.12))));
        InventarLayout.eigen(b);
        return b;
    }

    private static String hex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }

    /**
     * Ordnet Kinder in Spalten an (spaltenweise gefüllt): {@code max} Spalten, solange jede mindestens
     * {@code minBreite} breit ist, sonst weniger. Alle Zellen einer Zeile sind gleich hoch.
     */
    static final class Raster implements LayoutManager {
        final int max, minBreite, luecke;
        final boolean spaltenweise;
        Raster(int max, int minBreite, int luecke, boolean spaltenweise) {
            this.max = max; this.minBreite = minBreite; this.luecke = luecke; this.spaltenweise = spaltenweise;
        }
        int spalten(Container p) {
            int b = p.getWidth() - p.getInsets().left - p.getInsets().right;
            if (b <= 0) return max;
            int n = max;
            while (n > 1 && (b - (n - 1) * luecke) / n < minBreite) n--;
            return n;
        }
        int zeilenhoehe(Container p) {
            int h = 0;
            for (Component k : p.getComponents()) if (k.isVisible()) h = Math.max(h, k.getPreferredSize().height);
            return h;
        }
        public void addLayoutComponent(String n, Component c) { }
        public void removeLayoutComponent(Component c) { }
        public Dimension preferredLayoutSize(Container p) {
            int n = p.getComponentCount(), sp = spalten(p), zeilen = (n + sp - 1) / Math.max(1, sp);
            int w = 0;
            for (Component k : p.getComponents()) w = Math.max(w, k.getPreferredSize().width);
            Insets i = p.getInsets();
            return new Dimension(sp * w + (sp - 1) * luecke + i.left + i.right,
                    zeilen * zeilenhoehe(p) + Math.max(0, zeilen - 1) * luecke + i.top + i.bottom);
        }
        public Dimension minimumLayoutSize(Container p) {
            Insets i = p.getInsets();
            int w = 0;
            for (Component k : p.getComponents()) w = Math.max(w, k.getMinimumSize().width);
            int n = p.getComponentCount();
            return new Dimension(w + i.left + i.right, n * zeilenhoehe(p) + Math.max(0, n - 1) * luecke + i.top + i.bottom);
        }
        public void layoutContainer(Container p) {
            int n = p.getComponentCount();
            if (n == 0) return;
            Insets i = p.getInsets();
            int sp = spalten(p), zeilen = (n + sp - 1) / sp;
            int b = p.getWidth() - i.left - i.right, zw = (b - (sp - 1) * luecke) / sp, zh = zeilenhoehe(p);
            for (int k = 0; k < n; k++) {
                int s = spaltenweise ? k / zeilen : k % sp, z = spaltenweise ? k % zeilen : k / sp;
                int breite = zw;
                // letzte Kachel allein in ihrer Zeile: volle Breite
                if (!spaltenweise && k == n - 1 && n % sp != 0 && s == 0) breite = b;
                p.getComponent(k).setBounds(i.left + s * (zw + luecke), i.top + z * (zh + luecke), breite, zh);
            }
            Object alt = ((JComponent) p).getClientProperty("modernhelden.spalten");
            if (!Integer.valueOf(sp).equals(alt)) {
                ((JComponent) p).putClientProperty("modernhelden.spalten", sp);
                SwingUtilities.invokeLater(p::revalidate);
            }
        }
    }

    /**
     * Karten nebeneinander (Breite nach Gewicht, alle gleich hoch), wenn jede ihre Mindestbreite bekommt –
     * sonst untereinander.
     */
    static final class Spalten implements LayoutManager {
        final int luecke;
        final double[] gewicht;
        Spalten(int luecke, double[] gewicht) { this.luecke = luecke; this.gewicht = gewicht; }
        boolean nebeneinander(Container p) {
            int b = p.getWidth() - p.getInsets().left - p.getInsets().right;
            if (b <= 0) return true;
            int n = p.getComponentCount();
            double summe = 0;
            for (int k = 0; k < n; k++) summe += gewicht[k];
            for (int k = 0; k < n; k++) {
                int w = (int) ((b - (n - 1) * luecke) * gewicht[k] / summe);
                if (w < p.getComponent(k).getMinimumSize().width) return false;
            }
            return true;
        }
        public void addLayoutComponent(String n, Component c) { }
        public void removeLayoutComponent(Component c) { }
        public Dimension preferredLayoutSize(Container p) {
            Insets i = p.getInsets();
            boolean neben = nebeneinander(p);
            int w = 0, h = 0, n = p.getComponentCount();
            for (Component k : p.getComponents()) {
                Dimension d = k.getPreferredSize();
                if (neben) { w += d.width; h = Math.max(h, d.height); }
                else { w = Math.max(w, d.width); h += d.height; }
            }
            if (n > 1) { if (neben) w += (n - 1) * luecke; else h += (n - 1) * luecke; }
            return new Dimension(w + i.left + i.right, h + i.top + i.bottom);
        }
        public Dimension minimumLayoutSize(Container p) {
            int w = 0;
            for (Component k : p.getComponents()) w = Math.max(w, k.getMinimumSize().width);
            return new Dimension(w, 0);
        }
        public void layoutContainer(Container p) {
            Insets i = p.getInsets();
            int n = p.getComponentCount();
            if (n == 0) return;
            int b = p.getWidth() - i.left - i.right;
            boolean neben = nebeneinander(p);
            if (neben) {
                double summe = 0;
                for (int k = 0; k < n; k++) summe += gewicht[k];
                int x = i.left, h = 0, rest = b - (n - 1) * luecke;
                for (Component k : p.getComponents()) h = Math.max(h, k.getPreferredSize().height);
                for (int k = 0; k < n; k++) {
                    int w = k == n - 1 ? i.left + b - x : (int) (rest * gewicht[k] / summe);
                    p.getComponent(k).setBounds(x, i.top, w, h);
                    x += w + luecke;
                }
            } else {
                int y = i.top;
                for (Component k : p.getComponents()) {
                    int h = k.getPreferredSize().height;
                    k.setBounds(i.left, y, b, h);
                    y += h + luecke;
                }
            }
            Object alt = ((JComponent) p).getClientProperty("modernhelden.neben");
            if (!Boolean.valueOf(neben).equals(alt)) {
                ((JComponent) p).putClientProperty("modernhelden.neben", neben);
                SwingUtilities.invokeLater(p::revalidate);
            }
        }
    }

    // ------------------------------------------------------------------ Abenteuerpunkte

    private static JComponent abenteuerpunkte(HeroData h) {
        int gesamt = h.ap, frei = h.freieAp, aus = Math.max(0, gesamt - frei);
        Rund k = new Rund(new GridBagLayout(), Theme.card(), Theme.trenner(), 16) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        k.setBorder(new EmptyBorder(10, 16, 10, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 28);
        JLabel t = fett("Abenteuerpunkte", 1);
        t.setIcon(new Plakette("ap", 30, Theme.accent(), Color.WHITE));
        t.setIconTextGap(10);
        k.add(t, c);
        k.add(apZahl(gesamt, "Gesamt", null), c);
        k.add(apZahl(aus, "Ausgegeben", null), c);
        k.add(apZahl(frei, "Verfügbar", farbe("AU")), c);
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 10);
        double anteil = gesamt <= 0 ? 0 : Math.min(1, aus / (double) gesamt);
        JComponent balken = new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(160, 10); }
            @Override public Dimension getMinimumSize() { return new Dimension(60, 10); }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int y = (getHeight() - 10) / 2;
                g.setColor(mische(Theme.kachel(), vordergrund(), 0.12));
                g.fillRoundRect(0, y, getWidth(), 10, 10, 10);
                g.setColor(farbe("AU"));
                g.fillRoundRect(0, y, (int) Math.round(getWidth() * anteil), 10, 10, 10);
                g.dispose();
            }
        };
        TooltipHook.infoTip(balken, Math.round(anteil * 100) + " % der Abenteuerpunkte sind ausgegeben.");
        k.add(balken, c);
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, 0);
        k.add(leise(Math.round(anteil * 100) + " % ausgegeben"), c);
        return k;
    }

    private static JComponent apZahl(int wert, String text, Color f) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        InventarLayout.eigen(p);
        JLabel z = fett(String.format(Locale.GERMAN, "%,d", wert), 4);
        if (f != null) z.setForeground(f);
        p.add(z);
        JLabel l = leise(text);
        if (f != null) l.setForeground(f);
        p.add(l);
        return p;
    }

    // ------------------------------------------------------------------ Eigenschaften

    private static JComponent eigenschaften(Daten d, HeroData h) {
        int anzahl = 0;
        for (String[] e : EIGENSCHAFTEN) if (d.z(e[0]) != null) anzahl++;
        Rund k = karte("stern", "Eigenschaften", anzahl + " Eigenschaften", null);
        JPanel raster = new JPanel(new Raster(2, 560, 10, true));
        raster.setOpaque(false);
        InventarLayout.eigen(raster);
        for (String[] e : EIGENSCHAFTEN) {
            Zeile z = d.z(e[0]);
            if (z == null) continue;
            Integer wert = d.zahl(e[0]);
            Integer start = null;
            if (h != null && h.eigStart.get(e[1]) != null)
                start = h.eigStart.get(e[1]) + (h.eigMod.get(e[1]) == null ? 0 : h.eigMod.get(e[1]));
            Long zeit = d.zurueck.get(e[0]);
            raster.add(eigenschaft(e[0], e[1], z, wert, start, zeit == null ? -1 : zeit));
        }
        k.add(raster, BorderLayout.CENTER);
        return k;
    }

    private static JComponent eigenschaft(String name, String kuerzel, Zeile z, Integer wert, Integer start, long zeit) {
        Rund r = new Rund(new GridBagLayout(), Theme.kachel(), Theme.trenner(), 12);
        r.setBorder(new EmptyBorder(8, 12, 8, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 12);

        JLabel sym = new JLabel(new Plakette(kuerzel, 40, mische(Theme.kachel(), vordergrund(), Theme.isDark() ? 0.14 : 0.07),
                mische(vordergrund(), Theme.kachel(), 0.1)));
        InventarLayout.eigen(sym);
        r.add(sym, c);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        InventarLayout.eigen(text);
        JLabel n = fett(name, 1);
        text.add(n);
        String wirkung = wirkung(kuerzel);
        JLabel s = leise((start == null ? kuerzel : "Start: " + start) + "  ·  " + wirkung);
        text.add(s);
        // Name und Startwert dürfen im kleinen Fenster gekürzt werden, Wert und Knöpfe nicht
        text.setMinimumSize(new Dimension(90, text.getPreferredSize().height));
        text.setPreferredSize(new Dimension(Math.min(text.getPreferredSize().width, 150), text.getPreferredSize().height));
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        r.add(text, c);
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;

        Rund feld = new Rund(new GridBagLayout(), mische(Theme.kachel(), Theme.card(), 0.5), Theme.trenner(), 10);
        feld.setPreferredSize(new Dimension(60, 42));
        JLabel w = fett(wert == null ? "–" : String.valueOf(wert), 7);
        feld.add(w);
        r.add(feld, c);

        JComponent se = seHaken(z, name);
        r.add(se, c);

        JLabel ko = kosten(z);
        ko.setPreferredSize(new Dimension(Math.max(78, ko.getPreferredSize().width), ko.getPreferredSize().height));
        r.add(ko, c);

        c.insets = new Insets(0, 0, 0, 8);
        r.add(senken(name, wert, start, zeit), c);
        c.insets = new Insets(0, 0, 0, 0);
        JButton plus = rundknopf("+");
        steigernAnbinden(plus, z, name, wert);
        r.add(plus, c);

        String tip = "<html><b>" + name + " (" + kuerzel + ")</b><br>Wert " + (wert == null ? "–" : wert)
                + (start == null ? "" : " · Startwert " + start + (wert != null && wert > start ? " · seit der Erschaffung +" + (wert - start) : ""))
                + "<br>Fließt ein in: " + wirkung + "</html>";
        TooltipHook.tooltipFuer(name, n, sym, w);
        TooltipHook.infoTip(s, tip);
        return r;
    }

    /** „−“: nimmt die letzte Steigerung dieser Eigenschaft mit der Undo-Logik der Helden-Software zurück. */
    private static JComponent senken(String name, Integer wert, Integer start, long zeit) {
        JButton b = rundknopf("−");
        boolean geht = zeit > 0 && (start == null || wert == null || wert > start);
        b.setEnabled(geht);
        TooltipHook.infoTip(b, geht
                ? "Letzte Steigerung von " + name + " zurücknehmen" + (wert == null ? "" : " (" + wert + " → " + (wert - 1) + ")")
                  + " – die AP werden erstattet."
                : name + " wurde noch nicht gesteigert – nichts zurückzunehmen.");
        b.addActionListener(e -> {
            Window win = SwingUtilities.getWindowAncestor(b);
            Frame f = win instanceof Frame ? (Frame) win : Rueckgaengig.hauptfenster();
            if (f == null) return;
            long t = Rueckgaengig.letzteEigenschaftsSteigerung(f, name);
            if (t <= 0) return;
            String fehler = Rueckgaengig.ausfuehren(f, t, "Eigenschaft steigern");
            if (fehler != null) JOptionPane.showMessageDialog(b, fehler, "Zurücknehmen nicht möglich", JOptionPane.WARNING_MESSAGE);
            for (Zustand z : alle.values()) if (z.aktiv) z.spaeter();
        });
        return b;
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
        JCheckBox b = new JCheckBox("SE");
        b.setOpaque(false);
        b.setFocusable(false);
        b.setForeground(Theme.muted());
        InventarLayout.eigen(b);
        if (z.se == null) { b.setVisible(false); return b; }
        b.setSelected(z.se.isSelected());
        b.setEnabled(z.se.isEnabled());
        TooltipHook.infoTip(b, z.se.isSelected()
                ? "Spezielle Erfahrung für " + name + " eingetragen – der nächste Steigerungsschritt ist günstiger. Klicken zum Entfernen."
                : "Spezielle Erfahrung (SE) für " + name + " eintragen – macht den nächsten Steigerungsschritt günstiger.");
        // die Helden-Software ändert das Häkchen selbst und rechnet die Kosten neu
        b.addActionListener(e -> {
            b.setSelected(z.se.isSelected());
            z.se.doClick(0);
        });
        return b;
    }

    /** Verbindet einen eigenen Knopf mit „Steigern“ der Helden-Software (Zustand, Tooltip, Klick). */
    private static boolean steigernAnbinden(AbstractButton b, Zeile z, String name, Integer wert) {
        if (z.knopf == null) { b.setVisible(false); return false; }
        b.setEnabled(z.knopf.isEnabled());
        String grund = z.knopf.getToolTipText();
        String kosten = z.kosten == null ? "" : String.valueOf(z.kosten.getText()).trim();
        if (!z.knopf.isEnabled() && (grund == null || grund.isEmpty()) && "Astralenergie".equals(name))
            // die Helden-Software sperrt den Zukauf, solange AE dauerhaft verloren ist
            TooltipHook.infoTip(b, "Erst die dauerhaft verlorene Astralenergie zurückkaufen, dann kann wieder zugekauft werden.");
        else if (!z.knopf.isEnabled())
            TooltipHook.infoTip(b, grund != null && !grund.isEmpty() ? name + ": " + grund : name + " kann nicht gesteigert werden.");
        else
            TooltipHook.infoTip(b, name + (wert == null ? "" : " von " + wert + " auf " + (wert + 1)) + " steigern"
                    + (kosten.isEmpty() ? "" : " (" + kosten + ")"));
        b.addActionListener(e -> z.knopf.doClick(0));
        return true;
    }

    /**
     * Karmaenergie hat in der Helden-Software keinen „Steigern“-Knopf; erhöht wird sie über „Steigern → Karmalqueste …“.
     * Der Knopf löst genau diesen Menüpunkt aus und ist nur aktiv, wenn er es auch ist.
     */
    private static JComponent karmalqueste(JButton b) {
        JMenuItem m = null;
        for (Frame f : Frame.getFrames()) {
            if (!f.isDisplayable()) continue;
            m = AllgemeinLayout.menuepunkt(f, "Karmalqueste");
            if (m != null) break;
        }
        final JMenuItem punkt = m;
        if (punkt == null) { b.setVisible(false); return b; }
        b.setEnabled(punkt.isEnabled());
        TooltipHook.infoTip(b, punkt.isEnabled()
                ? "Karmaenergie über eine Karmalqueste erhöhen (wie Steigern → Karmalqueste …)."
                : "Eine Karmalqueste ist für diesen Helden gerade nicht möglich.");
        b.addActionListener(e -> { if (punkt.isEnabled()) punkt.doClick(0); });
        return b;
    }

    private static boolean maximum(Zeile z) {
        return z.knopf != null && !z.knopf.isEnabled() && "Maximum erreicht".equals(z.knopf.getToolTipText());
    }

    private static JLabel kosten(Zeile z) {
        String text = z.kosten == null ? "" : String.valueOf(z.kosten.getText()).trim();
        boolean max = maximum(z);
        JLabel l = new JLabel(max ? "Maximum" : text, SwingConstants.RIGHT);
        if (max) l.setForeground(Theme.muted());
        l.setBorder(new EmptyBorder(0, 2, 0, 4));
        InventarLayout.eigen(l);
        return l;
    }

    // ------------------------------------------------------------------ Abgeleitete Werte

    private static JComponent energien(Daten d, HeroData h) {
        List<JComponent> kacheln = new ArrayList<>();
        JComponent karma = null;
        int werte = 0;
        for (Energie e : ENERGIEN) {
            Zeile z = d.z(e.name);
            if (z == null) continue;
            Integer wert = d.zahl(e.name);
            // AE und KE nur bei Helden, die sie haben
            if (wert == null && (e.kuerzel.equals("AE") || e.kuerzel.equals("KE"))) continue;
            if (e.kuerzel.equals("KE")) { karma = karmaKachel(e, z, wert, h); continue; }
            werte++;
            kacheln.add(energieKachel(e, z, wert, h, d));
        }
        Rund k = karte("LE", "Abgeleitete Werte", werte + " Werte" + (karma != null ? " + Karma" : ""), null);
        k.setMinimumSize(null);
        JPanel raster = new JPanel(new Raster(2, 190, 10, false));
        raster.setOpaque(false);
        InventarLayout.eigen(raster);
        for (JComponent c : kacheln) raster.add(c);
        JPanel inhalt = new JPanel(new BorderLayout(0, 10));
        inhalt.setOpaque(false);
        InventarLayout.eigen(inhalt);
        inhalt.add(raster, BorderLayout.CENTER);
        if (karma != null) inhalt.add(karma, BorderLayout.SOUTH);
        k.add(oben(inhalt), BorderLayout.CENTER);
        return mindestens(k, 400);
    }

    /** Inhalt oben halten, wenn die Karte höher ist als nötig (gleich hohe Karten nebeneinander). */
    static JComponent oben(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        InventarLayout.eigen(p);
        p.add(c, BorderLayout.NORTH);
        return p;
    }

    /** Karte mit fester Mindestbreite (für die Entscheidung „nebeneinander oder untereinander“). */
    static JComponent mindestens(JComponent k, int breite) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override public Dimension getMinimumSize() { return new Dimension(breite, super.getMinimumSize().height); }
        };
        p.setOpaque(false);
        InventarLayout.eigen(p);
        p.add(k, BorderLayout.CENTER);
        return p;
    }

    private static JComponent energieKachel(Energie e, Zeile z, Integer wert, HeroData h, Daten d) {
        Color f = farbe(e.kuerzel);
        Rund r = kachel(f, new BorderLayout(0, 6));
        JPanel oben = new JPanel(new BorderLayout(8, 0));
        oben.setOpaque(false);
        InventarLayout.eigen(oben);
        JLabel n = fett(e.name, 0);
        n.setIcon(new EigenschaftenSymbole(e.kuerzel, 20, f));
        n.setIconTextGap(8);
        oben.add(n, BorderLayout.CENTER);
        JComponent se = seHaken(z, e.name);
        if (se.isVisible()) oben.add(se, BorderLayout.EAST);
        r.add(oben, BorderLayout.NORTH);

        JPanel mitte = new JPanel();
        mitte.setLayout(new BoxLayout(mitte, BoxLayout.Y_AXIS));
        mitte.setOpaque(false);
        InventarLayout.eigen(mitte);
        JLabel w = fett(wert == null ? "–" : String.valueOf(wert), 10);
        mitte.add(w);
        String kosten = z.kosten == null ? "" : String.valueOf(z.kosten.getText()).trim();
        mitte.add(leise(maximum(z) ? "Maximum erreicht" : kosten.isEmpty() ? " " : "Nächster Schritt: " + kosten));
        String weg = rechenweg(e, h, wert, e.kuerzel.equals("AE") ? verlorenAe(d) : null);
        if (!weg.isEmpty()) {
            JLabel rw = leise("<html><div style='width:170px'>" + esc(weg) + "</div></html>");
            rw.setBorder(new EmptyBorder(3, 0, 0, 0));
            mitte.add(rw);
        }
        if (e.kuerzel.equals("AE")) verloreneAe(mitte, d);
        r.add(mitte, BorderLayout.CENTER);

        JButton b = farbknopf("+  Steigern", f);
        if (!steigernAnbinden(b, z, e.name, wert)) b.setVisible(false);
        r.add(b, BorderLayout.SOUTH);

        String tip = "<html><div style='width:340px'><b>" + e.name + " (" + e.kuerzel + ")</b><br>" + e.zweck + ".<br><br>"
                + (weg.isEmpty() ? "" : esc(weg) + "<br>")
                + "Steigt eine der Eigenschaften, steigt der Grundwert mit. Zukaufen ist nur begrenzt möglich.</div></html>";
        TooltipHook.tooltipFuer(e.name, n, w);
        TooltipHook.infoTip(r, tip);
        return r;
    }

    private static JComponent karmaKachel(Energie e, Zeile z, Integer wert, HeroData h) {
        Color f = farbe("KE");
        Rund r = kachel(f, new BorderLayout(12, 0));
        JPanel l = new JPanel();
        l.setLayout(new BoxLayout(l, BoxLayout.Y_AXIS));
        l.setOpaque(false);
        InventarLayout.eigen(l);
        JLabel n = fett(e.name, 0);
        n.setIcon(new EigenschaftenSymbole("KE", 20, f));
        n.setIconTextGap(8);
        l.add(n);
        JLabel w = fett(wert == null ? "–" : String.valueOf(wert), 10);
        l.add(w);
        String weg = rechenweg(e, h, wert, null);
        if (!weg.isEmpty()) l.add(leise(weg));
        r.add(l, BorderLayout.CENTER);
        JButton b = farbknopf("Karmalqueste …", f);
        JComponent knopf = karmalqueste(b);
        JPanel halter = new JPanel(new GridBagLayout());
        halter.setOpaque(false);
        InventarLayout.eigen(halter);
        halter.add(knopf);
        r.add(halter, BorderLayout.EAST);
        TooltipHook.tooltipFuer(e.name, n, w);
        TooltipHook.infoTip(r, "<html><b>Karmaenergie (KE)</b><br>" + e.zweck
                + ".<br>Wird nicht direkt gesteigert, sondern über eine Karmalqueste erhöht.</html>");
        return r;
    }

    /** Dauerhaft verlorene Astralenergie mit „Zurückkaufen“ – bei Helden mit AE immer (ohne Verlust ausgegraut). */
    private static void verloreneAe(JPanel p, Daten d) {
        Zeile z = d.z("Astralenergie-permanent");
        if (z == null || z.feld == null) return;
        String v = String.valueOf(z.feld.getText()).trim();
        JPanel zeile = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        zeile.setOpaque(false);
        zeile.setAlignmentX(0);
        zeile.setBorder(new EmptyBorder(4, 0, 0, 0));
        InventarLayout.eigen(zeile);
        JLabel n = leise("dauerhaft verloren: " + (v.isEmpty() ? "0" : v) + "   ");
        TooltipHook.infoTip(n, "Dauerhaft verlorene Astralenergie (z. B. durch bestimmte Rituale) kann mit AP zurückgekauft werden.");
        zeile.add(n);
        JButton b = new JButton("Zurückkaufen");
        b.setFocusable(false);
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setMargin(new Insets(0, 6, 0, 6));
        b.setFont(b.getFont().deriveFont(b.getFont().getSize2D() - 1f));
        InventarLayout.eigen(b);
        if (z.knopf == null) b.setVisible(false);
        else {
            b.setEnabled(z.knopf.isEnabled());
            String k = z.kosten == null ? "" : String.valueOf(z.kosten.getText()).trim();
            TooltipHook.infoTip(b, z.knopf.isEnabled() ? "Einen Punkt dauerhaft verlorener AE zurückkaufen" + (k.isEmpty() ? "" : " (" + k + ")")
                    : "Keine dauerhaft verlorene AE.");
            b.addActionListener(e -> z.knopf.doClick(0));
        }
        zeile.add(b);
        p.add(zeile);
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

    static String esc(String t) { return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // ------------------------------------------------------------------ Kampf-Basiswerte

    private static JComponent kampfBasis(Daten d, HeroData h) {
        return mindestens(kampfBasisKarte("kampf", "Kampf-Basiswerte", d.zahl("Attacke"), d.zahl("Parade"), d.zahl("Fernkampf-Basis"),
                d.zahl("Initiative"), h, "Die Verteilung der Kampftalente auf Attacke und Parade steht im Reiter „Kampfwerte“."), 420);
    }

    /** Karte mit den vier Kampf-Basiswerten als Kacheln und aufklappbaren Berechnungsgrundlagen (auch im Reiter „Kampfwerte“). */
    static Rund kampfBasisKarte(String symbol, String titel, Integer at, Integer pa, Integer fk, Integer ini, HeroData h, String fussText) {
        Integer[] werte = {at, pa, fk, ini};
        Rund k = karte(symbol, titel, null, null);
        String[][] b = {
                {"AT", "Attacke", "0", "AT-Basis", "MU,GE,KK", "Grundlage aller Attacke-Werte im Nahkampf"},
                {"PA", "Parade", "1", "PA-Basis", "IN,GE,KK", "Grundlage aller Parade-Werte und des Ausweichens"},
                {"FK", "Fernkampf", "2", "FK-Basis", "IN,FF,KK", "Grundlage aller Fernkampfwerte"},
                {"INI", "Initiative", "3", "INI-Basis", "MU,MU,IN,GE",
                        "Grundlage der Reihenfolge im Kampf; dazu kommen 1W6 sowie Waffen- und Rüstungsmodifikatoren"}};
        JPanel reihe = new JPanel(new Raster(4, 88, 10, false));
        reihe.setOpaque(false);
        InventarLayout.eigen(reihe);
        JPanel formeln = new JPanel(new GridBagLayout());
        formeln.setOpaque(false);
        InventarLayout.eigen(formeln);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        for (String[] x : b) {
            Integer wert = werte[Integer.parseInt(x[2])];
            String[] eig = x[4].split(",");
            Color f = farbe(x[0]);
            Rund t = kachel(f, new GridBagLayout());
            t.setBorder(new EmptyBorder(10, 6, 10, 6));
            GridBagConstraints tc = new GridBagConstraints();
            tc.gridx = 0;
            JLabel w = fett(wert == null ? "–" : String.valueOf(wert), 14);
            t.add(w, tc);
            tc.gridy = 1;
            JLabel kz = fett(x[0], 0);
            kz.setForeground(f);
            t.add(kz, tc);
            tc.gridy = 2;
            t.add(leise("(" + x[1] + ")"), tc);
            String formel = KampfwerteLayout.formel(eig, h, wert);
            TooltipHook.infoTip(t, "<html><b>" + x[3] + "</b><br>" + x[5] + ".<br>Formel: (" + String.join(" + ", eig)
                    + ") / 5, kaufmännisch gerundet.</html>");
            reihe.add(t);

            // Zeile der Berechnungsgrundlagen: Farbstrich, Name, Formel
            c.gridx = 0;
            c.insets = new Insets(4, 0, 4, 10);
            JComponent strich = new JComponent() {
                @Override public Dimension getPreferredSize() { return new Dimension(3, 18); }
                @Override protected void paintComponent(Graphics g) { g.setColor(f); g.fillRoundRect(0, 0, 3, getHeight(), 3, 3); }
            };
            formeln.add(strich, c);
            c.gridx = 1;
            c.insets = new Insets(4, 0, 4, 14);
            JLabel n = fett(x[3], 0);
            formeln.add(n, c);
            c.gridx = 2;
            c.weightx = 1;
            c.insets = new Insets(4, 0, 4, 0);
            formeln.add(leise("<html><div style='width:230px'>" + esc(formel) + "</div></html>"), c);
            c.weightx = 0;
            c.gridy++;
        }
        JPanel inhalt = new JPanel(new BorderLayout(0, 12));
        inhalt.setOpaque(false);
        InventarLayout.eigen(inhalt);
        inhalt.add(reihe, BorderLayout.NORTH);
        inhalt.add(aufklapper("info", "Berechnungsgrundlagen", formeln,
                fussText == null ? null : leise("<html><div style='width:300px'>" + esc(fussText) + "</div></html>"),
                () -> rechenwegOffen, v -> rechenwegOffen = v, false), BorderLayout.CENTER);
        k.add(oben(inhalt), BorderLayout.CENTER);
        return k;
    }

    /**
     * Aufklappbarer Bereich in einer Kachel: Kopfzeile mit Symbol, Titel und „Details anzeigen/ausblenden“,
     * darunter der Inhalt und ein optionaler Fußhinweis (immer sichtbar).
     */
    static JComponent aufklapper(String symbol, String titel, JComponent inhalt, JComponent fuss,
                                         java.util.function.BooleanSupplier offen, java.util.function.Consumer<Boolean> setzen,
                                         boolean grossTitel) {
        Rund r = new Rund(new BorderLayout(0, 8), grossTitel ? null : Theme.kachel(), grossTitel ? null : Theme.trenner(), 12);
        r.setBorder(grossTitel ? new EmptyBorder(0, 0, 0, 0) : new EmptyBorder(8, 12, 10, 12));
        JPanel kopf = new JPanel(new BorderLayout(10, 0));
        kopf.setOpaque(false);
        InventarLayout.eigen(kopf);
        JLabel t = grossTitel ? InventarLayout.titel(titel) : fett(titel, 0);
        t.setIcon(grossTitel ? new Plakette(symbol, 34, Theme.accent(), Color.WHITE) : new EigenschaftenSymbole(symbol, 18, Theme.muted()));
        t.setIconTextGap(grossTitel ? 12 : 8);
        InventarLayout.eigen(t);
        kopf.add(t, BorderLayout.CENTER);
        JButton knopf = new JButton();
        knopf.setFocusable(false);
        knopf.putClientProperty("JButton.buttonType", "borderless");
        InventarLayout.eigen(knopf);
        kopf.add(knopf, BorderLayout.EAST);
        r.add(kopf, BorderLayout.NORTH);
        JPanel koerper = new JPanel(new BorderLayout(0, 8));
        koerper.setOpaque(false);
        InventarLayout.eigen(koerper);
        koerper.add(inhalt, BorderLayout.CENTER);
        r.add(koerper, BorderLayout.CENTER);
        if (fuss != null) {
            JLabel l = fuss instanceof JLabel ? (JLabel) fuss : null;
            if (l != null) { l.setIcon(new EigenschaftenSymbole("info", 16, Theme.muted())); l.setIconTextGap(8); }
            JPanel f = new JPanel(new BorderLayout());
            f.setOpaque(false);
            f.setBorder(new EmptyBorder(4, 0, 0, 0));
            InventarLayout.eigen(f);
            f.add(fuss, BorderLayout.CENTER);
            r.add(f, BorderLayout.SOUTH);
        }
        Runnable zeigen = () -> {
            boolean o = offen.getAsBoolean();
            koerper.setVisible(o);
            knopf.setText(grossTitel ? (o ? "▴" : "▾") : (o ? "Details ausblenden  ▴" : "Details anzeigen  ▾"));
            TooltipHook.infoTip(knopf, o ? "Zuklappen" : "Aufklappen");
        };
        zeigen.run();
        knopf.addActionListener(e -> {
            setzen.accept(!offen.getAsBoolean());
            zeigen.run();
            r.revalidate();
            r.repaint();
            for (Container q = r.getParent(); q != null; q = q.getParent()) { q.revalidate(); if (q instanceof JViewport) break; }
        });
        return r;
    }

    // ------------------------------------------------------------------ Sozialstatus und Gilde

    private static JComponent stand(Daten d) {
        Rund k = karte("person", "Sozialstatus & Gildenzugehörigkeit", null, null);
        JPanel t = KampfwerteLayout.tabelle(true);
        t.setOpaque(false);
        InventarLayout.eigen(t);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(4, 0, 4, 12);
        c.anchor = GridBagConstraints.WEST;

        Zeile so = d.z("Sozialstatus");
        if (so != null && so.feld != null) {
            c.gridx = 0;
            JLabel n = fett("Sozialstatus", 0);
            TooltipHook.tooltipFuer("Sozialstatus", n);
            t.add(n, c);
            c.gridx = 1;
            t.add(sozialstatus(so.feld), c);
            c.gridx = 2;
            c.weightx = 1;
            t.add(leise("<html><div style='width:130px'>Höchstens 21, kostet keine AP.</div></html>"), c);
            c.weightx = 0;
            c.gridy++;
        }
        if (d.gilde != null) {
            c.gridx = 0;
            t.add(fett("Gildenzugehörigkeit", 0), c);
            c.gridx = 1;
            c.gridwidth = 2;
            c.fill = GridBagConstraints.HORIZONTAL;
            t.add(gilde(d.gilde), c);
            c.fill = GridBagConstraints.NONE;
            c.gridy++;
            c.gridx = 1;
            t.add(leise("<html><div style='width:180px'>" + (d.gilde.isEnabled() ? "Weiße, graue oder schwarze Gilde."
                    : "Nur für Gildenmagier und Lehrmeister wählbar.") + "</div></html>"), c);
            c.gridwidth = 1;
        }
        k.add(t, BorderLayout.CENTER);
        return k;
    }

    /** Eigenes Zahlenfeld; übernommen wird über das Feld der Helden-Software (wie Enter dort). */
    private static JComponent sozialstatus(JTextField orig) {
        int wert;
        try { wert = Integer.parseInt(String.valueOf(orig.getText()).trim()); } catch (NumberFormatException e) { wert = 1; }
        SpinnerNumberModel m = new SpinnerNumberModel(wert, Math.min(1, wert), Math.max(21, wert), 1);
        JSpinner s = new JSpinner(m) {
            @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        };
        ((JSpinner.DefaultEditor) s.getEditor()).getTextField().setColumns(3);
        TooltipHook.infoTip(s, "Sozialstatus (1–21); wird sofort übernommen");
        InventarLayout.eigen(s);
        s.addChangeListener(e -> {
            String neu = String.valueOf(s.getValue());
            if (neu.equals(String.valueOf(orig.getText()).trim())) return;
            orig.setText(neu);
            orig.postActionEvent();
            // die Helden-Software setzt bei ungültiger Eingabe den alten Wert zurück
            SwingUtilities.invokeLater(() -> {
                try {
                    int ist = Integer.parseInt(String.valueOf(orig.getText()).trim());
                    if (!Integer.valueOf(ist).equals(s.getValue())) s.setValue(ist);
                } catch (IllegalArgumentException ignore) { }
            });
        });
        return s;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JComponent gilde(JComboBox orig) {
        DefaultComboBoxModel m = new DefaultComboBoxModel();
        for (int i = 0; i < orig.getItemCount(); i++) m.addElement(orig.getItemAt(i));
        JComboBox b = new JComboBox(m);
        b.setSelectedItem(orig.getSelectedItem());
        b.setEnabled(orig.isEnabled());
        b.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList l, Object v, int i, boolean s, boolean f) {
                String t = v == null || String.valueOf(v).trim().isEmpty() ? "– Keine Auswahl –" : String.valueOf(v);
                return super.getListCellRendererComponent(l, t, i, s, f);
            }
        });
        b.setPrototypeDisplayValue("– Keine Auswahl –    ");
        InventarLayout.eigen(b);
        b.addActionListener(e -> {
            Object neu = b.getSelectedItem();
            if (neu != null && !neu.equals(orig.getSelectedItem())) orig.setSelectedItem(neu);
        });
        return b;
    }

    // ------------------------------------------------------------------ Hilfe

    private static JComponent regeln() {
        String[][] punkte = {
                {"kosten", "Steigerungskosten", "Jeder Schritt erhöht den Wert um 1 und kostet AP – höhere Werte werden teurer."},
                {"ap", "Spezielle Erfahrung (SE)", "Macht den nächsten Schritt günstiger und wird danach verbraucht."},
                {"grenze", "Grenzen", "Eigenschaften steigen nur bis zum Höchstwert; LE, AU, AE und MR lassen sich nur begrenzt zukaufen."},
                {"rechnen", "Berechnung der Grundwerte", "LE, AU, AE, MR und die Kampf-Basiswerte entstehen aus den Eigenschaften und steigen mit."},
                {"minus", "Zurücknehmen", "„−“ nimmt die letzte Steigerung einer Eigenschaft zurück und erstattet die AP."},
                {"speichern", "Speichern", "Dauerhaft wird jede Änderung erst beim Speichern des Helden."}};
        return hilfeKarte("Wie funktioniert die Steigerung?", punkte, 260, 1, () -> hilfeOffen, v -> hilfeOffen = v);
    }

    /** Aufklappbare Hilfe-Karte: je Punkt Symbol, fetter Titel und kurze Erklärung. */
    static JComponent hilfeKarte(String titel, String[][] punkte, int textBreite, int spalten,
                                 java.util.function.BooleanSupplier offen, java.util.function.Consumer<Boolean> setzen) {
        Rund k = new Rund(new BorderLayout(), Theme.card(), Theme.trenner(), 16) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        k.setBorder(new EmptyBorder(14, 16, 16, 16));
        // Punkte in Spalten, die sich der Breite anpassen; der Text bricht nach der tatsächlichen Breite um
        JPanel liste = new JPanel(new Raster(spalten, textBreite, 10, false));
        liste.setOpaque(false);
        InventarLayout.eigen(liste);
        for (String[] p : punkte) {
            JPanel punkt = new JPanel(new BorderLayout(12, 0));
            punkt.setOpaque(false);
            punkt.setBorder(new EmptyBorder(4, 0, 4, 0));
            InventarLayout.eigen(punkt);
            JLabel sym = new JLabel(new Plakette(p[0], 32, mische(Theme.kachel(), vordergrund(), Theme.isDark() ? 0.14 : 0.07),
                    mische(vordergrund(), Theme.kachel(), 0.15)));
            sym.setVerticalAlignment(SwingConstants.TOP);
            InventarLayout.eigen(sym);
            punkt.add(sym, BorderLayout.WEST);
            JPanel t = new JPanel(new BorderLayout());
            t.setOpaque(false);
            InventarLayout.eigen(t);
            t.add(fett(p[1], 0), BorderLayout.NORTH);
            HtmlText text = new HtmlText("<html>" + p[2] + "</html>");
            text.setForeground(Theme.muted());
            text.setFont(text.getFont().deriveFont(text.getFont().getSize2D() - 1f));
            InventarLayout.eigen(text);
            t.add(text, BorderLayout.CENTER);
            punkt.add(t, BorderLayout.CENTER);
            liste.add(punkt);
        }
        k.add(aufklapper("liste", titel, liste, null, offen, setzen, true), BorderLayout.CENTER);
        return k;
    }
}
