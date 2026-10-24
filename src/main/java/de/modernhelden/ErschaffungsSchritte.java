package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inhalte der einzelnen Erschaffungsschritte in den modernen Designs.
 * <ul>
 *   <li>Jeder Schritt bekommt einen Kopf mit Nummer, Titel und einer kurzen Erklärung.</li>
 *   <li>Regelbasis, Einstellungen und Aussehen werden als Karten neu aufgebaut. Die Bedienelemente teilen sich
 *       Modell bzw. Dokument mit denen der Helden-Software – jede Eingabe landet also direkt dort.</li>
 *   <li>Rasse, Kultur und Profession: Liste mit hervorgehobenen GP-Kosten, Suchfeld mit Platzhalter.</li>
 * </ul>
 * Im Original-Design wird alles wieder so eingesetzt, wie die Helden-Software es angelegt hat.
 */
final class ErschaffungsSchritte {

    private static final String KOPF = "modernhelden.schrittKopf";
    private static final String ANSICHT = "modernhelden.schrittAnsicht";

    private ErschaffungsSchritte() {}

    /** Kurze Erklärung je Schritt (eigene Worte). */
    private static String erklaerung(String schritt) {
        switch (schritt) {
            case "Laden": return "Einen gespeicherten Entwurf fortsetzen oder einen Entwurf importieren.";
            case "Regelbasis": return "Regelwerk und Erweiterungen, aus denen Rassen, Kulturen, Professionen und Vorteile stammen.";
            case "Einstellungen": return "Punktegrenzen für die Erschaffung und das Geschlecht des Helden.";
            case "Rasse": return "Wähle die Rasse – Varianten stehen darunter, die Kosten in GP daneben.";
            case "Kultur": return "Wähle die Kultur, in der dein Held aufgewachsen ist.";
            case "Profession": return "Wähle die Profession. „Breitgefächerte Bildung“ und „Veteran“ fügen einen eigenen Schritt hinzu.";
            case "2. Profession": return "Die zweite Profession für die breitgefächerte Bildung.";
            case "Veteran Variante": return "Die Variante der Profession für den Veteranen.";
            case "Aussehen": return "Name und Aussehen – auswürfeln lassen oder selbst eintragen.";
            case "Auswahl": return "Wahlmöglichkeiten aus Rasse, Kultur und Profession, eine nach der anderen.";
            case "Eigenschaften": return "Eigenschaften festlegen. Mindest- und Höchstwerte ergeben sich aus Rasse, Kultur und Profession.";
            case "Vor- und Nachteile": return "Vorteile kosten GP, Nachteile bringen GP. Oben auswählen, unten steht, was der Held hat.";
            case "verbilligte SF": return "Sonderfertigkeiten, die Rasse, Kultur oder Profession günstiger machen.";
            case "Sonderfertigkeiten": return "Weitere Sonderfertigkeiten erwerben. Unten stehen die bereits gewählten.";
            case "Talente": return "Talente aktivieren (oben) und steigern (unten).";
            case "Zauber": return "Zauber aktivieren und steigern.";
            case "Kostenübersicht": return "Alle Kosten im Überblick. „Fertigstellen“ legt den Helden an.";
            default: return null;
        }
    }

    // ------------------------------------------------------------------ Anwenden / Zurücksetzen

    static void anwenden(JTabbedPane reiter) {
        for (int i = 0; i < reiter.getTabCount(); i++) {
            Component c = reiter.getComponentAt(i);
            if (!(c instanceof JPanel) || !(((JPanel) c).getLayout() instanceof BorderLayout)) continue;
            JPanel huelle = (JPanel) c;
            BorderLayout bl = (BorderLayout) huelle.getLayout();
            kopf(huelle, bl, reiter, i);
            Component mitte = bl.getLayoutComponent(BorderLayout.CENTER);
            if (mitte == null) continue;
            if (mitte instanceof Ansicht) {
                ((Ansicht) mitte).aktualisieren();
                continue;
            }
            Ansicht a = ansichtFuer(mitte);
            if (a != null) {
                huelle.remove(mitte);
                huelle.add(a, BorderLayout.CENTER);
                huelle.putClientProperty(ANSICHT, a);
                a.aktualisieren();
                huelle.revalidate();
                huelle.repaint();
            } else if (mitte instanceof JComponent) {
                baeume((JComponent) mitte);
                stil(huelle, (JComponent) mitte);
            }
        }
    }

    static void zuruecksetzen(JTabbedPane reiter) {
        for (int i = 0; i < reiter.getTabCount(); i++) {
            Component c = reiter.getComponentAt(i);
            if (!(c instanceof JPanel)) continue;
            JPanel huelle = (JPanel) c;
            Object k = huelle.getClientProperty(KOPF);
            if (k instanceof Component) huelle.remove((Component) k);
            huelle.putClientProperty(KOPF, null);
            Object r = huelle.getClientProperty(RUECK);
            if (r instanceof List) {
                for (Object o : (List<?>) r) ((Runnable) o).run();
                huelle.putClientProperty(RUECK, null);
            }
            Object a = huelle.getClientProperty(ANSICHT);
            if (a instanceof Ansicht) {
                Ansicht an = (Ansicht) a;
                an.loesen();
                huelle.remove(an);
                huelle.add(an.orig, BorderLayout.CENTER);
                huelle.putClientProperty(ANSICHT, null);
            }
            if (huelle.getLayout() instanceof BorderLayout) {
                Component mitte = ((BorderLayout) huelle.getLayout()).getLayoutComponent(BorderLayout.CENTER);
                if (mitte instanceof JComponent) baeumeZurueck((JComponent) mitte);
            }
            huelle.revalidate();
            huelle.repaint();
        }
    }

