package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Moderne Heldenübersicht als Reiter in der Helden-Software. */
public final class HeroPanel extends JPanel {

    private static final String[] GRUPPEN = {"Nahkampf", "Fernkampf", "Körperlich", "Gesellschaft", "Natur", "Wissen",
            "Sprache", "Schrift", "Handwerk", "Gabe", "Meta", "Sonstige"};
    private static final Map<String, String> GRUPPEN_TITEL = new HashMap<>();
    static {
        GRUPPEN_TITEL.put("Nahkampf", "Kampftalente · Nahkampf");
        GRUPPEN_TITEL.put("Fernkampf", "Kampftalente · Fernkampf");
        GRUPPEN_TITEL.put("Körperlich", "Körperliche Talente");
        GRUPPEN_TITEL.put("Meta", "Meta-Talente");
        GRUPPEN_TITEL.put("Gesellschaft", "Gesellschaftliche Talente");
        GRUPPEN_TITEL.put("Natur", "Naturtalente");
        GRUPPEN_TITEL.put("Wissen", "Wissenstalente");
        GRUPPEN_TITEL.put("Sprache", "Sprachen");
        GRUPPEN_TITEL.put("Schrift", "Schriften");
        GRUPPEN_TITEL.put("Handwerk", "Handwerkstalente");
        GRUPPEN_TITEL.put("Gabe", "Gaben");
        GRUPPEN_TITEL.put("Sonstige", "Sonstige Talente");
    }

    private final TooltipDB db;
    private final Consumer<TooltipDB.Match> editor;
    /** Macht ein Ereignis rückgängig; liefert null bei Erfolg, sonst die Fehlermeldung. */
    private java.util.function.Function<Herkunft.Ereignis, String> rueckgaengig;

    public void setRueckgaengig(java.util.function.Function<Herkunft.Ereignis, String> f) { this.rueckgaengig = f; }

    /** Liefert zu einer SF die später erworbenen SF, die sie voraussetzen. */
    private java.util.function.BiFunction<String, List<String>, List<String>> abhaengige;

    public void setAbhaengige(java.util.function.BiFunction<String, List<String>, List<String>> f) { this.abhaengige = f; }
    private HeroData hero;
    /** Schalter „Erweiterte Tooltips anzeigen“ – gilt auch für diesen Reiter. */
    private java.util.function.BooleanSupplier tooltipsAn = () -> true;

    public void setTooltipsAn(java.util.function.BooleanSupplier b) { this.tooltipsAn = b; }
    private final JPanel content = new JPanel(new BorderLayout());
    private JTabbedPane tabs;
    private int lastTab;
    private final JTextField filter = new JTextField();
    private TalentModel talentModel;

