package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reiter „Kampfwerte“ in den modernen Designs: kompakte Tabelle für Nah- und Fernkampf mit Rechenweg, erklärten
 * Basiswerten und Hinweisen zur Verteilung der TaP auf AT und PA.
 *
 * Verteilt wird weiter über die +1/−1-Knöpfe der Helden-Software: Die eigenen Knöpfe lösen nur deren Knöpfe aus.
 * Die Ansicht der Helden-Software bleibt unsichtbar im Fenster; im Design „Original“ wird sie wieder angezeigt.
 */
final class KampfwerteLayout {

    private KampfwerteLayout() {}

    private static final String MARKE = "modernhelden.kampfwerte";
    /** Größter erlaubter Unterschied zwischen AT- und PA-Anteil (so prüft es auch die Helden-Software). */
    static final int MAX_DIFFERENZ = 5;

    private static final Map<JComponent, Zustand> alle = new WeakHashMap<>();

    static void pruefen(JComponent c, boolean modern) {
        Object o = c.getClientProperty(MARKE);
        Zustand z = o instanceof Zustand ? (Zustand) o : null;
        if (z == null) {
            if (!modern || !(c instanceof JPanel) || !(c.getParent() instanceof JViewport) || !istKampfwerte((JPanel) c)) return;
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

    private static boolean istKampfwerte(JPanel p) {
        if (!(p.getLayout() instanceof GridBagLayout)) return false;
        for (Component k : p.getComponents())
            if (k instanceof JLabel && String.valueOf(((JLabel) k).getText()).startsWith("AT-Basis:")) return true;
        return false;
    }

    // ------------------------------------------------------------------ Daten aus der Ansicht der Helden-Software

    private static final class Nahkampf {
        String name;
        JTextField at, pa;
        AbstractButton atPlus, atMinus, paPlus, paMinus;
        JLabel tap;
    }

    private static final class Fernkampf {
        String name;
        JTextField wert;
    }

    private static final class Daten {
        Integer atBasis, paBasis, fkBasis;
        String rs = "";
        final List<Nahkampf> nah = new ArrayList<>();
        final List<Fernkampf> fern = new ArrayList<>();
    }

    private static final Pattern ZAHL = Pattern.compile("-?\\d+");
    private static final Pattern TAP = Pattern.compile("(-?\\d+)\\s*/\\s*(-?\\d+)");

    private static Integer zahl(String s) {
        if (s == null) return null;
        Matcher m = ZAHL.matcher(s);
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    private static Daten lesen(JPanel p) {
        Daten d = new Daten();
        GridBagLayout gb = (GridBagLayout) p.getLayout();
        Map<Integer, Map<Integer, List<Component>>> zeilen = new TreeMap<>();
        for (Component k : p.getComponents()) {
            if (k instanceof JLabel) {
                String t = String.valueOf(((JLabel) k).getText()).trim();
                if (t.startsWith("AT-Basis:")) { d.atBasis = zahl(t); continue; }
                if (t.startsWith("PA-Basis:")) { d.paBasis = zahl(t); continue; }
                if (t.startsWith("FK-Basis:")) { d.fkBasis = zahl(t); continue; }
                if (t.startsWith("RS-Berechnung:")) { d.rs = t.substring("RS-Berechnung:".length()).trim(); continue; }
            }
            GridBagConstraints c = gb.getConstraints(k);
            zeilen.computeIfAbsent(c.gridy, y -> new TreeMap<>()).computeIfAbsent(c.gridx, x -> new ArrayList<>()).add(k);
        }
        for (Map<Integer, List<Component>> z : zeilen.values()) {
            JLabel name = erstes(z.get(0), JLabel.class);
            JTextField f1 = erstes(z.get(2), JTextField.class);
            if (name == null || f1 == null) continue;
            String n = String.valueOf(name.getText()).trim();
            if (n.endsWith(":")) n = n.substring(0, n.length() - 1).trim();
            JTextField f2 = erstes(z.get(6), JTextField.class);
            if (f2 == null) {
                Fernkampf f = new Fernkampf();
                f.name = n;
                f.wert = f1;
                d.fern.add(f);
                continue;
            }
            Nahkampf k = new Nahkampf();
            k.name = n;
            k.at = f1;
            k.pa = f2;
            k.atPlus = erstes(z.get(3), AbstractButton.class);
            k.atMinus = erstes(z.get(4), AbstractButton.class);
            k.paPlus = erstes(z.get(7), AbstractButton.class);
            k.paMinus = erstes(z.get(8), AbstractButton.class);
            k.tap = erstes(z.get(9), JLabel.class);
            d.nah.add(k);
        }
        return d;
    }

    private static <T> T erstes(List<Component> l, Class<T> art) {
        if (l == null) return null;
        for (Component k : l) if (art.isInstance(k)) return art.cast(k);
        return null;
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

        Zustand(JPanel original, JViewport vp) {
            this.original = original;
            this.viewport = vp;
            spaeter.setRepeats(false);
            // die Helden-Software baut die Zeilen beim Heldenwechsel neu auf
            original.addContainerListener(new ContainerListener() {
                public void componentAdded(ContainerEvent e) { spaeter(); }
                public void componentRemoved(ContainerEvent e) { spaeter(); }
            });
        }

        void spaeter() { if (aktiv) spaeter.restart(); }

        void modern() {
            if (aktiv) return;
            aktiv = true;
            huelle = new Huelle(ansicht);
            huelle.setOpaque(false);
            InventarLayout.eigen(huelle);
            huelle.add(ansicht, BorderLayout.CENTER);
            // Original bleibt im Fenster (seine Knöpfe werden ausgelöst), aber unsichtbar
            original.setVisible(false);
            huelle.add(original, BorderLayout.SOUTH);
            viewport.setView(huelle);
            SwingUtilities.updateComponentTreeUI(ansicht);
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

        /** Änderungen an Feldern, TaP-Anzeige und Knöpfen der Helden-Software → Ansicht neu aufbauen. */
        void anbinden(Daten d) {
            DocumentListener dl = new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { spaeter(); }
                public void removeUpdate(DocumentEvent e) { spaeter(); }
                public void changedUpdate(DocumentEvent e) { spaeter(); }
            };
            List<Component> neu = new ArrayList<>();
            for (Nahkampf k : d.nah) {
                neu.add(k.at); neu.add(k.pa);
                if (k.tap != null) neu.add(k.tap);
                for (AbstractButton b : new AbstractButton[]{k.atPlus, k.atMinus, k.paPlus, k.paMinus}) if (b != null) neu.add(b);
            }
            for (Fernkampf f : d.fern) neu.add(f.wert);
            for (Component c : neu) {
                if (!angebunden.add(c)) continue;
                if (c instanceof JTextField) ((JTextField) c).getDocument().addDocumentListener(dl);
                else if (c instanceof JLabel) c.addPropertyChangeListener("text", e -> spaeter());
                else c.addPropertyChangeListener("enabled", e -> spaeter());
            }
        }

        /** Zuletzt angezeigter Stand – unverändert (z. B. beim Reiterwechsel) wird nicht neu aufgebaut. */
        String letzterStand;

        void neuAufbauen() {
            if (!aktiv) return;
            Daten d = lesen(original);
            anbinden(d);
            HeroData h = InventarLayout.held();
            String stand = stand(d, h);
            if (stand.equals(letzterStand) && ansicht.getComponentCount() > 0) return;
            letzterStand = stand;
            if (DebugBaum.ordner != null) System.err.println("[Kampfwerte] Ansicht neu aufgebaut");
            Point pos = viewport.getViewPosition();
            ansicht.aufbauen(d, InventarLayout.held());
            viewport.revalidate();
            viewport.repaint();
            SwingUtilities.invokeLater(() -> viewport.setViewPosition(pos));
        }
    }

    /** Alles, was die Ansicht zeigt, als Text – zum Erkennen, ob sich etwas geändert hat. */
    private static String stand(Daten d, HeroData h) {
        StringBuilder sb = new StringBuilder();
        sb.append(h == null ? "" : h.name).append('|').append(Theme.current()).append('|')
                .append(d.atBasis).append(',').append(d.paBasis).append(',').append(d.fkBasis).append(',').append(d.rs);
        if (h != null) sb.append('|').append(h.eig).append(h.abgeleitet);
        for (Nahkampf n : d.nah) {
            sb.append('|').append(n.name).append(':').append(n.at.getText()).append('/').append(n.pa.getText())
                    .append('/').append(n.tap == null ? "" : n.tap.getText());
            for (AbstractButton b : new AbstractButton[]{n.atPlus, n.atMinus, n.paPlus, n.paMinus})
                sb.append(b != null && b.isEnabled() ? '1' : '0');
        }
        for (Fernkampf f : d.fern) sb.append('|').append(f.name).append(':').append(f.wert.getText());
        return sb.toString();
    }

    // ------------------------------------------------------------------ Ansicht

    /** Sichtbares Element im Scrollbereich: folgt der Fensterbreite, solange der Inhalt hineinpasst. */
    static final class Huelle extends JPanel implements Scrollable {
        final JComponent inhalt;
        Huelle(JComponent inhalt) { super(new BorderLayout()); this.inhalt = inhalt; }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 200; }
        public boolean getScrollableTracksViewportWidth() {
            return !(getParent() instanceof JViewport) || getParent().getWidth() >= inhalt.getMinimumSize().width;
        }
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
        }
    }

    /** Breite folgt dem Fenster, Höhe dem Inhalt (scrollt bei Bedarf). */
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
            add(links(basiswerte(d, h)));
            if (!d.nah.isEmpty()) {
                add(Box.createVerticalStrut(14));
                add(links(nahkampf(d, h)));
            }
            if (!d.fern.isEmpty()) {
                add(Box.createVerticalStrut(14));
                add(links(fernkampf(d, h)));
            }
            add(Box.createVerticalStrut(14));
            add(links(regeln()));
            revalidate();
            repaint();
        }
    }

    static JComponent links(JComponent c) {
        c.setAlignmentX(0);
        return c;
    }

    static JPanel karte(String titel) {
        // volle Breite, aber nur so hoch wie nötig
        JPanel k = new JPanel(new BorderLayout(0, 8)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        k.setBackground(Theme.card());
        k.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(10, 12, 12, 12)));
        if (titel != null) k.add(InventarLayout.titel(titel), BorderLayout.NORTH);
        InventarLayout.eigen(k);
        return k;
    }

    /** Tabelle, die nie schmaler wird als ihr Inhalt (sonst schneidet GridBagLayout Zellen ab). */
    static JPanel tabelle() { return tabelle(false); }

    /**
     * @param schrumpfbar true: im kleinen Fenster dürfen Zellen mit kleinerer Mindestgröße (Hinweis, Balken) schmaler
     *                    werden; alle übrigen behalten ihre volle Breite.
     */
    static JPanel tabelle(boolean schrumpfbar) {
        return new JPanel(new GridBagLayout()) {
            @Override public Dimension getMinimumSize() { return schrumpfbar ? super.getMinimumSize() : getPreferredSize(); }
            /** Jede Beschriftung bekommt rechts etwas Luft – manche Schriften (z. B. Segoe UI) ragen sonst über die berechnete Breite. */
            @Override protected void addImpl(Component comp, Object constraints, int index) {
                if (comp instanceof JLabel && ((JLabel) comp).getBorder() == null)
                    ((JLabel) comp).setBorder(new EmptyBorder(0, 0, 0, 4));
                super.addImpl(comp, constraints, index);
            }
        };
    }

    static JLabel klein(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.muted());
        l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 1f));
        return l;
    }

    // ------------------------------------------------------------------ Basiswerte

    private static JComponent basiswerte(Daten d, HeroData h) {
        JPanel k = karte("Basiswerte");
        JPanel reihe = new Kacheln(10);
        reihe.setOpaque(false);
        reihe.add(basis("AT-Basis", d.atBasis, h, new String[]{"MU", "GE", "KK"}, "Grundlage aller Attacke-Werte im Nahkampf"));
        reihe.add(basis("PA-Basis", d.paBasis, h, new String[]{"IN", "GE", "KK"}, "Grundlage aller Parade-Werte und des Ausweichens"));
        reihe.add(basis("FK-Basis", d.fkBasis, h, new String[]{"IN", "FF", "KK"}, "Grundlage aller Fernkampfwerte"));
        reihe.add(basis("INI-Basis", h == null ? null : h.abgeleitet.get("INI"), h, new String[]{"MU", "MU", "IN", "GE"},
                "Initiative-Basiswert: Grundlage der Reihenfolge im Kampf; dazu kommen 1W6 sowie Waffen- und Rüstungsmodifikatoren"));
        k.add(reihe, BorderLayout.CENTER);
        if (!d.rs.isEmpty()) {
            // bricht im kleinen Fenster um, statt die Seite zu verbreitern
            JLabel hin = new HtmlText("<html>Rüstungsschutz wird berechnet nach: " + d.rs
                    + " · Waffen- und Rüstungsmodifikatoren stehen im Reiter „Ausrüstung“</html>");
            hin.setForeground(Theme.muted());
            hin.setFont(hin.getFont().deriveFont(hin.getFont().getSize2D() - 1f));
            k.add(hin, BorderLayout.SOUTH);
        }
        return k;
    }

    /**
     * Kacheln in so vielen gleich breiten Spalten, wie nebeneinander passen (4, 2 oder 1) – bei großer Schrift
     * oder schmalem Fenster brechen sie um, statt abgeschnitten zu werden.
     */
    static final class Kacheln extends JPanel {
        final int abstand;
        private java.awt.event.ComponentListener vpHoerer;
        private JViewport vp;
        Kacheln(int abstand) { super(null); this.abstand = abstand; setOpaque(false); }

        private int breiteste() {
            int w = 0;
            for (Component c : getComponents()) w = Math.max(w, c.getPreferredSize().width);
            return w;
        }
        private int hoechste() {
            int h = 0;
            for (Component c : getComponents()) h = Math.max(h, c.getPreferredSize().height);
            return h;
        }
        private int spalten(int breite) {
            int n = getComponentCount();
            if (n == 0) return 1;
            // Reserve je Kachel: manche Schriften (z. B. Segoe UI) laufen breiter als berechnet
            int b = breiteste() + 32;
            for (int sp : new int[]{n, (n + 1) / 2, 1})
                if (sp >= 1 && sp * b + (sp - 1) * abstand <= breite) return sp;
            return 1;
        }

        /**
         * Im Fenster sichtbare Breite: Ist die Seite breiter als das Fenster (z. B. wegen einer breiten Tabelle
         * darunter), sollen die Kacheln trotzdem ganz im sichtbaren Bereich liegen – und mit dem Fenster wachsen.
         */
        private int verfuegbar() {
            Insets i = getInsets();
            int eigen = getWidth() - i.left - i.right;
            JViewport v = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
            if (v == null || v.getView() == null) return eigen;
            Point p = SwingUtilities.convertPoint(this, 0, 0, v.getView());
            // rechts dieselbe Luft wie links (Abstand der Karte zum Seitenrand)
            int sichtbar = v.getExtentSize().width - 2 * Math.max(p.x, 0) - i.left - i.right;
            return eigen <= 0 ? sichtbar : Math.max(1, Math.min(eigen, sichtbar));
        }

        @Override public void addNotify() {
            super.addNotify();
            vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
            if (vp != null) {
                // Fenstergröße ändert sich, die (breitere) Seite aber nicht – trotzdem neu anordnen
                vpHoerer = new java.awt.event.ComponentAdapter() {
                    @Override public void componentResized(java.awt.event.ComponentEvent e) { revalidate(); repaint(); }
                };
                vp.addComponentListener(vpHoerer);
            }
        }
        @Override public void removeNotify() {
            if (vp != null && vpHoerer != null) vp.removeComponentListener(vpHoerer);
            vp = null;
            super.removeNotify();
        }

        @Override public Dimension getPreferredSize() {
            int n = getComponentCount();
            // noch nicht angeordnet: Breite aus dem Scrollbereich schätzen, damit die Kacheln nicht erst
            // einreihig erscheinen und dann umspringen
            int breite = verfuegbar();
            if (breite <= 0) {
                JViewport v = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
                breite = v != null && v.getWidth() > 0 ? v.getWidth() - 2 * 12 - 2 * 13 - 2 : n * breiteste() + (n - 1) * abstand;
            }
            int sp = spalten(breite), zeilen = (n + sp - 1) / sp;
            Insets i = getInsets();
            return new Dimension(breiteste() + i.left + i.right,
                    zeilen * hoechste() + (zeilen - 1) * abstand + i.top + i.bottom);
        }
        @Override public Dimension getMinimumSize() {
            Insets i = getInsets();
            int n = getComponentCount();
            return new Dimension(breiteste() + i.left + i.right, n * hoechste() + (n - 1) * abstand + i.top + i.bottom);
        }
        @Override public void doLayout() {
            int n = getComponentCount();
            if (n == 0) return;
            Insets i = getInsets();
            int breite = verfuegbar();
            int sp = spalten(breite);
            int zw = (breite - (sp - 1) * abstand) / sp, zh = hoechste();
            for (int k = 0; k < n; k++) {
                int x = i.left + (k % sp) * (zw + abstand), y = i.top + (k / sp) * (zh + abstand);
                getComponent(k).setBounds(x, y, zw, zh);
            }
            // Höhe hängt von der Spaltenzahl ab – bei Änderung neu anordnen lassen
            int zeilen = (n + sp - 1) / sp, soll = zeilen * zh + (zeilen - 1) * abstand + i.top + i.bottom;
            if (getHeight() != soll) SwingUtilities.invokeLater(this::revalidate);
        }
    }

    static JComponent basis(String titel, Integer wert, HeroData h, String[] eig, String zweck) {
        JPanel t = new JPanel(new BorderLayout(10, 0));
        t.setBackground(Theme.kachel());
        t.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.trenner()), new MatteBorder(0, 3, 0, 0, Theme.accent())),
                new EmptyBorder(6, 10, 6, 10)));
        JLabel w = new JLabel(wert == null ? "–" : String.valueOf(wert));
        w.setFont(w.getFont().deriveFont(Font.BOLD, w.getFont().getSize2D() * 1.7f));
        t.add(w, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        JLabel tl = new JLabel(titel);
        tl.setAlignmentX(0);
        tl.setFont(tl.getFont().deriveFont(Font.BOLD));
        text.add(tl);
        // Formel in zwei Zeilen („(MU 13 + GE 11 + KK 11)“ / „/ 5 = 7“) – so passen die Kacheln auch im kleinen Fenster
        tl.setBorder(new EmptyBorder(0, 0, 0, 6));
        text.add(new Formel(formel(eig, h, wert)));
        t.add(text, BorderLayout.CENTER);
        TooltipHook.infoTip(t, "<html><b>" + titel + "</b><br>" + zweck + ".<br>Formel: (" + String.join(" + ", eig)
                + ") / 5, kaufmännisch gerundet.</html>");
        InventarLayout.eigen(t);
        return t;
    }

    /**
     * Formel einer Basiswert-Kachel: in einer Zeile, wenn die Kachel breit genug ist (mit etwas Reserve für
     * Windows-Schriften), sonst in zwei Zeilen („(MU 13 + GE 11 + KK 11)“ / „/ 5 = 7“).
     */
    static final class Formel extends JLabel {
        final String ganz, zweizeilig;
        final int breitEinzeilig;
        boolean einzeilig;

        Formel(String f) {
            int teil = f.indexOf(") / ");
            ganz = f;
            zweizeilig = teil > 0 ? "<html>" + esc(f.substring(0, teil + 1)) + "<br>" + esc(f.substring(teil + 2)) + "</html>" : f;
            setForeground(Theme.muted());
            setFont(getFont().deriveFont(getFont().getSize2D() - 1f));
            setBorder(new EmptyBorder(0, 0, 0, 6));
            setAlignmentX(0);
            setVerticalAlignment(CENTER);
            breitEinzeilig = getFontMetrics(getFont()).stringWidth(f) + 6 + 10;   // + Rand + Reserve
            setText(zweizeilig);
        }
        private static String esc(String t) { return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
        /**
         * Größe immer von der zweizeiligen Fassung: Breite klein (mehr Kacheln nebeneinander), Höhe fest – so
         * springt beim Umschalten nichts, die einzeilige Fassung steht einfach mittig.
         */
        @Override public Dimension getPreferredSize() {
            JLabel probe = new JLabel(zweizeilig);
            probe.setFont(getFont());
            probe.setBorder(getBorder());
            return probe.getPreferredSize();
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override public void setBounds(int x, int y, int w, int h) {
            super.setBounds(x, y, w, h);
            boolean soll = w >= breitEinzeilig;
            if (soll != einzeilig) {
                einzeilig = soll;
                setText(soll ? ganz : zweizeilig);
                // einzeilig direkt unter dem Titel
                setVerticalAlignment(soll ? TOP : CENTER);
            }
        }
    }

    /** „(MU 15 + GE 14 + KK 15) / 5 = 8,8 → 9“ mit den Werten des Helden. */
    static String formel(String[] eig, HeroData h, Integer ergebnis) {
        StringBuilder sb = new StringBuilder("(");
        int summe = 0;
        boolean alle = h != null;
        for (int i = 0; i < eig.length; i++) {
            Integer v = h == null ? null : h.eig.get(eig[i]);
            if (v == null) alle = false; else summe += v;
            if (i > 0) sb.append(" + ");
            sb.append(eig[i]);
            if (v != null) sb.append(' ').append(v);
        }
        sb.append(") / 5");
        if (!alle) return sb.toString();
        double roh = summe / 5.0;
        long gerundet = Math.round(roh);
        sb.append(" = ").append(InventarLayout.zahl(roh));
        if (roh != gerundet) sb.append(" → ").append(gerundet);
        if (ergebnis != null && ergebnis != gerundet) sb.append(" (mit Modifikatoren ").append(ergebnis).append(')');
        return sb.toString();
    }

    // ------------------------------------------------------------------ Nahkampf

    private static JComponent nahkampf(Daten d, HeroData h) {
        JPanel k = karte("Nahkampf");
        JPanel t = tabelle(true);
        t.setOpaque(false);
        InventarLayout.eigen(t);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        // „Hinweis“ so breit wie sein längster Text, „Zurücksetzen“ direkt dahinter, der Rest bleibt frei
        String[] kopf = {"Talent", "TaW", "AT", "", "PA", "", "Verteilung", "AT | PA", "Hinweis", "<html><center>Zurück-<br>setzen</center></html>", ""};
        for (int i = 0; i < kopf.length; i++) {
            c.gridx = i;
            c.weightx = i == 10 ? 1 : 0;
            c.fill = GridBagConstraints.NONE;
            // Zahlenspalten rechtsbündig wie ihr Inhalt, „Zurücksetzen“ mittig über dem Knopf
            c.anchor = i == 1 || i == 2 || i == 4 ? GridBagConstraints.EAST : i == 9 ? GridBagConstraints.CENTER : GridBagConstraints.WEST;
            t.add(kopfzelle(kopf[i], c.anchor == GridBagConstraints.EAST), c);
        }
        c.anchor = GridBagConstraints.WEST;
        for (Nahkampf n : d.nah) {
            c.gridy++;
            zeile(t, c, n, d, h);
        }
        k.add(t, BorderLayout.CENTER);
        return k;
    }

    private static void zeile(JPanel t, GridBagConstraints c, Nahkampf n, Daten d, HeroData h) {
        Integer at = zahl(n.at.getText()), pa = zahl(n.pa.getText());
        Integer frei = null, taw = null;
        if (n.tap != null) {
            Matcher m = TAP.matcher(String.valueOf(n.tap.getText()));
            if (m.find()) { frei = Integer.valueOf(m.group(1)); taw = Integer.valueOf(m.group(2)); }
        }
        if (taw == null && h != null) {
            HeroData.Talent ht = h.talent(n.name);
            if (ht != null) taw = ht.wert;
        }
        Integer atA = at != null && d.atBasis != null ? at - d.atBasis : null;
        Integer paA = pa != null && d.paBasis != null ? pa - d.paBasis : null;
        boolean stimmig = atA != null && paA != null && frei != null && taw != null && atA + paA + frei == taw
                && atA >= 0 && paA >= 0;
        String rechenweg = rechenweg(n.name, at, pa, atA, paA, frei, taw, d, stimmig);

        c.insets = new Insets(3, 0, 3, 8);
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.gridx = 0;
        JLabel name = new JLabel(n.name);
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        TooltipHook.infoTip(name, rechenweg);
        InventarLayout.eigen(name);
        t.add(name, c);

        c.gridx = 1;
        c.anchor = GridBagConstraints.EAST;
        t.add(wert(taw == null ? "–" : String.valueOf(taw), false, rechenweg), c);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 2;
        c.anchor = GridBagConstraints.EAST;
        t.add(wert(at == null ? "–" : String.valueOf(at), true, rechenweg), c);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 3;
        t.add(knoepfe(n.atPlus, n.atMinus, plusGrund(frei, atA, paA, stimmig, "AT", "PA"), "AT"), c);
        c.gridx = 4;
        c.anchor = GridBagConstraints.EAST;
        t.add(wert(pa == null ? "–" : String.valueOf(pa), true, rechenweg), c);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 5;
        t.add(knoepfe(n.paPlus, n.paMinus, plusGrund(frei, paA, atA, stimmig, "PA", "AT"), "PA"), c);

        c.gridx = 6;
        Balken b = new Balken(stimmig ? atA : 0, stimmig ? paA : 0, stimmig ? frei : 0);
        TooltipHook.infoTip(b, rechenweg);
        t.add(b, c);

        c.gridx = 7;
        JLabel anteile = klein(stimmig ? "+" + atA + " | +" + paA + (frei > 0 ? " | " + frei + " frei" : "") : "");
        TooltipHook.infoTip(anteile, rechenweg);
        InventarLayout.eigen(anteile);
        t.add(anteile, c);

        c.gridx = 8;
        // zweizeilig („3 TaP / frei“, „AT/PA- / Grenze“) – so bleibt die Spalte schmal und alles passt ins Fenster
        String hw = hinweis(frei, atA, paA, stimmig);
        int lz = hw.lastIndexOf(' ');
        if (hw.startsWith("AT/PA-")) hw = "<html>AT/PA-<br>Grenze</html>";
        else if (lz > 0) hw = "<html>" + hw.substring(0, lz) + "<br>" + hw.substring(lz + 1) + "</html>";
        JLabel hinweis = new JLabel(hw);
        hinweis.setFont(hinweis.getFont().deriveFont(hinweis.getFont().getSize2D() - 1f));
        if (frei != null && frei > 0) hinweis.setForeground(Theme.accent()); else hinweis.setForeground(Theme.muted());
        TooltipHook.infoTip(hinweis, rechenweg);
        InventarLayout.eigen(hinweis);
        t.add(hinweis, c);

        c.gridx = 9;
        c.anchor = GridBagConstraints.CENTER;
        JButton reset = knopf(Symbol.ZURUECK, "Verteilte TaP von " + n.name + " wieder freigeben (AT- und PA-Anteil auf 0), um neu zu verteilen");
        boolean verteilt = stimmig && (atA > 0 || paA > 0);
        if (!verteilt || n.atMinus == null || n.paMinus == null) {
            reset.setEnabled(false);
            TooltipHook.infoTip(reset, stimmig ? "Keine TaP verteilt." : "Zurücksetzen nicht möglich: die Werte enthalten weitere Modifikatoren.");
        } else {
            final int fa = atA, fp = paA;
            reset.addActionListener(e -> zuruecksetzen(reset, n, d, fa, fp));
        }
        t.add(reset, c);
        c.anchor = GridBagConstraints.WEST;
    }

    /** Spaltenkopf; Zahlenspalten mit demselben rechten Rand wie die Werte darunter. */
    static JLabel kopfzelle(String text, boolean rechts) {
        JLabel l = klein(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        if (rechts) {
            l.setHorizontalAlignment(SwingConstants.RIGHT);
            l.setBorder(new EmptyBorder(0, 2, 0, 4));
        }
        return l;
    }

    // ------------------------------------------------------------------ Verteilung zurücksetzen

    /**
     * Gibt alle verteilten TaP eines Talents wieder frei. Die Helden-Software erlaubt „−1“ nur bis zu dem Wert,
     * den das Talent beim Laden hatte (sie merkt ihn sich in ihrem Knopf-Listener). Dieser gemerkte Wert wird für
     * das eine Talent auf die Basis gesetzt; danach nehmen ihre eigenen −1-Knöpfe die Punkte einzeln zurück –
     * gerechnet und geprüft wird also weiter von der Helden-Software.
     */
    private static void zuruecksetzen(Component parent, Nahkampf n, Daten d, int atA, int paA) {
        int ok = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(parent),
                "<html><b>" + n.name + ": Verteilung zurücksetzen?</b><br><br>"
                        + "AT-Anteil +" + atA + " und PA-Anteil +" + paA + " werden wieder zu " + (atA + paA)
                        + " freien TaP.<br>Danach mit + neu auf AT und PA verteilen.<br><br>"
                        + "Dauerhaft wird die Änderung erst beim Speichern des Helden.</html>",
                "Verteilung zurücksetzen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        String fehler = null;
        try {
            if (!startwerteSenken(n, d)) fehler = "Die Helden-Software hat die Verteilung nicht freigegeben (unbekannte Version).";
            else {
                // abwechselnd den größeren Anteil senken, damit der Unterschied nie über die Grenze steigt
                for (int i = 0; i < 100; i++) {
                    Integer at = zahl(n.at.getText()), pa = zahl(n.pa.getText());
                    if (at == null || pa == null) break;
                    int a = at - d.atBasis, p = pa - d.paBasis;
                    if (a <= 0 && p <= 0) break;
                    AbstractButton b = a >= p ? n.atMinus : n.paMinus;
                    b.setEnabled(true);
                    b.doClick(0);
                    Integer at2 = zahl(n.at.getText()), pa2 = zahl(n.pa.getText());
                    if (java.util.Objects.equals(at, at2) && java.util.Objects.equals(pa, pa2)) {
                        // die Helden-Software hat den Schritt abgelehnt – den anderen Anteil versuchen
                        AbstractButton o = b == n.atMinus ? n.paMinus : n.atMinus;
                        if ((o == n.atMinus ? a : p) <= 0) { fehler = "Die Helden-Software hat einen Schritt abgelehnt."; break; }
                        o.setEnabled(true);
                        o.doClick(0);
                        if (java.util.Objects.equals(at, zahl(n.at.getText())) && java.util.Objects.equals(pa, zahl(n.pa.getText()))) {
                            fehler = "Die Helden-Software hat einen Schritt abgelehnt.";
                            break;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            fehler = "Fehler beim Zurücksetzen: " + ex;
        }
        if (fehler != null)
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(parent), fehler, "Verteilung zurücksetzen", JOptionPane.WARNING_MESSAGE);
    }

    /** Setzt die beim Laden gemerkten AT/PA-Werte dieses Talents im Knopf-Listener der Helden-Software auf die Basis. */
    @SuppressWarnings("unchecked")
    private static boolean startwerteSenken(Nahkampf n, Daten d) throws IllegalAccessException {
        if (d.atBasis == null || d.paBasis == null) return false;
        java.awt.event.ActionListener l = null;
        for (java.awt.event.ActionListener a : n.atMinus.getActionListeners())
            if (a.getClass().getName().startsWith("helden.")) { l = a; break; }
        if (l == null) return false;
        java.lang.reflect.Field atFeld = null, paFeld = null;
        List<java.lang.reflect.Field> maps = new ArrayList<>();
        for (java.lang.reflect.Field f : l.getClass().getDeclaredFields())
            if (Map.class.isAssignableFrom(f.getType())) { f.setAccessible(true); maps.add(f); }
        // in der Helden-Software 5.6: „Ò200000“ = AT, „Ô400000“ = PA
        for (java.lang.reflect.Field f : maps) {
            if (f.getName().startsWith("\u00d2")) atFeld = f;
            if (f.getName().startsWith("\u00d4")) paFeld = f;
        }
        if (atFeld == null || paFeld == null) return false;
        boolean gesetzt = false;
        for (java.lang.reflect.Field f : new java.lang.reflect.Field[]{atFeld, paFeld}) {
            Object o = f.get(l);
            if (!(o instanceof Map)) return false;
            Map<Object, Integer> m = (Map<Object, Integer>) o;
            for (Object k : new ArrayList<>(m.keySet())) {
                if (!n.name.equals(String.valueOf(k).trim())) continue;
                m.put(k, f == atFeld ? d.atBasis : d.paBasis);
                gesetzt = true;
            }
        }
        return gesetzt;
    }

    static JLabel wert(String text, boolean gross, String tip) {
        // Breite immer für drei Ziffern in der aktuellen Schrift – auch nach einem Design- oder Schriftwechsel
        JLabel l = new JLabel(text, SwingConstants.RIGHT) {
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                FontMetrics fm = getFontMetrics(getFont());
                Insets i = getInsets();
                return new Dimension(Math.max(d.width, fm.stringWidth("888") + i.left + i.right + 4), d.height);
            }
            @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        };
        // etwas Luft rechts, damit Ziffern mit Überhang (z. B. Segoe UI) nicht angeschnitten wirken
        l.setBorder(new EmptyBorder(0, 2, 0, 4));
        if (gross) l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() + 2f));
        TooltipHook.infoTip(l, tip);
        InventarLayout.eigen(l);
        return l;
    }

    /** Eigene kleine „+“/„−“-Knöpfe, die die Knöpfe der Helden-Software auslösen. */
    private static JComponent knoepfe(AbstractButton plusOrig, AbstractButton minusOrig, String plusGesperrt, String was) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        p.setOpaque(false);
        InventarLayout.eigen(p);
        JButton plus = knopf(Symbol.PLUS, "Einen freien TaP auf " + was + " verteilen");
        JButton minus = knopf(Symbol.MINUS, "Einen in dieser Sitzung auf " + was + " verteilten TaP zurücknehmen");
        if (plusOrig == null || plusGesperrt != null) {
            plus.setEnabled(false);
            if (plusGesperrt != null) TooltipHook.infoTip(plus, plusGesperrt);
        } else plus.addActionListener(e -> plusOrig.doClick());
        if (minusOrig == null || !minusOrig.isEnabled()) {
            minus.setEnabled(false);
            TooltipHook.infoTip(minus, "Zurücknehmen geht nur für Punkte, die seit dem Laden des Helden auf " + was + " verteilt wurden.");
        } else minus.addActionListener(e -> minusOrig.doClick());
        p.add(plus);
        p.add(minus);
        return p;
    }

    private enum Symbol { PLUS, MINUS, ZURUECK }

    /**
     * Gezeichnetes Symbol statt Schriftzeichen: „+“ und „−“ sitzen in jeder Schrift (z. B. Segoe UI unter Windows)
     * genau in der Mitte; „Zurücksetzen“ nutzt dasselbe Symbol wie „Rückgängig machen“ im Verlauf.
     */
    private static final class SymbolIcon implements Icon {
        final Symbol art;
        final int groesse;
        SymbolIcon(Symbol art, int groesse) { this.art = art; this.groesse = groesse; }
        public int getIconWidth() { return groesse; }
        public int getIconHeight() { return groesse; }
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            if (art == Symbol.ZURUECK) {
                ModernIcons.icon("refresh", groesse, groesse).paintIcon(c, g0, x, y);
                return;
            }
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color fg = c != null && !c.isEnabled() ? UIManager.getColor("Button.disabledText") : UIManager.getColor("Button.foreground");
            g.setColor(fg != null ? fg : Theme.muted());
            float sw = Math.max(1.4f, groesse / 7f);
            g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float m = groesse / 2f, r = groesse * 0.32f;
            g.draw(new java.awt.geom.Line2D.Float(x + m - r, y + m, x + m + r, y + m));
            if (art == Symbol.PLUS) g.draw(new java.awt.geom.Line2D.Float(x + m, y + m - r, x + m, y + m + r));
            g.dispose();
        }
    }

    private static JButton knopf(Symbol art, String tip) {
        JButton b = klein("", tip);
        FontMetrics fm = b.getFontMetrics(b.getFont());
        int s = fm.getHeight() + 6;
        b.setIcon(new SymbolIcon(art, art == Symbol.ZURUECK ? Math.round(s * 0.62f) : Math.round(s * 0.5f)));
        return b;
    }

    private static JButton klein(String text, String tip) {
        // quadratisch und nach der aktuellen Schrift bemessen
        JButton b = new JButton(text) {
            @Override public Dimension getPreferredSize() {
                FontMetrics fm = getFontMetrics(getFont());
                int s = fm.getHeight() + 6;
                return new Dimension(s, s);
            }
            @Override public Dimension getMinimumSize() { return getPreferredSize(); }
            @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        };
        TooltipHook.infoTip(b, tip);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setVerticalAlignment(SwingConstants.CENTER);
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setFocusable(false);
        InventarLayout.eigen(b);
        return b;
    }

    /** Warum „+“ nicht geht – oder null, wenn es geht. */
    static String plusGrund(Integer frei, Integer eigen, Integer anders, boolean stimmig, String was, String gegen) {
        if (frei != null && frei <= 0)
            return "Keine freien TaP. Neue Punkte entstehen durch Steigern des Talents im Reiter „Talente“.";
        if (stimmig && eigen + 1 - anders > MAX_DIFFERENZ)
            return was + "-Anteil (" + eigen + ") darf höchstens " + MAX_DIFFERENZ + " über dem " + gegen + "-Anteil ("
                    + anders + ") liegen – den freien Punkt auf " + gegen + " verteilen.";
        return null;
    }

    static String hinweis(Integer frei, Integer atA, Integer paA, boolean stimmig) {
        if (frei != null && frei > 0) return frei + " TaP frei";
        if (stimmig && Math.abs(atA - paA) >= MAX_DIFFERENZ) return "AT/PA-Grenze";
        return "";
    }

    private static String rechenweg(String name, Integer at, Integer pa, Integer atA, Integer paA, Integer frei, Integer taw,
                                    Daten d, boolean stimmig) {
        StringBuilder sb = new StringBuilder("<html><div style='width:320px'><b>").append(name).append("</b><br>");
        if (stimmig) {
            sb.append("AT ").append(at).append(" = AT-Basis ").append(d.atBasis).append(" + ").append(atA).append(" TaP<br>");
            sb.append("PA ").append(pa).append(" = PA-Basis ").append(d.paBasis).append(" + ").append(paA).append(" TaP<br>");
            sb.append("TaW ").append(taw).append(" = ").append(atA).append(" (AT) + ").append(paA).append(" (PA)");
            if (frei > 0) sb.append(" + ").append(frei).append(" frei");
            sb.append("<br><br><span style='color:#8a8a8a'>Unterschied der Anteile: ").append(Math.abs(atA - paA))
                    .append(" (höchstens ").append(MAX_DIFFERENZ).append(")</span>");
        } else {
            sb.append("AT ").append(at == null ? "–" : at).append(" · PA ").append(pa == null ? "–" : pa);
            if (taw != null) sb.append(" · TaW ").append(taw);
            sb.append("<br><span style='color:#8a8a8a'>Die Werte enthalten weitere Modifikatoren der Helden-Software; "
                    + "maßgeblich sind die angezeigten Werte.</span>");
        }
        return sb.append("</div></html>").toString();
    }

    /** Verteilung als Balken: AT-Anteil (Akzent), PA-Anteil (gedämpft), freie TaP (Rahmen). */
    private static final class Balken extends JComponent {
        final int at, pa, frei;

        Balken(int at, int pa, int frei) {
            this.at = at; this.pa = pa; this.frei = frei;
            setPreferredSize(new Dimension(80, 16));
            setMinimumSize(new Dimension(50, 16));
            putClientProperty("modernhelden.own", true);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int summe = at + pa + frei;
            int w = getWidth(), h = 10, y = (getHeight() - h) / 2;
            // ein durchgehend gerundeter Balken; die Abschnitte darin enden gerade
            java.awt.geom.RoundRectangle2D form = new java.awt.geom.RoundRectangle2D.Float(0, y, w, h, h, h);
            g2.setColor(Theme.trenner());
            g2.fill(form);
            if (summe > 0) {
                int wa = Math.round(w * at / (float) summe), wp = Math.round(w * pa / (float) summe);
                // Abschnitte als geglättete Flächen (Schnitt mit dem gerundeten Balken)
                g2.setColor(Theme.accent());
                if (wa > 0) g2.fill(teil(form, 0, wa));
                Color m = Theme.muted();
                g2.setColor(new Color(m.getRed(), m.getGreen(), m.getBlue(), 200));
                if (wp > 0) g2.fill(teil(form, wa, wa + wp));
                if (frei > 0) {
                    // freie TaP: Rahmen um den Rest des Balkens
                    g2.setColor(Theme.accent());
                    g2.setStroke(new BasicStroke(1f));
                    java.awt.geom.RoundRectangle2D innen = new java.awt.geom.RoundRectangle2D.Float(0.5f, y + 0.5f, w - 1, h - 1, h - 1, h - 1);
                    g2.draw(teil(innen, wa + wp, w));
                }
            }
            g2.dispose();
        }
    }

    /** Ausschnitt [von, bis) aus einer Form als Fläche. */
    private static Shape teil(Shape form, float von, float bis) {
        java.awt.geom.Area a = new java.awt.geom.Area(form);
        java.awt.geom.Rectangle2D r = form.getBounds2D();
        a.intersect(new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Float(von, (float) r.getY() - 1, bis - von, (float) r.getHeight() + 2)));
        return a;
    }

    // ------------------------------------------------------------------ Fernkampf

    private static JComponent fernkampf(Daten d, HeroData h) {
        JPanel k = karte("Fernkampf");
        JPanel t = tabelle();
        t.setOpaque(false);
        InventarLayout.eigen(t);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 4, 12);
        c.anchor = GridBagConstraints.WEST;
        String[] kopf = {"Talent", "TaW", "FK", "Rechenweg"};
        for (int i = 0; i < kopf.length; i++) {
            c.gridx = i;
            c.weightx = i == 3 ? 1 : 0;
            c.anchor = i == 1 || i == 2 ? GridBagConstraints.EAST : GridBagConstraints.WEST;
            t.add(kopfzelle(kopf[i], c.anchor == GridBagConstraints.EAST), c);
        }
        c.anchor = GridBagConstraints.WEST;
        for (Fernkampf f : d.fern) {
            c.gridy++;
            c.insets = new Insets(3, 0, 3, 12);
            Integer fk = zahl(f.wert.getText());
            Integer taw = null;
            if (h != null) {
                HeroData.Talent ht = h.talent(f.name);
                if (ht != null) taw = ht.wert;
            }
            if (taw == null && fk != null && d.fkBasis != null) taw = fk - d.fkBasis;
            String weg = fk == null || d.fkBasis == null || taw == null ? ""
                    : fk == d.fkBasis + taw ? "= FK-Basis " + d.fkBasis + " + TaW " + taw
                    : "= FK-Basis " + d.fkBasis + " + TaW " + taw + " " + vorzeichen(fk - d.fkBasis - taw) + " Modifikatoren";
            String tip = "<html><b>" + f.name + "</b><br>FK " + (fk == null ? "–" : fk) + " " + weg
                    + "<br><span style='color:#8a8a8a'>Im Fernkampf zählt der ganze TaW; es wird nichts verteilt.</span></html>";
            c.gridx = 0;
            c.weightx = 0;
            JLabel name = new JLabel(f.name);
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            TooltipHook.infoTip(name, tip);
            InventarLayout.eigen(name);
            t.add(name, c);
            c.gridx = 1;
            c.anchor = GridBagConstraints.EAST;
            t.add(wert(taw == null ? "–" : String.valueOf(taw), false, tip), c);
            c.anchor = GridBagConstraints.WEST;
            c.gridx = 2;
            c.anchor = GridBagConstraints.EAST;
            t.add(wert(fk == null ? "–" : String.valueOf(fk), true, tip), c);
            c.anchor = GridBagConstraints.WEST;
            c.gridx = 3;
            c.weightx = 1;
            JLabel w = klein(weg);
            TooltipHook.infoTip(w, tip);
            InventarLayout.eigen(w);
            t.add(w, c);
        }
        k.add(t, BorderLayout.CENTER);
        return k;
    }

    private static String vorzeichen(int v) { return v >= 0 ? "+ " + v : "− " + (-v); }

    // ------------------------------------------------------------------ Regeln

    private static JComponent regeln() {
        JPanel k = karte("So wird gerechnet");
        JLabel l = new HtmlText("<html>"
                + "• <b>Nahkampf:</b> Der TaW eines Kampftalents wird auf Attacke und Parade verteilt. "
                + "AT = AT-Basis + AT-Anteil, PA = PA-Basis + PA-Anteil.<br>"
                + "• <b>Grenze:</b> AT- und PA-Anteil dürfen sich um höchstens " + MAX_DIFFERENZ + " unterscheiden.<br>"
                + "• <b>Freie TaP:</b> Nach dem Steigern im Reiter „Talente“ ist der neue Punkt zunächst frei und wird hier "
                + "mit + auf AT oder PA gelegt.<br>"
                + "• <b>Zurücknehmen:</b> − nimmt nur Punkte zurück, die seit dem Laden des Helden verteilt wurden; "
                + "\u21ba gibt alle verteilten TaP eines Talents wieder frei.<br>"
                + "• <b>Fernkampf:</b> FK = FK-Basis + TaW; hier wird nichts verteilt.<br>"
                + "• Waffen, Schilde und Rüstung verändern die Werte zusätzlich – siehe Reiter „Ausrüstung“."
                + "</html>");
        InventarLayout.eigen(l);
        k.add(l, BorderLayout.CENTER);
        return k;
    }
}
