package de.modernhelden;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

/**
 * Dialog „Geldbörse editieren“ in den modernen Designs: oben „Ein- und Ausgaben“ – einen Betrag eingeben
 * und einnehmen oder ausgeben (beim Ausgeben wird bei Bedarf gewechselt) – sowie der Gesamtwert.
 * Es werden nur die Münzfelder der Helden-Software verändert; übernommen wird wie bisher erst mit „OK“
 * durch die Helden-Software selbst, „Abbrechen“ verwirft alles. Im Design „Original“ bleibt der Dialog unverändert.
 */
final class GeldboerseLayout {

    private GeldboerseLayout() {}

    static final String TITEL = "Geldbörse editieren";

    /** Kaufauftrag aus dem Hinzufügen-Dialog: Betrag in Silbertalern (0 = keiner) und Ergebnis. */
    private static double auftrag;
    private static String ergebnis;

    static void auftrag(double silber) {
        auftrag = silber;
        if (silber > 0) { ergebnis = null; versteckenInstallieren(); }
    }

    private static boolean versteckenInstalliert;
    private static final int WEG = -30000;

    /**
     * Beim Kaufen soll die Geldbörse nicht aufblitzen: Ihr Dialog wird schon beim Erzeugen (vor dem Anzeigen)
     * außerhalb des Bildschirms platziert. Reicht das Geld nicht oder klappt etwas nicht, wird er wieder
     * sichtbar in die Mitte gesetzt, damit man selbst entscheiden kann.
     */
    private static void versteckenInstallieren() {
        if (versteckenInstalliert) return;
        versteckenInstalliert = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (auftrag <= 0 || !(ev instanceof java.awt.event.HierarchyEvent)) return;
            java.awt.event.HierarchyEvent h = (java.awt.event.HierarchyEvent) ev;
            if ((h.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
            if (!(h.getComponent() instanceof JDialog) || !h.getComponent().isDisplayable()) return;
            final JDialog d = (JDialog) h.getComponent();
            d.setLocation(WEG, WEG);
            // Sicherheitsnetz: ist der Dialog nach kurzer Zeit noch offen und versteckt, wieder zeigen
            javax.swing.Timer t = new javax.swing.Timer(1500, e -> zeigenFallsVersteckt(d));
            t.setRepeats(false);
            t.start();
        }, AWTEvent.HIERARCHY_EVENT_MASK);
    }

    private static void zeigenFallsVersteckt(JDialog d) {
        if (d.isShowing() && d.getX() <= WEG / 2) d.setLocationRelativeTo(d.getOwner());
    }

    /** „bezahlt“, „fehlt“ oder null (nicht bestätigt). */
    static String ergebnis() { return ergebnis; }

    /** Eine Münze der Helden-Software mit ihrem Eingabefeld. */
    private static final class Muenze {
        final String name, kurz, waehrung;
        final double wert;       // in Silbertalern
        final JSpinner feld;
        long einheiten;          // Wert in kleinsten Einheiten der Währung
        Muenze(String name, String kurz, String waehrung, double wert, JSpinner feld) {
            this.name = name; this.kurz = kurz; this.waehrung = waehrung; this.wert = wert; this.feld = feld;
        }
        int anzahl() { Object v = feld.getValue(); return v instanceof Number ? ((Number) v).intValue() : 0; }
        void setAnzahl(int n) { feld.setValue(n); }
    }

    static void anwenden(JDialog d) {
        try {
            umbauen(d);
        } finally {
            // nur ein erfolgreich bezahlter Kauf bleibt unsichtbar (er schließt sich gleich selbst)
            if (auftrag > 0 && "bezahlt".equals(ergebnis)) d.setLocation(WEG, WEG);
            else zeigenFallsVersteckt(d);
        }
    }

    private static void umbauen(JDialog d) {
        if (d.getRootPane().getClientProperty("modernhelden.geld") != null) return;
        Container cp = d.getContentPane();
        JPanel o = null;
        for (Component k : cp.getComponents()) if (k instanceof JPanel) o = (JPanel) k;
        if (o == null || !(o.getLayout() instanceof BorderLayout)) return;
        Map<String, List<Muenze>> waehrungen = lesen(o);
        if (waehrungen.isEmpty()) return;
        d.getRootPane().putClientProperty("modernhelden.geld", Boolean.TRUE);
        Aufbau a = new Aufbau(d, o, waehrungen);
        if (auftrag > 0) a.kaufBezahlen(auftrag, o);
    }

