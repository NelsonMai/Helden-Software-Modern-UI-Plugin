package de.modernhelden;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Macht einzelne Ereignisse (Talent steigern, Sonderfertigkeit hinzugefügt …) rückgängig.
 *
 * Die Plugin-Schnittstelle der Helden-Software kann keine Werte ändern. Die Helden-Software hat aber eine
 * eigene Undo-Logik („Bearbeiten → Undo“), die Werte zurücksetzt, AP erstattet und vorher prüft, ob der
 * aktuelle Wert noch zum Ereignis passt. Sie arbeitet allerdings nur mit dem jeweils letzten Ereignis und
 * nur mit Ereignissen der laufenden Sitzung. Diese Klasse nutzt genau diese Logik für ein ausgewähltes
 * Ereignis: Sie stellt es kurz allein in die Ereignisliste, ruft das Undo der Helden-Software auf und setzt
 * die übrigen Ereignisse danach unverändert zurück. Schlägt etwas fehl, wird alles wiederhergestellt.
 *
 * Getestet mit Helden-Software 5.6.0. Findet die Klasse die erwarteten Bausteine nicht, meldet sie das,
 * statt etwas zu verändern.
 */
public final class Rueckgaengig {

    private Rueckgaengig() {}

    /** Ereignisarten, die die Undo-Logik der Helden-Software beherrscht (ohne AP-Vergaben). */
    public static final String[] ARTEN = {"Talent steigern", "Zauber steigern", "Nahkampftalent steigern",
            "Sonderfertigkeit hinzugefügt", "Eigenschaft steigern", "Talent aktivieren"};

    public static boolean unterstuetzt(String text) {
        for (String a : ARTEN) if (a.equals(text)) return true;
        return false;
    }

    /** Ergebnis: null = erfolgreich, sonst die Fehlermeldung für den Nutzer. */
    public static String ausfuehren(Frame frame, long zeit, String text) {
        Object action = undoAction(frame);
        if (action == null) return "Die Rückgängig-Funktion der Helden-Software wurde nicht gefunden (Menü „Bearbeiten → Undo“).";
        try {
            Object gui = feldVomTyp(action, "helden.gui.A");
            if (gui == null) return "Unbekannte Version der Helden-Software (Undo ohne Heldenbezug).";
            Object held = methodeMitRueckgabe(gui, "helden.framework.held.H");
            if (held == null) return "Kein Held ausgewählt.";
            List<Object> liste = ereignisListe(held);
            Object undo = methodeMitRueckgabe(held, "helden.framework.held.void");
            if (liste == null || undo == null) return "Unbekannte Version der Helden-Software (Ereignisliste nicht gefunden).";

            Object ziel = null;
            for (Object e : liste) {
                if (zeitVon(e) == zeit && hatText(e, text)) { ziel = e; break; }
            }
            if (ziel == null) return "Das Ereignis wurde in der Helden-Software nicht gefunden. Bitte die Ansicht aktualisieren.";

            Field gesperrt = boolFeld(ziel);
            boolean warGesperrt = gesperrt != null && gesperrt.getBoolean(ziel);
            // Aus der Datei geladene Ereignisse kennen das Talent/den Zauber nur als Text – die Undo-Logik
            // braucht das Objekt des Helden. Für die Dauer des Undo das passende Objekt einsetzen.
            Field objFeld = objektFeld(ziel);
            Object objVorher = objFeld == null ? null : objFeld.get(ziel);
            boolean braucht = text.startsWith("Talent") || text.startsWith("Zauber") || text.startsWith("Nahkampftalent");
            if (braucht && objVorher instanceof String) {
                Object o = suche(held, (String) objVorher, text.startsWith("Zauber"));
                if (o == null) return "„" + objVorher + "“ wurde beim Helden nicht gefunden.";
                objFeld.set(ziel, o);
            }
            List<Object> kopie = new ArrayList<>(liste);
            String fehler = null;
            try {
                // das Ereignis kurz allein in die Liste stellen, damit Undo genau dieses (und nur dieses) zurücknimmt
                liste.clear();
                liste.add(ziel);
                if (gesperrt != null) gesperrt.setBoolean(ziel, false);
                Method neu = undo.getClass().getDeclaredMethod("new");
                neu.setAccessible(true);
                neu.invoke(undo);
                if (!liste.isEmpty()) fehler = "Die Helden-Software hat das Rückgängigmachen abgelehnt.";
            } catch (InvocationTargetException ex) {
                Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                fehler = "Die Helden-Software konnte das nicht rückgängig machen – der aktuelle Wert passt nicht mehr "
                        + "zum Ereignis (z. B. wurde danach weiter gesteigert oder der Wert anders geändert).\n(" + t + ")";
            } finally {
                // übrige Ereignisse in ursprünglicher Reihenfolge zurück
                liste.clear();
                for (Object e : kopie) if (fehler != null || e != ziel) liste.add(e);
                if (fehler != null && gesperrt != null) gesperrt.setBoolean(ziel, warGesperrt);
                if (fehler != null && objFeld != null) objFeld.set(ziel, objVorher);
            }
            if (fehler != null) return fehler;
            oberflaecheAktualisieren(action, gui);
            return null;
        } catch (Exception ex) {
            return "Rückgängig machen nicht möglich: " + ex;
        }
    }

