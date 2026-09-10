#!/bin/bash
#
# Kopiert das gebaute Jar nach /srv/monitoring und startet den Dienst neu.
# Die Version wird aus der pom.xml gelesen, damit hier nicht eine veraltete
# Versionsnummer stehen bleibt und der Dienst nach dem Deploy unbemerkt weiter
# mit dem alten Stand laeuft.
set -euo pipefail

cd "$(dirname "$0")"

VERSION="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout)"
JAR="target/monitoring-service-${VERSION}.jar"
TARGET_DIR=/srv/monitoring

if [ ! -f "$JAR" ]; then
    echo "FEHLER: $JAR nicht gefunden. Zuerst 'mvn clean install' ausfuehren." >&2
    exit 1
fi

sudo systemctl stop monitoring
sudo cp "$JAR" "$TARGET_DIR/"
sudo chmod 755 "$TARGET_DIR/monitoring-service-${VERSION}.jar"
# Stabiler Name fuer die systemd-Unit, damit diese die Versionsnummer nicht kennt.
sudo ln -sfn "$TARGET_DIR/monitoring-service-${VERSION}.jar" "$TARGET_DIR/monitoring-service.jar"
sudo systemctl start monitoring

echo "Version ${VERSION} deployed."
