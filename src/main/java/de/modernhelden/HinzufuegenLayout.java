package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

/**
 * Dialog „Gegenstände zum Inventar hinzufügen“ in den modernen Designs: links Suche, Kategorie und
 * Gegenstandsliste, rechts eine Detailansicht des markierten Gegenstands mit Anzahl und Hinzufügen,
 * darunter das Inventar. Alle Bedienelemente und Aktionen sind die der Helden-Software; das Plugin ordnet
 * sie nur neu an und ergänzt Bedienhilfen (Doppelklick bzw. Ziehen mit Nachfrage geben/kaufen, Anzahl, Rückmeldung).
 * Der Dialog wird bei jedem Öffnen neu aufgebaut – im Design „Original“ bleibt er unverändert.
 */
final class HinzufuegenLayout {

    private HinzufuegenLayout() {}

    static final String TITEL = "Gegenstände zum Inventar hinzufügen";

    /** Läuft gerade ein Ziehen aus der Gegenstandsliste? (Tooltips bleiben dann aus.) */
    static volatile boolean ziehtGerade;

    static void anwenden(JDialog d) {
        if (d.getRootPane().getClientProperty("modernhelden.hinzu") != null) return;
        Container cp = d.getContentPane();
        JPanel e = null;
        for (Component k : cp.getComponents()) if (k instanceof JPanel) e = (JPanel) k;
        if (e == null) return;

        // Bestandteile der Helden-Software suchen
        JList<?> kategorien = null, gegenstaende = null;
        for (JList<?> l : alle(e, JList.class)) {
            if (l.getSelectionMode() == ListSelectionModel.SINGLE_SELECTION) gegenstaende = l; else kategorien = l;
        }
        JTextField suche = null;
        for (JTextField t : alle(e, JTextField.class)) {
            if (SwingUtilities.getAncestorOfClass(JComboBox.class, t) == null && SwingUtilities.getAncestorOfClass(JSpinner.class, t) == null) {
                suche = t;
                break;
            }
        }
        JTable tabelle = InventarLayout.suche(e, JTable.class);
        AbstractButton hinzu = null, edit = null, teilen = null, verbinden = null, ende = null;
        for (AbstractButton b : alle(e, AbstractButton.class)) {
            String t = b.getText();
            if ("Hinzufügen".equals(t)) hinzu = b;
            else if ("Editieren".equals(t)) edit = b;
            else if ("Teilen".equals(t)) teilen = b;
            else if ("Verbinden".equals(t)) verbinden = b;
            else if ("Ende".equals(t)) ende = b;
        }
        if (kategorien == null || gegenstaende == null || suche == null || tabelle == null || hinzu == null || ende == null) return;
        d.getRootPane().putClientProperty("modernhelden.hinzu", Boolean.TRUE);
        new Aufbau(d, e, kategorien, gegenstaende, suche, tabelle, hinzu, edit, teilen, verbinden, ende);
    }

    @SuppressWarnings("unchecked")
    private static <T> java.util.List<T> alle(Container c, Class<T> typ) {
        java.util.List<T> l = new java.util.ArrayList<>();
        for (Component k : c.getComponents()) {
            if (typ.isInstance(k)) l.add((T) k);
            if (k instanceof Container) l.addAll(alle((Container) k, typ));
        }
        return l;
    }

    private static final class Aufbau {
        final JList<?> kategorien, liste;
        final JTextField suche;
        final JTable tabelle;
        final AbstractButton hinzu;
        final JComboBox<Object> kategorie = new JComboBox<>();
        final JLabel name = InventarLayout.titel(" "), angaben = new JLabel(), rueckmeldung = new JLabel(" ");
        final JSpinner anzahl = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
        final JButton los = new JButton("Hinzufügen"), kaufen = new JButton("Kaufen");
        JPanel aktion;
        AbstractButton edit;