    public HeroPanel(TooltipDB db, Consumer<TooltipDB.Match> editor) {
        super(new BorderLayout());
        this.db = db;
        this.editor = editor;
        add(content, BorderLayout.CENTER);
        filter.putClientProperty("JTextField.placeholderText", "Talente filtern …");
        filter.putClientProperty("JTextField.showClearButton", true);
        filter.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
        filter.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refilter(); }
            public void removeUpdate(DocumentEvent e) { refilter(); }
            public void changedUpdate(DocumentEvent e) { refilter(); }
        });
        showHero(null);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (content != null) SwingUtilities.invokeLater(() -> showHero(hero));
    }

    public void showHero(HeroData h) {
        this.hero = h;
        if (tabs != null) lastTab = tabs.getSelectedIndex();
        content.removeAll();
        SwingUtilities.updateComponentTreeUI(filter);
        filter.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
        setBackground(UIManager.getColor("Panel.background"));
        if (h == null) {
            JLabel l = new JLabel("<html><div style='text-align:center'><b style='font-size:130%'>Kein Held ausgewählt</b><br><br>"
                    + "Wähle links einen Helden aus, um die moderne Übersicht zu sehen.</div></html>", SwingConstants.CENTER);
            l.setForeground(Theme.muted());
            content.add(l, BorderLayout.CENTER);
        } else {
            JPanel top = new JPanel(new BorderLayout(0, 12));
            top.setOpaque(false);
            top.setBorder(new EmptyBorder(16, 18, 8, 18));
            top.add(header(h), BorderLayout.NORTH);
            top.add(werte(h), BorderLayout.CENTER);
            content.add(top, BorderLayout.NORTH);

            tabs = new JTabbedPane();
            tabs.putClientProperty("JTabbedPane.tabType", "card");
            // Symbole nur in den modernen Designs (im Original-Design unsichtbar und ohne Platzbedarf)
            tabs.addTab("Talente (" + h.talente.size() + ")", ModernIcons.nurModern("talente", 16), talentTab(h));
            tabs.addTab("Sonderfertigkeiten (" + h.sf.size() + ")", ModernIcons.nurModern("sonderfertigkeit", 16), sfTab(h));
            tabs.addTab("Vor- & Nachteile", ModernIcons.nurModern("vorteile", 16), vtTab(h));
            tabs.addTab("Kampf", ModernIcons.nurModern("kampf", 16), kampfTab(h));
            if (!h.zauber.isEmpty()) tabs.addTab("Zauber (" + h.zauber.size() + ")", ModernIcons.nurModern("zauber", 16), zauberTab(h));
            tabs.addTab("Inventar", ModernIcons.nurModern("inventar", 16), inventarTab(h));
            if (h.herkunft != null) tabs.addTab("Verlauf", ModernIcons.nurModern("verlauf", 16), verlaufTab(h));
            if (lastTab >= 0 && lastTab < tabs.getTabCount()) tabs.setSelectedIndex(lastTab);
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setBorder(new EmptyBorder(0, 12, 12, 12));
            wrap.setOpaque(false);
            wrap.add(tabs);
            content.add(wrap, BorderLayout.CENTER);
        }
        content.revalidate();
        content.repaint();
    }

    // ---------------------------------------------------------------- Kopf

    private JComponent header(HeroData h) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel name = new JLabel(h.name);
        name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() * 1.9f));
        // Rasse · Kultur · Profession umbrechen statt abschneiden
        JTextArea sub = new JTextArea(join(" · ", h.rasse, h.kultur, h.profession));
        sub.setLineWrap(true);
        sub.setWrapStyleWord(true);
        sub.setEditable(false);
        sub.setFocusable(false);
        sub.setOpaque(false);
        sub.setBorder(null);
        sub.setFont(name.getFont().deriveFont(Font.PLAIN, UIManager.getFont("Label.font").getSize2D()));
        sub.setForeground(Theme.muted());
        JPanel left = new JPanel(new BorderLayout(0, 2));
        left.setOpaque(false);
        left.add(name, BorderLayout.NORTH);
        left.add(sub, BorderLayout.CENTER);
        p.add(left, BorderLayout.CENTER);

        JPanel ap = new JPanel(new GridLayout(0, 1));
        ap.setOpaque(false);
        JLabel apl = new JLabel(h.ap + " AP", SwingConstants.RIGHT);
        apl.setFont(apl.getFont().deriveFont(Font.BOLD, apl.getFont().getSize2D() * 1.35f));
        JLabel frei = new JLabel(h.freieAp + " frei verfügbar", SwingConstants.RIGHT);
        frei.setForeground(h.freieAp > 0 ? Theme.accent() : Theme.muted());
        String apTip = TooltipDB.htmlInfo("Abenteuerpunkte: " + h.ap, "Woher die AP kommen und wofür sie ausgegeben wurden",
                h.herkunft.abenteuerpunkte(h.ap, h.freieAp));
        if (tooltipsAn.getAsBoolean()) {
            apl.setToolTipText(apTip);
            frei.setToolTipText(apTip);
        }
        ap.add(apl);
        ap.add(frei);
        p.add(ap, BorderLayout.EAST);
        return p;
    }

    private JComponent werte(HeroData h) {
        JPanel p = new JPanel(new GridLayout(1, 0, 6, 0));
        p.setOpaque(false);
        String[][] names = {{"MU", "Mut"}, {"KL", "Klugheit"}, {"IN", "Intuition"}, {"CH", "Charisma"},
                {"FF", "Fingerfertigkeit"}, {"GE", "Gewandtheit"}, {"KO", "Konstitution"}, {"KK", "Körperkraft"}};
        for (String[] n : names) {
            Integer v = h.eig.get(n[0]);
            if (v == null) continue;
            int mod = h.eigMod.getOrDefault(n[0], 0);
            p.add(tile(n[0], String.valueOf(v), n[1] + ": " + v + (mod != 0 ? " (Grundwert " + h.eigVal.get(n[0]) + ", Modifikator " + signed(mod) + ")" : ""), false));
        }
        Map<String, String> tips = new HashMap<>();
        tips.put("LE", "Lebensenergie");
        tips.put("AU", "Ausdauer");
        tips.put("AE", "Astralenergie");
        tips.put("KE", "Karmaenergie");
        tips.put("MR", "Magieresistenz");
        tips.put("INI", "Initiative-Basiswert");
        tips.put("AT", "Attacke-Basiswert");
        tips.put("PA", "Parade-Basiswert");
        tips.put("FK", "Fernkampf-Basiswert");
        for (Map.Entry<String, Integer> e : h.abgeleitet.entrySet()) {
            p.add(tile(e.getKey(), String.valueOf(e.getValue()), tips.get(e.getKey()) + ": " + e.getValue()
                    + " (berechnet aus Eigenschaften und Modifikatoren – maßgeblich ist der Wert der Helden-Software)", true));
        }
        return p;
    }

    private JComponent tile(String key, String value, String tip, boolean derived) {
        JPanel t = new JPanel(new BorderLayout());
        t.setBackground(Theme.kachel());
        t.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.trenner()),
                new MatteBorder(0, 0, 3, 0, derived ? Theme.muted() : Theme.accent())), new EmptyBorder(6, 4, 4, 4)));
        JLabel k = new JLabel(key, SwingConstants.CENTER);
        k.setForeground(Theme.muted());
        k.setFont(k.getFont().deriveFont(Font.BOLD, k.getFont().getSize2D() * 0.85f));
        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setFont(v.getFont().deriveFont(Font.BOLD, v.getFont().getSize2D() * 1.45f));
        t.add(k, BorderLayout.NORTH);
        t.add(v, BorderLayout.CENTER);
        if (tooltipsAn.getAsBoolean()) t.setToolTipText(tip);
        t.putClientProperty("modernhelden.own", true);
        return t;
    }

    // ---------------------------------------------------------------- Talente

    private static final class Row {
        final String gruppe; final HeroData.Talent t;
        Row(String g, HeroData.Talent t) { gruppe = g; this.t = t; }
    }

    private final class TalentModel extends AbstractTableModel {
        final List<Row> all = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        final String[] cols = {"Talent", "Probe / Werte", "BE", "TaW", "Lernmethode"};

        TalentModel(HeroData h) {
            Map<String, List<HeroData.Talent>> byGroup = new LinkedHashMap<>();
            for (String g : GRUPPEN) byGroup.put(g, new ArrayList<>());
            for (HeroData.Talent t : h.talente) {
                TooltipDB.Entry e = db.lookupEntry(t.name);
                String g = e != null && byGroup.containsKey(e.gruppe) ? e.gruppe : "Sonstige";
                byGroup.get(g).add(t);
            }
            for (Map.Entry<String, List<HeroData.Talent>> e : byGroup.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                all.add(new Row(e.getKey(), null));
                for (HeroData.Talent t : e.getValue()) all.add(new Row(e.getKey(), t));
            }
            filter("");
        }

        void filter(String q) {
            q = q.trim().toLowerCase(Locale.GERMAN);
            List<Row> r = new ArrayList<>();
            Row header = null;
            for (Row row : all) {
                if (row.t == null) { header = row; continue; }
                if (q.isEmpty() || row.t.name.toLowerCase(Locale.GERMAN).contains(q)) {
                    if (header != null) { r.add(header); header = null; }
                    r.add(row);
                }
            }
            rows = r;
            fireTableDataChanged();
        }

        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }
        public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            if (row.t == null) return c == 0 ? GRUPPEN_TITEL.get(row.gruppe) : "";
            HeroData.Talent t = row.t;
            switch (c) {
                case 0: return t.name;
                case 1: { String kw = hero.kampfwerteText(t.name); return kw != null ? kw : hero.probeMitWerten(t.probe); }
                case 2: return t.be;
                case 3: return t.wert;
                default: return t.lernmethode;
            }
        }
    }

    private void refilter() { if (talentModel != null) talentModel.filter(filter.getText()); }

    private JComponent talentTab(HeroData h) {
        talentModel = new TalentModel(h);
        final JTable table = new JTable(talentModel) {
            @Override public String getToolTipText(MouseEvent e) {
                if (!tooltipsAn.getAsBoolean()) return null;
                int r = rowAtPoint(e.getPoint());
                if (r < 0) return null;
                Row row = talentModel.rows.get(convertRowIndexToModel(r));
                return row.t == null ? null : talentTip(row.t);
            }
        };
        styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(230);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);
        table.getColumnModel().getColumn(4).setPreferredWidth(150);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Row row = talentModel.rows.get(t.convertRowIndexToModel(r));
                if (row.t == null) {
                    super.getTableCellRendererComponent(t, v, false, false, r, c);
                    setFont(getFont().deriveFont(Font.BOLD));
                    setForeground(Theme.accent());
                    setBackground(Theme.kopfzeile());
                    setBorder(new EmptyBorder(0, 8, 0, 4));
                    return this;
                }
                super.getTableCellRendererComponent(t, v, sel, false, r, c);
                if (!sel) setBackground(Theme.zeile(r));
                setFont(t.getFont());
                setBorder(new EmptyBorder(0, c == 0 ? 18 : 6, 0, 6));
                if (!sel) setForeground(c == 0 || c == 3 ? t.getForeground() : Theme.muted());
                if (c == 2) setHorizontalAlignment(CENTER); else setHorizontalAlignment(LEFT);
                return this;
            }
        });
        table.getColumnModel().getColumn(3).setCellRenderer(new BarRenderer(() -> talentModel));
        // Spaltenkopf sortiert innerhalb der Talentgruppen
        TalenteSortierung.sortierbarInGruppen(table, i -> talentModel.rows.get(i).t == null);
        attachPopup(table, e -> {
            int r = table.rowAtPoint(e.getPoint());
            if (r < 0) return null;
            Row row = talentModel.rows.get(table.convertRowIndexToModel(r));
            return row.t == null ? null : row.t.name;
        });

        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(filter, BorderLayout.NORTH);
        p.add(scroll(table), BorderLayout.CENTER);
        return p;
    }

    private String talentTip(HeroData.Talent t) {
        TooltipDB.Match m = db.find(t.name);
        if (m == null) m = new TooltipDB.Match(t.name, null, t.name);
        Map<String, String> x = new LinkedHashMap<>();
        String kw = hero.kampfwerteText(t.name);
        if (kw != null) x.put("Werte", kw); else x.put("Probe", hero.probeMitWerten(t.probe));
        x.put("TaW", String.valueOf(t.wert));
        x.put("BE", t.be);
        if (!t.k.isEmpty()) x.put("Komplexität", t.k);
        boolean meta = "berechnet".equals(t.lernmethode);
        if (meta) x.put("Art", "Meta-Talent (berechnet)"); else x.put("Lernen", t.lernmethode);
        return db.html(m, x, meta ? null : hero.herkunft.talent(t.name, t.wert));
    }

    /** Talentwert als Zahl mit kleinem Balken (0–21). */
    private static final class BarRenderer extends DefaultTableCellRenderer {
        private final java.util.function.Supplier<TalentModel> model;
        private int value; private boolean header;
        BarRenderer(java.util.function.Supplier<TalentModel> m) { model = m; }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, "", sel, false, r, c);
            header = model.get().rows.get(t.convertRowIndexToModel(r)).t == null;
            value = v instanceof Integer ? (Integer) v : 0;
            if (header) setBackground(Theme.kopfzeile());
            else if (!sel) setBackground(Theme.zeile(r));
            return this;
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (header) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font f = getFont().deriveFont(Font.BOLD);
            g2.setFont(f);
            String s = String.valueOf(value);
            FontMetrics fm = g2.getFontMetrics();
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(getForeground());
            g2.drawString(s, 8 + 24 - fm.stringWidth(s), y);
            int bx = 40, bw = Math.max(10, getWidth() - bx - 10), bh = 6, by = (getHeight() - bh) / 2;
            g2.setColor(Theme.trenner());
            g2.fillRoundRect(bx, by, bw, bh, bh, bh);
            g2.setColor(Theme.accent());
            int w = (int) (bw * Math.max(0, Math.min(21, value)) / 21.0);
            if (w > 0) g2.fillRoundRect(bx, by, Math.max(w, bh), bh, bh, bh);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- SF, Vor-/Nachteile

    private JComponent sfTab(HeroData h) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(scroll(itemList(h.sf, true)));
        return p;
    }

    private JComponent vtTab(HeroData h) {
        JPanel p = new JPanel(new GridLayout(1, 2, 12, 0));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(titled("Vorteile (" + h.vorteile.size() + ")", scroll(itemList(h.vorteile, true))));
        p.add(titled("Nachteile (" + h.nachteile.size() + ")", scroll(itemList(h.nachteile, true))));
        return p;
    }

    /** Liefert die ausführlichen Heldendaten (wie für den Heldenbogen) – nur bei Bedarf abgerufen. */
    private java.util.function.Supplier<de.modernhelden.print.SheetData> druckDaten;

    public void setDruckDaten(java.util.function.Supplier<de.modernhelden.print.SheetData> s) { this.druckDaten = s; }

    private JComponent inventarTab(final HeroData h) {
        final JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        String summe = h.geldSilber > 0 ? "  (= " + Geld.silber(h.geldSilber) + " Silbertaler)" : "";
        JLabel geld = new JLabel("Geldbörse: " + (h.geld.isEmpty() ? "leer" : h.geld) + summe);
        geld.setFont(geld.getFont().deriveFont(Font.BOLD));
        JLabel umr = new JLabel(Geld.UMRECHNUNG + "   ·   1 Stein = 40 Unzen = 1 kg (1 Unze = 25 g)");
        umr.setForeground(Theme.muted());
        final JPanel kopf = new JPanel(new GridLayout(0, 1, 0, 2));
        kopf.setOpaque(false);
        kopf.add(geld);
        kopf.add(umr);
        p.add(kopf, BorderLayout.NORTH);
        if (druckDaten == null) {
            p.add(scroll(itemList(h.inventar, false)));
            return p;
        }
        // ausführliche Daten erst laden, wenn der Reiter sichtbar wird
        final JLabel laden = new JLabel("Inventar wird geladen …", SwingConstants.CENTER);
        laden.setForeground(Theme.muted());
        p.add(laden, BorderLayout.CENTER);
        p.addHierarchyListener(new java.awt.event.HierarchyListener() {
            boolean fertig;
            @Override public void hierarchyChanged(java.awt.event.HierarchyEvent e) {
                if (fertig || (e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0 || !p.isShowing()) return;
                fertig = true;
                SwingUtilities.invokeLater(() -> {
                    de.modernhelden.print.SheetData sd = null;
                    try { sd = druckDaten.get(); } catch (RuntimeException ignore) { }
                    p.remove(laden);
                    if (sd == null || sd.inventar.isEmpty() && !h.inventar.isEmpty()) {
                        p.add(scroll(itemList(h.inventar, false)), BorderLayout.CENTER);
                    } else {
                        p.add(inventarTabelle(sd, kopf), BorderLayout.CENTER);
                    }
                    p.revalidate();
                    p.repaint();
                });
            }
        });
        return p;
    }

    /** Zeile der Inventartabelle mit allen Angaben zu einem Gegenstand. */
    private static final class InvZeile {
        de.modernhelden.print.SheetData.Gegenstand g;
        int anzahl;
        String werte = "";
        final Map<String, String> tip = new LinkedHashMap<>();
        String filterArt = "Sonstiges";
    }

    private static final String[] INV_FILTER = {"Alle", "Angelegt", "Waffen", "Rüstung", "Schilde", "Sonstiges"};

    private JComponent inventarTabelle(de.modernhelden.print.SheetData sd, JPanel kopf) {
        final List<InvZeile> zeilen = new ArrayList<>();
        double unzen = 0, unzenAngelegt = 0;
        long preis = 0;
        for (de.modernhelden.print.SheetData.Gegenstand g : sd.inventar) {
            InvZeile z = new InvZeile();
            z.g = g;
            z.anzahl = Math.max(1, num(g.anzahl));
            z.werte = InventarInfo.werte(sd, g, z.tip);
            String a = g.art.toLowerCase(Locale.GERMAN);
            z.filterArt = a.contains("waffe") ? "Waffen" : a.contains("rüstung") ? "Rüstung" : a.contains("schild") ? "Schilde" : "Sonstiges";
            zeilen.add(z);
            unzen += g.unzen;
            if (g.angelegt) unzenAngelegt += g.unzen;
            preis += g.gesamtpreis;
        }
        // Zusammenfassung über der Tabelle
        JLabel summe = new JLabel(zeilen.size() + (zeilen.size() == 1 ? " Gegenstand" : " Gegenstände")
                + "  ·  Gesamtgewicht " + InventarInfo.stein(unzen) + " Stein"
                + (unzenAngelegt > 0 ? " (davon angelegt " + InventarInfo.stein(unzenAngelegt) + ")" : "")
                + (preis > 0 ? "  ·  Gesamtwert " + InventarInfo.preisText(preis) : ""));
        kopf.add(summe);

        final String[] cols = {"Anz.", "Gegenstand", "Art", "Gewicht (Stein)", "Wert", "Angelegt", "Werte"};
        final javax.swing.table.AbstractTableModel model = new javax.swing.table.AbstractTableModel() {
            @Override public int getRowCount() { return zeilen.size(); }
            @Override public int getColumnCount() { return cols.length; }
            @Override public String getColumnName(int c) { return cols[c]; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 0 ? Integer.class : c == 3 ? Double.class : c == 4 ? Long.class : c == 5 ? Boolean.class : String.class;
            }
            @Override public Object getValueAt(int r, int c) {
                InvZeile z = zeilen.get(r);
                switch (c) {
                    case 0: return z.anzahl;
                    case 1: return z.g.name;
                    case 2: return z.g.art.equalsIgnoreCase("sonst") ? "Sonstiges" : z.g.art;
                    case 3: return z.g.unzen / 40.0;
                    case 4: return z.g.gesamtpreis;
                    case 5: return z.g.angelegt;
                    default: return z.werte;
                }
            }
        };
        final JTable table = new JTable(model) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public String getToolTipText(MouseEvent e) {
                if (!tooltipsAn.getAsBoolean()) return null;
                int r = rowAtPoint(e.getPoint());
                if (r < 0) return null;
                InvZeile z = zeilen.get(convertRowIndexToModel(r));
                // Gegenstände nicht mit gleichnamigen Talenten/SF verknüpfen (z. B. Parierwaffe „Linkhand“)
                return db.html(new TooltipDB.Match(z.g.name, null, z.g.name), z.tip);
            }
        };
        styleTable(table);
        final javax.swing.table.TableRowSorter<javax.swing.table.TableModel> sorter = new javax.swing.table.TableRowSorter<>(model);
        table.setRowSorter(sorter);
        int[] breiten = {45, 220, 140, 95, 85, 70, 420};
        for (int i = 0; i < cols.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(breiten[i]);
        DefaultTableCellRenderer zentriert = new DefaultTableCellRenderer();
        zentriert.setHorizontalAlignment(SwingConstants.CENTER);
        DefaultTableCellRenderer gewicht = new DefaultTableCellRenderer() {
            @Override protected void setValue(Object v) { setText(v instanceof Double && (Double) v > 0 ? InventarInfo.zahl((Double) v) : ""); }
        };
        gewicht.setHorizontalAlignment(SwingConstants.RIGHT);
        DefaultTableCellRenderer wert = new DefaultTableCellRenderer() {
            @Override protected void setValue(Object v) { setText(v instanceof Long && (Long) v > 0 ? InventarInfo.preisText((Long) v) : ""); }
        };
        wert.setHorizontalAlignment(SwingConstants.RIGHT);
        DefaultTableCellRenderer haken = new DefaultTableCellRenderer() {
            @Override protected void setValue(Object v) { setText(Boolean.TRUE.equals(v) ? "\u2713" : ""); }
        };
        haken.setHorizontalAlignment(SwingConstants.CENTER);
        javax.swing.table.TableCellRenderer[] r = {zentriert, null, null, gewicht, wert, haken, null};
        for (int i = 0; i < cols.length; i++) {
            javax.swing.table.TableColumn col = table.getColumnModel().getColumn(i);
            if (r[i] != null) col.setCellRenderer(r[i]);
            col.setCellRenderer(new TabellenStil.Zebra(col.getCellRenderer()));
        }
        attachPopup(table, e -> {
            int row = table.rowAtPoint(e.getPoint());
            return row < 0 ? null : zeilen.get(table.convertRowIndexToModel(row)).g.name;
        });

        // Suche und Filter nach Art
        final JTextField suche = new JTextField();
        suche.putClientProperty("JTextField.placeholderText", "Gegenstände filtern …");
        suche.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
        final JComboBox<String> art = new JComboBox<>(INV_FILTER);
        Runnable filtern = () -> {
            final String q = suche.getText().trim().toLowerCase(Locale.GERMAN);
            final String a = (String) art.getSelectedItem();
            sorter.setRowFilter(new RowFilter<javax.swing.table.TableModel, Integer>() {
                @Override public boolean include(Entry<? extends javax.swing.table.TableModel, ? extends Integer> e) {
                    InvZeile z = zeilen.get(e.getIdentifier());
                    if ("Angelegt".equals(a) && !z.g.angelegt) return false;
                    // Gegenstände mit mehreren Arten (z. B. „Nahkampfwaffe, Schild“) passen zu jedem ihrer Filter
                    String art = z.g.art.toLowerCase(Locale.GERMAN);
                    if ("Waffen".equals(a) && !art.contains("waffe")) return false;
                    if ("Rüstung".equals(a) && !art.contains("rüstung")) return false;
                    if ("Schilde".equals(a) && !art.contains("schild")) return false;
                    if ("Sonstiges".equals(a) && !"Sonstiges".equals(z.filterArt)) return false;
                    return q.isEmpty() || (z.g.name + " " + z.g.art + " " + z.werte).toLowerCase(Locale.GERMAN).contains(q);
                }
            });
        };
        suche.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filtern.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filtern.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filtern.run(); }
        });
        art.addActionListener(e -> filtern.run());
        JPanel leiste = new JPanel(new BorderLayout(8, 0));
        leiste.setOpaque(false);
        leiste.add(suche, BorderLayout.CENTER);
        leiste.add(art, BorderLayout.EAST);

        JLabel hinweis = new JLabel("Werte aus dem aktiven Kampfset (inkl. Behinderung). Mauszeiger über einer Zeile zeigt alle Angaben, Doppelklick legt eine Notiz an.");
        hinweis.setForeground(Theme.muted());
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.setOpaque(false);
        box.add(leiste, BorderLayout.NORTH);
        box.add(scroll(table), BorderLayout.CENTER);
        box.add(hinweis, BorderLayout.SOUTH);
        return box;
    }


    private JList<HeroData.Item> itemList(List<HeroData.Item> items, boolean withDescription) {
        final JList<HeroData.Item> list = new JList<HeroData.Item>(items.toArray(new HeroData.Item[0])) {
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
            @Override public String getToolTipText(MouseEvent e) {
                if (!tooltipsAn.getAsBoolean()) return null;
                int i = locationToIndex(e.getPoint());
                if (i < 0 || !getCellBounds(i, i).contains(e.getPoint())) return null;
                HeroData.Item it = getModel().getElementAt(i);
                TooltipDB.Match m = db.find(it.name);
                if (m == null) return null;
                Map<String, String> x = new LinkedHashMap<>();
                if (!it.detail.isEmpty()) x.put("Details", it.detail);
                boolean istSf = hero.sf.contains(it);
                return db.html(m, x, istSf ? hero.herkunft.sonderfertigkeit(it.name) : null);
            }
        };
        list.putClientProperty("modernhelden.own", true);
        list.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                int w = list.getParent() != null ? list.getParent().getWidth() : list.getWidth();
                if (w > 0 && w != list.getFixedCellWidth()) list.setFixedCellWidth(w);
            }
        });
        ToolTipManager.sharedInstance().registerComponent(list);
        list.setCellRenderer(new ListCellRenderer<HeroData.Item>() {
            final JPanel panel = new JPanel(new BorderLayout(0, 2));
            final JLabel title = new JLabel(), right = new JLabel();
            final JTextArea desc = new JTextArea();
            {
                desc.setLineWrap(true);
                desc.setWrapStyleWord(true);
                desc.setEditable(false);
                desc.setOpaque(false);
                desc.setBorder(null);
                panel.setBorder(BorderFactory.createCompoundBorder(
                        new MatteBorder(0, 0, 1, 0, Theme.trenner()), new EmptyBorder(6, 10, 6, 10)));
                JPanel center = new JPanel(new BorderLayout(0, 2));
                center.setOpaque(false);
                center.add(title, BorderLayout.NORTH);
                center.add(desc, BorderLayout.CENTER);
                panel.add(center, BorderLayout.CENTER);
                panel.add(right, BorderLayout.EAST);
            }
            public Component getListCellRendererComponent(JList<? extends HeroData.Item> l, HeroData.Item it, int i, boolean sel, boolean foc) {
                panel.setBackground(sel ? l.getSelectionBackground() : Theme.zeile(i));
                // Trennlinie mit aktueller Designfarbe (nach einem Designwechsel)
                panel.setBorder(BorderFactory.createCompoundBorder(
                        new MatteBorder(0, 0, 1, 0, Theme.trenner()), new EmptyBorder(6, 10, 6, 10)));
                title.setForeground(sel ? l.getSelectionForeground() : l.getForeground());
                title.setFont(l.getFont().deriveFont(Font.BOLD));
                String col = sel ? "" : String.format("color:#%06x", Theme.accent().getRGB() & 0xffffff);
                if (withDescription && !it.detail.isEmpty()) {
                    title.setText("<html>" + esc(it.name) + " <span style='" + col + "'>· " + esc(it.detail) + "</span></html>");
                    right.setText(null);
                } else {
                    title.setText(it.name);
                    right.setText(it.detail);
                }
                right.setForeground(sel ? l.getSelectionForeground() : Theme.accent());
                right.setFont(l.getFont().deriveFont(Font.BOLD));
                TooltipDB.Entry e = withDescription ? db.lookupEntry(it.name) : null;
                String own = withDescription ? db.getUserText(it.name) : null;
                String d = own != null ? own : (e != null ? e.text : "");
                int w = Math.max(200, (l.getFixedCellWidth() > 0 ? l.getFixedCellWidth() : l.getWidth()) - 22);
                desc.setFont(l.getFont());
                desc.setText(d);
                desc.setSize(new Dimension(w, Short.MAX_VALUE));
                desc.setVisible(!d.isEmpty());
                desc.setForeground(sel ? l.getSelectionForeground() : Theme.muted());
                return panel;
            }
        });
        attachPopup(list, ev -> {
            int i = list.locationToIndex(ev.getPoint());
            return i < 0 ? null : list.getModel().getElementAt(i).name;
        });
        return list;
    }

    // ---------------------------------------------------------------- Kampf & Zauber

    private JComponent kampfTab(HeroData h) {
        String[] cols = {"Waffentalent", "AT", "PA", "FK", "TaW"};
        List<Object[]> data = new ArrayList<>();
        for (HeroData.Kampfwert k : h.kampf) {
            HeroData.Talent t = h.talent(k.name);
            data.add(new Object[]{k.name, k.fernkampf ? "" : k.at, k.fernkampf ? "" : k.pa, k.fernkampf ? k.at : "", t == null ? "" : t.wert});
        }
        return simpleTable(cols, data, "Kampfwerte inkl. Talentverteilung, ohne Waffen- und Rüstungsmodifikatoren.");
    }

    private JComponent zauberTab(HeroData h) {
        String[] cols = {"Zauber", "Probe", "ZfW", "Repräsentation"};
        List<Object[]> data = new ArrayList<>();
        for (HeroData.Talent z : h.zauber) data.add(new Object[]{z.name, h.probeMitWerten(z.probe), z.wert, z.lernmethode});
        return simpleTable(cols, data, null);
    }

    private JComponent simpleTable(String[] cols, List<Object[]> data, String hint) {
        final JTable table = new JTable(data.toArray(new Object[0][]), cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public String getToolTipText(MouseEvent e) {
                if (!tooltipsAn.getAsBoolean()) return null;
                int r = rowAtPoint(e.getPoint());
                if (r < 0) return null;
                String n = String.valueOf(getValueAt(r, 0));
                HeroData.Talent t = hero.talent(n);
                if (t != null) return talentTip(t);
                TooltipDB.Match m = db.find(n);
                HeroData.Talent z = hero.zauber(n);
                if (z != null) {
                    Map<String, String> x = new LinkedHashMap<>();
                    x.put("Art", "Zauber");
                    x.put("Probe", hero.probeMitWerten(z.probe));
                    x.put("ZfW", String.valueOf(z.wert));
                    if (!z.lernmethode.isEmpty()) x.put("Repräsentation", z.lernmethode);
                    return db.html(m != null ? m : new TooltipDB.Match(z.name, null, z.name), x);
                }
                return m == null ? null : db.html(m, null);
            }
        };
        styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(260);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 1; i < cols.length; i++) table.getColumnModel().getColumn(i).setCellRenderer(center);
        // Zebra-Streifen (in den modernen Designs)
        for (int i = 0; i < cols.length; i++) {
            javax.swing.table.TableColumn col = table.getColumnModel().getColumn(i);
            col.setCellRenderer(new TabellenStil.Zebra(col.getCellRenderer()));
        }
        attachPopup(table, e -> {
            int r = table.rowAtPoint(e.getPoint());
            return r < 0 ? null : String.valueOf(table.getValueAt(r, 0));
        });
        TalenteSortierung.sortierbar(table);
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(scroll(table));
        if (hint != null) {
            JLabel l = new JLabel(hint);
            l.setForeground(Theme.muted());
            p.add(l, BorderLayout.SOUTH);
        }
        return p;
    }

    // ---------------------------------------------------------------- Verlauf / Rückgängig

    private JComponent verlaufTab(final HeroData h) {
        final List<Herkunft.Ereignis> ev = h.herkunft.rueckgaengigMoeglich();
        String[] cols = {"Datum", "Aktion", "Eintrag", "Änderung", "Lernmethode", "AP"};
        List<Object[]> data = new ArrayList<>();
        for (Herkunft.Ereignis e : ev) {
            String aend = e.alt.isEmpty() && e.neu.isEmpty() ? "" : erster(e.alt) + " → " + erster(e.neu);
            int ap = -num(e.ap);
            // Lernmethode inkl. Spezieller Erfahrung (SE), wie von der Helden-Software vermerkt
            data.add(new Object[]{h.herkunft.datum(e), e.text, e.obj, aend, Herkunft.lernText(e.info), ap == 0 ? "" : String.valueOf(ap)});
        }
        final JTable table = new JTable(data.toArray(new Object[0][]), cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        styleTable(table);
        int[] breiten = {85, 170, 220, 80, 300, 50};
        for (int i = 0; i < breiten.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(breiten[i]);
        TalenteSortierung.sortierbar(table);
        for (int i = 0; i < cols.length; i++) {
            javax.swing.table.TableColumn col = table.getColumnModel().getColumn(i);
            col.setCellRenderer(new TabellenStil.Zebra(col.getCellRenderer()));
        }
        final JButton undo = new JButton("Rückgängig machen …");
        undo.setIcon(ModernIcons.nurModern("refresh", 16));
        undo.setEnabled(false);
        final JLabel hinweis = new JLabel(ev.isEmpty()
                ? "Keine Steigerungen oder Sonderfertigkeiten nach der Erschaffung vorhanden."
                : "Eintrag wählen, um ihn rückgängig zu machen. Die AP werden erstattet.");
        hinweis.setForeground(Theme.muted());
        table.getSelectionModel().addListSelectionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0) { undo.setEnabled(false); return; }
            Herkunft.Ereignis x = ev.get(table.convertRowIndexToModel(r));
            Herkunft.Ereignis spaeter = h.herkunft.spaeteresZuSelbem(x);
            if (spaeter != null) {
                undo.setEnabled(false);
                hinweis.setText("Erst die spätere Änderung an „" + x.obj + "“ vom " + h.herkunft.datum(spaeter) + " rückgängig machen.");
            } else {
                undo.setEnabled(rueckgaengig != null);
                int ap = -num(x.ap);
                String was = x.text.startsWith("Sonderfertigkeit") ? "entfernen"
                        : x.text.endsWith("aktivieren") ? "deaktivieren"
                        : "zurück auf " + erster(x.alt);
                hinweis.setText("<html>" + esc(x.obj) + " " + was + (ap > 0 ? " · <b>" + ap + " AP</b> zurück" : "") + "</html>");
            }
        });
        undo.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0 || rueckgaengig == null) return;
            Herkunft.Ereignis x = ev.get(table.convertRowIndexToModel(r));
            int ap = -num(x.ap);
            StringBuilder msg = new StringBuilder("<html><div style='width:360px'><b>" + esc(x.text) + ": " + esc(x.obj) + "</b>");
            if (!x.alt.isEmpty() || !x.neu.isEmpty()) msg.append("<br>Wert ").append(esc(erster(x.neu))).append(" → ").append(esc(erster(x.alt)));
            msg.append("<br>Erstattung: <b>").append(ap).append(" AP</b>");
            if (x.info.contains("SE")) msg.append("<br><br>Für diese Steigerung wurde eine <b>Spezielle Erfahrung</b> genutzt. "
                    + "Sie wird nicht automatisch zurückgegeben – bei Bedarf das SE-Häkchen im Reiter „Talente“ wieder setzen.");
            // Sonderfertigkeiten, die diese voraussetzen, werden mit zurückgesetzt (spätere zuerst)
            final List<Herkunft.Ereignis> mit = new ArrayList<>();
            boolean warnung = false;
            if (x.text.startsWith("Sonderfertigkeit") && abhaengige != null) {
                List<String> dep = abhaengige.apply(x.obj, h.herkunft.andereSonderfertigkeiten(x));
                List<String> ohneEreignis = new ArrayList<>(dep);
                for (Herkunft.Ereignis y : ev)
                    if (y != x && y.text.startsWith("Sonderfertigkeit") && dep.contains(y.obj)) {
                        if (!mit.contains(y)) mit.add(y);
                        ohneEreignis.remove(y.obj);
                    }
                ohneEreignis.remove(x.obj);
                if (!mit.isEmpty()) {
                    int summe = ap;
                    StringBuilder namen = new StringBuilder();
                    for (Herkunft.Ereignis y : mit) {
                        summe += -num(y.ap);
                        namen.append("<br>&nbsp;• ").append(esc(y.obj)).append(" (").append(-num(y.ap)).append(" AP)");
                    }
                    msg.append("<br><br><b style='color:#c0392b'>⚠ Achtung:</b> Folgende Sonderfertigkeiten setzen „")
                            .append(esc(x.obj)).append("“ voraus und werden <b>mit zurückgesetzt</b>:").append(namen)
                            .append("<br>Erstattung insgesamt: <b>").append(summe).append(" AP</b>");
                }
                if (!ohneEreignis.isEmpty()) {
                    StringBuilder namen = new StringBuilder();
                    for (String n : ohneEreignis) namen.append("<br>&nbsp;• ").append(esc(n));
                    msg.append("<br><br><b style='color:#c0392b'>⚠ Achtung:</b> Auch diese Sonderfertigkeiten setzen „")
                            .append(esc(x.obj)).append("“ voraus, stehen aber nicht im Verlauf (z. B. bei der Erschaffung erworben) "
                            + "und können nicht automatisch zurückgesetzt werden:").append(namen)
                            .append("<br>Sie bleiben erhalten, ihre Voraussetzung ist danach aber nicht mehr erfüllt.");
                }
                warnung = !dep.isEmpty();
            }
            msg.append("<br><br>Die Änderung wird erst mit dem nächsten Speichern des Helden dauerhaft.</div></html>");
            int ok = JOptionPane.showConfirmDialog(this, msg.toString(), "Rückgängig machen", JOptionPane.OK_CANCEL_OPTION,
                    warnung ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            // erst die abhängigen (neueste zuerst – ev ist bereits nach Datum absteigend sortiert), dann die gewählte SF
            String fehler = null;
            List<String> erledigt = new ArrayList<>();
            for (Herkunft.Ereignis y : mit) {
                fehler = rueckgaengig.apply(y);
                if (fehler != null) { fehler = "„" + y.obj + "“: " + fehler; break; }
                erledigt.add(y.obj);
            }
            if (fehler == null) fehler = rueckgaengig.apply(x);
            if (fehler != null && !erledigt.isEmpty())
                fehler += "\n\nBereits zurückgesetzt: " + String.join(", ", erledigt) + ".";
            if (fehler != null) JOptionPane.showMessageDialog(this, fehler, "Rückgängig machen", JOptionPane.WARNING_MESSAGE);
        });
        JPanel unten = new JPanel(new BorderLayout(8, 0));
        unten.setOpaque(false);
        unten.add(hinweis, BorderLayout.CENTER);
        unten.add(undo, BorderLayout.EAST);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(scroll(table), BorderLayout.CENTER);
        p.add(unten, BorderLayout.SOUTH);
        return p;
    }

    private static String erster(String s) {
        int i = s.indexOf(';');
        return i >= 0 ? s.substring(0, i) : s;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // ---------------------------------------------------------------- Hilfsfunktionen

    private void styleTable(JTable t) {
        t.putClientProperty("modernhelden.own", true);
        t.setRowHeight(Math.round(t.getFont().getSize() * 2.1f));
        t.setFillsViewportHeight(true);
        t.setShowVerticalLines(true);
        t.setShowHorizontalLines(true);
        t.setGridColor(Theme.trenner());
        t.setAutoCreateRowSorter(false);
        t.getTableHeader().setReorderingAllowed(false);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ToolTipManager.sharedInstance().registerComponent(t);
    }

    private interface NameAt { String at(MouseEvent e); }

    private void attachPopup(final JComponent c, final NameAt nameAt) {
        c.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { maybe(e); }
            public void mouseReleased(MouseEvent e) { maybe(e); }
            @Override public void mouseClicked(MouseEvent e) {
                // Doppelklick: eigene Notiz zum Eintrag anlegen/bearbeiten
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                String n = nameAt.at(e);
                if (n == null) return;
                TooltipDB.Match m = db.find(n);
                editor.accept(m != null ? m : new TooltipDB.Match(n, null, n));
                showHero(hero);
            }
            private void maybe(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                String n = nameAt.at(e);
                if (n == null) return;
                TooltipDB.Match m = db.find(n);
                final TooltipDB.Match target = m != null ? m : new TooltipDB.Match(n, null, n);
                JPopupMenu menu = new JPopupMenu();
                JMenuItem edit = new JMenuItem("Eigenen Tooltip-Text bearbeiten …");
                edit.addActionListener(a -> { editor.accept(target); showHero(hero); });
                menu.add(edit);
                menu.show(c, e.getX(), e.getY());
            }
        });
    }

    private static JScrollPane scroll(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createLineBorder(Theme.trenner()));
        return s;
    }

    private static JComponent titled(String title, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        JLabel l = new JLabel(title);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l, BorderLayout.NORTH);
        p.add(c);
        return p;
    }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>"); }

    private static String signed(int v) { return v > 0 ? "+" + v : String.valueOf(v); }
}
