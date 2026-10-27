package de.modernhelden;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.*;
import java.util.List;

/**
 * Reiter „Inventar / Tiere“ in den modernen Designs: dieselben Bedienelemente der Helden-Software,
 * neu angeordnet – Kopfzeile mit Übersicht (Anzahl, Gewicht, Geldbörse), Suche, Filter nach Art,
 * Gruppierung nach Art, schmale +/−/X-Spalten und Doppelklick zum Bearbeiten.
 *
 * Es werden nur vorhandene Komponenten umgehängt und Darstellungsdetails gesetzt; alle Aktionen
 * (Hinzufügen, +, −, Löschen, Editieren …) laufen weiter über die Helden-Software. Im Design „Original“
 * wird das ursprüngliche Layout mit denselben Komponenten und Einstellungen wiederhergestellt.
 */
final class InventarLayout {

    private InventarLayout() {}

    private static final String MARKE = "modernhelden.inventar";
    /** Tabelle: Spalte, nach der gruppiert wird (wird von {@link TabellenStil.Zebra} ausgewertet). */
    static final String GRUPPE = "modernhelden.gruppenSpalte";

    private static final Map<JComponent, Zustand> alle = new WeakHashMap<>();
    private static HeroData held;

    /** Neue Heldendaten (für die Geldbörse in der Übersicht). */
    static void setHero(HeroData h) {
        boolean anderer = h == null || held == null || !java.util.Objects.equals(h.name, held.name);
        held = h;
        for (Zustand z : alle.values()) {
            // beim Wechsel zu einem anderen Helden Suche und Filter zurücksetzen
            if (anderer && z.aktiv) {
                z.suchfeld.setText("");
                if (z.artWahl.getItemCount() > 0) z.artWahl.setSelectedIndex(0);
            }
            if (z.aktiv) z.uebersicht();
        }
    }

    static HeroData held() { return held; }

    /** Gegenstand der Helden-Software (aus der Inventartabelle) mit diesem Namen, sonst null. */
    static Object gegenstand(String name) {
        for (Zustand z : alle.values()) {
            javax.swing.table.TableModel m = z.tabelle.getModel();
            if (m.getColumnCount() < 5) continue;
            for (int r = 0; r < m.getRowCount(); r++)
                if (name.equals(String.valueOf(m.getValueAt(r, 1)).trim())) return m.getValueAt(r, 4);
        }
        return null;
    }

    /** Knopf „Geldbörse“ des Reiters „Inventar / Tiere“ (in den modernen Designs), sonst null. */
    static AbstractButton geldKnopf() {
        for (Zustand z : alle.values()) if (z.aktiv && z.geld.isShowing()) return z.geld;
        for (Zustand z : alle.values()) if (z.aktiv) return z.geld;
        return null;
    }

    /** Wird für jede Komponente der Helden-Software aufgerufen (siehe {@link IconSwap}). */
    static void pruefen(JComponent c, boolean modern) {
        Object o = c.getClientProperty(MARKE);
        Zustand z = o instanceof Zustand ? (Zustand) o : null;
        if (z == null) {
            if (!modern || !(c instanceof JPanel) || !(c.getLayout() instanceof GridBagLayout)) return;
            z = Zustand.erkennen((JPanel) c);
            if (z == null) return;
            c.putClientProperty(MARKE, z);
            alle.put(c, z);
        }
        if (modern) z.modern(); else z.original();
    }

    // ------------------------------------------------------------------ Gemeinsame Helfer (auch für den Hinzufügen-Dialog)

