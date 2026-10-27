package de.modernhelden;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.text.Collator;
import java.util.*;
import java.util.List;

/**
 * Reiter „Talente“ und „Zauber“ in den modernen Designs: Klick auf einen Spaltenkopf sortiert die Tabelle (Art, Talent, Wert,
 * Lernmethode, Kategorie, Kosten …), ein weiterer Klick kehrt die Reihenfolge um, ein dritter stellt die
 * ursprüngliche Reihenfolge wieder her. Die Helden-Software greift beim Steigern über den Knopf bzw. über die
 * Tabelle selbst auf die Zeilen zu – das funktioniert auch sortiert. Im Design „Original“ wird nicht sortiert.
 */
public final class TalenteSortierung {

    private TalenteSortierung() {}

    private static final String MARKE = "modernhelden.talentSortierung";
    /** Tabellen mit ihren Spalten; Spalten mit Knöpfen („Steigern“, „Info“) werden nicht sortiert. */
    private static final String[][] TABELLEN = {
            {"SE", "Art", "Talent", "Wert", "Lernmethode", "Kat.", "Kosten", "Steigern"},
            {"SE", "Zauberfertigkeit", "Komp", "Rep", "Merkmale", "Haus", "Wert", "Steigern", "Lernmethode", "Kosten",
                    "Info", "Quelle", "Lern"}};
    private static final Set<String> KNOEPFE = new HashSet<>(Arrays.asList("Steigern", "Info"));

    static boolean istTalentTabelle(JTable t) { return spalten(t) != null; }

    private static String[] spalten(JTable t) {
        TableModel m = t.getModel();
        naechste:
        for (String[] sp : TABELLEN) {
            if (m.getColumnCount() != sp.length) continue;
            for (int i = 0; i < sp.length; i++) if (!sp[i].equals(m.getColumnName(i))) continue naechste;
            return sp;
        }
        return null;
    }

    static void pruefen(JTable t, boolean modern) {
        Zustand z = (Zustand) t.getClientProperty(MARKE);
        if (z == null) {
            if (!modern || !istTalentTabelle(t)) return;
            z = new Zustand(t);
            t.putClientProperty(MARKE, z);
        }
        if (modern) z.an(); else z.aus();
    }

    private static final class Zustand {
        final JTable t;
        boolean aktiv;
        List<? extends RowSorter.SortKey> sortierung = Collections.emptyList();
        /** Suche und ausgewählte Talentgruppen (leer = alle) – bleiben beim Heldenwechsel erhalten. */
        String suche = "";
        final Set<String> gruppen = new LinkedHashSet<>();
        TableRowSorter<TableModel> sorter;
        Suchleiste leiste;

        Zustand(JTable t) {
            this.t = t;
            // die Helden-Software setzt beim Heldenwechsel ein neues Modell – Sortierung dafür neu einrichten
            t.addPropertyChangeListener("model", e -> {
                if (!aktiv) return;
                RowSorter<?> rs = t.getRowSorter();
                if (rs != null && rs.getModel() != t.getModel()) t.setRowSorter(null);
                SwingUtilities.invokeLater(this::einrichten);
            });
        }

        void an() {
            if (aktiv) {
                // Designwechsel zwischen hell und dunkel: Schalterfarben neu
                if (leiste != null && !Theme.current().equals(leiste.thema)) leiste.gruppenNeu();
                return;
            }
            aktiv = true;
            einrichten();
            if (leiste == null) leiste = new Suchleiste(this);
            leiste.einbauen();
        }

        // ---- Spaltenbreiten: lesbar auch im kleinen Fenster (sonst „Kost…“, „LCD: …“)

        /** Sinnvolle Breiten je Spalte (Pixel bei Schriftgröße 13, werden mit der Schrift skaliert). */
        private final Map<String, Integer> BREITEN = new HashMap<String, Integer>() {{
            put("SE", 30); put("Art", 90); put("Talent", 210); put("Wert", 45); put("Lernmethode", 160);
            put("Kat.", 40); put("Kosten", 60); put("Steigern", 70);
            put("Zauberfertigkeit", 230); put("Komp", 50); put("Rep", 45); put("Merkmale", 120); put("Haus", 45);
            put("Info", 40); put("Quelle", 80); put("Lern", 45);
        }};
        Integer altModus;
        java.awt.event.ComponentListener vpHoerer;
        JViewport vp;

