# Village Plugin – Testdokumentation

## Voraussetzungen

### Build

```bash
# Java 21 aktivieren (SDKMAN)
sdk use java 21.0.11-tem
# oder manuell:
export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.11-zulu
export PATH=$JAVA_HOME/bin:$PATH

# Build (ohne Tests)
mvn -DskipTests clean package

# Build mit Tests
mvn -DskipTests=false test
```

### Server-Setup

1. JAR aus `target/village-1.0.0.jar` in den `plugins`-Ordner kopieren
2. Folgende Plugins aktiviert: **Vault**, **WorldEdit**, **WorldGuard**, **Citizens** (optional), **ProtocolLib**
3. Paper/Spigot 1.21

---

## Unit-Tests (JUnit + Mockito)

### `BuildingConfigLoaderTest`

- Lade `resources/config/buildings.yml`
- Prüfe, dass Upgrade-Tiers mit `achievement` und `commands` korrekt in `BuildingDefinition`-Objekte gemappt werden
- **Erwartung:** `getUpgradeTier(2).getChange("achievement") != null` und `getChange("commands")` ist `List<String>`
- Prüfe, dass `dorfzentrum` mit `show_in_menu: false` geladen wird und `isShowInMenu()` == false zurückgibt
- Prüfe, dass `dorfzentrum` Upgrades für tier_2, tier_3 und small_storage enthält und `getMaxUpgradeTier()` == 3 liefert

### `BlockCheckValidatorTest`

- Erzeuge eine Testwelt/Mock-World, setze definierte Blöcke im Radius
- Prüfe `validate()` für: `required_blocks`, `required_block_percentage`, `hollow_structure`
- **Erwartung:** `ok()` wenn Bedingungen erfüllt, sonst `fail(...)` mit sinnvoller Meldung

### `BuildingServiceUpgradeTest`

- **Mock/Stub:** `EconomyService`, `CurrencyService`, `VillageManager`, `WorldEditHook`
- **Ablauf:** Erstelle `Village` + `VillageBuilding` (level 1), rufe `upgradeBuilding(player, village, id)` auf
- **Prüfe:**
  - Geldabbuchung (`EconomyService.withdraw`) wird aufgerufen
  - Gebäude-Level erhöht sich auf 2
  - `executeUpgradeTierEffects` löst Achievement-Nachricht (`MessageUtil.send`) und Kommandos (`Bukkit.dispatchCommand`) aus
  - Effekte werden auch ausgeführt wenn `refreshBuildingProtectionAndMarker` eine Exception wirft

### `DorfzentrumAutoRegistrationTest` *(neu)*

- **Mock/Stub:** `VillageDatabaseManager`, `WorldGuardHook`, `CurrencyService`
- **Ablauf:** Rufe `VillageManager.createVillage(player, name, bellLocation)` auf
- **Prüfe:**
  - Rückgegebenes `Village`-Objekt enthält genau ein `VillageBuilding` mit `typeKey == "dorfzentrum"`
  - Dieses Building hat `isCompleted() == true`
  - `getOwnerId()` entspricht der UUID des Gründers
  - `getLevel()` == 1
  - `getDirection()` == "N"

### `DorfzentrumSignTest` *(neu)*

- **Mock/Stub:** World (Bukkit), Block-Nachbarn der Bell-Location als passierbar
- **Ablauf:** Rufe `buildingService.initVillageCenterSign(village, centerBuilding)` auf
- **Prüfe:**
  - `centerBuilding.getSignLocation()` ist nicht null nach dem Aufruf
  - Die Sign-Location ist ein orthogonaler Nachbar der Bell-Location (kein Diagonal, gleiche Y-Ebene oder Y+1)
  - `villageManager.saveVillage(village)` wurde aufgerufen

### `DorfzentrumRemoveGuardTest` *(neu)*

- **Ablauf:** Erstelle `VillageBuilding` mit `typeKey == "dorfzentrum"`, rufe `removeCompletedBuilding(player, village, id)` auf
- **Erwartung:** Gibt `false` zurück; `village.getBuildings()` enthält das Dorfzentrum weiterhin; Fehlermeldung wurde an den Spieler gesendet

