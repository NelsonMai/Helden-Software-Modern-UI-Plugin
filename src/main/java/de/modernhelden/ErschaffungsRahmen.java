package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Rahmen des Erschaffungs-Assistenten in den modernen Designs:
 * <ul>
 *   <li>links eine Schrittleiste (Nummer bzw. Häkchen, Warnung, gesperrte Schritte blass),</li>
 *   <li>rechts eine Info-Spalte: der Held bisher, Punkte (GP, AP, …) mit Balken, die gewählte
 *       Rasse/Kultur/Profession, Hinweise der Regelprüfung und der Tipp zum Schritt,</li>
 *   <li>unten einheitliche Knöpfe, „Weiter“ hervorgehoben,</li>
 *   <li>ein größeres Fenster, wenn der Bildschirm es hergibt.</li>
 * </ul>
 * Alle Angaben stammen aus den Feldern und Objekten der Helden-Software; bedient wird weiter über ihre Reiter und
 * Knöpfe. Im Original-Design wird alles zurückgesetzt.
 */
final class ErschaffungsRahmen {

    static final String DIALOG = "helden.gui.erschaffung.HauptDialog";
    private static final String ZUSTAND = "modernhelden.erschaffungsRahmen";
    private static final int INFO_BREITE = 300;

    private ErschaffungsRahmen() {}

    /** Wird für die JRootPane des Erschaffungsdialogs aufgerufen (regelmäßig und bei Designwechsel). */
    static void pruefen(JRootPane root, boolean modern) {
        Window w = SwingUtilities.getWindowAncestor(root);
        if (!(w instanceof JDialog) || !DIALOG.equals(w.getClass().getName())) return;
        JDialog d = (JDialog) w;
        Zustand z = (Zustand) root.getClientProperty(ZUSTAND);
        if (modern) {
            if (z == null) {
                z = Zustand.lesen(d);
                if (z == null) return;
                root.putClientProperty(ZUSTAND, z);
            }
            z.modern();
        } else if (z != null) {
            z.original();
            root.putClientProperty(ZUSTAND, null);
        }
    }

    /** true, solange der moderne Rahmen aktiv ist – dann überlässt ErschaffungsLayout ihm die Reiter. */
    static boolean aktiv(Component c) {
        JRootPane r = SwingUtilities.getRootPane(c);
        return r != null && r.getClientProperty(ZUSTAND) != null;
    }

    // ------------------------------------------------------------------ Zustand

    private static final class Zustand {
        final JDialog dialog;
        final JSplitPane split;
        final Component rechtsOrig;
        final JTabbedPane reiter;
        final JComponent zusatz;     // GP/AP-Felder der Helden-Software
        final JTable hinweisTabelle;
        final JLabel hilfeLabel;
        final Info info = new Info();
        Dimension groesseOrig;
        int teilerOrig;
        Timer timer;
        String stand = "";
        final PropertyChangeListener textHoerer = e -> {
            if (!Theme.isModern() || !(e.getSource() instanceof AbstractButton)) return;
            Object neu = e.getNewValue();
            if (neu instanceof String && ((String) neu).startsWith("<html>")) {
                AbstractButton b = (AbstractButton) e.getSource();
                b.putClientProperty("modernhelden.textOrig", neu);
                SwingUtilities.invokeLater(() -> knopfText(b));
            }
        };

        private Zustand(JDialog d, JSplitPane s, JTabbedPane t, JComponent zusatz, JTable tab, JLabel hilfe) {
            dialog = d; split = s; rechtsOrig = s.getRightComponent(); reiter = t; this.zusatz = zusatz;
            hinweisTabelle = tab; hilfeLabel = hilfe;
        }

        static Zustand lesen(JDialog d) {
            JSplitPane s = erstes(d.getContentPane(), JSplitPane.class);
            if (s == null || !(s.getRightComponent() instanceof JPanel)) return null;
            JPanel rechts = (JPanel) s.getRightComponent();
            JTable tab = erstes(rechts, JTable.class);
            JLabel hilfe = null;
            for (Component k : rechts.getComponents()) if (k instanceof JLabel) hilfe = (JLabel) k;
            JTabbedPane t = s.getLeftComponent() instanceof Container ? erstes((Container) s.getLeftComponent(), JTabbedPane.class) : null;
            JComponent zusatz = null;
            if (s.getLeftComponent() instanceof Container)
                for (Component k : ((Container) s.getLeftComponent()).getComponents())
                    if (k instanceof JPanel) zusatz = (JComponent) k;
            if (t == null) return null;
            return new Zustand(d, s, t, zusatz, tab, hilfe);
        }

