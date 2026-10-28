# Modern-UI für die Helden-Software (DSA 4.1)

Ein Plugin für die [Helden-Software](https://www.helden-software.de/): moderne Oberfläche, übersichtliches
Inventar und Ausrüstung, Tooltips, ein Verlauf zum Rückgängigmachen und ein neuer Heldenbogen. Es nutzt nur die
offizielle Plugin-Schnittstelle, verändert keine Heldendateien und lässt sich jederzeit auf das Original-Design
zurückschalten.

| Modern hell | Original |
|---|---|
| ![Talentliste im modernen hellen Design](docs/bilder/modern-hell.png) | ![Talentliste im Original-Design](docs/bilder/original.png) |

## Funktionen

### Oberfläche

- **Drei Designs:** *Modern hell*, *Modern dunkel* und *Original* – für die ganze Software samt Dialogen.
- **Tabellen** mit Zebra-Streifen, sortierbar; **Suche** bei Talenten und Zaubern; **eigene Symbole**.
- Neu gestaltete Reiter nur in den modernen Designs; Änderungen laufen immer über die Helden-Software.

| Modern dunkel | Neue Symbole |
|---|---|
| ![Talentliste im dunklen Design](docs/bilder/modern-dunkel.png) | ![Übersicht der neuen Symbole](docs/bilder/symbole.png) |

### Helden erschaffen

Assistent in neuem Rahmen: Schritte links, Erklärung in der Mitte, rechts der bisherige Held mit GP/AP als Balken
und Hinweisen der Regelprüfung. Sortierbare Tabellen mit Suche, Vor- und Nachteile getrennt nebeneinander.
„Einen Helden eingeben/editieren“ im selben Rahmen.

![Erschaffung: Schritt Kultur mit Info-Spalte](docs/bilder/erschaffung.png)

### Allgemein

Steckbrief, Abenteuerpunkte mit Herkunft und *AP hinzufügen …*, Vor-/Nachteile und SF mit Kurzbeschreibung.

![Reiter „Allgemein“ im modernen Design](docs/bilder/allgemein.png)

### Eigenschaften

Abenteuerpunkte, Eigenschaften als Kacheln mit Steigern und Zurücknehmen, LE/AU/AE/MR mit Rechenweg, Kampf-Basiswerte,
Sozialstatus und Gilde.

![Reiter „Eigenschaften“ im modernen Design](docs/bilder/eigenschaften.png)

### Inventar, Hinzufügen und Geldbörse

Gewicht und Geld auf einen Blick, Suche und Gruppierung; Gegenstände mit Werten hinzufügen und direkt kaufen;
Geldbörse zum Einnehmen und Ausgeben mit automatischem Wechseln.

![Inventar im modernen Design](docs/bilder/inventar.png)

| Gegenstand hinzufügen | Geldbörse |
|---|---|
| ![Dialog „Gegenstände zum Inventar hinzufügen“](docs/bilder/inventar-hinzufuegen.png) | ![Geldbörse mit Ein- und Ausgaben](docs/bilder/geldboerse.png) |

### Ausrüstung

Je Set Figur mit RS je Zone, Belastung und Kampfwerte; Rüstung nach Zonen, Waffen als Kacheln. Hand, Schild,
beidhändig und Jagdwaffe per Schalter, „Werte …“ und Ablegen direkt; *Bearbeiten*: Platz wählen und anlegen.

| Ausrüstung | Nahkampfwaffen bearbeiten |
|---|---|
| ![Reiter Ausrüstung im modernen Design](docs/bilder/ausruestung.png) | ![Dialog Nahkampfwaffen](docs/bilder/ausruestung-nahkampf.png) |

### Kampfwerte

AT/PA-Verteilung als Balken mit freien TaP, Basiswerte mit Formel, Fernkampf mit Rechenweg; ordnet sich nach Fensterbreite an.

![Reiter „Kampfwerte“ im modernen Design](docs/bilder/kampfwerte.png)

### Tooltips und eigene Notizen

Tooltips zu Talenten, SF, Vor-/Nachteilen, Zaubern und Werten, mit Herkunft jeder Steigerung; eigene Notizen per
Doppelklick oder `Strg+Umschalt+T` (auch auf dem Heldenbogen).

| Herkunft eines Talentwerts | Herkunft der Abenteuerpunkte |
|---|---|
| ![Tooltip mit Herkunft eines Talentwerts](docs/bilder/tooltip-herkunft.png) | ![Tooltip mit Herkunft der Abenteuerpunkte](docs/bilder/tooltip-ap.png) |

### Verlauf

Alle Steigerungen mit Datum, Lernmethode und AP; *Rückgängig machen …* nimmt die letzte Änderung zurück.

![Reiter „Verlauf“ mit Lernmethode und Spezieller Erfahrung](docs/bilder/verlauf.png)

### Kompaktansicht

Der ganze Held auf einen Blick.

![Reiter „Kompaktansicht“ im dunklen Design](docs/bilder/kompaktansicht-dunkel.png)

### Moderner Heldenbogen

A4-Bogen in der Druckvorschau (Drucken und PDF): Werte, Kampf mit Trefferzonen, Talente, Magie, Inventar und
Notizen mit Portrait. *Optionen ▾* blendet Teile ein und aus, legt das Portrait-Format fest und nimmt eine
Charakterbeschreibung auf; *Original* zeigt die bisherige Druckvorschau.

![Druckvorschau mit dem modernen Heldenbogen](docs/bilder/druckvorschau.png)

| Seite 1 | Kampf und Initiative |
|---|---|
| ![Heldenbogen, Seite 1](docs/bilder/bogen-seite1.png) | ![Heldenbogen mit Rüstung, Initiative und Trefferzonen](docs/bilder/bogen-kampf-initiative.png) |
| **Talente** | **Magie-Seite** |
| ![Heldenbogen mit Talenten nach Gruppen](docs/bilder/bogen-talente.png) | ![Magie-Seite mit Astralenergie und Zauberübersicht](docs/bilder/bogen-magie.png) |
| **Inventar, Geldbörse und Notizen** | |
| ![Inventar, Geldbörse und Notizseite mit Portrait](docs/bilder/bogen-inventar-notizen.png) | |

## Installation

1. Helden-Software beenden.
2. `ModernUI-Plugin.jar` aus den [Releases](../../releases) herunterladen.
3. In den Plugin-Ordner kopieren: Windows `C:\Users\<Name>\helden\plugins\`, macOS `/Users/<Name>/helden/plugins/`,
   Linux `~/helden/plugins/`.
4. Helden-Software starten.

Deinstallation: JAR löschen – die Helden-Software sieht danach aus wie vorher.

**Updates:** automatisch über *Einstellungen → Updater → Automatisch Updaten* oder von Hand über
*Erweiterungen → Modern-UI → Nach Updates suchen …*.

## Bedienung

| Was | Wie |
|---|---|
| Design, Tooltips an/aus | *Erweiterungen → Modern-UI* |
| AP hinzufügen | *Allgemein* → *AP hinzufügen …* oder „+350“ ins Feld *AP gesamt* |
| Eigene Notiz | Doppelklick auf einen Eintrag oder `Strg+Umschalt+T` |
| Ausrüstung anlegen / ablegen | *Ausrüstung* → *Bearbeiten* → Platz wählen, „Anlegen“ / rotes „✕“ |
| Steigerung rückgängig | *Verlauf* → Eintrag wählen → *Rückgängig machen …* |

Eigene Notizen liegen nur auf deinem Rechner in `helden/plugins/modern-ui/eigene-tooltips.tsv` – Auszüge aus
Regelbüchern bitte nicht weitergeben.

## Selbst bauen

JDK 8 oder neuer: `./build.sh` erzeugt `build/ModernUI-Plugin.jar` (FlatLaf von Maven Central oder aus
`FLATLAF_JAR`). Quellcode in `src/main/java/de/modernhelden/`.

## Hinweise

- Die Kurzbeschreibungen sind eigene Zusammenfassungen, keine Regeltexte; maßgeblich sind die Regelbücher und
  die Berechnungen der Helden-Software.
- Die Helden-Software ist nicht Teil dieses Repositorys. Die Bilder zeigen Testhelden.
- *Das Schwarze Auge* und *Aventurien* sind Marken der Ulisses Spiele GmbH. Inoffizielles Fan-Projekt ohne
  Verbindung zu Ulisses Spiele oder den Entwicklern der Helden-Software.
- Enthält [FlatLaf](https://www.formdev.com/flatlaf/) (Apache-Lizenz 2.0, siehe `LICENSE-FlatLaf.txt`).
