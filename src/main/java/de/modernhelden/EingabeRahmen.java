package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * „Einen Helden eingeben“ und „Einen Helden editieren“ (beide derselbe Dialog der Helden-Software) im Stil des
 * Erschaffungs-Assistenten:
 * <ul>
 *   <li>links die Schrittleiste (Häkchen, Nummer), oben ein Kopf mit Schritt, Titel und der Anweisung,</li>
 *   <li>rechts eine Info-Spalte mit den bisherigen Angaben,</li>
 *   <li>einheitliche Knöpfe („Weiter“/„Fertig“ hervorgehoben), ein größeres Fenster,</li>
 *   <li>Listen mit Suche (Rasse, Kultur, Profession, Talente), Tabellen sortier- und durchsuchbar.</li>
 * </ul>
 * Bedient wird weiterhin über die Elemente der Helden-Software. Im Original-Design wird alles zurückgesetzt.
 */
final class EingabeRahmen {

    static final String DIALOG = "helden.gui.void.Stringnew";
    private static final String ZUSTAND = "modernhelden.eingabeRahmen";
    private static final int INFO_BREITE = 260;

    private EingabeRahmen() {}

    static boolean ist(Component c) { return c != null && DIALOG.equals(c.getClass().getName()); }

    static void pruefen(JRootPane root, boolean modern) {
        Window w = SwingUtilities.getWindowAncestor(root);
        if (!(w instanceof JDialog) || !ist(w)) return;
        Zustand z = (Zustand) root.getClientProperty(ZUSTAND);
        if (modern) {
            if (z == null) {
                z = Zustand.lesen((JDialog) w);
                if (z == null) return;
                root.putClientProperty(ZUSTAND, z);
            }
            z.modern();
        } else if (z != null) {
            z.original();
            root.putClientProperty(ZUSTAND, null);
        }
    }

    private static final class Zustand {
        final JDialog dialog;
        final JSplitPane split;
        final JList<?> schritte;
        final JPanel rechts;            // Helden-Software: Kopfzeile (N), Schritt (C), Navigation (S)
        final JPanel aussen = new JPanel(new BorderLayout());
        final JPanel innen = new JPanel(new BorderLayout());
        final JPanel info = new JPanel();
        Dimension groesseOrig;
        int teilerOrig;
        Object rendererOrig;
        int zeilenhoeheOrig;
        javax.swing.border.Border navRahmenOrig;
        LayoutManager navLayoutOrig;
        boolean eingerichtet, horcht, geplant;
        Timer timer;
        String kopfStand = "", infoStand = "";
        ErschaffungsSchritte.Kopf kopf;
        final Map<String, String> angaben = new LinkedHashMap<>();
        final java.awt.event.AWTEventListener sofort = this::hinzugefuegt;

        private Zustand(JDialog d, JSplitPane s, JList<?> l, JPanel r) {
            dialog = d; split = s; schritte = l; rechts = r;
            InventarLayout.eigen(aussen);
            InventarLayout.eigen(innen);
            InventarLayout.eigen(info);
        }

        static Zustand lesen(JDialog d) {
            JSplitPane s = ErschaffungsRahmen.erstes(d.getContentPane(), JSplitPane.class);
            if (s == null || !(s.getLeftComponent() instanceof JList) || !(s.getRightComponent() instanceof JPanel)) return null;
            return new Zustand(d, s, (JList<?>) s.getLeftComponent(), (JPanel) s.getRightComponent());
        }

