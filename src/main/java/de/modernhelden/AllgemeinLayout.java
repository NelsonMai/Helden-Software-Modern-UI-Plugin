package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
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
 * Reiter „Allgemein“ in den modernen Designs als Steckbrief: Kopf mit Name, Rasse, Kultur und Profession,
 * Abenteuerpunkte mit Übersicht, Aussehen und Daten, Vor- und Nachteile getrennt sowie Sonderfertigkeiten –
 * jeweils mit Kurzbeschreibung und gemeinsamer Suche.
 *
 * Geändert wird weiter über die Felder und Knöpfe der Helden-Software (Name, Abenteuerpunkte, „Nachteile senken“,
 * „Hinzufügen“). Ihre Ansicht bleibt unsichtbar im Reiter; im Design „Original“ wird sie wieder angezeigt.
 */
final class AllgemeinLayout {

    private AllgemeinLayout() {}

    private static final String MARKE = "modernhelden.allgemein";
    private static final Map<JComponent, Zustand> alle = new WeakHashMap<>();

    /** Beschreibungen der Vor-/Nachteile und SF (vom Plugin gesetzt). */
    static TooltipDB db;

    static void pruefen(JComponent c, boolean modern) {
        Object o = c.getClientProperty(MARKE);
        Zustand z = o instanceof Zustand ? (Zustand) o : null;
        if (z == null) {
            if (!modern || !(c instanceof JPanel) || !istAllgemein((JPanel) c)) return;
            Container p = c.getParent();
            if (p == null || !(p.getLayout() instanceof BorderLayout)) return;
            z = new Zustand((JPanel) c);
            c.putClientProperty(MARKE, z);
            alle.put(c, z);
        }
        if (modern) z.modern(); else z.original();
    }

    static void aktualisieren() {
        for (Zustand z : alle.values()) if (z.aktiv) z.spaeter();
    }

    private static boolean istAllgemein(JPanel p) {
        if (!(p.getLayout() instanceof GridBagLayout)) return false;
        boolean name = false, ap = false;
        for (Component k : p.getComponents()) {
            if (k instanceof JTextField && "Name-Field".equals(k.getName())) name = true;
            if (k instanceof JTextField && "Abenteuerpunkte-Field".equals(k.getName())) ap = true;
        }
        return name && ap;
    }

    // ------------------------------------------------------------------ Daten aus der Ansicht der Helden-Software

    private static final class Daten {
        /** Beschriftung (ohne „:“) → Feld, in der Reihenfolge der Helden-Ansicht. */
        final Map<String, JTextField> felder = new LinkedHashMap<>();
        JTable vorNach, sf;
        AbstractButton senken, hinzufuegen;

        String text(String label) {
            JTextField f = felder.get(label);
            return f == null ? "" : String.valueOf(f.getText()).trim();
        }
        Integer zahl(String label) {
            try { return Integer.valueOf(text(label)); } catch (NumberFormatException e) { return null; }
        }
        JTextField feld(String name) {
            for (JTextField f : felder.values()) if (name.equals(f.getName())) return f;
            return null;
        }
    }

    private static Daten lesen(JPanel p) {
        Daten d = new Daten();
        GridBagLayout gb = (GridBagLayout) p.getLayout();
        Map<String, JLabel> beschriftung = new HashMap<>();
        for (Component k : p.getComponents()) {
            if (!(k instanceof JLabel)) continue;
            GridBagConstraints c = gb.getConstraints(k);
            beschriftung.put(c.gridx + "," + c.gridy, (JLabel) k);
        }
        for (Component k : p.getComponents()) {
            if (k instanceof JTextField) {
                GridBagConstraints c = gb.getConstraints(k);
                JLabel l = beschriftung.get((c.gridx - 1) + "," + c.gridy);
                String t = l == null ? k.getName() : String.valueOf(l.getText()).trim();
                if (t.endsWith(":")) t = t.substring(0, t.length() - 1).trim();
                d.felder.put(t, (JTextField) k);
            }
        }
        sammeln(p, d);
        return d;
    }

    /** Tabellen und Knöpfe im unteren Bereich (Vor-/Nachteile links, Sonderfertigkeiten rechts). */
    private static void sammeln(Container c, Daten d) {
        for (Component k : c.getComponents()) {
            if (k instanceof JTable) {
                JTable t = (JTable) k;
                // die Tabellen haben eigene Klassen; unterschieden wird über die Beschriftung des Bereichs
                String bereich = bereich(t);
                if (bereich.startsWith("Vor")) d.vorNach = t;
                else if (bereich.startsWith("Sonder")) d.sf = t;
            } else if (k instanceof AbstractButton) {
                String t = String.valueOf(((AbstractButton) k).getText());
                if (t.startsWith("Nachteile")) d.senken = (AbstractButton) k;
                if (t.startsWith("Hinzuf")) d.hinzufuegen = (AbstractButton) k;
            }
            if (k instanceof Container) sammeln((Container) k, d);
        }
    }