    private static void kopf(JPanel huelle, BorderLayout bl, JTabbedPane reiter, int i) {
        String titel = reiter.getTitleAt(i);
        Object alt = huelle.getClientProperty(KOPF);
        if (alt instanceof Kopf && ((Kopf) alt).passt(titel, i + 1)) return;
        if (alt instanceof Component) huelle.remove((Component) alt);
        if (bl.getLayoutComponent(BorderLayout.NORTH) != null && !(bl.getLayoutComponent(BorderLayout.NORTH) instanceof Kopf)) return;
        Kopf k = new Kopf(titel, i + 1, erklaerung(titel));
        huelle.add(k, BorderLayout.NORTH);
        huelle.putClientProperty(KOPF, k);
        huelle.revalidate();
    }

    /** Schritte mit Suchleiste über den Tabellen. */
    private static final java.util.Set<String> SUCHE = new java.util.HashSet<>(java.util.Arrays.asList(
            "Vor- und Nachteile", "verbilligte SF", "Sonderfertigkeiten", "Talente", "Zauber"));

    /** Kopf eines Schritts: „Schritt 4“ klein darüber, Titel groß, Erklärung blass. */
    private static final class Kopf extends JPanel {
        final String titel; final int nr;
        Suchleiste leiste;
        void suche(JPanel huelle) {
            if (leiste != null) return;
            leiste = new Suchleiste(huelle, titel);
            add(leiste, BorderLayout.SOUTH);
            revalidate();
        }
        Kopf(String titel, int nr, String erklaerung) {
            super(new BorderLayout(0, 2));
            this.titel = titel; this.nr = nr;
            setOpaque(false);
            setBorder(new EmptyBorder(10, 14, 6, 14));
            JLabel schritt = KampfwerteLayout.klein("SCHRITT " + nr);
            schritt.setFont(schritt.getFont().deriveFont(Font.BOLD, schritt.getFont().getSize2D() - 1f));
            schritt.setForeground(Theme.accent());
            JLabel t = new JLabel(titel);
            t.setFont(t.getFont().deriveFont(Font.BOLD, t.getFont().getSize2D() + 5f));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(KampfwerteLayout.links(schritt));
            text.add(KampfwerteLayout.links(t));
            if (erklaerung != null) {
                Umbruchtext e = new Umbruchtext(erklaerung);
                e.setForeground(Theme.muted());
                e.setAlignmentX(0);
                text.add(Box.createVerticalStrut(2));
                text.add(e);
                InventarLayout.eigen(e);
            }
            add(text, BorderLayout.CENTER);
            for (JComponent c : new JComponent[]{this, schritt, t, text}) InventarLayout.eigen(c);
        }
        boolean passt(String titel, int nr) { return this.titel.equals(titel) && this.nr == nr; }
    }

    /** Suchfeld und (bei Talenten) Schalter je Kategorie; filtert alle Tabellen des Schritts. */
    private static final class Suchleiste extends JPanel {
        final JPanel huelle;
        final JTextField feld = new JTextField(18);
        final JPanel chips = new JPanel(new TalenteSortierung.Umbruch(4, 4));
        final JLabel zahl = KampfwerteLayout.klein("");
        String kategorie;
        String kategorienStand = "";