        boolean eingerichtet;

        /**
         * Gegen Flackern: Baut die Helden-Software einen Schritt (neu) auf – z. B. jede neue Wahl im Schritt
         * „Auswahl“ –, wird der Feinschliff noch vor dem nächsten Zeichnen angewandt statt erst beim Timer.
         * Das Ereignis „hinzugefügt“ kommt vor ihrem revalidate/repaint, ein invokeLater läuft also vorher.
         */
        boolean geplant, horcht;
        final java.awt.event.AWTEventListener sofort = this::hinzugefuegt;

        private void hinzugefuegt(AWTEvent ev) {
            if (ev.getID() != java.awt.event.ContainerEvent.COMPONENT_ADDED || geplant) return;
            java.awt.event.ContainerEvent ce = (java.awt.event.ContainerEvent) ev;
            Container p = ce.getContainer();
            if (p instanceof CellRendererPane || p instanceof JTable || p instanceof JTree) return;
            // Zellen werden beim Zeichnen umgehängt – das ist keine neue Seite (sonst Endlosschleife aus Zeichnen
            // und Aktualisieren, die Oberfläche friert ein)
            if (SwingUtilities.getAncestorOfClass(CellRendererPane.class, p) != null) return;
            if (p instanceof JComponent && Boolean.TRUE.equals(((JComponent) p).getClientProperty("modernhelden.own"))) return;
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

        void modern() {
            if (!eingerichtet) {
                eingerichtet = true;
                groesseOrig = dialog.getSize();
                teilerOrig = split.getDividerLocation();
                vergroessern();
                split.setRightComponent(info);
                split.setResizeWeight(1.0);
                SwingUtilities.invokeLater(() -> split.setDividerLocation(Math.max(400, split.getWidth() - INFO_BREITE - split.getDividerSize())));
                if (zusatz != null) zusatz.setVisible(false);
                reiter.putClientProperty("JTabbedPane.tabAreaInsets", new Insets(10, 8, 10, 0));
                reiter.putClientProperty("JTabbedPane.showTabSeparators", false);
                reiter.putClientProperty("JTabbedPane.tabInsets", new Insets(0, 4, 0, 8));
                reiter.putClientProperty("JTabbedPane.tabAlignment", "leading");
            }
            if (timer == null) timer = new Timer(350, e -> aktualisieren());
            if (!timer.isRunning()) timer.start();
            horchen(true);
            aktualisieren();
        }

        void original() {
            if (timer != null) { timer.stop(); timer = null; }
            horchen(false);
            eingerichtet = false;
            for (int i = 0; i < reiter.getTabCount(); i++)
                if (reiter.getTabComponentAt(i) instanceof Schritt) reiter.setTabComponentAt(i, null);
            for (String k : new String[]{"JTabbedPane.tabAreaInsets", "JTabbedPane.showTabSeparators",
                    "JTabbedPane.tabInsets", "JTabbedPane.tabHeight", "JTabbedPane.minimumTabWidth", "JTabbedPane.tabAlignment"})
                reiter.putClientProperty(k, null);
            if (zusatz != null) zusatz.setVisible(true);
            ErschaffungsSchritte.zuruecksetzen(reiter);
            split.setRightComponent(rechtsOrig);
            for (JButton b : knoepfe()) knopfOriginal(b);
            if (groesseOrig != null) {
                Rectangle r = dialog.getBounds();
                dialog.setBounds(r.x + (r.width - groesseOrig.width) / 2, r.y + (r.height - groesseOrig.height) / 2,
                        groesseOrig.width, groesseOrig.height);
            }
            dialog.validate();
            split.setDividerLocation(teilerOrig);
            final int teiler = teilerOrig;
            SwingUtilities.invokeLater(() -> split.setDividerLocation(teiler));
            // Originalberechnung der GP-Feld-Position auslösen
            Component ext = split.getLeftComponent();
            ext.dispatchEvent(new java.awt.event.ComponentEvent(ext, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
            dialog.validate();
        }

        /** Mehr Platz für Listen und Info-Spalte, wenn der Bildschirm es zulässt. */
        private void vergroessern() {
            GraphicsConfiguration gc = dialog.getGraphicsConfiguration();
            if (gc == null) return;
            Rectangle b = gc.getBounds();
            Insets i = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int w = Math.min(1260, b.width - i.left - i.right - 40);
            int h = Math.min(800, b.height - i.top - i.bottom - 40);
            Dimension jetzt = dialog.getSize();
            if (w > jetzt.width || h > jetzt.height) {
                // um die bisherige Mitte vergrößern, aber auf dem Bildschirm halten
                int nw = Math.max(w, jetzt.width), nh = Math.max(h, jetzt.height);
                Rectangle r = dialog.getBounds();
                int x = r.x + (r.width - nw) / 2, y = r.y + (r.height - nh) / 2;
                x = Math.max(b.x + i.left, Math.min(x, b.x + b.width - i.right - nw));
                y = Math.max(b.y + i.top, Math.min(y, b.y + b.height - i.bottom - nh));
                dialog.setBounds(x, y, nw, nh);
            }
        }

        void aktualisieren() {
            // verborgen (fertig oder abgebrochen): nichts mehr tun; wird es wieder gezeigt, startet IconSwap neu
            if (!dialog.isShowing()) { if (timer != null) timer.stop(); horchen(false); return; }
            schritte();
            // bereits gewählte Vor-/Nachteile nicht ein zweites Mal anbieten (siehe ErschaffungsVorteile)
            ErschaffungsVorteile.doppelteAusblenden(dialog);
            ErschaffungsSchritte.anwenden(reiter);
            for (JButton b : knoepfe()) knopf(b);
            Daten d = Daten.lesen(this);
            String s = d.toString();
            if (!s.equals(stand)) {
                stand = s;
                info.zeigen(d);
            }
        }

        /** Schrittleiste: jeder Reiter bekommt ein eigenes Reiterbild. */
        private void schritte() {
            int n = reiter.getTabCount();
            if (n == 0) return;
            int verfuegbar = reiter.getHeight() - 20;
            int hoehe = Math.max(22, Math.min(32, verfuegbar / n));
            if (!Integer.valueOf(hoehe).equals(reiter.getClientProperty("JTabbedPane.tabHeight"))) {
                reiter.putClientProperty("JTabbedPane.tabHeight", hoehe);
                reiter.putClientProperty("JTabbedPane.minimumTabWidth", 190);
            }
            for (int i = 0; i < n; i++) {
                if (!(reiter.getTabComponentAt(i) instanceof Schritt)) reiter.setTabComponentAt(i, new Schritt(reiter));
            }
            reiter.repaint();
        }

        List<JButton> knoepfe() {
            List<JButton> l = new ArrayList<>();
            sammleKnoepfe(reiter, l);
            return l;
        }

        private void sammleKnoepfe(Container c, List<JButton> l) {
            for (Component k : c.getComponents()) {
                if (k instanceof JButton) {
                    for (java.awt.event.ActionListener a : ((JButton) k).getActionListeners())
                        if (a.getClass().getName().equals(DIALOG)) { l.add((JButton) k); break; }
                } else if (k instanceof Container) sammleKnoepfe((Container) k, l);
            }
        }

        private void knopf(JButton b) {
            if (b.getClientProperty("modernhelden.knopf") == null) {
                b.putClientProperty("modernhelden.knopf", Boolean.TRUE);
                b.putClientProperty("modernhelden.textOrig", b.getText());
                b.putClientProperty("modernhelden.groesseOrig", b.isPreferredSizeSet() ? b.getPreferredSize() : null);
                b.addPropertyChangeListener("text", textHoerer);
            }
            knopfText(b);
        }

        private void knopfOriginal(JButton b) {
            if (b.getClientProperty("modernhelden.knopf") == null) return;
            b.removePropertyChangeListener("text", textHoerer);
            Object t = b.getClientProperty("modernhelden.textOrig");
            if (t instanceof String) b.setText((String) t);
            b.setPreferredSize((Dimension) b.getClientProperty("modernhelden.groesseOrig"));
            b.putClientProperty("FlatLaf.style", null);
            b.putClientProperty("modernhelden.knopf", null);
            b.revalidate();
        }
    }

    /** Knopftext ohne HTML-Zeilenumbruch, gleich hohe Knöpfe, „Weiter“/„Fertigstellen“ hervorgehoben. */
    static void knopfText(AbstractButton b) {
        String t = b.getText();
        if (t != null && t.startsWith("<html>")) {
            String neu = t.substring(6).replaceAll("(?i)<br>\\s*$", "").replaceAll("(?i)<br>", " ").trim();
            b.setText(neu);
            t = neu;
        }
        if (t == null) return;
        boolean vor = t.startsWith("Weiter") || t.startsWith("Erschaffung") || t.startsWith("Fertig");
        Object stil = vor ? "font: bold" : null;
        if (vor && b.isEnabled()) {
            Color a = Theme.accent();
            String hex = String.format("#%02x%02x%02x", a.getRed(), a.getGreen(), a.getBlue());
            stil = "font: bold; background: " + hex + "; foreground: #ffffff; borderColor: " + hex
                    + "; focusedBorderColor: " + hex + "; hoverBackground: darken(" + hex + ",6%); pressedBackground: darken(" + hex + ",12%)";
        }
        if (!java.util.Objects.equals(stil, b.getClientProperty("FlatLaf.style"))) b.putClientProperty("FlatLaf.style", stil);
        Dimension alt = b.isPreferredSizeSet() ? b.getPreferredSize() : null;
        b.setPreferredSize(null);
        Dimension p = b.getPreferredSize();
        Dimension neu = new Dimension(Math.max(p.width + 12, 118), 34);
        b.setPreferredSize(neu);
        if (!neu.equals(alt)) b.revalidate();
    }

    // ------------------------------------------------------------------ Daten der Info-Spalte

    private static final class Punkte {
        final String name; final String akt, max;
        Punkte(String name, String akt, String max) { this.name = name; this.akt = akt; this.max = max; }
        @Override public String toString() { return name + "=" + akt + "/" + max; }
    }

    private static final class Hinweis {
        final String text; final int art;   // 0 Warnung, 1 Frage, sonst Info
        Hinweis(String text, int art) { this.text = text; this.art = art; }
        @Override public String toString() { return art + ":" + text; }
    }

    private static final class Daten {
        String schritt = "";
        final List<String[]> held = new ArrayList<>();
        final List<Punkte> punkte = new ArrayList<>();
        String auswahl, auswahlInfo, variantVon;
        final List<String[]> auswahlDetails = new ArrayList<>();
        final List<Hinweis> hinweise = new ArrayList<>();
        String tipp;

        static Daten lesen(Zustand z) {
            Daten d = new Daten();
            int sel = z.reiter.getSelectedIndex();
            if (sel >= 0) d.schritt = z.reiter.getTitleAt(sel);
            Object w = rufe(z.dialog, "getWerkezug");
            if (w != null) {
                d.feld("Name", rufe(w, "getName"));
                d.feld("Geschlecht", rufe(w, "getGeschlecht"));
                d.feld("Rasse", rufe(w, "getRasse"));
                d.feld("Kultur", rufe(w, "getKultur"));
                d.feld("Profession", rufe(w, "getHauptProfession"));
                if (Boolean.TRUE.equals(rufe(w, "isBGB"))) d.feld("2. Profession", rufe(w, "getBGBProfession"));
                if (Boolean.TRUE.equals(rufe(w, "isVeteran"))) d.feld("Veteran", rufe(w, "getVeteranProfession"));
            }
            if (z.zusatz != null) d.punkte(z.zusatz);
            if (z.hinweisTabelle != null) {
                javax.swing.table.TableModel m = z.hinweisTabelle.getModel();
                for (int r = 0; r < m.getRowCount(); r++) {
                    Object h = null;
                    try { h = m.getValueAt(r, m.getColumnCount() - 1); } catch (RuntimeException ignore) { }
                    if (h == null) continue;
                    Object art = rufe(h, "getHinweisart");
                    int a = art instanceof Enum ? ((Enum<?>) art).ordinal() : 2;
                    String t = h.toString();
                    if (t != null && !t.trim().isEmpty()) d.hinweise.add(new Hinweis(t.trim(), a));
                }
            }
            if (z.hilfeLabel != null) {
                String t = z.hilfeLabel.getText();
                if (t != null && !t.replaceAll("<[^>]*>", "").trim().isEmpty()) d.tipp = t;
            }
            Component c = z.reiter.getSelectedComponent();
            JTree baum = c instanceof Container ? erstes((Container) c, JTree.class) : null;
            if (baum != null && baum.isShowing()) d.auswahl(baum);
            return d;
        }

        private void feld(String name, Object wert) {
            if (wert == null) return;
            String t = wert.toString().trim();
            if (t.isEmpty()) return;
            held.add(new String[]{name, t});
        }

        /** GP/AP-Felder: Beschriftung, darunter „genutzt“ und „insgesamt“ (GridBag-Reihenfolge der Helden-Software). */
        private void punkte(JComponent z) {
            Component[] k = z.getComponents();
            for (int i = 0; i < k.length; i++) {
                if (!(k[i] instanceof JLabel) || !k[i].isVisible()) continue;
                String name = ((JLabel) k[i]).getText();
                if (name == null) continue;
                name = name.replaceAll("(?i)<br>", " ").replaceAll("<[^>]*>", "").trim();
                if (name.isEmpty()) continue;
                String akt = null, max = null;
                for (int j = i + 1; j < k.length && !(k[j] instanceof JLabel); j++) {
                    if (!(k[j] instanceof JTextField)) continue;
                    String t = ((JTextField) k[j]).getText().trim();
                    if (akt == null) akt = t; else if (max == null) max = t;
                }
                if (akt == null || akt.isEmpty()) continue;
                if (name.equals("Merkmal Obj")) name = "Zauber mit Merkmal Objekt";
                punkte.add(new Punkte(name, akt, max == null ? "" : max));
            }
        }

        private void auswahl(JTree baum) {
            Object p = baum.getLastSelectedPathComponent();
            if (!(p instanceof javax.swing.tree.DefaultMutableTreeNode)) return;
            javax.swing.tree.DefaultMutableTreeNode k = (javax.swing.tree.DefaultMutableTreeNode) p;
            Object u = k.getUserObject();
            if (u == null || u instanceof String) return;
            auswahl = u.toString();
            Object mod = rufe(u, "getModifizierer");
            if (mod == null && k.getParent() instanceof javax.swing.tree.DefaultMutableTreeNode) {
                // Variante: Werte der übergeordneten Rasse/Kultur/Profession
                mod = rufe(((javax.swing.tree.DefaultMutableTreeNode) k.getParent()).getUserObject(), "getModifizierer");
                if (mod != null) variantVon = mod.toString();
            }
            if (mod != null) auswahlDetails.addAll(ErschaffungsInfo.lesen(mod));
            Object tip = rufe(u, "getToolTip");
            if ((tip == null || tip.toString().trim().isEmpty()) && mod != null) tip = rufe(mod, "getToolTip");
            if (tip != null && !tip.toString().trim().isEmpty()) auswahlInfo = tip.toString();
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder(schritt).append('|');
            for (String[] h : held) sb.append(h[0]).append('=').append(h[1]).append(';');
            sb.append('|').append(punkte).append('|').append(auswahl).append('|')
                    .append(auswahlInfo == null ? 0 : auswahlInfo.hashCode()).append(auswahlDetails.size()).append('|').append(hinweise).append('|').append(tipp)
                    .append('|').append(Theme.current());
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ Info-Spalte

    private static final class Info extends JPanel {
        private final JPanel inhalt = new JPanel();
        Info() {
            super(new BorderLayout());
            InventarLayout.eigen(this);
            inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
            inhalt.setBorder(new EmptyBorder(10, 8, 10, 10));
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
            sp.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.trenner()));
            sp.getVerticalScrollBar().setUnitIncrement(16);
            InventarLayout.eigen(sp);
            add(sp, BorderLayout.CENTER);
            setMinimumSize(new Dimension(220, 100));
        }

        void zeigen(Daten d) {
            inhalt.removeAll();
            Color hg = UIManager.getColor("Panel.background");
            setBackground(hg);
            inhalt.setBackground(hg);
            if (inhalt.getParent() != null) inhalt.getParent().setBackground(hg);

            if (!d.held.isEmpty()) {
                JPanel k = karte("Dein Held");
                JPanel t = tabelle();
                for (String[] h : d.held) zeile(t, h[0], h[1]);
                k.add(t, BorderLayout.CENTER);
                inhalt.add(k);
                inhalt.add(Box.createVerticalStrut(8));
            }
            if (!d.punkte.isEmpty()) {
                JPanel k = karte("Punkte");
                JPanel l = new JPanel();
                l.setLayout(new BoxLayout(l, BoxLayout.Y_AXIS));
                l.setOpaque(false);
                InventarLayout.eigen(l);
                for (Punkte p : d.punkte) l.add(punkteZeile(p));
                k.add(l, BorderLayout.CENTER);
                inhalt.add(k);
                inhalt.add(Box.createVerticalStrut(8));
            }
            if (d.auswahl != null) {
                JPanel k = karte(d.schritt.isEmpty() ? "Auswahl" : d.schritt);
                JPanel l = new JPanel();
                l.setLayout(new BoxLayout(l, BoxLayout.Y_AXIS));
                l.setOpaque(false);
                InventarLayout.eigen(l);
                // lange Namen (z. B. „Freie Kämpferschule der Trutzburg zu Prem …“) umbrechen statt abschneiden
                Umbruchtext n = new Umbruchtext(d.auswahl);
                Font lf = UIManager.getFont("Label.font") != null ? UIManager.getFont("Label.font") : n.getFont();
                n.setFont(lf.deriveFont(Font.BOLD));
                n.setAlignmentX(0);
                InventarLayout.eigen(n);
                l.add(n);
                if (d.variantVon != null) {
                    Umbruchtext v = new Umbruchtext("Variante – Grundwerte von " + d.variantVon
                            + (d.auswahlDetails.isEmpty() && d.auswahlInfo == null ? "" : ":"));
                    v.setForeground(Theme.muted());
                    v.setFont(lf.deriveFont(lf.getSize2D() - 1f));
                    v.setAlignmentX(0);
                    InventarLayout.eigen(v);
                    l.add(Box.createVerticalStrut(2));
                    l.add(v);
                }
                for (String[] det : d.auswahlDetails) {
                    l.add(Box.createVerticalStrut(6));
                    JLabel dt = KampfwerteLayout.klein(det[0]);
                    dt.setFont(dt.getFont().deriveFont(Font.BOLD));
                    InventarLayout.eigen(dt);
                    l.add(KampfwerteLayout.links(dt));
                    Umbruchtext ut = new Umbruchtext(det[1]);
                    ut.setAlignmentX(0);
                    InventarLayout.eigen(ut);
                    l.add(ut);
                }
                if (d.auswahlInfo != null) {
                    l.add(Box.createVerticalStrut(4));
                    HtmlText h = new HtmlText(html(d.auswahlInfo));
                    InventarLayout.eigen(h);
                    l.add(KampfwerteLayout.links(h));
                }
                k.add(l, BorderLayout.CENTER);
                inhalt.add(k);
                inhalt.add(Box.createVerticalStrut(8));
            }
            if (!d.hinweise.isEmpty()) {
                JPanel k = karte("Hinweise");
                JPanel l = new JPanel();
                l.setLayout(new BoxLayout(l, BoxLayout.Y_AXIS));
                l.setOpaque(false);
                InventarLayout.eigen(l);
                for (Hinweis h : d.hinweise) l.add(hinweisZeile(h));
                k.add(l, BorderLayout.CENTER);
                inhalt.add(k);
                inhalt.add(Box.createVerticalStrut(8));
            }
            if (d.tipp != null) {
                JPanel k = karte("Tipp");
                Umbruchtext h = new Umbruchtext(klartext(d.tipp));
                h.setForeground(Theme.muted());
                InventarLayout.eigen(h);
                k.add(h, BorderLayout.CENTER);
                inhalt.add(k);
            }
            inhalt.revalidate();
            inhalt.repaint();
        }

        /** HTML der Helden-Software als schlichter Text (Absätze bleiben erhalten). */
        static String klartext(String t) {
            String s = t.replaceAll("(?is)<head>.*?</head>", "").replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)</(p|div|tr|li)>", "\n").replaceAll("<[^>]*>", "")
                    .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
            StringBuilder sb = new StringBuilder();
            for (String z : s.split("\n")) {
                z = z.replaceAll("\\s+", " ").trim();
                if (z.isEmpty()) { if (sb.length() > 0 && !sb.toString().endsWith("\n\n")) sb.append('\n'); continue; }
                if (sb.length() > 0 && !sb.toString().endsWith("\n")) sb.append(' ');
                sb.append(z);
            }
            return sb.toString().trim();
        }

        private static String html(String t) {
            String s = t.trim();
            if (s.regionMatches(true, 0, "<html>", 0, 6)) {
                s = s.substring(6);
                s = s.replaceAll("(?i)</?html>", "").replaceAll("(?i)</?body>", "");
            } else {
                s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
            }
            // feste Schriftgrößen/-familien der Vorlage nicht übernehmen
            s = s.replaceAll("(?i)<font[^>]*>", "").replaceAll("(?i)</font>", "")
                    .replaceAll("(?i)</?center>", "").replaceAll("(?i)<(p|div|table|td|tr)\\b[^>]*>", "<$1>")
                    .replaceAll("(?i)<head>.*?</head>", "").replaceAll("^(\\s|&nbsp;|<br>)+", "");
            return "<html>" + s + "</html>";
        }

        private static JPanel karte(String titel) {
            JPanel k = new JPanel(new BorderLayout(0, 6)) {
                @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            };
            k.setAlignmentX(0);
            k.setBackground(Theme.card());
            k.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(8, 10, 10, 10)));
            JLabel t = new JLabel(titel);
            t.setFont(t.getFont().deriveFont(Font.BOLD, t.getFont().getSize2D() + 1f));
            InventarLayout.eigen(t);
            k.add(t, BorderLayout.NORTH);
            InventarLayout.eigen(k);
            return k;
        }

