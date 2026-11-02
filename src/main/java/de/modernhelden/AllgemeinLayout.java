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

    /** [i]-Knopf über die ganze Zeilenhöhe; öffnet „Infos“ der Helden-Software zum Eintrag. */
    static JButton iKnopf(String name, Runnable aktion) {
        JButton k = new JButton("i") {
            @Override public Dimension getPreferredSize() { return new Dimension(26, super.getPreferredSize().height); }
        };
        k.setFocusable(false);
        k.setFont(k.getFont().deriveFont(Font.BOLD));
        k.setMargin(new Insets(0, 0, 0, 0));
        k.putClientProperty("JButton.buttonType", "square");
        TooltipHook.infoTip(k, "<html>Öffnet <b>Infos</b> der Helden-Software zu „" + esc(name) + "“:<br>"
                + "Kommentar und – bei Ritualen und Liturgien – Probe, Dauer, Kosten und Wirkung</html>");
        k.addActionListener(e -> aktion.run());
        InventarLayout.eigen(k);
        return k;
    }

    /**
     * Öffnet „Infos“ der Helden-Software zu einer Sonderfertigkeit – über den i-Knopf der Tabelle im Reiter
     * „Allgemein“ (auch wenn dieser gerade im Original-Design oder unsichtbar ist). false, wenn nicht gefunden.
     */
    static boolean sfInfos(String name) {
        JPanel allg = null;
        for (JComponent c : alle.keySet()) if (c instanceof JPanel) { allg = (JPanel) c; break; }
        if (allg == null) for (Frame f : Frame.getFrames()) { allg = allgemeinSuchen(f); if (allg != null) break; }
        if (allg == null) return false;
        Daten d = lesen(allg);
        if (d.sf == null || infoSpalte(d.sf) < 0) return false;
        javax.swing.table.TableModel m = d.sf.getModel();
        int treffer = -1;
        for (int i = 0; i < m.getRowCount() && treffer < 0; i++) {
            Object o = m.getValueAt(i, 0);
            if (o != null && String.valueOf(o).trim().equals(name)) treffer = i;
        }
        // Namen in anderer Schreibweise (z. B. mit Auswahl in Klammern): Anfang genügt
        for (int i = 0; i < m.getRowCount() && treffer < 0; i++) {
            Object o = m.getValueAt(i, 0);
            if (o != null && (String.valueOf(o).trim().startsWith(name) || name.startsWith(String.valueOf(o).trim()))) treffer = i;
        }
        if (treffer < 0) return false;
        infosOeffnen(d.sf, treffer);
        return true;
    }

    private static JPanel allgemeinSuchen(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof JPanel && istAllgemein((JPanel) k)) return (JPanel) k;
            if (k instanceof Container) { JPanel r = allgemeinSuchen((Container) k); if (r != null) return r; }
        }
        return null;
    }

    /** Spalte „Info“ (i-Knöpfe) der Tabelle der Helden-Software; -1, wenn es sie nicht gibt. */
    private static int infoSpalte(JTable t) {
        for (int c = 0; c < t.getColumnCount(); c++) if ("Info".equals(t.getColumnName(c))) return c;
        return -1;
    }

    /** Löst den i-Knopf der Helden-Software zum Eintrag aus (öffnet ihr Fenster „Infos“). */
    private static void infosOeffnen(JTable t, String name) {
        javax.swing.table.TableModel m = t.getModel();
        for (int i = 0; i < m.getRowCount(); i++) {
            Object o = m.getValueAt(i, 0);
            if (o != null && String.valueOf(o).trim().equals(name)) { infosOeffnen(t, i); return; }
        }
    }

    /**
     * Klickt den i-Knopf einer Tabellenzeile der Helden-Software. Der Knopf kommt von ihrem Zellen-Renderer zum
     * Wert der Zeile – das klappt auch, wenn die Zeile gerade weggefiltert oder die Tabelle unsichtbar ist.
     */
    private static boolean infosOeffnen(JTable t, int modelZeile) {
        int col = infoSpalte(t);
        if (col < 0 || modelZeile < 0 || modelZeile >= t.getModel().getRowCount()) return false;
        Object wert = t.getModel().getValueAt(modelZeile, t.convertColumnIndexToModel(col));
        int zeile = t.convertRowIndexToView(modelZeile);
        javax.swing.table.TableCellRenderer r = t.getColumnModel().getColumn(col).getCellRenderer();
        if (r == null) r = t.getDefaultRenderer(t.getColumnClass(col));
        Component k = r.getTableCellRendererComponent(t, wert, false, false, Math.max(0, zeile), col);
        if (!(k instanceof AbstractButton)) return false;
        ((AbstractButton) k).doClick();
        return true;
    }

    /** Öffnet „Infos“ der Helden-Software zu einem Zauber – über den i-Knopf im Reiter „Zauber“. */
    static boolean zauberInfos(String name) {
        for (Frame f : Frame.getFrames()) {
            JTable t = tabelleSuchen(f, "Zauberfertigkeit");
            if (t == null || infoSpalte(t) < 0) continue;
            int sp = -1;
            for (int c = 0; c < t.getColumnCount(); c++) if ("Zauberfertigkeit".equals(t.getColumnName(c))) sp = t.convertColumnIndexToModel(c);
            javax.swing.table.TableModel m = t.getModel();
            for (int i = 0; i < m.getRowCount(); i++) {
                String z = String.valueOf(m.getValueAt(i, sp)).trim();
                // „Analys Arkanstruktur (KL/KL/IN)“ – Probe in Klammern hinter dem Namen
                if (z.equals(name) || z.startsWith(name + " (")) return infosOeffnen(t, i);
            }
        }
        return false;
    }

    private static JTable tabelleSuchen(Container c, String spalte) {
        for (Component k : c.getComponents()) {
            if (k instanceof JTable) {
                JTable t = (JTable) k;
                for (int i = 0; i < t.getColumnCount(); i++) if (spalte.equals(t.getColumnName(i))) return t;
            }
            if (k instanceof Container) { JTable r = tabelleSuchen((Container) k, spalte); if (r != null) return r; }
        }
        return null;
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
        // etwas länger warten: beim Heldenwechsel kommen viele Feldänderungen kurz hintereinander
        final javax.swing.Timer spaeter = new javax.swing.Timer(250, e -> neuAufbauen());
        String letzterStand;
        int warten;

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
            // Heldenwechsel: Die Helden-Software füllt ihre Felder nach und nach, die Heldendaten des Plugins kommen
            // danach. Erst aufbauen, wenn beides zum selben Helden gehört – sonst flackert die Seite durch Zwischenstände.
            if (h != null && ansicht.getComponentCount() > 0 && !d.text("Name").isEmpty() && !d.text("Name").equals(h.name)
                    && warten++ < 12) {   // höchstens etwa 3 Sekunden – falls die Namen einmal nicht übereinstimmen
                spaeter();
                return;
            }
            warten = 0;
            String stand = stand(d, h);
            if (stand.equals(letzterStand) && ansicht.getComponentCount() > 0) return;
            letzterStand = stand;
            if (DebugBaum.ordner != null) System.err.println("[Allgemein] Ansicht neu aufgebaut");
            ansicht.aufbauen(d, h);
            // umbrechende Texte kennen ihre Höhe erst nach dem Anordnen: vor dem Zeichnen mehrfach anordnen,
            // damit die Karten nicht kurz danach springen
            if (scroll != null && scroll.getViewport().getWidth() > 0)
                for (int i = 0; i < 3; i++) { ungueltig(ansicht); scroll.getViewport().validate(); }
        }

        private void ungueltig(Component c) {
            c.invalidate();
            if (c instanceof Container) for (Component k : ((Container) c).getComponents()) ungueltig(k);
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
            super(new BorderLayout(0, 10));
            this.z = z;
            setOpaque(true);
            setBorder(new EmptyBorder(12, 12, 12, 12));
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
            JPanel oben = new JPanel(new BorderLayout(0, 10));
            oben.setOpaque(false);
            InventarLayout.eigen(oben);
            oben.add(steckbrief(d, h), BorderLayout.CENTER);
            oben.add(suche(d), BorderLayout.SOUTH);
            add(oben, BorderLayout.NORTH);

            vorteile.eintraege.clear();
            nachteile.eintraege.clear();
            for (String s : zeilen(d.vorNach)) (istNachteil(s, h) ? nachteile : vorteile).eintraege.add(s);
            sonder.eintraege.clear();
            sonder.eintraege.addAll(zeilen(d.sf));
            sonder.info = d.sf == null || infoSpalte(d.sf) < 0 ? null : name -> infosOeffnen(d.sf, name);
            JPanel spalten = new JPanel(new GridLayout(1, 3, 10, 0));
            spalten.setOpaque(false);
            InventarLayout.eigen(spalten);
            vorteile.symbol = "vorteil";
            nachteile.symbol = "nachteil";
            sonder.symbol = "stern";
            spalten.add(vorteile.karte(null));
            spalten.add(nachteile.karte(knopf(d.senken, "Nachteile senken", "Nachteile mit AP senken (Dialog der Helden-Software)")));
            spalten.add(sonder.karte(knopf(d.hinzufuegen, "Hinzufügen", "Sonderfertigkeit erlernen (Dialog der Helden-Software)")));
            add(spalten, BorderLayout.CENTER);
            filtern();
            revalidate();
            repaint();
        }

        JComponent suche(Daten d) {
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
            JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            links.setOpaque(false);
            InventarLayout.eigen(links);
            links.add(f);
            // „Info“ öffnet das Fenster „Infos“ der Helden-Software (Kommentare und Angaben aller Einträge)
            JTable quelle = d.sf != null && d.sf.getRowCount() > 0 && infoSpalte(d.sf) >= 0 ? d.sf
                    : d.vorNach != null && d.vorNach.getRowCount() > 0 && infoSpalte(d.vorNach) >= 0 ? d.vorNach : null;
            if (quelle != null) {
                JButton info = new JButton("Info");
                info.setFocusable(false);
                TooltipHook.infoTip(info, "<html>Öffnet <b>Infos</b> der Helden-Software: Kommentare zu Sonderfertigkeiten, "
                        + "Vor-/Nachteilen und Zaubern, bei Ritualen und Liturgien auch Probe, Dauer, Kosten und Wirkung</html>");
                info.addActionListener(e -> infosOeffnen(quelle, 0));
                InventarLayout.eigen(info);
                links.add(info);
            }
            p.add(links, BorderLayout.WEST);
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
        /** Öffnet „Infos“ der Helden-Software zum Eintrag (null: kein Info-Knopf in dieser Liste). */
        java.util.function.Consumer<String> info;
        /** Breite folgt der Spalte (Namen brechen um statt abgeschnitten zu werden), Höhe dem Inhalt. */
        final JPanel inhalt = new ListenInhalt();
        /** Anzahl im Chip; etwas breiter als berechnet – Segoe UI (Windows) ragt sonst über den Rand und wird abgeschnitten */
        final JLabel anzahl = new JLabel() {
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(d.width + 4, d.height);
            }
            @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        };

        Liste(String titel, String leer) {
            this.titel = titel;
            this.leer = leer;
            inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
            InventarLayout.eigen(inhalt);
            InventarLayout.eigen(anzahl);
        }

        /** Symbol der Plakette im Kartenkopf */
        String symbol = "liste";

        JComponent karte(JComponent knopf) {
            EigenschaftenLayout.Rund k = new EigenschaftenLayout.Rund(new BorderLayout(0, 10), Theme.card(), Theme.trenner(), 16) {
                // im schmalen Fenster darf der Kopf gekürzt werden – die Seite soll nicht breiter als das Fenster werden
                @Override public Dimension getMinimumSize() { return new Dimension(160, super.getMinimumSize().height); }
            };
            k.setBorder(new EmptyBorder(12, 14, 14, 14));
            // Titel darf im schmalen Fenster gekürzt werden („Sonderfertigk…“), der Zähl-Chip bleibt immer ganz sichtbar
            JPanel kopf = new JPanel(new GridBagLayout());
            kopf.setOpaque(false);
            InventarLayout.eigen(kopf);
            // passt der Titel nicht, steht die Abkürzung da („SF“) – der Zähl-Chip daneben bleibt ganz
            final String kurz = "Sonderfertigkeiten".equals(titel) ? "SF" : titel;
            JLabel t = new JLabel(titel) {
                int vollBreite = -1;
                @Override public Dimension getMinimumSize() {
                    JLabel p = new JLabel(kurz, getIcon(), LEADING);
                    p.setFont(getFont());
                    p.setIconTextGap(getIconTextGap());
                    return p.getPreferredSize();
                }
                @Override public void setBounds(int x, int y, int w, int h) {
                    super.setBounds(x, y, w, h);
                    if (vollBreite < 0) {
                        JLabel p = new JLabel(titel, getIcon(), LEADING);
                        p.setFont(getFont());
                        p.setIconTextGap(getIconTextGap());
                        vollBreite = p.getPreferredSize().width;
                    }
                    String soll = w >= vollBreite ? titel : kurz;
                    if (!soll.equals(getText())) setText(soll);
                }
                @Override public Dimension getPreferredSize() {
                    JLabel p = new JLabel(titel, getIcon(), LEADING);
                    p.setFont(getFont());
                    p.setIconTextGap(getIconTextGap());
                    return p.getPreferredSize();
                }
            };
            t.setFont(t.getFont().deriveFont(Font.BOLD, t.getFont().getSize2D() + 4f));
            t.setIcon(new EigenschaftenLayout.Plakette(symbol, 30, Theme.accent(), Color.WHITE));
            t.setIconTextGap(10);
            TooltipHook.infoTip(t, titel);
            InventarLayout.eigen(t);
            GridBagConstraints kc = new GridBagConstraints();
            kc.anchor = GridBagConstraints.WEST;
            kc.fill = GridBagConstraints.HORIZONTAL;
            kc.weightx = 0;
            kc.insets = new Insets(0, 0, 0, 10);
            kopf.add(t, kc);
            // Anzahl als Chip (bei Suche „n von m“)
            EigenschaftenLayout.Rund chip = new EigenschaftenLayout.Rund(new BorderLayout(),
                    EigenschaftenLayout.mische(Theme.kachel(), Theme.accent(), 0.08),
                    EigenschaftenLayout.mische(Theme.kachel(), Theme.accent(), 0.3), 999);
            chip.setBorder(new EmptyBorder(2, 10, 3, 10));
            // von der Grundschrift ableiten – die Karte wird bei jedem Heldenwechsel neu gebaut, das Label bleibt dasselbe
            Font basis = UIManager.getFont("Label.font");
            anzahl.setFont(basis.deriveFont(basis.getSize2D() - 1f));
            anzahl.setForeground(UIManager.getColor("Label.foreground"));
            anzahl.setBorder(null);
            anzahl.setHorizontalAlignment(SwingConstants.CENTER);
            chip.add(anzahl);
            JPanel chipHalter = new JPanel(new GridBagLayout()) {
                @Override public Dimension getMinimumSize() { return getPreferredSize(); }
            };
            chipHalter.setOpaque(false);
            InventarLayout.eigen(chipHalter);
            chipHalter.add(chip);
            kc.fill = GridBagConstraints.NONE;
            kc.insets = new Insets(0, 0, 0, 0);
            kopf.add(chipHalter, kc);
            kc.weightx = 1;
            kopf.add(Box.createHorizontalGlue(), kc);
            k.add(kopf, BorderLayout.NORTH);
            // Knopf unten über die ganze Breite – wie in der Ansicht der Helden-Software
            if (knopf != null) k.add(knopf, BorderLayout.SOUTH);
            inhalt.setOpaque(false);
            JScrollPane sp = new JScrollPane(inhalt, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            sp.getVerticalScrollBar().setUnitIncrement(16);
            InventarLayout.eigen(sp);
            // Einträge in einer hellen Fläche mit runden Ecken (wie die Tabellen in „Kampfwerte“)
            EigenschaftenLayout.Rund flaeche = new EigenschaftenLayout.Rund(new BorderLayout(), Theme.kachel(), Theme.trenner(), 12) {
                @Override protected void paintChildren(Graphics g) {
                    // Inhalt an die runden Ecken anpassen
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.clip(new java.awt.geom.RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 11, 11));
                    super.paintChildren(g2);
                    g2.dispose();
                }
            };
            flaeche.setBorder(new EmptyBorder(1, 1, 1, 1));
            flaeche.add(sp, BorderLayout.CENTER);
            // mindestens ein paar Zeilen hoch, auch bei sehr kleinem Fenster
            flaeche.setMinimumSize(new Dimension(80, 120));
            flaeche.setPreferredSize(new Dimension(80, 120));
            k.add(flaeche, BorderLayout.CENTER);
            return k;
        }

        void fuellen(String q) {
            inhalt.removeAll();
            int n = 0;
            for (String s : eintraege) {
                String b = beschreibung(s), eigen = eigeneNotiz(s);
                if (!q.isEmpty() && !(s + " " + b + " " + eigen).toLowerCase(Locale.GERMAN).contains(q)) continue;
                inhalt.add(eintrag(s, b, eigen, n++, info));
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

    /** Eigener Tooltip-Text zum Eintrag (eine Zeile), leer, wenn keiner. */
    static String eigeneNotiz(String zeile) {
        if (db == null) return "";
        TooltipDB.Match m = db.find(zeile);
        if (m == null) return "";
        String t = db.getUserText(m.key);
        if (t == null && m.entry != null) t = db.getUserText(m.entry.name);
        return t == null ? "" : t.replace('\n', ' ').trim();
    }

    /** Erster Satz der Kurzbeschreibung – ein eigener Tooltip-Text ersetzt sie nicht, er steht darunter. */
    static String beschreibung(String zeile) {
        if (db == null) return "";
        TooltipDB.Match m = db.find(zeile);
        if (m == null || m.entry == null) return "";
        String t = m.entry.text;
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

    /**
     * Wie in „Eigenschaften“: oben die Abenteuerpunkte als Leiste, darunter die Karte „Held“ (Name und Herkunft,
     * Aussehen und Daten daneben oder – im schmalen Fenster – darunter).
     */
    private static JComponent steckbrief(Daten d, HeroData h) {
        // Held kompakt: Kopf mit Plakette, Name (Titel) und „Angaben bearbeiten …“; darunter Herkunft in einer Zeile
        // und die Daten (im breiten Fenster rechts daneben)
        EigenschaftenLayout.Rund heldKarte = new EigenschaftenLayout.Rund(new BorderLayout(0, 8), Theme.card(), Theme.trenner(), 16) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        heldKarte.setBorder(new EmptyBorder(10, 16, 12, 16));
        JPanel kopf = new JPanel(new BorderLayout(12, 0));
        kopf.setOpaque(false);
        InventarLayout.eigen(kopf);
        kopf.add(name(d), BorderLayout.CENTER);
        JPanel knopfHalter = new JPanel(new GridBagLayout());
        knopfHalter.setOpaque(false);
        InventarLayout.eigen(knopfHalter);
        knopfHalter.add(menueKnopf("Angaben bearbeiten …", "Angaben bearb",
                "<html>Öffnet <b>Bearbeiten → Angaben bearbeiten</b>: Geburtstag, Alter, Größe, Gewicht, Haar- und Augenfarbe,<br>"
                        + "Titel, Stand, Tarnidentität, Aussehen und Familie.</html>"));
        kopf.add(knopfHalter, BorderLayout.EAST);
        heldKarte.add(kopf, BorderLayout.NORTH);

        JPanel held = new JPanel(new EigenschaftenLayout.Spalten(12, new double[]{1, 2}));
        held.setOpaque(false);
        InventarLayout.eigen(held);
        JComponent da = daten(d, h);
        // Daten nur daneben, wenn sie ganz hineinpassen – sonst darunter (nicht abgeschnitten)
        held.add(EigenschaftenLayout.mindestens(EigenschaftenLayout.oben(herkunft(d)), 260));
        held.add(EigenschaftenLayout.mindestens(EigenschaftenLayout.oben(da), da.getPreferredSize().width + 8));
        heldKarte.add(EigenschaftenLayout.oben(held), BorderLayout.CENTER);

        // wie in „Eigenschaften“: Abenteuerpunkte als Leiste oben über die ganze Breite, darunter der Held
        JPanel stapel = new JPanel(new BorderLayout(0, 10));
        stapel.setOpaque(false);
        InventarLayout.eigen(stapel);
        stapel.add(abenteuerpunkte(d), BorderLayout.NORTH);
        stapel.add(heldKarte, BorderLayout.CENTER);
        return stapel;
    }

    /** Plakette und Name des Helden als Titel der Karte (Eingabefeld, Enter übernimmt). */
    private static JComponent name(Daten d) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        InventarLayout.eigen(p);
        JLabel plakette = new JLabel(new EigenschaftenLayout.Plakette("person", 32, Theme.accent(), Color.WHITE));
        InventarLayout.eigen(plakette);
        p.add(plakette, BorderLayout.WEST);
        JTextField orig = d.feld("Name-Field");
        JTextField name = new JTextField(d.text("Name"), 12);
        name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() + 5f));
        name.setBorder(new EmptyBorder(0, 0, 0, 0));
        name.setOpaque(false);
        TooltipHook.infoTip(name, "Name des Helden – ändern und mit Enter übernehmen");
        InventarLayout.eigen(name);
        if (orig != null) uebernehmen(name, orig); else name.setEditable(false);
        p.add(name, BorderLayout.CENTER);
        return p;
    }

    /** Rasse, Kultur und Profession – je eine Zeile mit Bezeichnung; lange Professionen brechen um. */
    private static JComponent herkunft(Daten d) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        InventarLayout.eigen(p);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        for (String[] z : new String[][]{{"Rasse", d.text("Rasse")}, {"Kultur", d.text("Kultur")}, {"Profession", d.text("Profession")}}) {
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.insets = new Insets(1, 0, 1, 12);
            JLabel l = new JLabel(z[0]);
            l.setForeground(Theme.muted());
            InventarLayout.eigen(l);
            p.add(l, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(1, 0, 1, 0);
            HtmlText w = new HtmlText("<html>" + esc(z[1].isEmpty() ? "–" : z[1]) + "</html>");
            InventarLayout.eigen(w);
            p.add(w, c);
            c.gridy++;
        }
        return p;
    }

    private static String hex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // ------------------------------------------------------------------ Abenteuerpunkte

    /**
     * Abenteuerpunkte als Leiste wie in „Eigenschaften“, in einer Zeile: „Gesamt“ bleibt ein Eingabefeld, dazu
     * „AP hinzufügen …“. Im schmalen Fenster entfällt nur das Wort „Abenteuerpunkte“ neben der Plakette.
     */
    private static JComponent abenteuerpunkte(Daten d) {
        Integer gesamt = d.zahl("Abenteuerpunkte"), frei = d.zahl("AP Guthaben");
        Integer aus = gesamt != null && frei != null ? gesamt - frei : null;
        JLabel t = new JLabel("Abenteuerpunkte");
        EigenschaftenLayout.Rund k = new EigenschaftenLayout.Rund(new GridBagLayout(), Theme.card(), Theme.trenner(), 16) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            @Override public Dimension getMinimumSize() { return new Dimension(200, super.getMinimumSize().height); }
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                Insets i = getInsets();
                return new Dimension(d.width, Math.max(d.height, apLeisteHoehe() + i.top + i.bottom));
            }
            @Override public void setBounds(int x, int y, int w, int h) {
                super.setBounds(x, y, w, h);
                String soll = w >= 1000 ? "Abenteuerpunkte" : "";
                if (!soll.equals(t.getText())) { t.setText(soll); SwingUtilities.invokeLater(this::revalidate); }
            }
        };
        k.setBorder(new EmptyBorder(8, 16, 8, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 24);
        t.setFont(t.getFont().deriveFont(Font.BOLD, t.getFont().getSize2D() + 1f));
        t.setIcon(new EigenschaftenLayout.Plakette("ap", 30, Theme.accent(), Color.WHITE));
        t.setIconTextGap(10);
        TooltipHook.infoTip(t, "Abenteuerpunkte");
        InventarLayout.eigen(t);
        k.add(t, c);

        // Gesamt: Eingabefeld (neue Zahl oder z. B. „+350“)
        JTextField orig = d.feld("Abenteuerpunkte-Field");
        JTextField g = new JTextField(gesamt == null ? d.text("Abenteuerpunkte") : String.valueOf(gesamt), 5) {
            @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        };
        g.setHorizontalAlignment(SwingConstants.RIGHT);
        g.setFont(g.getFont().deriveFont(Font.BOLD, g.getFont().getSize2D() + 2f));
        TooltipHook.infoTip(g, "<html>Gesamte AP – neue Zahl oder z. B. <b>+350</b> eingeben und mit Enter übernehmen.</html>");
        InventarLayout.eigen(g);
        if (orig != null) uebernehmen(g, orig); else g.setEditable(false);
        k.add(zahlMitText(g, "Gesamt", null), c);
        k.add(zahlMitText(apZahl(aus), "Ausgegeben", null), c);
        k.add(zahlMitText(apZahl(frei), "Verfügbar", frei != null && frei > 0 ? Theme.accent() : null), c);

        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 12);
        double anteil = gesamt == null || gesamt <= 0 || aus == null ? 0 : Math.max(0, Math.min(1, aus / (double) gesamt));
        JComponent balken = new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(120, 10); }
            @Override public Dimension getMinimumSize() { return new Dimension(40, 10); }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g2 = (Graphics2D) g0.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int y = (getHeight() - 10) / 2;
                g2.setColor(EigenschaftenLayout.mische(Theme.kachel(), UIManager.getColor("Label.foreground"), 0.12));
                g2.fillRoundRect(0, y, getWidth(), 10, 10, 10);
                g2.setColor(Theme.accent());
                g2.fillRoundRect(0, y, (int) Math.round(getWidth() * anteil), 10, 10, 10);
                g2.dispose();
            }
        };
        k.add(balken, c);
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, 14);
        // zwei feste Zeilen (kein Umbruch) – die Leiste bleibt flach
        JLabel stufe = KampfwerteLayout.klein("<html><nobr>" + Math.round(anteil * 100) + "&nbsp;%&nbsp;ausgegeben</nobr><br><nobr>Stufe&nbsp;"
                + esc(d.text("Stufe (DSA 4.1)")) + "&nbsp;(4.1)&nbsp;·&nbsp;" + esc(d.text("Stufe (DSA 4.0)")) + "&nbsp;(4.0)</nobr></html>");
        InventarLayout.eigen(stufe);
        k.add(stufe, c);
        c.insets = new Insets(0, 0, 0, 0);
        k.add(apHinzufuegen(), c);
        // Herkunft der AP wie im Original-Reiter: Tooltip über der ganzen Leiste (außer Knopf und Eingabefeld)
        for (Component x : alleIn(k))
            if (x instanceof JComponent && !(x instanceof AbstractButton) && !(x instanceof JTextField) && x != t)
                TooltipHook.tooltipFuer(TooltipHook.AP_HERKUNFT, (JComponent) x);
        return k;
    }

    /**
     * Innenhöhe der Abenteuerpunkte-Leiste – in „Allgemein“ und „Eigenschaften“ gleich, auch wenn dort kein
     * Eingabefeld und kein Knopf steht: die höchste der Zellen (Feld mit Bezeichnung, zweizeiliger Hinweis, Knopf).
     */
    static int apLeisteHoehe() {
        JTextField f = new JTextField("0", 5);
        f.setFont(f.getFont().deriveFont(Font.BOLD, f.getFont().getSize2D() + 2f));
        int h = zahlMitText(f, "Gesamt", null).getPreferredSize().height;
        h = Math.max(h, KampfwerteLayout.klein("<html>x<br>x</html>").getPreferredSize().height);
        h = Math.max(h, new JButton("AP hinzufügen …").getPreferredSize().height);
        return h;
    }

    private static List<Component> alleIn(Container c) {
        List<Component> l = new ArrayList<>();
        for (Component k : c.getComponents()) {
            l.add(k);
            if (k instanceof Container) l.addAll(alleIn((Container) k));
        }
        return l;
    }

    private static JLabel apZahl(Integer v) {
        JLabel l = new JLabel(v == null ? "–" : String.format(Locale.GERMAN, "%,d", v));
        l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() + 4f));
        InventarLayout.eigen(l);
        return l;
    }

    /** Große Zahl (oder Eingabefeld), darunter klein die Bezeichnung. */
    private static JComponent zahlMitText(JComponent zahl, String text, Color farbe) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        InventarLayout.eigen(p);
        zahl.setAlignmentX(0);
        if (farbe != null) zahl.setForeground(farbe);
        p.add(zahl);
        JLabel l = KampfwerteLayout.klein(text);
        l.setAlignmentX(0);
        if (farbe != null) l.setForeground(farbe);
        InventarLayout.eigen(l);
        p.add(l);
        return p;
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
    static JMenuItem menuepunkt(Window w, String anfang) {
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
                {"Größe", "Größe"}, {"Gewicht", "Gewicht"}, {"Haare", "Haarfarbe"}})
            felder.add(new String[]{f[0], d.text(f[1]), null});
        // statt der RS-Berechnung: Stand, Titel, Sozialstatus und Tarnidentität (aus „Angaben bearbeiten“ bzw. „Eigenschaften“),
        // bei Gildenmagiern zusätzlich die Gilde
        Integer so = h == null ? null : h.eig.get("SO");
        felder.add(new String[]{"Augen", d.text("Augenfarbe"), null});
        felder.add(new String[]{"Stand", h == null ? "" : h.stand, null});
        felder.add(new String[]{"Titel", h == null ? "" : h.titel, null});
        felder.add(new String[]{"Sozialstatus", so == null ? "" : String.valueOf(so), "Sozialstatus"});
        felder.add(new String[]{"Tarnidentität", h == null ? "" : h.tarnidentitaet, null});
        // „–“ bei Helden ohne Gilde (wählbar nur für Gildenmagier und Lehrmeister)
        felder.add(new String[]{"Gilde", h == null || h.gilde == null ? "" : h.gilde, null});
        // drei Paare je Zeile (vier Zeilen) – so bleibt die Karte flach
        for (int i = 0; i < felder.size(); i++) {
            String[] f = felder.get(i);
            c.gridy = i / 3;
            c.gridx = (i % 3) * 2;
            c.insets = new Insets(1, 0, 1, 10);
            JLabel l = new JLabel(f[0]);
            l.setForeground(Theme.muted());
            InventarLayout.eigen(l);
            t.add(l, c);
            c.gridx++;
            JLabel w = new JLabel(f[1].isEmpty() ? "–" : f[1]);
            InventarLayout.eigen(w);
            c.insets = new Insets(1, 0, 1, i % 3 < 2 ? 22 : 4);
            t.add(w, c);
            if (f[2] != null) TooltipHook.tooltipFuer(f[2], l, w);
        }
        // linksbündig, auch wenn die Karte breiter ist
        JPanel links = new JPanel(new BorderLayout());
        links.setOpaque(false);
        InventarLayout.eigen(links);
        links.add(t, BorderLayout.WEST);
        return links;
    }

    // ------------------------------------------------------------------ Listen

    private static JComponent knopf(AbstractButton orig, String text, String tip) {
        // gewöhnlicher Knopf wie „Angaben bearbeiten …“ – passt in Hell und Dunkel zum übrigen Design
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
    private static JComponent eintrag(String name, String beschreibung, String eigen, int i, java.util.function.Consumer<String> info) {
        JPanel z = new JPanel(new BorderLayout(0, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        // Zebra-Streifen in denselben Farben wie die Tabellen in Talente/Zauber
        Color alt = Theme.isModern() ? UIManager.getColor("Table.alternateRowColor") : null;
        if (alt == null) alt = EigenschaftenLayout.mische(Theme.kachel(), Theme.accent(), Theme.isDark() ? 0.06 : 0.035);
        z.setBackground(i % 2 == 0 ? Theme.kachel() : alt);
        z.setOpaque(true);
        z.setBorder(new EmptyBorder(4, 10, 4, 8));
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
        if (!eigen.isEmpty()) {
            // eigener Tooltip-Text als zusätzliche Zeile unter der Kurzbeschreibung (gekürzt, ganz im Tooltip)
            JLabel o = new JLabel(eigen) {
                @Override public Dimension getPreferredSize() { return new Dimension(40, super.getPreferredSize().height); }
            };
            o.setForeground(Theme.accent());
            o.setFont(o.getFont().deriveFont(o.getFont().getSize2D() - 1f));
            InventarLayout.eigen(o);
            z.add(o, BorderLayout.SOUTH);
        }
        // Tooltip (mit vollem Text) und Doppelklick (eigene Notiz) wie in den Ansichten der Helden-Software
        TooltipHook.tooltipFuer(name, z);
        if (info != null) {
            // [i] rechts neben dem Eintrag über die ganze Zeilenhöhe – wie der i-Knopf in der Tabelle der Helden-Software
            JPanel text = new JPanel(new BorderLayout(0, 0));
            text.setOpaque(false);
            text.setBorder(new EmptyBorder(4, 0, 4, 6));
            InventarLayout.eigen(text);
            for (Component c : z.getComponents()) {
                Object lage = ((BorderLayout) z.getLayout()).getConstraints(c);
                z.remove(c);
                text.add(c, lage);
            }
            z.setBorder(new EmptyBorder(0, 10, 0, 0));
            z.add(text, BorderLayout.CENTER);
            z.add(iKnopf(name, () -> info.accept(name)), BorderLayout.EAST);
        }
        return z;
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