### `DorfzentrumMigrationTest` *(neu)*

- **Ablauf:**
  1. Erstelle ein `Village`-Objekt ohne Dorfzentrum-Building (simuliert Altdaten)
  2. Rufe `VillagePlugin.migrateMissingVillageCenters()` auf (via Reflection oder direkte Instanz)
- **Prüfe:**
  - `village.getBuildings()` enthält danach genau ein `dorfzentrum`-Building
  - Das Building ist `completed == true`
  - `initVillageCenterSign` wurde für das neue Building aufgerufen

### `BuildingMenuConfigTest`

- Lade `config/buildings.yml`, prüfe dass `menu_categories` existiert und Reihenfolge sowie Kategorie-Zuordnung stimmt
- **Erwartung:** GUI-Menu-Builder ordnet Kategorien in konfigurierter Reihenfolge

### `VillageCenterMenuTest` *(neu)*

- **Prüfe:**
  - `MenuBuilder.listBuildable(village)` enthält keinen Eintrag für `dorfzentrum`
  - `openBuildingCategoryContentGui` für die Dorfzentrum-Kategorie enthält einen leuchtenden Info-Eintrag wenn das Dorf ein Dorfzentrum-Building hat
  - Derselbe Aufruf liefert keinen Eintrag wenn `village.getBuildings()` kein Dorfzentrum enthält

### `SmallStorageUpgradeTest`

- Prüfe, dass `small_storage` nicht als eigenständiges Gebäude im Bau-Menü auftaucht
- **Erwartung:** Upgrade erscheint unter `dorfzentrum.upgrades`; kein separater Flächenplatzierungs-Flow

### `SchematicOptionalTest`

- Lade Gebäudedefinitionen mit `requires_schematic: false` und verifiziere, dass kein Fehler erzeugt wird wenn die Schematic fehlt
- **Erwartung:** Menü erlaubt Auswahl und startet Block-Check-/Placement-Flow entsprechend `validation_mode`

---

## Ingame-Integrationstests

### 1) Dorfgründung – Dorfzentrum-Registrierung *(neu)*

**Schritte:**
1. Glocke aufstellen, Gründungs-GUI durchlaufen, Dorf gründen
2. `/village building list` ausführen

**Erwartet:**
- Dorfzentrum erscheint in der Liste mit Level 1 und Status „fertig"
- Ein Schild erscheint an einem der vier orthogonalen Nachbarblöcke der Glocke (gleiche Y-Ebene, falls frei)
- Rechtsklick auf das Schild öffnet die Verwaltungs-GUI

### 2) Dorfzentrum-Schild und Verwaltungs-GUI *(neu)*

**Schritte:**
1. Schild rechtsklicken
2. Schild-Editor öffnen, Template bearbeiten, Platzhalter `%building_name%` und `%level%` nutzen
3. Schild ausblenden (im GUI), danach wieder einblenden

**Erwartet:**
- GUI zeigt korrektes Level und Gebäudenamen
- Schild-Text aktualisiert sich entsprechend
- Kein „Abreißen"-Button im Detail-GUI
- Kein „Neues Gebäude bauen"-Button im Manage-Typ-GUI

### 3) Dorfzentrum-Kategorie im Gebäude-Menü *(neu)*

**Schritte:**
1. Gebäude-Menü öffnen → Kategorie „Dorfzentrum" anklicken

**Erwartet:**
- Kategorie zeigt das Dorfzentrum als leuchtenden Eintrag mit „Level 1 / 3"
- Klick darauf öffnet die Manage-Typ-GUI (nicht den Bau-Flow)
- Keine „Neues Gebäude bauen"-Schaltfläche sichtbar

### 4) Dorfzentrum-Upgrade *(neu)*

**Schritte:**
1. Dorf auf Level 3 bringen (`/village admin setlevel <dorf> 3`)
2. Dorfzentrum-GUI öffnen → Upgrade auf tier_2 durchführen

**Erwartet:**
- Level steigt auf 2
- Achievement-Nachricht im Chat (falls konfiguriert)
- Konfig-Commands werden ausgeführt (Platzhalter `%player%`, `%village%`, `%level%` werden ersetzt)
- Schild-Text aktualisiert sich auf Level 2

