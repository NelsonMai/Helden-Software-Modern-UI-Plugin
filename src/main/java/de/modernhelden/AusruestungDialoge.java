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
        /** statt Ablegen: „Werte …“ dieses Platzes öffnen */
        boolean werte;
        Ablegen(boolean ruestung, int nummer, String name, int vorkommen) {
            this.ruestung = ruestung; this.nummer = nummer; this.name = name; this.vorkommen = vorkommen;
        }
        /** statt Ablegen: Bedienelemente des Platzes ändern (Hand, Schild, Jagdwaffe …); false = nicht möglich */
        java.util.function.Predicate<Map<String, JComponent>> aendern;
        static Ablegen aendern(int nummer, String name, java.util.function.Predicate<Map<String, JComponent>> p) {
            Ablegen a = new Ablegen(false, nummer, name, 0);
            a.aendern = p;
            return a;
        }
        static Ablegen werte(int nummer, String name) {
            Ablegen a = new Ablegen(false, nummer, name, 0);
            a.werte = true;
            return a;
        }
    }

    /** Fenster, über dem Dialoge erscheinen, die aus dem versteckten Platz-Dialog heraus geöffnet werden. */
    static Window werteMitte;

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
                if (!(ev instanceof java.awt.event.HierarchyEvent)) return;
                java.awt.event.HierarchyEvent h = (java.awt.event.HierarchyEvent) ev;
                if ((h.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
                if (!(h.getComponent() instanceof JDialog) || !h.getComponent().isDisplayable()) return;
                if (auftrag != null) h.getComponent().setLocation(-30000, -30000);
                else if (werteMitte != null) {
                    // „Werte …“ aus der Übersicht: das Fenster der Helden-Software liegt versteckt außerhalb – mittig zeigen
                    Window w = (Window) h.getComponent(), m = werteMitte;
                    SwingUtilities.invokeLater(() -> { if (w.getX() < -10000 || w.getOwner() != null && w.getOwner().getX() < -10000) w.setLocationRelativeTo(m); });
                }
            }, AWTEvent.HIERARCHY_EVENT_MASK);
        }
        auftrag = a;
        try { oeffnen.run(); } finally { auftrag = null; }
    }

    private static final Pattern PLATZ = Pattern.compile("^(Waffe|FKW|Schild)\\s*(\\d)$");

    /** Beim Öffnen eines Dialogs (nur moderne Designs) aufrufen. */
    static void anwenden(JDialog d) {
        if (d.getRootPane().getClientProperty("modernhelden.ausr") != null) return;
        if (WERTE_TITEL.equals(d.getTitle())) {
            try { werteDialog(d); } catch (RuntimeException ignore) { }
            return;
        }
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
        } else if (a.aendern != null) {
            Object l = d.getRootPane().getClientProperty("modernhelden.steuerung");
            @SuppressWarnings("unchecked")
            Map<String, JComponent> m = l instanceof List && a.nummer >= 1 && a.nummer <= ((List<?>) l).size()
                    ? (Map<String, JComponent>) ((List<?>) l).get(a.nummer - 1) : null;
            auftrag = null;
            AbstractButton okA = null, abbr = null;
            for (AbstractButton b : alleKnoepfe(d.getContentPane())) {
                if (okA == null && ("OK".equals(b.getName()) || "OK".equals(b.getText()))) okA = b;
                if (abbr == null && "Abbrechen".equals(b.getText())) abbr = b;
            }
            boolean geklappt = false;
            try { geklappt = m != null && okA != null && a.aendern.test(m); } catch (RuntimeException ignore) { }
            final AbstractButton ende = geklappt ? okA : abbr;
            final boolean ok = geklappt;
            final Window mitte = werteMitte;
            werteMitte = null;
            SwingUtilities.invokeLater(() -> {
                if (ende != null) ende.doClick(); else d.dispose();
                if (!ok) JOptionPane.showMessageDialog(mitte, "Das lässt die Helden-Software für diesen Gegenstand gerade nicht zu.",
                        "Ausrüstung", JOptionPane.INFORMATION_MESSAGE);
            });
            return;
        } else if (a.werte) {
            Object l = d.getRootPane().getClientProperty("modernhelden.werteKnoepfe");
            AbstractButton w = l instanceof List && a.nummer >= 1 && a.nummer <= ((List<?>) l).size() ? (AbstractButton) ((List<?>) l).get(a.nummer - 1) : null;
            auftrag = null;
            AbstractButton okW = null;
            for (AbstractButton b : alleKnoepfe(d.getContentPane()))
                if ("OK".equals(b.getName()) || "OK".equals(b.getText())) { okW = b; break; }
            final AbstractButton ok2 = okW;
            if (w == null || !w.isEnabled() || ok2 == null) {
                werteMitte = null;
                d.setLocationRelativeTo(d.getOwner());
                InventarLayout.groesse(d, Math.max(d.getWidth(), 960), Math.max(d.getHeight(), 560));
                return;
            }
            SwingUtilities.invokeLater(() -> {
                try { w.doClick(); } finally { werteMitte = null; }
                ok2.doClick();
            });
            return;
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
        String titel;          // „Waffe 1“
        JRadioButton ohne;     // eigene Auswahl „ohne Schild“
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
            z.titel = z.label.getText();
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
                TooltipHook.infoTip(z.feld, "Hierher ziehen oder in der Liste doppelklicken, um eine " + ding + " anzulegen");
            }
            if (z.add != null) TooltipHook.infoTip(z.add, "Markierten Gegenstand aus der Liste auf diesen Platz legen");
            if (z.rem != null) TooltipHook.infoTip(z.rem, "Diesen Platz leeren");
            int radio = 0;
            for (Component k : z.rest) {
                if (k instanceof AbstractButton && "I".equals(((AbstractButton) k).getText())) {
                    ((AbstractButton) k).setText("Werte …");
                    TooltipHook.infoTip(((AbstractButton) k), "Waffenwerte bearbeiten: Bruchfaktor (aktuell/minimal) und Kampftalent");
                } else if (k instanceof JRadioButton) {
                    radio++;
                    ((JRadioButton) k).setText("Schild " + radio);
                    TooltipHook.infoTip(((JRadioButton) k), "<html>Schild bzw. Parierwaffe " + radio + " zusammen mit dieser Waffe führen</html>");
                } else if (k instanceof JComboBox && String.valueOf(k.getName()).startsWith("hand")) {
                    TooltipHook.infoTip(((JComponent) k), "Hand, in der die Waffe geführt wird");
                } else if (k instanceof JCheckBox && String.valueOf(((JCheckBox) k).getText()).startsWith("ist NKW")) {
                    TooltipHook.infoTip(((JComponent) k), "Die Parierwaffe ist zugleich eine Nahkampfwaffe");
                } else if (k instanceof JCheckBox && String.valueOf(((JCheckBox) k).getText()).startsWith("Jagd")) {
                    TooltipHook.infoTip(((JComponent) k), "Als Jagdwaffe verwenden (für Jagd-Talente)");
                } else if (k instanceof JComboBox) {
                    TooltipHook.infoTip(((JComponent) k), "Schild oder Parierwaffe");
                }
            }
        }

        // „Werte …“-Knöpfe je Platz (für „Werte …“ aus der Übersicht)
        List<AbstractButton> werteKnoepfe = new ArrayList<>();
        for (Zeile z : zeilen) {
            AbstractButton w = null;
            for (Component k : z.rest) if (k instanceof AbstractButton && "Werte …".equals(((AbstractButton) k).getText())) w = (AbstractButton) k;
            werteKnoepfe.add(w);
        }
        d.getRootPane().putClientProperty("modernhelden.werteKnoepfe", werteKnoepfe);
        // alle Bedienelemente je Platz (für Änderungen direkt aus der Übersicht)
        List<Map<String, JComponent>> steuerung = new ArrayList<>();
        for (Zeile z : zeilen) {
            Map<String, JComponent> m = new HashMap<>();
            int radio = 0;
            for (Component k : z.rest) {
                if (k instanceof JComboBox && String.valueOf(k.getName()).startsWith("hand")) m.put("hand", (JComponent) k);
                else if (k instanceof JComboBox) m.put("art", (JComponent) k);
                else if (k instanceof JRadioButton) m.put("s" + (++radio), (JComponent) k);
                else if (k instanceof JCheckBox && String.valueOf(((JCheckBox) k).getText()).startsWith("Jagd")) m.put("jagd", (JComponent) k);
                else if (k instanceof JCheckBox && String.valueOf(((JCheckBox) k).getText()).startsWith("ist NKW")) m.put("nkw", (JComponent) k);
            }
            steuerung.add(m);
        }
        List<Component> loseSortiert = new ArrayList<>(lose);
        loseSortiert.sort(Comparator.comparingInt((Component k) -> c.get(k).gridy).thenComparingInt(k -> c.get(k).gridx));
        int keine = 0;
        for (Component k : loseSortiert) {
            if (k instanceof JRadioButton) { keine++; for (Map<String, JComponent> m : steuerung) m.put("keine" + keine, (JComponent) k); }
            if (k instanceof JCheckBox && String.valueOf(k.getName()).matches("bk\\d")) {
                int paar = Integer.parseInt(k.getName().substring(2));
                for (int i : new int[]{2 * paar, 2 * paar + 1}) if (i < steuerung.size()) steuerung.get(i).put("bh", (JComponent) k);
            }
        }
        d.getRootPane().putClientProperty("modernhelden.steuerung", steuerung);
        for (int zi = 0; zi < zeilen.size(); zi++) {
            Map<String, JComponent> m = steuerung.get(zi);
            if (!(m.get("s1") instanceof AbstractButton) || !(m.get("keine1") instanceof AbstractButton)) continue;
            final AbstractButton s1 = (AbstractButton) m.get("s1"), s2 = m.get("s2") instanceof AbstractButton ? (AbstractButton) m.get("s2") : null;
            final AbstractButton k1 = (AbstractButton) m.get("keine1"), k2 = m.get("keine2") instanceof AbstractButton ? (AbstractButton) m.get("keine2") : null;
            JRadioButton ohne = new JRadioButton("ohne");
            ohne.setOpaque(false);
            TooltipHook.infoTip(ohne, "Diese Waffe ohne Schild bzw. Parierwaffe führen");
            Runnable stand = () -> ohne.setSelected(!s1.isSelected() && (s2 == null || !s2.isSelected()));
            stand.run();
            ohne.addActionListener(e -> {
                if (s1.isSelected()) k1.doClick();
                if (s2 != null && s2.isSelected() && k2 != null) k2.doClick();
                stand.run();
            });
            for (AbstractButton b : new AbstractButton[]{s1, s2, k1, k2}) if (b != null) b.addItemListener(e -> SwingUtilities.invokeLater(stand));
            zeilen.get(zi).ohne = ohne;
        }

        // ---- neu anordnen
        innen.removeAll();
        innen.setLayout(new BorderLayout(14, 10));
        innen.setBorder(new EmptyBorder(12, 12, 8, 12));
        letzteArt = art;
        String titel = d.getTitle() != null && !d.getTitle().trim().isEmpty() ? d.getTitle()
                : "Waffe".equals(art) ? "Nahkampfwaffen" : "FKW".equals(art) ? "Fernkampfwaffen" : "Schilde / Parierwaffen";
        innen.add(kopf(symbol(art), titel, "Platz links anklicken, dann Gegenstand rechts doppelklicken oder „Anlegen“ – oder direkt auf einen Platz ziehen. "
                + ("Schild".equals(art) ? "" : "„Werte …“ ändert Bruchfaktor und Talent, ") + "„OK“ übernimmt."), BorderLayout.NORTH);

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
            if (b instanceof Zeile) links.add(zeilePanel((Zeile) b, art, tabelle));
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

        // angelegte Gegenstände in der Liste kennzeichnen (Name → Plätze)
        java.util.function.Function<String, String> belegt = name -> {
            List<String> l = new ArrayList<>();
            for (Zeile z : zeilen) if (z.feld != null && name.equals(z.feld.getText().trim())) l.add(z.titel);
            return l.isEmpty() ? null : String.join(", ", l);
        };
        JPanel listeKarte = (JPanel) liste(tabelle, listeScroll, "Im Inventar", art, "inventar", belegt);
        innen.add(listeKarte, BorderLayout.EAST);

        // ---- gewählter Platz: Klick auf eine Karte wählt sie; „Anlegen“ unter der Liste legt dort an
        final Zeile[] gewaehlt = {null};
        final JButton anlegen = new JButton();
        anlegen.setIcon(ModernIcons.icon("vcrback", 12, 12));
        anlegen.putClientProperty("JButton.buttonType", "roundRect");
        anlegen.setMargin(new Insets(4, 12, 4, 14));
        final Runnable knopfStand = () -> {
            Zeile z = gewaehlt[0];
            anlegen.setText(z == null ? "Platz links wählen" : "In „" + z.titel + "“ anlegen");
            anlegen.setEnabled(z != null && z.add != null && z.add.isEnabled() && tabelle.getSelectedRow() >= 0);
            TooltipHook.infoTip(anlegen, z == null ? "Zuerst links einen Platz anklicken"
                    : "Markierten Gegenstand auf „" + z.titel + "“ legen (ersetzt, was dort liegt)");
        };
        final java.util.function.Consumer<Zeile> waehlen = z -> {
            gewaehlt[0] = z;
            for (Zeile zz : zeilen) if (zz.panel != null) zz.panel.setBorder(zz == z ? rahmenGewaehlt() : rahmenNormal());
            // gewählten Platz ins Bild holen
            if (z != null && z.panel != null) SwingUtilities.invokeLater(() -> z.panel.scrollRectToVisible(new Rectangle(0, 0, z.panel.getWidth(), z.panel.getHeight())));
            knopfStand.run();
        };
        final Runnable naechsterFreier = () -> {
            for (Zeile z : zeilen) if (z.feld != null && z.feld.getText().trim().isEmpty() && z.add != null) { waehlen.accept(z); return; }
        };
        anlegen.addActionListener(e -> {
            Zeile z = gewaehlt[0];
            if (z == null || z.add == null || tabelle.getSelectedRow() < 0) return;
            z.add.doClick();
            // danach den nächsten freien Platz vorwählen (falls es einen gibt)
            SwingUtilities.invokeLater(() -> { naechsterFreier.run(); tabelle.repaint(); knopfStand.run(); });
        });
        tabelle.getSelectionModel().addListSelectionListener(e -> knopfStand.run());
        JPanel unterListe = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        unterListe.setOpaque(false);
        unterListe.add(anlegen);
        listeKarte.add(unterListe, BorderLayout.SOUTH);
        InventarLayout.eigen(unterListe);
        for (Zeile z : zeilen) {
            if (z.panel == null) continue;
            MouseAdapter klick = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { waehlen.accept(z); }
            };
            klickAlle(z.panel, klick);
            z.panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        // anfangs: erster freier Platz, sonst Platz 1
        naechsterFreier.run();
        if (gewaehlt[0] == null) waehlen.accept(zeilen.get(0));
        // Felder ändern sich (Anlegen/Leeren): Liste neu kennzeichnen
        for (Zeile z : zeilen) if (z.feld != null) z.feld.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { SwingUtilities.invokeLater(() -> zeilenHoehen(tabelle)); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { SwingUtilities.invokeLater(() -> zeilenHoehen(tabelle)); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { SwingUtilities.invokeLater(() -> zeilenHoehen(tabelle)); }
        });

        // ---- Doppelklick und Ziehen
        tabelle.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e) || tabelle.getSelectedRow() < 0) return;
                Zeile z = gewaehlt[0];
                if (z == null || z.add == null) {
                    JOptionPane.showMessageDialog(d, "Bitte zuerst links einen Platz anklicken.", d.getTitle(), JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                anlegen.doClick();
            }
        });
        ziehbar(tabelle);
        for (Zeile z : zeilen) {
            if (z.add == null) continue;
            final AbstractButton add = z.add;
            final JPanel zp = z.panel;
            final javax.swing.border.Border markiert = BorderFactory.createCompoundBorder(
                    BorderFactory.createDashedBorder(Theme.accent(), 2, 4, 3, true), new EmptyBorder(5, 7, 5, 7));
            final Runnable aus = () -> { if (zp.getBorder() == markiert) zp.setBorder(gewaehlt[0] == z ? rahmenGewaehlt() : rahmenNormal()); };
            TransferHandler ablegen = annehmen(tabelle, () -> { aus.run(); waehlen.accept(z); add.doClick();
                        SwingUtilities.invokeLater(() -> { naechsterFreier.run(); tabelle.repaint(); }); },
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
        if (auftrag == null) InventarLayout.groesse(d, Math.max(s.width + 360, "Waffe".equals(art) ? 1120 : 960), Math.max(s.height + 60, 600));
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

    /**
     * Ein Platz als Karte (wie die Waffen in der Übersicht): Symbol, Platz und Name, die Werte des angelegten
     * Gegenstands in Kästchen, darunter Hand, „Werte …“ und Schildzuordnung; rechts „Anlegen“ und „✕“.
     */
    private static JComponent zeilePanel(Zeile z, String art, JTable tabelle) {
        // feste Größen aus dem Original-Layout aufheben (sonst werden Texte abgeschnitten)
        List<Component> alle = new ArrayList<>(z.rest);
        alle.add(z.label);
        if (z.feld != null) alle.add(z.feld);
        for (Component k : alle) { k.setPreferredSize(null); k.setMinimumSize(null); k.setMaximumSize(null); }
        JPanel p = new JPanel(new BorderLayout(10, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setBackground(Theme.kachel());
        p.setBorder(rahmenNormal());
        p.setAlignmentX(0);
        p.add(AusruestungLayout.symbolKachel(symbol(art), 46), BorderLayout.WEST);

        JPanel mitte = new JPanel();
        mitte.setLayout(new BoxLayout(mitte, BoxLayout.Y_AXIS));
        mitte.setOpaque(false);
        // Kopfzeile: „WAFFE 1“ – rechts Anlegen und Leeren
        JPanel kopf = new JPanel(new BorderLayout(6, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        kopf.setOpaque(false);
        kopf.setAlignmentX(0);
        z.label.setText(z.label.getText().toUpperCase(Locale.GERMAN));
        z.label.setFont(z.label.getFont().deriveFont(Font.BOLD, z.label.getFont().getSize2D() - 2f));
        z.label.setForeground(Theme.muted());
        z.label.setBorder(new EmptyBorder(0, 0, 0, 4));
        kopf.add(z.label, BorderLayout.WEST);
        JPanel knoepfe = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        knoepfe.setOpaque(false);
        // Anlegen geschieht über den Knopf unter der Liste (für den gewählten Platz)
        if (z.rem != null) {
            z.rem.setText("");
            z.rem.setIcon(ModernIcons.icon("minidelete", 14, 14));
            z.rem.setMargin(new Insets(2, 10, 2, 10));
            knoepfe.add(z.rem);
        }
        kopf.add(knoepfe, BorderLayout.EAST);
        mitte.add(kopf);

        // Name des angelegten Gegenstands (Feld der Helden-Software, als Überschrift gestaltet)
        if (z.feld != null) {
            z.feld.setFont(z.feld.getFont().deriveFont(Font.BOLD, z.feld.getFont().getSize2D() + 1f));
            z.feld.setBorder(new EmptyBorder(1, 0, 3, 0));
            z.feld.setOpaque(false);
            z.feld.putClientProperty("JTextField.placeholderText", "frei – Gegenstand hierher ziehen");
            z.feld.setAlignmentX(0);
            z.feld.setMaximumSize(new Dimension(Integer.MAX_VALUE, z.feld.getPreferredSize().height));
            mitte.add(z.feld);
        }
        // Werte des angelegten Gegenstands
        JPanel werte = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        werte.setOpaque(false);
        werte.setAlignmentX(0);
        mitte.add(werte);
        Runnable fuellen = () -> {
            werte.removeAll();
            String name = z.feld == null ? "" : z.feld.getText().trim();
            String text = name.isEmpty() ? "" : werteText(gegenstand(tabelle, name), art);
            for (String[] k : kaestchen(text)) { werte.add(new AusruestungLayout.Kaestchen(k[0], k[1])); werte.add(Box.createHorizontalStrut(6)); }
            werte.setBorder(werte.getComponentCount() == 0 ? null : new EmptyBorder(2, 2, 4, 0));   // links Luft für den ersten Rand
            werte.revalidate();
            werte.repaint();
        };
        fuellen.run();
        if (z.feld != null) z.feld.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { SwingUtilities.invokeLater(fuellen); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { SwingUtilities.invokeLater(fuellen); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { SwingUtilities.invokeLater(fuellen); }
        });

        // weitere Bedienelemente: Hand, „Werte …“, Schildzuordnung, Häkchen
        JPanel unten = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        unten.setOpaque(false);
        unten.setAlignmentX(0);
        boolean schildBeschriftet = false;
        for (Component k : z.rest) {
            // Schilde/Paradewaffen: „Werte …“ wird von der Helden-Software nie freigegeben – nicht zeigen
            if ("Schild".equals(art) && k instanceof AbstractButton && "Werte …".equals(((AbstractButton) k).getText())) continue;
            // ebenso „ist NKW“: immer gesperrt und nie gesetzt
            if (k instanceof JCheckBox && String.valueOf(((JCheckBox) k).getText()).startsWith("ist NKW")) continue;
            if (k instanceof JRadioButton) {
                ((JRadioButton) k).setOpaque(false);
                if (!schildBeschriftet) {
                    JLabel s = new JLabel("Schild:");
                    s.setForeground(Theme.muted());
                    s.setBorder(new EmptyBorder(0, 6, 0, 0));
                    unten.add(s);
                    schildBeschriftet = true;
                }
                // „Schild 1“ → „1“ (die Beschriftung „Schild:“ steht davor)
                ((JRadioButton) k).setText(((JRadioButton) k).getText().replace("Schild ", ""));
            }
            if (k instanceof JCheckBox) ((JCheckBox) k).setOpaque(false);
            unten.add(k);
        }
        // „ohne“: Waffe ohne Schild führen (löst Schild 1/2 über „keiner Waffe zugeordnet“)
        if (schildBeschriftet && z.ohne != null) unten.add(z.ohne);
        if (unten.getComponentCount() > 0) mitte.add(unten);
        p.add(mitte, BorderLayout.CENTER);
        z.panel = p;
        InventarLayout.eigen(mitte);
        InventarLayout.eigen(kopf);
        InventarLayout.eigen(knoepfe);
        InventarLayout.eigen(unten);
        InventarLayout.eigen(werte);
        // Abstand zur nächsten Karte außerhalb (nicht mit deren Hintergrund gefüllt)
        return p;
    }

    private static javax.swing.border.Border rahmenNormal() {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(7, 8, 7, 8));
    }

    private static javax.swing.border.Border rahmenGewaehlt() {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.accent(), 2), new EmptyBorder(6, 7, 6, 7));
    }

    /** Klick-Hörer an die Karte und alle Teile, die selbst nicht bedienbar sind (Knöpfe/Listen behalten ihre Funktion). */
    private static void klickAlle(Component c, MouseAdapter m) {
        if (c instanceof AbstractButton || c instanceof JComboBox || c instanceof JSpinner) return;
        c.addMouseListener(m);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) klickAlle(k, m);
    }

    private static String symbol(String art) {
        return "Waffe".equals(art) ? "aus_schwert" : "FKW".equals(art) ? "aus_bogen" : "Schild".equals(art) ? "aus_schild" : "aus_ruestung";
    }

    /** Kopf eines Dialogs: Symbol, Titel und ein kurzer Hinweis. */
    private static JComponent kopf(String symbol, String titel, String hinweis) {
        JPanel k = new JPanel(new BorderLayout(10, 0));
        k.setOpaque(false);
        k.add(AusruestungLayout.symbolKachel(symbol, 40), BorderLayout.WEST);
        JPanel t = new JPanel();
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.setOpaque(false);
        JLabel tl = new JLabel(titel == null ? "" : titel);
        tl.setFont(tl.getFont().deriveFont(Font.BOLD, tl.getFont().getSize2D() + 4f));
        tl.setBorder(new EmptyBorder(0, 0, 2, 4));
        tl.setAlignmentX(0);
        t.add(tl);
        Umbruchtext h = new Umbruchtext(hinweis);
        h.setFont(UIManager.getFont("Label.font"));
        h.setForeground(Theme.muted());
        h.setAlignmentX(0);
        t.add(h);
        k.add(t, BorderLayout.CENTER);
        k.setBorder(BorderFactory.createCompoundBorder(new javax.swing.border.MatteBorder(0, 0, 1, 0, Theme.trenner()), new EmptyBorder(0, 0, 8, 0)));
        InventarLayout.eigen(k);
        InventarLayout.eigen(t);
        return k;
    }

    /** Gegenstand (Objekt der Helden-Software) zu einem Namen aus der Liste „Im Inventar“. */
    private static Object gegenstand(JTable t, String name) {
        TableModel m = t.getModel();
        for (int r = 0; r < m.getRowCount(); r++)
            if (name.equals(String.valueOf(m.getValueAt(r, 1)).trim())) return m.getValueAt(r, 2);
        Object g = InventarLayout.gegenstand(name);
        return g;
    }

    /** Werte eines Gegenstands als Text (wie in der Liste), passend zur Art des Dialogs. */
    static String werteText(Object g, String art) {
        if (g == null) return "";
        try {
            Map<String, String> a = VorlagenInfo.angaben(g);
            if (a == null) return "";
            String[] keys = "Waffe".equals(art) ? new String[]{"Nahkampf"}
                    : "FKW".equals(art) ? new String[]{"Fernkampf"}
                    : "Schild".equals(art) ? new String[]{"Schild", "Parierwaffe", "Nahkampf"}
                    : new String[]{"Rüstung"};
            for (String k : keys) if (a.containsKey(k)) return a.get(k);
            return a.containsKey("Gewicht") ? a.get("Gewicht") : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Werte für die Liste gekürzt: „Reichweite“ → „RW“, ohne TP je Entfernung und ohne Laden ≤ 0. */
    static String kurz(String werte) {
        if (werte == null || werte.isEmpty()) return "";
        List<String> l = new ArrayList<>();
        for (String teil : werte.split(" · ")) {
            String t = teil.trim();
            if (t.startsWith("TP-Mod.")) continue;
            if (t.matches("Laden\\s*-?\\d+") && Integer.parseInt(t.replaceAll("\\D*?(-?\\d+)$", "$1")) <= 0) continue;
            if (t.startsWith("Reichweite ")) t = "RW " + t.substring(11).replace(" ", "");
            l.add(t);
        }
        return String.join(" · ", l);
    }

    /** „TP 1W+4 · TP/KK 11/4 · DK N“ → Kästchen {Wert, Bezeichnung}. */
    static List<String[]> kaestchen(String text) {
        List<String[]> l = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return l;
        for (String teil : text.split(" · ")) {
            String t = teil.trim();
            int sp = t.indexOf(' ');
            if (sp <= 0) { l.add(new String[]{t, ""}); continue; }
            String k = t.substring(0, sp), v = t.substring(sp + 1).replace(" ", "");
            if ("Laden".equals(k) && v.matches("-?\\d+") && Integer.parseInt(v) <= 0) continue;
            if ("Reichweite".equals(k)) k = "RW";
            else if ("TP-Mod.".equals(k)) k = "TP je Entf.";
            else if ("Zonen:".equals(k)) continue;
            l.add(new String[]{v, k});
        }
        return l;
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
                TooltipHook.infoTip(((JRadioButton) k), "Schild bzw. Parierwaffe " + r + " mit keiner Waffe führen");
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

    // ------------------------------------------------------------------ Waffenwerte („Werte …“)

    static final String WERTE_TITEL = "Waffenwerte bearbeiten";
    /** Art des zuletzt umgebauten Platz-Dialogs (für das Symbol im Werte-Dialog). */
    private static String letzteArt = "Waffe";

    /**
     * „Waffenwerte bearbeiten“: Kopf mit Symbol und Waffenname, darunter Bruchfaktor (minimal/aktuell) und
     * Kampftalent als Karte, „OK“/„Abbrechen“ rechts unten. Felder und Knöpfe sind die der Helden-Software.
     */
    private static void werteDialog(JDialog d) {
        JTextField name = null;
        List<JSpinner> spinner = new ArrayList<>();
        JComboBox<?> talent = null;
        JPanel knopfleiste = null;
        List<AbstractButton> knoepfe = alleKnoepfe(d.getContentPane());
        for (Component k : alleKomponenten(d.getContentPane())) {
            if (k instanceof JSpinner) spinner.add((JSpinner) k);
            else if (k instanceof JComboBox && talent == null) talent = (JComboBox<?>) k;
            else if (k instanceof JTextField && name == null && SwingUtilities.getAncestorOfClass(JSpinner.class, k) == null) name = (JTextField) k;
        }
        for (AbstractButton b : knoepfe) if ("OK".equals(b.getText()) && b.getParent() instanceof JPanel) knopfleiste = (JPanel) b.getParent();
        if (name == null || spinner.size() != 2 || talent == null || knopfleiste == null) return;
        Container wurzel = knopfleiste.getParent();
        if (wurzel == null) return;
        // minimal links, aktuell rechts (wie im Original)
        spinner.sort(Comparator.comparingInt(Component::getX));
        d.getRootPane().putClientProperty("modernhelden.ausr", Boolean.TRUE);

        Window owner = d.getOwner();
        String art = letzteArt;

        JPanel neu = new JPanel(new BorderLayout(0, 10));
        neu.setOpaque(false);
        neu.setBorder(new EmptyBorder(12, 14, 6, 14));
        String titel = name.getText().trim().isEmpty() ? "Waffenwerte" : name.getText().trim();
        neu.add(kopf(symbol(art), titel, "Bruchfaktor und Kampftalent dieser Waffe"), BorderLayout.NORTH);

        JPanel karte = new JPanel(new GridBagLayout());
        karte.setBackground(Theme.kachel());
        karte.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(10, 12, 10, 12)));
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(4, 0, 4, 10);
        g.gridy = 0;
        g.gridx = 0;
        karte.add(feldTitel("Bezeichnung"), g);
        g.gridx = 1; g.gridwidth = 3; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; g.insets = new Insets(4, 0, 4, 0);
        name.setPreferredSize(null);
        karte.add(name, g);
        g.gridy = 1; g.gridx = 0; g.gridwidth = 1; g.fill = GridBagConstraints.NONE; g.weightx = 0; g.insets = new Insets(4, 0, 4, 10);
        karte.add(feldTitel("Bruchfaktor"), g);
        g.gridx = 1;
        karte.add(mitBeschriftung(spinner.get(0), "minimal"), g);
        g.gridx = 2;
        karte.add(mitBeschriftung(spinner.get(1), "aktuell"), g);
        g.gridx = 3; g.weightx = 1;
        JLabel platz = new JLabel();
        karte.add(platz, g);
        g.gridy = 2; g.gridx = 0; g.weightx = 0;
        karte.add(feldTitel("Kampftalent"), g);
        g.gridx = 1; g.gridwidth = 3; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; g.insets = new Insets(4, 0, 4, 0);
        talent.setPreferredSize(null);
        karte.add(talent, g);
        // Bezeichnung lässt die Helden-Software nicht ändern: als schreibgeschützt zeigen (grau, ohne Fokus)
        if (!name.isEditable()) {
            name.setFocusable(false);
            name.setForeground(Theme.muted());
            name.setBackground(Theme.card());
            name.putClientProperty("JComponent.outline", null);
            TooltipHook.infoTip(name, "Die Bezeichnung kann hier nicht geändert werden");
        }
        // Bruchfaktor ist änderbar: Felder wie normale Eingabefelder (nicht grau wie ein gesperrtes Feld)
        Color feld = UIManager.getColor("TextField.background");
        if (feld == null) feld = talent.getBackground();
        for (JSpinner sp : spinner) {
            sp.setBackground(feld);
            if (sp.getEditor() instanceof JSpinner.DefaultEditor) {
                JFormattedTextField ft = ((JSpinner.DefaultEditor) sp.getEditor()).getTextField();
                ft.setBackground(feld);
                ft.setForeground(UIManager.getColor("TextField.foreground"));
            }
        }
        // Fokus auf den ersten änderbaren Wert statt auf die Bezeichnung
        if (spinner.get(1).getEditor() instanceof JSpinner.DefaultEditor)
            SwingUtilities.invokeLater(((JSpinner.DefaultEditor) spinner.get(1).getEditor()).getTextField()::requestFocusInWindow);
        TooltipHook.infoTip(spinner.get(0), "Minimaler Bruchfaktor der Waffe");
        TooltipHook.infoTip(spinner.get(1), "Aktueller Bruchfaktor (steigt bei Beschädigung)");
        TooltipHook.infoTip(talent, "Kampftalent, mit dem die Waffe geführt wird");
        JPanel karteOben = new JPanel(new BorderLayout());
        karteOben.setOpaque(false);
        karteOben.add(karte, BorderLayout.NORTH);
        neu.add(karteOben, BorderLayout.CENTER);

        // Knopfleiste: rechts, ohne Rahmen
        knopfleiste.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        knopfleiste.setBorder(new EmptyBorder(4, 0, 6, 0));
        knopfleiste.setOpaque(false);
        for (AbstractButton b : knoepfe) {
            if ("OK".equals(b.getText())) { b.setIcon(ModernIcons.icon("check", 14, 14)); d.getRootPane().setDefaultButton((JButton) b); }
            if ("Abbrechen".equals(b.getText())) b.setIcon(ModernIcons.icon("minidelete", 14, 14));
        }

        wurzel.removeAll();
        wurzel.setLayout(new BorderLayout());
        wurzel.add(neu, BorderLayout.CENTER);
        wurzel.add(knopfleiste, BorderLayout.SOUTH);
        for (JComponent c : new JComponent[]{neu, karte}) InventarLayout.eigen(c);
        wurzel.revalidate();
        wurzel.repaint();
        // Höhe nach Inhalt (ohne Leerraum), Breite mindestens 460
        d.pack();
        d.setSize(Math.max(d.getWidth(), 460), d.getHeight());
        if (owner != null) d.setLocationRelativeTo(owner);
    }

    private static JLabel feldTitel(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.muted());
        l.setBorder(new EmptyBorder(0, 0, 0, 4));
        return l;
    }

    /** Spinner mit kleiner Beschriftung darunter (wie die Wert-Kästchen). */
    private static JComponent mitBeschriftung(JComponent k, String text) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        k.setPreferredSize(new Dimension(Math.max(k.getPreferredSize().width, 70), k.getPreferredSize().height));
        p.add(k, BorderLayout.CENTER);
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 2f));
        l.setForeground(Theme.muted());
        p.add(l, BorderLayout.SOUTH);
        InventarLayout.eigen(p);
        return p;
    }

    private static List<Component> alleKomponenten(Container c) {
        List<Component> l = new ArrayList<>();
        for (Component k : c.getComponents()) {
            l.add(k);
            if (k instanceof Container) l.addAll(alleKomponenten((Container) k));
        }
        return l;
    }

    // ------------------------------------------------------------------ Rüstung (zwei Listen)

    private static void ruestung(JDialog d, JPanel innen, List<JTable> tabellen, AbstractButton add, AbstractButton rem) {
        GridBagLayout gb = (GridBagLayout) innen.getLayout();
        tabellen.sort(Comparator.comparingInt(t -> gb.getConstraints(SwingUtilities.getAncestorOfClass(JScrollPane.class, t)).gridx));
        JTable angelegt = tabellen.get(0), inventar = tabellen.get(1);
        JScrollPane sl = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, angelegt);
        JScrollPane sr = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, inventar);
        TooltipHook.infoTip(add, "Markiertes Rüstungsteil aus dem Inventar anlegen");
        TooltipHook.infoTip(rem, "Markiertes Rüstungsteil ablegen");

        innen.removeAll();
        innen.setLayout(new BorderLayout(0, 10));
        innen.setBorder(new EmptyBorder(12, 12, 8, 12));
        innen.add(kopf("aus_ruestung", d.getTitle(), "Doppelklick oder Ziehen legt ein Rüstungsteil an bzw. ab. „OK“ übernimmt."), BorderLayout.NORTH);
        add.setText("Anlegen");
        add.setIcon(ModernIcons.icon("vcrback", 12, 12));
        rem.setText("Ablegen");
        rem.setIcon(ModernIcons.icon("vcrforward", 12, 12));
        rem.setHorizontalTextPosition(SwingConstants.LEFT);
        JPanel mitte = new JPanel(new GridBagLayout());
        mitte.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weighty = 1;
        g.weightx = 1;
        g.gridx = 0;
        mitte.add(liste(angelegt, sl, "In diesem Set angelegt", "R", "aus_ruestung"), g);
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
        // im Inventar kennzeichnen, was schon angelegt ist
        java.util.function.Function<String, String> belegt = name -> {
            for (int r = 0; r < angelegt.getRowCount(); r++)
                if (name.equals(String.valueOf(angelegt.getValueAt(r, angelegt.convertColumnIndexToView(1))).trim())) return "";
            return null;
        };
        mitte.add(liste(inventar, sr, "Im Inventar", "R", "inventar", belegt), g);
        angelegt.getModel().addTableModelListener(e -> SwingUtilities.invokeLater(() -> zeilenHoehen(inventar)));
        angelegt.addPropertyChangeListener("model", e -> {
            angelegt.getModel().addTableModelListener(ev -> SwingUtilities.invokeLater(() -> zeilenHoehen(inventar)));
            SwingUtilities.invokeLater(() -> zeilenHoehen(inventar));
        });
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
        if (auftrag == null) InventarLayout.groesse(d, Math.max(s.width + 260, 1100), Math.max(s.height + 60, 560));
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
    private static JComponent liste(JTable t, JScrollPane sp, String titel, String art, String symbol) {
        return liste(t, sp, titel, art, symbol, null);
    }

    private static JComponent liste(JTable t, JScrollPane sp, String titel, String art, String symbol,
                                    java.util.function.Function<String, String> belegt) {
        if (belegt != null) t.putClientProperty("modernhelden.belegt", belegt);
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
            public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
                // breitere/schmalere Spalte: Zeilenhöhen neu (umbrechender Text)
                if (geplant) return;
                geplant = true;
                SwingUtilities.invokeLater(() -> { geplant = false; zeilenHoehen(t); });
            }
            public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) { }
        });
        t.getModel().addTableModelListener(e -> SwingUtilities.invokeLater(() -> zeilenHoehen(t)));
        t.addPropertyChangeListener("model", e -> t.getModel().addTableModelListener(ev -> SwingUtilities.invokeLater(() -> zeilenHoehen(t))));
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        JLabel tl = new JLabel(titel, ModernIcons.icon(symbol, 18, 18), SwingConstants.LEFT);
        tl.setIconTextGap(8);
        tl.setFont(tl.getFont().deriveFont(Font.BOLD, tl.getFont().getSize2D() + 1f));
        tl.setBorder(new EmptyBorder(0, 0, 0, 4));
        p.add(tl, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        if (!"R".equals(art)) p.setPreferredSize(new Dimension(380, 100));
        InventarLayout.eigen(p);
        return p;
    }

    private static void listenStil(JTable t, String art) {
        t.setRowHeight(Math.max(t.getRowHeight(), 42));
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
        SwingUtilities.invokeLater(() -> zeilenHoehen(t));
    }

    /**
     * Zeilenhöhe je Gegenstand nach dem Text in der Namensspalte: Bricht er in der aktuellen Spaltenbreite um
     * (z. B. „angelegt: Schild 1 · WM …“ unter Windows-Schriften), wird die Zeile höher statt abgeschnitten.
     */
    static void zeilenHoehen(JTable t) {
        int sp = t.convertColumnIndexToView(1);
        if (sp < 0 || t.getRowCount() == 0) { t.repaint(); return; }
        int breite = t.getColumnModel().getColumn(sp).getWidth();
        if (breite <= 0) { t.repaint(); return; }
        for (int r = 0; r < t.getRowCount(); r++) {
            int h = 42;
            try {
                Component c = t.prepareRenderer(t.getCellRenderer(r, sp), r, sp);
                if (c instanceof JLabel) {
                    JLabel l = (JLabel) c;
                    Object v = l.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
                    Insets i = l.getInsets();
                    // etwas schmaler rechnen als die Spalte (Rundung, Rand der Tabelle) – lieber eine Zeile zu hoch
                    int text = breite - i.left - i.right - (l.getIcon() == null ? 0 : l.getIcon().getIconWidth() + l.getIconTextGap()) - 8;
                    if (v instanceof javax.swing.text.View && text > 20) {
                        javax.swing.text.View view = (javax.swing.text.View) v;
                        view.setSize(text, 0);
                        int hoehe = (int) Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS));
                        int zeile = l.getFontMetrics(l.getFont()).getHeight();
                        // bricht der Text um (mehr als zwei Zeilen), eine Zeile Reserve: Windows-Schriften laufen breiter
                        if (hoehe > 2 * zeile + 4) hoehe += zeile;
                        h = Math.max(h, hoehe + i.top + i.bottom + 10);
                    }
                }
            } catch (RuntimeException ignore) { }
            if (t.getRowHeight(r) != h) t.setRowHeight(r, h);
        }
        t.repaint();
    }

    /** Name und darunter Werte des Gegenstands (aus der Spalte mit dem Gegenstand selbst). */
    private static final class WerteRenderer extends DefaultTableCellRenderer {
        final String art;
        WerteRenderer(String art) { this.art = art; }

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            setBackground(null);   // Markierung der vorigen Zeile nicht übernehmen
            super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            String werte = "";
            try { werte = werteText(t.getModel().getValueAt(t.convertRowIndexToModel(row), 2), art); } catch (RuntimeException ignore) { }
            String farbe = sel ? "" : "color:" + HinzufuegenLayout.farbe(Theme.muted());
            // bereits angelegt? (Platz-Dialoge: Name in einem Platz; Rüstung: in der Liste „angelegt“)
            String platz = null;
            Object b = t.getClientProperty("modernhelden.belegt");
            if (b instanceof java.util.function.Function) {
                try { platz = (String) ((java.util.function.Function<String, String>) b).apply(String.valueOf(v).trim()); } catch (RuntimeException ignore) { }
            }
            // Zeile 1: Name und „✓ Waffe 1“, Zeile 2: nur die (gekürzten) Werte – so passt es meist in zwei Zeilen
            String akzent = "color:" + HinzufuegenLayout.farbe(sel ? t.getSelectionForeground() : Theme.accent());
            String haken = platz == null ? "" : "&nbsp;&nbsp;<span style='" + akzent + "'>✓&nbsp;" + (platz.isEmpty() ? "angelegt" : HinzufuegenLayout.esc(platz).replace(" ", "&nbsp;")) + "</span>";
            String zeile2 = HinzufuegenLayout.esc(kurz(werte));
            setText("<html><b>" + HinzufuegenLayout.esc(String.valueOf(v)) + "</b>" + haken
                    + (zeile2.isEmpty() ? "" : "<br><span style='" + farbe + "'>" + zeile2 + "</span>") + "</html>");
            if (platz != null && !sel) {
                Color a = Theme.accent(), bg = t.getBackground();
                setBackground(new Color((a.getRed() + 5 * bg.getRed()) / 6, (a.getGreen() + 5 * bg.getGreen()) / 6, (a.getBlue() + 5 * bg.getBlue()) / 6));
            }
            // Symbol wie in der Übersicht (bei Rüstung nach der Zone mit dem meisten Schutz)
            setIcon(ModernIcons.icon("R".equals(art) ? zonenSymbol(werte) : symbol(art), 20, 20));
            setIconTextGap(8);
            setBorder(new EmptyBorder(0, 6, 0, 4));
            TooltipHook.infoTip(this, werte.isEmpty() ? null : werte);
            return this;
        }
    }

    /** Symbol eines Rüstungsteils nach „Zonen: Kopf 3, Rumpf 1 …“ (Zone mit dem höchsten Schutz). */
    private static String zonenSymbol(String werte) {
        int i = werte.indexOf("Zonen:");
        if (i < 0) return "aus_ruestung";
        String[] sym = {"aus_helm", "aus_ruestung", "aus_arm", "aus_bein"};
        int beste = -1, max = -1;
        Matcher m = Pattern.compile("([A-Za-zÄÖÜäöü.]+(?: [A-Za-zÄÖÜäöü.]+)?) (\\d+)").matcher(werte.substring(i + 6));
        while (m.find()) {
            String z = m.group(1).toLowerCase(Locale.GERMAN);
            int g = z.contains("kopf") ? 0 : z.contains("arm") ? 2 : z.contains("bein") ? 3 : 1;
            int v = Integer.parseInt(m.group(2));
            if (v > max) { max = v; beste = g; }
        }
        return beste < 0 ? "aus_ruestung" : sym[beste];
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
