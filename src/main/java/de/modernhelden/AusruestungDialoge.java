package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bearbeiten-Dialoge des Reiters „Ausrüstung“ (Nahkampfwaffen, Fernkampfwaffen, Schilde/Parierwaffen, Rüstung)
 * in den modernen Designs: neu angeordnet (Plätze links, Gegenstände mit Werten rechts), mit Beschriftungen und
 * Tooltips, Doppelklick und Ziehen. Alle Bedienelemente sind die der Helden-Software und behalten ihre
 * Funktion; übernommen wird wie bisher mit „OK“. Die Dialoge werden bei jedem Öffnen neu gebaut –
 * im Design „Original“ bleiben sie unverändert.
 */
final class AusruestungDialoge {

    private AusruestungDialoge() {}

    /** Auftrag aus der Übersicht: einen angelegten Gegenstand ablegen (Platz bzw. Rüstungsteil). */
    static final class Ablegen {
        final boolean ruestung;
        final int nummer, vorkommen;
        final String name;
        Ablegen(boolean ruestung, int nummer, String name, int vorkommen) {
            this.ruestung = ruestung; this.nummer = nummer; this.name = name; this.vorkommen = vorkommen;
        }
    }

    /** Gesetzt, während die Übersicht einen Dialog zum Ablegen öffnet. */
    static Ablegen auftrag;

    private static boolean versteckenInstalliert;

    /**
     * Ablegen ohne sichtbares Fenster: Der Dialog der Helden-Software wird noch vor dem Anzeigen außerhalb des
     * Bildschirms platziert (beim Erzeugen des Fensters), erledigt dort seine Arbeit und schließt sich gleich wieder.
     */
    static void unsichtbarAusfuehren(Ablegen a, Runnable oeffnen) {
        if (!versteckenInstalliert) {
            versteckenInstalliert = true;
            Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
                if (auftrag == null || !(ev instanceof java.awt.event.HierarchyEvent)) return;
                java.awt.event.HierarchyEvent h = (java.awt.event.HierarchyEvent) ev;
                if ((h.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
                if (h.getComponent() instanceof JDialog && h.getComponent().isDisplayable())
                    h.getComponent().setLocation(-30000, -30000);
            }, AWTEvent.HIERARCHY_EVENT_MASK);
        }
        auftrag = a;
        try { oeffnen.run(); } finally { auftrag = null; }
    }

    private static final Pattern PLATZ = Pattern.compile("^(Waffe|FKW|Schild)\\s*(\\d)$");