    static AbstractButton knopf(Container c, String text) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton && text.equals(((AbstractButton) k).getText())) return (AbstractButton) k;
        }
        return null;
    }

    static <T extends Component> T suche(Container c, Class<T> typ) {
        for (Component k : c.getComponents()) {
            if (typ.isInstance(k)) return typ.cast(k);
            if (k instanceof Container) {
                T t = suche((Container) k, typ);
                if (t != null) return t;
            }
        }
        return null;
    }

    /** Ist das die Inventartabelle der Helden-Software (Anzahl, Name, Art, Gewicht, +, -, X)? */
    static boolean istInventarTabelle(JTable t) {
        TableModel m = t.getModel();
        return m.getColumnCount() == 7 && "Anzahl".equals(m.getColumnName(0)) && "Name".equals(m.getColumnName(1))
                && "Art".equals(m.getColumnName(2)) && "Gewicht".equals(m.getColumnName(3)) && "X".equals(m.getColumnName(6));
    }

    /**
     * Hinweis zu einem ausgegrauten „−“ bzw. „✕“ in der Inventartabelle, sonst null.
     * „✕“ sperrt die Helden-Software bei ausgerüsteten Gegenständen, „−“ bei nur noch einem Stück.
     */
    static String sperrHinweis(JTable t, int row, int col) {
        if (row < 0 || col < 0 || !istInventarTabelle(t)) return null;
        int mc = t.convertColumnIndexToModel(col);
        if (mc != 5 && mc != 6) return null;
        if (zelleAktiv(t, row, col)) return null;
        int xCol = t.convertColumnIndexToView(6);
        boolean ausgeruestet = mc == 6 || (xCol >= 0 && !zelleAktiv(t, row, xCol));
        if (ausgeruestet)
            return "<html>Dieser Gegenstand ist ausgerüstet und muss erst abgelegt werden<br>"
                    + "(Reiter „Ausrüstung“), bevor er " + (mc == 6 ? "gelöscht" : "verringert") + " werden kann.</html>";
        return "<html>Nur noch ein Stück vorhanden –<br>zum Entfernen „✕“ benutzen.</html>";
    }

    /** Hinweise an den Knöpfen „−“/„✕“ wieder entfernen (Design „Original“). */
    static void hinweiseEntfernen(JTable t) {
        if (!istInventarTabelle(t)) return;
        for (int row = 0; row < t.getRowCount(); row++) {
            for (int mc = 4; mc < 7; mc++) {
                int col = t.convertColumnIndexToView(mc);
                if (col < 0) continue;
                javax.swing.table.TableCellRenderer r = t.getColumnModel().getColumn(col).getCellRenderer();
                if (r instanceof TabellenStil.Zebra) r = ((TabellenStil.Zebra) r).original;
                if (r == null) continue;
                try {
                    Component c = r.getTableCellRendererComponent(t, t.getValueAt(row, col), false, false, row, col);
                    if (c instanceof JComponent) ((JComponent) c).setToolTipText(null);
                } catch (RuntimeException ignore) { }
            }
        }
    }

    /** Ist der Knopf in dieser Zelle (laut Renderer der Helden-Software) aktiv? */
    private static boolean zelleAktiv(JTable t, int row, int col) {
        javax.swing.table.TableCellRenderer r = t.getColumnModel().getColumn(col).getCellRenderer();
        if (r instanceof TabellenStil.Zebra) r = ((TabellenStil.Zebra) r).original;
        if (r == null) return true;
        try {
            Component c = r.getTableCellRendererComponent(t, t.getValueAt(row, col), false, false, row, col);
            return !(c instanceof AbstractButton) || c.isEnabled();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Spaltenbreiten der Inventartabelle: schmale Knopfspalten, mehr Platz für den Namen. */
    static void spalten(JTable t, boolean breit) {
        TableColumnModel cm = t.getColumnModel();
        if (cm.getColumnCount() != 7) return;
        int[] pref = breit ? new int[]{60, 340, 150, 90, 34, 34, 34} : new int[]{40, 220, 110, 60, 30, 30, 30};
        for (int i = 0; i < 7; i++) {
            javax.swing.table.TableColumn col = cm.getColumn(i);
            if (i >= 4) {
                col.setMinWidth(26);
                col.setMaxWidth(40);
            } else if (i == 0 || i == 3) {
                col.setMaxWidth(i == 0 ? 80 : 120);
            }
            col.setPreferredWidth(pref[i]);
        }
        cm.getColumn(5).setHeaderValue("−");   // „−“ statt Bindestrich
        cm.getColumn(6).setHeaderValue("✕");
    }

    /** Primärknopf (Akzentfarbe) bzw. wieder normal. */
    static void primaer(AbstractButton b, boolean an) {
        b.putClientProperty("FlatLaf.style", an
                ? "background: $Component.accentColor; foreground: #ffffff; borderColor: $Component.accentColor; focusedBorderColor: #ffffff; font: bold"
                : null);
    }

    /**
     * Dialoggröße setzen, aber nie größer als der nutzbare Bildschirmbereich (ohne Taskleiste) –
     * sonst lägen OK/Abbrechen bei kleinen oder skalierten Bildschirmen außerhalb.
     */
    static void groesse(Window d, int w, int h) {
        GraphicsConfiguration gc = d.getOwner() != null ? d.getOwner().getGraphicsConfiguration() : d.getGraphicsConfiguration();
        if (gc != null) {
            Rectangle b = gc.getBounds();
            Insets i = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            w = Math.min(w, b.width - i.left - i.right - 20);
            h = Math.min(h, b.height - i.top - i.bottom - 20);
        }
        d.setSize(w, h);
        d.setLocationRelativeTo(d.getOwner());
        // nach dem Zentrieren noch vollständig im nutzbaren Bereich halten
        if (gc != null) {
            Rectangle b = gc.getBounds();
            Insets i = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int x = Math.max(b.x + i.left, Math.min(d.getX(), b.x + b.width - i.right - d.getWidth()));
            int y = Math.max(b.y + i.top, Math.min(d.getY(), b.y + b.height - i.bottom - d.getHeight()));
            d.setLocation(x, y);
        }
    }

    static JLabel titel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() + 4f));
        return l;
    }

    static JComponent eigen(JComponent c) {
        c.putClientProperty("modernhelden.own", true);
        return c;
    }

    static String zahl(double v) { return InventarInfo.zahl(v); }

    // ------------------------------------------------------------------ Zustand je Reiter

    private static final class Zustand {
        final JPanel panel;
        final JTable tabelle;
        final JScrollPane scroll;
        final AbstractButton hinzu, geld, edit, teilen, verbinden, export;
        final JLabel alterTitel;

        // Original
        final LayoutManager origLayout;
        final List<Component> origKinder = new ArrayList<>();
        final List<Object> origConstraints = new ArrayList<>();
        Border origRand;
        boolean origVpOpak;
        String origHinzuText;
        boolean origOpaque;
        final Map<AbstractButton, ActionListener[]> origListener = new LinkedHashMap<>();

        // Modern
        boolean aktiv;
        final JTextField suchfeld = new JTextField();
        final JComboBox<String> artWahl = new JComboBox<>();
        final JCheckBox gruppieren = new JCheckBox("Nach Art gruppieren", true);
        /** Übersicht (Einträge, Gewicht) – bricht im kleinen Fenster um statt abgeschnitten zu werden. */
        final Umbruchtext info = new Umbruchtext("");
        JPanel inhalt;
        TableModel letztesModell;
        int[] origBreiten, origMin, origMax;
        Object[] origKoepfe;
        final TableModelListener modellHorcher = e -> SwingUtilities.invokeLater(this::nachAenderung);
        boolean artWahlFuellen, gruppenSync;
        /** Zuletzt gewählte Sortierung (bleibt beim Aktualisieren durch die Helden-Software erhalten). */
        List<? extends RowSorter.SortKey> sortierung;

        private Zustand(JPanel p, JTable t, JScrollPane s, AbstractButton hinzu, AbstractButton geld, AbstractButton edit,
                        AbstractButton teilen, AbstractButton verbinden, AbstractButton export, JLabel titel) {
            panel = p; tabelle = t; scroll = s;
            this.hinzu = hinzu; this.geld = geld; this.edit = edit; this.teilen = teilen; this.verbinden = verbinden; this.export = export;
            alterTitel = titel;
            origLayout = p.getLayout();
            GridBagLayout gb = (GridBagLayout) origLayout;
            for (Component k : p.getComponents()) {
                origKinder.add(k);
                origConstraints.add(gb.getConstraints(k));
            }
            origRand = p.getBorder();
            origOpaque = p.isOpaque();
            origHinzuText = hinzu.getText();
            // bei jedem Aktualisieren setzt die Helden-Software ein neues Tabellenmodell
            t.addPropertyChangeListener("model", e -> {
                if (!aktiv) return;
                // alte Sortierung gehört zum alten Modell – sofort lösen, gleich darauf für das neue Modell einrichten
                RowSorter<?> rs = tabelle.getRowSorter();
                if (rs != null && rs.getModel() != tabelle.getModel()) tabelle.setRowSorter(null);
                SwingUtilities.invokeLater(this::tabelle);
            });
            // nur im modernen Design belegt – im Original bleibt der Doppelklick wie bisher
            t.putClientProperty(TooltipHook.DOPPELKLICK_BELEGT, (java.util.function.BooleanSupplier) () -> aktiv);
            t.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    // Doppelklick auf Anzahl/Name/Art/Gewicht: Gegenstand bearbeiten
                    if (!aktiv || e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                    int col = tabelle.columnAtPoint(e.getPoint());
                    if (col >= 0 && tabelle.convertColumnIndexToModel(col) < 4 && tabelle.getSelectedRow() >= 0 && edit.isEnabled())
                        edit.doClick();
                }
            });
            suchfeld.putClientProperty("JTextField.placeholderText", "Im Inventar suchen …");
            suchfeld.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
            suchfeld.putClientProperty("JTextField.showClearButton", true);
            suchfeld.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { filtern(); }
                public void removeUpdate(DocumentEvent e) { filtern(); }
                public void changedUpdate(DocumentEvent e) { filtern(); }
            });
            artWahl.addActionListener(e -> { if (!artWahlFuellen) filtern(); });
            gruppieren.setOpaque(false);
            gruppieren.addActionListener(e -> {
                if (gruppenSync) return;
                sortierung = null;          // Standardsortierung (mit bzw. ohne Gruppen) neu setzen
                if (tabelle.getRowSorter() != null) tabelle.setRowSorter(null);
                tabelle();
            });
            for (JComponent c : new JComponent[]{suchfeld, artWahl, gruppieren, info}) eigen(c);
        }

        static Zustand erkennen(JPanel p) {
            AbstractButton hinzu = knopf(p, "Hinzufügen"), geld = knopf(p, "Geldbörse"), edit = knopf(p, "Editieren"),
                    teilen = knopf(p, "Teilen"), verbinden = knopf(p, "Verbinden"), export = knopf(p, "Exportieren");
            if (hinzu == null || geld == null || edit == null || teilen == null || verbinden == null || export == null) return null;
            JScrollPane s = null;
            JLabel titel = null;
            for (Component k : p.getComponents()) {
                if (k instanceof JScrollPane) s = (JScrollPane) k;
                if (k instanceof JLabel) titel = (JLabel) k;
            }
            if (s == null || !(s.getViewport().getView() instanceof JTable)) return null;
            JTable t = (JTable) s.getViewport().getView();
            if (!istInventarTabelle(t)) return null;
            return new Zustand(p, t, s, hinzu, geld, edit, teilen, verbinden, export, titel);
        }

        void modern() {
            if (!aktiv) {
                aktiv = true;
                panel.removeAll();
                panel.setLayout(new BorderLayout(0, 8));
                panel.setBorder(new EmptyBorder(10, 12, 8, 12));

                // Kopf: Titel + Übersicht links, Geldbörse und Hinzufügen rechts
                JPanel links = new JPanel();
                links.setLayout(new BoxLayout(links, BoxLayout.Y_AXIS));
                links.setOpaque(false);
                JLabel t = titel("Inventar");
                t.setAlignmentX(0);
                info.setAlignmentX(0);
                info.setForeground(Theme.muted());
                info.setFont(UIManager.getFont("Label.font"));
                links.add(t);
                links.add(Box.createVerticalStrut(2));
                links.add(info);
                JPanel rechts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                rechts.setOpaque(false);
                hinzu.setText("Gegenstand hinzufügen …");
                primaer(hinzu, true);
                rechts.add(geld);
                rechts.add(hinzu);
                JPanel kopf = new JPanel(new BorderLayout(12, 0));
                kopf.setOpaque(false);
                kopf.add(links, BorderLayout.CENTER);
                kopf.add(rechts, BorderLayout.EAST);

                JPanel filter = new JPanel(new BorderLayout(8, 0));
                filter.setOpaque(false);
                filter.add(suchfeld, BorderLayout.CENTER);
                JPanel f2 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                f2.setOpaque(false);
                f2.add(artWahl);
                f2.add(gruppieren);
                filter.add(f2, BorderLayout.EAST);

                JPanel nord = new JPanel(new BorderLayout(0, 8));
                nord.setOpaque(false);
                nord.add(kopf, BorderLayout.NORTH);
                nord.add(filter, BorderLayout.SOUTH);

                JPanel sued = new JPanel(new BorderLayout());
                sued.setOpaque(false);
                JPanel aktionen = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                aktionen.setOpaque(false);
                aktionen.add(edit);
                aktionen.add(teilen);
                aktionen.add(verbinden);
                JLabel tipp = new JLabel("Doppelklick auf eine Zeile: bearbeiten");
                tipp.setForeground(Theme.muted());
                aktionen.add(Box.createHorizontalStrut(8));
                aktionen.add(tipp);
                sued.add(aktionen, BorderLayout.WEST);
                JPanel ex = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                ex.setOpaque(false);
                ex.add(export);
                sued.add(ex, BorderLayout.EAST);

                // eigene, deckende Fläche (weiß bzw. grau je nach Design) über dem Hintergrundbild der Helden-Software
                JPanel flaeche = new JPanel(new BorderLayout(0, 8));
                flaeche.setOpaque(true);
                flaeche.setBackground(UIManager.getColor("Panel.background"));
                flaeche.setBorder(new EmptyBorder(10, 12, 8, 12));
                panel.setBorder(null);
                panel.setLayout(new BorderLayout());
                for (JComponent c : new JComponent[]{links, rechts, kopf, filter, f2, nord, sued, aktionen, ex, tipp, t, flaeche}) eigen(c);
                flaeche.add(nord, BorderLayout.NORTH);
                flaeche.add(scroll, BorderLayout.CENTER);
                flaeche.add(sued, BorderLayout.SOUTH);
                panel.add(flaeche, BorderLayout.CENTER);
                origVpOpak = scroll.getViewport().isOpaque();
                scroll.getViewport().setOpaque(true);
                scroll.getViewport().setBackground(UIManager.getColor("Table.background"));

                // eigene Felder waren im Design „Original“ nicht eingehängt – an das aktuelle Design anpassen
                for (JComponent c : new JComponent[]{suchfeld, artWahl, gruppieren, info}) SwingUtilities.updateComponentTreeUI(c);
                info.setForeground(Theme.muted());
                info.setFont(UIManager.getFont("Label.font"));
                tipp.setForeground(Theme.muted());
                // Aktionen, die die markierte Zeile als Index ins Modell nehmen, auf die sortierte Ansicht umleiten
                umleiten(edit);
                umleiten(export);
                tabelle();
                panel.revalidate();
                panel.repaint();
            }
        }

        void original() {
            if (!aktiv) return;
            aktiv = false;
            panel.removeAll();
            panel.setLayout(origLayout);
            for (int i = 0; i < origKinder.size(); i++) panel.add(origKinder.get(i), origConstraints.get(i));
            panel.setBorder(origRand);
            panel.setOpaque(origOpaque);
            scroll.getViewport().setOpaque(origVpOpak);
            hinzu.setText(origHinzuText);
            primaer(hinzu, false);
            for (Map.Entry<AbstractButton, ActionListener[]> e : origListener.entrySet()) {
                AbstractButton b = e.getKey();
                for (ActionListener l : b.getActionListeners()) b.removeActionListener(l);
                for (ActionListener l : e.getValue()) b.addActionListener(l);
            }
            origListener.clear();
            tabelle.setRowSorter(null);
            tabelle.putClientProperty(GRUPPE, null);
            spaltenZurueck();
            hinweiseEntfernen(tabelle);
            panel.revalidate();
            panel.repaint();
        }

        /**
         * Editieren und Exportieren der Helden-Software lesen die markierte Zeile direkt aus dem Tabellenmodell.
         * Bei sortierter oder gefilterter Ansicht wird deshalb kurz die unsortierte Ansicht mit derselben
         * Auswahl hergestellt und dann die Aktion der Helden-Software ausgeführt.
         */
        private void umleiten(AbstractButton b) {
            final ActionListener[] orig = b.getActionListeners();
            origListener.put(b, orig);
            for (ActionListener l : orig) b.removeActionListener(l);
            b.addActionListener(e -> {
                RowSorter<?> rs = tabelle.getRowSorter();
                if (rs == null) { for (ActionListener l : orig) l.actionPerformed(e); return; }
                int[] sicht = tabelle.getSelectedRows();
                int[] modell = new int[sicht.length];
                for (int i = 0; i < sicht.length; i++) modell[i] = tabelle.convertRowIndexToModel(sicht[i]);
                tabelle.setRowSorter(null);
                ListSelectionModel sm = tabelle.getSelectionModel();
                sm.setValueIsAdjusting(true);
                sm.clearSelection();
                for (int r : modell) sm.addSelectionInterval(r, r);
                sm.setValueIsAdjusting(false);
                try {
                    for (ActionListener l : orig) l.actionPerformed(new ActionEvent(b, e.getID(), e.getActionCommand(), e.getWhen(), e.getModifiers()));
                } finally {
                    if (aktiv) {
                        tabelle();
                        // Auswahl in der sortierten Ansicht wiederherstellen (falls das Modell noch dasselbe ist)
                        if (tabelle.getRowSorter() != null && tabelle.getModel().getRowCount() > 0) {
                            sm.clearSelection();
                            for (int r : modell) {
                                if (r >= tabelle.getModel().getRowCount()) continue;
                                int v = tabelle.convertRowIndexToView(r);
                                if (v >= 0) sm.addSelectionInterval(v, v);
                            }
                        }
                    }
                }
            });
        }

        /** Tabelle für das aktuelle Modell einrichten (nach jedem Aktualisieren durch die Helden-Software). */
        void tabelle() {
            if (!aktiv || !istInventarTabelle(tabelle)) return;
            TableModel m = tabelle.getModel();
            if (m != letztesModell) {
                if (letztesModell != null) letztesModell.removeTableModelListener(modellHorcher);
                letztesModell = m;
                m.addTableModelListener(modellHorcher);
                TableColumnModel cm = tabelle.getColumnModel();
                origBreiten = new int[7]; origMin = new int[7]; origMax = new int[7]; origKoepfe = new Object[7];
                for (int i = 0; i < 7; i++) {
                    origBreiten[i] = cm.getColumn(i).getPreferredWidth();
                    origMin[i] = cm.getColumn(i).getMinWidth();
                    origMax[i] = cm.getColumn(i).getMaxWidth();
                    origKoepfe[i] = cm.getColumn(i).getHeaderValue();
                }
            }
            spalten(tabelle, true);
            artWahlAktualisieren();
            RowSorter<?> alt = tabelle.getRowSorter();
            if (alt != null && alt.getModel() == m) {     // schon eingerichtet: nur Filter und Anzeige auffrischen
                filtern();
                gruppenAnzeige();
                return;
            }
            TableRowSorter<TableModel> s = new TableRowSorter<>(m);
            final Collator col = Collator.getInstance(Locale.GERMAN);
            Comparator<Object> text = (a, b) -> col.compare(String.valueOf(a), String.valueOf(b));
            s.setComparator(1, text);
            s.setComparator(2, text);
            for (int i = 4; i < 7; i++) s.setSortable(i, false);
            List<RowSorter.SortKey> keys = new ArrayList<>();
            if (gruppieren.isSelected()) keys.add(new RowSorter.SortKey(2, SortOrder.ASCENDING));
            keys.add(new RowSorter.SortKey(1, SortOrder.ASCENDING));
            s.setSortKeys(sortierung != null ? sortierung : keys);
            s.setSortsOnUpdates(true);
            s.setRowFilter(rowFilter());
            // Klick auf einen Spaltenkopf: Sortierung merken; gruppiert ist nur, solange zuerst nach Art sortiert wird
            s.addRowSorterListener(ev -> {
                if (ev.getType() != javax.swing.event.RowSorterEvent.Type.SORT_ORDER_CHANGED) return;
                // beim Modellwechsel der Helden-Software setzt der alte Sortierer seine Sortierung zurück – nicht merken
                if (tabelle.getRowSorter() != s || tabelle.getModel() != s.getModel()) return;
                sortierung = new ArrayList<>(s.getSortKeys());
                gruppenAnzeige();
            });
            tabelle.setRowSorter(s);
            gruppenAnzeige();
            uebersicht();
            tabelle.repaint();
        }

        /** Trennlinien nur, wenn zuerst nach Art sortiert ist; der Haken „Nach Art gruppieren“ folgt der Sortierung. */
        private void gruppenAnzeige() {
            RowSorter<?> rs = tabelle.getRowSorter();
            if (rs == null) return;
            List<? extends RowSorter.SortKey> k = rs == null ? Collections.<RowSorter.SortKey>emptyList() : rs.getSortKeys();
            boolean gruppiert = !k.isEmpty() && k.get(0).getColumn() == 2;
            tabelle.putClientProperty(GRUPPE, gruppiert ? 2 : null);
            if (gruppieren.isSelected() != gruppiert) {
                gruppenSync = true;
                gruppieren.setSelected(gruppiert);
                gruppenSync = false;
            }
            tabelle.repaint();
        }

        private void spaltenZurueck() {
            TableColumnModel cm = tabelle.getColumnModel();
            if (origBreiten == null || cm.getColumnCount() != 7 || tabelle.getModel() != letztesModell) return;
            for (int i = 0; i < 7; i++) {
                javax.swing.table.TableColumn c = cm.getColumn(i);
                c.setMinWidth(origMin[i]);
                c.setMaxWidth(origMax[i]);
                c.setPreferredWidth(origBreiten[i]);
                c.setHeaderValue(origKoepfe[i]);
            }
            tabelle.getTableHeader().repaint();
        }

        private RowFilter<TableModel, Integer> rowFilter() {
            final String q = suchfeld.getText().trim().toLowerCase(Locale.GERMAN);
            final Object art = artWahl.getSelectedIndex() > 0 ? artWahl.getSelectedItem() : null;
            if (q.isEmpty() && art == null) return null;
            return new RowFilter<TableModel, Integer>() {
                @Override public boolean include(Entry<? extends TableModel, ? extends Integer> e) {
                    String name = String.valueOf(e.getValue(1)), a = String.valueOf(e.getValue(2));
                    if (art != null && !art.equals(a)) return false;
                    return q.isEmpty() || (name + " " + a).toLowerCase(Locale.GERMAN).contains(q);
                }
            };
        }

        void filtern() {
            RowSorter<?> rs = tabelle.getRowSorter();
            if (aktiv && rs instanceof TableRowSorter) {
                @SuppressWarnings("unchecked") TableRowSorter<TableModel> s = (TableRowSorter<TableModel>) rs;
                s.setRowFilter(rowFilter());
                uebersicht();
            }
        }

        /** Auswahl „Alle Arten“ + alle Arten, die im Inventar vorkommen. */
        private void artWahlAktualisieren() {
            TableModel m = tabelle.getModel();
            TreeSet<String> arten = new TreeSet<>(Collator.getInstance(Locale.GERMAN));
            for (int r = 0; r < m.getRowCount(); r++) arten.add(String.valueOf(m.getValueAt(r, 2)));
            Object alt = artWahl.getSelectedIndex() > 0 ? artWahl.getSelectedItem() : null;
            artWahlFuellen = true;
            artWahl.removeAllItems();
            artWahl.addItem("Alle Arten");
            for (String a : arten) artWahl.addItem(a);
            if (alt != null && arten.contains(alt)) artWahl.setSelectedItem(alt); else artWahl.setSelectedIndex(0);
            artWahlFuellen = false;
        }

        private void nachAenderung() {
            if (!aktiv) return;
            // neue Art hinzugekommen? (z. B. nach +/X)
            TableModel m = tabelle.getModel();
            Set<String> da = new HashSet<>();
            for (int i = 1; i < artWahl.getItemCount(); i++) da.add(artWahl.getItemAt(i));
            Set<String> jetzt = new HashSet<>();
            for (int r = 0; r < m.getRowCount(); r++) jetzt.add(String.valueOf(m.getValueAt(r, 2)));
            if (!da.equals(jetzt)) artWahlAktualisieren();
            uebersicht();
        }

        /** „16 Einträge · 25 Stück · 61 Stein (2440 Unzen) · Geldbörse: …“ */
        void uebersicht() {
            if (!aktiv) return;
            TableModel m = tabelle.getModel();
            int eintraege = m.getRowCount(), stueck = 0;
            double unzen = 0;
            for (int r = 0; r < eintraege; r++) {
                Object a = m.getValueAt(r, 0), g = m.getValueAt(r, 3);
                if (a instanceof Number) stueck += ((Number) a).intValue();
                if (g instanceof Number) unzen += ((Number) g).doubleValue();
            }
            List<String> t = new ArrayList<>();
            t.add(eintraege + (eintraege == 1 ? " Eintrag" : " Einträge") + (stueck != eintraege ? " (" + stueck + " Stück)" : ""));
            t.add("Gewicht " + zahl(unzen / 40.0) + " Stein (" + zahl(unzen) + " Unzen)");
            if (held != null && !held.geld.isEmpty()) t.add("Geldbörse: " + held.geld);
            int sichtbar = tabelle.getRowCount();
            if (sichtbar != eintraege) t.add(sichtbar + " angezeigt");
            info.setText(String.join("  ·  ", t));
            TooltipHook.infoTip(info, String.join("  ·  ", t));   // vollständig, falls das Fenster schmal ist
        }
    }
}
