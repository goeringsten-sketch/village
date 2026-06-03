# Village

Ein Minecraft-Plugin für umfangreiche Dorfmechaniken:

- Dorfgründung mit eigener Grenze und Verwaltung
- Mitgliederverwaltung, Einladungen, Beitritt und Austritt
- Dorf-Upgrades, Gebäude und Dorfbewohner
- **Dorf-Levelup-System mit Voraussetzungen und Partikeleffekten**
- Lichtregelung pro Gebiet mit konfigurierbaren Helligkeitsstufen
- Dorf-Relationen: Freundschaft, Handel, Krieg und Durchgangssperre
- GUI-gestützte Steuerung für Dörfer und Beziehungen
- Gebäude ohne Schematic (Pfade, Brunnen, Brücken, Bezirke, etc.)

---

## Installation

1. Installiere Java 21 über SDKMAN und nutze es für den Build:
   ```bash
   curl -s "https://get.sdkman.io" | bash
   source "$HOME/.sdkman/bin/sdkman-init.sh"
   sdk install java 21.0.11-jbr
   sdk use java 21.0.11-jbr
   ```
2. Baue das Plugin mit Maven:
   ```bash
   mvn clean package
   ```
   oder als Einzeiler:
   ```bash
   sdk use java 21.0.11-jbr && mvn clean package
   ```
3. Kopiere die erzeugte JAR-Datei in den `plugins`-Ordner deines Minecraft-Servers.
4. Starte den Server neu.
 
## Entwicklerhinweise

Wenn du am Plugin entwickelst oder es lokal kompilieren möchtest, verwende SDKMAN zur Verwaltung der Java-Versionen (unterstützt z.B. Java 21 und Java 24). Empfohlen ist, vor dem Build die gewünschte JDK-Version zu aktivieren.

Beispielablauf:

```bash
# SDKMAN installieren (falls noch nicht installiert)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Liste verfügbare JDKs und wähle eine Version aus (z. B. Java 21 oder Java 24)
sdk list java

# Installieren/auswählen (Beispiele; wähle die passende ID aus der Liste)
sdk install java 21.0.11-jbr
sdk use java 21.0.11-jbr
# oder für Java 24 (ID aus `sdk list java` wählen):
sdk install java 24.<version>-open
sdk use java 24.<version>-open

# Projekt kompilieren (schnell prüfen, ohne Tests)
mvn -DskipTests=true clean compile

# Vollständiges Paket
mvn clean package
```

Hinweise:
- Nutze `sdk list java`, um die exakte ID für die gewünschte JDK-Version zu finden.
- Verwende `mvn -DskipTests=true clean compile` für schnelle Kompilierprüfungen während der Entwicklung.
- CI/Release-Builds sollten `mvn clean package` verwenden und Tests ausführen.

Ich habe lokal eine Kompilierung via Maven ausgeführt, um sicherzustellen, dass der Code kompiliert.

Bitte führe bei Änderungen vor dem Commit lokal `mvn -DskipTests=true clean compile` aus, um sicherzustellen, dass keine Kompilationsfehler eingeführt werden.

**Letzte erfolgreiche lokale Kompilierung:** 2026-06-02T10:23:24Z (BUILD SUCCESS)

---

## Berechtigungen (Permissions)

Für Dorf-Verwaltung, Gebäude-Interaktion und spezielle Features kannst du folgende Permissions verwenden (für Permission-Management-Systeme wie LuckPerms):