    /** Münzen und Eingabefelder aus dem Feld vom Typ Map&lt;Muenze, JSpinner&gt; des Dialogs (über Reflexion, nur lesend). */
    private static Map<String, List<Muenze>> lesen(JPanel o) {
        Map<String, List<Muenze>> w = new LinkedHashMap<>();
        try {
            for (Field f : o.getClass().getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object m = f.get(o);
                if (!(m instanceof Map)) continue;
                for (Map.Entry<?, ?> e : ((Map<?, ?>) m).entrySet()) {
                    if (!(e.getValue() instanceof JSpinner) || e.getKey() == null) continue;
                    Object mz = e.getKey();
                    String name = str(mz, "getBezeichner"), kurz = str(mz, "getKurzString"), wae = str(mz, "getWaehrungsBezeichner");
                    Object wert = rufe(mz, "getWertInSilberstuecke");
                    if (name == null || !(wert instanceof Number)) continue;
                    w.computeIfAbsent(wae == null ? "" : wae, k -> new ArrayList<>())
                            .add(new Muenze(name, kurz == null ? name : kurz, wae, ((Number) wert).doubleValue(), (JSpinner) e.getValue()));
                }
            }
        } catch (Exception ignore) {
            return Collections.emptyMap();
        }
        // Mittelreich zuerst, Münzen absteigend nach Wert; kleinste Einheit je Währung bestimmen
        Map<String, List<Muenze>> sortiert = new LinkedHashMap<>();
        List<String> namen = new ArrayList<>(w.keySet());
        namen.sort((a, b) -> a.equals("Mittelreich") ? -1 : b.equals("Mittelreich") ? 1 : a.compareTo(b));
        for (String n : namen) {
            List<Muenze> l = w.get(n);
            l.sort((a, b) -> Double.compare(b.wert, a.wert));
            double min = Double.MAX_VALUE;
            for (Muenze m : l) if (m.wert > 0) min = Math.min(min, m.wert);
            if (min == Double.MAX_VALUE) continue;
            for (Muenze m : l) m.einheiten = Math.max(1, Math.round(m.wert / min));
            sortiert.put(n, l);
        }
        return sortiert;
    }

    private static Object rufe(Object o, String m) {
        try {
            Method me = o.getClass().getMethod(m);
            me.setAccessible(true);
            return me.invoke(o);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o, String m) { Object v = rufe(o, m); return v == null ? null : v.toString(); }

    private static final class Aufbau {
        final Map<String, List<Muenze>> waehrungen;
        final JComboBox<String> waehrung = new JComboBox<>();
        final JPanel betragPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        final Map<Muenze, JSpinner> betrag = new LinkedHashMap<>();
        final JLabel gesamt = new JLabel(), status = new JLabel(" ");

        Aufbau(JDialog d, JPanel o, Map<String, List<Muenze>> waehrungen) {
            this.waehrungen = waehrungen;
            for (String w : waehrungen.keySet()) waehrung.addItem(w);
            waehrung.addActionListener(e -> betragFelder());
            betragPanel.setOpaque(false);

            JButton ein = new JButton("Einnehmen"), aus = new JButton("Ausgeben");
            InventarLayout.primaer(aus, true);
            ein.addActionListener(e -> buchen(true));
            aus.addActionListener(e -> buchen(false));

            JLabel titelText = InventarLayout.titel("Ein- und Ausgaben");
            JPanel titel = new JPanel(new BorderLayout(8, 0));
            titel.setOpaque(false);
            titel.add(titelText, BorderLayout.CENTER);
            if (waehrungen.size() > 1) titel.add(waehrung, BorderLayout.EAST);
            JPanel zeile1 = betragPanel;
            JPanel zeile2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            zeile2.setOpaque(false);
            zeile2.add(ein);
            zeile2.add(aus);
            zeile2.add(status);
            JLabel hinweis = new JLabel("<html>Beim Ausgeben wird bei Bedarf gewechselt. Übernommen wird alles erst mit „OK“.</html>");
            hinweis.setForeground(Theme.muted());

            JPanel karte = new JPanel();
            karte.setLayout(new BoxLayout(karte, BoxLayout.Y_AXIS));
            karte.setBackground(Theme.card());
            karte.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.trenner()), new EmptyBorder(10, 12, 10, 12)));
            for (JComponent c : new JComponent[]{titel, zeile1, zeile2, hinweis, gesamt}) {
                c.setAlignmentX(0);
                karte.add(c);
                karte.add(Box.createVerticalStrut(8));
            }
            gesamt.setFont(gesamt.getFont().deriveFont(Font.BOLD));

