package de.modernhelden.print;

import de.modernhelden.TooltipDB;
import org.w3c.dom.Document;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.print.PrinterJob;
import java.io.File;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Moderne Druckvorschau: ersetzt den Inhalt des Reiters „Druckvorschau“ und
 * lässt per Umschalter jederzeit die Original-Druckvorschau anzeigen.
 */
public final class PrintPreviewPanel extends JPanel {

    private static final String[] ZOOMS = {"Seitenbreite", "Ganze Seite", "50 %", "75 %", "100 %", "125 %", "150 %"};

    private final TooltipDB db;
    private final Supplier<Document> data;
    private final Map<String, String> props;
    private final BiConsumer<String, String> save;
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final PagesView pages = new PagesView();
    private final JScrollPane scroll = new JScrollPane(pages);
    private final JToggleButton modern = new JToggleButton("Modern"), original = new JToggleButton("Original");
    private final JComboBox<String> zoom = new JComboBox<>(ZOOMS);
    private final JCheckBox desc = new JCheckBox("Beschreibungen & eigene Notizen zu SF, Vor-/Nachteilen und Zaubern"), notes = new JCheckBox("Notizfelder und Linienseite (auch bei Zaubern)"),
            meta = new JCheckBox("Meta-Talente zeigen (z. B. Wache halten, Kräuter suchen)"),
            portrait = new JCheckBox("Portrait neben der Charakterbeschreibung");
    private final JLabel info = new JLabel();
    private final JPanel modernTools = new JPanel();
    private final JButton refreshBtn = new JButton("Aktualisieren"), pdf = new JButton("Als PDF speichern …"), print = new JButton("Drucken …");
    private JComponent originalComp;
    private ModernSheet sheet;
    private String heroName = "Held";
    private boolean dirty = true;