| Permission | Beschreibung |
|---|---|
| `village.admin` | Vollständiger administrativer Zugriff (umgeht alle Einschränkungen) |
| `village.list.all` | Sehe alle Dörfer in der `/village list` Anzeige (inkl. unbekannter Dörfer) |
| `village.list` | Basis-Permission für `/village list` Kommando |
| `village.tp.others` | Teleportiere Spieler zu Dörfern mit `/village tp <dorf> <spieler>` (oder klickbar in Liste) |
| `village.border.shownames` | Zeige Grenzennamen bei Betreten/Verlassen einer Grenze an |
| `village.building.*` | Interagiere mit allen Workstations (Ofenmodus, etc.) |
| `village.bypass` | Umgehe bestimmte Einschränkungen (für Admin-Features) |
| `village.villager.show.temp` | Zeige Dorfbewohner temporär mit Glowing-Effekt an (ca. 15 Sekunden, konfigurierbar) |
| `village.villager.show.on` | Zeige Dorfbewohner dauerhaft mit Glowing-Effekt an (bis deaktiviert) |
| `village.admin.trade` | Admin-Kommandos für Handels-Management |

Hinweise:
- `village.admin` gewährt unbegrenzten Zugriff; alle einzelnen Permissions sind optional.
- `village.list` und `village.list.all` kontrollieren Zugriff auf das `/village list` Kommando.
- `village.tp.others` erlaubt Teleportation zu Dörfern und ist erforderlich, um in `/village list` auf Village-Namen zu klicken.
- `village.villager.show.temp` und `village.villager.show.on` bestimmen den Zugriff auf die Dorfbewohner-Glowing-Anzeige im Dorfbewohner-Menü (Slot 48).
- Einzelne Dorf-Rollen (Gründer, Manager, etc.) werden über die Dorf-interne Verwaltung zugewiesen und erfordern diese Permissions nicht.

### Gebäude- und Upgrade-Permissions

Gebäude und ihre Upgrades werden über ein granulares Permission-System gesteuert, konfigurierbar in `config/buildings.yml`. Die Struktur folgt diesem Schema:

```
village.building.<kategorie>.<gebäudetyp>
village.building.<kategorie>.<gebäudetyp>.upgrade.<upgrade-name>
```

**Beispiele aus der Konfiguration:**

| Permission | Beschreibung |
|---|---|
| `village.building.general.dorfzentrum` | Platziere/verwalte das Dorfzentrum |
| `village.building.general.dorfzentrum.upgrade.2` | Upgrade zu Erweitertes Dorfzentrum (Tier 2) |
| `village.building.general.dorfzentrum.upgrade.3` | Upgrade zu Prächtiges Dorfzentrum (Tier 3) |
| `village.building.general.dorfzentrum.upgrade.small_storage` | Unlock Kleines Lager (Upgrade) |
| `village.building.general.dorfbrunnen` | Platziere/verwalte den Dorfbrunnen |
| `village.building.wohnen.baracke.upgrade.2` | Upgrade zur Baracke Tier 2 |

**Konfiguration in `config/buildings.yml`:**

Jedes Gebäude hat eine `permission`-Feld und optionale `upgrades` mit individuellen Permissions:

```yaml
buildings:
  special:
    dorfzentrum:
      name: "Dorfzentrum"
      permission: "village.building.general.dorfzentrum"
      upgrades:
        tier_2:
          name: "Erweitertes Dorfzentrum"
          permission: "village.building.general.dorfzentrum.upgrade.2"
          requires_village_level: 3
          build_cost:
            items: { STONE_BRICKS: 64, OAK_LOG: 32 }
            money: 100
        tier_3:
          name: "Prächtiges Dorfzentrum"
          permission: "village.building.general.dorfzentrum.upgrade.3"
          requires_village_level: 6
```

**Verwendung mit LuckPerms:**

```
/luckperms user <spieler> permission set village.building.general.dorfzentrum true
/luckperms user <spieler> permission set village.building.general.dorfzentrum.upgrade.small_storage true
```

**Automatische Freischaltung:**

Wenn ein Upgrade in der Config `achievement`-Benachrichtigungen oder `commands` definiert, werden diese beim erfolgreichen Upgrade ausgelöst. Die erforderliche `requires_village_level` wird automatisch überprüft.