    private static String bereich(Component c) {
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            for (Component k : p.getComponents())
                if (k instanceof JLabel) {
                    String t = String.valueOf(((JLabel) k).getText());
                    if (t.startsWith("Vor-") || t.startsWith("Sonderfertigkeiten")) return t;
                }
        }
        return "";
    }

    private static List<String> zeilen(JTable t) {
        List<String> l = new ArrayList<>();
        if (t == null) return l;
        javax.swing.table.TableModel m = t.getModel();
        for (int i = 0; i < m.getRowCount(); i++) {
            Object o = m.getValueAt(i, 0);
            String s = o == null ? "" : String.valueOf(o).trim();
            if (!s.isEmpty()) l.add(s);
        }
        return l;
    }

    // ------------------------------------------------------------------ Zustand

    private static final class Zustand {
        final JPanel original;
        final Ansicht ansicht = new Ansicht(this);
        JScrollPane scroll;
        boolean aktiv;
        String filter = "";
        final Set<Object> angebunden = Collections.newSetFromMap(new WeakHashMap<>());
        final javax.swing.Timer spaeter = new javax.swing.Timer(120, e -> neuAufbauen());
        String letzterStand;

        Zustand(JPanel original) {
            this.original = original;
            spaeter.setRepeats(false);
            original.addContainerListener(new ContainerListener() {
                public void componentAdded(ContainerEvent e) { spaeter(); }
                public void componentRemoved(ContainerEvent e) { spaeter(); }
            });
        }

        void spaeter() { if (aktiv) spaeter.restart(); }

        void modern() {
            if (aktiv) return;
            Container p = original.getParent();
            if (p == null) return;
            aktiv = true;
            // Original bleibt im Reiter (seine Felder und Knöpfe werden ausgelöst), aber unsichtbar
            original.setVisible(false);
            scroll = new JScrollPane(ansicht);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getVerticalScrollBar().setUnitIncrement(20);
            InventarLayout.eigen(scroll);
            p.add(scroll, BorderLayout.CENTER);
            SwingUtilities.updateComponentTreeUI(scroll);
            ansicht.setBackground(UIManager.getColor("Panel.background"));
            scroll.getViewport().setBackground(UIManager.getColor("Panel.background"));
            p.revalidate();
            p.repaint();
            letzterStand = null;
            neuAufbauen();
        }

        void original() {
            if (!aktiv) return;
            aktiv = false;
            spaeter.stop();
            Container p = scroll.getParent();
            if (p != null) {
                p.remove(scroll);
                p.add(original, BorderLayout.CENTER);
                p.revalidate();
                p.repaint();
            }
            original.setVisible(true);
        }

        void anbinden(Daten d) {
            DocumentListener dl = new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { spaeter(); }
                public void removeUpdate(DocumentEvent e) { spaeter(); }
                public void changedUpdate(DocumentEvent e) { spaeter(); }
            };
            for (JTextField f : d.felder.values()) if (angebunden.add(f)) f.getDocument().addDocumentListener(dl);
            for (JTable t : new JTable[]{d.vorNach, d.sf}) {
                if (t == null) continue;
                if (angebunden.add(t)) t.addPropertyChangeListener("model", e -> spaeter());
                if (angebunden.add(t.getModel())) t.getModel().addTableModelListener(e -> spaeter());
            }
            for (AbstractButton b : new AbstractButton[]{d.senken, d.hinzufuegen})
                if (b != null && angebunden.add(b)) b.addPropertyChangeListener("enabled", e -> spaeter());
        }

        void neuAufbauen() {
            if (!aktiv) return;
            Daten d = lesen(original);
            anbinden(d);
            HeroData h = InventarLayout.held();
            String stand = stand(d, h);
            if (stand.equals(letzterStand) && ansicht.getComponentCount() > 0) return;
            letzterStand = stand;
            if (DebugBaum.ordner != null) System.err.println("[Allgemein] Ansicht neu aufgebaut");
            ansicht.aufbauen(d, h);
        }
    }

    private static String stand(Daten d, HeroData h) {
        StringBuilder sb = new StringBuilder();
        sb.append(h == null ? "" : h.name + h.ap + "/" + h.freieAp + h.nachteile.size() + "/" + h.eig.get("SO") + h.gilde + "/" + h.titel + "/" + h.stand + "/" + h.tarnidentitaet).append('|').append(Theme.current());
        for (Map.Entry<String, JTextField> e : d.felder.entrySet()) sb.append('|').append(e.getKey()).append('=').append(e.getValue().getText());
        sb.append('|').append(zeilen(d.vorNach)).append('|').append(zeilen(d.sf));
        for (AbstractButton b : new AbstractButton[]{d.senken, d.hinzufuegen}) sb.append(b != null && b.isEnabled() ? '1' : '0');
        return sb.toString();
    }

    // ------------------------------------------------------------------ Ansicht

    /** Oben der Steckbrief, darunter drei Spalten (Vorteile, Nachteile, Sonderfertigkeiten), die die Höhe füllen. */
    private static final class Ansicht extends JPanel implements Scrollable {
        final Zustand z;
        final Liste vorteile = new Liste("Vorteile", "Keine Vorteile");
        final Liste nachteile = new Liste("Nachteile", "Keine Nachteile");
        final Liste sonder = new Liste("Sonderfertigkeiten", "Keine Sonderfertigkeiten");

        Ansicht(Zustand z) {
            super(new BorderLayout(0, 8));
            this.z = z;
            setOpaque(true);
            setBorder(new EmptyBorder(8, 8, 8, 8));
            putClientProperty("modernhelden.own", true);
        }

        // füllt das Fenster; erst wenn es zu klein ist, rollt die ganze Ansicht (die Listen behalten Mindesthöhe)
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 200; }
        public boolean getScrollableTracksViewportWidth() {
            return !(getParent() instanceof JViewport) || getParent().getWidth() >= getMinimumSize().width;
        }
        public boolean getScrollableTracksViewportHeight() {
            return !(getParent() instanceof JViewport) || getParent().getHeight() >= getPreferredSize().height;
        }

        void aufbauen(Daten d, HeroData h) {
            removeAll();
            JPanel oben = new JPanel(new BorderLayout(0, 8));
            oben.setOpaque(false);
            InventarLayout.eigen(oben);
            oben.add(steckbrief(d, h), BorderLayout.CENTER);
            oben.add(suche(), BorderLayout.SOUTH);
            add(oben, BorderLayout.NORTH);

            vorteile.eintraege.clear();
            nachteile.eintraege.clear();
            for (String s : zeilen(d.vorNach)) (istNachteil(s, h) ? nachteile : vorteile).eintraege.add(s);
            sonder.eintraege.clear();
            sonder.eintraege.addAll(zeilen(d.sf));
            JPanel spalten = new JPanel(new GridLayout(1, 3, 8, 0));
            spalten.setOpaque(false);
            InventarLayout.eigen(spalten);
            spalten.add(vorteile.karte(null));
            spalten.add(nachteile.karte(knopf(d.senken, "Nachteile senken", "Nachteile mit AP senken (Dialog der Helden-Software)")));
            spalten.add(sonder.karte(knopf(d.hinzufuegen, "Hinzufügen", "Sonderfertigkeit erlernen (Dialog der Helden-Software)")));
            add(spalten, BorderLayout.CENTER);
            filtern();
            revalidate();
            repaint();
        }

        JComponent suche() {
            JPanel p = new JPanel(new BorderLayout());
            p.setOpaque(false);
            InventarLayout.eigen(p);
            JTextField f = new JTextField(z.filter, 24);
            f.putClientProperty("JTextField.placeholderText", "Vor-/Nachteile und Sonderfertigkeiten filtern …");
            f.putClientProperty("JTextField.showClearButton", true);
            f.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
            InventarLayout.eigen(f);
            f.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { neu(); }
                public void removeUpdate(DocumentEvent e) { neu(); }
                public void changedUpdate(DocumentEvent e) { neu(); }
                void neu() { z.filter = f.getText(); filtern(); }
            });
            p.add(f, BorderLayout.WEST);
            return p;
        }

        /** Nur die Listen neu füllen – das Suchfeld behält den Fokus. */
        void filtern() {
            String q = z.filter == null ? "" : z.filter.trim().toLowerCase(Locale.GERMAN);
            for (Liste l : new Liste[]{vorteile, nachteile, sonder}) l.fuellen(q);
        }
    }

    /** Eine Spalte: Kopf mit Titel, Anzahl und Knopf; darunter die Einträge mit eigenem Rollbalken. */
    private static final class Liste {
        final String titel, leer;
        final List<String> eintraege = new ArrayList<>();
        /** Breite folgt der Spalte (Namen brechen um statt abgeschnitten zu werden), Höhe dem Inhalt. */
        final JPanel inhalt = new ListenInhalt();
        final JLabel anzahl = new JLabel();

        Liste(String titel, String leer) {
            this.titel = titel;
            this.leer = leer;
            inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
            InventarLayout.eigen(inhalt);
            InventarLayout.eigen(anzahl);
        }

        JComponent karte(JComponent knopf) {
            JPanel k = new JPanel(new BorderLayout(0, 4));
            k.setBackground(Theme.card());
            k.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(6, 8, 6, 8)));
            InventarLayout.eigen(k);
            JPanel kopf = new JPanel(new BorderLayout(6, 0));
            kopf.setOpaque(false);
            InventarLayout.eigen(kopf);
            JLabel t = new JLabel(titel);
            t.setFont(t.getFont().deriveFont(Font.BOLD, t.getFont().getSize2D() + 1f));
            t.setBorder(new EmptyBorder(0, 0, 0, 4));
            InventarLayout.eigen(t);
            anzahl.setForeground(Theme.muted());
            anzahl.setBorder(new EmptyBorder(0, 0, 0, 4));
            JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            links.setOpaque(false);
            InventarLayout.eigen(links);
            links.add(t);
            links.add(anzahl);
            kopf.add(links, BorderLayout.CENTER);
            k.add(kopf, BorderLayout.NORTH);
            // Knopf unten über die ganze Breite – wie in der Ansicht der Helden-Software
            if (knopf != null) k.add(knopf, BorderLayout.SOUTH);
            inhalt.setBackground(Theme.card());
            inhalt.setOpaque(true);
            JScrollPane sp = new JScrollPane(inhalt, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.trenner()));
            sp.getVerticalScrollBar().setUnitIncrement(16);
            sp.getViewport().setBackground(Theme.card());
            InventarLayout.eigen(sp);
            // mindestens ein paar Zeilen hoch, auch bei sehr kleinem Fenster
            sp.setMinimumSize(new Dimension(80, 120));
            sp.setPreferredSize(new Dimension(80, 120));
            k.add(sp, BorderLayout.CENTER);
            return k;
        }

        void fuellen(String q) {
            inhalt.removeAll();
            int n = 0;
            for (String s : eintraege) {
                String b = beschreibung(s);
                if (!q.isEmpty() && !s.toLowerCase(Locale.GERMAN).contains(q) && !b.toLowerCase(Locale.GERMAN).contains(q)) continue;
                inhalt.add(eintrag(s, b, n++));
            }
            if (n == 0) {
                JLabel x = KampfwerteLayout.klein(eintraege.isEmpty() ? leer : "Keine Treffer");
                x.setBorder(new EmptyBorder(4, 6, 4, 6));
                InventarLayout.eigen(x);
                inhalt.add(x);
            }
            inhalt.add(Box.createVerticalGlue());
            anzahl.setText(q.isEmpty() ? String.valueOf(eintraege.size()) : n + " von " + eintraege.size());
            inhalt.revalidate();
            inhalt.repaint();
        }
    }

    private static boolean istNachteil(String zeile, HeroData h) {
        if (h != null)
            for (HeroData.Item it : h.nachteile) if (!it.name.isEmpty() && zeile.startsWith(it.name)) return true;
        TooltipDB.Entry e = db == null ? null : db.lookupEntry(zeile);
        return e != null && "Nachteil".equals(e.gruppe);
    }

    /** Erster Satz der Beschreibung (eigene Notiz hat Vorrang). */
    static String beschreibung(String zeile) {
        if (db == null) return "";
        TooltipDB.Match m = db.find(zeile);
        if (m == null) return "";
        String t = db.getUserText(m.key);
        if (t == null && m.entry != null) t = m.entry.text;
        if (t == null) return "";
        t = t.replace('\n', ' ').trim();
        int punkt = satzende(t);
        if (punkt > 20 && punkt < 200) t = t.substring(0, punkt + 1);
        return t;
    }

    /** Ende des ersten Satzes – Abkürzungen wie „z. B.“ oder „ggf.“ zählen nicht; -1, wenn keins gefunden. */
    static int satzende(String t) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\. (?=[A-ZÄÖÜ])").matcher(t);
        while (m.find()) {
            int i = m.start(), w = i;
            while (w > 0 && Character.isLetter(t.charAt(w - 1))) w--;
            String wort = t.substring(w, i);
            if (wort.length() <= 3 && !wort.isEmpty() && (Character.isLowerCase(wort.charAt(0)) || wort.length() == 1)) continue;
            if (wort.equals("bzw") || wort.equals("ggf") || wort.equals("evtl") || wort.equals("usw") || wort.equals("vgl")) continue;
            return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ Steckbrief

    /** Eine Karte: Name und Herkunft, Abenteuerpunkte, Aussehen und Daten – nebeneinander oder (schmal) untereinander. */
    private static JComponent steckbrief(Daten d, HeroData h) {
        JPanel k = new JPanel(new BorderLayout()) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        k.setBackground(Theme.card());
        k.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.trenner()), new MatteBorder(0, 3, 0, 0, Theme.accent())),
                new EmptyBorder(8, 10, 8, 10)));
        InventarLayout.eigen(k);
        k.add(new Steckbrief(person(d), daten(d, h), abenteuerpunkte(d)), BorderLayout.CENTER);
        return k;
    }

    private static JComponent person(Daten d) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        InventarLayout.eigen(p);
        JTextField orig = d.feld("Name-Field");
        JTextField name = new JTextField(d.text("Name"), 14);
        name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() + 5f));
        name.setBorder(new EmptyBorder(0, 0, 0, 0));
        name.setOpaque(false);
        TooltipHook.infoTip(name, "Name des Helden – ändern und mit Enter übernehmen");
        name.setAlignmentX(0);
        name.setMaximumSize(new Dimension(Integer.MAX_VALUE, name.getPreferredSize().height));
        InventarLayout.eigen(name);
        if (orig != null) uebernehmen(name, orig); else name.setEditable(false);
        p.add(name);
        p.add(Box.createVerticalStrut(2));
        for (String[] z : new String[][]{{"Rasse", d.text("Rasse")}, {"Kultur", d.text("Kultur")}, {"Profession", d.text("Profession")}}) {
            JLabel l = new JLabel("<html><span style='color:" + hex(Theme.muted()) + "'>" + z[0] + "</span>&nbsp; " + esc(z[1]) + "</html>");
            l.setAlignmentX(0);
            l.setBorder(new EmptyBorder(1, 0, 1, 4));
            InventarLayout.eigen(l);
            p.add(l);
        }
        return p;
    }

    private static String hex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // ------------------------------------------------------------------ Abenteuerpunkte

    private static JComponent abenteuerpunkte(Daten d) {
        Integer gesamt = d.zahl("Abenteuerpunkte"), frei = d.zahl("AP Guthaben");
        Integer aus = gesamt != null && frei != null ? gesamt - frei : null;
        JPanel t = KampfwerteLayout.tabelle();
        t.setOpaque(false);
        InventarLayout.eigen(t);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(1, 0, 1, 10);

        JTextField orig = d.feld("Abenteuerpunkte-Field");
        JTextField g = new JTextField(gesamt == null ? d.text("Abenteuerpunkte") : String.valueOf(gesamt), 5) {
            @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        };
        g.setHorizontalAlignment(SwingConstants.RIGHT);
        g.setFont(g.getFont().deriveFont(Font.BOLD));
        TooltipHook.infoTip(g, "<html>Gesamte AP – neue Zahl oder z. B. <b>+350</b> eingeben und mit Enter übernehmen.</html>");
        InventarLayout.eigen(g);
        if (orig != null) uebernehmen(g, orig); else g.setEditable(false);
        zeile(t, c, "AP gesamt", g);
        zeile(t, c, "ausgegeben", wert(aus));
        JLabel fl = wert(frei);
        if (frei != null && frei > 0) fl.setForeground(Theme.accent());
        zeile(t, c, "frei", fl);
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 2, 0);
        ApBalken b = new ApBalken(aus == null ? 0 : aus, frei == null ? 0 : frei);
        t.add(b, c);
        c.gridy++;
        c.insets = new Insets(1, 0, 0, 0);
        JLabel stufe = KampfwerteLayout.klein("Stufe " + d.text("Stufe (DSA 4.1)") + " (4.1) · " + d.text("Stufe (DSA 4.0)") + " (4.0)");
        InventarLayout.eigen(stufe);
        t.add(stufe, c);
        c.gridy++;
        c.insets = new Insets(6, 0, 0, 0);
        c.fill = GridBagConstraints.NONE;
        t.add(apHinzufuegen(), c);
        // Herkunft der AP wie im Original-Reiter: Tooltip über dem ganzen AP-Block (außer dem Knopf)
        for (Component k : t.getComponents())
            if (k instanceof JComponent && !(k instanceof AbstractButton))
                TooltipHook.tooltipFuer(TooltipHook.AP_HERKUNFT, (JComponent) k);
        return oben(t);
    }

    /** „AP hinzufügen“ öffnet „Steigern → Ereignis hinzufügen …“ der Helden-Software (dort ist AP-Hinzugewinn vorgewählt). */
    private static JComponent apHinzufuegen() {
        return menueKnopf("AP hinzufügen …", "Ereignis hinzuf",
                "<html>Öffnet <b>Steigern → Ereignis hinzufügen</b>: AP (z. B. nach einem Abenteuer) mit Kommentar eintragen.<br>"
                        + "Das Ereignis erscheint im Verlauf.</html>");
    }

    /** Knopf, der einen Menüpunkt der Helden-Software auslöst (gefunden über den Anfang seines Textes). */
    private static JComponent menueKnopf(String text, String menueAnfang, String tip) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.putClientProperty("JButton.buttonType", "roundRect");
        TooltipHook.infoTip(b, tip);
        InventarLayout.eigen(b);
        b.addActionListener(e -> {
            JMenuItem m = menuepunkt(SwingUtilities.getWindowAncestor(b), menueAnfang);
            if (m != null && m.isEnabled()) m.doClick(0);
            else JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(b),
                    "Der passende Menüpunkt der Helden-Software wurde nicht gefunden.", text,
                    JOptionPane.WARNING_MESSAGE);
        });
        return b;
    }

    /** Menüpunkt der Helden-Software, dessen Text so beginnt. */
    private static JMenuItem menuepunkt(Window w, String anfang) {
        if (!(w instanceof RootPaneContainer)) return null;
        JMenuBar bar = ((RootPaneContainer) w).getRootPane().getJMenuBar();
        if (bar == null) return null;
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenuItem m = suchen(bar.getMenu(i), anfang);
            if (m != null) return m;
        }
        return null;
    }

    private static JMenuItem suchen(JMenu menu, String anfang) {
        if (menu == null) return null;
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem it = menu.getItem(i);
            if (it == null) continue;
            if (it instanceof JMenu) {
                JMenuItem m = suchen((JMenu) it, anfang);
                if (m != null) return m;
            } else if (String.valueOf(it.getText()).startsWith(anfang)) return it;
        }
        return null;
    }

    /** Inhalt oben halten, auch wenn die Nachbarn höher sind. */
    private static JComponent oben(JComponent c) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override public Dimension getMinimumSize() { return c.getMinimumSize(); }
        };
        p.setOpaque(false);
        InventarLayout.eigen(p);
        JPanel links = new JPanel(new BorderLayout());
        links.setOpaque(false);
        InventarLayout.eigen(links);
        links.add(c, BorderLayout.WEST);
        p.add(links, BorderLayout.NORTH);
        return p;
    }

    private static void zeile(JPanel t, GridBagConstraints c, String titel, JComponent wert) {
        c.gridx = 0;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(titel);
        l.setForeground(Theme.muted());
        InventarLayout.eigen(l);
        t.add(l, c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.EAST;
        t.add(wert, c);
        c.anchor = GridBagConstraints.WEST;
        c.gridy++;
    }

    private static JLabel wert(Integer v) {
        JLabel l = new JLabel(v == null ? "–" : String.valueOf(v), SwingConstants.RIGHT);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(new EmptyBorder(0, 2, 0, 6));
        InventarLayout.eigen(l);
        return l;
    }

    /** Eigenes Feld; übernommen wird über das Feld der Helden-Software (Enter oder Verlassen des Feldes). */
    private static void uebernehmen(JTextField f, JTextField orig) {
        Runnable r = () -> {
            String neu = f.getText().trim();
            if (neu.equals(String.valueOf(orig.getText()).trim())) return;
            orig.setText(neu);
            orig.postActionEvent();
            SwingUtilities.invokeLater(() -> f.setText(String.valueOf(orig.getText())));
        };
        f.addActionListener(e -> r.run());
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { r.run(); }
        });
    }

    private static String verbinden(String sep, String... teile) {
        StringBuilder sb = new StringBuilder();
        for (String t : teile) {
            if (t == null || t.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(t.trim());
        }
        return sb.toString();
    }

    /** Ausgegebene (Akzent) und freie AP (Rahmen) als Balken. */
    private static final class ApBalken extends JComponent {
        final int aus, frei;
        ApBalken(int aus, int frei) {
            this.aus = Math.max(0, aus);
            this.frei = Math.max(0, frei);
            setPreferredSize(new Dimension(160, 14));
            putClientProperty("modernhelden.own", true);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = 10, y = (getHeight() - h) / 2;
            java.awt.geom.RoundRectangle2D form = new java.awt.geom.RoundRectangle2D.Float(0, y, w, h, h, h);
            g2.setColor(Theme.trenner());
            g2.fill(form);
            int summe = aus + frei;
            if (summe > 0 && aus > 0) {
                int wa = Math.round(w * aus / (float) summe);
                java.awt.geom.Area a = new java.awt.geom.Area(form);
                a.intersect(new java.awt.geom.Area(new Rectangle(0, y - 1, wa, h + 2)));
                Color m = Theme.muted();
                g2.setColor(new Color(m.getRed(), m.getGreen(), m.getBlue(), 200));
                g2.fill(a);
                if (frei > 0) {
                    java.awt.geom.Area f = new java.awt.geom.Area(form);
                    f.intersect(new java.awt.geom.Area(new Rectangle(wa, y - 1, w - wa, h + 2)));
                    g2.setColor(Theme.accent());
                    g2.fill(f);
                }
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ Aussehen und Daten

    private static JComponent daten(Daten d, HeroData h) {
        JPanel t = KampfwerteLayout.tabelle();
        t.setOpaque(false);
        InventarLayout.eigen(t);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        List<String[]> felder = new ArrayList<>();
        for (String[] f : new String[][]{
                {"Geschlecht", "Geschlecht"}, {"Alter", "Alter"}, {"Geburtstag", "Geburtsdatum"},
                {"Größe", "Größe"}, {"Gewicht", "Gewicht"}, {"Haare", "Haarfarbe"}, {"Augen", "Augenfarbe"}})
            felder.add(new String[]{f[0], d.text(f[1]), null});
        // statt der RS-Berechnung: Stand, Titel, Sozialstatus und Tarnidentität (aus „Angaben bearbeiten“ bzw. „Eigenschaften“),
        // bei Gildenmagiern zusätzlich die Gilde
        Integer so = h == null ? null : h.eig.get("SO");
        felder.add(new String[]{"Stand", h == null ? "" : h.stand, null});
        felder.add(new String[]{"Titel", h == null ? "" : h.titel, null});
        felder.add(new String[]{"Sozialstatus", so == null ? "" : String.valueOf(so), "Sozialstatus"});
        felder.add(new String[]{"Tarnidentität", h == null ? "" : h.tarnidentitaet, null});
        // immer neben der Tarnidentität; „–“ bei Helden ohne Gilde (wählbar nur für Gildenmagier und Lehrmeister)
        felder.add(new String[]{"Gildenzugehörigkeit", h == null || h.gilde == null ? "" : h.gilde, null});
        for (int i = 0; i < felder.size(); i++) {
            String[] f = felder.get(i);
            c.gridy = i / 2;
            c.gridx = (i % 2) * 2;
            c.insets = new Insets(1, 0, 1, 8);
            JLabel l = new JLabel(f[0]);
            l.setForeground(Theme.muted());
            InventarLayout.eigen(l);
            t.add(l, c);
            c.gridx++;
            JLabel w = new JLabel(f[1].isEmpty() ? "–" : f[1]);
            InventarLayout.eigen(w);
            c.insets = new Insets(1, 0, 1, i % 2 == 0 ? 20 : 4);
            t.add(w, c);
            if (f[2] != null) TooltipHook.tooltipFuer(f[2], l, w);
        }
        c.gridy = (felder.size() + 1) / 2;
        c.gridx = 0;
        c.gridwidth = 4;
        c.insets = new Insets(6, 0, 0, 0);
        t.add(menueKnopf("Angaben bearbeiten …", "Angaben bearb",
                "<html>Öffnet <b>Bearbeiten → Angaben bearbeiten</b>: Geburtstag, Alter, Größe, Gewicht, Haar- und Augenfarbe,<br>"
                        + "Titel, Stand, Tarnidentität, Aussehen und Familie.</html>"), c);
        return oben(t);
    }

    // ------------------------------------------------------------------ Listen

    private static JComponent knopf(AbstractButton orig, String text, String tip) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.putClientProperty("JButton.buttonType", "roundRect");
        TooltipHook.infoTip(b, tip);
        InventarLayout.eigen(b);
        if (orig == null) b.setVisible(false);
        else {
            b.setEnabled(orig.isEnabled());
            b.addActionListener(e -> orig.doClick(0));
        }
        return b;
    }

    /** Eine Zeile: Name (fett) und Kurzbeschreibung darunter (gedämpft, bei Platzmangel gekürzt). */
    private static JComponent eintrag(String name, String beschreibung, int i) {
        JPanel z = new JPanel(new BorderLayout(0, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        z.setBackground(i % 2 == 0 ? Theme.card() : Theme.zeile(1));
        z.setOpaque(true);
        z.setBorder(new EmptyBorder(2, 6, 2, 6));
        z.setAlignmentX(0);
        InventarLayout.eigen(z);
        // Name bricht bei schmaler Spalte in eine zweite Zeile um, statt gekürzt zu werden
        Umbruchtext n = new Umbruchtext(name);
        n.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
        n.setForeground(UIManager.getColor("Label.foreground"));
        InventarLayout.eigen(n);
        z.add(n, BorderLayout.NORTH);
        if (!beschreibung.isEmpty()) {
            // die Beschreibung bestimmt die Breite nicht mit – sie wird gekürzt
            JLabel b = new JLabel(beschreibung) {
                @Override public Dimension getPreferredSize() { return new Dimension(40, super.getPreferredSize().height); }
            };
            b.setForeground(Theme.muted());
            b.setFont(b.getFont().deriveFont(b.getFont().getSize2D() - 1f));
            InventarLayout.eigen(b);
            z.add(b, BorderLayout.CENTER);
        }
        // Tooltip (mit vollem Text) und Doppelklick (eigene Notiz) wie in den Ansichten der Helden-Software
        TooltipHook.tooltipFuer(name, z);
        return z;
    }

    /**
     * Zwei Spalten: links Person und Daten untereinander, rechts die Abenteuerpunkte; nur bei sehr schmalem
     * Fenster alles untereinander.
     */
    private static final class Steckbrief extends JPanel {
        static final int ABSTAND = 24, ZEILE = 8;
        final JComponent person, daten, ap;

        Steckbrief(JComponent person, JComponent daten, JComponent ap) {
            super(null);
            this.person = person;
            this.daten = daten;
            this.ap = ap;
            setOpaque(false);
            putClientProperty("modernhelden.own", true);
            add(person);
            add(daten);
            add(ap);
        }

        private int breite() {
            if (getWidth() > 0) return getWidth();
            Container p = getParent();
            while (p != null && p.getWidth() <= 0) p = p.getParent();
            return p == null ? Integer.MAX_VALUE : p.getWidth() - 40;
        }

        /** 3 = nebeneinander, 2 = zwei Spalten, 1 = untereinander. */
        private int modus(int b) {
            Dimension p = person.getPreferredSize(), d = daten.getPreferredSize(), a = ap.getPreferredSize();
            // immer zwei Spalten: links alles zur Person, rechts die Abenteuerpunkte
            if (Math.max(p.width, d.width) + a.width + ABSTAND <= b) return 2;
            return 1;
        }

        @Override public Dimension getPreferredSize() {
            Dimension p = person.getPreferredSize(), d = daten.getPreferredSize(), a = ap.getPreferredSize();
            int h;
            switch (modus(breite())) {
                case 3: h = Math.max(p.height, Math.max(d.height, a.height)); break;
                case 2: h = Math.max(p.height + ZEILE + d.height, a.height); break;
                default: h = p.height + d.height + a.height + 2 * ZEILE;
            }
            return new Dimension(p.width + d.width + a.width + 2 * ABSTAND, h);
        }

        @Override public Dimension getMinimumSize() {
            int w = Math.max(person.getMinimumSize().width, Math.max(daten.getMinimumSize().width, ap.getMinimumSize().width));
            return new Dimension(w, getPreferredSize().height);
        }

        @Override public void doLayout() {
            int b = getWidth(), h = getHeight();
            Dimension p = person.getPreferredSize(), d = daten.getPreferredSize(), a = ap.getPreferredSize();
            switch (modus(b)) {
                case 3: {
                    // Person bekommt den übrigen Platz, Abenteuerpunkte und Daten stehen rechts
                    int wp = b - a.width - d.width - 2 * ABSTAND;
                    person.setBounds(0, 0, wp, h);
                    ap.setBounds(wp + ABSTAND, 0, a.width, h);
                    daten.setBounds(wp + a.width + 2 * ABSTAND, 0, d.width, h);
                    break;
                }
                case 2: {
                    // linke Spalte nur so breit wie ihr Inhalt – die AP stehen direkt daneben, nicht am rechten Rand
                    int wl = Math.min(Math.max(p.width, d.width), b - a.width - ABSTAND);
                    person.setBounds(0, 0, wl, p.height);
                    daten.setBounds(0, p.height + ZEILE, wl, d.height);
                    ap.setBounds(wl + ABSTAND, 0, a.width, h);
                    break;
                }
                default: {
                    person.setBounds(0, 0, b, p.height);
                    daten.setBounds(0, p.height + ZEILE, b, d.height);
                    ap.setBounds(0, p.height + d.height + 2 * ZEILE, b, a.height);
                }
            }
            if (h != getPreferredSize().height) SwingUtilities.invokeLater(this::revalidate);
        }
    }

    /** Spalteninhalt: so breit wie der sichtbare Bereich, nie breiter. */
    private static final class ListenInhalt extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 160; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
        }
    }

}