        Aufbau(JDialog d, JPanel e, JList<?> kategorien, JList<?> liste, JTextField suche, JTable tabelle,
               AbstractButton hinzu, AbstractButton edit, AbstractButton teilen, AbstractButton verbinden, AbstractButton ende) {
            this.kategorien = kategorien;
            this.liste = liste;
            this.suche = suche;
            this.tabelle = tabelle;
            this.hinzu = hinzu;
            this.edit = edit;

            // ---- links: Suche, Kategorie, Liste
            suche.putClientProperty("JTextField.placeholderText", "Gegenstand suchen …");
            suche.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
            kategorie.addItem("Alle Kategorien");
            ListModel<?> km = kategorien.getModel();
            for (int i = 0; i < km.getSize(); i++) kategorie.addItem(km.getElementAt(i));
            kategorie.setMaximumRowCount(20);
            kategorie.addActionListener(ev -> {
                int i = kategorie.getSelectedIndex();
                if (i <= 0) kategorien.setSelectionInterval(0, kategorien.getModel().getSize() - 1);
                else kategorien.setSelectedIndex(i - 1);
                liste.clearSelection();
                details();
            });
            JPanel suchKopf = new JPanel(new GridLayout(2, 1, 0, 6));
            suchKopf.setOpaque(false);
            suchKopf.add(suche);
            suchKopf.add(kategorie);
            JScrollPane listScroll = new JScrollPane(liste);
            JPanel links = new JPanel(new BorderLayout(0, 8));
            links.setOpaque(false);
            links.add(suchKopf, BorderLayout.NORTH);
            links.add(listScroll, BorderLayout.CENTER);
            links.setPreferredSize(new Dimension(320, 100));

            // ---- rechts oben: Detailansicht mit Anzahl und Hinzufügen
            angaben.setVerticalAlignment(SwingConstants.TOP);
            rueckmeldung.setForeground(Theme.accent());
            InventarLayout.primaer(los, true);
            los.addActionListener(ev -> hinzufuegen());
            kaufen.addActionListener(ev -> kaufen());
            TooltipHook.infoTip(kaufen, "Hinzufügen und den Preis gleich aus der Geldbörse bezahlen");
            anzahl.addChangeListener(ev -> kaufText());
            aktion = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            aktion.setOpaque(false);
            aktion.add(new JLabel("Anzahl"));
            aktion.add(anzahl);
            aktion.add(los);
            aktion.add(kaufen);
            aktion.add(rueckmeldung);
            JPanel karte = new JPanel(new BorderLayout(0, 6));
            karte.setBackground(Theme.card());
            karte.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(10, 12, 10, 12)));
            karte.add(name, BorderLayout.NORTH);
            JScrollPane angabenScroll = new JScrollPane(angaben);
            angabenScroll.setBorder(null);
            angabenScroll.setOpaque(false);
            angabenScroll.getViewport().setOpaque(false);
            karte.add(angabenScroll, BorderLayout.CENTER);
            karte.add(aktion, BorderLayout.SOUTH);
            karte.setPreferredSize(new Dimension(100, 210));

            // ---- rechts unten: Inventar
            InventarLayout.spalten(tabelle, false);
            tabelle.addPropertyChangeListener("model", ev -> SwingUtilities.invokeLater(() -> InventarLayout.spalten(tabelle, false)));
            JLabel invTitel = new JLabel("Im Inventar");
            invTitel.setFont(invTitel.getFont().deriveFont(Font.BOLD));
            JScrollPane tabScroll = new JScrollPane(tabelle);
            JPanel knoepfe = new JPanel(new BorderLayout());
            knoepfe.setOpaque(false);
            JPanel kl = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            kl.setOpaque(false);
            for (AbstractButton b : new AbstractButton[]{edit, teilen, verbinden}) if (b != null) kl.add(b);
            knoepfe.add(kl, BorderLayout.WEST);
            ende.setText("Fertig");
            knoepfe.add(ende, BorderLayout.EAST);
            JPanel inventar = new JPanel(new BorderLayout(0, 6));
            inventar.setOpaque(false);
            inventar.add(invTitel, BorderLayout.NORTH);
            inventar.add(tabScroll, BorderLayout.CENTER);

            JPanel rechts = new JPanel(new BorderLayout(0, 12));
            rechts.setOpaque(false);
            rechts.add(karte, BorderLayout.NORTH);
            rechts.add(inventar, BorderLayout.CENTER);

            // Die Helden-Software bezieht sich weiter auf ihre Komponenten – die Hilfspanels bleiben unsichtbar erhalten
            e.removeAll();
            e.setLayout(new BorderLayout(14, 12));
            e.setBorder(new EmptyBorder(12, 12, 12, 12));
            e.add(links, BorderLayout.WEST);
            e.add(rechts, BorderLayout.CENTER);
            e.add(knoepfe, BorderLayout.SOUTH);
            for (JComponent c : new JComponent[]{suchKopf, links, aktion, karte, angabenScroll, invTitel, knoepfe, kl, inventar, rechts,
                    kategorie, name, angaben, rueckmeldung, anzahl, los, kaufen, listScroll, tabScroll}) InventarLayout.eigen(c);

            // ---- Ziehen und Ablegen: Gegenstand aus der Liste ins Inventar ziehen → geben oder kaufen
            ziehenUndAblegen(tabScroll);

