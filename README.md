# Modern-UI für die Helden-Software (DSA 4.1)

Ein Plugin für die [Helden-Software](https://www.helden-software.de/), das ihr eine moderne Oberfläche gibt.
Alle Funktionen der Helden-Software bleiben unverändert erhalten. Das Plugin nutzt ausschließlich die
offizielle Plugin-Schnittstelle und verändert keine Heldendateien.

### Moderne Designs: Felder und Symbole
* Tabellen der Helden-Software mit Zebra-Streifen, sichtbaren Spaltenlinien und gut lesbarer Markierung;
  bearbeitbare Felder sind weiß, reine Anzeigefelder leicht getönt.
* Eigene, gezeichnete Symbole (Werkzeugleiste, Dialoge, Meldungen, Markierungen wie Basistalent,
  Hauszauber, Leittalent …) – nur in „Modern hell“ und „Modern dunkel“. Im Design „Original“ erscheinen
  wieder die ursprünglichen Symbole und Tabellen.

## Funktionen

- **Modernes Design für die ganze Software**: hell oder dunkel, inklusive aller Dialoge
  (Steigern, Erschaffung, Ausrüstung …). Umschaltbar unter *Erweiterungen → Modern-UI*, auch zurück zum Original.
- **Tooltips** zu Talenten, Sonderfertigkeiten sowie Vor- und Nachteilen – auch in den Original-Dialogen.
  Bei eigenen Talenten mit Probe und Eigenschaftswerten, TaW, BE bzw. AT/PA.
- **Herkunft des Werts** im Tooltip: Startwert aus Rasse/Kultur/Profession, Professions-Auswahl,
  Steigerungen bei der Erschaffung und jede spätere Steigerung mit Datum, Lernmethode
  (Lehrmeister, Selbststudium …), Spezieller Erfahrung und AP-Kosten. Bei Sonderfertigkeiten das Erwerbsdatum.
  Grundlage ist das Ereignisprotokoll der Helden-Software.
- **Eigene Tooltip-Texte**: Doppelklick auf einen Eintrag oder Maus darüber und `Strg+Umschalt+T`. Gespeichert in
  `helden/plugins/modern-ui/eigene-tooltips.tsv` (nur lokal, nicht im Repository).
- **Reiter „Plugin: Modern-UI“**: moderne Heldenübersicht mit Eigenschaften, Basiswerten, filterbaren Talenten,
  Sonderfertigkeiten, Vor-/Nachteilen, Kampfwerten, Zaubern und Inventar.
- **Moderne Druckvorschau** im Reiter „Druckvorschau“: neu gestalteter Heldenbogen (A4) mit Zustand
  (LE/AU-Schwellen, Wundkästchen), Waffen, Rüstungszonen inkl. Trefferzonen-Tabelle (Zufall, Reiter, gezielter Schlag, Wundfolgen, Wundkästchen je Zone), Gesamt-BE und
  Initiative-Rechnung je Waffe (INI-Basis − BE + Waffen-/Schild-Mod. + 1W6), Talenten mit Probe,
  Werten, BE und Steigerungsspalte, Inventar und Notizseite. Drucken, PDF-Export, Umschalter zur Original-Vorschau.
  Unter *Optionen* lassen sich Kurzbeschreibungen, Meta-Talente und die Notizseite ein- und ausblenden.
  Zauber zeigen darunter die Anmerkung aus der Helden-Software und deine eigene Notiz (Strg+Umschalt+T
  über dem Zauber). Die Geldbörse listet Dukaten, Silbertaler, Heller und Kreuzer mit Gesamtwert und
  Umrechnung (1 D = 10 S = 100 H = 1000 K), Gewichte stehen in Stein mit kg in Klammern, und die
  Notizseite hat ein Feld für die Charakterbeschreibung, daneben ein Portrait (unter *Optionen* ein- und
  ausschaltbar; Bild per „Portrait wählen …“ je Held hinterlegen, sonst das Portrait aus der Helden-Software
  oder ein leeres Feld zum Zeichnen). Das Inventar hat leere Zeilen zum Eintragen weiterer Gegenstände.
* Tooltip an den Abenteuerpunkten (Reiter „Allgemein“ und Plugin-Reiter): AP aus der Erschaffung,
  jede AP-Vergabe aus den Ereignissen mit Datum und Anlass, Gesamt, Ausgaben nach Bereich und freie AP.
  Zauberkundige (und Geweihte) bekommen eine eigene Magie-Seite: Astral- bzw. Karmaenergie mit Zahlenleiste
  für einen Marker und Verlaufsprotokoll (kein Radieren nötig) sowie eine Zauberübersicht mit Probe, ZfW,
  AsP-Kosten, Eckdaten und Notizen. Helden ohne Magie bekommen diese Seite nicht.
  Meta-Talente (z. B. Wache halten) berechnet die Helden-Software nur, wenn in ihren Druckeinstellungen „Metatalente“
  aktiv ist – der Eintrag *Meta-Talente in der Helden-Software einschalten …* öffnet diese Einstellungen direkt.

## Installation

1. Helden-Software beenden.
2. `ModernUI-Plugin.jar` aus den [Releases](../../releases) bzw. den Build-Artefakten herunterladen.
3. In den Plugin-Ordner kopieren:
   - Windows: `C:\Users\<Name>\helden\plugins\`
   - macOS: `/Users/<Name>/helden/plugins/`
   - Linux: `~/helden/plugins/`
4. Helden-Software starten.

Deinstallation: JAR aus dem Plugin-Ordner löschen.

## Selbst bauen

Voraussetzung: JDK 8 oder neuer.

```sh
./build.sh            # Ergebnis: build/ModernUI-Plugin.jar
```

Das Skript lädt FlatLaf von Maven Central (oder nimmt das JAR aus `FLATLAF_JAR`).
Die Helden-Software selbst wird zum Bauen **nicht** benötigt: Der Ordner `api-stubs/` enthält nur die
Signaturen der drei verwendeten Plugin-Schnittstellen. Sie werden zum Kompilieren genutzt, aber nicht
ins Plugin gepackt – zur Laufzeit liefert die Helden-Software die echten Klassen.

GitHub Actions baut bei jedem Push automatisch und hängt das JAR als Artefakt an.
Ein Tag `vX.Y.Z` erzeugt zusätzlich ein Release mit dem JAR.

## Aufbau

| Pfad | Inhalt |
|---|---|
| `src/main/java/de/modernhelden/ModernHeldenPlugin.java` | Einstiegspunkt (Plugin-Schnittstelle, Menü, Einstellungen) |
| `.../Theme.java` | Umschalten des Designs (FlatLaf hell/dunkel/Original) |
| `.../TooltipHook.java`, `TooltipDB.java` | Globale Tooltips und Tooltip-Daten |
| `.../HeroPanel.java`, `HeroData.java` | Reiter „Plugin: Modern-UI“ |
| `.../print/` | Moderne Druckvorschau, Heldenbogen-Layout, PDF-Export |
| `src/main/resources/de/modernhelden/tooltips.tsv` | Kurzbeschreibungen (eigene Formulierungen) |
| `api-stubs/` | Platzhalter der Plugin-Schnittstelle, nur zum Kompilieren |

## Hinweise

- Die Kurzbeschreibungen sind eigene Zusammenfassungen, keine Regeltexte. Maßgeblich sind die Regelbücher
  und die Berechnungen der Helden-Software.
- Die Helden-Software ist nicht Teil dieses Repositories und darf hier nicht eingecheckt werden.
- *Das Schwarze Auge* und *Aventurien* sind eingetragene Marken der Ulisses Spiele GmbH. Dieses Projekt ist
  ein inoffizielles Fan-Projekt und steht in keiner Verbindung zu Ulisses Spiele oder den Entwicklern der
  Helden-Software.
- Enthält [FlatLaf](https://www.formdev.com/flatlaf/) (Apache-Lizenz 2.0, siehe `LICENSE-FlatLaf.txt`).