    // ------------------------------------------------------------------ Reflection-Hilfen

    /** Die Undo-Aktion der Helden-Software aus dem Menü „Bearbeiten“. */
    private static Object undoAction(Frame frame) {
        if (!(frame instanceof JFrame) || ((JFrame) frame).getJMenuBar() == null) return null;
        JMenuBar bar = ((JFrame) frame).getJMenuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu m = bar.getMenu(i);
            if (m == null) continue;
            for (int k = 0; k < m.getItemCount(); k++) {
                JMenuItem it = m.getItem(k);
                if (it != null && it.getAction() != null && it.getAction().getClass().getName().equals("helden.gui.Object.for"))
                    return it.getAction();
            }
        }
        return null;
    }

    private static Object feldVomTyp(Object o, String typ) throws IllegalAccessException {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().getName().equals(typ) && !Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    return f.get(o);
                }
            }
        }
        return null;
    }

    private static Object methodeMitRueckgabe(Object o, String typ) throws Exception {
        for (Method m : alleMethoden(o.getClass())) {
            if (m.getParameterCount() == 0 && m.getReturnType().getName().equals(typ)) {
                m.setAccessible(true);
                return m.invoke(o);
            }
        }
        return null;
    }

    private static List<Method> alleMethoden(Class<?> c) {
        List<Method> l = new ArrayList<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) l.addAll(java.util.Arrays.asList(k.getDeclaredMethods()));
        for (Class<?> i : c.getInterfaces()) l.addAll(java.util.Arrays.asList(i.getMethods()));
        return l;
    }

    /** Die Ereignisliste des Helden: ArrayList&lt;helden.framework.A.A&gt; ohne Parameter. */
    @SuppressWarnings("unchecked")
    private static List<Object> ereignisListe(Object held) throws Exception {
        for (Method m : alleMethoden(held.getClass())) {
            if (m.getParameterCount() != 0 || !List.class.isAssignableFrom(m.getReturnType())) continue;
            Type g = m.getGenericReturnType();
            if (g instanceof ParameterizedType) {
                Type[] a = ((ParameterizedType) g).getActualTypeArguments();
                if (a.length == 1 && a[0].getTypeName().equals("helden.framework.A.A")) {
                    m.setAccessible(true);
                    return (List<Object>) m.invoke(held);
                }
            }
        }
        return null;
    }

    private static long zeitVon(Object ereignis) throws IllegalAccessException {
        for (Field f : ereignis.getClass().getDeclaredFields()) {
            if (f.getType() == Date.class) {
                f.setAccessible(true);
                Date d = (Date) f.get(ereignis);
                return d == null ? -1 : d.getTime();
            }
        }
        return -1;
    }

    private static boolean hatText(Object ereignis, String text) throws IllegalAccessException {
        for (Field f : ereignis.getClass().getDeclaredFields()) {
            if (f.getType() == String.class && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                if (text.equals(f.get(ereignis))) return true;
            }
        }
        return false;
    }

    /** Das Objekt-Feld des Ereignisses (Talent, Zauber oder Name). */
    private static Field objektFeld(Object ereignis) {
        for (Field f : ereignis.getClass().getDeclaredFields()) {
            if (f.getType() == Object.class && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    /**
     * Sucht beim Helden das Talent bzw. den Zauber mit diesem Namen: alle Listen des Helden durchgehen,
     * deren Elemente Talente/Zauber der Helden-Software sind, und per Anzeigename vergleichen.
     */
    private static Object suche(Object held, String name, boolean zauber) throws Exception {
        if (!zauber) {
            // wie beim Laden der Heldendatei: Talent über die TalentFabrik der Helden-Software holen
            try {
                Class<?> fabrik = Class.forName("helden.framework.B.N", true, held.getClass().getClassLoader());
                Method inst = fabrik.getDeclaredMethod("forsuper");
                inst.setAccessible(true);
                Object f = inst.invoke(null);
                for (Method m : fabrik.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                            && m.getReturnType().getName().equals("helden.framework.B.oOoo")) {
                        m.setAccessible(true);
                        Object t = m.invoke(f, name);
                        if (t != null && passt(t, name)) return t;
                    }
                }
            } catch (Exception ignore) {
                // ältere/neuere Version: unten in den Listen des Helden weitersuchen
            }
        }
        String typ = zauber ? "helden.framework.zauber.KonkreterZauber" : "helden.framework.B.public";
        Class<?> basis = Class.forName(typ, false, held.getClass().getClassLoader());
        for (Method m : alleMethoden(held.getClass())) {
            if (m.getParameterCount() != 0 || !java.util.Collection.class.isAssignableFrom(m.getReturnType())) continue;
            if (Modifier.isAbstract(m.getModifiers()) && !m.getDeclaringClass().isInterface()) continue;
            Object liste;
            try {
                m.setAccessible(true);
                liste = m.invoke(held);
            } catch (Exception ex) { continue; }
            if (!(liste instanceof java.util.Collection)) continue;
            for (Object o : (java.util.Collection<?>) liste) {
                if (o != null && basis.isInstance(o) && passt(o, name)) return o;
            }
        }
        return null;
    }

    private static boolean passt(Object o, String name) {
        String t = String.valueOf(o).trim();
        if (t.equals(name)) return true;
        // Zauber zeigen teils Zusätze wie Repräsentation: „Odem Arcanum (Mag)“
        return t.startsWith(name + " (") || t.startsWith(name + " [");
    }

    /** Merker „aus Datei geladen – nicht rückgängig machbar“ (einziges boolesches Feld des Ereignisses). */
    private static Field boolFeld(Object ereignis) {
        for (Field f : ereignis.getClass().getDeclaredFields()) {
            if (f.getType() == boolean.class && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    /** Wie nach „Bearbeiten → Undo“: Menüeintrag aktualisieren und Oberfläche neu zeichnen. */
    private static void oberflaecheAktualisieren(Object action, Object gui) {
        try {
            Method m = action.getClass().getDeclaredMethod("super");
            m.setAccessible(true);
            m.invoke(action);
        } catch (Exception ignore) { }
        try {
            Method m = gui.getClass().getDeclaredMethod("if");
            m.setAccessible(true);
            m.invoke(gui);
        } catch (Exception ignore) { }
    }
}