            JPanel nord = new JPanel(new BorderLayout());
            nord.setOpaque(false);
            nord.setBorder(new EmptyBorder(12, 12, 6, 12));
            nord.add(karte, BorderLayout.CENTER);
            o.add(nord, BorderLayout.NORTH);
            // schwarze Linie um die Knopfleiste der Helden-Software entfernen
            Component sued = ((BorderLayout) o.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
            if (sued instanceof JComponent) ((JComponent) sued).setBorder(new EmptyBorder(6, 6, 6, 6));
            for (JComponent c : new JComponent[]{titel, titelText, zeile2, hinweis, karte, nord, waehrung, betragPanel, gesamt, status, ein, aus})
                InventarLayout.eigen(c);

            for (List<Muenze> l : waehrungen.values())
                for (Muenze m : l) m.feld.addChangeListener(e -> gesamt());
            betragFelder();
            gesamt();
            Dimension s = d.getSize();
            InventarLayout.groesse(d, Math.max(s.width, 620), Math.max(s.height, 720));
            // die Münzfelder der gewählten Währung im Raster der Helden-Software sichtbar machen
            SwingUtilities.invokeLater(this::zeigen);
            o.revalidate();
            o.repaint();
        }

        List<Muenze> aktuelle() {
            List<Muenze> l = waehrungen.get(String.valueOf(waehrung.getSelectedItem()));
            return l == null ? Collections.<Muenze>emptyList() : l;
        }