        Suchleiste(JPanel huelle, String titel) {
            super(new BorderLayout(10, 0));
            this.huelle = huelle;
            setOpaque(false);
            setBorder(new EmptyBorder(6, 0, 0, 0));
            feld.putClientProperty("JTextField.placeholderText", platzhalter(titel));
            feld.putClientProperty("JTextField.showClearButton", true);
            feld.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
            feld.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { filtern(); }
            });
            // Suchfeld und Kategorie-Schalter fließen gemeinsam: so wenig Zeilen wie möglich
            feld.setBorder(BorderFactory.createCompoundBorder(feld.getBorder(), new EmptyBorder(0, 0, 0, 0)));
            chips.setOpaque(false);
            chips.add(feld);
            add(chips, BorderLayout.CENTER);
            zahl.setVerticalAlignment(SwingConstants.TOP);
            zahl.setBorder(new EmptyBorder(6, 8, 0, 4));
            add(zahl, BorderLayout.EAST);
            for (JComponent c : new JComponent[]{this, feld, chips, zahl}) InventarLayout.eigen(c);
        }

        private static String platzhalter(String titel) {
            switch (titel) {
                case "Talente": return "Talente suchen …";
                case "Zauber": return "Zauber suchen …";
                case "Vor- und Nachteile": return "Vor- und Nachteile suchen …";
                default: return "Sonderfertigkeiten suchen …";
            }
        }

        List<JTable> tabellen() {
            List<JTable> l = new ArrayList<>();
            if (huelle.getLayout() instanceof BorderLayout) {
                Component m = ((BorderLayout) huelle.getLayout()).getLayoutComponent(BorderLayout.CENTER);
                if (m instanceof Container) for (JTable t : alle((Container) m, JTable.class)) if (ErschaffungsTabellen.ist(t)) l.add(t);
            }
            return l;
        }

        void filtern() {
            String q = feld.getText();
            for (JTable t : tabellen()) ErschaffungsTabellen.filtern(t, q, kategorie);
            anzahl();
            reiterTreffer(!q.trim().isEmpty() || kategorie != null, true);
        }

        /**
         * Unterreiter (z. B. Vorteile/Nachteile, Allgemein/Nahkampf …) zeigen die Trefferzahl; liegt im gewählten
         * Unterreiter nichts, springt die Ansicht zum ersten mit Treffern.
         */
        private void reiterTreffer(boolean aktiv, boolean springen) {
            java.util.Map<JTabbedPane, int[]> erster = new java.util.LinkedHashMap<>();
            java.util.Map<JTabbedPane, java.util.Map<Integer, Integer>> zahlen = new java.util.HashMap<>();
            for (JTable t : tabellen()) {
                Component k = t;
                while (k != null && k != huelle) {
                    Container p = k.getParent();
                    if (p instanceof JTabbedPane) {
                        JTabbedPane tp = (JTabbedPane) p;
                        int i = tp.indexOfComponent(k);
                        if (i >= 0) zahlen.computeIfAbsent(tp, x -> new java.util.TreeMap<>()).merge(i, t.getRowCount(), Integer::sum);
                    }
                    k = p;
                }
            }
            for (java.util.Map.Entry<JTabbedPane, java.util.Map<Integer, Integer>> e : zahlen.entrySet()) {
                JTabbedPane tp = e.getKey();
                for (java.util.Map.Entry<Integer, Integer> z : e.getValue().entrySet()) {
                    int i = z.getKey();
                    Component c = tp.getComponentAt(i);
                    if (!(c instanceof JComponent)) continue;
                    JComponent jc = (JComponent) c;
                    Object orig = jc.getClientProperty("modernhelden.reiterTitel");
                    if (orig == null) {
                        orig = tp.getTitleAt(i);
                        jc.putClientProperty("modernhelden.reiterTitel", orig);
                        final Object o = orig;
                        rueck(huelle, () -> { int j = tp.indexOfComponent(jc); if (j >= 0) tp.setTitleAt(j, (String) o); jc.putClientProperty("modernhelden.reiterTitel", null); });
                    }
                    String neu = aktiv ? orig + " (" + z.getValue() + ")" : (String) orig;
                    if (!neu.equals(tp.getTitleAt(i))) tp.setTitleAt(i, neu);
                }
                if (aktiv && springen) {
                    Integer jetzt = e.getValue().get(tp.getSelectedIndex());
                    if (jetzt != null && jetzt == 0)
                        for (java.util.Map.Entry<Integer, Integer> z : e.getValue().entrySet())
                            if (z.getValue() > 0) { tp.setSelectedIndex(z.getKey()); break; }
                }
            }
        }

        /** Regelmäßig: Kategorien neu zählen (Aktivieren verschiebt Talente zwischen den Tabellen), neue Tabellen filtern. */
        void aktualisieren() {
            List<JTable> ts = tabellen();
            java.util.Map<String, Integer> k = ErschaffungsTabellen.kategorien(ts);
            String st = k.toString() + "|" + kategorie + "|" + Theme.current();
            if (!st.equals(kategorienStand)) {
                kategorienStand = st;
                while (chips.getComponentCount() > 1) chips.remove(chips.getComponentCount() - 1);   // Suchfeld bleibt
                if (!k.isEmpty()) {
                    chips.add(chip("Alle", kategorie == null, null));
                    for (java.util.Map.Entry<String, Integer> e : k.entrySet())
                        chips.add(chip(e.getKey() + " " + e.getValue(), e.getKey().equals(kategorie), e.getKey()));
                }
                chips.revalidate();
                chips.repaint();
            }
            boolean neu = false;
            for (JTable t : ts) {
                if (t.getClientProperty("modernhelden.gefiltert") == null) { t.putClientProperty("modernhelden.gefiltert", true); neu = true; }
            }
            if (neu && (!feld.getText().trim().isEmpty() || kategorie != null)) filtern();
            else if (!feld.getText().trim().isEmpty() || kategorie != null) reiterTreffer(true, false);
            anzahl();
        }

        private JToggleButton chip(String text, boolean an, String wert) {
            // Breite immer aus dem aktuellen Text/der Schrift (Windows-Schriften sind breiter), Höhe wie das Suchfeld
            JToggleButton b = new JToggleButton(text, an) {
                @Override public Dimension getPreferredSize() {
                    Dimension d = super.getPreferredSize();
                    return new Dimension(d.width + 4, Math.max(feld.getPreferredSize().height, 20));
                }
                @Override public Dimension getMinimumSize() { return getPreferredSize(); }
            };
            b.setFocusable(false);
            b.putClientProperty("JButton.buttonType", "roundRect");
            b.setMargin(new Insets(0, 7, 0, 7));
            Color a = Theme.accent();
            b.putClientProperty("FlatLaf.style", String.format(
                    "selectedBackground: #%02x%02x%02x; selectedForeground: #ffffff", a.getRed(), a.getGreen(), a.getBlue()));
            b.addActionListener(e -> {
                kategorie = wert;
                kategorienStand = "";
                aktualisieren();
                filtern();
            });
            InventarLayout.eigen(b);
            return b;
        }

        void anzahl() {
            int alle = 0, sichtbar = 0;
            for (JTable t : tabellen()) { alle += t.getModel().getRowCount(); sichtbar += t.getRowCount(); }
            String s = sichtbar == alle ? alle + " Einträge" : sichtbar + " von " + alle;
            if (!s.equals(zahl.getText())) zahl.setText(s);
        }
    }

    private static Ansicht ansichtFuer(Component mitte) {
        String n = mitte.getClass().getName();
        if (n.equals("helden.gui.erschaffung.dialoge.SettingsPanel")) return new Regelbasis((JComponent) mitte);
        if (n.equals("helden.gui.erschaffung.dialoge.GeschlechtPanel")) return new Einstellungen((JComponent) mitte);
        if (n.equals("helden.gui.erschaffung.dialoge.AussehenDialog")) return new Aussehen((JComponent) mitte);
        return null;
    }

    // ------------------------------------------------------------------ eigene Ansichten

    /** Eigene Ansicht eines Schritts; das Original bleibt erhalten und wird im Original-Design wieder eingesetzt. */
    private abstract static class Ansicht extends JPanel {
        final JComponent orig;
        final JPanel inhalt = new JPanel();
        String stand = "";
        Ansicht(JComponent orig) {
            super(new BorderLayout());
            this.orig = orig;
            InventarLayout.eigen(this);
            inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
            inhalt.setBorder(new EmptyBorder(0, 14, 14, 14));
            InventarLayout.eigen(inhalt);
            JPanel oben = new JPanel(new BorderLayout()) {
                @Override public Dimension getPreferredSize() {
                    Dimension d = super.getPreferredSize();
                    Container p = getParent();
                    return p instanceof JViewport ? new Dimension(((JViewport) p).getWidth(), d.height) : d;
                }
            };
            oben.add(inhalt, BorderLayout.NORTH);
            InventarLayout.eigen(oben);
            JScrollPane sp = new JScrollPane(oben, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.getVerticalScrollBar().setUnitIncrement(16);
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            oben.setOpaque(false);
            inhalt.setOpaque(false);
            setOpaque(false);
            InventarLayout.eigen(sp);
            add(sp, BorderLayout.CENTER);
        }
        /** Neu aufbauen, wenn sich am Original etwas geändert hat. */
        final void aktualisieren() {
            String s = stand() + "|" + Theme.current();
            if (s.equals(stand)) return;
            stand = s;
            inhalt.removeAll();
            aufbauen();
            inhalt.revalidate();
            inhalt.repaint();
        }
        abstract String stand();
        abstract void aufbauen();
        void loesen() { }

        JPanel karte(String titel) {
            JPanel k = KampfwerteLayout.karte(titel);
            k.setAlignmentX(0);
            if (inhalt.getComponentCount() > 0) inhalt.add(Box.createVerticalStrut(10));
            inhalt.add(k);
            return k;
        }
    }

    /** Regelbasis: Auswahl des Regelwerks mit Beschreibung, darunter die Regelbücher und Erweiterungen. */
    private static final class Regelbasis extends Ansicht {
        Regelbasis(JComponent orig) { super(orig); }

        @Override String stand() {
            StringBuilder sb = new StringBuilder();
            JComboBox<?> cb = ErschaffungsRahmen.erstes(orig, JComboBox.class);
            if (cb != null) sb.append(cb.getSelectedItem());
            JEditorPane ep = ErschaffungsRahmen.erstes(orig, JEditorPane.class);
            if (ep != null) sb.append(ep.getText().hashCode());
            for (Component c : buecherPanel().getComponents())
                sb.append(c instanceof AbstractButton ? "[" + ((AbstractButton) c).getText() + ((AbstractButton) c).isEnabled() : ((JLabel) c).getText());
            return sb.toString();
        }

        private JPanel buecherPanel() {
            JCheckBox cb = ErschaffungsRahmen.erstes(orig, JCheckBox.class);
            if (cb != null && cb.getParent() instanceof JPanel) return (JPanel) cb.getParent();
            return new JPanel();
        }

        @Override void aufbauen() {
            JPanel k = karte("Regelwerk");
            JPanel z = new JPanel();
            z.setLayout(new BoxLayout(z, BoxLayout.Y_AXIS));
            z.setOpaque(false);
            JComboBox<?> cbo = ErschaffungsRahmen.erstes(orig, JComboBox.class);
            if (cbo != null) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                JComboBox cb = new JComboBox(cbo.getModel());
                cb.setRenderer((ListCellRenderer) cbo.getRenderer());
                cb.setMaximumSize(new Dimension(260, cb.getPreferredSize().height));
                cb.setPreferredSize(new Dimension(260, cb.getPreferredSize().height));
                z.add(KampfwerteLayout.links(cb));
            }
            JEditorPane ep = ErschaffungsRahmen.erstes(orig, JEditorPane.class);
            if (ep != null) {
                String t = ep.getText().replaceAll("(?is)<head>.*?</head>", "").replaceAll("<[^>]*>", "\n")
                        .replaceAll("[ \\t]+", " ").replaceAll("\\s*\\n\\s*", "\n").trim();
                if (!t.isEmpty()) {
                    z.add(Box.createVerticalStrut(6));
                    Umbruchtext u = new Umbruchtext(t);
                    u.setForeground(Theme.muted());
                    u.setAlignmentX(0);
                    z.add(u);
                }
            }
            k.add(z, BorderLayout.CENTER);

            JPanel b = karte("Regelbücher und Erweiterungen");
            JPanel l = new JPanel();
            l.setLayout(new BoxLayout(l, BoxLayout.Y_AXIS));
            l.setOpaque(false);
            JPanel grund = null;
            List<String> unter = new ArrayList<>();
            JComponent letzte = null;
            for (Component c : buecherPanel().getComponents()) {
                if (c instanceof AbstractButton) {
                    if (letzte != null) unterliste(l, unter);
                    AbstractButton o = (AbstractButton) c;
                    JCheckBox n = new JCheckBox(o.getText().trim());
                    n.setModel(o.getModel());
                    n.setOpaque(false);
                    n.setToolTipText(o.isEnabled() ? null : "Gehört fest zum Regelwerk.");
                    if (!o.isEnabled()) {
                        // feste Grundbücher kompakt als eigene Gruppe
                        if (grund == null) {
                            grund = new JPanel(new TalenteSortierung.Umbruch(0, 0));
                            grund.setOpaque(false);
                            grund.setAlignmentX(0);
                            JLabel h = KampfwerteLayout.klein("Immer enthalten");
                            h.setBorder(new EmptyBorder(0, 0, 2, 0));
                            l.add(KampfwerteLayout.links(h));
                            l.add(grund);
                            l.add(Box.createVerticalStrut(8));
                            JLabel w = KampfwerteLayout.klein("Wählbar");
                            w.setBorder(new EmptyBorder(0, 0, 2, 0));
                            l.add(KampfwerteLayout.links(w));
                        }
                        n.setBorder(new EmptyBorder(1, 0, 1, 14));
                        grund.add(n);
                        letzte = null;
                    } else {
                        n.setFont(n.getFont().deriveFont(Font.BOLD));
                        n.setAlignmentX(0);
                        l.add(Box.createVerticalStrut(4));
                        l.add(n);
                        letzte = n;
                    }
                } else if (c instanceof JLabel) {
                    String t = ((JLabel) c).getText();
                    if (t != null) unter.add(t.replaceFirst("^-\\s*", "").trim());
                }
            }
            unterliste(l, unter);
            b.add(l, BorderLayout.CENTER);
        }

        private static void unterliste(JPanel l, List<String> unter) {
            if (unter.isEmpty()) return;
            Umbruchtext u = new Umbruchtext(String.join(" · ", unter));
            u.setForeground(Theme.muted());
            u.setBorder(new EmptyBorder(0, 26, 2, 4));
            u.setAlignmentX(0);
            l.add(u);
            unter.clear();
        }
    }

    /** Einstellungen: Punktegrenzen und Geschlecht. */
    private static final class Einstellungen extends Ansicht {
        Einstellungen(JComponent orig) { super(orig); }

        @Override String stand() {
            StringBuilder sb = new StringBuilder();
            for (JSpinner s : alle(orig, JSpinner.class)) sb.append(s.getName()).append(s.isEnabled());
            for (JRadioButton r : alle(orig, JRadioButton.class)) sb.append(r.getText()).append(r.isEnabled());
            return sb.toString();
        }

        @Override void aufbauen() {
            JPanel k = karte("Punktegrenzen");
            JPanel t = KampfwerteLayout.tabelle();
            t.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = 0;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(3, 0, 3, 12);
            List<JLabel> labels = alle(orig, JLabel.class);
            for (JSpinner o : alle(orig, JSpinner.class)) {
                String name = "", standard = "";
                // Beschriftung links und „Standard: …“ rechts stehen in derselben Zeile wie der Spinner
                int zeile = gridy(o);
                for (JLabel lb : labels) {
                    if (lb.getText() == null || lb.getParent() != o.getParent() || gridy(lb) != zeile) continue;
                    if (gridx(lb) < gridx(o)) name = lb.getText().replaceAll(":\\s*$", "");
                    else standard = lb.getText();
                }
                c.gridx = 0;
                t.add(new JLabel(beschriftung(name)), c);
                JSpinner s = new JSpinner(o.getModel());
                s.setEnabled(o.isEnabled());
                s.setPreferredSize(new Dimension(90, s.getPreferredSize().height));
                c.gridx = 1;
                t.add(s, c);
                c.gridx = 2;
                t.add(KampfwerteLayout.klein(standard), c);
                c.gridy++;
            }
            k.add(t, BorderLayout.WEST);

            JPanel g = karte("Geschlecht");
            JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            r.setOpaque(false);
            for (JRadioButton o : alle(orig, JRadioButton.class)) {
                JRadioButton n = new JRadioButton(o.getText());
                n.setModel(o.getModel());
                n.setOpaque(false);
                n.setBorder(new EmptyBorder(2, 0, 2, 22));
                r.add(n);
            }
            g.add(r, BorderLayout.WEST);
            JLabel h = KampfwerteLayout.klein("Ohne Geschlecht kann die Erschaffung nicht starten.");
            g.add(h, BorderLayout.SOUTH);
        }

        private static int gridy(Component c) {
            LayoutManager l = c.getParent() == null ? null : c.getParent().getLayout();
            return l instanceof GridBagLayout ? ((GridBagLayout) l).getConstraints(c).gridy : c.getY();
        }
        private static int gridx(Component c) {
            LayoutManager l = c.getParent() == null ? null : c.getParent().getLayout();
            return l instanceof GridBagLayout ? ((GridBagLayout) l).getConstraints(c).gridx : c.getX();
        }

        private static String beschriftung(String n) {
            switch (n) {
                case "max GP": return "Generierungspunkte (GP)";
                case "max Eigenschaft-GP": return "davon höchstens für Eigenschaften";
                case "Eigenschaftsmax": return "Höchster Eigenschaftswert";
                default: return n;
            }
        }
    }

    /** Aussehen: Name mit Würfeln und Namensoptionen, darunter Größe, Gewicht, Haar- und Augenfarbe. */
    private static final class Aussehen extends Ansicht {
        Aussehen(JComponent orig) { super(orig); }

        @Override String stand() {
            StringBuilder sb = new StringBuilder();
            for (JTextField f : alle(orig, JTextField.class)) sb.append(f.isEditable()).append(f.isEnabled());
            for (JButton b : alle(orig, JButton.class)) sb.append(b.getText()).append(b.isEnabled());
            return sb.toString();
        }

        @Override void aufbauen() {
            JPanel k = karte("Name");
            JPanel z = new JPanel(new BorderLayout(6, 0));
            z.setOpaque(false);
            JTextField name = feld("getNameTextField");
            if (name != null) {
                name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() + 2f));
                name.putClientProperty("JTextField.placeholderText", "Name des Helden");
                z.add(name, BorderLayout.CENTER);
            }
            JPanel kn = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            kn.setOpaque(false);
            for (JButton o : alle(orig, JButton.class)) {
                if ("Name auswürfeln".equals(o.getText())) kn.add(proxy(o, "Name auswürfeln", null));
            }
            JButton optionen = new JButton("Namen …");
            optionen.setToolTipText("Namensliste wählen und ob der Held adlig ist");
            optionen.addActionListener(e -> {
                Object m = ErschaffungsRahmen.rufe(orig, "getEinstellungMenu");
                if (m instanceof JPopupMenu) ((JPopupMenu) m).show(optionen, 0, optionen.getHeight());
            });
            kn.add(optionen);
            z.add(kn, BorderLayout.EAST);
            k.add(z, BorderLayout.CENTER);

            JPanel a = karte("Aussehen");
            JPanel t = KampfwerteLayout.tabelle();
            t.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = 0;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(3, 0, 3, 12);
            String[][] felder = {{"Größe", "getGroesseTextField"}, {"Gewicht", "getGewichtTextField"},
                    {"Haarfarbe", "getHaarfarbeTextField"}, {"Augenfarbe", "getAugenfarbeTextField"}};
            for (String[] f : felder) {
                JTextField n = feld(f[1]);
                if (n == null) continue;
                c.gridx = 0;
                c.weightx = 0;
                c.fill = GridBagConstraints.NONE;
                t.add(new JLabel(f[0]), c);
                c.gridx = 1;
                n.setColumns(14);
                t.add(n, c);
                c.gridy++;
            }
            for (JButton o : alle(orig, JButton.class)) {
                if ("Nochmal würfeln".equals(o.getText())) {
                    c.gridx = 1;
                    c.insets = new Insets(8, 0, 0, 0);
                    t.add(proxy(o, "Aussehen neu würfeln", null), c);
                }
            }
            a.add(t, BorderLayout.WEST);
        }

        /** Textfeld, das sich das Dokument mit dem Feld der Helden-Software teilt. */
        private JTextField feld(String methode) {
            Object o = ErschaffungsRahmen.rufe(orig, methode);
            if (!(o instanceof JTextField)) return null;
            JTextField f = (JTextField) o;
            JTextField n = new JTextField(f.getDocument(), null, 18);
            n.setEditable(f.isEditable());
            n.setEnabled(f.isEnabled());
            return n;
        }
    }

    /** Knopf, der den Knopf der Helden-Software auslöst. */
    private static JButton proxy(AbstractButton o, String text, String tip) {
        JButton b = new JButton(text);
        b.setEnabled(o.isEnabled());
        if (tip != null) b.setToolTipText(tip);
        b.addActionListener(e -> o.doClick(0));
        return b;
    }

    static <T> List<T> alle(Container c, Class<T> art) {
        List<T> l = new ArrayList<>();
        sammeln(c, art, l);
        return l;
    }

    private static <T> void sammeln(Container c, Class<T> art, List<T> l) {
        for (Component k : c.getComponents()) {
            if (art.isInstance(k)) l.add(art.cast(k));
            if (k instanceof Container) sammeln((Container) k, art, l);
        }
    }

    // ------------------------------------------------------------------ Feinschliff an den Original-Bausteinen

    private static final String RUECK = "modernhelden.schrittRueck";
    private static final String GESTYLT = "modernhelden.schrittStil";

    @SuppressWarnings("unchecked")
    private static void rueck(JPanel huelle, Runnable r) {
        Object o = huelle.getClientProperty(RUECK);
        List<Runnable> l = o instanceof List ? (List<Runnable>) o : new ArrayList<>();
        l.add(r);
        huelle.putClientProperty(RUECK, l);
    }

    /**
     * Überschriften wie „Nicht aktivierte Talente:“ hervorheben, Listen luftiger, Auswahl-Raster oben links statt
     * mitten im leeren Feld. Jede Änderung merkt sich, wie sie rückgängig gemacht wird.
     */
    private static void stil(JPanel huelle, JComponent mitte) {
        for (JLabel l : alle(mitte, JLabel.class)) {
            if (l.getClientProperty(GESTYLT) != null || Boolean.TRUE.equals(l.getClientProperty("modernhelden.own"))) continue;
            String t = l.getText();
            if (t == null) continue;
            String rein = t.replaceAll("<[^>]*>", " ").trim();
            boolean ueberschrift = rein.endsWith(":") && rein.length() > 6 && l.getIcon() == null
                    && !(l.getParent() != null && l.getParent().getLayout() instanceof GridBagLayout && rein.length() < 14);
            boolean frage = rein.startsWith("Wähle") || rein.startsWith("Treffe");
            // Abschnitts-Überschriften ohne Doppelpunkt (z. B. „Automatische und gewählte Vor- und Nachteile“)
            if (!ueberschrift && (rein.startsWith("Automatische ") || rein.startsWith("Mögliche ") || rein.startsWith("Nicht aktivierte ")))
                ueberschrift = true;
            if (!ueberschrift && !frage) continue;
            l.putClientProperty(GESTYLT, Boolean.TRUE);
            Object kopf = huelle.getClientProperty(KOPF);
            if (kopf instanceof Kopf && rein.replaceAll(":$", "").trim().equalsIgnoreCase(((Kopf) kopf).titel)) {
                // doppelt zum Kopf – ausblenden
                boolean sichtbar = l.isVisible();
                l.setVisible(false);
                rueck(huelle, () -> { l.setVisible(sichtbar); l.putClientProperty(GESTYLT, null); });
                continue;
            }
            Font f = l.getFont();
            javax.swing.border.Border b = l.getBorder();
            if (frage) l.setFont(f.deriveFont(Font.BOLD, f.getSize2D() + 2f));
            l.setBorder(BorderFactory.createCompoundBorder(b, new EmptyBorder(frage ? 2 : 4, 2, 4, 0)));
            if (rein.endsWith(":")) l.setText(t.replaceFirst(":(\\s*(<br>)?\\s*)$", "$1"));
            final String alt = t;
            rueck(huelle, () -> { l.setFont(f); l.setBorder(b); l.setText(alt); l.putClientProperty(GESTYLT, null); });
        }
        boolean tabellen = false;
        for (JTable t : alle(mitte, JTable.class)) {
            if (!ErschaffungsTabellen.ist(t)) continue;
            tabellen = true;
            ErschaffungsTabellen.einrichten(t, r -> rueck(huelle, r));
        }
        Object kopf = huelle.getClientProperty(KOPF);
        if (tabellen && kopf instanceof Kopf && SUCHE.contains(((Kopf) kopf).titel)) ((Kopf) kopf).suche(huelle);
        if (kopf instanceof Kopf && ((Kopf) kopf).leiste != null) ((Kopf) kopf).leiste.aktualisieren();
        // Schritte mit Auswahl oben und Gewähltem unten: unten etwas mehr Platz als die Helden-Software vorsieht
        if (kopf instanceof Kopf && SUCHE.contains(((Kopf) kopf).titel)) {
            for (JSplitPane sp : alle(mitte, JSplitPane.class)) {
                if (sp.getOrientation() != JSplitPane.VERTICAL_SPLIT || sp.getClientProperty(GESTYLT) != null || sp.getHeight() <= 0) continue;
                sp.putClientProperty(GESTYLT, Boolean.TRUE);
                int ort = sp.getDividerLocation();
                double gewicht = sp.getResizeWeight();
                sp.setResizeWeight(0.46);
                sp.setDividerLocation(0.46);
                rueck(huelle, () -> { sp.setResizeWeight(gewicht); sp.setDividerLocation(ort); sp.putClientProperty(GESTYLT, null); });
            }
        }
        for (JList<?> li : alle(mitte, JList.class)) {
            if (li.getClientProperty(GESTYLT) != null) continue;
            li.putClientProperty(GESTYLT, Boolean.TRUE);
            int h = li.getFixedCellHeight();
            li.setFixedCellHeight(26);
            rueck(huelle, () -> { li.setFixedCellHeight(h); li.putClientProperty(GESTYLT, null); });
        }
        // Auswahl-Raster (Talente/Werte mit Radioknöpfen je Spalte)
        for (JRadioButton rb : alle(mitte, JRadioButton.class)) {
            Container p = rb.getParent();
            if (!(p instanceof JPanel) || !(p.getLayout() instanceof GridBagLayout)) continue;
            JPanel raster = (JPanel) p;
            GridBagLayout gb = (GridBagLayout) raster.getLayout();
            @SuppressWarnings("unchecked")
            java.util.Map<Component, GridBagConstraints> alt = (java.util.Map<Component, GridBagConstraints>) raster.getClientProperty(GESTYLT);
            if (alt == null) {
                // einmal je Raster: Füller rechts unten schiebt das Raster nach oben links
                java.util.Map<Component, GridBagConstraints> karte = new java.util.HashMap<>();
                alt = karte;
                raster.putClientProperty(GESTYLT, karte);
                JPanel fueller = new JPanel();
                fueller.setOpaque(false);
                InventarLayout.eigen(fueller);
                GridBagConstraints fc = new GridBagConstraints();
                fc.gridx = 50; fc.gridy = 999; fc.weightx = 1; fc.weighty = 1;
                raster.add(fueller, fc);
                rueck(huelle, () -> {
                    raster.remove(fueller);
                    for (java.util.Map.Entry<Component, GridBagConstraints> e : karte.entrySet()) gb.setConstraints(e.getKey(), e.getValue());
                    raster.putClientProperty(GESTYLT, null);
                    raster.revalidate();
                });
            }
            // Zellen einzeln – auch solche, die die Helden-Software erst nach dem Einhängen ergänzt
            boolean neu = false;
            for (Component k : raster.getComponents()) {
                if (alt.containsKey(k) || Boolean.TRUE.equals(k instanceof JComponent ? ((JComponent) k).getClientProperty("modernhelden.own") : null)) continue;
                GridBagConstraints c = gb.getConstraints(k);
                alt.put(k, (GridBagConstraints) c.clone());
                c.insets = new Insets(3, c.gridx == 0 ? 4 : 10, 3, 18);
                c.anchor = c.gridx <= 1 ? GridBagConstraints.WEST : GridBagConstraints.CENTER;
                gb.setConstraints(k, c);
                neu = true;
                if (c.gridy == 0 && k instanceof JLabel) {
                    JLabel kl = (JLabel) k;
                    Font f = kl.getFont();
                    Color fg = kl.getForeground();
                    kl.setFont(f.deriveFont(Font.BOLD));
                    kl.setForeground(Theme.muted());
                    rueck(huelle, () -> { kl.setFont(f); kl.setForeground(fg); });
                }
            }
            if (neu) raster.revalidate();
        }
    }


    // ------------------------------------------------------------------ Auswahlbäume (Rasse, Kultur, Profession)

    private static final String BAUM = "modernhelden.erschaffungsBaum";
    private static final Pattern KOSTEN = Pattern.compile("^(.*?)\\s*\\(([^()]*GP)\\)\\s*$");

    private static void baeume(JComponent c) {
        for (JTree t : alle(c, JTree.class)) {
            if (t.getClientProperty(BAUM) != null) continue;
            Object[] orig = {t.getCellRenderer(), t.getRowHeight(), t.isRootVisible(), t.getShowsRootHandles()};
            t.putClientProperty(BAUM, orig);
            t.setCellRenderer(new Eintrag(t.getCellRenderer()));
            t.setRowHeight(26);
            t.setRootVisible(false);
            t.setShowsRootHandles(true);
        }
        for (JTextField f : alle(c, JTextField.class)) {
            if (!f.getClass().getName().endsWith("JTextFieldWithMenu") || f.getClientProperty(BAUM) != null) continue;
            List<JTree> baeume = alle(c, JTree.class);
            // Beim Suchen Gruppen mit Treffern aufklappen, damit z. B. „Frei“ gleich „Freie Kämpferschule …“ zeigt
            java.awt.event.KeyListener aufklappen = new java.awt.event.KeyAdapter() {
                @Override public void keyReleased(java.awt.event.KeyEvent e) {
                    SwingUtilities.invokeLater(() -> {
                        boolean suche = !f.getText().trim().isEmpty();
                        for (JTree t : baeume) {
                            if (suche) {
                                for (int i = 0; i < t.getRowCount() && i < 2000; i++) t.expandRow(i);
                            } else {
                                for (int i = t.getRowCount() - 1; i >= 0; i--)
                                    if (t.getPathForRow(i) != null && t.getPathForRow(i).getPathCount() > 1) t.collapseRow(i);
                                if (t.getSelectionPath() != null) t.scrollPathToVisible(t.getSelectionPath());
                            }
                        }
                    });
                }
            };
            f.addKeyListener(aufklappen);
            f.putClientProperty(BAUM + ".tasten", aufklappen);
            f.putClientProperty(BAUM, Boolean.TRUE);
            f.putClientProperty("JTextField.placeholderText", "Suchen …");
            f.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
        }
    }

    private static void baeumeZurueck(JComponent c) {
        for (JTree t : alle(c, JTree.class)) {
            Object o = t.getClientProperty(BAUM);
            if (!(o instanceof Object[])) continue;
            Object[] orig = (Object[]) o;
            t.setCellRenderer((TreeCellRenderer) orig[0]);
            t.setRowHeight((Integer) orig[1]);
            t.setRootVisible((Boolean) orig[2]);
            t.setShowsRootHandles((Boolean) orig[3]);
            t.putClientProperty(BAUM, null);
        }
        for (JTextField f : alle(c, JTextField.class)) {
            if (f.getClientProperty(BAUM) == null) continue;
            f.putClientProperty("JTextField.placeholderText", null);
            f.putClientProperty("JTextField.leadingIcon", null);
            Object k = f.getClientProperty(BAUM + ".tasten");
            if (k instanceof java.awt.event.KeyListener) f.removeKeyListener((java.awt.event.KeyListener) k);
            f.putClientProperty(BAUM + ".tasten", null);
            f.putClientProperty(BAUM, null);
        }
    }

    private static double helligkeit(Color c) {
        return (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255.0;
    }

    private static double kontrast(Color a, Color b) {
        double x = helligkeit(a) + 0.05, y = helligkeit(b) + 0.05;
        return Math.max(x, y) / Math.min(x, y);
    }

    /** Zeile im Auswahlbaum: Name, daneben die GP-Kosten als Plakette. */
    private static final class Eintrag extends JPanel implements TreeCellRenderer {
        final TreeCellRenderer orig;
        final JLabel name = new JLabel();
        final JLabel kosten = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 2, getWidth(), getHeight() - 4, getHeight() - 4, getHeight() - 4);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        Eintrag(TreeCellRenderer orig) {
            super(new GridBagLayout());
            this.orig = orig;
            setOpaque(true);
            kosten.setBorder(new EmptyBorder(1, 7, 1, 7));
            kosten.setFont(kosten.getFont().deriveFont(Font.BOLD, kosten.getFont().getSize2D() - 1f));
            name.setBorder(new EmptyBorder(0, 4, 0, 8));
            add(name);
            add(kosten);
        }
        int hoehe = 26;
        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width + 4, Math.max(d.height, hoehe));
        }
        @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                                boolean leaf, int row, boolean hasFocus) {
            Object u = value instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode) value).getUserObject() : value;
            String t = u == null ? "" : u.toString();
            Matcher m = KOSTEN.matcher(t);
            String n = t, k = null;
            if (m.matches()) { n = m.group(1); k = m.group(2); }
            boolean gruppe = !leaf;
            hoehe = tree.getRowHeight() > 0 ? tree.getRowHeight() : 26;
            name.setText(n);
            name.setFont(tree.getFont().deriveFont(gruppe ? Font.BOLD : Font.PLAIN));
            boolean aktiv = tree.hasFocus();
            Color hg = sel ? UIManager.getColor(aktiv ? "Tree.selectionBackground" : "Tree.selectionInactiveBackground") : tree.getBackground();
            Color vg = sel ? UIManager.getColor(aktiv ? "Tree.selectionForeground" : "Tree.selectionInactiveForeground") : tree.getForeground();
            if (sel && !aktiv && hg != null && vg != null && kontrast(hg, vg) < 3) vg = tree.getForeground();
            boolean hell = sel && (vg == null || helligkeit(vg) > 0.6);
            if (hg == null) hg = Theme.accent();
            if (vg == null) vg = Color.WHITE;
            setBackground(hg);
            name.setForeground(vg);
            kosten.setVisible(k != null);
            if (k != null) {
                kosten.setText(k);
                Color a = Theme.accent();
                kosten.setBackground(hell ? new Color(255, 255, 255, 60) : ErschaffungsRahmen.mischen(a, hg, 0.16f));
                kosten.setForeground(hell ? vg : a.darker());
                if (Theme.isDark() && !hell) kosten.setForeground(ErschaffungsRahmen.mischen(a, Color.WHITE, 0.7f));
            }
            setToolTipText(null);
            if (orig instanceof JComponent) {
                Component oc = orig.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if (oc instanceof JComponent) setToolTipText(((JComponent) oc).getToolTipText());
            }
            return this;
        }
    }
}