        void modern() {
            if (!eingerichtet) {
                eingerichtet = true;
                groesseOrig = dialog.getSize();
                teilerOrig = split.getDividerLocation();
                vergroessern();
                // rechte Seite: eigener Kopf oben, Info-Spalte rechts, dazwischen die Seite der Helden-Software
                split.setRightComponent(aussen);
                innen.add(rechts, BorderLayout.CENTER);
                aussen.add(innen, BorderLayout.CENTER);
                info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
                info.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.trenner()), new EmptyBorder(10, 8, 10, 10)));
                info.setPreferredSize(new Dimension(INFO_BREITE, 100));
                aussen.add(info, BorderLayout.EAST);
                split.setDividerLocation(210);
                split.setDividerSize(1);
                // Schrittleiste
                rendererOrig = schritte.getCellRenderer();
                zeilenhoeheOrig = schritte.getFixedCellHeight();
                schritte.setCellRenderer(new Schritt());
                schritte.setFixedCellHeight(32);
                schritte.setBorder(new EmptyBorder(10, 8, 10, 0));
                schritte.setBackground(UIManager.getColor("Panel.background"));
            }
            if (timer == null) timer = new Timer(350, e -> aktualisieren());
            if (!timer.isRunning()) timer.start();
            horchen(true);
            aktualisieren();
        }

        void original() {
            if (timer != null) { timer.stop(); timer = null; }
            horchen(false);
            if (!eingerichtet) return;
            eingerichtet = false;
            ErschaffungsSchritte.rueckgaengig(rechts);
            Component mitte = mitte();
            if (mitte instanceof JComponent) ErschaffungsSchritte.baeumeZurueck((JComponent) mitte);
            Component kz = kopfzeile();
            if (kz != null) kz.setVisible(true);
            JPanel nav = navigation();
            if (nav != null) {
                if (navRahmenOrig != null) nav.setBorder(navRahmenOrig);
                if (navLayoutOrig != null) nav.setLayout(navLayoutOrig);
                for (Component k : nav.getComponents()) if (k instanceof JButton) knopfOriginal((JButton) k);
                nav.revalidate();
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            JList<Object> l = (JList) schritte;
            if (rendererOrig instanceof ListCellRenderer) {
                @SuppressWarnings("unchecked")
                ListCellRenderer<Object> r = (ListCellRenderer<Object>) rendererOrig;
                l.setCellRenderer(r);
            }
            schritte.setFixedCellHeight(zeilenhoeheOrig);
            schritte.setBorder(null);
            schritte.setBackground(UIManager.getColor("List.background"));
            innen.removeAll();
            aussen.removeAll();
            split.setRightComponent(rechts);
            split.setDividerSize(UIManager.getInt("SplitPane.dividerSize") > 0 ? UIManager.getInt("SplitPane.dividerSize") : 5);
            if (groesseOrig != null) {
                Rectangle r = dialog.getBounds();
                dialog.setBounds(r.x + (r.width - groesseOrig.width) / 2, r.y + (r.height - groesseOrig.height) / 2,
                        groesseOrig.width, groesseOrig.height);
            }
            dialog.validate();
            split.setDividerLocation(teilerOrig);
        }

        private void vergroessern() {
            GraphicsConfiguration gc = dialog.getGraphicsConfiguration();
            if (gc == null) return;
            Rectangle b = gc.getBounds();
            Insets i = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int w = Math.min(1260, b.width - i.left - i.right - 40);
            int h = Math.min(800, b.height - i.top - i.bottom - 40);
            Dimension jetzt = dialog.getSize();
            if (w > jetzt.width || h > jetzt.height) {
                int nw = Math.max(w, jetzt.width), nh = Math.max(h, jetzt.height);
                Rectangle r = dialog.getBounds();
                int x = r.x + (r.width - nw) / 2, y = r.y + (r.height - nh) / 2;
                x = Math.max(b.x + i.left, Math.min(x, b.x + b.width - i.right - nw));
                y = Math.max(b.y + i.top, Math.min(y, b.y + b.height - i.bottom - nh));
                dialog.setBounds(x, y, nw, nh);
            }
        }

        private void hinzugefuegt(AWTEvent ev) {
            if (ev.getID() != java.awt.event.ContainerEvent.COMPONENT_ADDED || geplant) return;
            java.awt.event.ContainerEvent ce = (java.awt.event.ContainerEvent) ev;
            Container p = ce.getContainer();
            if (p instanceof CellRendererPane || p instanceof JTable || p instanceof JTree || p instanceof JList) return;
            Component c = ce.getChild();
            if (c instanceof JComponent && Boolean.TRUE.equals(((JComponent) c).getClientProperty("modernhelden.own"))) return;
            if (!Theme.isModern() || SwingUtilities.getWindowAncestor(p) != dialog) return;
            geplant = true;
            SwingUtilities.invokeLater(() -> {
                geplant = false;
                if (!eingerichtet) return;
                try { aktualisieren(); } catch (RuntimeException ignore) { }
            });
        }

        void horchen(boolean an) {
            if (an == horcht) return;
            horcht = an;
            if (an) Toolkit.getDefaultToolkit().addAWTEventListener(sofort, AWTEvent.CONTAINER_EVENT_MASK);
            else Toolkit.getDefaultToolkit().removeAWTEventListener(sofort);
        }

        private Component bereich(String wo) {
            LayoutManager l = rechts.getLayout();
            return l instanceof BorderLayout ? ((BorderLayout) l).getLayoutComponent(wo) : null;
        }
        Component kopfzeile() { return bereich(BorderLayout.NORTH); }
        Component mitte() { return bereich(BorderLayout.CENTER); }
        JPanel navigation() { Component c = bereich(BorderLayout.SOUTH); return c instanceof JPanel ? (JPanel) c : null; }

        void aktualisieren() {
            if (!dialog.isShowing()) { if (timer != null) timer.stop(); horchen(false); return; }
            // Kopfzeile der Helden-Software durch den eigenen Kopf ersetzen
            Component kz = kopfzeile();
            String anweisung = "";
            if (kz instanceof Container) {
                JLabel l = ErschaffungsRahmen.erstes((Container) kz, JLabel.class);
                if (l != null && l.getText() != null) anweisung = ErschaffungsRahmenText.klartext(l.getText());
                if (kz.isVisible()) kz.setVisible(false);
            }
            int nr = schritte.getSelectedIndex() + 1;
            Object titelObj = schritte.getSelectedValue();
            String titel = titelObj == null ? "" : titelObj.toString();
            String st = nr + "|" + titel + "|" + anweisung + "|" + System.identityHashCode(mitte()) + "|" + Theme.current();
            if (!st.equals(kopfStand)) {
                kopfStand = st;
                if (kopf != null) innen.remove(kopf);
                kopf = new ErschaffungsSchritte.Kopf(titel, nr, anweisung.isEmpty() ? null : anweisung);
                innen.add(kopf, BorderLayout.NORTH);
                rechts.putClientProperty(ErschaffungsSchritte.KOPF, kopf);
                suche(titel);
                aussen.revalidate();
                aussen.repaint();
            }
            // Navigation
            JPanel nav = navigation();
            if (nav != null) {
                if (navRahmenOrig == null) { navRahmenOrig = nav.getBorder(); navLayoutOrig = nav.getLayout(); }
                if (!(nav.getLayout() instanceof FlowLayout) || ((FlowLayout) nav.getLayout()).getAlignment() != FlowLayout.RIGHT) {
                    nav.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 8));
                    nav.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.trenner()));
                }
                for (Component k : nav.getComponents()) if (k instanceof JButton) knopf((JButton) k);
            }
            // Inhalt des Schritts
            Component m = mitte();
            if (m instanceof JComponent) {
                ansicht((JComponent) m);
                ErschaffungsSchritte.stil(rechts, (JComponent) m);
                obenLinks(rechts, (JComponent) m);
                talentGruppen(rechts, (JComponent) m);
            }
            angabenMerken(titel, m);
            infoZeigen();
            schritte.repaint();
        }

        /**
         * Geschlecht/Setting und Person als Karten wie im Erschaffungs-Assistenten. Die eigene Ansicht ersetzt nur
         * das Formular innerhalb der Seite der Helden-Software (die Seite selbst tauscht sie beim Blättern aus).
         */
        private void ansicht(JComponent seite) {
            if (!(seite.getLayout() instanceof BorderLayout)) return;
            BorderLayout bl = (BorderLayout) seite.getLayout();
            for (Component k : seite.getComponents())
                if (k instanceof ErschaffungsSchritte.Ansicht) { ((ErschaffungsSchritte.Ansicht) k).aktualisieren(); return; }
            // das Formular liegt je nach Seite in CENTER oder NORTH
            Component mitte = bl.getLayoutComponent(BorderLayout.CENTER);
            if (mitte == null) mitte = bl.getLayoutComponent(BorderLayout.NORTH);
            if (mitte == null) return;
            final Object lage = bl.getConstraints(mitte);
            Component form = mitte instanceof JScrollPane ? ((JScrollPane) mitte).getViewport().getView() : mitte;
            if (!(form instanceof JPanel) || !(((JPanel) form).getLayout() instanceof GridBagLayout)) return;
            ErschaffungsSchritte.Ansicht a = null;
            if ("Geschlecht-Panel".equals(seite.getName())) a = new ErschaffungsSchritte.Regelbasis((JComponent) form);
            else if ("Person-Panel".equals(seite.getName())) a = new Person((JComponent) form);
            if (a == null) return;
            final ErschaffungsSchritte.Ansicht an = a;
            final Component altMitte = mitte;
            seite.remove(mitte);
            seite.add(an, BorderLayout.CENTER);
            an.aktualisieren();
            seite.revalidate();
            seite.repaint();
            ErschaffungsSchritte.rueck(rechts, () -> {
                seite.remove(an);
                seite.add(altMitte, lage == null ? BorderLayout.CENTER : lage);
                seite.revalidate();
            });
        }

        /** Auswahl des aktuellen Schritts für die Info-Spalte merken. */
        private void angabenMerken(String titel, Component m) {
            if (!(m instanceof Container) || titel.isEmpty()) return;
            Container c = (Container) m;
            String wert = null;
            JList<?> l = ErschaffungsRahmen.erstes(c, JList.class);
            if (l != null && l.getSelectedValue() != null) wert = l.getSelectedValue().toString();
            if (wert == null) {
                List<String> an = new ArrayList<>();
                for (AbstractButton b : ErschaffungsSchritte.alle(c, AbstractButton.class))
                    if ((b instanceof JRadioButton || b instanceof JCheckBox) && b.isSelected() && b.getText() != null
                            && !b.getText().trim().isEmpty())
                        an.add(b.getText().trim());
                if (titel.startsWith("Geschlecht") && !an.isEmpty()) wert = an.get(0);
                else if (titel.endsWith("variante") && !an.isEmpty()) wert = String.join(", ", an);
            }
            if (titel.equals("Person")) {
                for (JTextField f : ErschaffungsSchritte.alle(c, JTextField.class))
                    if ("textfeldname".equals(f.getName()) && !f.getText().trim().isEmpty()) { angaben.put("Name", f.getText().trim()); break; }
                return;
            }
            if (wert != null && !wert.trim().isEmpty()) angaben.put(titel.replace("Geschlecht/Setting", "Geschlecht"), wert.trim());
        }

        /** Beim Editieren: Name, Rasse, Kultur und Profession aus der Allgemein-Ansicht des Hauptfensters (nur lesen). */
        private boolean heldGelesen;
        private void heldLesen() {
            if (heldGelesen) return;
            heldGelesen = true;
            if (schritte.getModel().getSize() == 0 || !"Info".equals(String.valueOf(schritte.getModel().getElementAt(0)))) return;
            Window w = dialog.getOwner();
            if (w == null) return;
            for (JTextField f : ErschaffungsSchritte.alle(w, JTextField.class)) {
                Container p = f.getParent();
                if (p == null || !(p.getLayout() instanceof GridBagLayout)) continue;
                GridBagLayout gb = (GridBagLayout) p.getLayout();
                GridBagConstraints k = gb.getConstraints(f);
                for (Component c : p.getComponents()) {
                    if (!(c instanceof JLabel)) continue;
                    GridBagConstraints lk = gb.getConstraints(c);
                    if (lk.gridy != k.gridy || lk.gridx != k.gridx - 1) continue;
                    String n = String.valueOf(((JLabel) c).getText()).replaceAll(":\\s*$", "").trim();
                    String t = f.getText() == null ? "" : f.getText().trim();
                    if (!t.isEmpty() && (n.equals("Name") || n.equals("Rasse") || n.equals("Kultur") || n.equals("Profession")))
                        angaben.putIfAbsent(n, t);
                }
            }
        }

        private void infoZeigen() {
            heldLesen();
            String st = angaben.toString() + Theme.current();
            if (st.equals(infoStand)) return;
            infoStand = st;
            info.removeAll();
            info.setBackground(UIManager.getColor("Panel.background"));
            JPanel k = KampfwerteLayout.karte("Dein Held");
            k.setAlignmentX(0);
            JPanel t = new JPanel(new GridBagLayout());
            t.setOpaque(false);
            InventarLayout.eigen(t);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = 0;
            c.anchor = GridBagConstraints.NORTHWEST;
            String[] reihenfolge = {"Name", "Geschlecht", "Rasse", "Rassenvariante", "Kultur", "Kulturvariante", "Profession", "Professionsvariante"};
            boolean leer = true;
            for (String n : reihenfolge) {
                String w = angaben.get(n);
                if (w == null) continue;
                leer = false;
                c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.insets = new Insets(1, 0, 1, 8);
                JLabel nl = KampfwerteLayout.klein(n.replace("variante", "-Var."));
                InventarLayout.eigen(nl);
                t.add(nl, c);
                c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(1, 0, 1, 0);
                Umbruchtext u = new Umbruchtext(w);
                InventarLayout.eigen(u);
                t.add(u, c);
                c.gridy++;
            }
            if (leer) {
                JLabel l = KampfwerteLayout.klein("Noch keine Angaben.");
                InventarLayout.eigen(l);
                t.add(l, c);
            }
            k.add(t, BorderLayout.CENTER);
            info.add(k);
            info.add(Box.createVerticalGlue());
            info.revalidate();
            info.repaint();
        }

        private void knopf(JButton b) {
            if (b.getClientProperty("modernhelden.knopf") == null) {
                b.putClientProperty("modernhelden.knopf", Boolean.TRUE);
                b.putClientProperty("modernhelden.groesseOrig", b.isPreferredSizeSet() ? b.getPreferredSize() : null);
                b.addPropertyChangeListener("text", e -> { if (Theme.isModern()) SwingUtilities.invokeLater(() -> ErschaffungsRahmen.knopfText(b)); });
            }
            ErschaffungsRahmen.knopfText(b);
        }

        private void knopfOriginal(JButton b) {
            if (b.getClientProperty("modernhelden.knopf") == null) return;
            b.setPreferredSize((Dimension) b.getClientProperty("modernhelden.groesseOrig"));
            b.putClientProperty("FlatLaf.style", null);
            b.putClientProperty("modernhelden.knopf", null);
        }

        // ---------------------------------------------------------------- Suche in Listen und Talenten

        private void suche(String titel) {
            Component m = mitte();
            if (!(m instanceof Container)) return;
            JList<?> liste = ErschaffungsRahmen.erstes((Container) m, JList.class);
            boolean talente = "Talente".equals(titel);
            if (liste == null && !talente) return;
            JTextField feld = new JTextField(20);
            feld.putClientProperty("JTextField.placeholderText", talente ? "Talente suchen …" : titel + " suchen …");
            feld.putClientProperty("JTextField.showClearButton", true);
            feld.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
            JPanel halter = new JPanel(new BorderLayout());
            halter.setOpaque(false);
            halter.setBorder(new EmptyBorder(6, 0, 0, 0));
            halter.add(feld, BorderLayout.WEST);
            InventarLayout.eigen(halter);
            InventarLayout.eigen(feld);
            Runnable filtern = talente ? () -> talenteFiltern((Container) m, feld.getText())
                    : () -> listeFiltern(liste, feld.getText());
            feld.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { filtern.run(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { filtern.run(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { filtern.run(); }
            });
            kopf.werkzeug(halter);
            if (liste != null) {
                // Liste: Name hervorgehoben, Varianten blass; Rückweg merken
                ListCellRenderer<?> alt = liste.getCellRenderer();
                if (!(alt instanceof Eintrag)) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    JList<Object> lo = (JList) liste;
                    @SuppressWarnings("unchecked")
                    ListCellRenderer<Object> ao = (ListCellRenderer<Object>) alt;
                    Eintrag e = new Eintrag(ao);
                    lo.setCellRenderer(e);
                    int h = liste.getFixedCellHeight();
                    liste.setFixedCellHeight(-1);
                    ErschaffungsSchritte.rueck(rechts, () -> { lo.setCellRenderer(ao); liste.setFixedCellHeight(h); });
                }
            } else {
                ErschaffungsSchritte.rueck(rechts, () -> talenteFiltern((Container) m, ""));
            }
        }

        private void listeFiltern(JList<?> l, String q) {
            if (!(l.getCellRenderer() instanceof Eintrag)) return;
            ((Eintrag) l.getCellRenderer()).suche = q.trim().toLowerCase(Locale.GERMAN);
            // Zellhöhen neu berechnen lassen (Treffer behalten ihre Höhe, der Rest wird 0)
            l.setFixedCellHeight(0);
            l.setFixedCellHeight(-1);
            for (int i = 0; i < l.getModel().getSize(); i++) {
                if (((Eintrag) l.getCellRenderer()).trifft(l.getModel().getElementAt(i))) { l.ensureIndexIsVisible(i); break; }
            }
        }

        /** Talente: Häkchen und Wertfeld tragen den Talentnamen – nicht passende Zeilen ausblenden. */
        private void talenteFiltern(Container m, String q) {
            String s = q.trim().toLowerCase(Locale.GERMAN);
            JPanel raster = null;
            for (JCheckBox cb : ErschaffungsSchritte.alle(m, JCheckBox.class)) {
                if (cb.getParent() instanceof JPanel && cb.getParent().getLayout() instanceof GridBagLayout) { raster = (JPanel) cb.getParent(); break; }
            }
            if (raster == null) return;
            GridBagLayout gb = (GridBagLayout) raster.getLayout();
            // Zeilen nach gridy: eine Zeile ist sichtbar, wenn eines ihrer benannten Elemente passt
            java.util.TreeMap<Integer, List<Component>> zeilen = new java.util.TreeMap<>();
            for (Component c : raster.getComponents()) {
                if (Boolean.TRUE.equals(((JComponent) c).getClientProperty("modernhelden.talentFueller"))) continue;
                zeilen.computeIfAbsent(gb.getConstraints(c).gridy, x -> new ArrayList<>()).add(c);
            }
            JLabel gruppe = null;
            boolean gruppeHatTreffer = false;
            for (List<Component> z : zeilen.values()) {
                JLabel kopf = null;
                boolean benannt = false, treffer = false;
                for (Component c : z) {
                    if (c instanceof JLabel && ((JLabel) c).getClientProperty("modernhelden.gruppe") != null) kopf = (JLabel) c;
                    String n = c.getName();
                    if (n == null) continue;
                    benannt = true;
                    if (n.toLowerCase(Locale.GERMAN).contains(s)) treffer = true;
                }
                if (kopf != null) {
                    if (gruppe != null) gruppe.setVisible(s.isEmpty() || gruppeHatTreffer);
                    gruppe = kopf;
                    gruppeHatTreffer = false;
                    continue;
                }
                // Zeilen ohne Talentnamen (z. B. „Max: 15“) nur ohne Suchbegriff zeigen
                boolean an = s.isEmpty() || (benannt && treffer);
                for (Component c : z) c.setVisible(an);
                if (an && benannt) gruppeHatTreffer = true;
            }
            if (gruppe != null) gruppe.setVisible(s.isEmpty() || gruppeHatTreffer);
            // Treffer oben ausrichten statt mittig im Feld
            JPanel fueller = null;
            for (Component c : raster.getComponents())
                if (Boolean.TRUE.equals(((JComponent) c).getClientProperty("modernhelden.talentFueller"))) fueller = (JPanel) c;
            if (s.isEmpty() && fueller != null) raster.remove(fueller);
            else if (!s.isEmpty() && fueller == null) {
                fueller = new JPanel();
                fueller.setOpaque(false);
                fueller.putClientProperty("modernhelden.talentFueller", Boolean.TRUE);
                GridBagConstraints fc = new GridBagConstraints();
                fc.gridx = 0; fc.gridy = 99999; fc.weightx = 1; fc.weighty = 1;
                fc.gridwidth = GridBagConstraints.REMAINDER;
                fc.fill = GridBagConstraints.BOTH;
                raster.add(fueller, fc);
            }
            raster.revalidate();
            raster.repaint();
        }
    }

    /** Person: Name, Werte, Aussehen und Geburt als Karten; die Felder teilen Modell/Dokument mit dem Original. */
    private static final class Person extends ErschaffungsSchritte.Ansicht {
        Person(JComponent orig) { super(orig); }

        private List<Component[]> zeilen() {
            List<Component[]> l = new ArrayList<>();
            if (!(orig.getLayout() instanceof GridBagLayout)) return l;
            GridBagLayout gb = (GridBagLayout) orig.getLayout();
            Map<Integer, Component[]> m = new java.util.TreeMap<>();
            for (Component c : orig.getComponents()) {
                GridBagConstraints k = gb.getConstraints(c);
                Component[] z = m.computeIfAbsent(k.gridy, x -> new Component[2]);
                if (k.gridx == 0 && c instanceof JLabel) z[0] = c; else if (k.gridx >= 1 && z[1] == null) z[1] = c;
            }
            for (Component[] z : m.values()) if (z[0] != null && z[1] != null) l.add(z);
            return l;
        }

        @Override String stand() {
            StringBuilder sb = new StringBuilder();
            for (Component[] z : zeilen()) sb.append(((JLabel) z[0]).getText()).append(z[1].isEnabled())
                    .append(z[1] instanceof JTextField && ((JTextField) z[1]).isEditable());
            return sb.toString();
        }

        @Override void aufbauen() {
            String[][] gruppen = {{"Name", "Name"}, {"Werte", "Größe Gewicht Stufe AP Eingesetzte AP"},
                    {"Aussehen", "Haarfarbe Augenfarbe"}, {"Geburt", "Geburtstag Geburtsmonat Geburtsjahr Alter Göttergeschenk"}};
            List<Component[]> alle = zeilen();
            java.util.Set<Component[]> benutzt = new java.util.HashSet<>();
            for (String[] g : gruppen) {
                JPanel t = KampfwerteLayout.tabelle();
                t.setOpaque(false);
                GridBagConstraints c = new GridBagConstraints();
                c.gridy = 0;
                c.anchor = GridBagConstraints.WEST;
                c.insets = new Insets(3, 0, 3, 12);
                for (Component[] z : alle) {
                    String n = ((JLabel) z[0]).getText().replaceAll(":\\s*$", "").trim();
                    if (benutzt.contains(z) || !(" " + g[1] + " ").contains(" " + n + " ") && !(g[1].contains(n) && n.contains(" "))) continue;
                    JComponent kopie = kopie(z[1]);
                    if (kopie == null) continue;
                    benutzt.add(z);
                    c.gridx = 0;
                    t.add(new JLabel(n), c);
                    c.gridx = 1;
                    t.add(kopie, c);
                    c.gridy++;
                }
                if (t.getComponentCount() == 0) continue;
                JPanel k = karte(g[0]);
                k.add(t, BorderLayout.WEST);
            }
        }

        /** Bedienelement, das sich Modell bzw. Dokument mit dem der Helden-Software teilt. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static JComponent kopie(Component o) {
            JComponent n = null;
            if (o instanceof JSpinner) {
                JSpinner s = new JSpinner(((JSpinner) o).getModel());
                s.setPreferredSize(new Dimension(120, s.getPreferredSize().height));
                n = s;
            } else if (o instanceof JComboBox) {
                JComboBox cb = new JComboBox(((JComboBox) o).getModel());
                cb.setRenderer(((JComboBox) o).getRenderer());
                cb.setPreferredSize(new Dimension(200, cb.getPreferredSize().height));
                n = cb;
            } else if (o instanceof JCheckBox) {
                JCheckBox cb = new JCheckBox();
                cb.setModel(((JCheckBox) o).getModel());
                cb.setOpaque(false);
                n = cb;
            } else if (o instanceof JTextField) {
                JTextField f = (JTextField) o;
                JTextField t = new JTextField(f.getDocument(), null, "textfeldname".equals(f.getName()) ? 24 : 10);
                t.setEditable(f.isEditable());
                if ("textfeldname".equals(f.getName())) {
                    t.setFont(t.getFont().deriveFont(Font.BOLD, t.getFont().getSize2D() + 2f));
                    t.putClientProperty("JTextField.placeholderText", "Name des Helden");
                }
                n = t;
            }
            if (n != null) n.setEnabled(o.isEnabled());
            return n;
        }
    }

    /** Talent-Gruppenüberschriften („Kampf“ …) in der Schrift des Plugins statt der großen Serifenschrift. */
    private static void talentGruppen(JPanel huelle, JComponent m) {
        for (JLabel l : ErschaffungsSchritte.alle(m, JLabel.class)) {
            if (l.getClientProperty("modernhelden.gruppe") != null || l.getParent() == null
                    || !(l.getParent().getLayout() instanceof GridBagLayout)) continue;
            Font f = l.getFont();
            if (f == null || f.getSize2D() < 16) continue;
            l.putClientProperty("modernhelden.gruppe", Boolean.TRUE);
            Font neu = UIManager.getFont("Label.font");
            if (neu == null) neu = f;
            l.setFont(neu.deriveFont(Font.BOLD, neu.getSize2D() + 3f));
            Color fg = l.getForeground();
            l.setForeground(Theme.accent());
            ErschaffungsSchritte.rueck(huelle, () -> { l.setFont(f); l.setForeground(fg); l.putClientProperty("modernhelden.gruppe", null); });
        }
    }

    /**
     * Formulare (Geschlecht/Setting, Person, Spezialisierungen) stehen bei der Helden-Software mittig im Feld –
     * ein Füller rechts unten schiebt sie nach oben links, wie im Erschaffungs-Assistenten.
     */
    private static void obenLinks(JPanel huelle, JComponent m) {
        List<JPanel> kandidaten = new ArrayList<>();
        if (m instanceof JPanel && m.getLayout() instanceof GridBagLayout) kandidaten.add((JPanel) m);
        for (Component c : m.getComponents()) {
            Component v = c instanceof JScrollPane ? ((JScrollPane) c).getViewport().getView() : c;
            if (v instanceof JPanel && ((JPanel) v).getLayout() instanceof GridBagLayout) kandidaten.add((JPanel) v);
        }
        for (JPanel p : kandidaten) {
            if (p.getClientProperty("modernhelden.obenLinks") != null) continue;
            // nur Formulare ohne eigene Gewichte (sonst füllt die Helden-Software die Fläche absichtlich)
            GridBagLayout gb = (GridBagLayout) p.getLayout();
            boolean gewichtet = false;
            for (Component c : p.getComponents()) {
                GridBagConstraints k = gb.getConstraints(c);
                if (k.weightx > 0 || k.weighty > 0) { gewichtet = true; break; }
            }
            if (gewichtet || p.getComponentCount() == 0) continue;
            p.putClientProperty("modernhelden.obenLinks", Boolean.TRUE);
            JPanel fueller = new JPanel();
            fueller.setOpaque(false);
            InventarLayout.eigen(fueller);
            GridBagConstraints fc = new GridBagConstraints();
            fc.gridx = 50; fc.gridy = 999; fc.weightx = 1; fc.weighty = 1;
            p.add(fueller, fc);
            javax.swing.border.Border b = p.getBorder();
            p.setBorder(BorderFactory.createCompoundBorder(b, new EmptyBorder(8, 14, 8, 8)));
            p.revalidate();
            ErschaffungsSchritte.rueck(huelle, () -> {
                p.remove(fueller);
                p.setBorder(b);
                p.putClientProperty("modernhelden.obenLinks", null);
                p.revalidate();
            });
        }
    }

    /** Eintrag der Schrittleiste. */
    private static final class Schritt extends JComponent implements ListCellRenderer<Object> {
        int nummer; String titel = ""; boolean aktuell, erledigt;
        Font schrift;
        Schritt() { setOpaque(false); }
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                boolean isSelected, boolean cellHasFocus) {
            nummer = index + 1;
            titel = value == null ? "" : value.toString();
            int sel = list.getSelectedIndex();
            aktuell = index == sel;
            erledigt = index < sel;
            schrift = list.getFont();
            return this;
        }
        @Override public Dimension getPreferredSize() { return new Dimension(180, 32); }
        @Override protected void paintComponent(Graphics g) {
            if (aktuell) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ErschaffungsRahmen.mischen(Theme.accent(), UIManager.getColor("Panel.background"), 0.12f));
                g2.fillRoundRect(0, 2, getWidth() - 6, getHeight() - 4, 8, 8);
                g2.dispose();
            }
            ErschaffungsRahmen.schrittMalen(g, this, schrift == null ? getFont() : schrift, nummer, titel, true, aktuell, erledigt, false);
        }
    }

    /** Listeneintrag (Rasse, Kultur, Profession): Name, Varianten blass dahinter; bei Suche nicht passende ausblenden. */
    private static final class Eintrag extends JPanel implements ListCellRenderer<Object> {
        final ListCellRenderer<Object> orig;
        final JLabel name = new JLabel(), varianten = new JLabel();
        String suche = "";
        final JList<Object> probe = new JList<>();
        Eintrag(ListCellRenderer<Object> orig) {
            super(new BorderLayout(6, 0));
            this.orig = orig;
            name.setBorder(new EmptyBorder(0, 8, 0, 0));
            add(name, BorderLayout.WEST);
            add(varianten, BorderLayout.CENTER);
        }
        boolean trifft(Object v) {
            if (suche.isEmpty()) return true;
            Component c = orig.getListCellRendererComponent(probe, v, 0, false, false);
            String t = c instanceof JLabel ? ((JLabel) c).getText() : String.valueOf(v);
            return t != null && t.replaceAll("<[^>]*>", "").toLowerCase(Locale.GERMAN).contains(suche);
        }
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                boolean sel, boolean focus) {
            @SuppressWarnings("unchecked")
            JList<Object> lo = (JList<Object>) list;
            Component c = orig.getListCellRendererComponent(lo, value, index, sel, focus);
            String t = c instanceof JLabel ? ((JLabel) c).getText() : String.valueOf(value);
            if (t == null) t = "";
            String rein = t.replaceAll("<[^>]*>", "");
            String n = rein, v = "";
            int k = rein.indexOf(" [");
            if (k > 0 && rein.endsWith("]")) { n = rein.substring(0, k); v = rein.substring(k + 2, rein.length() - 1); }
            name.setText(n);
            varianten.setText(v);
            name.setFont(list.getFont().deriveFont(Font.BOLD));
            varianten.setFont(list.getFont().deriveFont(list.getFont().getSize2D() - 1f));
            Color hg = sel ? list.getSelectionBackground() : list.getBackground();
            Color vg = sel ? list.getSelectionForeground() : list.getForeground();
            setBackground(hg);
            setOpaque(true);
            name.setForeground(vg);
            varianten.setForeground(sel ? vg : Theme.muted());
            setToolTipText(c instanceof JComponent ? ((JComponent) c).getToolTipText() : null);
            boolean sichtbar = suche.isEmpty() || rein.toLowerCase(Locale.GERMAN).contains(suche);
            setPreferredSize(sichtbar ? new Dimension(name.getPreferredSize().width + varianten.getPreferredSize().width + 20, 26)
                    : new Dimension(1, 0));
            return this;
        }
    }
}

/** Kleine Texthilfe (HTML der Helden-Software → schlichter Text). */
final class ErschaffungsRahmenText {
    private ErschaffungsRahmenText() {}
    static String klartext(String t) {
        String s = t.replaceAll("(?is)<head>.*?</head>", "").replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|li)>", "\n").replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
        StringBuilder sb = new StringBuilder();
        for (String z : s.split("\n")) {
            z = z.replaceAll("\\s+", " ").trim();
            if (z.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(z);
        }
        return sb.toString().trim();
    }
}