        void betragFelder() {
            betragPanel.removeAll();
            betrag.clear();
            for (Muenze m : aktuelle()) {
                JSpinner sp = new JSpinner(new SpinnerNumberModel(0, 0, 999999, 1));
                ((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setColumns(3);
                sp.setToolTipText(m.name);
                InventarLayout.eigen(sp);
                betrag.put(m, sp);
                JLabel k = new JLabel(m.kurz);
                k.setToolTipText(m.name);
                betragPanel.add(sp);
                betragPanel.add(k);
                betragPanel.add(Box.createHorizontalStrut(6));
            }
            status.setText(" ");
            zeigen();
            betragPanel.revalidate();
            betragPanel.repaint();
        }

        /**
         * Kauf aus dem Hinzufügen-Dialog: Betrag in Mittelreich-Münzen abziehen (mit Wechseln) und mit dem
         * „OK“ der Helden-Software übernehmen. Reicht das Geld nicht, bleibt der Dialog zum Anpassen offen.
         */
        void kaufBezahlen(double silber, JPanel o) {
            String w = waehrungen.containsKey("Mittelreich") ? "Mittelreich" : waehrungen.keySet().iterator().next();
            waehrung.setSelectedItem(w);
            List<Muenze> l = aktuelle();
            double min = Double.MAX_VALUE;
            for (Muenze m : l) if (m.wert > 0) min = Math.min(min, m.wert / m.einheiten);
            long summe = Math.round(silber / min);
            long vorhanden = 0;
            for (Muenze m : l) vorhanden += m.einheiten * m.anzahl();
            if (vorhanden < summe) {
                ergebnis = "fehlt";
                status.setText("Kauf: es fehlen " + text(l, summe - vorhanden) + " – bitte anpassen oder abbrechen.");
                return;
            }
            int[] neu = bezahlen(l, summe);
            for (int i = 0; i < l.size(); i++) l.get(i).setAnzahl(neu[i]);
            gesamt();
            status.setText("\u2713 Kauf: " + text(l, summe) + " bezahlt");
            AbstractButton ok = null;
            for (Component k : alle(o)) {
                if (k instanceof AbstractButton && ("OK".equals(((AbstractButton) k).getName()) || "OK".equals(((AbstractButton) k).getText()))) {
                    ok = (AbstractButton) k;
                    break;
                }
            }
            if (ok == null) { ergebnis = "fehlt"; return; }
            final AbstractButton bestaetigen = ok;
            ergebnis = "bezahlt";
            SwingUtilities.invokeLater(bestaetigen::doClick);
        }

        private static List<Component> alle(Container c) {
            List<Component> l = new ArrayList<>();
            for (Component k : c.getComponents()) {
                l.add(k);
                if (k instanceof Container) l.addAll(alle((Container) k));
            }
            return l;
        }

        /** Raster der Helden-Software zur gewählten Währung scrollen. */
        void zeigen() {
            List<Muenze> l = aktuelle();
            if (l.isEmpty()) return;
            JSpinner f = l.get(0).feld;
            Container p = f.getParent();
            if (p instanceof JComponent) {
                Rectangle r = f.getBounds();
                r.grow(0, 40);
                ((JComponent) p).scrollRectToVisible(r);
            }
        }

        /** Betrag einnehmen (Münzen wie eingegeben) oder ausgeben (mit Wechseln). */
        void buchen(boolean einnehmen) {
            List<Muenze> l = aktuelle();
            long summe = 0;
            for (Map.Entry<Muenze, JSpinner> e : betrag.entrySet())
                summe += e.getKey().einheiten * ((Number) e.getValue().getValue()).longValue();
            if (summe <= 0) { status.setText("Bitte einen Betrag eingeben."); return; }
            if (einnehmen) {
                for (Map.Entry<Muenze, JSpinner> e : betrag.entrySet()) {
                    int n = ((Number) e.getValue().getValue()).intValue();
                    if (n > 0) e.getKey().setAnzahl(e.getKey().anzahl() + n);
                }
                status.setText("✓ " + text(l, summe) + " eingenommen");
            } else {
                long vorhanden = 0;
                for (Muenze m : l) vorhanden += m.einheiten * m.anzahl();
                if (vorhanden < summe) {
                    status.setText("Nicht genug Geld – es fehlen " + text(l, summe - vorhanden) + ".");
                    return;
                }
                int[] neu = bezahlen(l, summe);
                for (int i = 0; i < l.size(); i++) l.get(i).setAnzahl(neu[i]);
                status.setText("✓ " + text(l, summe) + " ausgegeben");
            }
            for (JSpinner sp : betrag.values()) sp.setValue(0);
            gesamt();
        }

        /**
         * Zahlt {@code betrag} (kleinste Einheiten) aus den Münzen {@code l} (absteigend nach Wert):
         * erst passend mit möglichst großen Münzen, den Rest durch Wechseln der kleinsten ausreichenden Münze;
         * das Wechselgeld kommt in möglichst großen kleineren Münzen zurück.
         */
        static int[] bezahlen(List<Muenze> l, long betrag) {
            int n = l.size();
            int[] a = new int[n];
            for (int i = 0; i < n; i++) a[i] = l.get(i).anzahl();
            long rest = betrag;
            for (int i = 0; i < n && rest > 0; i++) {
                long v = l.get(i).einheiten;
                int k = (int) Math.min(a[i], rest / v);
                a[i] -= k;
                rest -= k * v;
            }
            if (rest > 0) {
                // kleinste vorhandene Münze, die den Rest abdeckt, wechseln
                for (int i = n - 1; i >= 0; i--) {
                    long v = l.get(i).einheiten;
                    if (a[i] > 0 && v > rest) {
                        a[i]--;
                        long wechsel = v - rest;
                        for (int j = i + 1; j < n && wechsel > 0; j++) {
                            long vj = l.get(j).einheiten;
                            int k = (int) (wechsel / vj);
                            a[j] += k;
                            wechsel -= k * vj;
                        }
                        rest = 0;
                        break;
                    }
                }
            }
            return a;
        }

        /** Betrag (kleinste Einheiten) als „2 S 5 H“. */
        static String text(List<Muenze> l, long einheiten) {
            List<String> t = new ArrayList<>();
            for (Muenze m : l) {
                long k = einheiten / m.einheiten;
                if (k > 0) { t.add(k + " " + m.kurz); einheiten -= k * m.einheiten; }
            }
            return t.isEmpty() ? "0" : String.join(" ", t);
        }

        void gesamt() {
            double s = 0;
            List<String> teile = new ArrayList<>();
            for (Map.Entry<String, List<Muenze>> e : waehrungen.entrySet()) {
                double w = 0;
                for (Muenze m : e.getValue()) w += m.anzahl() * m.wert;
                s += w;
                if (w > 0 && waehrungen.size() > 1) teile.add(e.getKey() + " " + Geld.silber(w));
            }
            gesamt.setText("Gesamt in der Geldbörse: " + Geld.silber(s) + " Silbertaler"
                    + (teile.size() > 1 ? "  (" + String.join(", ", teile) + ")" : ""));
        }
    }
}