**Optionale Abhängigkeiten** (für erweiterte Funktionen):
- Vault — Wirtschafts-Integration
- WorldEdit — Gebäude-Schematic-Verwaltung
- WorldGuard — Region-Schutz
- Citizens — NPC-Integration für Dorfbewohner (optional)
- ProtocolLib — Paket-Manipulation für GUI-Effekte

---

## Konfiguration

Standardkonfigurationen liegen in `resources/config.yml` und in `resources/config/village.yml`.

| Datei | Zweck |
|---|---|
| `config.yml` | Globale Einstellungen: Grenzen, Upgrades, Wirtschaft, Gebäude-Platzierungs-Timeout |
| `config/village.yml` | Levelup-System, Partikeleffekte |
| `config/light-limits.yml` | Lichtbegrenzung, Weltfilter, Aktualisierungsintervalle |
| `config/buildings.yml` | Gebäudetypen, Workstations, Kategorien, Timeouts |

### Gebäude-Platzierungs-Timeout

In `config.yml`:
```yaml
placement:
  timeout-seconds: 60   # Sekunden bis automatischer Abbruch der Platzierung
```

### Gebäude-Vorschau-Konfiguration

In `config/village.yml` unter `territory` lassen sich Vorschaublöcke für Rand- und Gebäudepreview anpassen:
```yaml
territory:
  preview-block: REDSTONE_ORE
  preview-selected-block: EMERALD_ORE
  building-preview-border-block: GOLD_ORE
  building-border-block: LAPIS_ORE
```

### Debug-Actionbar beim Betreten neuer Border-Flächen

Zum Aktivieren in `config/village.yml`:
```yaml
territory:
  debug-border-entry: true
```

Wenn ein Spieler eine Baustelle platzieren soll und innerhalb dieser Zeit keinen Block anklickt, wird die Platzierung automatisch abgebrochen. Der Timer wird zurückgesetzt, sobald das Gebäude erneut aus dem Menü heraus platziert wird.

### Levelup-System konfigurieren

In `config/village.yml` unter `levels`:

```yaml
levels:
  max-level: 50
  points-per-level:
    - level: 1
      points: 100
    - level: 5
      points: 250
    # ... weitere Level

  prerequisites:
    level_1:
      particles: "flame"
    level_5:
      min-villagers: 5
      particles: "flame flame_small"
    level_10:
      buildings:
        - marketplace
      min-villagers: 10
      particles: "flame enchant"

  particle-config:
    enabled: true
    check-interval-ticks: 20
    height-above-bell: 3
    radius-around-bell: 3
```

---

## Befehle

### Spieler-Befehle

| Befehl | Beschreibung |
|---|---|
| `/village` | Öffnet das Dorfmenü oder zeigt Hilfe an |
| `/village info [Name]` | Zeigt Informationen zu einem Dorf |
| `/village list` | Listet alle Dörfer auf |
| `/village join <Name>` | Sendet eine Beitrittsanfrage |
| `/village joinrequest accept\|deny <VillageUUID> <PlayerUUID>` | Bearbeitet eine Beitrittsanfrage |
| `/village leave` | Verlässt dein aktuelles Dorf |
| `/village invite <Spieler>` | Lädt einen Spieler ein |
| `/village kick <Spieler>` | Entfernt einen Spieler |
| `/village promote <Spieler> <Rolle>` | Befördert einen Spieler |
| `/village memberremoveconfirm ja\|nein <VillageUUID> <MemberUUID>` | Bestätigt das Entfernen eines Mitglieds |
| `/village border` | Öffnet die Grenzauswahl |
| `/village manage border info` | Zeigt Grenzflächen-Infos inkl. ID der Fläche |
| `/village manage border delete [ID]` | Löscht eine Grenzfläche (nicht die Default-Fläche) |
| `/village manage border fusion <ID1> <ID2>` | Vereint zwei angrenzende Grenzflächen |
| `/village preview` | Zeigt die Dorfgrenzen an |
| `/village tp <village_id|name> [<player>]` | Teleportiert dich oder einen Spieler zum Dorf |
| `/village delete` | Löscht dein Dorf |
| `/village rename <Name>` | Ändert den Dorfnamen |
| `/village menu` | Öffnet das Haupt-Dorfmenü |
| `/village signshowconfirm ja\|nein` | Bestätigt das Einblenden eines verschobenen Schildes |
| `/village building start <Nr>` | Startet den Baustellenbau für ein Gebäude |
| `/village building cancel <Nr>` | Bricht eine aktive Konstruktion ab |
| `/village cancel` | Bricht eine laufende Gebäude-Platzierung ab |