### 5) Dorfzentrum nicht abreißbar *(neu)*

**Schritte:**
1. Versuche `/village building remove <dorfzentrum-id>` auszuführen
2. Versuche über das Gebäude-Detail-GUI den Abriss auszulösen

**Erwartet:**
- Beides wird mit Fehlermeldung „Das Dorfzentrum ist das Fundament des Dorfes und kann nicht abgerissen werden" abgelehnt
- Kein TNT-Abreißen-Button im Detail-GUI sichtbar

### 6) Migration bestehender Dörfer *(neu)*

**Schritte:**
1. Dorf-YAML manuell anpassen: `buildings`-Liste leeren (simuliert Altstand)
2. Server (neu)starten

**Erwartet:**
- Server-Log enthält: `[Village] Dorfzentrum für Dorf '<name>' nachregistriert.`
- Dorfzentrum erscheint danach in `/village building list`
- Schild wurde automatisch platziert

### 7) Villager-Zuweisung über GUI → Workstation

**Schritte:**
1. Dorf betreten, Gebäude mit Workstation-Blöcken platzieren
2. Baumenü öffnen → „Villager zuweisen" → Villager auswählen
3. Workstation-Block des Gebäudes anklicken

**Erwartet:**
- Chat-Bestätigung: `<Villager> arbeitet jetzt als <Beruf> in <Gebäude>`
- Villager erhält zugewiesenen Job

### 8) Block-Check-Gebäude und Workstation

**Schritte:**
1. Block-Check-Gebäude (z.B. Dorfbrunnen) auswählen
2. Erforderliche Workstation-Blöcke im Bereich platzieren
3. `/village buildconfirm ja` ausführen

**Erwartet:**
- Bau wird nur abgeschlossen wenn konfigurierte Blöcke vorhanden sind
- Fehlende Blöcke verhindern Fertigstellung mit konkreter Meldung

### 9) Path-Upgrade-Effekte

**Schritte:**
1. Pfad-Gebäude via Walk-Session registrieren (`/village path start` ... `/village path done`)
2. Upgrade auf das Pfad-Gebäude durchführen
3. Pfadzone betreten und verlassen

**Erwartet:**
- Speed-Bonus (Potion `SPEED`) steigt pro Level um +1 (bis `max_amplifier`)
- Nachlauf-Dauer erhöht sich um konfigurierte Ticks pro Level
- Zone-Breite wächst wenn `widthUpgradeable: true`

### 10) Permissions-Check

**Schritte:**
1. Spieler ohne `village.admin` und ohne Manage-Permission testen
2. Versuche Villager zuzuweisen, Upgrades durchzuführen und Schild zu bearbeiten

**Erwartet:**
- Fehlende Berechtigungen werden mit passender Chat-Meldung abgelehnt

---

## Automatisierte Verifikation (Checkliste)

```bash
# 1. Unit-Tests ausführen
mvn -DskipTests=false test

# 2. Config-Migration prüfen
#    → config-version: 0 in buildings.yml setzen, Server starten
#    → Backup in config_backups/<timestamp>/ erwartet

# 3. Schematic-Save-Nummerierung
#    → /village schematic save testname (mehrfach)
#    → Dateien testname_001.schem, testname_002.schem, ... erwartet

# 4. Upgrade-Benachrichtigung
#    → Upgrade kaufen → Käufer + alle Online-Mitglieder erhalten Chat-Nachricht

# 5. End-to-End: Gründen → Dorfzentrum → Upgrade → Schild → Kategorie-GUI
```

---

## Edge Cases

