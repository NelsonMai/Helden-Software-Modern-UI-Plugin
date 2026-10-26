package de.modernhelden;

import de.modernhelden.print.SheetData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Reiter „Ausrüstung“ in den modernen Designs: jedes der drei Ausrüstungssets als Karte mit Summen
 * (RS, BE, Gewicht), einer Figur mit dem Rüstungsschutz je Körperzone und den belegten Plätzen samt Werten
 * (AT/PA/TP, FK, Schild-PA, RS/BE). Leere Plätze werden nicht gezeigt.
 *
 * Bearbeitet wird weiter über die Stift-Knöpfe der Helden-Software (ihre Dialoge). Die Ansicht der
 * Helden-Software bleibt unsichtbar im Fenster; im Design „Original“ wird sie wieder angezeigt.
 * Werte kommen aus den Druckdaten der Helden-Software (nur lesend).
 */
final class AusruestungLayout {

    private AusruestungLayout() {}

    private static final String MARKE = "modernhelden.ausruestung";
    /** Liefert die Druckdaten (Format 3) des gewählten Helden. */
    static Supplier<Document> daten;
    /** Liefert die Heldendaten im Speicherformat (für alle angelegten Rüstungsteile, nicht nur die ersten fünf). */
    static Supplier<Document> export;

    private static final Map<JComponent, Zustand> alle = new WeakHashMap<>();

    static void pruefen(JComponent c, boolean modern) {
        Object o = c.getClientProperty(MARKE);
        Zustand z = o instanceof Zustand ? (Zustand) o : null;
        if (z == null) {
            if (!modern || !(c instanceof JPanel) || !(c.getParent() instanceof JViewport)) return;
            z = Zustand.erkennen((JPanel) c);
            if (z == null) return;
            c.putClientProperty(MARKE, z);
            alle.put(c, z);
        }
        if (modern) z.modern(); else z.original();
    }

    /** Heldendaten haben sich geändert (Plugin-Aktualisierung). */
    static void aktualisieren() {
        for (Zustand z : alle.values()) if (z.aktiv) z.spaeter();
    }

    // ------------------------------------------------------------------ Zustand

    private static final String[] BEREICHE = {"Nahkampfwaffen", "Fernkampfwaffen", "Schilde / Parierwaffen", "Rüstungsteile"};

    private static final class Zustand {
        final JPanel original;
        final JViewport viewport;
        /** je Set: Stift-Knöpfe der Helden-Software für die vier Bereiche */
        final List<AbstractButton[]> stifte = new ArrayList<>();
        final List<JTextField> felder = new ArrayList<>();
        boolean aktiv;
        /** gezeigtes Set (0..2) */
        int set;
        JPanel huelle;
        final Ansicht ansicht = new Ansicht();
        final javax.swing.Timer spaeter = new javax.swing.Timer(300, e -> neuAufbauen());

        Zustand(JPanel original, JViewport vp) {
            this.original = original;
            this.viewport = vp;
            spaeter.setRepeats(false);
        }

        static Zustand erkennen(JPanel p) {
            List<JPanel> sets = new ArrayList<>();
            for (Component k : p.getComponents()) {
                if (!(k instanceof JPanel)) continue;
                for (Component l : ((JPanel) k).getComponents())
                    if (l instanceof JLabel && String.valueOf(((JLabel) l).getText()).startsWith("Ausrüstungsset ")) { sets.add((JPanel) k); break; }
            }
            if (sets.size() != 3) return null;
            Zustand z = new Zustand(p, (JViewport) p.getParent());
            for (JPanel s : sets) {
                List<AbstractButton> b = new ArrayList<>();
                for (Component k : s.getComponents()) {
                    if (k instanceof AbstractButton && (((AbstractButton) k).getText() == null || ((AbstractButton) k).getText().isEmpty())) b.add((AbstractButton) k);
                    if (k instanceof JTextField) z.felder.add((JTextField) k);
                }
                if (b.size() < 4) return null;
                z.stifte.add(b.subList(0, 4).toArray(new AbstractButton[0]));
                // tauscht das Plugin das Symbol (Designwechsel), die eigenen Knöpfe neu zeichnen
                for (AbstractButton st : b.subList(0, 4))
                    st.addPropertyChangeListener("icon", e -> { z.ansicht.revalidate(); z.ansicht.repaint(); });
            }
            // ändert die Helden-Software einen Platz (nach ihren Dialogen), Ansicht neu aufbauen
            DocumentListener dl = new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { z.spaeter(); }
                public void removeUpdate(DocumentEvent e) { z.spaeter(); }
                public void changedUpdate(DocumentEvent e) { z.spaeter(); }
            };
            for (JTextField f : z.felder) f.getDocument().addDocumentListener(dl);
            return z;
        }

        void spaeter() { if (aktiv) spaeter.restart(); }

        void modern() {
            if (aktiv) return;
            aktiv = true;
            huelle = new JPanel(new BorderLayout());
            huelle.setOpaque(false);
            InventarLayout.eigen(huelle);
            huelle.add(ansicht, BorderLayout.CENTER);
            // Original bleibt im Fenster (für die Dialoge der Stift-Knöpfe), aber unsichtbar
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

        void neuAufbauen() {
            if (!aktiv) return;
            Document d = null, e = null;
            try { d = daten == null ? null : daten.get(); } catch (RuntimeException ignore) { }
            try { e = export == null ? null : export.get(); } catch (RuntimeException ignore) { }
            ansicht.aufbauen(d, e, this);
            viewport.revalidate();
            viewport.repaint();
        }
    }

    // ------------------------------------------------------------------ Ansicht

    /**
     * Gleich breite Spalten, solange jede mindestens {@code mindest} Pixel breit sein kann – sonst weniger je
     * Zeile (z. B. in der Startgröße des Fensters), damit nichts rechts abgeschnitten wird.
     */
    private static final class SetLayout implements LayoutManager {
        final int abstand, mindest;
        /** Raster: Spaltenzahl nur nach Breite (eine einzelne Waffe wird nicht seitenbreit) */
        final boolean raster;
        SetLayout(int abstand, int mindest) { this(abstand, mindest, false); }
        SetLayout(int abstand, int mindest, boolean raster) { this.abstand = abstand; this.mindest = mindest; this.raster = raster; }

        private int spalten(Container c, int breite) {
            int n = c.getComponentCount();
            for (int sp = raster ? Math.max(1, (breite + abstand) / (mindest + abstand)) : Math.max(n, 1); sp > 1; sp--) if (sp * mindest + (sp - 1) * abstand <= breite) return sp;
            return 1;
        }
        private int innenBreite(Container c) {
            Insets i = c.getInsets();
            int w = c.getWidth();
            if (w <= 0 && c.getParent() != null) w = c.getParent().getWidth();
            return Math.max(0, w - i.left - i.right);
        }
        public void addLayoutComponent(String name, Component comp) {}
        public void removeLayoutComponent(Component comp) {}
        public Dimension preferredLayoutSize(Container c) {
            Insets i = c.getInsets();
            int b = innenBreite(c), n = c.getComponentCount(), sp = spalten(c, b), h = 0;
            for (int z = 0; z < n; z += sp) {
                int zh = 0;
                for (int k = z; k < Math.min(n, z + sp); k++) zh = Math.max(zh, c.getComponent(k).getPreferredSize().height);
                h += zh + (z > 0 ? abstand : 0);
            }
            return new Dimension(Math.min(mindest, Math.max(b, 1)) + i.left + i.right, h + i.top + i.bottom);
        }
        public Dimension minimumLayoutSize(Container c) { return preferredLayoutSize(c); }
        public void layoutContainer(Container c) {
            Insets i = c.getInsets();
            int b = innenBreite(c), n = c.getComponentCount(), sp = spalten(c, b);
            int w = (b - (sp - 1) * abstand) / sp, y = i.top;
            for (int z = 0; z < n; z += sp) {
                int zh = 0;
                for (int k = z; k < Math.min(n, z + sp); k++) zh = Math.max(zh, c.getComponent(k).getPreferredSize().height);
                for (int k = z; k < Math.min(n, z + sp); k++)
                    c.getComponent(k).setBounds(i.left + (k - z) * (w + abstand), y, w, zh);
                y += zh + abstand;
            }
        }
    }