### Admin-Befehle

| Befehl | Beschreibung |
|---|---|
| `/village reload` | Lädt die Konfiguration neu (**auch von der Konsole ausführbar**) |
| `/village admin setlevel <Dorf> <Level>` | Setzt das Dorf-Level |
| `/village admin addpoints <Dorf> <Punkte>` | Addiert Dorfpunkte |
| `/village admin delete <Dorf>` | Löscht ein Dorf |
| `/village admin saveall` | Speichert alle Dörfer |
| `/village schematic tool` | Gibt ein Auswahl-Werkzeug für Schematic-Positionen |
| `/village schematic pos1` | Setzt Position 1 der Schematic |
| `/village schematic pos2` | Setzt Position 2 der Schematic |
| `/village schematic origin` | Setzt den Ursprung der Schematic |
| `/village schematic save <name>` | Speichert die aktuelle Auswahl als `.schem`-Datei |
| `/village schematic register <name> [...]` | Registriert eine Schematic in `config/buildings.yml` || `/village schematic list <building_type>` | Listet verfügbare Schematic-Varianten für einen Gebäudetyp |
> Alle `schematic`-Befehle erfordern `village.admin`.

---

## Berechtigungen

| Permission | Beschreibung |
|---|---|
| `village.admin` | Voller Admin-Zugriff inkl. Konsolen-Reload |
| `village.create` | Erlaubt das Gründen eines Dorfes |
| `village.manage` | Erlaubt Dorfverwaltung (Upgrades, Gebäude usw.) |
| `village.bypass` | Umgeht Dorf-Einschränkungen |

---

## Gebäude-System

### Grundprinzip

Dörfer können vorgefertigte Gebäude bauen. Jedes Gebäude hat einen Typ (`typeKey`), eine Richtung (`direction`: N/S/E/W) und eine Position im Dorfgebiet.

**Gebäudetypen:**

| Typ | Beschreibung |
|---|---|
| Schematic-Gebäude | Werden aus einer `.schem`-Datei platziert |
| Block-Check-Gebäude | Keine Schematic – das Plugin prüft ob konfigurierte Blöcke vorhanden sind |
| Pfade | Werden durch Ablaufen erstellt, bilden einen Routing-Graphen |

### Platzierungsregeln

Beim Platzieren einer Baustelle gelten folgende Regeln – das Plugin gibt bei Verletzung eine genaue Fehlermeldung aus:

| Fehler | Bedeutung |
|---|---|
| `OUTSIDE_BORDER` | Das Gebäude liegt vollständig außerhalb der Dorfgrenzen |
| `ON_START_BORDER` | Das Gebäude überlappt den Dorfbrunnen-Bereich |
| `OVERLAPS_BUILDING` | Das Gebäude überschneidet ein bestehendes Gebäude oder den Dorfbrunnen |
| `PARTIAL_OUTSIDE_BORDER` | Das Gebäude liegt teilweise auf einer Dorfgrenzlinie |
| `BUILDING_TOO_FAR` | Das Gebäude liegt außerhalb des erlaubten Bauradius |
| `INSIDE_DEFAULT_BORDER` | Das Gebäude liegt nur in der Default-Grenzfläche, obwohl Erweiterungsflächen existieren |

