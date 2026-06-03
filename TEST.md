TESTING - Village Plugin

Scope: Verifikation des Villager-Construction-Flow und Basischecks

Vorbereitung

1. Baue das Plugin:

```bash
mvn -DskipTests clean package
```

2. Kopiere die erzeugte JAR (`target/village-1.0.0.jar`) in den `plugins`-Ordner eines lokalen Paper/Spigot-Servers (1.21).
3. Stelle sicher, dass folgende Plugins installiert und aktiviert sind: Vault, WorldEdit, WorldGuard, Citizens (falls NPCs benötigt werden), ProtocolLib.
4. Starte den Server.

Testfälle

1) Villager-Zuweisung über GUI → Workstation
- Schritte:
  - Erstelle oder betrete ein Dorf.
  - Platziere ein Gebäude mit Workstation-Blöcken (z. B. Truhe, Lectern, Bell).
  - Öffne das Baumenü (bei einem aktiven Bau) und klicke "Villager zuweisen".
  - Wähle einen Villager aus der Liste.
  - Klicke nun im Spiel auf einen Workstation-Block des fertigen Gebäudes.
- Erwartet:
  - Im Chat erscheint eine Bestätigung: "<Villager> arbeitet jetzt als <Beruf> in <Gebäude> (workstation_<type>)".
  - Der Villager erhält das zugewiesene Job-Ziel (bei NPCs: Profi-Umstellung, bei echten Villagern: Job-Mapping).

1a) Block-Check-Gebäude und Workstation-Platzierung
- Schritte:
  - Wähle ein block-check-basiertes Gebäude wie den Dorfbrunnen.
  - Platziere die erforderlichen Workstation-Blöcke im Bereich (z. B. Glocke am Bauort).
  - Bestätige den Bau mit /village buildconfirm ja.
- Erwartet:
  - Der Bau wird nur abgeschlossen, wenn die konfigurierten Workstation-Blöcke vorhanden sind.
  - Fehlende oder zu viele spezifizierte zusätzliche Workstation-Blöcke verhindern die Fertigstellung.

2) Klick auf Workstation ohne offene Zuweisung
- Schritte:
  - Rechtsklick auf eine Workstation ohne vorherige Villager-Auswahl.
- Erwartet:
  - WorkstationInteractListener öffnet Truhe / zeigt Info (keine Job-Zuweisung wird ausgelöst).

3) Abgebrochene Auswahl
- Schritte:
  - Öffne Villagers-GUI über Baumenü, dann schließe das GUI ohne Auswahl.
  - Klicke auf Workstation.
- Erwartet:
  - Keine Job-Zuweisung erfolgt; normale Workstation-Verhalten bleibt.

4) Permissions
- Schritte:
  - Teste mit einem Spieler ohne `village.admin` und ohne Manage-Permission.
  - Versuche Villager zuzuweisen und Workstation-Interaktionen.
- Erwartet:
  - Fehlende Berechtigungen blockieren Aktionen; der Chat zeigt passende Meldungen.

5) Path-Upgrade-Effekte
- Vorbereitung:
  - Erstelle ein Pfad-Gebäude (z. B. `street`) und registriere es über Walk-Session (`/village path start` ... `/village path done`).
  - Stelle sicher, dass das Pfad-Gebäude als fertig markiert ist und eine Zone registriert wurde.
- Schritte:
  - Prüfe Basiswerte im Chat nachdem die Zone registriert wurde (Speed-Bonus & Nachlauf angezeigt).
  - Upgraden: Führe ein Upgrade auf das Pfad-Gebäude durch (`/village building upgrade <id>` oder über das Upgrade-GUI).
  - Beobachte die Chatmeldungen und betrete bzw. verlasse die Pfadzone.
- Erwartet:
  - Breite der Zone vergrößert sich, falls `widthUpgradeable` gesetzt.
  - Speed-Bonus (Potion `SPEED`) steigt pro Level um +1, bis Speed V (sichtbar beim Betreten).
  - Nachlauf-Dauer (Nachverfolgung nach Verlassen) erhöht sich um ~2s pro Level.

Fehlerbehebung für Pfade

- Wenn die Zone nach Upgrade nicht aktualisiert wird: prüfe `server.log` auf Exceptions; `BuildingService.upgradeBuilding` ruft `PathService.refreshZoneForBuilding(...)` auf.
- Wenn Speed-Effekte nicht angewendet werden: prüfe ob `PathEffectListener` registriert ist und `PathService.getZoneAt(loc)` die Zone liefert.

Fehlerbehebung

- Wenn Klicks auf Workstations keine Zuordnung auslösen: prüfe `server.log` auf Exceptions und ob `GuiClickListener`-Maps (`pendingJobAssignments`) korrekt gesetzt werden.
- Wenn der Villager nicht die erwartete Profession erhält: prüfe `config/buildings.yml` und `config.yml` auf `professions`-Mapping.

6) Path-Upgrade-Konfiguration
- Schritte:
  - Öffne `config/buildings.yml` und suche den Pfad-Gebäudetyp, z. B. `street`.
  - Prüfe oder ändere unter `path.speed_effect` die neuen Werte:
    - `amplifier_per_level`
    - `max_amplifier`
    - `duration_after_leave_ticks_per_level`
  - Starte den Server neu, und führe ein Upgrade am Pfad-Gebäude durch.
- Erwartet:
  - Die Pfad-Effekte passen sich an die konfigurierten Inkremente an.
  - `max_amplifier` wird nicht überschritten.

Weiteres

- Nach erfolgreichem Test fahre mit dem Implementieren der "Path upgrade mechanics" fort.

-- Verifikation und automatisierte Tests --

1. Unit-Tests ausführen (lokal):
```bash
mvn -DskipTests=false test
```

2. Config-Migration prüfen:
 - Setze in einer Testkopie von `config/buildings.yml` `config-version: 0` und starte das Plugin; es sollte unter `config_backups/<timestamp>/` gesichert werden.
 - Prüfe die Server-Logs auf Hinweise zu erstellten Backups und Ersetzungen.

3. Schematic-Save-Verhalten prüfen (Admin):
 - Verwende `/village schematic save testname` mehrmals; im `schematics/`-Ordner sollten `testname_001.schem`, `testname_002.schem`, ... entstehen.

4. Upgrade-Benachrichtigung:
 - Kaufe ein Upgrade im Spiel; der Käufer und alle online-Mitglieder erhalten eine Chat-Nachricht.

5. End-to-End-Check (empfohlen auf Testserver):
 - Platziere Gebäude, weise Villager zu, führe Pfad-Aufzeichnung aus und teste Upgrades. Achte auf Chatmeldungen und Server-Logs.