        private static JPanel tabelle() {
            JPanel t = new JPanel(new GridBagLayout());
            t.setOpaque(false);
            InventarLayout.eigen(t);
            return t;
        }

        private static void zeile(JPanel t, String name, String wert) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = t.getComponentCount() / 2;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.insets = new Insets(1, 0, 1, 8);
            JLabel n = KampfwerteLayout.klein(name);
            InventarLayout.eigen(n);
            t.add(n, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(1, 0, 1, 0);
            Umbruchtext w = new Umbruchtext(wert);
            InventarLayout.eigen(w);
            t.add(w, c);
        }

        private static JComponent punkteZeile(Punkte p) {
            Integer akt = zahl(p.akt), max = zahl(p.max);
            JPanel z = new JPanel(new BorderLayout(6, 2)) {
                @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            };
            z.setOpaque(false);
            z.setAlignmentX(0);
            z.setBorder(new EmptyBorder(3, 0, 3, 0));
            InventarLayout.eigen(z);
            JLabel n = new JLabel(p.name);
            InventarLayout.eigen(n);
            boolean zuViel = akt != null && max != null && akt > max;
            JLabel w = new JLabel(max == null ? p.akt : p.akt + " / " + p.max);
            w.setFont(w.getFont().deriveFont(Font.BOLD));
            // rechts Luft: fette Ziffern ragen unter Windows (Segoe UI) über die berechnete Breite
            w.setBorder(new EmptyBorder(0, 0, 0, 4));
            if (zuViel) w.setForeground(fehlerFarbe());
            InventarLayout.eigen(w);
            z.add(n, BorderLayout.CENTER);
            z.add(w, BorderLayout.EAST);
            if (akt != null && max != null && max > 0) {
                Balken b = new Balken(akt, max);
                z.add(b, BorderLayout.SOUTH);
                String rest = zuViel ? (akt - max) + " zu viel" : (max - akt) + " frei";
                TooltipHook.infoTip(z, p.name + ": " + akt + " von " + max + " genutzt, " + rest + ".");
            }
            return z;
        }