        /**
         * Breiten setzen; passt die Tabelle nicht ins Fenster, wird sie waagerecht rollbar statt die Spalten
         * zu stauchen. Im großen Fenster verteilt die Tabelle den Platz wie gewohnt.
         */
        void breiten() {
            float f = t.getFont() == null ? 1f : t.getFont().getSize2D() / 13f;
            javax.swing.table.TableColumnModel cm = t.getColumnModel();
            for (int i = 0; i < cm.getColumnCount(); i++) {
                Integer b = BREITEN.get(String.valueOf(cm.getColumn(i).getHeaderValue()).trim());
                if (b != null) cm.getColumn(i).setPreferredWidth(Math.round(b * f));
            }
            if (altModus == null) altModus = t.getAutoResizeMode();
            JViewport v = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, t);
            if (v != vp) {
                if (vp != null && vpHoerer != null) vp.removeComponentListener(vpHoerer);
                vp = v;
                vpHoerer = new java.awt.event.ComponentAdapter() {
                    @Override public void componentResized(java.awt.event.ComponentEvent e) { modusAnpassen(); }
                };
                if (vp != null) vp.addComponentListener(vpHoerer);
            }
            modusAnpassen();
        }

        void modusAnpassen() {
            if (!aktiv || vp == null || altModus == null) return;
            int summe = 0;
            javax.swing.table.TableColumnModel cm = t.getColumnModel();
            for (int i = 0; i < cm.getColumnCount(); i++) summe += cm.getColumn(i).getPreferredWidth();
            int soll = vp.getWidth() > 0 && vp.getWidth() < summe ? JTable.AUTO_RESIZE_OFF : altModus;
            if (t.getAutoResizeMode() != soll) t.setAutoResizeMode(soll);
        }

        void aus() {
            if (!aktiv) return;
            if (altModus != null) t.setAutoResizeMode(altModus);
            if (vp != null && vpHoerer != null) vp.removeComponentListener(vpHoerer);
            vp = null;
            aktiv = false;
            t.setRowSorter(null);
            t.getTableHeader().repaint();
            if (leiste != null) leiste.ausbauen();
        }

        /** Spalte „Art“ (Talentgruppe) – nur in der Talenttabelle. */
        int artSpalte() {
            String[] sp = spalten(t);
            if (sp == null) return -1;
            for (int i = 0; i < sp.length; i++) if ("Art".equals(sp[i])) return i;
            return -1;
        }

        void filtern() {
            if (sorter == null) return;
            String q = suche == null ? "" : suche.trim().toLowerCase(Locale.GERMAN);
            int art = artSpalte();
            if (q.isEmpty() && (art < 0 || gruppen.isEmpty())) sorter.setRowFilter(null);
            else sorter.setRowFilter(new RowFilter<TableModel, Integer>() {
                @Override public boolean include(Entry<? extends TableModel, ? extends Integer> e) {
                    if (art >= 0 && !gruppen.isEmpty() && !gruppen.contains(String.valueOf(e.getValue(art)))) return false;
                    if (q.isEmpty()) return true;
                    for (int i = 0; i < e.getValueCount(); i++) {
                        Object v = e.getValue(i);
                        if (v == null || v instanceof Boolean || v instanceof Number) continue;
                        if (String.valueOf(v).toLowerCase(Locale.GERMAN).contains(q)) return true;
                    }
                    return false;
                }
            });
            if (leiste != null) leiste.anzahl();
        }