| Szenario | Erwartetes Verhalten |
|---|---|
| Alle Nachbarblöcke der Glocke beim Gründen belegt | Sign wird bei Y+1 nördlich der Glocke gesetzt (Fallback) |
| Dorfzentrum-Upgrade schlägt wegen fehlender Ressourcen fehl | Geld wird zurückerstattet, Level bleibt unverändert |
| `refreshBuildingProtectionAndMarker` wirft Exception beim Upgrade | Upgrade-Effekte (Achievement, Commands) werden trotzdem ausgeführt |
| Migration mit ungültiger/ungeladener World | Warnung im Server-Log, Migration für dieses Dorf übersprungen |
| `commands`-Feld als String statt Liste in der Config | Wird als Einzelbefehl behandelt, kein Fehler |
| Zweiter Gründungsversuch für ein Dorf das bereits ein Dorfzentrum hat | Kein doppelter Dorfzentrum-Eintrag; Migration überspringt vorhandene |
| Upgrade auf tier mit `achievement` aber ohne `commands` | Nur Achievement-Nachricht, kein Command-Fehler |
| Abbruch des Dorfzentrum-Upgrades nach globalem Geldabzug | Globale Kosten werden zurückerstattet (bestehender Rollback-Code) |

---

## Fehlerbehebung

**Dorfzentrum-Schild erscheint nicht:**
- Server-Log auf Exceptions prüfen
- Prüfen ob alle 4 Nachbarblöcke der Glocke besetzt sind → Fallback-Position Y+1 kontrollieren

**Dorfzentrum erscheint nicht in Kategorie-GUI:**
- Prüfen ob das Dorf wirklich ein `dorfzentrum`-Building in `village.getBuildings()` hat
- Migration-Log nach `[Village] Dorfzentrum für Dorf` durchsuchen

**Upgrade-Effekte werden nicht ausgeführt:**
- Prüfen ob `tier_2` und `tier_3` korrekt als Integer-Keys in `BuildingDefinition.upgrades` landen
- `small_storage` Schlüssel enthält keine Ziffern → mappt auf Tier 2 (Config-Loader Verhalten)

**Pfad-Zone nach Upgrade nicht aktualisiert:**
- `server.log` auf Exceptions prüfen
- `BuildingService.upgradeBuilding` ruft `PathService.refreshZoneForBuilding(...)` auf

**Villager-Zuweisung löst keinen Job aus:**
- `server.log` auf Exceptions prüfen
- `GuiClickListener.pendingJobAssignments` korrekt gesetzt?
- `config/buildings.yml` → `professions`-Mapping prüfen

---

## Erweiterte System-Tests

> Abgedeckte Systeme: Villager, Quests, Skills, Währung, Handel, Rollen, Licht, Bereichsschutz, Produktions-System
> Server-Version: Paper 1.21 · Soft-Dependencies: Vault, WorldEdit, WorldGuard, Citizens, ProtocolLib

### Empfohlene Test-Reihenfolge

```
1.  Dorf gründen                (Bell + Brunnen)
2.  Dorfzentrum prüfen          (Schild, Kategorie-GUI, kein Abreißen)
3.  Grenze setzen               (Walk oder Koordinaten)
4.  Mitglied einladen           (join/invite)
5.  Admin-Geld geben            (/village admin money add)
6.  Upgrade kaufen              (border-expansion)
7.  Gebäude platzieren          (house, benötigt WorldEdit)
8.  Gebäude upgraden            (/village building upgrade 1)
9.  Villager rekrutieren        (/villager recruit, benötigt Citizens)
10. Villager-Quest starten      (/villager quest list / accept / complete)
11. Handel testen               (/trade local / player)
12. Währung senden              (/sendmoney)
13. Licht-System prüfen         (Grenze verlassen, benötigt ProtocolLib)
14. Bereichsschutz prüfen       (Nicht-Mitglied versucht zu bauen)
```

---

### Villager-System

**Rekrutierung** (benötigt Citizens):
```
/villager recruit <Name>   → NPC spawnt an Spieler-Position
```

**Villager-Interaktion (GUI):** Rechtsklick auf NPC öffnet Menü mit Umbenennen, Job wechseln, Inventar, Skilltree, Handel, Heilen, Verbannen.

**Villager-States (automatisch):**

| State | Auslöser |
|---|---|
| IDLE | Standard |
| WORKING | Automatisch nach 30–60 s |
| EATING | Hunger < 20 |
| SLEEPING | Happiness < 20 |
| FLEEING | Feindliche Mobs in 16 Blöcken |

**Test:** Hunger sinken lassen → Villager wechselt nach EATING-State und greift auf Dorftruhe zu.