    /** Breite folgt dem Fenster, Höhe dem Inhalt (scrollt bei Bedarf). Zeigt ein Set: Übersicht, Rüstung, Waffen. */
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
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
        }

        void aufbauen(Document d, Document export, Zustand z) {
            removeAll();
            Map<Integer, Element> sets = sets(d);
            Seite s = new Seite(z, sets, gewichte(d), export);
            JPanel oben = new JPanel(new SetLayout(12, 470));
            oben.setOpaque(false);
            oben.setAlignmentX(0);
            oben.add(s.uebersicht());
            oben.add(s.ruestungKarte());
            add(oben);
            add(Box.createVerticalStrut(12));
            JComponent w = s.waffenKarte();
            add(w);
            markieren(this);
            revalidate();
            repaint();
        }
    }

    /** Baut die Seite eines Sets (Übersicht, Rüstungsteile, Waffen). */
    private static final class Seite {
        final Zustand z;
        final int nr;                 // 0..2
        final Element set;
        final boolean belegt;
        final Map<Integer, Element> sets;
        final Map<String, Double> gewicht;
        final List<Element> ruestung;
        final AbstractButton[] stifte;
        final Map<String, String> zonen = new LinkedHashMap<>();
        String rs = "", be = "";
        /** Sprungziele der Übersicht („Nahkampfwaffen“ → Bereich) */
        final JComponent[] ziele = new JComponent[4];

        final Document export;

        Seite(Zustand z, Map<Integer, Element> sets, Map<String, Double> gewicht, Document export) {
            this.export = export;
            this.z = z;
            this.nr = Math.max(0, Math.min(2, z.set));
            this.sets = sets;
            this.set = sets.get(nr + 1);
            this.gewicht = gewicht;
            this.stifte = z.stifte.get(nr);
            this.ruestung = ruestungen(set, export, nr);
            // „inbenutzung“ der Druckdaten heißt nur: in diesem Set ist etwas angelegt
            this.belegt = set != null && "true".equals(set.getAttribute("inbenutzung"));
            Element rz = kind(set, "ruestungzonen"), re = kind(set, "ruestungeinfach");
            if (rz != null && !text(rz, "gesamtschutz").isEmpty()) {
                for (String k : ZONEN) zonen.put(k, text(rz, k));
                rs = text(rz, "gesamtzonenschutz");
                be = text(rz, "behinderung");
            } else if (re != null) {
                rs = text(re, "gesamt");
                be = text(re, "behinderung");
            }
        }

        List<Element> eintraege(int bereich) {
            if (bereich == 3) return ruestung;
            List<Element> l = new ArrayList<>();
            for (Element w : kinder(kind(set, TAGS[bereich]))) if (!text(w, "name").isEmpty()) l.add(w);
            return l;
        }

        double unzen(List<Element> l) {
            double u = 0;
            for (Element w : l) { Double g = gewicht.get(ohneZusatz(text(w, "name"))); if (g != null) u += g; }
            return u;
        }

        String stein(double unzen) { return InventarLayout.zahl(unzen / 40.0); }

        // ---------------------------------------------------------- Übersicht (Figur, Belastung, Kampfwerte, Sprünge)

        JComponent uebersicht() {
            JPanel k = karte();
            // Kopf: Auswahl des Sets
            JComboBox<String> wahl = new JComboBox<>();
            for (int i = 0; i < 3; i++) {
                Element s = sets.get(i + 1);
                boolean b = s != null && "true".equals(s.getAttribute("inbenutzung"));
                wahl.addItem("Ausrüstungsset " + (i + 1) + (b ? "" : "  (leer)"));
            }
            wahl.setSelectedIndex(nr);
            wahl.addActionListener(e -> {
                if (wahl.getSelectedIndex() == z.set) return;
                z.set = wahl.getSelectedIndex();
                SwingUtilities.invokeLater(() -> { z.neuAufbauen(); z.viewport.setViewPosition(new Point(0, 0)); });
            });
            TooltipHook.infoTip(wahl, "Welches der drei Ausrüstungssets gezeigt wird");
            JPanel kopf = zeile();
            JPanel wahlHalter = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            wahlHalter.setOpaque(false);
            wahlHalter.add(wahl);
            kopf.add(wahlHalter, BorderLayout.WEST);
            k.add(kopf);
            k.add(Box.createVerticalStrut(10));

            // Figur links
            JPanel links = new JPanel();
            links.setLayout(new BoxLayout(links, BoxLayout.Y_AXIS));
            links.setOpaque(false);
            Figur f = new Figur(zonen, rs, 1.2f);
            f.setAlignmentX(0.5f);
            links.add(f);
            links.add(Box.createVerticalStrut(6));
            JLabel pille = new Pille("RS gesamt: " + (rs.isEmpty() ? "–" : rs));
            pille.setAlignmentX(0.5f);
            links.add(pille);

            // rechts: Werte und Sprünge
            JPanel rechts = new JPanel();
            rechts.setLayout(new BoxLayout(rechts, BoxLayout.Y_AXIS));
            rechts.setOpaque(false);
            int teile = 0;
            for (int b = 0; b < 4; b++) teile += eintraege(b).size();
            double u = 0;
            for (int b = 0; b < 4; b++) u += unzen(eintraege(b));
            rechts.add(abschnittTitel("Rüstung und Belastung"));
            rechts.add(werteZeile("aus_schild", rs.isEmpty() ? "–" : rs, "Rüstungsschutz",
                    "Gesamter Rüstungsschutz dieses Sets; bei Zonenrüstung zeigt die Figur den Schutz je Körperzone"));
            rechts.add(werteZeile("aus_behinderung", be.isEmpty() ? "–" : be, "Behinderung",
                    "Behinderung durch die Rüstung dieses Sets; je Talent wirkt sie als effektive Behinderung (eBE)"));
            rechts.add(werteZeile("aus_last", teile == 0 ? "–" : stein(u), "Gewicht (Stein)",
                    "Gewicht aller Gegenstände, die in diesem Set angelegt sind"));
            rechts.add(Box.createVerticalStrut(8));
            rechts.add(abschnittTitel("Kampfwerte"));
            String ini = text(set, "ini"), aw = text(set, "ausweichen");
            rechts.add(werteZeile("aus_ini", ini.isEmpty() ? "–" : ini, "Initiative",
                    "Initiative mit diesem Set (Basis samt Waffen- und Rüstungsmodifikatoren); im Kampf kommt 1W6 dazu"));
            rechts.add(werteZeile("aus_ausweichen", aw.isEmpty() ? "–" : aw, "Ausweichen",
                    "Ausweichen mit diesem Set, bereits um die Behinderung gemindert"));
            rechts.add(Box.createVerticalStrut(8));
            rechts.add(abschnittTitel("Ausrüstung"));
            for (int b = 0; b < 4; b++) {
                final int bereich = b;
                rechts.add(sprung(SYMBOL[b], BEREICHE[b], eintraege(b).size(), () -> {
                    JComponent ziel = ziele[bereich];
                    Component v = z.viewport.getView();
                    if (ziel == null || v == null) return;
                    // Bereich oben ins Bild holen (soweit die Seite reicht)
                    Point p = SwingUtilities.convertPoint(ziel, 0, 0, v);
                    int max = Math.max(0, v.getHeight() - z.viewport.getHeight());
                    z.viewport.setViewPosition(new Point(0, Math.max(0, Math.min(max, p.y - 8))));
                }));
                rechts.add(Box.createVerticalStrut(4));
            }

            JPanel mitte = new JPanel(new BorderLayout(16, 0));
            mitte.setOpaque(false);
            mitte.setAlignmentX(0);
            JPanel lh = new JPanel(new BorderLayout());
            lh.setOpaque(false);
            lh.add(links, BorderLayout.NORTH);
            mitte.add(lh, BorderLayout.WEST);
            mitte.add(rechts, BorderLayout.CENTER);
            k.add(mitte);
            return aussen(k);
        }

        // ---------------------------------------------------------- Rüstungsteile nach Zonen

        JComponent ruestungKarte() {
            JPanel k = karte();
            k.add(kopfMitStift("aus_ruestung", "Rüstungsteile", stifte[3], true));
            ziele[3] = k;
            k.add(Box.createVerticalStrut(8));

            // aktueller Schutz je Zone und Summen
            JPanel stand = new JPanel(new GridLayout(1, 0, 18, 0));
            stand.setOpaque(false);
            stand.setAlignmentX(0);
            if (!zonen.isEmpty()) {
                stand.add(zonenSpalte(new String[][]{{"kopf", "Kopf"}, {"brust", "Brust"}, {"ruecken", "Rücken"}, {"bauch", "Bauch"}}));
                stand.add(zonenSpalte(new String[][]{{"rechterarm", "Rechter Arm"}, {"linkerarm", "Linker Arm"},
                        {"rechtesbein", "Rechtes Bein"}, {"linkesbein", "Linkes Bein"}}));
            }
            JPanel summe = new JPanel(new GridLayout(0, 1, 0, 3));
            summe.setOpaque(false);
            List<Element> rl = eintraege(3);
            summe.add(paar("Gesamt-RS", rs.isEmpty() ? "–" : rs, true));
            summe.add(paar("Behinderung", be.isEmpty() ? "–" : be, true));
            summe.add(paar("Gewicht", rl.isEmpty() ? "–" : stein(unzen(rl)) + " Stein", true));
            stand.add(summe);
            JLabel ueber = new JLabel("AKTUELLER RÜSTUNGSSCHUTZ");
            ueber.setFont(ueber.getFont().deriveFont(Font.BOLD, ueber.getFont().getSize2D() - 2f));
            ueber.setForeground(Theme.muted());
            ueber.setAlignmentX(0);
            ueber.setBorder(new EmptyBorder(0, 0, 4, 0));
            k.add(ueber);
            JPanel standHuelle = new JPanel(new BorderLayout());
            standHuelle.setOpaque(true);
            standHuelle.setBackground(Theme.kachel());
            standHuelle.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(6, 10, 6, 10)));
            standHuelle.setAlignmentX(0);
            standHuelle.add(stand, BorderLayout.CENTER);
            standHuelle.setMaximumSize(new Dimension(Integer.MAX_VALUE, standHuelle.getPreferredSize().height));
            k.add(standHuelle);
            k.add(Box.createVerticalStrut(10));

            // Rüstungsteile nach Gruppen
            String[] gruppen = {"Kopfschutz", "Oberkörper", "Armschutz", "Beinschutz", "Weitere Rüstungsteile"};
            String[] symbol = {"aus_helm", "aus_ruestung", "aus_arm", "aus_bein", "aus_schild"};
            List<List<Element>> je = new ArrayList<>();
            for (int i = 0; i < 5; i++) je.add(new ArrayList<>());
            for (Element r : rl) je.get(gruppe(r)).add(r);
            Map<String, Integer> gleich = new HashMap<>();
            int n = 0;
            for (int g = 0; g < 5; g++) {
                if (je.get(g).isEmpty()) continue;
                k.add(gruppenTitel(gruppen[g]));
                for (Element r : je.get(g)) {
                    n++;
                    String name = text(r, "name");
                    int nummer;
                    try { nummer = Integer.parseInt(text(r, "nummer")); } catch (NumberFormatException e) { nummer = n; }
                    int vorkommen = gleich.merge(name, 1, Integer::sum) - 1;    // für gleichnamige Rüstungsteile
                    List<String> w = new ArrayList<>();
                    w.add(werte(3, r));
                    Double gw = gewicht.get(name);
                    if (gw != null) w.add(stein(gw) + " Stein");
                    k.add(listenEintrag(symbol[g], name, String.join("  ·  ", w), stifte[3],
                            new AusruestungDialoge.Ablegen(true, nummer, name, vorkommen)));
                }
            }
            if (n == 0) k.add(anlegen("Rüstungsteil", stifte[3]));
            k.add(Box.createVerticalGlue());
            return aussen(k);
        }

        /** Gruppe eines Rüstungsteils: die Zone mit dem höchsten Schutz (Kopf, Oberkörper, Arme, Beine). */
        private int gruppe(Element r) {
            int[] max = new int[4];
            boolean irgend = false;
            for (int i = 0; i < ZONEN.length; i++) {
                int v;
                try { v = Integer.parseInt(text(r, ZONEN[i])); } catch (NumberFormatException e) { continue; }
                int g = i == 0 ? 0 : i <= 3 ? 1 : i <= 5 ? 2 : 3;
                if (v > 0) irgend = true;
                max[g] = Math.max(max[g], v);
            }
            if (!irgend) {
                // ohne Schutzwert (z. B. RS 0 überall): nach dem Namen
                String n = text(r, "name").toLowerCase(Locale.GERMAN);
                if (n.matches(".*(helm|kappe|haube|kapuze|schaller|hut|maske|kopf).*")) return 0;
                if (n.matches(".*(handschuh|armschiene|arm|ärmel|unterarm).*")) return 2;
                if (n.matches(".*(stiefel|bein|hose|schuh).*")) return 3;
                if (n.matches(".*(hemd|wams|jacke|mantel|weste|brust|harnisch|panzer|kürass|gambeson|rock|unterzeug|robe).*")) return 1;
                return 4;
            }
            int beste = 0;
            for (int g = 1; g < 4; g++) if (max[g] > max[beste]) beste = g;
            return beste;
        }

        private JComponent zonenSpalte(String[][] z) {
            JPanel p = new JPanel(new GridLayout(0, 1, 0, 3));
            p.setOpaque(false);
            for (String[] e : z) {
                String v = zonen.get(e[0]);
                JPanel zeile = new JPanel(new BorderLayout(6, 0));
                zeile.setOpaque(false);
                JLabel l = new JLabel(e[1], new Punkt(Figur.farbe(v)), SwingConstants.LEFT);
                l.setIconTextGap(6);
                l.setBorder(new EmptyBorder(0, 0, 0, 4));
                JLabel w = new JLabel(v == null || v.isEmpty() ? "–" : v);
                w.setFont(w.getFont().deriveFont(Font.BOLD));
                w.setBorder(new EmptyBorder(0, 2, 0, 4));
                zeile.add(l, BorderLayout.WEST);
                zeile.add(w, BorderLayout.EAST);
                p.add(zeile);
            }
            return p;
        }

        // ---------------------------------------------------------- Waffen

        JComponent waffenKarte() {
            JPanel k = karte();
            k.setAlignmentX(0);
            String[] leer = {"Nahkampfwaffe", "Fernkampfwaffe", "Schild oder Parierwaffe"};
            for (int b = 0; b < 3; b++) {
                if (b > 0) k.add(Box.createVerticalStrut(14));
                JComponent kopf = kopfMitStift(SYMBOL[b], BEREICHE[b], stifte[b], b == 0);
                ziele[b] = kopf;
                k.add(kopf);
                k.add(Box.createVerticalStrut(6));
                List<Element> l = eintraege(b);
                if (l.isEmpty()) { k.add(anlegen(leer[b], stifte[b])); continue; }
                JPanel raster = new JPanel(new SetLayout(10, 380, true));
                raster.setOpaque(false);
                raster.setAlignmentX(0);
                int n = 0;
                for (Element w : l) {
                    n++;
                    String name = text(w, "name");
                    int nummer;
                    try { nummer = Integer.parseInt(text(w, "nummer")); } catch (NumberFormatException e) { nummer = n; }
                    raster.add(waffe(b, w, name, new AusruestungDialoge.Ablegen(false, nummer, name, 0)));
                }
                k.add(raster);
            }
            JPanel aussen = new JPanel(new BorderLayout());
            aussen.setOpaque(false);
            aussen.setAlignmentX(0);
            aussen.add(k, BorderLayout.NORTH);
            aussen.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            return aussen;
        }

        /** Eine Waffe: Symbol, Name, Werte in Kästchen, weitere Angaben und „✕“ zum Ablegen. */
        private JComponent waffe(int art, Element w, String name, AusruestungDialoge.Ablegen auftrag) {
            List<String[]> boxen = new ArrayList<>();
            List<String> info = new ArrayList<>();
            switch (art) {
                case 0:
                    box(boxen, text(w, "at"), "AT"); box(boxen, text(w, "pa"), "PA"); box(boxen, text(w, "tp"), "TP");
                    box(boxen, text(w, "ini"), "INI"); box(boxen, text(w, "dk"), "DK");
                    info(info, "Talent", text(w, "waffentalent"));
                    info(info, "TP/KK", text(w, "tpkk").replace(" ", ""));
                    info(info, "WM", text(w, "wm").replace(" ", ""));
                    info(info, "BF", text(w, "bfakt"));
                    break;
                case 1:
                    box(boxen, text(w, "at"), "FK"); box(boxen, text(w, "tp"), "TP");
                    box(boxen, text(w, "reichweite").replace(" ", ""), "RW");
                    info(info, "Talent", text(w, "kampftalent"));
                    info(info, "TP je Entfernung", text(w, "tpmod").replace(" ", ""));
                    String lz = text(w, "ladezeit");
                    if (lz.matches("\\d+") && Integer.parseInt(lz) > 0) info(info, "Ladezeit", lz);
                    break;
                default:
                    box(boxen, text(w, "pa"), "PA"); box(boxen, text(w, "mod").replace(" ", ""), "WM AT/PA");
                    box(boxen, text(w, "ini"), "INI"); box(boxen, text(w, "bf"), "BF");
            }
            Double g = gewicht.get(ohneZusatz(name));
            if (g != null) info(info, "Gewicht", stein(g) + " Stein");

            JPanel t = new JPanel(new BorderLayout(10, 0));
            t.setOpaque(true);
            t.setBackground(Theme.kachel());
            t.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(8, 8, 8, 8)));
            t.add(symbolKachel(SYMBOL[art], 46), BorderLayout.WEST);
            JPanel mitte = new JPanel();
            mitte.setLayout(new BoxLayout(mitte, BoxLayout.Y_AXIS));
            mitte.setOpaque(false);
            JPanel kopf = zeile();
            // „Rapier (S1)“: der Zusatz steht als Marke („mit Schild 1“) darunter
            JLabel n = new JLabel(name.replaceAll("\\s*\\(S\\d\\)", "").trim());
            n.setFont(n.getFont().deriveFont(Font.BOLD));
            n.setBorder(new EmptyBorder(0, 0, 0, 4));
            kopf.add(n, BorderLayout.CENTER);
            // rechts: „Werte …“ (Bruchfaktor/Talent – über den Dialog der Helden-Software) und „✕“
            JPanel rechtsOben = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            rechtsOben.setOpaque(false);
            JButton werteK = new JButton("Werte …");
            werteK.setMargin(new Insets(1, 8, 1, 8));
            werteK.setFont(werteK.getFont().deriveFont(werteK.getFont().getSize2D() - 1f));
            TooltipHook.infoTip(werteK, art == 1 ? "Kampftalent dieser Fernkampfwaffe wählen" : "Bruchfaktor und Kampftalent dieser Waffe bearbeiten");
            werteK.setEnabled(stifte[art].isEnabled());
            final int platz = auftrag.nummer;
            werteK.addActionListener(e -> {
                AusruestungDialoge.werteMitte = SwingUtilities.getWindowAncestor(werteK);
                AusruestungDialoge.unsichtbarAusfuehren(AusruestungDialoge.Ablegen.werte(platz, name), stifte[art]::doClick);
            });
            // Schilde/Paradewaffen: die Helden-Software gibt „Werte …“ dort nie frei – Knopf weglassen
            if (art != 2) rechtsOben.add(werteK);
            rechtsOben.add(weg(name, stifte[art], auftrag));
            kopf.add(rechtsOben, BorderLayout.EAST);
            mitte.add(kopf);
            // Hand, Schild, Jagdwaffe usw. als Plaketten
            // Hand, Schild, beidhändig, Jagdwaffe … als Schalter (ändern über den Dialog der Helden-Software)
            List<JComponent> schalter = schalter(art, w, platz, name, stifte[art]);
            if (!schalter.isEmpty()) {
                JPanel mk = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
                    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
                };
                mk.setOpaque(false);
                mk.setAlignmentX(0);
                mk.setBorder(new EmptyBorder(3, 0, 3, 0));
                for (JComponent k : schalter) { mk.add(k); mk.add(Box.createHorizontalStrut(5)); }
                mitte.add(mk);
            }
            mitte.add(Box.createVerticalStrut(4));
            JPanel reihe = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
                @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            };
            reihe.setOpaque(false);
            reihe.setAlignmentX(0);
            reihe.setBorder(new EmptyBorder(0, 2, 0, 0));   // Luft links für den Rand des ersten Kästchens
            for (String[] b : boxen) { reihe.add(new Kaestchen(b[0], b[1])); reihe.add(Box.createHorizontalStrut(6)); }
            mitte.add(reihe);
            if (!info.isEmpty()) {
                mitte.add(Box.createVerticalStrut(4));
                // Punkt bleibt am vorigen Wert (keine Zeile beginnt mit „·“)
                Umbruchtext it = new Umbruchtext(String.join("\u00a0· ", info));
                it.setFont(UIManager.getFont("Label.font").deriveFont(UIManager.getFont("Label.font").getSize2D() - 1f));
                it.setForeground(Theme.muted());
                it.setAlignmentX(0);
                mitte.add(it);
            }
            t.add(mitte, BorderLayout.CENTER);
            JPanel aussen = new JPanel(new BorderLayout());
            aussen.setOpaque(false);
            aussen.add(t, BorderLayout.NORTH);
            return aussen;
        }

        /**
         * Schalter je Platz: Nahkampf – Hand, Schild 1/2, beidhändig (Waffen 1+2 bzw. 3+4); Fernkampf – Jagdwaffe;
         * Schilde – Verwendung. Ein Klick ändert die Einstellung über den (versteckten) Dialog der Helden-Software.
         */
        private List<JComponent> schalter(int art, Element w, int platz, String name, AbstractButton stift) {
            List<JComponent> l = new ArrayList<>();
            Element a = ausruestung((art == 0 ? "nkwaffe" : art == 1 ? "fkwaffe" : "schild") + platz);
            if (art == 0) {
                boolean links = a != null && "links".equals(a.getAttribute("hand"));
                l.add(new Schalter((links ? "linke Hand" : "rechte Hand") + "  ▾", true, "Hand, in der die Waffe geführt wird", b -> menue(b,
                        new String[]{"rechte Hand", "linke Hand"}, new boolean[]{!links, links}, i -> aendern(platz, name, stift, m -> {
                            JComboBox<?> h = (JComboBox<?>) m.get("hand");
                            if (h == null || !h.isEnabled()) return false;
                            h.setSelectedItem(i == 0 ? "rechts" : "links");
                            return true;
                        }))));
                boolean s1 = name.contains("(S1)"), s2 = name.contains("(S2)");
                String st = s1 && s2 ? "mit Schild 1 + 2" : s1 ? "mit Schild 1" : s2 ? "mit Schild 2" : "ohne Schild";
                l.add(new Schalter(st + "  ▾", s1 || s2, "Schild bzw. Parierwaffe, die mit dieser Waffe geführt wird", b -> menue(b,
                        new String[]{"Ohne Schild", "Schild 1", "Schild 2"}, new boolean[]{!s1 && !s2, s1, s2}, i -> aendern(platz, name, stift, m -> {
                            // „Ohne Schild“: alle zugeordneten Schilde lösen (Auswahl „keiner Waffe zugeordnet“)
                            if (i == 0) {
                                for (int n = 1; n <= 2; n++) {
                                    if (!(n == 1 ? s1 : s2)) continue;
                                    JComponent k = m.get("keine" + n);
                                    if (!(k instanceof AbstractButton) || !k.isEnabled()) return false;
                                    ((AbstractButton) k).doClick();
                                }
                                return true;
                            }
                            boolean an = i == 1 ? s1 : s2;
                            // zuordnen: Knopf dieser Waffe; lösen: „keiner Waffe zugeordnet“
                            JComponent k = m.get(an ? "keine" + i : "s" + i);
                            if (!(k instanceof AbstractButton) || !k.isEnabled()) return false;
                            ((AbstractButton) k).doClick();
                            return true;
                        }))));
                if (platz <= 4) {
                    boolean bh = "true".equals(text(w, "bh"));
                    l.add(new Schalter("beidhändig", bh, "Beidhändiger Kampf mit Waffe " + (platz <= 2 ? "1 und 2" : "3 und 4") + " (an/aus)",
                            b -> aendern(platz, name, stift, m -> umschalten(m.get("bh"), !bh))));
                }
            } else if (art == 1) {
                Element j = ausruestung("jagtwaffe");
                boolean jagd = "true".equals(text(w, "jagdwaffe")) || j != null && String.valueOf(platz).equals(j.getAttribute("nummer"));
                l.add(new Schalter("Jagdwaffe", jagd, "Als Jagdwaffe verwenden (an/aus)",
                        b -> aendern(platz, name, stift, m -> umschalten(m.get("jagd"), !jagd))));
            } else {
                String v = a == null || a.getAttribute("verwendungsArt").isEmpty() ? "Schild" : a.getAttribute("verwendungsArt");
                l.add(new Schalter(v + "  ▾", true, "Verwendung: als Schild oder als Paradewaffe", b -> menue(b,
                        new String[]{"Schild", "Paradewaffe"}, new boolean[]{"Schild".equals(v), "Paradewaffe".equals(v)}, i -> aendern(platz, name, stift, m -> {
                            JComboBox<?> c = (JComboBox<?>) m.get("art");
                            if (c == null || !c.isEnabled()) return false;
                            c.setSelectedItem(i == 0 ? "Schild" : "Paradewaffe");
                            return true;
                        }))));
            }
            return l;
        }

        private boolean umschalten(JComponent k, boolean ziel) {
            if (!(k instanceof AbstractButton) || !k.isEnabled()) return false;
            if (((AbstractButton) k).isSelected() != ziel) ((AbstractButton) k).doClick();
            return true;
        }

        /** Änderung über den versteckten Dialog der Helden-Software, danach „OK“. */
        private void aendern(int platz, String name, AbstractButton stift, java.util.function.Predicate<Map<String, JComponent>> p) {
            if (!stift.isEnabled()) return;
            AusruestungDialoge.werteMitte = z.viewport.getTopLevelAncestor() instanceof Window ? (Window) z.viewport.getTopLevelAncestor() : null;
            AusruestungDialoge.unsichtbarAusfuehren(AusruestungDialoge.Ablegen.aendern(platz, name, p), stift::doClick);
        }

        private void menue(JComponent b, String[] texte, boolean[] an, java.util.function.IntConsumer wahl) {
            JPopupMenu pm = new JPopupMenu();
            for (int i = 0; i < texte.length; i++) {
                final int k = i;
                JCheckBoxMenuItem it = new JCheckBoxMenuItem(texte[i], an[i]);
                it.addActionListener(e -> SwingUtilities.invokeLater(() -> wahl.accept(k)));
                pm.add(it);
                if (i == 0 && "Ohne Schild".equals(texte[0])) pm.addSeparator();
            }
            pm.show(b, 0, b.getHeight());
        }

        /** Eintrag „heldenausruestung“ dieses Sets in den Heldendaten. */
        private Element ausruestung(String name) {
            if (export == null) return null;
            org.w3c.dom.NodeList nl = export.getElementsByTagName("heldenausruestung");
            for (int i = 0; i < nl.getLength(); i++) {
                Element e = (Element) nl.item(i);
                if (name.equals(e.getAttribute("name")) && String.valueOf(nr).equals(e.getAttribute("set"))) return e;
            }
            return null;
        }

        private void box(List<String[]> l, String v, String k) {
            if (v != null && !v.trim().isEmpty() && !"-".equals(v.trim())) l.add(new String[]{v.trim(), k});
        }

        private void info(List<String> l, String k, String v) {
            if (v != null && !v.trim().isEmpty() && !"-".equals(v.trim())) l.add((k + ": " + v.trim()).replace(' ', '\u00a0'));
        }

        // ---------------------------------------------------------- Bausteine

        private JPanel karte() {
            JPanel k = new JPanel();
            k.setLayout(new BoxLayout(k, BoxLayout.Y_AXIS));
            k.setBackground(Theme.card());
            k.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(10, 12, 12, 12)));
            return k;
        }

        /** Karte oben ausrichten (Zeilen gleich hoher Karten). */
        private JComponent aussen(JPanel k) {
            JPanel a = new JPanel(new BorderLayout());
            a.setOpaque(false);
            a.add(k, BorderLayout.NORTH);
            return a;
        }

        private JComponent abschnittTitel(String t) {
            JLabel l = new JLabel(t);
            l.setFont(l.getFont().deriveFont(Font.BOLD));
            l.setBorder(new EmptyBorder(0, 0, 4, 4));
            JPanel z = zeile();
            z.add(l, BorderLayout.WEST);
            z.setBorder(BorderFactory.createCompoundBorder(new javax.swing.border.MatteBorder(0, 0, 1, 0, Theme.trenner()), new EmptyBorder(0, 0, 2, 0)));
            JPanel h = zeile();
            h.add(z, BorderLayout.CENTER);
            h.setBorder(new EmptyBorder(0, 0, 4, 0));
            return h;
        }

        private JComponent gruppenTitel(String t) {
            JLabel l = new JLabel(t.toUpperCase(Locale.GERMAN));
            l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() - 2f));
            l.setForeground(Theme.muted());
            l.setBorder(new EmptyBorder(6, 2, 2, 4));
            JPanel z = zeile();
            z.add(l, BorderLayout.WEST);
            return z;
        }

        /** Symbol, Wert (fett), Bezeichnung – wie in der Übersicht von Element 1. */
        private JComponent werteZeile(String symbol, String wert, String titel, String tip) {
            JPanel z = zeile();
            z.setBorder(new EmptyBorder(2, 2, 2, 2));
            JLabel s = new JLabel(ModernIcons.icon(symbol, 18, 18));
            JLabel w = new JLabel(wert);
            w.setFont(w.getFont().deriveFont(Font.BOLD, w.getFont().getSize2D() + 3f));
            // Werte bündig: feste Breite für „888“ in dieser Schrift (+ Reserve für Windows-Schriften)
            w.setPreferredSize(new Dimension(Math.max(w.getFontMetrics(w.getFont()).stringWidth("88,8"), w.getPreferredSize().width) + 6, w.getPreferredSize().height));
            JLabel t = new JLabel(titel);
            t.setForeground(Theme.muted());
            t.setBorder(new EmptyBorder(0, 0, 0, 4));
            JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            links.setOpaque(false);
            links.add(s);
            links.add(w);
            links.add(t);
            z.add(links, BorderLayout.WEST);
            TooltipHook.infoTip(z, "<html><b>" + titel + "</b><br>" + tip + ".</html>");
            return z;
        }

        /** Eintrag der Übersicht, der zum Bereich springt. */
        private JComponent sprung(String symbol, String titel, int anzahl, Runnable ziel) {
            JButton b = new JButton();
            b.setLayout(new BorderLayout(8, 0));
            b.putClientProperty("JButton.buttonType", "borderless");
            b.setBackground(Theme.kachel());
            b.setOpaque(true);
            b.setContentAreaFilled(true);
            b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(5, 8, 5, 8)));
            JLabel l = new JLabel(titel, ModernIcons.icon(symbol, 18, 18), SwingConstants.LEFT);
            l.setIconTextGap(8);
            l.setBorder(new EmptyBorder(0, 0, 0, 4));
            b.add(l, BorderLayout.CENTER);
            JLabel r = new JLabel((anzahl > 0 ? anzahl + "   " : "") + "›");
            r.setForeground(Theme.muted());
            r.setFont(r.getFont().deriveFont(Font.BOLD));
            r.setBorder(new EmptyBorder(0, 4, 0, 2));
            b.add(r, BorderLayout.EAST);
            b.addActionListener(e -> ziel.run());
            b.setAlignmentX(0);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
            TooltipHook.infoTip(b, anzahl == 0 ? titel + ": nichts angelegt – zum Bereich springen" : "Zu „" + titel + "“ springen");
            JPanel h = zeile();
            h.add(b, BorderLayout.CENTER);
            return h;
        }

        /** Bereichskopf: Symbol, Titel und „Bearbeiten“ (Stift-Knopf der Helden-Software). */
        private JComponent kopfMitStift(String symbol, String titel, AbstractButton stift, boolean gross) {
            JPanel kopf = zeile();
            JLabel t = new JLabel(titel, ModernIcons.icon(symbol, gross ? 22 : 20, gross ? 22 : 20), SwingConstants.LEFT);
            t.setIconTextGap(8);
            t.setFont(t.getFont().deriveFont(Font.BOLD, t.getFont().getSize2D() + (gross ? 3f : 2f)));
            t.setBorder(new EmptyBorder(0, 0, 0, 4));
            // Symbol immer vom Stift-Knopf der Helden-Software holen: es wird beim Designwechsel erst nach dem Aufbau getauscht
            JButton bearbeiten = new JButton("Bearbeiten", new Icon() {
                Icon akt() { Icon i = stift.getIcon(); return i != null ? i : UIManager.getIcon("Tree.leafIcon"); }
                public void paintIcon(Component c, Graphics g, int x, int y) { Icon i = akt(); if (i != null) i.paintIcon(c, g, x, y); }
                public int getIconWidth() { Icon i = akt(); return i == null ? 0 : i.getIconWidth(); }
                public int getIconHeight() { Icon i = akt(); return i == null ? 0 : i.getIconHeight(); }
            });
            bearbeiten.putClientProperty("JButton.buttonType", "toolBarButton");
            TooltipHook.infoTip(bearbeiten, titel + " dieses Sets an- oder ablegen");
            bearbeiten.addActionListener(e -> stift.doClick());
            // die Helden-Software sperrt den Stift, wenn passende Gegenstände im Inventar fehlen – das übernehmen
            bearbeiten.setEnabled(stift.isEnabled());
            if (!stift.isEnabled() && stift.getToolTipText() != null && !stift.getToolTipText().isEmpty())
                TooltipHook.infoTip(bearbeiten, stift.getToolTipText());
            // „Bearbeiten“ direkt neben dem Titel
            JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            links.setOpaque(false);
            links.add(t);
            links.add(Box.createHorizontalStrut(8));
            links.add(bearbeiten);
            kopf.add(links, BorderLayout.WEST);
            kopf.setBorder(BorderFactory.createCompoundBorder(new javax.swing.border.MatteBorder(0, 0, 1, 0, Theme.trenner()), new EmptyBorder(0, 0, 4, 0)));
            return kopf;
        }

        private JComponent anlegen(String was, AbstractButton stift) {
            JButton plus = new JButton("+ " + was + " anlegen");
            plus.putClientProperty("JButton.buttonType", "borderless");
            plus.setForeground(Theme.accent());
            plus.setHorizontalAlignment(SwingConstants.LEFT);
            plus.addActionListener(e -> stift.doClick());
            if (!stift.isEnabled()) {
                plus.setText(stift.getToolTipText() != null && !stift.getToolTipText().isEmpty() ? stift.getToolTipText() : "– nichts angelegt –");
                plus.setEnabled(false);
            }
            JPanel z = zeile();
            z.add(plus, BorderLayout.WEST);
            return z;
        }

        /** Zeile der Rüstungsliste: Symbol, Name, Werte, „✕“. */
        private JComponent listenEintrag(String symbol, String name, String werte, AbstractButton stift, AusruestungDialoge.Ablegen auftrag) {
            JPanel z = new JPanel(new BorderLayout(10, 1)) {
                @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            };
            z.setOpaque(true);
            z.setBackground(Theme.kachel());
            z.setAlignmentX(0);
            z.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(5, 6, 5, 6)));
            z.add(symbolKachel(symbol, 34), BorderLayout.WEST);
            JLabel n = new JLabel(name);
            n.setFont(n.getFont().deriveFont(Font.BOLD));
            n.setBorder(new EmptyBorder(0, 0, 0, 4));
            // Werte brechen nur zwischen den Werten um, nicht innerhalb („RS 2“)
            String fest = werte.isEmpty() ? " " : String.join("\u00a0· ", java.util.Arrays.stream(werte.split(" · "))
                    .map(t -> t.replace(' ', ' ')).toArray(String[]::new));
            Umbruchtext w = new Umbruchtext(fest);
            w.setFont(UIManager.getFont("Label.font").deriveFont(UIManager.getFont("Label.font").getSize2D() - 1f));
            w.setForeground(Theme.muted());
            w.setBorder(new EmptyBorder(0, 0, 0, 6));
            JPanel text = new JPanel(new BorderLayout(0, 1));
            text.setOpaque(false);
            text.add(n, BorderLayout.NORTH);
            text.add(w, BorderLayout.SOUTH);
            z.add(text, BorderLayout.CENTER);
            JPanel r = new JPanel(new GridBagLayout());     // senkrecht mittig
            r.setOpaque(false);
            r.add(weg(name, stift, auftrag));
            z.add(r, BorderLayout.EAST);
            // Abstand zur nächsten Zeile außerhalb der Kachel (nicht mit deren Hintergrund gefüllt)
            JPanel h = new JPanel(new BorderLayout()) {
                @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            };
            h.setOpaque(false);
            h.setAlignmentX(0);
            h.setBorder(new EmptyBorder(0, 0, 4, 0));
            h.add(z, BorderLayout.CENTER);
            return h;
        }

        /** „✕“: direkt ablegen – über den Dialog der Helden-Software, der dafür kurz geöffnet und bestätigt wird. */
        private JButton weg(String name, AbstractButton stift, AusruestungDialoge.Ablegen auftrag) {
            JButton weg = new JButton(ModernIcons.icon("minidelete", 14, 14));
            weg.setMargin(new Insets(2, 10, 2, 10));
            weg.putClientProperty("JComponent.minimumWidth", 0);
            TooltipHook.infoTip(weg, "<html>" + HinzufuegenLayout.esc(name) + " ablegen<br>(bleibt im Inventar)</html>");
            weg.setEnabled(stift.isEnabled());
            weg.addActionListener(e -> AusruestungDialoge.unsichtbarAusfuehren(auftrag, stift::doClick));
            return weg;
        }

        private JComponent paar(String k, String v, boolean fett) {
            JPanel z = new JPanel(new BorderLayout(8, 0));
            z.setOpaque(false);
            JLabel a = new JLabel(k);
            a.setForeground(Theme.muted());
            a.setBorder(new EmptyBorder(0, 0, 0, 4));
            JLabel b = new JLabel(v);
            if (fett) b.setFont(b.getFont().deriveFont(Font.BOLD));
            b.setBorder(new EmptyBorder(0, 2, 0, 4));
            z.add(a, BorderLayout.WEST);
            z.add(b, BorderLayout.EAST);
            return z;
        }
    }

    /** Name ohne die Zusätze der Druckdaten („ (S1)“ = mit Schild 1, „ (A)“). */
    static String ohneZusatz(String name) {
        return name == null ? "" : name.replaceAll("(\\s*\\((S\\d|A)\\))+$", "").trim();
    }

    private static final String[] TAGS = {"nahkampfwaffen", "fernkampfwaffen", "schilder", "ruestungen"};
    private static final String[] SYMBOL = {"aus_schwert", "aus_bogen", "aus_schild", "aus_ruestung"};
    private static final String[] ZONEN = {"kopf", "brust", "ruecken", "bauch", "linkerarm", "rechterarm", "linkesbein", "rechtesbein"};

    /** Quadratische Kachel mit Symbol (statt eines Bildes des Gegenstands). */
    static JComponent symbolKachel(String symbol, int groesse) {
        JLabel l = new JLabel(ModernIcons.icon(symbol, groesse * 3 / 5, groesse * 3 / 5), SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.card());
                g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f, 8, 8));
                g2.setColor(Theme.trenner());
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f, 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        Dimension d = new Dimension(groesse, groesse);
        l.setPreferredSize(d);
        l.setMinimumSize(d);
        l.setMaximumSize(d);
        JPanel h = new JPanel(new BorderLayout());
        h.setOpaque(false);
        h.add(l, BorderLayout.NORTH);
        return h;
    }

    /** Wert-Kästchen: oben der Wert (fett), darunter klein die Bezeichnung. */
    static final class Kaestchen extends JPanel {
        Kaestchen(String wert, String titel) {
            super(new BorderLayout(0, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(4, 8, 3, 8));
            JLabel w = new JLabel(wert, SwingConstants.CENTER);
            w.setFont(w.getFont().deriveFont(Font.BOLD, w.getFont().getSize2D() + 1f));
            w.setBorder(new EmptyBorder(0, 2, 0, 2));
            JLabel t = new JLabel(titel, SwingConstants.CENTER);
            t.setFont(t.getFont().deriveFont(t.getFont().getSize2D() - 2f));
            t.setForeground(Theme.muted());
            t.setBorder(new EmptyBorder(0, 2, 0, 2));
            add(w, BorderLayout.CENTER);
            add(t, BorderLayout.SOUTH);
        }
        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width + 4, 48), d.height);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.card());
            // um einen halben Pixel eingerückt: sonst schneidet die Bildschirmskalierung (z. B. 125 %) den Rand ab
            g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f, 6, 6));
            g2.setColor(Theme.trenner());
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f, 6, 6));
            g2.dispose();
        }
    }

    /** Schalter als Marke: an = gefüllt in Akzentfarbe, aus = nur umrandet; Klick führt die Aktion aus. */
    private static final class Schalter extends JButton {
        final boolean an;
        Schalter(String t, boolean an, String tip, java.util.function.Consumer<JComponent> aktion) {
            super(t);
            this.an = an;
            setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 2f));
            setForeground(an ? Color.WHITE : Theme.muted());
            setBorder(new EmptyBorder(2, 9, 3, 10));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            TooltipHook.infoTip(this, tip);
            addActionListener(e -> aktion.accept(this));
        }
        @Override public void updateUI() { setUI(new javax.swing.plaf.basic.BasicButtonUI()); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f, getHeight() - 1, getHeight() - 1);
            Color a = Theme.accent();
            if (an) { g2.setColor(getModel().isRollover() ? a.darker() : a); g2.fill(r); }
            else {
                if (getModel().isRollover()) { g2.setColor(new Color(a.getRed(), a.getGreen(), a.getBlue(), 30)); g2.fill(r); }
                g2.setColor(getModel().isRollover() ? a : Theme.muted());
                g2.draw(r);
            }
            g2.dispose();
            setForeground(an ? Color.WHITE : getModel().isRollover() ? a : Theme.muted());
            super.paintComponent(g);
        }
    }

    /** Runde Plakette „RS gesamt: 6“ unter der Figur. */
    private static final class Pille extends JLabel {
        Pille(String t) {
            super(t);
            setFont(getFont().deriveFont(Font.BOLD));
            setBorder(new EmptyBorder(3, 12, 4, 14));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.kachel());
            g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f, getHeight(), getHeight()));
            g2.setColor(Theme.trenner());
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f, getHeight(), getHeight()));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Farbpunkt (Farbe wie die Zone in der Figur). */
    private static final class Punkt implements Icon {
        final Color c;
        Punkt(Color c) { this.c = c; }
        public void paintIcon(Component k, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Float(x, y + 1, 10, 10, 3, 3));
            g2.setColor(Theme.trenner());
            g2.draw(new RoundRectangle2D.Float(x, y + 1, 10, 10, 3, 3));
            g2.dispose();
        }
        public int getIconWidth() { return 10; }
        public int getIconHeight() { return 12; }
    }

    /** Je Set-Nummer das Kampfset passend zum Rüstungsmodell (Zonen/einfach) der Druckdaten. */
    private static Map<Integer, Element> sets(Document d) {
        Map<Integer, Element> m = new HashMap<>();
        if (d == null) return m;
        Element root = d.getDocumentElement();
        boolean zone = !"einfach".equalsIgnoreCase(text(kind(root, "config"), "rsmodell"));
        Element ks = kind(root, "kampfsets");
        for (Element k : kinder(ks)) {
            int nr;
            try { nr = Integer.parseInt(k.getAttribute("nr")); } catch (NumberFormatException e) { continue; }
            boolean tzm = "true".equals(k.getAttribute("tzm"));
            if (!m.containsKey(nr) || tzm == zone) m.put(nr, k);
        }
        return m;
    }

    /** Gewicht je Gegenstand (Unzen, einzeln) aus dem Inventar der Druckdaten. */
    private static Map<String, Double> gewichte(Document d) {
        Map<String, Double> m = new HashMap<>();
        if (d == null) return m;
        try {
            for (SheetData.Gegenstand g : SheetData.parse(d).inventar)
                if (g.einzelUnzen > 0 && !m.containsKey(g.name)) m.put(g.name, (double) g.einzelUnzen);
        } catch (RuntimeException ignore) { }
        return m;
    }

    /**
     * Alle Rüstungsteile eines Sets. Die Druckdaten enthalten nur die ersten fünf; weitere stehen in den
     * Heldendaten (heldenausruestung „ruestung6“ …). Deren Werte kommen dann aus dem Gegenstand im Inventar.
     */
    private static List<Element> ruestungen(Element set, Document export, int setIndex) {
        List<Element> druck = kinder(kind(set, "ruestungen"));
        if (export == null) return druck;
        TreeMap<Integer, String> namen = new TreeMap<>();
        org.w3c.dom.NodeList l = export.getElementsByTagName("heldenausruestung");
        for (int i = 0; i < l.getLength(); i++) {
            Element a = (Element) l.item(i);
            String n = a.getAttribute("name");
            if (!n.startsWith("ruestung") || !String.valueOf(setIndex).equals(a.getAttribute("set"))) continue;
            try { namen.put(Integer.parseInt(n.substring(8)), a.getAttribute("ruestungsname")); } catch (NumberFormatException ignore) { }
        }
        if (namen.size() <= druck.size()) return druck;
        // Druckdaten nach Platznummer (der Name kann umbenannt sein, z. B. „Schaller“ → „Flügelhelm“)
        Map<Integer, Element> jeNummer = new HashMap<>();
        for (Element r : druck) {
            try { jeNummer.put(Integer.parseInt(text(r, "nummer")), r); } catch (NumberFormatException ignore) { }
        }
        List<Element> alle = new ArrayList<>();
        for (Map.Entry<Integer, String> e : namen.entrySet()) {
            Element aus = jeNummer.get(e.getKey());
            if (aus == null) {
                e.setValue(anzeigeName(export, e.getValue()));
                // Ersatzeintrag mit den Werten des Gegenstands (RS, BE, Zonen)
                aus = export.createElement("ruestung");
                Element n = export.createElement("name");
                n.setTextContent(e.getValue());
                aus.appendChild(n);
                Element nr = export.createElement("nummer");
                nr.setTextContent(String.valueOf(e.getKey()));
                aus.appendChild(nr);
                Object g = InventarLayout.gegenstand(e.getValue());
                Map<String, String> a = g == null ? null : VorlagenInfo.angaben(g);
                Element w = export.createElement("_werte");
                w.setTextContent(a != null && a.containsKey("Rüstung") ? a.get("Rüstung") : "");
                aus.appendChild(w);
            }
            alle.add(aus);
        }
        return alle;
    }

    /** Angezeigter Name eines Gegenstands: umbenannte Gegenstände tragen ihren neuen Namen in „modallgemein/name“. */
    private static String anzeigeName(Document export, String grundname) {
        org.w3c.dom.NodeList l = export.getElementsByTagName("gegenstand");
        for (int i = 0; i < l.getLength(); i++) {
            Element g = (Element) l.item(i);
            if (!grundname.equals(g.getAttribute("name"))) continue;
            Element mod = kind(g, "modallgemein");
            Element n = mod == null ? null : kind(mod, "name");
            if (n != null && !n.getAttribute("value").isEmpty()) return n.getAttribute("value");
        }
        return grundname;
    }

    /** Kurzwerte je Art: Nahkampf, Fernkampf, Schild, Rüstung. */
    private static String werte(int art, Element w) {
        if (kind(w, "_werte") != null) return text(w, "_werte").replace(" · ", "  ·  ");
        List<String> l = new ArrayList<>();
        switch (art) {
            case 0:
                add(l, "AT ", text(w, "at")); add(l, "PA ", text(w, "pa")); add(l, "TP ", text(w, "tp"));
                add(l, "INI ", text(w, "ini")); add(l, "DK ", text(w, "dk"));
                break;
            case 1:
                add(l, "FK ", text(w, "at")); add(l, "TP ", text(w, "tp"));
                add(l, "RW ", text(w, "reichweite").replace(" ", ""));
                break;
            case 2:
                add(l, "PA ", text(w, "pa")); add(l, "WM ", text(w, "mod").replace(" ", "")); add(l, "INI ", text(w, "ini"));
                add(l, "BF ", text(w, "bf"));
                break;
            default:
                add(l, "RS ", text(w, "gesamt")); add(l, "BE ", text(w, "behinderung"));
                String z = zonenKurz(w);
                if (!z.isEmpty()) l.add(z);
        }
        return String.join("  ·  ", l);
    }

    /** Zonen einer Rüstung mit Schutz > 0, z. B. „Kopf 3, Beine 2“. */
    private static String zonenKurz(Element w) {
        String[] z = new String[8];
        String[] tags = {"kopf", "brust", "ruecken", "bauch", "linkerarm", "rechterarm", "linkesbein", "rechtesbein"};
        for (int i = 0; i < 8; i++) z[i] = text(w, tags[i]);
        return InventarInfo.zonenKurz(z);
    }

    private static void add(List<String> l, String k, String v) {
        if (v != null && !v.trim().isEmpty() && !"-".equals(v.trim())) l.add(k + v.trim());
    }

    private static JPanel zeile() {
        JPanel z = new JPanel(new BorderLayout(8, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        z.setOpaque(false);
        z.setAlignmentX(0);
        return z;
    }

    private static JComponent trenner() {
        JSeparator s = new JSeparator();
        s.setForeground(Theme.trenner());
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        s.setAlignmentX(0);
        return s;
    }

    private static void markieren(Component c) {
        if (c instanceof JComponent) ((JComponent) c).putClientProperty("modernhelden.own", true);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) markieren(k);
    }

    /** Kleine, dezente Plakette (z. B. „leer“). */
    private static final class Plakette extends JLabel {
        Plakette(String t) {
            super(t);
            setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 1f));
            setForeground(Color.WHITE);
            setBorder(new EmptyBorder(1, 8, 2, 8));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.muted());
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ------------------------------------------------------------------ Figur mit Rüstungsschutz je Zone

    /** Stilisierte Figur (von vorn) mit dem RS je Zone; ohne Zonenmodell nur der Gesamtwert. */
    private static final class Figur extends JComponent {
        final Map<String, String> z;
        final String gesamt;
        final float k;

        Figur(Map<String, String> zonen, String gesamt, float massstab) {
            this.z = zonen;
            this.gesamt = gesamt;
            this.k = massstab;
            Dimension d = new Dimension(Math.round(150 * massstab), Math.round(198 * massstab));
            setPreferredSize(d);
            setMaximumSize(d);
            StringBuilder tip = new StringBuilder("<html><b>Rüstungsschutz je Zone</b>");
            String[][] n = {{"kopf", "Kopf"}, {"brust", "Brust"}, {"ruecken", "Rücken"}, {"bauch", "Bauch"},
                    {"rechterarm", "Rechter Arm"}, {"linkerarm", "Linker Arm"}, {"rechtesbein", "Rechtes Bein"}, {"linkesbein", "Linkes Bein"}};
            for (String[] e : n) if (zonen.containsKey(e[0])) tip.append("<br>").append(e[1]).append(": ").append(zonen.get(e[0]));
            if (zonen.isEmpty()) tip.append("<br>Gesamt: ").append(gesamt.isEmpty() ? "–" : gesamt);
            TooltipHook.infoTip(this, tip.append("</html>").toString());
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color schrift = UIManager.getColor("Label.foreground");
            if (schrift != null) setForeground(schrift);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.scale(k, k);
            int cx = Math.round(getWidth() / k / 2);
            boolean zonen = !z.isEmpty();
            // Kopf, Rumpf (Brust/Bauch), Arme, Beine – der Held sieht den Betrachter an: sein rechter Arm ist links
            teil(g, new Rectangle(cx - 15, 4, 30, 30), true, zonen ? z.get("kopf") : null);
            teil(g, new Rectangle(cx - 26, 38, 52, 40), false, zonen ? z.get("brust") : null);
            teil(g, new Rectangle(cx - 26, 80, 52, 30), false, zonen ? z.get("bauch") : null);
            teil(g, new Rectangle(cx - 52, 38, 22, 70), false, zonen ? z.get("rechterarm") : null);
            teil(g, new Rectangle(cx + 30, 38, 22, 70), false, zonen ? z.get("linkerarm") : null);
            teil(g, new Rectangle(cx - 26, 114, 24, 66), false, zonen ? z.get("rechtesbein") : null);
            teil(g, new Rectangle(cx + 2, 114, 24, 66), false, zonen ? z.get("linkesbein") : null);
            g.setFont(getFont().deriveFont(getFont().getSize2D() - 2f));
            g.setColor(Theme.muted());
            String unten = zonen ? "Rücken " + leer(z.get("ruecken")) : "RS gesamt " + leer(gesamt);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(unten, cx - fm.stringWidth(unten) / 2, 194);
            if (!zonen) {
                g.setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 6f));
                fm = g.getFontMetrics();
                String s = leer(gesamt);
                g.setColor(getForeground());
                g.drawString(s, cx - fm.stringWidth(s) / 2, 72 + fm.getAscent() / 2);
            }
            g.dispose();
        }

        private static String leer(String s) { return s == null || s.isEmpty() ? "–" : s; }

        /** Farbe einer Zone: je mehr Schutz, desto kräftiger der Akzent. */
        static Color farbe(String wert) {
            int v = 0;
            try { v = wert == null ? 0 : Integer.parseInt(wert.trim()); } catch (NumberFormatException ignore) { }
            Color a = Theme.accent();
            int alpha = wert == null ? 25 : Math.min(230, 35 + v * 30);
            return new Color(a.getRed(), a.getGreen(), a.getBlue(), alpha);
        }

        private void teil(Graphics2D g, Rectangle r, boolean rund, String wert) {
            Color f = farbe(wert);
            int alpha = f.getAlpha();
            g.setColor(f);
            Shape s = rund ? new java.awt.geom.Ellipse2D.Float(r.x, r.y, r.width, r.height)
                    : new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, 8, 8);
            g.fill(s);
            g.setColor(Theme.trenner());
            g.draw(s);
            if (wert == null) return;
            g.setFont(getFont().deriveFont(Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            String t = leer(wert);
            g.setColor(alpha > 140 ? Color.WHITE : getForeground());
            g.drawString(t, r.x + (r.width - fm.stringWidth(t)) / 2, r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    // ------------------------------------------------------------------ XML

    private static Element kind(Element e, String tag) {
        if (e == null) return null;
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element && tag.equals(n.getNodeName())) return (Element) n;
        return null;
    }

    private static List<Element> kinder(Element e) {
        List<Element> l = new ArrayList<>();
        if (e == null) return l;
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element) l.add((Element) n);
        return l;
    }

    private static String text(Element e, String tag) {
        Element k = kind(e, tag);
        return k == null ? "" : k.getTextContent().trim();
    }
}