> Gebäudegrenzen dürfen sich **nicht überschneiden**. Das gilt auch für den Dorfbrunnen (Startgebäude). Es kann immer nur einen Dorfbrunnen geben.

### Dorfbrunnen (Startgebäude)

Der Dorfbrunnen (`dorfbrunnen`) ist das erste Gebäude eines jeden Dorfes und wird automatisch bei der Dorfgründung registriert. Er definiert das Dorfzentrum. Kein weiteres Gebäude darf seinen Bereich überschneiden.

### Workstations zuweisen

Workstations werden **in `config/buildings.yml`** pro Gebäudetyp konfiguriert, nicht manuell per Befehl:

```yaml
categories:
  general:
    buildings:
      farm:
        workstation_blocks:
          workstation: FARMLAND
          workstation_compost: COMPOSTER
        area:
          shape: rectangle
          min_width: 5
          max_width: 10
```

Das Plugin erkennt beim Platzieren automatisch, ob die konfigurierten Blöcke innerhalb des Bereichs vorhanden sind. Der **primäre Workstation-Block** (`workstation:`) ist derjenige, den der Spieler beim Platzieren anklickt.

### Villager einem Gebäude zuweisen (Beruf)

1. Öffne das **Baumenü** des Gebäudes (Rechtsklick auf das Gebäude-Schild).
2. Klicke auf **„Villager zuweisen"** – es öffnet sich eine Liste der Dorfbewohner.
3. Wähle den gewünschten Dorfbewohner aus.
4. Klicke im Spiel auf einen **Arbeitsblock (Workstation)** des Gebäudes.
5. Der `JobAssignmentListener` verknüpft den Villager mit dem Block und setzt den Beruf automatisch anhand des Block-Typs.

Villager entnehmen Materialien aus konfigurierten Dorftruhen. Chat-Meldungen informieren über Materialmangel, Fortschritt und Fertigstellung.

### Gebäude-Schild

Jedes Gebäude hat ein Schild, das per Rechtsklick das Baumenü öffnet. Das Schild kann über das Gebäudemenü ausgeblendet und wieder eingeblendet werden. Beim Wiedereinblenden wird die gespeicherte Richtung (`direction`) korrekt gesetzt – sowohl bei Wandzeichen als auch bei Bodenzeichen.

---

## Grenzen und Glocken

### Dorfgründung

1. Brunnen mit Wasser und Glocke darüber bauen.
2. `/village border` für die Grenzauswahl ausführen.
3. Grenze per Koordinaten eingeben **oder** ablaufen.
4. Vorschau mit roten Blöcken bestätigen.

### Grenzauswahl per Koordinaten (Zwei-Punkte-Methode)

- Zwei Blöcke anklicken oder jeweils `x z` in den Chat eingeben.
- Das Plugin berechnet ein Rechteck aus den beiden Punkten.
- Bei ungültiger Auswahl wird eine **genaue Fehlermeldung** ausgegeben (z.B. ob die Grenze das Dorfzentrum einschließen würde, die Fläche zu groß ist oder keine Verbindung zum bestehenden Gebiet besteht).

### Grenzfunktionen

- Polygonale Grenzen möglich.
- Höhe: 64 Blöcke über/unter Glocke.
- Validierung: Mindestens 3×3 Quadrat an jeder Stelle platzierbar.
- Erweiterung durch Upgrades.
- Grenzflächen-IDs: Default-Fläche ist immer `0` (nicht einzeln löschbar), zusätzliche Flächen starten bei `1`.
- Gelöschte IDs werden wiederverwendet.
- **Grenzen löschen**: Warnung mit klickbaren Buttons (`[JA] [NEIN] [ABBRECHEN]`).
- **Grenzen vereinen**: Start über das Grenzen-Menü, 2+ Grenzflächen anklicken.
- **Diagonal-Lücken**: Kantenberechnung ergänzt automatisch orthogonale Verbindungsblöcke.