**Villager-Jobs:**

| Job | Arbeitsgebäude |
|---|---|
| FARMER | farm |
| MERCHANT | marketplace |
| GUARD | watchtower |
| LIBRARIAN | library |
| PRIEST | tavern |
| LABORER | storage |

---

### Quest-System

```
/villager quest list              → Verfügbare und aktive Quests
/villager quest accept <ID>       → Quest annehmen
/villager quest complete <ID>     → Quest abschließen
```

**Tägliche Quests:** Können 1× pro Tag abgeschlossen werden. Test: `trade-quest` zweimal täglich → zweiter Versuch abgelehnt.

**Test-Sequenz:**
1. `/villager quest accept starter-quest-1`
2. 10× OAK_LOG sammeln
3. `/villager quest complete starter-quest-1`
4. Belohnungen: Punkte + Geld (Vault) + Items ins Inventar

---

### Skill-System

```
/villager skill list <Nr>              → Skills eines Villagers
/villager skill upgrade <Nr> <Skill>   → Skill upgraden (kostet Geld)
```

SkillTreeGui zeigt alle Skills des aktuellen Jobs. Klick auf Skill erhöht Level, Kosten werden abgezogen.

---

### Währungs-System (Dual-Ebene)

| Ebene | Symbol | Scope | Beschreibung |
|---|---|---|---|
| Global | ⊕ | Server-weit | Via Vault |
| Lokal | Ɖ | Dorf-intern | Via OpenEco (optional) |

```
/balance                              → Eigenes globales Guthaben
/village balance                      → Dorf-Guthaben (lokal + global)
/sendmoney <Spieler> <Betrag>         → Globales Geld senden
/village sendmoney <Spieler> <Betrag> [global|lokal]
```

**Währungskonversion:** 100 lokale Taler → 80 Goldmünzen (20 % Gebühr, konfigurierbar).

---

### Handels-System

```
/trade balance        → Aktuelles Guthaben
/trade local          → Lokaler Villager-Handel (Dorf-Mitglieder)
/trade external       → Externer Handel (globale Währung)
/trade player <Name>  → Spieler-zu-Spieler-Handel
/trade shop           → Marktplatz öffnen
/trade history        → Handelshistorie
```

**Spieler-Handel Test:**
1. Spieler A: `/trade player SpielerB`
2. Beide erhalten Trade-GUI
3. Beide legen Items/Währung ein und klicken „Bereit"
4. Items werden getauscht, Transaktion in History gespeichert

---

### Admin Money

```
/village admin money add <Ziel> global <Betrag>
/village admin money remove <Ziel> global <Betrag>
# Ziel: Spielername | "all" | "village:<Dorfname>"
```

Beispiel: `/village admin money add all global 50` gibt allen Spielern 50 Goldmünzen.

---

### Bereichsschutz

Innerhalb der Dorfgrenzen (WorldGuard aktiv):
- Nicht-Mitglieder können nicht bauen/abbauen
- Dorfglocke und Block darüber sind unzerstörbar

**Test:** Nicht-Mitglied betritt Dorf → Block setzen → abgebrochen mit Fehlermeldung → Spieler dem Dorf hinzufügen → Bauen möglich.

---

### Produktionssystem

Gebäude mit `recipes` produzieren automatisch alle ~5 Sekunden wenn ein Villager zugewiesen ist.

**Test:**
1. `farm`-Gebäude registrieren, FARMER-Villager zuweisen
2. WHEAT_SEEDS in öffentliche Dorftruhe legen
3. Nach ~180 Sekunden: WHEAT erscheint in Gebäude-Truhe

---

### Licht-System

Spieler außerhalb der Dorfgrenze sehen reduziertes Licht (via ProtocolLib):

| Entfernung | Max. Lichtlevel |
|---|---|
| 0 (Grenze) | 15 |
| 10 Blöcke außerhalb | 14 |
| 50 Blöcke außerhalb | 10 |
| 100 Blöcke außerhalb | 5 |
| 110+ Blöcke | 3 |

**Test:** Grenze überschreiten → Licht dimmt schrittweise. `/village reload` nach Konfig-Änderung.

---