        private static JComponent hinweisZeile(Hinweis h) {
            JPanel z = new JPanel(new BorderLayout(6, 0)) {
                @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            };
            z.setOpaque(false);
            z.setAlignmentX(0);
            z.setBorder(new EmptyBorder(3, 0, 3, 0));
            InventarLayout.eigen(z);
            JLabel sym = new JLabel(h.art == 0 ? "!" : "?", SwingConstants.CENTER) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(h.art == 0 ? fehlerFarbe() : Theme.muted());
                    g2.fill(new Ellipse2D.Float(0, 1, 16, 16));
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            sym.setForeground(Color.WHITE);
            sym.setFont(sym.getFont().deriveFont(Font.BOLD, 11f));
            sym.setPreferredSize(new Dimension(16, 18));
            sym.setVerticalAlignment(SwingConstants.TOP);
            InventarLayout.eigen(sym);
            JPanel symHalter = new JPanel(new BorderLayout());
            symHalter.setOpaque(false);
            symHalter.add(sym, BorderLayout.NORTH);
            InventarLayout.eigen(symHalter);
            Umbruchtext t = new Umbruchtext(h.text);
            InventarLayout.eigen(t);
            z.add(symHalter, BorderLayout.WEST);
            z.add(t, BorderLayout.CENTER);
            return z;
        }
    }