        void einrichten() {
            if (!aktiv || !istTalentTabelle(t)) return;
            RowSorter<?> alt = t.getRowSorter();
            if (alt != null && alt.getModel() == t.getModel()) return;
            TableRowSorter<TableModel> s = new TableRowSorter<>(t.getModel());
            Comparator<Object> c = TalenteSortierung::vergleiche;
            String[] sp = spalten(t);
            for (int i = 0; i < sp.length; i++) {
                s.setComparator(i, c);
                if (KNOEPFE.contains(sp[i])) s.setSortable(i, false);
            }
            s.setSortsOnUpdates(true);
            // Klick 1: aufsteigend, Klick 2: absteigend, Klick 3: ursprüngliche Reihenfolge
            s.setMaxSortKeys(1);
            try { s.setSortKeys(sortierung); } catch (IllegalArgumentException ignore) { }
            s.addRowSorterListener(ev -> {
                if (ev.getType() != javax.swing.event.RowSorterEvent.Type.SORT_ORDER_CHANGED) return;
                if (t.getModel() != s.getModel()) return;   // beim Modellwechsel nicht merken
                sortierung = new ArrayList<>(s.getSortKeys());
            });
            t.setRowSorter(new DreiStufen(s));
            t.getTableHeader().repaint();
            sorter = s;
            breiten();
            filtern();
            if (leiste != null) leiste.gruppenNeu();
        }
    }

    // ---------------------------------------------------------------- Suche und Gruppenauswahl

    /** Wie FlowLayout, bricht aber in weitere Zeilen um, statt Schalter rechts abzuschneiden. */
    public static final class Umbruch extends FlowLayout {
        public Umbruch(int hgap, int vgap) { super(FlowLayout.LEFT, hgap, vgap); }
        @Override public Dimension preferredLayoutSize(Container c) { return groesse(c, true); }
        @Override public void layoutContainer(Container c) {
            super.layoutContainer(c);
            // Höhe hängt von der Breite ab – nach einer Größenänderung einmal neu anordnen lassen. Nur einmal je
            // Breite und Höhe: Gibt der umgebende Bereich die Höhe nicht her, entstünde sonst eine Endlosschleife,
            // die das ganze Fenster ständig neu anordnet (Flackern, z. B. der Hover-Markierung der Reiter).
            int soll = groesse(c, true).height;
            if (c.getHeight() != soll && c instanceof JComponent) {
                JComponent j = (JComponent) c;
                String stand = c.getWidth() + "x" + soll;
                if (stand.equals(j.getClientProperty("modernhelden.umbruch"))) return;
                j.putClientProperty("modernhelden.umbruch", stand);
                Container p = c.getParent();
                SwingUtilities.invokeLater(() -> { c.invalidate(); if (p != null) p.revalidate(); });
            }
        }
        @Override public Dimension minimumLayoutSize(Container c) {
            Dimension d = groesse(c, false);
            d.width = 0;
            return d;
        }
        private Dimension groesse(Container c, boolean pref) {
            synchronized (c.getTreeLock()) {
                int ziel = c.getWidth();
                // noch keine Breite: an der des umgebenden Bereichs orientieren
                for (Container p = c.getParent(); ziel <= 0 && p != null; p = p.getParent()) ziel = p.getWidth();
                if (ziel <= 0) ziel = Integer.MAX_VALUE;
                Insets i = c.getInsets();
                int max = ziel - i.left - i.right - getHgap() * 2;
                int b = 0, h = 0, zb = 0, zh = 0;
                for (Component k : c.getComponents()) {
                    if (!k.isVisible()) continue;
                    Dimension d = pref ? k.getPreferredSize() : k.getMinimumSize();
                    if (zb > 0 && zb + getHgap() + d.width > max) {
                        b = Math.max(b, zb);
                        h += zh + getVgap();
                        zb = 0;
                        zh = 0;
                    }
                    zb += (zb > 0 ? getHgap() : 0) + d.width;
                    zh = Math.max(zh, d.height);
                }
                b = Math.max(b, zb);
                h += zh;
                return new Dimension(b + i.left + i.right + getHgap() * 2, h + i.top + i.bottom + getVgap() * 2);
            }
        }
    }

    /**
     * Zeile unter der Kopfleiste der Helden-Software („Freie AP …“): Suchfeld wie im Inventar und – bei den Talenten –
     * Schalter für die Talentgruppen (keiner gewählt = alle Gruppen).
     */
    private static final class Suchleiste extends JPanel {
        final Zustand z;
        final JTextField feld = new JTextField(18);
        final JPanel chips = new JPanel(new Umbruch(4, 4));
        final JLabel zahl = new JLabel();
        JPanel nav;

        /** Breite bestimmt die Kopfleiste (füllt sie); nur die Höhe kommt aus dem Inhalt. */
        @Override public Dimension getPreferredSize() { Dimension d = super.getPreferredSize(); d.width = 100; return d; }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        Suchleiste(Zustand z) {
            super(new BorderLayout(10, 0));
            this.z = z;
            setOpaque(false);
            putClientProperty("modernhelden.own", true);
            setBorder(BorderFactory.createEmptyBorder(2, 5, 4, 5));
            boolean talente = z.artSpalte() >= 0;
            feld.putClientProperty("JTextField.placeholderText", talente ? "Talente suchen …" : "Zauber suchen …");
            feld.putClientProperty("JTextField.showClearButton", true);
            feld.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
            feld.putClientProperty("modernhelden.own", true);
            feld.setText(z.suche);
            feld.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { neu(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { neu(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { neu(); }
                void neu() { z.suche = feld.getText(); z.filtern(); }
            });
            // Suchfeld oben halten, auch wenn die Schalter in mehrere Zeilen umbrechen
            JPanel links = new JPanel(new BorderLayout());
            links.setOpaque(false);
            links.putClientProperty("modernhelden.own", true);
            links.add(feld, BorderLayout.NORTH);
            add(links, BorderLayout.WEST);
            chips.setOpaque(false);
            chips.putClientProperty("modernhelden.own", true);
            add(chips, BorderLayout.CENTER);
            zahl.setForeground(Theme.muted());
            zahl.putClientProperty("modernhelden.own", true);
            zahl.setVerticalAlignment(SwingConstants.TOP);
            zahl.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 4));
            add(zahl, BorderLayout.EAST);
            gruppenNeu();
        }

        /** Über der Tabelle einhängen: in die Kopfleiste „Navigationspanel“ der Helden-Software, eine Zeile tiefer. */
        /** Vorherige Deckkraft von Kopfleiste und Tabellenhintergrund – für das Design „Original“. */
        Boolean navOpak, vpOpak;
        JViewport vp;

        void einbauen() {
            if (getParent() != null) return;
            nav = navigation();
            if (nav == null || !(nav.getLayout() instanceof GridBagLayout)) return;
            // ruhiger Hintergrund (weiß bzw. grau) statt des Bildes der Helden-Software
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, z.t);
            vp = sp == null ? null : sp.getViewport();
            navOpak = nav.isOpaque();
            nav.setOpaque(true);
            nav.setBackground(UIManager.getColor("Panel.background"));
            if (vp != null) {
                vpOpak = vp.isOpaque();
                vp.setOpaque(true);
                vp.setBackground(UIManager.getColor("Table.background"));
            }
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 50;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            nav.add(this, c);
            // war die Leiste ausgehängt (Design „Original“), hat sie den Designwechsel verpasst
            SwingUtilities.updateComponentTreeUI(this);
            gruppenNeu();
            nav.revalidate();
            nav.repaint();
        }

        void ausbauen() {
            if (nav != null && navOpak != null) nav.setOpaque(navOpak);
            if (vp != null && vpOpak != null) vp.setOpaque(vpOpak);
            navOpak = vpOpak = null;
            Container p = getParent();
            if (p == null) return;
            p.remove(this);
            p.revalidate();
            p.repaint();
        }

        private JPanel navigation() {
            Container sp = SwingUtilities.getAncestorOfClass(JScrollPane.class, z.t);
            Container reiter = sp == null ? null : sp.getParent();
            if (reiter == null) return null;
            for (Component k : reiter.getComponents())
                if (k instanceof JPanel && "Navigationspanel".equals(k.getName())) return (JPanel) k;
            return null;
        }

        /** Schalter für die Talentgruppen aus der Spalte „Art“, mit Anzahl. */
        String thema;

        void gruppenNeu() {
            thema = Theme.current();
            chips.removeAll();
            int art = z.artSpalte();
            if (art >= 0) {
                Map<String, Integer> n = new LinkedHashMap<>();
                TableModel m = z.t.getModel();
                for (int i = 0; i < m.getRowCount(); i++) n.merge(String.valueOf(m.getValueAt(i, art)), 1, Integer::sum);
                z.gruppen.retainAll(n.keySet());
                JToggleButton alle = chip("Alle", z.gruppen.isEmpty(), "Alle Talentgruppen zeigen");
                alle.addActionListener(e -> { z.gruppen.clear(); gruppenNeu(); z.filtern(); });
                chips.add(alle);
                for (Map.Entry<String, Integer> g : n.entrySet()) {
                    JToggleButton b = chip(g.getKey() + " " + g.getValue(), z.gruppen.contains(g.getKey()),
                            "Nur diese Talentgruppe zeigen – weitere Gruppen dazuklicken");
                    b.addActionListener(e -> {
                        if (!z.gruppen.remove(g.getKey())) z.gruppen.add(g.getKey());
                        gruppenNeu();
                        z.filtern();
                    });
                    chips.add(b);
                }
            }
            anzahl();
            chips.revalidate();
            chips.repaint();
        }

        private JToggleButton chip(String text, boolean an, String tip) {
            JToggleButton b = new JToggleButton(text, an);
            b.setFocusable(false);
            b.putClientProperty("JButton.buttonType", "roundRect");
            b.putClientProperty("modernhelden.own", true);
            b.setMargin(new Insets(1, 8, 1, 8));
            TooltipHook.infoTip(b, tip);
            // gewählte Gruppen deutlich in der Akzentfarbe
            Color a = Theme.accent();
            b.putClientProperty("FlatLaf.style", String.format(
                    "selectedBackground: #%02x%02x%02x; selectedForeground: #ffffff", a.getRed(), a.getGreen(), a.getBlue()));
            return b;
        }

        void anzahl() {
            int alle = z.t.getModel().getRowCount(), sichtbar = z.t.getRowCount();
            zahl.setText(sichtbar == alle ? alle + " Einträge" : sichtbar + " von " + alle);
        }
    }

    /** Sortierer, der beim dritten Klick auf denselben Spaltenkopf die Sortierung aufhebt. */
    private static final class DreiStufen extends RowSorter<TableModel> {
        final TableRowSorter<TableModel> s;

        DreiStufen(TableRowSorter<TableModel> s) {
            this.s = s;
            s.addRowSorterListener(e -> {
                // Ereignisse des inneren Sortierers weiterreichen
                if (e.getType() == javax.swing.event.RowSorterEvent.Type.SORT_ORDER_CHANGED) fireSortOrderChanged();
                else fireRowSorterChanged(null);
            });
        }

        @Override public TableModel getModel() { return s.getModel(); }
        @Override public void toggleSortOrder(int column) {
            List<? extends SortKey> k = s.getSortKeys();
            if (!k.isEmpty() && k.get(0).getColumn() == column && k.get(0).getSortOrder() == SortOrder.DESCENDING) {
                s.setSortKeys(null);
            } else {
                s.toggleSortOrder(column);
            }
        }
        @Override public int convertRowIndexToModel(int index) { return s.convertRowIndexToModel(index); }
        // Die Helden-Software meldet teils eine Zeile zu viel („Zeilen 0 bis Anzahl geändert“) – ohne Sortierung
        // unschädlich, mit Sortierung muss sie abgefangen werden
        @Override public int convertRowIndexToView(int index) {
            return index < 0 || index >= s.getModelRowCount() ? -1 : s.convertRowIndexToView(index);
        }
        @Override public void setSortKeys(List<? extends SortKey> keys) { s.setSortKeys(keys); }
        @Override public List<? extends SortKey> getSortKeys() { return s.getSortKeys(); }
        @Override public int getViewRowCount() { return s.getViewRowCount(); }
        @Override public int getModelRowCount() { return s.getModelRowCount(); }
        @Override public void modelStructureChanged() { s.modelStructureChanged(); }
        @Override public void allRowsChanged() { s.allRowsChanged(); }
        @Override public void rowsInserted(int a, int b) { s.rowsInserted(a, b); }
        @Override public void rowsDeleted(int a, int b) { s.rowsDeleted(a, b); }
        @Override public void rowsUpdated(int a, int b) {
            int n = s.getModelRowCount();
            if (n == 0) return;
            s.rowsUpdated(Math.min(Math.max(a, 0), n - 1), Math.min(Math.max(b, 0), n - 1));
        }
        @Override public void rowsUpdated(int a, int b, int c) {
            int n = s.getModelRowCount();
            if (n == 0) return;
            s.rowsUpdated(Math.min(Math.max(a, 0), n - 1), Math.min(Math.max(b, 0), n - 1), c);
        }
    }

    // ---------------------------------------------------------------- eigene Tabellen (Reiter „Plugin: Modern-UI“)

    /**
     * Eigene Tabelle: Spalten bekommen ihre bevorzugte Breite; ist das Fenster schmaler als die Summe, wird die
     * Tabelle waagerecht rollbar, statt Inhalte mit „…“ abzuschneiden. Im breiten Fenster füllt sie die Breite.
     */
    static void breitOderRollen(JTable t) {
        final int modus = t.getAutoResizeMode();
        Runnable anpassen = () -> {
            JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, t);
            if (vp == null) return;
            int summe = 0;
            for (int i = 0; i < t.getColumnModel().getColumnCount(); i++) summe += t.getColumnModel().getColumn(i).getPreferredWidth();
            int soll = vp.getWidth() > 0 && vp.getWidth() < summe ? JTable.AUTO_RESIZE_OFF : modus;
            if (t.getAutoResizeMode() != soll) t.setAutoResizeMode(soll);
        };
        java.awt.event.ComponentAdapter hoerer = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { anpassen.run(); }
        };
        t.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.PARENT_CHANGED) == 0) return;
            JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, t);
            if (vp != null) {
                vp.removeComponentListener(hoerer);
                vp.addComponentListener(hoerer);
                anpassen.run();
            }
        });
    }

    /** Macht eine eigene Tabelle per Spaltenkopf sortierbar (aufsteigend, absteigend, ursprünglich). */
    /** Zeilenfilter für eine mit {@link #sortierbar(JTable)} sortierbare Tabelle (null: alle Zeilen). */
    static void filtern(JTable t, RowFilter<? super TableModel, ? super Integer> f) {
        if (t.getRowSorter() instanceof DreiStufen) ((DreiStufen) t.getRowSorter()).s.setRowFilter(f);
        else if (t.getRowSorter() instanceof TableRowSorter) ((TableRowSorter<?>) t.getRowSorter()).setRowFilter(f);
    }

    static void sortierbar(JTable t) {
        TableRowSorter<TableModel> s = new TableRowSorter<>(t.getModel());
        for (int i = 0; i < t.getModel().getColumnCount(); i++) s.setComparator(i, TalenteSortierung::vergleiche);
        s.setMaxSortKeys(1);
        t.setRowSorter(new DreiStufen(s));
    }

    /**
     * Sortierer für Tabellen mit Gruppen-Kopfzeilen (z. B. Talente nach Talentgruppe): sortiert nur innerhalb der
     * Gruppen, die Kopfzeilen und die Reihenfolge der Gruppen bleiben erhalten.
     */
    static void sortierbarInGruppen(JTable t, java.util.function.IntPredicate kopfzeile) {
        t.setRowSorter(new GruppenSortierer(t.getModel(), kopfzeile));
    }

    private static final class GruppenSortierer extends RowSorter<TableModel> {
        final TableModel m;
        final java.util.function.IntPredicate kopf;
        List<SortKey> keys = Collections.emptyList();
        int[] v2m = new int[0], m2v = new int[0];

        GruppenSortierer(TableModel m, java.util.function.IntPredicate kopf) {
            this.m = m;
            this.kopf = kopf;
            sortieren(false);
        }

        void sortieren(boolean melden) {
            int[] alt = v2m;
            int n = m.getRowCount();
            Integer[] idx = new Integer[n];
            final int[] gruppe = new int[n];
            int g = -1;
            for (int i = 0; i < n; i++) {
                idx[i] = i;
                if (kopf.test(i)) g = i;
                gruppe[i] = g;
            }
            if (!keys.isEmpty()) {
                final int c = keys.get(0).getColumn();
                final int dir = keys.get(0).getSortOrder() == SortOrder.DESCENDING ? -1 : 1;
                Arrays.sort(idx, (a, b) -> {
                    if (gruppe[a] != gruppe[b]) return Integer.compare(gruppe[a], gruppe[b]);
                    boolean ka = kopf.test(a), kb = kopf.test(b);
                    if (ka || kb) return ka == kb ? 0 : ka ? -1 : 1;
                    Object x = m.getValueAt(a, c), y = m.getValueAt(b, c);
                    // leere Zellen bleiben auch absteigend am Ende
                    if (leer(x) || leer(y)) return vergleiche(x, y);
                    return dir * vergleiche(x, y);
                });
            }
            v2m = new int[n];
            m2v = new int[n];
            for (int i = 0; i < n; i++) { v2m[i] = idx[i]; m2v[idx[i]] = i; }
            if (melden) fireRowSorterChanged(alt);
        }

        @Override public TableModel getModel() { return m; }
        @Override public void toggleSortOrder(int column) {
            List<SortKey> k;
            if (keys.isEmpty() || keys.get(0).getColumn() != column) k = Collections.singletonList(new SortKey(column, SortOrder.ASCENDING));
            else if (keys.get(0).getSortOrder() == SortOrder.ASCENDING) k = Collections.singletonList(new SortKey(column, SortOrder.DESCENDING));
            else k = Collections.emptyList();
            setSortKeys(k);
        }
        @Override public void setSortKeys(List<? extends SortKey> k) {
            keys = k == null ? Collections.emptyList() : new ArrayList<>(k);
            fireSortOrderChanged();
            sortieren(true);
        }
        @Override public List<? extends SortKey> getSortKeys() { return keys; }
        @Override public int convertRowIndexToModel(int i) { return i < 0 || i >= v2m.length ? -1 : v2m[i]; }
        @Override public int convertRowIndexToView(int i) { return i < 0 || i >= m2v.length ? -1 : m2v[i]; }
        @Override public int getViewRowCount() { return v2m.length; }
        @Override public int getModelRowCount() { return m.getRowCount(); }
        @Override public void modelStructureChanged() { sortieren(true); }
        @Override public void allRowsChanged() { sortieren(true); }
        @Override public void rowsInserted(int a, int b) { sortieren(true); }
        @Override public void rowsDeleted(int a, int b) { sortieren(true); }
        @Override public void rowsUpdated(int a, int b) { sortieren(true); }
        @Override public void rowsUpdated(int a, int b, int c) { sortieren(true); }
    }

    private static final java.util.regex.Pattern DATUM = java.util.regex.Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");

    private static final Collator TEXT = Collator.getInstance(Locale.GERMAN);

    /** Zahlen nach Wert, Texte alphabetisch; leere Zellen (z. B. Kosten bei Meta-Talenten) immer ans Ende. */
    static int vergleiche(Object a, Object b) {
        boolean la = leer(a), lb = leer(b);
        if (la || lb) return la == lb ? 0 : la ? 1 : -1;
        Double x = zahl(a), y = zahl(b);
        if (x == null || y == null) { x = datum(a); y = datum(b); }
        if (x != null && y != null) return Double.compare(x, y);
        if (a instanceof Boolean && b instanceof Boolean) return Boolean.compare((Boolean) a, (Boolean) b);
        return TEXT.compare(String.valueOf(a), String.valueOf(b));
    }

    /** „TT.MM.JJJJ“ als sortierbare Zahl JJJJMMTT. */
    private static Double datum(Object o) {
        java.util.regex.Matcher d = DATUM.matcher(String.valueOf(o).trim());
        if (!d.matches()) return null;
        return Double.valueOf(d.group(3) + String.format("%02d%02d", Integer.parseInt(d.group(2)), Integer.parseInt(d.group(1))));
    }

    private static boolean leer(Object o) { return o == null || String.valueOf(o).trim().isEmpty(); }

    private static Double zahl(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.valueOf(String.valueOf(o).trim()); } catch (NumberFormatException e) { return null; }
    }
}