### Bekannte Einschränkungen (Tester-Hinweise)

| Einschränkung | Details |
|---|---|
| Grenzziehung | Diagonalbewegungen werden abgebrochen – immer in Himmelsrichtungen laufen |
| Schematic-Validierung | Fehlende `.schem`-Datei bricht Platzierung ab, aber kein Server-Crash |
| Villager-KI | Vollständiges Wegfinde-Verhalten hängt von Citizens ab |
| Truhen-GUI | Virtuelle Truhen synchronisieren beim Schließen in `chest-data.yml` |
| Produktionssystem | Output wird synchron im Bukkit-Haupt-Thread eingelagert |

---

### Job-Ausbau: Produktion, Aufträge, Lager (Ausbauplan)

Vervollständigt das zuvor begonnene, aber unfertige Job-/Auftragssystem
(`VILLAGER_CONTRACTS` existierte als Menü-Typ ohne Implementierung).

**Produktions-Menü:**
1. Berufsmenu öffnen → `Produktion` → Zielprodukt anklicken (z.B. nur `WHEAT` statt allem).
2. Erwarten: Nur das gewählte Produkt wird produziert (Lager nach einem Zyklus prüfen).
3. `Produktion pausieren` anklicken → Lager bleibt nach mehreren Minuten unverändert.
4. `Produktion fortsetzen` anklicken → Produktion läuft wieder normal weiter.
5. Server neu starten → Pause-Status und Zielprodukt bleiben erhalten.

**Aufträge-Menü:**
1. Mindestens zwei Dorfbewohner mit passenden Berufen anstellen (z.B. Tischler + Holzfäller).
2. Holzfäller produzieren lassen, bis er Logs im Lager hat.
3. Beim Tischler `Berufsmenu → Aufträge → Neuer Auftrag` anklicken.
4. Erwarten: Auftrag erscheint in der Liste mit Status **Offen** (gelb) - er wird noch **nicht**
   ausgeliefert, solange er offen ist.
5. Auftrag anklicken (Linksklick) → Status wechselt zu **Aktiv** (blau, mit Glanzeffekt).
6. Warten, bis der Server-Tick die Auftragsverarbeitung ausführt → Status wechselt zu
   **Abgeschlossen**, Logs sind vom Holzfäller zum Tischler transferiert worden.
7. Einen weiteren offenen Auftrag per Rechtsklick abbrechen → Status **Abgebrochen**, keine Lieferung.
8. Job-Lager des Empfängers künstlich vollstopfen (alle Slot-Typen belegt) → aktiven Auftrag
   bestehen lassen → Lieferung bleibt aus, bis wieder Platz frei ist (kein Datenverlust).

**Kurier-Logistik:**
1. Einen Dorfbewohner mit großem Materialüberschuss (≥ 8 Stück) und einen Kurier anstellen.
2. Beim Kurier `Aufträge → Neuer Auftrag` anklicken.
3. Erwarten: Logistikauftrag mit dem Überschuss-Material wird vorgeschlagen.
4. Annehmen und abwarten → Ware landet in einer öffentlichen, dorfbewohner-zugänglichen
   Gebäude-Truhe, **nicht** im persönlichen Lager des Kuriers; Kurier erhält stattdessen
   eine kleine Geld- und XP-Belohnung.

**Neue Produktionsgüter (zuvor `produces: []`):**

| Beruf | Produziert jetzt |
|---|---|
| Tischler | `OAK_PLANKS`, `OAK_STAIRS`, `STICK` |
| Steinmetz | `STONE_BRICKS`, `BRICKS`, `CHISELED_STONE_BRICKS` |
| Imker | `HONEYCOMB`, `HONEY_BOTTLE` |
| Brauer | `GLASS_BOTTLE`, `SUGAR`, `PUMPKIN_PIE` |
| Fischer | `COD`, `SALMON`, `KELP` |
| Jäger | `LEATHER`, `ARROW`, `RABBIT_HIDE` |

**Test:** Jeden der obigen Berufe zuweisen, Workstation zuweisen, ein Produktionsintervall
abwarten → Lager (`Berufsmenu → Lager`) zeigt die neuen Items statt leer zu bleiben.