### WorldGuard-Integration

- Pro Grenzfläche wird eine WorldGuard-Region erzeugt (polygonal).
- Regionsnamen: `<dorfname>_<id>`.
- Owner werden nach WorldGuard gespiegelt.

### Dorfglocke (Schutz)

Die Dorfglocke ist das Zentrum des Dorfes und **vollständig geschützt**:
- Die **Glocke selbst** kann nicht abgebaut werden.
- Der **Block direkt über der Glocke** ist ebenfalls geschützt, damit die Glocke nicht durch indirektes Abbauen zerstört werden kann.

---

## Villager-System

### Citizens-Integration

Das Plugin unterstützt Citizens-NPCs als Dorfbewohner. Ohne Citizens werden Vanilla-Villager verwendet, die über eine `village-villager-id` PersistentData getrackt werden.

### Dorf auflösen – Villager-Verhalten

Wird ein Dorf gelöscht, werden **alle Dorfbewohner vollständig zu Vanilla-Villagern konvertiert**:
- Citizens-NPCs werden entfernt.
- Es wird ein sauberer Vanilla-Villager ohne Custom-Namen und ohne Plugin-Tags gespawnt.
- Bereits als Vanilla-Entitäten getrackten Villager verlieren ihren Namen (`customName = null`) und den `village-villager-id`-Tag.

---

## Rollenmodell (Mehrfachrollen)

| Rolle | Rechte |
|---|---|
| **Gründer** | Vollzugriff, kann Gründerrolle übertragen (einzigartig) |
| **HR** | Beitrittsanfragen, Mitgliederverwaltung, Rollenvergabe |
| **Baumeister** | Gebäude platzieren/bearbeiten/löschen, Gebäude-Upgrades |
| **Builder** | Baustellenarbeit und Baustellenoptionen (außer Abbrechen) |
| **Händler** | Dorfbewohner-Handel mit Punkteausgaben |
| **Trainer** | Dorfbewohner-Upgrades/Verwaltung mit Punkteausgaben |
| **Member** | Standardrolle für Nicht-Gründer |

Rollen außer Gründer/Member werden über den Upgrade-Bereich `Rollen freischalten` freigeschaltet.

---

## Upgrade-System

Dörfer sammeln Punkte durch Aktivitäten und können diese für Upgrades ausgeben:

| Upgrade | Beschreibung |
|---|---|
| Grenzerweiterung | Mehr Baufläche |
| Mehr Mitglieder | Größere Spielerzahl |
| Mehr Dorfbewohner | Höhere Bewohnerkapazität |
| Produktionsgeschwindigkeit | Schnellere Produktion |
| Neue Berufe | Zusätzliche Dorfbewohner-Arten |
| Steuern | Einnahmen von Besuchern |
| Gebäude freischalten | Neue Gebäudetypen |
| Verteidigung | Verbesserte Anlagen |
| Rollen-Freischaltungen | Rollenspezifische Berechtigungen |

---

## Punkte-System

| Aktion | Punkte |
|---|---|
| Block platzieren/abbauen | 1 |
| Mob töten | 5 |
| Gebäude fertigstellen | 50 |
| Handel | 3 |
| Dorfbewohner-Produktion | 2 |

---

## Persistenz

Alle Dorf-Daten (Grenzen, Mitglieder, Gebäude, bekannte Dörfer) werden beim Speichern in YAML-Dateien persistiert. Nach einem Serverneustart werden entdeckte Dörfer aus den gespeicherten `known-villages`-Einträgen wiederhergestellt – Spieler müssen bereits entdeckte Dörfer **nicht erneut entdecken**.

---

## Light-System

Das Plugin kann für jede Welt eine maximale Lichtstufe definieren. Spieler ohne Dorf sehen den `default-max-light-level`. Spieler in einem Dorf nutzen die konfigurierten Lichtregeln ihres Dorfes.

