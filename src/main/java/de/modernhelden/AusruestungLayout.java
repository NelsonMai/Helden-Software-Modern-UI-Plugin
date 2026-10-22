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

    /** Breite folgt dem Fenster, Höhe dem Inhalt (scrollt bei Bedarf). */
    private static final class Ansicht extends JPanel implements Scrollable {
        Ansicht() {
            super(new GridLayout(1, 3, 12, 0));
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
            Map<String, Double> gewicht = gewichte(d);
            for (int i = 0; i < 3; i++)
                add(karte(i + 1, sets.get(i + 1), ruestungen(sets.get(i + 1), export, i), gewicht, z.stifte.get(i)));
            revalidate();
            repaint();
        }
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

    // ------------------------------------------------------------------ Karte je Set

    private static JComponent karte(int nr, Element set, List<Element> ruestung, Map<String, Double> gewicht, AbstractButton[] stifte) {
        JPanel k = new JPanel();
        k.setLayout(new BoxLayout(k, BoxLayout.Y_AXIS));
        // „inbenutzung“ der Druckdaten heißt nur: in diesem Set ist etwas angelegt (ein „aktives“ Set gibt es nicht)
        boolean belegt = set != null && "true".equals(set.getAttribute("inbenutzung"));
        k.setBackground(Theme.card());
        k.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(10, 12, 12, 12)));

        // Kopf: „Ausrüstungsset 1“, leere Sets mit dezenter Plakette „leer“
        JPanel kopf = zeile();
        JLabel t = InventarLayout.titel("Ausrüstungsset " + nr);
        kopf.add(t, BorderLayout.WEST);
        if (!belegt) {
            JLabel a = new Plakette("leer");
            JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
            p.setOpaque(false);
            p.add(a);
            kopf.add(p, BorderLayout.EAST);
        }
        k.add(kopf);
        k.add(Box.createVerticalStrut(8));

        // Figur + Summen
        Element rz = kind(set, "ruestungzonen"), re = kind(set, "ruestungeinfach");
        Map<String, String> zonen = new LinkedHashMap<>();
        String rs = "", be = "";
        if (rz != null && !text(rz, "gesamtschutz").isEmpty()) {
            for (String z : new String[]{"kopf", "brust", "ruecken", "bauch", "linkerarm", "rechterarm", "linkesbein", "rechtesbein"})
                zonen.put(z, text(rz, z));
            rs = text(rz, "gesamtzonenschutz");
            be = text(rz, "behinderung");
        } else if (re != null) {
            rs = text(re, "gesamt");
            be = text(re, "behinderung");
        }
        double unzen = 0;
        int teile = 0;
        for (String b : new String[]{"nahkampfwaffen", "fernkampfwaffen", "schilder", "ruestungen"}) {
            for (Element w : "ruestungen".equals(b) ? ruestung : kinder(kind(set, b))) {
                if (text(w, "name").isEmpty()) continue;
                teile++;
                Double g = gewicht.get(text(w, "name"));
                if (g != null) unzen += g;
            }
        }
        JPanel mitte = new JPanel(new BorderLayout(14, 0));
        mitte.setOpaque(false);
        mitte.setAlignmentX(0);
        mitte.add(new Figur(zonen, rs), BorderLayout.WEST);
        JPanel summen = new JPanel(new GridLayout(0, 1, 0, 2));
        summen.setOpaque(false);
        summen.add(wert("Rüstungsschutz", rs.isEmpty() ? "–" : rs));
        summen.add(wert("Behinderung", be.isEmpty() ? "–" : be));
        summen.add(wert("Gewicht", teile == 0 ? "–" : InventarLayout.zahl(unzen / 40.0) + " Stein"));
        summen.add(wert("Initiative", text(set, "ini").isEmpty() ? "–" : text(set, "ini")));
        summen.add(wert("Ausweichen", text(set, "ausweichen").isEmpty() ? "–" : text(set, "ausweichen")));
        JPanel sw = new JPanel(new BorderLayout());
        sw.setOpaque(false);
        sw.add(summen, BorderLayout.NORTH);
        mitte.add(sw, BorderLayout.CENTER);
        k.add(mitte);

        // Bereiche
        String[] tags = {"nahkampfwaffen", "fernkampfwaffen", "schilder", "ruestungen"};
        String[] leer = {"Nahkampfwaffe", "Fernkampfwaffe", "Schild oder Parierwaffe", "Rüstungsteil"};
        for (int b = 0; b < 4; b++) {
            k.add(Box.createVerticalStrut(12));
            k.add(bereich(BEREICHE[b], leer[b], b, b == 3 ? ruestung : kinder(kind(set, tags[b])), stifte[b]));
        }
        k.add(Box.createVerticalGlue());

        // Karte oben ausrichten
        JPanel aussen = new JPanel(new BorderLayout());
        aussen.setOpaque(false);
        aussen.add(k, BorderLayout.NORTH);
        markieren(aussen);
        return aussen;
    }

    private static JComponent bereich(String titel, String leer, int art, List<Element> eintraege, AbstractButton stift) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(0);
        JPanel kopf = zeile();
        JLabel t = new JLabel(titel.toUpperCase(Locale.GERMAN));
        t.setFont(t.getFont().deriveFont(Font.BOLD, t.getFont().getSize2D() - 1f));
        t.setForeground(Theme.muted());
        kopf.add(t, BorderLayout.WEST);
        // Symbol immer vom Stift-Knopf der Helden-Software holen: es wird beim Designwechsel erst nach dem Aufbau getauscht
        JButton bearbeiten = new JButton("Bearbeiten", new Icon() {
            Icon akt() { Icon i = stift.getIcon(); return i != null ? i : UIManager.getIcon("Tree.leafIcon"); }
            public void paintIcon(Component c, Graphics g, int x, int y) { Icon i = akt(); if (i != null) i.paintIcon(c, g, x, y); }
            public int getIconWidth() { Icon i = akt(); return i == null ? 0 : i.getIconWidth(); }
            public int getIconHeight() { Icon i = akt(); return i == null ? 0 : i.getIconHeight(); }
        });
        bearbeiten.putClientProperty("JButton.buttonType", "toolBarButton");
        bearbeiten.setToolTipText(titel + " dieses Sets an- oder ablegen");
        bearbeiten.addActionListener(e -> stift.doClick());
        // die Helden-Software sperrt den Stift, wenn passende Gegenstände im Inventar fehlen – das übernehmen
        bearbeiten.setEnabled(stift.isEnabled());
        if (!stift.isEnabled() && stift.getToolTipText() != null && !stift.getToolTipText().isEmpty())
            bearbeiten.setToolTipText(stift.getToolTipText());
        kopf.add(bearbeiten, BorderLayout.EAST);
        p.add(kopf);
        p.add(trenner());
        int n = 0;
        Map<String, Integer> gleich = new HashMap<>();
        for (Element w : eintraege) {
            String name = text(w, "name");
            if (name.isEmpty()) continue;
            n++;
            int nummer;
            try { nummer = Integer.parseInt(text(w, "nummer")); } catch (NumberFormatException e) { nummer = n; }
            int vorkommen = gleich.merge(name, 1, Integer::sum) - 1;    // für gleichnamige Rüstungsteile
            final AusruestungDialoge.Ablegen auftrag = new AusruestungDialoge.Ablegen(art == 3, nummer, name, vorkommen);
            p.add(eintrag(name, werte(art, w), stift, auftrag));
        }
        if (n == 0) {
            JButton plus = new JButton("+ " + leer + " anlegen");
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
            p.add(z);
        }
        return p;
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

    private static JComponent eintrag(String name, String werte, AbstractButton stift, AusruestungDialoge.Ablegen auftrag) {
        JPanel z = new JPanel(new BorderLayout(0, 1));
        z.setOpaque(false);
        z.setAlignmentX(0);
        z.setBorder(new EmptyBorder(5, 2, 5, 2));
        JLabel n = new JLabel(name);
        n.setFont(n.getFont().deriveFont(Font.BOLD));
        JLabel w = new JLabel(werte.isEmpty() ? " " : werte);
        w.setForeground(Theme.muted());
        JPanel text = new JPanel(new BorderLayout(0, 1));
        text.setOpaque(false);
        text.add(n, BorderLayout.NORTH);
        text.add(w, BorderLayout.SOUTH);
        z.add(text, BorderLayout.CENTER);
        // „✕“: direkt ablegen – über den Dialog der Helden-Software, der dafür kurz geöffnet und bestätigt wird
        // derselbe kleine Knopf mit rotem ✕ wie in der Tabelle im Reiter „Inventar / Tiere“
        JButton weg = new JButton(ModernIcons.icon("minidelete", 14, 14));
        weg.setMargin(new Insets(2, 10, 2, 10));
        weg.putClientProperty("JComponent.minimumWidth", 0);
        weg.setToolTipText("<html>" + HinzufuegenLayout.esc(name) + " ablegen<br>(bleibt im Inventar)</html>");
        weg.setEnabled(stift.isEnabled());
        weg.addActionListener(e -> {
            AusruestungDialoge.unsichtbarAusfuehren(auftrag, stift::doClick);
        });
        JPanel r = new JPanel(new GridBagLayout());     // senkrecht mittig
        r.setOpaque(false);
        r.add(weg);
        z.add(r, BorderLayout.EAST);
        z.setMaximumSize(new Dimension(Integer.MAX_VALUE, z.getPreferredSize().height));
        return z;
    }

    private static JComponent wert(String k, String v) {
        JPanel z = new JPanel(new BorderLayout(8, 0));
        z.setOpaque(false);
        JLabel a = new JLabel(k);
        a.setForeground(Theme.muted());
        JLabel b = new JLabel(v);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        z.add(a, BorderLayout.WEST);
        z.add(b, BorderLayout.EAST);
        return z;
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

        Figur(Map<String, String> zonen, String gesamt) {
            this.z = zonen;
            this.gesamt = gesamt;
            setPreferredSize(new Dimension(150, 196));
            StringBuilder tip = new StringBuilder("<html><b>Rüstungsschutz je Zone</b>");
            String[][] n = {{"kopf", "Kopf"}, {"brust", "Brust"}, {"ruecken", "Rücken"}, {"bauch", "Bauch"},
                    {"rechterarm", "Rechter Arm"}, {"linkerarm", "Linker Arm"}, {"rechtesbein", "Rechtes Bein"}, {"linkesbein", "Linkes Bein"}};
            for (String[] e : n) if (zonen.containsKey(e[0])) tip.append("<br>").append(e[1]).append(": ").append(zonen.get(e[0]));
            if (zonen.isEmpty()) tip.append("<br>Gesamt: ").append(gesamt.isEmpty() ? "–" : gesamt);
            setToolTipText(tip.append("</html>").toString());
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color schrift = UIManager.getColor("Label.foreground");
            if (schrift != null) setForeground(schrift);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int cx = getWidth() / 2;
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

        private void teil(Graphics2D g, Rectangle r, boolean rund, String wert) {
            int v = 0;
            try { v = wert == null ? 0 : Integer.parseInt(wert.trim()); } catch (NumberFormatException ignore) { }
            Color a = Theme.accent();
            // je mehr Schutz, desto kräftiger die Farbe
            int alpha = wert == null ? 25 : Math.min(230, 35 + v * 30);
            g.setColor(new Color(a.getRed(), a.getGreen(), a.getBlue(), alpha));
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