            // ---- Bedienhilfen
            liste.addListSelectionListener(ev -> { if (!ev.getValueIsAdjusting()) details(); });
            // zurück in die Liste: wieder die Vorlage zeigen
            liste.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent ev) { details(); }
            });
            // Klick auf einen Gegenstand im Inventar: dessen Angaben oben zeigen (nicht bei der Markierung durch die Helden-Software)
            tabelle.getSelectionModel().addListSelectionListener(ev -> {
                if (!ev.getValueIsAdjusting() && tabelle.isFocusOwner()) inventarDetails();
            });
            tabelle.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent ev) { SwingUtilities.invokeLater(() -> inventarDetails()); }
            });
            liste.putClientProperty(TooltipHook.DOPPELKLICK_BELEGT, true);
            liste.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent ev) {
                    // Doppelklick: wie beim Ziehen ins Inventar nachfragen (geben oder kaufen)
                    if (ev.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(ev) && liste.getSelectedIndex() >= 0) abgelegt();
                }
            });
            suche.addKeyListener(new KeyAdapter() {
                @Override public void keyReleased(KeyEvent ev) {
                    // nach dem Filtern der Helden-Software (läuft beim Loslassen der Taste) in die Liste wechseln
                    if (ev.getKeyCode() == KeyEvent.VK_DOWN && liste.getModel().getSize() > 0) {
                        liste.requestFocusInWindow();
                        if (liste.getSelectedIndex() < 0) liste.setSelectedIndex(0);
                    }
                }
            });

            Dimension s = d.getSize();
            InventarLayout.groesse(d, Math.max(s.width, 1060), Math.max(s.height, 700));
            e.revalidate();
            e.repaint();
            details();
            SwingUtilities.invokeLater(suche::requestFocusInWindow);
        }

        /** Detailansicht des markierten Gegenstands (Angaben wie im Tooltip). */
        /** Angaben des markierten Gegenstands im Inventar (eigene Werte des Helden, z. B. geänderter Preis). */
        void inventarDetails() {
            int r = tabelle.getSelectedRow();
            if (r < 0 || tabelle.getColumnCount() < 5) return;
            Object g = tabelle.getValueAt(r, tabelle.convertColumnIndexToView(4));
            if (g == null) return;
            rueckmeldung.setText(" ");
            aktion.setVisible(false);
            if (edit != null) edit.setEnabled(true);     // Gegenstand im Inventar: bearbeiten möglich
            Object anz = tabelle.getModel().getValueAt(tabelle.convertRowIndexToModel(r), 0);
            name.setText(VorlagenInfo.name(g));
            Map<String, String> a = VorlagenInfo.angaben(g);
            Map<String, String> m = new java.util.LinkedHashMap<>();
            m.put("Im Inventar", (anz == null ? "1" : anz) + " Stück");
            if (a != null) m.putAll(a);
            tabelleZeigen(m);
        }

        private void tabelleZeigen(Map<String, String> a) {
            StringBuilder sb = new StringBuilder("<html><table cellpadding=1 cellspacing=0>");
            String grau = farbe(Theme.muted());
            for (Map.Entry<String, String> en : a.entrySet()) {
                sb.append("<tr><td valign=top style='color:").append(grau).append(";padding-right:10px'>")
                        .append(esc(en.getKey())).append("</td><td>").append(esc(en.getValue())).append("</td></tr>");
            }
            angaben.setText(sb.append("</table></html>").toString());
        }

        void details() {
            Object v = liste.getSelectedValue();
            rueckmeldung.setText(" ");
            aktion.setVisible(true);
            // Vorlage aus der Liste gewählt: „Editieren“ bezieht sich nur auf Gegenstände im Inventar
            if (edit != null) edit.setEnabled(false);
            if (v == null) {
                name.setText("Kein Gegenstand gewählt");
                angaben.setText("<html><div style='color:" + farbe(Theme.muted()) + "'>Gegenstand links suchen und markieren. "
                        + "Doppelklick oder ins Inventar ziehen fragt: geben oder kaufen.</div></html>");
                los.setEnabled(false);
                anzahl.setEnabled(false);
                kaufText();
                return;
            }
            los.setEnabled(true);
            anzahl.setEnabled(true);
            kaufText();
            name.setText(VorlagenInfo.name(v));
            Map<String, String> a = VorlagenInfo.angaben(v);
            if (a == null || a.isEmpty()) { angaben.setText(""); return; }
            tabelleZeigen(a);
        }

        private boolean ziehtGegenstand;

        private void ziehenUndAblegen(final JScrollPane ziel) {
            final javax.swing.border.Border normal = ziel.getBorder();
            final javax.swing.border.Border markiert = BorderFactory.createLineBorder(Theme.accent(), 2);
            // Rahmen nur bei echtem Wechsel setzen; beim Verlassen kurz warten, ob das Ziehen gleich wieder
            // über einem Teil der Tabelle weitergeht (Kopf, Zeilen, leere Fläche) – sonst flackert es
            final Timer aus = new Timer(150, e -> { if (ziel.getBorder() != normal) ziel.setBorder(normal); });
            aus.setRepeats(false);
            final Runnable markierungAus = () -> { aus.stop(); if (ziel.getBorder() != normal) ziel.setBorder(normal); };
            final Runnable markierungAn = () -> { aus.stop(); if (ziel.getBorder() != markiert) ziel.setBorder(markiert); };
            tabelle.setFillsViewportHeight(true);       // die ganze Fläche gehört zur Tabelle: ein Ablageziel
            liste.setDragEnabled(true);
            liste.setTransferHandler(new TransferHandler() {
                @Override public int getSourceActions(JComponent c) { return COPY; }
                @Override protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
                    Object v = liste.getSelectedValue();
                    if (v == null) return null;
                    ziehtGegenstand = true;
                    ziehtGerade = true;
                    ToolTipManager.sharedInstance().setEnabled(false);   // auch normale Tooltips beim Ziehen aus
                    return new java.awt.datatransfer.StringSelection(VorlagenInfo.name(v));
                }
                @Override protected void exportDone(JComponent source, java.awt.datatransfer.Transferable data, int action) {
                    ziehtGegenstand = false;
                    ziehtGerade = false;
                    ToolTipManager.sharedInstance().setEnabled(true);
                    markierungAus.run();
                }
            });
            TransferHandler annehmen = new TransferHandler() {
                @Override public boolean canImport(TransferSupport t) {
                    boolean ok = ziehtGegenstand && t.isDrop();
                    if (ok) {
                        t.setDropAction(COPY);
                        t.setShowDropLocation(false);   // keine Zeile hervorheben – abgelegt wird ins ganze Inventar
                        markierungAn.run();
                    }
                    return ok;
                }
                @Override public boolean importData(TransferSupport t) {
                    if (!canImport(t)) return false;
                    markierungAus.run();
                    // erst nach dem Ende des Ziehens fragen (Dialog während des Drops würde das Ziehen blockieren)
                    SwingUtilities.invokeLater(Aufbau.this::abgelegt);
                    return true;
                }
            };
            tabelle.setTransferHandler(annehmen);
            tabelle.setDropMode(DropMode.ON);
            ziel.setTransferHandler(annehmen);
            ziel.getViewport().setTransferHandler(annehmen);
            // Markierung entfernen, wenn der Mauszeiger die Tabelle beim Ziehen wieder verlässt
            java.awt.dnd.DropTargetAdapter verlassen = new java.awt.dnd.DropTargetAdapter() {
                @Override public void dragExit(java.awt.dnd.DropTargetEvent e) { aus.restart(); }
                @Override public void drop(java.awt.dnd.DropTargetDropEvent e) { }
            };
            for (JComponent k : new JComponent[]{tabelle, ziel.getViewport()}) {
                try {
                    if (k.getDropTarget() != null) k.getDropTarget().addDropTargetListener(verlassen);
                } catch (java.util.TooManyListenersException ignore) { }
            }
        }

        /** Nach dem Ablegen: Anzahl wählen und geben (ohne Bezahlen) oder kaufen. */
        void abgelegt() {
            Object v = liste.getSelectedValue();
            if (v == null) return;
            String n = VorlagenInfo.name(v);
            long k = VorlagenInfo.preisKreuzer(v);
            JSpinner menge = new JSpinner(new SpinnerNumberModel(((Integer) anzahl.getValue()).intValue(), 1, 99, 1));
            JPanel p = new JPanel(new BorderLayout(0, 8));
            JLabel frage = new JLabel("<html><b>" + esc(n) + "</b> ins Inventar legen."
                    + (k > 0 ? "<br>Soll der Gegenstand bezahlt werden (" + esc(InventarInfo.preisText(k)) + " je Stück) "
                    + "oder bekommt der Held ihn geschenkt?" : "<br>Für diesen Gegenstand ist kein Preis hinterlegt.") + "</html>");
            JPanel mz = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            mz.add(new JLabel("Anzahl"));
            mz.add(menge);
            p.add(frage, BorderLayout.CENTER);
            p.add(mz, BorderLayout.SOUTH);
            String bezahlen = "Kaufen (bezahlen)", geben = "Geben (ohne Bezahlen)", abbrechen = "Abbrechen";
            Object[] wahl = k > 0 && InventarLayout.geldKnopf() != null ? new Object[]{geben, bezahlen, abbrechen} : new Object[]{geben, abbrechen};
            int r = JOptionPane.showOptionDialog(SwingUtilities.getWindowAncestor(liste), p, "Gegenstand hinzufügen",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, wahl, wahl[0]);
            if (r < 0 || abbrechen.equals(wahl[r])) return;
            anzahl.setValue(menge.getValue());
            if (bezahlen.equals(wahl[r])) kaufen(); else hinzufuegen();
        }

        /** „Kaufen (16 D)“ – nur aktiv, wenn der Gegenstand einen Preis hat und die Geldbörse erreichbar ist. */
        void kaufText() {
            Object v = liste.getSelectedValue();
            long k = v == null ? 0 : VorlagenInfo.preisKreuzer(v);
            int n = (Integer) anzahl.getValue();
            if (k > 0) {
                kaufen.setText("Kaufen (" + InventarInfo.preisText(k * n) + ")");
                kaufen.setEnabled(InventarLayout.geldKnopf() != null);
            } else {
                kaufen.setText("Kaufen");
                kaufen.setEnabled(false);
            }
        }

        /** Hinzufügen und den Preis über die Geldbörse der Helden-Software bezahlen. */
        void kaufen() {
            Object v = liste.getSelectedValue();
            long k = v == null ? 0 : VorlagenInfo.preisKreuzer(v);
            AbstractButton geld = InventarLayout.geldKnopf();
            if (k <= 0 || geld == null) return;
            int n = (Integer) anzahl.getValue();
            double silber = k * n / 100.0;
            HeroData h = InventarLayout.held();
            if (h != null && h.geldSilber + 1e-6 < silber) {
                rueckmeldung.setText("Nicht genug Geld: " + InventarInfo.preisText(k * n) + " nötig, in der Geldbörse "
                        + Geld.silber(h.geldSilber) + " S.");
                return;
            }
            String name = VorlagenInfo.name(v);
            int geschafft = hinzufuegen();
            if (geschafft <= 0) return;
            long preis = k * geschafft;
            // Bezahlen über den Geldbörsen-Dialog der Helden-Software (dort wird gewechselt und mit „OK“ übernommen)
            GeldboerseLayout.auftrag(preis / 100.0);
            try {
                geld.doClick();
            } finally {
                GeldboerseLayout.auftrag(0);
            }
            String erg = GeldboerseLayout.ergebnis();
            String was = (geschafft > 1 ? geschafft + "\u00d7 " : "") + name;
            if ("bezahlt".equals(erg)) rueckmeldung.setText("\u2713 " + was + " gekauft (" + InventarInfo.preisText(preis) + " bezahlt)");
            else rueckmeldung.setText(was + " hinzugefügt – Bezahlung bitte in der Geldbörse prüfen");
        }

        /** Markierten Gegenstand Anzahl-mal über die Helden-Software hinzufügen; liefert die Zahl der hinzugefügten. */
        int hinzufuegen() {
            Object v = liste.getSelectedValue();
            if (v == null) { hinzu.doClick(); return 0; }   // Hinweis der Helden-Software
            int n = (Integer) anzahl.getValue(), geschafft = 0;
            for (int i = 0; i < n; i++) {
                int vorher = stueck();
                hinzu.doClick();
                if (stueck() <= vorher) break;     // Maximum erreicht oder Eingabe abgebrochen
                geschafft++;
            }
            rueckmeldung.setText(geschafft > 0 ? "✓ " + (geschafft > 1 ? geschafft + "× " : "") + VorlagenInfo.name(v) + " hinzugefügt" : " ");
            anzahl.setValue(1);
            // Hervorhebung im Inventar sichtbar machen
            int r = tabelle.getSelectedRow();
            if (r >= 0) tabelle.scrollRectToVisible(tabelle.getCellRect(r, 0, true));
            return geschafft;
        }

        private int stueck() {
            TableModel m = tabelle.getModel();
            int s = 0;
            for (int r = 0; r < m.getRowCount(); r++) {
                Object a = m.getValueAt(r, 0);
                if (a instanceof Number) s += ((Number) a).intValue();
            }
            return s + m.getRowCount() * 100000;   // auch neue Einträge mit Anzahl 0 zählen
        }
    }

    static String farbe(Color c) { return String.format("#%06x", c.getRGB() & 0xffffff); }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