---

## Relationensystem

Dörfer können Beziehungen zueinander aufbauen:

| Relation | Beschreibung |
|---|---|
| Freundschaft | Gegenseitige Lichtunterstützung und Nähe |
| Handel | Markiert Handelsbeziehungen |
| Krieg | Markiert feindliche Dörfer |
| Durchgangssperre | Sperrt Gebiete zwischen Dörfern |

Beziehungen werden über das Dorf-Menü verwaltet.

---

## GUI-Flows

- **Gebäudedetail**: Whitelist per Rechtsklick toggeln, Linksklick öffnet Whitelist-Untermenü.
- **Gebäude-Schild**: Aktuelle Vorlage wird im Chat klickbar angezeigt; Abbrechen ist klickbar.
- **Schild einblenden**: Berücksichtigt den zuletzt verschobenen Schild-Standort; fragt bei Blockersetzung per Klick-Bestätigung nach.
- **Baumenü**: Admins (`village.admin`) erhalten einen Button für sofortiges Fertigstellen.
- **Hauptmenü**: `Level & Fortschritt` wurde in `Quests` umbenannt (6×9-Übersicht).

---

## Gebäudemenü & Kategorisierung

Das Gebäudemenü ist hierarchisch und vollständig konfigurierbar. Kategorien und Gebäude werden in `config/buildings.yml` unter `menu_categories` definiert:

```yaml
menu_categories:
  - name: "Allgemeine Gebäude"
    items: [lantern, harbor, bridge]
  - name: "Dorfzentrum"
    items: [village_center]
  - name: "Wohnen"
    items: [barracks, house]
```

**Wichtige Regeln:**
- `village_center` (Dorfbrunnen) ist einzigartig und erscheint nicht als auswählbares Bauobjekt. Es kann **nur einen** Dorfbrunnen geben.
- Upgrades sind keine eigenständigen Gebäude und sollten unter dem jeweiligen Gebäudeeintrag konfiguriert werden.
- Gebäude ohne Schematic (z.B. Pfade) mit `requires_schematic: false` markieren.

---

## Schematic-Namenskonvention

Neue `.schem`-Dateien werden beim Speichern automatisch mit einer dreistelligen Varianten-Nummer versehen: `<name>_001.schem`, `<name>_002.schem`, ...

Beim Registrieren mit `/village schematic register <name>` wird der **Basisname ohne** `_###` erwartet (z.B. `house` für `house_001.schem`).

---

## Konfigurationsmigration (Auto-Backup)

Das Plugin prüft beim Laden die `config-version` in den Konfigurationsdateien. Bei einer älteren Version wird die Datei automatisch in `%PLUGIN_FOLDER%/config_backups/<timestamp>/` gesichert und durch die Default-Ressource ersetzt (falls vorhanden).

Um die Versionierung manuell zu setzen, füge `config-version: 1` in `config.yml` oder `config/buildings.yml` ein.

---

## Benachrichtigungen

Beim Kauf von Dorf-Upgrades und Rollen werden neben dem Käufer alle online befindlichen Dorf-Mitglieder informiert. Dies erleichtert die Koordination.

---

## Pfade

Pfade sind keine klassischen Schematics, sondern werden durch Ablaufen erstellt. Sie sind standardmäßig 3 Blöcke breit und bilden einen gewichteten Graphen für Villager-Routing.

**Path-Upgrades** skalieren mit dem Upgrade-Level (konfigurierbar in `config/buildings.yml` unter `path.speed_effect`):

| Parameter | Beschreibung |
|---|---|
| `width_upgradeable` | Ob die Breite mit Level skaliert |
| `max_width` | Maximale Breite |
| `amplifier_per_level` | Speed-Bonus-Zuwachs pro Level |
| `max_amplifier` | Maximaler Speed-Bonus |
| `duration_after_leave_ticks_per_level` | Nachlauf-Dauer pro Level |
