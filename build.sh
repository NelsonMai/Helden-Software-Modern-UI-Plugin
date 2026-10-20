#!/bin/sh
# Baut build/ModernUI-Plugin.jar (Java 8 kompatibel).
# Benötigt ein JDK (8 oder neuer). FlatLaf wird bei Bedarf von Maven Central geladen;
# alternativ den Pfad zu einem FlatLaf-JAR in FLATLAF_JAR angeben.
set -e
cd "$(dirname "$0")"

VERSION="${VERSION:-$(cat VERSION)}"
FLATLAF_VERSION="3.5.4"
FLATLAF_JAR="${FLATLAF_JAR:-lib/flatlaf-$FLATLAF_VERSION.jar}"

if [ ! -f "$FLATLAF_JAR" ]; then
  mkdir -p lib
  echo "Lade FlatLaf $FLATLAF_VERSION ..."
  curl -fsSL -o "$FLATLAF_JAR" \
    "https://repo1.maven.org/maven2/com/formdev/flatlaf/$FLATLAF_VERSION/flatlaf-$FLATLAF_VERSION.jar"
fi

rm -rf build
mkdir -p build/stubs build/classes

# 1) Schnittstellen-Platzhalter (nur zum Kompilieren, landen nicht im JAR)
javac --release 8 -nowarn -encoding UTF-8 -d build/stubs $(find api-stubs -name '*.java')

# 2) Plugin
javac --release 8 -nowarn -encoding UTF-8 -cp "build/stubs:$FLATLAF_JAR" -d build/classes \
  $(find src/main/java -name '*.java')
cp -r src/main/resources/* build/classes/

# 3) FlatLaf einbetten (ohne native Bibliotheken und Signaturdateien)
FLATLAF_ABS="$(cd "$(dirname "$FLATLAF_JAR")" && pwd)/$(basename "$FLATLAF_JAR")"
(cd build/classes && unzip -q -o "$FLATLAF_ABS" -x 'META-INF/*' 'com/formdev/flatlaf/natives/*')
cp LICENSE-FlatLaf.txt build/classes/FLATLAF-LICENSE.txt

# 4) Manifest – die Helden-Software sucht den Eintrag "HeldenPluginClass"
printf 'Manifest-Version: 1.0\nHeldenPluginClass: de.modernhelden.ModernHeldenPlugin.class\nImplementation-Title: Modern-UI fuer die Helden-Software\nImplementation-Version: %s\n' "$VERSION" > build/MANIFEST.MF
(cd build/classes && jar cfm ../ModernUI-Plugin.jar ../MANIFEST.MF .)
echo "Fertig: build/ModernUI-Plugin.jar ($VERSION)"