    public PrintPreviewPanel(TooltipDB db, Supplier<Document> data, Map<String, String> props, BiConsumer<String, String> save) {
        super(new BorderLayout());
        this.db = db;
        this.data = data;
        this.props = props;
        this.save = save;
        putClientProperty("modernhelden.own", true);

        desc.setSelected(!"false".equals(props.get("druck.beschreibungen")));
        notes.setSelected(!"false".equals(props.get("druck.notizen")));
        // Meta-Talente sind zunächst aus; einschalten nur, wenn sie für den Helden in der Helden-Software aktiv sind
        meta.setSelected("true".equals(props.get("druck.meta")));
        portrait.setSelected(!"false".equals(props.get("druck.portrait")));
        String z = props.get("druck.zoom");
        zoom.setSelectedItem(z != null ? z : "Seitenbreite");

        ButtonGroup g = new ButtonGroup();
        g.add(modern);
        g.add(original);
        for (JToggleButton b : new JToggleButton[]{modern, original}) {
            b.putClientProperty("JButton.buttonType", "roundRect");
            b.setFocusable(false);
        }
        modern.setToolTipText("Moderner Heldenbogen");
        original.setToolTipText("Original-Druckvorschau der Helden-Software");
        modern.addActionListener(e -> showMode(true));
        original.addActionListener(e -> showMode(false));

        desc.addActionListener(e -> { save.accept("druck.beschreibungen", String.valueOf(desc.isSelected())); rebuild(); });
        notes.addActionListener(e -> { save.accept("druck.notizen", String.valueOf(notes.isSelected())); rebuild(); });
        portrait.addActionListener(e -> { save.accept("druck.portrait", String.valueOf(portrait.isSelected())); rebuild(); });
        // Meta-Talente liefert die Helden-Software nur auf Anfrage – daher Daten neu holen
        meta.addActionListener(e -> {
            if (meta.isSelected() && sheetData != null && !sheetData.metaAktiv) {
                meta.setSelected(false);
                metaMenue();
                metaHinweis();
                return;
            }
            save.accept("druck.meta", String.valueOf(meta.isSelected()));
            dirty = true;
            refresh();
        });
        zoom.addActionListener(e -> { save.accept("druck.zoom", String.valueOf(zoom.getSelectedItem())); pages.revalidate(); pages.repaint(); });
        refreshBtn.addActionListener(e -> { dirty = true; refresh(); });
        pdf.addActionListener(e -> exportPdf());
        print.addActionListener(e -> doPrint());
        print.putClientProperty("JButton.buttonType", "roundRect");
        pdf.setIcon(UIManager.getIcon("FileView.fileIcon"));

        Color sep = UIManager.getColor("Component.borderColor");
        if (sep == null) sep = Color.LIGHT_GRAY;
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, sep), new EmptyBorder(5, 8, 5, 8)));
        bar.add(modern);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(original);
        modernTools.setOpaque(false);
        modernTools.setLayout(new BoxLayout(modernTools, BoxLayout.X_AXIS));
        modernTools.add(Box.createHorizontalStrut(14));
        modernTools.add(new JLabel("Zoom "));
        zoom.setMaximumSize(zoom.getPreferredSize());
        modernTools.add(zoom);
        modernTools.add(Box.createHorizontalStrut(8));
        final JPopupMenu optMenu = new JPopupMenu();
        for (JCheckBox cb : new JCheckBox[]{desc, meta, notes, portrait}) {
            final JCheckBox src = cb;
            final JCheckBoxMenuItem mi = new JCheckBoxMenuItem(cb.getText(), cb.isSelected());
            mi.addActionListener(e -> {
                src.setSelected(mi.isSelected());
                for (java.awt.event.ActionListener l : src.getActionListeners()) l.actionPerformed(e);
                mi.setSelected(src.isSelected());   // Option kann abgelehnt werden (Meta-Talente)
            });
            if (cb == meta) metaItem = mi;
            optMenu.add(mi);
        }
        optMenu.addSeparator();
        JMenuItem bildWaehlen = new JMenuItem("Portrait wählen …");
        bildWaehlen.setToolTipText("Bild (JPG, PNG, GIF, BMP) für den aktuellen Helden hinterlegen");
        bildWaehlen.addActionListener(e -> portraitWaehlen());
        optMenu.add(bildWaehlen);
        JMenuItem bildWeg = new JMenuItem("Portrait entfernen (leeres Feld zum Zeichnen)");
        bildWeg.addActionListener(e -> { save.accept(portraitKey(), ""); props.put(portraitKey(), ""); portraitCache = null; rebuild(); });
        optMenu.add(bildWeg);
        optMenu.addSeparator();
        JMenuItem metaEin = new JMenuItem("Meta-Talente in der Helden-Software einschalten …");
        metaEin.setToolTipText("<html>Die Helden-Software berechnet Meta-Talente nur, wenn in ihren Druckeinstellungen<br>"
                + "„Metatalente“ aktiv ist. Öffnet diese Einstellungen.</html>");
        metaEin.addActionListener(e -> oeffneDruckeinstellungen());
        optMenu.add(metaEin);
        final JButton optBtn = new JButton("Optionen ▾");
        optBtn.addActionListener(e -> {
            // Menü liegt außerhalb des Komponentenbaums: aktuelles Design vor dem Anzeigen übernehmen
            SwingUtilities.updateComponentTreeUI(optMenu);
            optMenu.show(optBtn, 0, optBtn.getHeight());
        });
        modernTools.add(optBtn);
        bar.add(modernTools);
        bar.add(Box.createHorizontalGlue());
        info.setForeground(UIManager.getColor("Label.disabledForeground"));
        bar.add(info);
        bar.add(Box.createHorizontalStrut(8));
        refreshBtn.setToolTipText("Aktualisieren");
        bar.add(refreshBtn);
        bar.add(Box.createHorizontalStrut(4));
        pdf.setText("PDF …");
        pdf.setToolTipText("Als PDF speichern");
        bar.add(pdf);
        bar.add(Box.createHorizontalStrut(4));
        print.setText("Drucken …");
        bar.add(print);
        // Symbole nur in den modernen Designs; „↻“ als Text im Original-Design
        pdf.setIcon(de.modernhelden.ModernIcons.nurModern("pdf", 16));
        print.setIcon(de.modernhelden.ModernIcons.nurModern("print", 16));
        refreshBtn.setIcon(de.modernhelden.ModernIcons.nurModern("refresh", 16));
        Runnable symbole = () -> refreshBtn.setText(de.modernhelden.Theme.isModern() ? "" : "↻");
        symbole.run();
        UIManager.addPropertyChangeListener(ev -> {
            if ("lookAndFeel".equals(ev.getPropertyName())) SwingUtilities.invokeLater(symbole);
        });
        add(bar, BorderLayout.NORTH);

        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getViewport().addChangeListener(e -> { if ("Seitenbreite".equals(zoom.getSelectedItem()) || "Ganze Seite".equals(zoom.getSelectedItem())) pages.revalidate(); });
        center.add(scroll, "modern");
        add(center, BorderLayout.CENTER);

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) refresh();
        });
        showMode(!"original".equals(props.get("druck.modus")));
    }

    /** Setzt die Original-Druckvorschau, die über den Umschalter erreichbar bleibt. */
    public void setOriginal(JComponent comp) {
        if (originalComp != null) center.remove(originalComp);
        originalComp = comp;
        if (comp != null) center.add(comp, "original");
        original.setEnabled(comp != null);
        showMode(modern.isSelected() || comp == null);
    }

    public JComponent getOriginal() { return originalComp; }

    private void showMode(boolean mod) {
        if (!mod && originalComp == null) mod = true;
        modern.setSelected(mod);
        original.setSelected(!mod);
        cards.show(center, mod ? "modern" : "original");
        modernTools.setVisible(mod);
        pdf.setVisible(mod);
        print.setVisible(mod);
        refreshBtn.setVisible(mod);
        info.setVisible(mod);
        save.accept("druck.modus", mod ? "modern" : "original");
        if (mod) { dirty = true; refresh(); }
        revalidate();
        repaint();
    }

    private JCheckBoxMenuItem metaItem;

    private void metaMenue() { if (metaItem != null) metaItem.setSelected(meta.isSelected()); }

    /** Hinweis, wenn Meta-Talente eingeschaltet werden sollen, sie für den Helden aber nicht aktiviert sind. */
    private void metaHinweis() {
        String name = heroName == null || heroName.isEmpty() ? "diesen Charakter" : "„" + heroName + "“";
        Object[] knoepfe = {"Druckeinstellungen öffnen", "Abbrechen"};
        int w = JOptionPane.showOptionDialog(this,
                "<html>Meta-Talente müssen für " + esc(name) + " in der Helden-Software aktiviert sein.<br><br>"
                        + "Dazu in den Druckeinstellungen der Helden-Software „Metatalente“ einschalten.<br>"
                        + "Danach lässt sich die Option hier einschalten.</html>",
                "Meta-Talente", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, knoepfe, knoepfe[1]);
        if (w == 0) oeffneDruckeinstellungen();
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;"); }

    /**
     * Öffnet die Druckeinstellungen der Original-Druckvorschau (Zahnrad), in denen u. a.
     * „Metatalente“ eingeschaltet wird. Danach werden die Daten neu geladen.
     */
    private void oeffneDruckeinstellungen() {
        AbstractButton zahnrad = originalComp == null ? null : findeZahnrad(originalComp);
        if (zahnrad == null) {
            JOptionPane.showMessageDialog(this, "Bitte in der Original-Druckvorschau über das Zahnrad „Metatalente“ aktivieren.",
                    "Meta-Talente", JOptionPane.INFORMATION_MESSAGE);
            showMode(false);
            return;
        }
        java.util.Set<Window> vorher = new java.util.HashSet<>();
        for (Window w : Window.getWindows()) if (w.isShowing()) vorher.add(w);
        zahnrad.doClick();
        dirty = true;
        refresh();
        // Einstellungsfenster ist nicht modal: nach dem Schließen die Daten neu laden
        for (Window w : Window.getWindows()) {
            if (!w.isShowing() || vorher.contains(w)) continue;
            w.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentHidden(java.awt.event.ComponentEvent e) { markDirty(); }
            });
            w.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) { markDirty(); }
            });
        }
    }

    /** Erste Schaltfläche ohne Text, aber mit Symbol – in der Original-Vorschau das Zahnrad. */
    private static AbstractButton findeZahnrad(Container c) {
        for (Component ch : c.getComponents()) {
            if (ch instanceof AbstractButton) {
                AbstractButton b = (AbstractButton) ch;
                if (b.getIcon() != null && (b.getText() == null || b.getText().trim().isEmpty())) return b;
            }
            if (ch instanceof Container) {
                AbstractButton b = findeZahnrad((Container) ch);
                if (b != null) return b;
            }
        }
        return null;
    }

    /** Daten haben sich geändert – beim nächsten Anzeigen neu aufbauen. */
    public void markDirty() {
        dirty = true;
        if (isShowing()) refresh();
    }

    public void refresh() {
        if (!dirty || !modern.isSelected()) return;
        dirty = false;
        Document doc = null;
        try { doc = data.get(); } catch (Exception ignore) { }
        if (doc == null) {
            sheet = null;
            info.setText("Kein Held ausgewählt");
        } else {
            try {
                SheetData d = SheetData.parse(doc);
                heroName = d.name.isEmpty() ? "Held" : d.name;
                sheetData = d;
                rebuild();
                return;
            } catch (Exception ex) {
                ex.printStackTrace();
                sheet = null;
                info.setText("Fehler beim Aufbau: " + ex.getMessage());
            }
        }
        pages.revalidate();
        pages.repaint();
    }

    private SheetData sheetData;

    private void rebuild() {
        if (sheetData == null) return;
        ModernSheet.Options o = new ModernSheet.Options();
        o.beschreibungen = desc.isSelected();
        o.notizseite = notes.isSelected();
        // ohne Aktivierung in der Helden-Software liefert sie keine Meta-Talente – Option dann wieder aus
        if (meta.isSelected() && !sheetData.metaAktiv) {
            meta.setSelected(false);
            metaMenue();
        }
        o.metatalente = meta.isSelected();
        o.portrait = portrait.isSelected();
        o.portraitBild = o.portrait ? portraitBild() : null;
        sheet = new ModernSheet(sheetData, db, o);
        info.setText(sheet.getNumberOfPages() + (sheet.getNumberOfPages() == 1 ? " Seite" : " Seiten"));
        boolean hatMeta = false;
        for (SheetData.Talent t : sheetData.talente) hatMeta |= t.meta;
        info.setToolTipText(meta.isSelected() && !hatMeta
                ? "Keine Meta-Talente: Der Held hat keine der dafür nötigen Talente aktiviert" : null);
        pdf.setEnabled(true);
        print.setEnabled(true);
        pages.revalidate();
        pages.repaint();
    }

    // ------------------------------------------------------------ Portrait

    private String portraitCachePfad;
    private java.awt.image.BufferedImage portraitCache;

    /** Pro Held gespeichert (in den Plugin-Einstellungen), Schlüssel aus dem Heldennamen. */
    private String portraitKey() {
        String n = sheetData == null || sheetData.name.isEmpty() ? "held" : sheetData.name;
        return "druck.portrait.bild." + n.replaceAll("[^A-Za-z0-9ÄÖÜäöüß]+", "_");
    }

    /** Eigenes Bild aus den Plugin-Einstellungen, sonst das in der Helden-Software hinterlegte Portrait. */
    private java.awt.image.BufferedImage portraitBild() {
        String pfad = props.get(portraitKey());
        if ((pfad == null || pfad.isEmpty()) && sheetData != null) pfad = sheetData.bildPfad;
        if (pfad == null || pfad.trim().isEmpty()) return null;
        if (pfad.equals(portraitCachePfad)) return portraitCache;
        portraitCachePfad = pfad;
        portraitCache = null;
        try {
            java.io.File f = new java.io.File(pfad);
            if (f.isFile()) portraitCache = javax.imageio.ImageIO.read(f);
        } catch (Exception ex) {
            portraitCache = null;
        }
        return portraitCache;
    }

    private void portraitWaehlen() {
        if (sheetData == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Portrait für " + heroName + " wählen");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Bilder (JPG, PNG, GIF, BMP)", "jpg", "jpeg", "png", "gif", "bmp"));
        String alt = props.get(portraitKey());
        if (alt != null && !alt.isEmpty()) fc.setSelectedFile(new java.io.File(alt));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File f = fc.getSelectedFile();
        java.awt.image.BufferedImage img = null;
        try { img = javax.imageio.ImageIO.read(f); } catch (Exception ignore) { }
        if (img == null) {
            JOptionPane.showMessageDialog(this, "Die Datei konnte nicht als Bild gelesen werden.", "Portrait", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String pfad = f.getAbsolutePath();
        props.put(portraitKey(), pfad);
        save.accept(portraitKey(), pfad);
        portraitCachePfad = pfad;
        portraitCache = img;
        if (!portrait.isSelected()) { portrait.setSelected(true); save.accept("druck.portrait", "true"); }
        rebuild();
    }

    private void doPrint() {
        if (sheet == null) return;
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName("Heldenbogen – " + heroName);
            job.setPageable(sheet);
            if (job.printDialog()) job.print();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Drucken fehlgeschlagen: " + ex.getMessage(), "Druckvorschau", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportPdf() {
        if (sheet == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("PDF-Dokument", "pdf"));
        String dir = props.get("druck.pdfordner");
        if (dir != null && new File(dir).isDirectory()) fc.setCurrentDirectory(new File(dir));
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), heroName.replaceAll("[\\\\/:*?\"<>|]", "_") + " - Heldenbogen.pdf"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".pdf")) f = new File(f.getParentFile(), f.getName() + ".pdf");
        save.accept("druck.pdfordner", f.getParent());
        final File target = f;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            PdfWriter.write(sheet, target);
            int r = JOptionPane.showConfirmDialog(this, "PDF gespeichert:\n" + target.getAbsolutePath() + "\n\nJetzt öffnen?",
                    "Druckvorschau", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (r == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) Desktop.getDesktop().open(target);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "PDF konnte nicht gespeichert werden: " + ex.getMessage(), "Druckvorschau", JOptionPane.ERROR_MESSAGE);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    /** Zeichnet die Seiten untereinander mit Schatten. */
    private final class PagesView extends JPanel implements Scrollable {
        private static final int GAP = 24;

        PagesView() { setOpaque(true); }

        double scale() {
            String z = String.valueOf(zoom.getSelectedItem());
            Dimension vp = scroll.getViewport().getExtentSize();
            double px = 96.0 / 72.0;
            if (z.startsWith("Seitenbreite")) return Math.max(0.2, (vp.width - 2 * GAP) / ModernSheet.W);
            if (z.startsWith("Ganze")) return Math.max(0.2, Math.min((vp.width - 2 * GAP) / ModernSheet.W, (vp.height - 2 * GAP) / ModernSheet.H));
            try { return Integer.parseInt(z.replaceAll("[^0-9]", "")) / 100.0 * px; } catch (Exception e) { return px; }
        }

        @Override public Dimension getPreferredSize() {
            int n = sheet == null ? 1 : sheet.getNumberOfPages();
            double s = scale();
            return new Dimension((int) (ModernSheet.W * s) + 2 * GAP, (int) ((ModernSheet.H * s + GAP) * n) + GAP);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Color bg = UIManager.getColor("Panel.background");
            if (bg == null) bg = Color.LIGHT_GRAY;
            g.setColor(darker(bg));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (sheet == null) {
                g.setColor(UIManager.getColor("Label.disabledForeground"));
                String t = "Kein Held ausgewählt";
                g.drawString(t, (getWidth() - g.getFontMetrics().stringWidth(t)) / 2, 60);
                g.dispose();
                return;
            }
            ModernSheet.hints(g);
            double s = scale();
            int pw = (int) (ModernSheet.W * s), ph = (int) (ModernSheet.H * s);
            int x = Math.max(GAP, (getWidth() - pw) / 2);
            Rectangle clip = g.getClipBounds();
            for (int i = 0; i < sheet.getNumberOfPages(); i++) {
                int y = GAP + i * (ph + GAP);
                if (clip != null && (y > clip.y + clip.height || y + ph < clip.y)) continue;
                for (int sh = 6; sh > 0; sh--) {
                    g.setColor(new Color(0, 0, 0, 10));
                    g.fill(new RoundRectangle2D.Float(x - sh + 2, y - sh + 4, pw + 2 * sh - 4, ph + 2 * sh - 4, 10, 10));
                }
                Graphics2D pg = (Graphics2D) g.create(x, y, pw, ph);
                pg.transform(AffineTransform.getScaleInstance(s, s));
                sheet.paintPage(pg, i);
                pg.dispose();
            }
            g.dispose();
        }

        private Color darker(Color c) {
            boolean dark = (c.getRed() + c.getGreen() + c.getBlue()) / 3 < 128;
            int d = dark ? -10 : -18;
            return new Color(Math.max(0, c.getRed() + d), Math.max(0, c.getGreen() + d), Math.max(0, c.getBlue() + d));
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        public boolean getScrollableTracksViewportWidth() { return getParent() != null && getParent().getWidth() > getPreferredSize().width; }
        public boolean getScrollableTracksViewportHeight() { return getParent() != null && getParent().getHeight() > getPreferredSize().height; }
    }
}
