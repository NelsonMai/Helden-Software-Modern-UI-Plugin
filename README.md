# Modern-UI für die Helden-Software (DSA 4.1)

Ein Plugin für die [Helden-Software](https://www.helden-software.de/), das ihr eine moderne Oberfläche,
hilfreiche Tooltips und einen neu gestalteten Heldenbogen gibt. Alle Funktionen der Helden-Software bleiben
erhalten. Das Plugin nutzt ausschließlich die offizielle Plugin-Schnittstelle und verändert keine Heldendateien.
Mit einem Klick lässt sich jederzeit zum Original-Design zurückschalten.

| Modern hell | Original |
|---|---|
| ![Talentliste im modernen hellen Design](docs/bilder/modern-hell.png) | ![Talentliste im Original-Design](docs/bilder/original.png) |

## Inhalt

- [Funktionen](#funktionen)
  - [Moderne Oberfläche](#moderne-oberfläche)
  - [Tooltips und eigene Notizen](#tooltips-und-eigene-notizen)
  - [Reiter „Plugin: Modern-UI“](#reiter-plugin-modern-ui)
  - [Moderner Heldenbogen](#moderner-heldenbogen)
- [Installation](#installation)
- [Bedienung](#bedienung)
- [Selbst bauen](#selbst-bauen)
- [Aufbau des Quellcodes](#aufbau-des-quellcodes)
- [Getestete Fälle](#getestete-fälle)
- [Hinweise](#hinweise)

## Funktionen

### Moderne Oberfläche

- **Drei Designs:** *Modern hell*, *Modern dunkel* und *Original*. Das gewählte Design gilt für die ganze
  Software einschließlich aller Dialoge (Steigern, Erschaffung, Ausrüstung …).
- **Gut unterscheidbare Felder:** Tabellen mit Zebra-Streifen, Spaltenlinien und gut lesbarer Markierung.
  Bearbeitbare Eingabefelder sind hell, reine Anzeigefelder leicht getönt.
- **Eigene Symbole:** über 40 neu gezeichnete Symbole für Werkzeugleiste, Dialoge, Meldungen und die
  Markierungen in Talent- und Zauberlisten (Basistalent, Hauszauber, Leittalent …). Sie passen sich
  hellem und dunklem Design an. Im Design *Original* erscheinen wieder die ursprünglichen Symbole.

| Modern dunkel | Neue Symbole (hell und dunkel) |
|---|---|
| ![Talentliste im dunklen Design](docs/bilder/modern-dunkel.png) | ![Übersicht der neuen Symbole](docs/bilder/symbole.png) |

### Tooltips und eigene Notizen

- **Tooltips** zu Talenten, Sonderfertigkeiten, Vor- und Nachteilen und Zaubern – überall in der Software,
  auch in den Original-Dialogen. Bei eigenen Talenten mit Probe und Eigenschaftswerten, TaW, BE bzw. AT/PA.
- **Herkunft eines Werts:** Startwert aus Rasse, Kultur und Profession, Professions-Auswahl, Steigerungen bei
  der Erschaffung und jede spätere Steigerung mit Datum, Lernmethode (Lehrmeister, Selbststudium …),
  Spezieller Erfahrung und AP-Kosten. Grundlage ist das Ereignisprotokoll der Helden-Software.
- **Herkunft der Abenteuerpunkte** (Reiter „Allgemein“ und Plugin-Reiter): AP aus der Erschaffung, jede
  AP-Vergabe mit Datum und Anlass, Ausgaben nach Bereich und freie AP.
- **Eigene Notizen:** Doppelklick auf einen Eintrag oder Maus darüber und `Strg+Umschalt+T`.
  Notizen erscheinen im Tooltip und auf dem Heldenbogen. Lange Notizen werden im Tooltip gekürzt und auf
  dem Bogen vollständig gedruckt.

| Herkunft eines Talentwerts | Herkunft der Abenteuerpunkte |
|---|---|
| ![Tooltip mit Herkunft eines Talentwerts](docs/bilder/tooltip-herkunft.png) | ![Tooltip mit Herkunft der Abenteuerpunkte](docs/bilder/tooltip-ap.png) |

### Reiter „Plugin: Modern-UI“

Eine kompakte Heldenübersicht: Eigenschaften und Basiswerte als Kacheln, filterbare Talentliste mit
Fortschrittsbalken, Sonderfertigkeiten und Vor-/Nachteile mit Kurzbeschreibung, Kampfwerte, Zauber und
Inventar mit Geldbörse. Die Übersicht aktualisiert sich automatisch.

![Reiter „Plugin: Modern-UI“ im dunklen Design](docs/bilder/plugin-reiter-dunkel.png)

### Moderner Heldenbogen

Im Reiter „Druckvorschau“ zeigt das Plugin einen neu gestalteten Heldenbogen (A4) mit Drucken und
PDF-Export. Über den Umschalter *Original* ist die bisherige Druckvorschau mit allen Bogenvarianten
weiterhin erreichbar.

![Druckvorschau mit dem modernen Heldenbogen](docs/bilder/druckvorschau.png)

**Inhalt des Bogens**

- **Kopf und Werte:** Name, Rasse, Kultur, Profession, Steckbrief (Gewicht in Stein mit kg), Eigenschaften
  und Basiswerte.
- **Zustand:** LE und AU mit ½-, ⅓- und ¼-Schwellen.
- **Kampf:** Nah- und Fernkampfwaffen, Schilde und Parierwaffen, Rüstungszonen mit W20-Bereichen,
  Behinderung durch Rüstung, Initiative-Rechnung je Waffe (INI-Basis − BE + Waffen-/Schild-Mod. + 1W6) und
  eine Trefferzonen-Tabelle mit Wundkästchen je Zone.
- **Sonderfertigkeiten, Vor- und Nachteile** mit Kurzbeschreibung bzw. eigener Notiz.
- **Talente** nach Gruppen mit Probe, aktuellen Eigenschaftswerten, BE, Steigerungsspalte und TaW;
  auf Wunsch mit Meta-Talenten.
- **Magie-Seite** (nur für Zauberkundige bzw. Geweihte): Astral- bzw. Karmaenergie als Zahlenleiste für
  einen Marker und Verlaufsprotokoll – so muss nichts radiert werden. Dazu eine Zauberübersicht mit Probe,
  ZfW, AsP-Kosten, Eckdaten, Anmerkung aus der Helden-Software und eigener Notiz.
- **Inventar** mit Gewichten, Gesamtgewicht und leeren Zeilen zum Eintragen.
- **Geldbörse** mit Dukaten, Silbertalern, Hellern und Kreuzern, Gesamtwert und Umrechnung
  (1 D = 10 S = 100 H = 1000 K); fremde Währungen werden gesondert aufgeführt.
- **Notizseite** mit Feld für die Charakterbeschreibung, Portrait und Linien für weitere Notizen.

Lange Listen und Notizen laufen sauber über Spalten und Seiten weiter, lange Namen werden umbrochen statt
abgeschnitten.

| Seite 1 | Kampf und Initiative |
|---|---|
| ![Heldenbogen, Seite 1](docs/bilder/bogen-seite1.png) | ![Heldenbogen mit Rüstung, Initiative und Trefferzonen](docs/bilder/bogen-kampf-initiative.png) |

| Magie-Seite | Inventar, Geldbörse und Notizen |
|---|---|
| ![Magie-Seite mit Astralenergie und Zauberübersicht](docs/bilder/bogen-magie.png) | ![Inventar, Geldbörse und Notizseite mit Portrait](docs/bilder/bogen-inventar-notizen.png) |

**Optionen** (Knopf *Optionen ▾* in der Druckvorschau)

| Option | Wirkung |
|---|---|
| Beschreibungen & eigene Notizen | Kurzbeschreibungen und eigene Notizen zu SF, Vor-/Nachteilen und Zaubern |
| Meta-Talente zeigen | Meta-Talente wie *Wache halten* (die Helden-Software liefert sie nur, wenn in ihren Druckeinstellungen „Metatalente“ aktiv ist – der Eintrag *Meta-Talente in der Helden-Software einschalten …* öffnet diese Einstellungen) |
| Notizfelder und Linienseite | Notizseite, Schreiblinien bei Zaubern und leere Inventarzeilen |
| Portrait neben der Charakterbeschreibung | Rahmen für ein Portrait; *Portrait wählen …* hinterlegt ein Bild je Held, sonst wird das Portrait aus der Helden-Software genutzt oder ein leeres Feld zum Zeichnen gedruckt |

## Installation

1. Helden-Software beenden.
2. `ModernUI-Plugin.jar` aus den [Releases](../../releases) bzw. den Build-Artefakten herunterladen.
3. In den Plugin-Ordner kopieren:
   - Windows: `C:\Users\<Name>\helden\plugins\`
   - macOS: `/Users/<Name>/helden/plugins/`
   - Linux: `~/helden/plugins/`
4. Helden-Software starten. Nach ein bis zwei Sekunden erscheint das moderne Design.

Deinstallation: JAR aus dem Plugin-Ordner löschen. Die Helden-Software sieht danach wieder genau so aus wie vorher.

## Bedienung

| Was | Wie |
|---|---|
| Design wechseln | *Erweiterungen → Modern-UI → Design: Modern hell / Modern dunkel / Original* |
| Tooltips an/aus | *Erweiterungen → Modern-UI → Tooltips für Talente & Sonderfertigkeiten* |
| Eigene Notiz anlegen | Doppelklick auf einen Eintrag, oder Maus darüber und `Strg+Umschalt+T`, oder im Plugin-Reiter per Rechtsklick |
| Ordner mit den Notizen öffnen | *Erweiterungen → Modern-UI → Ordner mit eigenen Tooltip-Texten öffnen* |
| Heldenbogen drucken / als PDF | Reiter *Druckvorschau* → *Drucken …* bzw. *PDF …* |

Doppelklick öffnet die Notiz nur dort, wo die Helden-Software selbst nichts mit einem Doppelklick macht
(z. B. nicht in bearbeitbaren Zellen).

**Eigene Notizen** werden in `helden/plugins/modern-ui/eigene-tooltips.tsv` gespeichert (eine Zeile pro
Eintrag: `Name<TAB>Text`, Zeilenumbruch als `\n`). Die Datei bleibt auf deinem Rechner und ist nicht Teil
dieses Repositorys. Auszüge aus eigenen Regelbüchern sind für den privaten Gebrauch gedacht – die Datei
bitte nicht weitergeben.

## Selbst bauen

Voraussetzung: JDK 8 oder neuer.

```sh
./build.sh            # Ergebnis: build/ModernUI-Plugin.jar
```

Das Skript lädt FlatLaf von Maven Central (oder nimmt das JAR aus der Umgebungsvariable `FLATLAF_JAR`).
Die Helden-Software wird zum Bauen **nicht** benötigt: Der Ordner `api-stubs/` enthält nur die Signaturen
der drei verwendeten Plugin-Schnittstellen. Sie dienen nur zum Kompilieren und werden nicht ins Plugin gepackt.

GitHub Actions baut bei jedem Push und hängt das JAR als Artefakt an. Bei einem über die GitHub-Oberfläche
veröffentlichten Release (oder einem Tag `vX.Y.Z`) wird das JAR automatisch an das Release angehängt.
Die Versionsnummer kommt aus dem Tag, wenn er wie eine Version aussieht (z. B. `v1.13.2`), sonst aus der
Datei `VERSION`.

## Aufbau des Quellcodes

| Pfad (`src/main/java/de/modernhelden/`) | Inhalt |
|---|---|
| `ModernHeldenPlugin.java` | Einstiegspunkt: Plugin-Schnittstelle, Menü, Einstellungen, Datenaustausch |
| `Theme.java` | Designwechsel (FlatLaf hell/dunkel/Original), Farben für unterscheidbare Felder |
| `ModernIcons.java`, `IconSwap.java` | Eigene Vektor-Symbole und deren Austausch bzw. Wiederherstellung |
| `TabellenStil.java` | Zebra-Streifen und Gitterlinien für die Tabellen der Helden-Software |
| `TooltipHook.java`, `TooltipDB.java` | Globale Tooltips, Doppelklick-Notizen, Kurzbeschreibungen und eigene Notizen |
| `Herkunft.java` | Auswertung des Ereignisprotokolls (Herkunft von Werten und Abenteuerpunkten) |
| `HeroPanel.java`, `HeroData.java` | Reiter „Plugin: Modern-UI“ |
| `Geld.java` | Münzen, Reihenfolge und Umrechnung |
| `print/` | Druckvorschau, Heldenbogen-Layout (`ModernSheet`), Datenaufbereitung, PDF-Export |
| `src/main/resources/.../tooltips.tsv` | Kurzbeschreibungen (eigene Formulierungen) |
| `api-stubs/` | Platzhalter der Plugin-Schnittstelle, nur zum Kompilieren |
| `docs/bilder/` | Bilder für dieses README |

## Getestete Fälle

Der Heldenbogen wurde mit Testhelden geprüft – von „nichts“ bis „alles maximal“:

- sehr viele Einträge: 19 Nah- und 13 Fernkampfwaffen, 10 Schilde, 19 Rüstungsteile, 56 Sonderfertigkeiten,
  61 Vor-/Nachteile, 95 Zauber, über 200 Gegenstände, 7 Währungen, 300 AsP
- lange Namen und Notizen (bis 9 000 Zeichen, mit Absätzen und überlangen Wörtern)
- Minimalfälle: Held ohne Talente, Ausrüstung, Magie oder Namen; nur Nachteile; zauberkundig ohne Zauber;
  1 AsP bzw. 1 KaP; fehlende Bereiche in den Daten
- Designwechsel hell → dunkel → Original → hell in Hauptfenster, Dialogen und Plugin-Reiter

## Hinweise

- Die Kurzbeschreibungen sind eigene Zusammenfassungen, keine Regeltexte. Maßgeblich sind die Regelbücher
  und die Berechnungen der Helden-Software.
- Die Helden-Software ist nicht Teil dieses Repositorys und darf hier nicht eingecheckt werden.
- Die Bilder in diesem README zeigen Testhelden; das Portrait ist ein Platzhalterbild.
- *Das Schwarze Auge* und *Aventurien* sind eingetragene Marken der Ulisses Spiele GmbH. Dieses Projekt ist
  ein inoffizielles Fan-Projekt und steht in keiner Verbindung zu Ulisses Spiele oder den Entwicklern der
  Helden-Software.
- Enthält [FlatLaf](https://www.formdev.com/flatlaf/) (Apache-Lizenz 2.0, siehe `LICENSE-FlatLaf.txt`).