    /** Beim Öffnen eines Dialogs (nur moderne Designs) aufrufen. */
    static void anwenden(JDialog d) {
        if (d.getRootPane().getClientProperty("modernhelden.ausr") != null) return;
        JPanel innen = innen(d.getContentPane());
        if (innen == null) return;
        // Nahkampf-Dialog: das Panel liegt in einer Scrollfläche – herausnehmen, die neue Anordnung scrollt selbst
        if (innen.getParent() instanceof JViewport) {
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, innen);
            Container p = sp == null ? null : sp.getParent();
            if (p != null && p.getLayout() instanceof BorderLayout) {
                Object pos = ((BorderLayout) p.getLayout()).getConstraints(sp);
                p.remove(sp);
                sp.getViewport().setView(null);
                p.add(innen, pos == null ? BorderLayout.CENTER : pos);
            }
        }
        List<JTable> tabellen = new ArrayList<>();
        for (Component k : innen.getComponents()) {
            JTable t = tabelle(k);
            if (t != null) tabellen.add(t);
        }
        if (tabellen.isEmpty()) return;
        AbstractButton add = null, rem = null;
        boolean plaetze = false;
        for (Component k : innen.getComponents()) {
            if ("ADD".equals(k.getName())) add = (AbstractButton) k;
            if ("REM".equals(k.getName())) rem = (AbstractButton) k;
            if (k instanceof JLabel && PLATZ.matcher(String.valueOf(((JLabel) k).getText()).trim()).matches()) plaetze = true;
        }
        try {
            if (tabellen.size() == 2 && add != null && rem != null) {
                d.getRootPane().putClientProperty("modernhelden.ausr", Boolean.TRUE);
                ruestung(d, innen, tabellen, add, rem);
            } else if (tabellen.size() == 1 && plaetze) {
                d.getRootPane().putClientProperty("modernhelden.ausr", Boolean.TRUE);
                ohneDoppelte(tabellen.get(0));
                plaetze(d, innen, tabellen.get(0));
            }
        } catch (RuntimeException ignore) {
            // im Zweifel bleibt der Dialog, wie ihn die Helden-Software gebaut hat
        }
        if (d.getRootPane().getClientProperty("modernhelden.ausr") != null) navigationOhneRahmen(d.getContentPane());
        if (auftrag != null) ablegen(d, innen, tabellen, add, rem, auftrag);
    }

    /**
     * Ablegen aus der Übersicht: im Dialog der Helden-Software den Platz leeren (bzw. das Rüstungsteil ablegen)
     * und mit ihrem „OK“ übernehmen. Klappt das nicht eindeutig, bleibt der Dialog offen.
     */
    private static void ablegen(JDialog d, JPanel innen, List<JTable> tabellen, AbstractButton addR, AbstractButton remR, Ablegen a) {
        boolean erledigt = false;
        if (a.ruestung && remR != null && !tabellen.isEmpty()) {
            JTable angelegt = tabellen.get(0);   // beim Umbau nach Spalten sortiert: links = angelegt
            int treffer = -1, gesehen = 0;
            for (int r = 0; r < angelegt.getRowCount(); r++) {
                if (a.name.equals(String.valueOf(angelegt.getValueAt(r, angelegt.convertColumnIndexToView(1))).trim())) {
                    if (gesehen++ == a.vorkommen) { treffer = r; break; }
                }
            }
            if (treffer >= 0) {
                angelegt.setRowSelectionInterval(treffer, treffer);
                remR.doClick();
                erledigt = true;
            }
        } else if (!a.ruestung) {
            // Platz-Dialog: der n-te REM-Knopf (Namen REM0… bzw. REM1…), Reihenfolge wie die Zeilen
            List<AbstractButton> rems = new ArrayList<>();
            for (AbstractButton b : alleKnoepfe(d.getContentPane()))
                if (b.getName() != null && b.getName().matches("REM\\d+")) rems.add(b);
            rems.sort(Comparator.comparingInt(b -> Integer.parseInt(b.getName().substring(3))));
            if (a.nummer >= 1 && a.nummer <= rems.size()) {
                rems.get(a.nummer - 1).doClick();
                erledigt = true;
            }
        }
        // ab hier wieder normale Fenster (z. B. Meldungen der Helden-Software beim Übernehmen)
        auftrag = null;
        AbstractButton ok = null;
        for (AbstractButton b : alleKnoepfe(d.getContentPane()))
            if ("OK".equals(b.getName()) || "OK".equals(b.getText())) { ok = b; break; }
        if (!erledigt || ok == null) {
            // nicht eindeutig: Dialog sichtbar machen, damit man selbst entscheiden kann
            InventarLayout.groesse(d, Math.max(d.getWidth(), 960), Math.max(d.getHeight(), 560));
            return;
        }
        SwingUtilities.invokeLater(ok::doClick);
    }

    private static List<AbstractButton> alleKnoepfe(Container c) {
        List<AbstractButton> l = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton) l.add((AbstractButton) k);
            if (k instanceof Container) l.addAll(alleKnoepfe((Container) k));
        }
        return l;
    }

    /** Schwarze Linie um die Knopfleiste (OK/Abbrechen) der Helden-Software entfernen. */
    private static void navigationOhneRahmen(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof JPanel && "Navigationspanel".equals(k.getName())) ((JPanel) k).setBorder(new EmptyBorder(6, 6, 6, 6));
            else if (k instanceof Container) navigationOhneRahmen((Container) k);
        }
    }

    /** Das GridBag-Panel mit Tabelle und ADD/REM-Knöpfen. */
    private static JPanel innen(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof JPanel && ((JPanel) k).getLayout() instanceof GridBagLayout) {
                for (Component l : ((JPanel) k).getComponents()) {
                    String n = l.getName();
                    if (n != null && (n.startsWith("ADD") || n.startsWith("REM"))) return (JPanel) k;
                }
            }
            if (k instanceof Container) {
                JPanel p = innen((Container) k);
                if (p != null) return p;
            }
        }
        return null;
    }

    private static JTable tabelle(Component k) {
        if (!(k instanceof JScrollPane)) return null;
        Component v = ((JScrollPane) k).getViewport().getView();
        if (!(v instanceof JTable)) return null;
        TableModel m = ((JTable) v).getModel();
        return m.getColumnCount() >= 3 && "Name".equals(m.getColumnName(1)) ? (JTable) v : null;
    }

    // ------------------------------------------------------------------ doppelte Gegenstände in der Liste

    /**
     * Die Helden-Software stellt die Liste „Im Inventar“ aus zwei Gegenstandsarten zusammen (z. B. Nahkampfwaffen
     * und Parierwaffen). Ein Gegenstand, der zu beiden gehört (etwa der Langdolch), steht dadurch zweimal darin.
     * Die Tabelle zeigt ihn nur einmal: Ein vorgeschaltetes Modell blendet das zweite Vorkommen desselben
     * Gegenstands aus. Die Helden-Software liest den gewählten Gegenstand über getModel() der Tabelle und
     * bekommt so weiterhin genau den markierten.
     */
    private static void ohneDoppelte(JTable t) {
        if (t.getModel() instanceof OhneDoppelte || t.getModel().getColumnCount() < 3) return;
        // Spalten (Breiten, Renderer, Info-Knopf) der Helden-Software behalten
        t.setAutoCreateColumnsFromModel(false);
        t.setModel(new OhneDoppelte(t.getModel()));
    }

    private static final class OhneDoppelte extends javax.swing.table.AbstractTableModel {
        final TableModel m;
        int[] zeilen = new int[0];
        int anzahl = -1;

        OhneDoppelte(TableModel m) {
            this.m = m;
            neu();
            m.addTableModelListener(e -> {
                int vorher = zeilen.length;
                neu();
                if (e.getType() == javax.swing.event.TableModelEvent.UPDATE && vorher == zeilen.length && zeilen.length > 0
                        && e.getFirstRow() != javax.swing.event.TableModelEvent.HEADER_ROW)
                    fireTableRowsUpdated(0, zeilen.length - 1);
                else fireTableDataChanged();
            });
        }

        void neu() {
            anzahl = m.getRowCount();
            Set<Object> gesehen = Collections.newSetFromMap(new IdentityHashMap<>());
            List<Integer> l = new ArrayList<>();
            for (int i = 0; i < anzahl; i++) {
                Object g = m.getValueAt(i, 2);
                if (g == null || gesehen.add(g)) l.add(i);
            }
            zeilen = new int[l.size()];
            for (int i = 0; i < zeilen.length; i++) zeilen[i] = l.get(i);
        }

        private int z(int r) {
            if (m.getRowCount() != anzahl) neu();
            return r >= 0 && r < zeilen.length ? zeilen[r] : r;
        }

        @Override public int getRowCount() { if (m.getRowCount() != anzahl) neu(); return zeilen.length; }
        @Override public int getColumnCount() { return m.getColumnCount(); }
        @Override public String getColumnName(int c) { return m.getColumnName(c); }
        @Override public Class<?> getColumnClass(int c) { return m.getColumnClass(c); }
        @Override public Object getValueAt(int r, int c) { return m.getValueAt(z(r), c); }
        @Override public boolean isCellEditable(int r, int c) { return m.isCellEditable(z(r), c); }
        @Override public void setValueAt(Object v, int r, int c) { m.setValueAt(v, z(r), c); }
    }

    // ------------------------------------------------------------------ Dialoge mit Plätzen (Nah-, Fernkampf, Schilde)

    private static final class Zeile {
        int y0, y1;            // Zeilenbereich (gridy) im Original
        JLabel label;
        JTextField feld;
        AbstractButton add, rem;
        final List<Component> rest = new ArrayList<>();
        JPanel panel;
    }

    private static void plaetze(JDialog d, JPanel innen, JTable tabelle) {
        GridBagLayout gb = (GridBagLayout) innen.getLayout();
        Map<Component, GridBagConstraints> c = new LinkedHashMap<>();
        for (Component k : innen.getComponents()) c.put(k, gb.getConstraints(k));
        JScrollPane listeScroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tabelle);
        if (listeScroll == null) return;

        // Zeilen anhand der Beschriftungen „Waffe1“, „FKW2“, „Schild 1“
        List<Zeile> zeilen = new ArrayList<>();
        String art = "";
        for (Map.Entry<Component, GridBagConstraints> e : c.entrySet()) {
            if (!(e.getKey() instanceof JLabel)) continue;
            Matcher m = PLATZ.matcher(String.valueOf(((JLabel) e.getKey()).getText()).trim());
            if (!m.matches()) continue;
            art = m.group(1);
            Zeile z = new Zeile();
            z.label = (JLabel) e.getKey();
            z.y0 = e.getValue().gridy;
            z.y1 = e.getValue().gridy + Math.max(1, e.getValue().gridheight) - 1;
            String nr = m.group(2);
            z.label.setText(("Waffe".equals(art) ? "Waffe " : "FKW".equals(art) ? "Fernkampf " : "Schild ") + nr);
            zeilen.add(z);
        }
        zeilen.sort(Comparator.comparingInt(z -> z.y0));
        if (zeilen.isEmpty()) return;
        List<Component> lose = new ArrayList<>();
        for (Map.Entry<Component, GridBagConstraints> e : c.entrySet()) {
            Component k = e.getKey();
            if (k == listeScroll || zeilenLabel(zeilen, k)) continue;
            int y = e.getValue().gridy;
            Zeile z = null;
            for (Zeile zz : zeilen) if (y >= zz.y0 && y <= zz.y1) { z = zz; break; }
            String n = k.getName() == null ? "" : k.getName();
            if (z == null) {
                if (k instanceof JLabel && String.valueOf(((JLabel) k).getText()).trim().isEmpty()) continue;   // Abstandhalter
                lose.add(k);
                continue;
            }
            if (k instanceof JTextField && z.feld == null) z.feld = (JTextField) k;
            else if (n.startsWith("ADD")) z.add = (AbstractButton) k;
            else if (n.startsWith("REM")) z.rem = (AbstractButton) k;
            else z.rest.add(k);
        }
        // weitere Bedienelemente einer Zeile in ihrer Spaltenreihenfolge
        for (Zeile z : zeilen) z.rest.sort(Comparator.comparingInt((Component k) -> c.get(k).gridx).thenComparingInt(k -> c.get(k).gridy));

        // Beschriftungen und Tooltips
        String ding = "Waffe".equals(art) ? "Nahkampfwaffe" : "FKW".equals(art) ? "Fernkampfwaffe" : "Schild oder Parierwaffe";
        for (Zeile z : zeilen) {
            if (z.feld != null) {
                z.feld.setColumns(8);
                z.feld.putClientProperty("JTextField.placeholderText", "frei");
                z.feld.setToolTipText("Hierher ziehen oder in der Liste doppelklicken, um eine " + ding + " anzulegen");
            }
            if (z.add != null) z.add.setToolTipText("Markierten Gegenstand aus der Liste auf diesen Platz legen");
            if (z.rem != null) z.rem.setToolTipText("Diesen Platz leeren");
            int radio = 0;
            for (Component k : z.rest) {
                if (k instanceof AbstractButton && "I".equals(((AbstractButton) k).getText())) {
                    ((AbstractButton) k).setText("Werte …");
                    ((AbstractButton) k).setToolTipText("Waffenwerte bearbeiten: Bruchfaktor (aktuell/minimal) und Kampftalent");
                } else if (k instanceof JRadioButton) {
                    radio++;
                    ((JRadioButton) k).setText("Schild " + radio);
                    ((JRadioButton) k).setToolTipText("<html>Schild bzw. Parierwaffe " + radio + " zusammen mit dieser Waffe führen</html>");
                } else if (k instanceof JComboBox && String.valueOf(k.getName()).startsWith("hand")) {
                    ((JComponent) k).setToolTipText("Hand, in der die Waffe geführt wird");
                } else if (k instanceof JCheckBox && String.valueOf(((JCheckBox) k).getText()).startsWith("ist NKW")) {
                    ((JComponent) k).setToolTipText("Die Parierwaffe ist zugleich eine Nahkampfwaffe");
                } else if (k instanceof JCheckBox && String.valueOf(((JCheckBox) k).getText()).startsWith("Jagd")) {
                    ((JComponent) k).setToolTipText("Als Jagdwaffe verwenden (für Jagd-Talente)");
                } else if (k instanceof JComboBox) {
                    ((JComponent) k).setToolTipText("Schild oder Parierwaffe");
                }
            }
        }

        // ---- neu anordnen
        innen.removeAll();
        innen.setLayout(new BorderLayout(14, 10));
        innen.setBorder(new EmptyBorder(12, 12, 8, 12));
        JLabel hinweis = new JLabel("<html>Gegenstand rechts doppelklicken (nächster freier Platz) oder auf einen Platz ziehen. "
                + "„Werte …“ ändert Bruchfaktor und Talent, „OK“ übernimmt.</html>");
        hinweis.setForeground(Theme.muted());
        innen.add(hinweis, BorderLayout.NORTH);

        JPanel links = new JPanel();
        links.setLayout(new BoxLayout(links, BoxLayout.Y_AXIS));
        links.setOpaque(false);
        // Zeilen und lose Elemente (z. B. „Beidhändiger Kampf“, „keine“) in der Reihenfolge des Originals
        List<Object> bloecke = new ArrayList<>(zeilen);
        Map<Integer, List<Component>> loseJeY = new TreeMap<>();
        for (Component k : lose) loseJeY.computeIfAbsent(c.get(k).gridy, y -> new ArrayList<>()).add(k);
        bloecke.addAll(loseJeY.values());
        bloecke.sort(Comparator.comparingInt(b -> b instanceof Zeile ? ((Zeile) b).y0 : c.get(((List<?>) b).get(0)).gridy));
        for (Object b : bloecke) {
            if (b instanceof Zeile) links.add(zeilePanel((Zeile) b));
            else {
                JComponent lz = loseZeile((List<Component>) b);
                if (lz != null) links.add(lz);
            }
            links.add(Box.createVerticalStrut(6));
        }
        links.add(Box.createVerticalGlue());
        JPanel linksAussen = new BreiteFolgen();
        linksAussen.setOpaque(false);
        linksAussen.add(links, BorderLayout.NORTH);
        JScrollPane linksScroll = new JScrollPane(linksAussen, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        linksScroll.setBorder(null);
        linksScroll.setOpaque(false);
        linksScroll.getViewport().setOpaque(false);
        innen.add(linksScroll, BorderLayout.CENTER);

        innen.add(liste(tabelle, listeScroll, "Im Inventar", art), BorderLayout.EAST);

        // ---- Doppelklick und Ziehen
        tabelle.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e) || tabelle.getSelectedRow() < 0) return;
                for (Zeile z : zeilen) {
                    if (z.feld != null && z.feld.getText().trim().isEmpty() && z.add != null) { z.add.doClick(); return; }
                }
                JOptionPane.showMessageDialog(d, "Alle Plätze sind belegt. Bitte zuerst einen Platz leeren.", d.getTitle(),
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        ziehbar(tabelle);
        for (Zeile z : zeilen) {
            if (z.add == null) continue;
            final AbstractButton add = z.add;
            final JPanel zp = z.panel;
            final javax.swing.border.Border normal = zp.getBorder();
            final javax.swing.border.Border markiert = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.accent(), 2), new EmptyBorder(5, 7, 5, 7));
            final Runnable aus = () -> { if (zp.getBorder() != normal) zp.setBorder(normal); };
            TransferHandler ablegen = annehmen(tabelle, () -> { aus.run(); add.doClick(); },
                    () -> { if (zp.getBorder() != markiert) zp.setBorder(markiert); });
            z.panel.setTransferHandler(ablegen);
            if (z.feld != null) z.feld.setTransferHandler(ablegen);
            // Markierung weg, wenn das Ziehen die Zeile verlässt
            java.awt.dnd.DropTargetAdapter weg = new java.awt.dnd.DropTargetAdapter() {
                @Override public void dragExit(java.awt.dnd.DropTargetEvent e) {
                    // der Wechsel zwischen Zeile und Feld löst ebenfalls „verlassen“ aus – kurz später prüfen
                    SwingUtilities.invokeLater(() -> {
                        Point p = MouseInfo.getPointerInfo() == null ? null : MouseInfo.getPointerInfo().getLocation();
                        if (p == null || !zp.isShowing()) { aus.run(); return; }
                        SwingUtilities.convertPointFromScreen(p, zp);
                        if (!zp.contains(p)) aus.run();
                    });
                }
                @Override public void drop(java.awt.dnd.DropTargetDropEvent e) { }
            };
            for (JComponent k : new JComponent[]{z.panel, z.feld}) {
                try { if (k != null && k.getDropTarget() != null) k.getDropTarget().addDropTargetListener(weg); }
                catch (java.util.TooManyListenersException ignore) { }
            }
        }

        Dimension s = d.getSize();
        if (auftrag == null) InventarLayout.groesse(d, Math.max(s.width + 360, "Waffe".equals(art) ? 1120 : 960), Math.max(s.height + 60, "FKW".equals(art) ? 430 : 560));
        innen.revalidate();
        innen.repaint();
    }

    /** Panel, dessen Breite der Scrollfläche folgt (nur senkrecht scrollen). */
    private static final class BreiteFolgen extends JPanel implements Scrollable {
        BreiteFolgen() { super(new BorderLayout()); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 160; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static boolean zeilenLabel(List<Zeile> zeilen, Component k) {
        for (Zeile z : zeilen) if (z.label == k) return true;
        return false;
    }

    /** Eine Zeile: Nummer, Feld, weitere Bedienelemente, Anlegen/Leeren – als Karte. */
    private static JComponent zeilePanel(Zeile z) {
        // feste Größen aus dem Original-Layout aufheben (sonst werden Texte abgeschnitten)
        List<Component> alle = new ArrayList<>(z.rest);
        alle.add(z.label);
        if (z.feld != null) alle.add(z.feld);
        for (Component k : alle) { k.setPreferredSize(null); k.setMinimumSize(null); k.setMaximumSize(null); }
        JPanel p = new JPanel(new BorderLayout(8, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setBackground(Theme.card());
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(6, 8, 6, 8)));
        p.setAlignmentX(0);
        z.label.setFont(z.label.getFont().deriveFont(Font.BOLD));
        z.label.setPreferredSize(new Dimension(Math.max(z.label.getPreferredSize().width, 70), z.label.getPreferredSize().height));
        p.add(z.label, BorderLayout.WEST);
        JPanel mitte = new JPanel(new BorderLayout(6, 0));
        mitte.setOpaque(false);
        if (z.add != null) mitte.add(z.add, BorderLayout.WEST);
        if (z.feld != null) mitte.add(z.feld, BorderLayout.CENTER);
        p.add(mitte, BorderLayout.CENTER);
        JPanel rechts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rechts.setOpaque(false);
        for (Component k : z.rest) {
            if (k instanceof JRadioButton) ((JRadioButton) k).setOpaque(false);
            if (k instanceof JCheckBox) ((JCheckBox) k).setOpaque(false);
            rechts.add(k);
        }
        if (z.rem != null) rechts.add(z.rem);
        p.add(rechts, BorderLayout.EAST);
        z.panel = p;
        InventarLayout.eigen(mitte);
        InventarLayout.eigen(rechts);
        return p;
    }

    /** Lose Elemente einer Original-Zeile, z. B. „Beidhändiger Kampf“ oder die Auswahl „keine“ für die Schilde. */
    private static JComponent loseZeile(List<Component> l) {
        List<Component> sichtbar = new ArrayList<>();
        boolean radios = false;
        for (Component k : l) {
            if (k instanceof JLabel) {
                String t = String.valueOf(((JLabel) k).getText()).trim();
                if (t.startsWith("S/P")) continue;          // Spaltenköpfe: die Knöpfe sind jetzt beschriftet
                if ("keine".equals(t)) continue;
            }
            if (k instanceof JRadioButton) radios = true;
            sichtbar.add(k);
        }
        if (sichtbar.isEmpty()) return null;
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setOpaque(false);
        p.setAlignmentX(0);
        if (radios) {
            JLabel t = new JLabel("Keiner Waffe zugeordnet:");
            t.setForeground(Theme.muted());
            p.add(t);
        }
        int r = 0;
        for (Component k : sichtbar) {
            if (k instanceof JRadioButton) {
                r++;
                ((JRadioButton) k).setText("Schild " + r);
                ((JRadioButton) k).setToolTipText("Schild bzw. Parierwaffe " + r + " mit keiner Waffe führen");
                ((JRadioButton) k).setOpaque(false);
            }
            if (k instanceof JCheckBox) {
                JCheckBox cb = (JCheckBox) k;
                cb.setText(cb.getText().replace(": ", "").trim());
                cb.setOpaque(false);
                if (cb.getToolTipText() != null) cb.setToolTipText("<html>" + cb.getToolTipText().replace("\n", "<br>") + "</html>");
            }
            p.add(k);
        }
        InventarLayout.eigen(p);
        return p;
    }

    // ------------------------------------------------------------------ Rüstung (zwei Listen)

    private static void ruestung(JDialog d, JPanel innen, List<JTable> tabellen, AbstractButton add, AbstractButton rem) {
        GridBagLayout gb = (GridBagLayout) innen.getLayout();
        tabellen.sort(Comparator.comparingInt(t -> gb.getConstraints(SwingUtilities.getAncestorOfClass(JScrollPane.class, t)).gridx));
        JTable angelegt = tabellen.get(0), inventar = tabellen.get(1);
        JScrollPane sl = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, angelegt);
        JScrollPane sr = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, inventar);
        add.setToolTipText("Markiertes Rüstungsteil aus dem Inventar anlegen");
        rem.setToolTipText("Markiertes Rüstungsteil ablegen");

        innen.removeAll();
        innen.setLayout(new BorderLayout(0, 10));
        innen.setBorder(new EmptyBorder(12, 12, 8, 12));
        JLabel hinweis = new JLabel("<html>Doppelklick oder Ziehen legt ein Rüstungsteil an bzw. ab. „OK“ übernimmt.</html>");
        hinweis.setForeground(Theme.muted());
        innen.add(hinweis, BorderLayout.NORTH);
        JPanel mitte = new JPanel(new GridBagLayout());
        mitte.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weighty = 1;
        g.weightx = 1;
        g.gridx = 0;
        mitte.add(liste(angelegt, sl, "In diesem Set angelegt", "R"), g);
        JPanel knoepfe = new JPanel();
        knoepfe.setLayout(new BoxLayout(knoepfe, BoxLayout.Y_AXIS));
        knoepfe.setOpaque(false);
        knoepfe.add(Box.createVerticalGlue());
        add.setAlignmentX(0.5f);
        rem.setAlignmentX(0.5f);
        knoepfe.add(add);
        knoepfe.add(Box.createVerticalStrut(8));
        knoepfe.add(rem);
        knoepfe.add(Box.createVerticalGlue());
        g.gridx = 1;
        g.weightx = 0;
        g.insets = new Insets(0, 10, 0, 10);
        mitte.add(knoepfe, g);
        g.gridx = 2;
        g.weightx = 1;
        g.insets = new Insets(0, 0, 0, 0);
        mitte.add(liste(inventar, sr, "Im Inventar", "R"), g);
        innen.add(mitte, BorderLayout.CENTER);
        InventarLayout.eigen(mitte);
        InventarLayout.eigen(knoepfe);

        doppelklick(inventar, add);
        doppelklick(angelegt, rem);
        ziehbar(inventar);
        ziehbar(angelegt);
        // Ziehen von rechts nach links legt an, von links nach rechts ab
        TransferHandler anlegen = annehmen(inventar, add::doClick), ablegen = annehmen(angelegt, rem::doClick);
        angelegt.setTransferHandler(kombiniert(anlegen, inventar, angelegt));
        sl.setTransferHandler(anlegen);
        sl.getViewport().setTransferHandler(anlegen);
        inventar.setTransferHandler(kombiniert(ablegen, angelegt, inventar));
        sr.setTransferHandler(ablegen);
        sr.getViewport().setTransferHandler(ablegen);
        angelegt.setFillsViewportHeight(true);
        inventar.setFillsViewportHeight(true);

        Dimension s = d.getSize();
        if (auftrag == null) InventarLayout.groesse(d, Math.max(s.width + 260, 960), Math.max(s.height + 60, 560));
        innen.revalidate();
        innen.repaint();
    }

    private static void doppelklick(JTable t, AbstractButton b) {
        t.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && t.getSelectedRow() >= 0) b.doClick();
            }
        });
    }

    // ------------------------------------------------------------------ Liste mit Werten

    /** Karte mit Titel und der Gegenstandsliste; der Name zeigt darunter die wichtigsten Werte. */
    private static JComponent liste(JTable t, JScrollPane sp, String titel, String art) {
        listenStil(t, art);
        // die Helden-Software setzt nach jeder Änderung ein neues Modell (und damit neue Spalten)
        t.addPropertyChangeListener("model", e -> SwingUtilities.invokeLater(() -> listenStil(t, art)));
        t.getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {
            boolean geplant;
            public void columnAdded(javax.swing.event.TableColumnModelEvent e) {
                if (geplant) return;
                geplant = true;
                SwingUtilities.invokeLater(() -> { geplant = false; listenStil(t, art); });
            }
            public void columnRemoved(javax.swing.event.TableColumnModelEvent e) { }
            public void columnMoved(javax.swing.event.TableColumnModelEvent e) { }
            public void columnMarginChanged(javax.swing.event.ChangeEvent e) { }
            public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) { }
        });
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        JLabel tl = new JLabel(titel);
        tl.setFont(tl.getFont().deriveFont(Font.BOLD));
        p.add(tl, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        if (!"R".equals(art)) p.setPreferredSize(new Dimension(330, 100));
        InventarLayout.eigen(p);
        return p;
    }

    private static void listenStil(JTable t, String art) {
        t.setRowHeight(Math.max(t.getRowHeight(), 38));
        javax.swing.table.TableColumnModel cm = t.getColumnModel();
        int nameSpalte = t.convertColumnIndexToView(1);
        if (nameSpalte >= 0 && !(cm.getColumn(nameSpalte).getCellRenderer() instanceof WerteRenderer))
            cm.getColumn(nameSpalte).setCellRenderer(new WerteRenderer(art));
        if (cm.getColumnCount() >= 3) {
            cm.getColumn(0).setPreferredWidth(50);
            cm.getColumn(0).setMaxWidth(60);
            cm.getColumn(1).setPreferredWidth(260);
            cm.getColumn(2).setPreferredWidth(40);
            cm.getColumn(2).setMaxWidth(50);
        }
    }

    /** Name und darunter Werte des Gegenstands (aus der Spalte mit dem Gegenstand selbst). */
    private static final class WerteRenderer extends DefaultTableCellRenderer {
        final String art;
        WerteRenderer(String art) { this.art = art; }

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            String werte = "";
            try {
                Object g = t.getModel().getValueAt(t.convertRowIndexToModel(row), 2);
                Map<String, String> a = g == null ? null : VorlagenInfo.angaben(g);
                if (a != null) {
                    String[] keys = "Waffe".equals(art) ? new String[]{"Nahkampf"}
                            : "FKW".equals(art) ? new String[]{"Fernkampf"}
                            : "Schild".equals(art) ? new String[]{"Schild", "Parierwaffe", "Nahkampf"}
                            : new String[]{"Rüstung"};
                    for (String k : keys) if (a.containsKey(k)) { werte = a.get(k); break; }
                    if (werte.isEmpty() && a.containsKey("Gewicht")) werte = a.get("Gewicht");
                }
            } catch (RuntimeException ignore) { }
            String farbe = sel ? "" : "color:" + HinzufuegenLayout.farbe(Theme.muted());
            setText("<html><b>" + HinzufuegenLayout.esc(String.valueOf(v)) + "</b>"
                    + (werte.isEmpty() ? "" : "<br><span style='" + farbe + "'>" + HinzufuegenLayout.esc(werte) + "</span>") + "</html>");
            setToolTipText(werte.isEmpty() ? null : werte);
            return this;
        }
    }

    // ------------------------------------------------------------------ Ziehen

    private static void ziehbar(JTable t) {
        t.setDragEnabled(true);
        TransferHandler alt = t.getTransferHandler();
        t.setTransferHandler(new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return COPY; }
            @Override protected Transferable createTransferable(JComponent c) {
                if (t.getSelectedRow() < 0) return null;
                ziehQuelle = t;
                return new StringSelection(String.valueOf(t.getValueAt(t.getSelectedRow(), t.convertColumnIndexToView(1))));
            }
            @Override protected void exportDone(JComponent s, Transferable d, int a) { ziehQuelle = null; }
        });
    }

    private static JTable ziehQuelle;

    /** Nimmt nur Gegenstände aus {@code quelle} an und führt dann die Aktion aus. */
    private static TransferHandler annehmen(JTable quelle, Runnable aktion) { return annehmen(quelle, aktion, null); }

    private static TransferHandler annehmen(JTable quelle, Runnable aktion, Runnable markieren) {
        return new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) {
                boolean ok = s.isDrop() && ziehQuelle == quelle;
                if (ok) {
                    s.setDropAction(COPY);
                    s.setShowDropLocation(false);
                    if (markieren != null) markieren.run();
                }
                return ok;
            }
            @Override public boolean importData(TransferSupport s) {
                if (!canImport(s)) return false;
                SwingUtilities.invokeLater(aktion);
                return true;
            }
        };
    }

    /** Tabelle ist zugleich Ziehquelle (eigene Einträge) und Ablageziel (Einträge der anderen Tabelle). */
    private static TransferHandler kombiniert(TransferHandler ablage, JTable andere, JTable selbst) {
        TransferHandler quelle = selbst.getTransferHandler();
        return new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return COPY; }
            @Override protected Transferable createTransferable(JComponent c) {
                if (selbst.getSelectedRow() < 0) return null;
                ziehQuelle = selbst;
                return new StringSelection(String.valueOf(selbst.getValueAt(selbst.getSelectedRow(), selbst.convertColumnIndexToView(1))));
            }
            @Override protected void exportDone(JComponent s, Transferable d, int a) { ziehQuelle = null; }
            @Override public boolean canImport(TransferSupport s) { return ablage.canImport(s); }
            @Override public boolean importData(TransferSupport s) { return ablage.importData(s); }
        };
    }
}