    static Color fehlerFarbe() {
        return Theme.isDark() ? new Color(0xe0705a) : new Color(0xc0392b);
    }

    private static Integer zahl(String s) {
        if (s == null) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    /** Fortschrittsbalken genutzt/insgesamt; über dem Maximum rot. */
    private static final class Balken extends JComponent {
        final int akt, max;
        Balken(int akt, int max) {
            this.akt = akt; this.max = max;
            setPreferredSize(new Dimension(100, 8));
            putClientProperty("modernhelden.own", true);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = 6, y = (getHeight() - h) / 2;
            RoundRectangle2D form = new RoundRectangle2D.Float(0, y, w, h, h, h);
            g2.setColor(Theme.trenner());
            g2.fill(form);
            float anteil = Math.max(0f, Math.min(1f, akt / (float) max));
            if (anteil > 0) {
                java.awt.geom.Area a = new java.awt.geom.Area(form);
                a.intersect(new java.awt.geom.Area(new Rectangle(0, y - 1, Math.round(w * anteil), h + 2)));
                g2.setColor(akt > max ? fehlerFarbe() : Theme.accent());
                g2.fill(a);
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ Schrittleiste

    /** Reiterbild: Kreis mit Nummer (erledigt: Häkchen, Warnung: „!“) und Name des Schritts. */
    private static final class Schritt extends JComponent {
        final JTabbedPane reiter;
        Schritt(JTabbedPane reiter) {
            this.reiter = reiter;
            setOpaque(false);
            putClientProperty("modernhelden.own", true);
        }
        private int index() { return reiter.indexOfTabComponent(this); }
        @Override public Dimension getPreferredSize() {
            int i = index();
            String t = i >= 0 ? reiter.getTitleAt(i) : "";
            FontMetrics fm = getFontMetrics(reiter.getFont().deriveFont(Font.BOLD));
            // volle Reiterbreite, damit alle Schritte linksbündig untereinander stehen
            return new Dimension(Math.max(172, 30 + fm.stringWidth(t) + 6), Math.max(20, fm.getHeight()));
        }
        @Override public String getToolTipText() { return null; }
        @Override protected void paintComponent(Graphics g) {
            int i = index();
            if (i < 0) return;
            boolean an = reiter.isEnabledAt(i);
            int sel = reiter.getSelectedIndex();
            schrittMalen(g, this, reiter.getFont(), i + 1, reiter.getTitleAt(i), an, i == sel,
                    an && i != sel && i < sel, an && reiter.getIconAt(i) != null);
        }
    }

    /** Zeichnet einen Schritt der Schrittleiste: Kreis (Nummer, Häkchen oder „!“) und Name. */
    static void schrittMalen(Graphics g, Component c, Font schrift, int nummer, String titel,
                             boolean an, boolean aktuell, boolean erledigt, boolean warnung) {
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color akzent = Theme.accent();
            Color blass = Theme.muted();
            Color text = an ? UIManager.getColor("Label.foreground") : blass;
            if (text == null) text = Color.BLACK;
            int d = 18, y = (c.getHeight() - d) / 2;
            Ellipse2D kreis = new Ellipse2D.Float(4, y, d, d);
            String zeichen = String.valueOf(nummer);
            Color zeichenFarbe;
            if (warnung) {
                g2.setColor(fehlerFarbe());
                g2.fill(kreis);
                zeichen = "!";
                zeichenFarbe = Color.WHITE;
            } else if (aktuell) {
                g2.setColor(akzent);
                g2.fill(kreis);
                zeichenFarbe = Color.WHITE;
            } else if (erledigt) {
                g2.setColor(mischen(akzent, UIManager.getColor("Panel.background"), 0.25f));
                g2.fill(kreis);
                g2.setColor(akzent);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(kreis);
                zeichen = null;
                // Häkchen
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                java.awt.geom.Path2D p = new java.awt.geom.Path2D.Float();
                p.moveTo(4 + 5, y + 9.5f);
                p.lineTo(4 + 8, y + 12.5f);
                p.lineTo(4 + 13.5f, y + 6);
                g2.draw(p);
                zeichenFarbe = akzent;
            } else {
                g2.setColor(an ? text : mischen(blass, UIManager.getColor("Panel.background"), 0.5f));
                g2.setStroke(new BasicStroke(1.1f));
                g2.draw(kreis);
                zeichenFarbe = an ? text : blass;
            }
            if (zeichen != null) {
                Font f = schrift.deriveFont(Font.BOLD, 10.5f);
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(zeichenFarbe);
                g2.drawString(zeichen, 4 + (d - fm.stringWidth(zeichen)) / 2f,
                        y + (d - fm.getHeight()) / 2f + fm.getAscent());
            }
            Font f = schrift;
            if (aktuell) f = f.deriveFont(Font.BOLD);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(an ? text : blass);
            g2.drawString(titel, 30, (c.getHeight() - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
        }
    }

    static Color mischen(Color a, Color b, float anteilA) {
        if (b == null) return a;
        return new Color(Math.round(a.getRed() * anteilA + b.getRed() * (1 - anteilA)),
                Math.round(a.getGreen() * anteilA + b.getGreen() * (1 - anteilA)),
                Math.round(a.getBlue() * anteilA + b.getBlue() * (1 - anteilA)));
    }

    // ------------------------------------------------------------------ Hilfen

    static Object rufe(Object o, String methode) {
        if (o == null) return null;
        try {
            Method m = o.getClass().getMethod(methode);
            m.setAccessible(true);
            return m.invoke(o);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    static <T> T erstes(Container c, Class<T> art) {
        if (c == null) return null;
        for (Component k : c.getComponents()) {
            if (art.isInstance(k)) return art.cast(k);
            if (k instanceof Container) {
                T t = erstes((Container) k, art);
                if (t != null) return t;
            }
        }
        return null;
    }
}
