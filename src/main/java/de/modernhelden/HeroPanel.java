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
            "Sprache", "Schrift", "Handwerk", "Gabe", "Sonstige"};
    private static final Map<String, String> GRUPPEN_TITEL = new HashMap<>();
    static {
        GRUPPEN_TITEL.put("Nahkampf", "Kampftalente · Nahkampf");
        GRUPPEN_TITEL.put("Fernkampf", "Kampftalente · Fernkampf");
        GRUPPEN_TITEL.put("Körperlich", "Körperliche Talente");
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
    private HeroData hero;
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
            tabs.addTab("Talente (" + h.talente.size() + ")", talentTab(h));
            tabs.addTab("Sonderfertigkeiten (" + h.sf.size() + ")", sfTab(h));
            tabs.addTab("Vor- & Nachteile", vtTab(h));
            tabs.addTab("Kampf", kampfTab(h));
            if (!h.zauber.isEmpty()) tabs.addTab("Zauber (" + h.zauber.size() + ")", zauberTab(h));
            tabs.addTab("Inventar", inventarTab(h));
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
        JLabel sub = new JLabel(join(" · ", h.rasse, h.kultur, h.profession));
        sub.setForeground(Theme.muted());
        JPanel left = new JPanel(new GridLayout(0, 1, 0, 2));
        left.setOpaque(false);
        left.add(name);
        left.add(sub);
        p.add(left, BorderLayout.CENTER);

        JPanel ap = new JPanel(new GridLayout(0, 1));
        ap.setOpaque(false);
        JLabel apl = new JLabel(h.ap + " AP", SwingConstants.RIGHT);
        apl.setFont(apl.getFont().deriveFont(Font.BOLD, apl.getFont().getSize2D() * 1.35f));
        JLabel frei = new JLabel(h.freieAp + " frei verfügbar", SwingConstants.RIGHT);
        frei.setForeground(h.freieAp > 0 ? Theme.accent() : Theme.muted());
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
        t.setBackground(Theme.card());
        t.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 3, 0, derived ? Theme.muted() : Theme.accent()), new EmptyBorder(6, 4, 4, 4)));
        JLabel k = new JLabel(key, SwingConstants.CENTER);
        k.setForeground(Theme.muted());
        k.setFont(k.getFont().deriveFont(Font.BOLD, k.getFont().getSize2D() * 0.85f));
        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setFont(v.getFont().deriveFont(Font.BOLD, v.getFont().getSize2D() * 1.45f));
        t.add(k, BorderLayout.NORTH);
        t.add(v, BorderLayout.CENTER);
        t.setToolTipText(tip);
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
                int r = rowAtPoint(e.getPoint());
                if (r < 0) return null;
                Row row = talentModel.rows.get(r);
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
                Row row = talentModel.rows.get(r);
                if (row.t == null) {
                    super.getTableCellRendererComponent(t, v, false, false, r, c);
                    setFont(getFont().deriveFont(Font.BOLD));
                    setForeground(Theme.accent());
                    setBackground(Theme.card());
                    setBorder(new EmptyBorder(0, 8, 0, 4));
                    return this;
                }
                super.getTableCellRendererComponent(t, v, sel, false, r, c);
                setFont(t.getFont());
                setBorder(new EmptyBorder(0, c == 0 ? 18 : 6, 0, 6));
                if (!sel) setForeground(c == 0 || c == 3 ? t.getForeground() : Theme.muted());
                if (c == 2) setHorizontalAlignment(CENTER); else setHorizontalAlignment(LEFT);
                return this;
            }
        });
        table.getColumnModel().getColumn(3).setCellRenderer(new BarRenderer(() -> talentModel));
        attachPopup(table, e -> {
            int r = table.rowAtPoint(e.getPoint());
            if (r < 0 || talentModel.rows.get(r).t == null) return null;
            return talentModel.rows.get(r).t.name;
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
        x.put("Lernen", t.lernmethode);
        return db.html(m, x);
    }

    /** Talentwert als Zahl mit kleinem Balken (0–21). */
    private static final class BarRenderer extends DefaultTableCellRenderer {
        private final java.util.function.Supplier<TalentModel> model;
        private int value; private boolean header;
        BarRenderer(java.util.function.Supplier<TalentModel> m) { model = m; }
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, "", sel, false, r, c);
            header = model.get().rows.get(r).t == null;
            value = v instanceof Integer ? (Integer) v : 0;
            if (header) setBackground(Theme.card());
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
            g2.setColor(Theme.card().darker());
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

    private JComponent inventarTab(HeroData h) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        JLabel geld = new JLabel("Geldbörse: " + (h.geld.isEmpty() ? "leer" : h.geld));
        geld.setFont(geld.getFont().deriveFont(Font.BOLD));
        p.add(geld, BorderLayout.NORTH);
        p.add(scroll(itemList(h.inventar, false)));
        return p;
    }

    private JList<HeroData.Item> itemList(List<HeroData.Item> items, boolean withDescription) {
        final JList<HeroData.Item> list = new JList<HeroData.Item>(items.toArray(new HeroData.Item[0])) {
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
            @Override public String getToolTipText(MouseEvent e) {
                int i = locationToIndex(e.getPoint());
                if (i < 0 || !getCellBounds(i, i).contains(e.getPoint())) return null;
                HeroData.Item it = getModel().getElementAt(i);
                TooltipDB.Match m = db.find(it.name);
                if (m == null) return null;
                Map<String, String> x = new LinkedHashMap<>();
                if (!it.detail.isEmpty()) x.put("Details", it.detail);
                return db.html(m, x);
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
                panel.setBorder(new EmptyBorder(6, 10, 6, 10));
                JPanel center = new JPanel(new BorderLayout(0, 2));
                center.setOpaque(false);
                center.add(title, BorderLayout.NORTH);
                center.add(desc, BorderLayout.CENTER);
                panel.add(center, BorderLayout.CENTER);
                panel.add(right, BorderLayout.EAST);
            }
            public Component getListCellRendererComponent(JList<? extends HeroData.Item> l, HeroData.Item it, int i, boolean sel, boolean foc) {
                panel.setBackground(sel ? l.getSelectionBackground() : (i % 2 == 0 ? l.getBackground() : Theme.card()));
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
                int r = rowAtPoint(e.getPoint());
                if (r < 0) return null;
                String n = String.valueOf(getValueAt(r, 0));
                HeroData.Talent t = hero.talent(n);
                if (t != null) return talentTip(t);
                TooltipDB.Match m = db.find(n);
                return m == null ? null : db.html(m, null);
            }
        };
        styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(260);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 1; i < cols.length; i++) table.getColumnModel().getColumn(i).setCellRenderer(center);
        attachPopup(table, e -> {
            int r = table.rowAtPoint(e.getPoint());
            return r < 0 ? null : String.valueOf(table.getValueAt(r, 0));
        });
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

    // ---------------------------------------------------------------- Hilfsfunktionen

    private void styleTable(JTable t) {
        t.putClientProperty("modernhelden.own", true);
        t.setRowHeight(Math.round(t.getFont().getSize() * 2.1f));
        t.setFillsViewportHeight(true);
        t.setShowVerticalLines(false);
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
        s.setBorder(BorderFactory.createLineBorder(Theme.card().darker()));
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
