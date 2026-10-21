package de.modernhelden.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Kleiner JSON-Leser (Objekte → Map, Arrays → List, Zahlen → Double) für die GitHub-Antwort. */
final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        Object v = j.wert();
        j.ws();
        if (j.i != j.s.length()) throw new IllegalArgumentException("JSON: Zeichen nach dem Ende");
        return v;
    }

    private Object wert() {
        if (i >= s.length()) throw new IllegalArgumentException("JSON: unerwartetes Ende");
        char c = s.charAt(i);
        switch (c) {
            case '{': return objekt();
            case '[': return liste();
            case '"': return text();
            case 't': wort("true"); return Boolean.TRUE;
            case 'f': wort("false"); return Boolean.FALSE;
            case 'n': wort("null"); return null;
            default: return zahl();
        }
    }

    private Map<String, Object> objekt() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = text();
            ws();
            erwarte(':');
            ws();
            m.put(k, wert());
            ws();
            if (s.charAt(i) == ',') { i++; continue; }
            erwarte('}');
            return m;
        }
    }

    private List<Object> liste() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(wert());
            ws();
            if (s.charAt(i) == ',') { i++; continue; }
            erwarte(']');
            return l;
        }
    }

    private String text() {
        erwarte('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                default: b.append(e);
            }
        }
    }

    private Double zahl() {
        int a = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (a == i) throw new IllegalArgumentException("JSON: unerwartetes Zeichen bei " + a);
        return Double.valueOf(s.substring(a, i));
    }

    private void wort(String w) {
        if (!s.startsWith(w, i)) throw new IllegalArgumentException("JSON: erwartet " + w);
        i += w.length();
    }

    private void erwarte(char c) {
        if (i >= s.length() || s.charAt(i) != c) throw new IllegalArgumentException("JSON: erwartet " + c + " bei " + i);
        i++;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }
}
