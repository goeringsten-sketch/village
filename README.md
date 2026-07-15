# Village Plugin

Paper/Spigot-Plugin (Minecraft 1.21, Java 21) für Dorfgründung, Gebiete, Gebäude, Dorfbewohner, Wirtschaft und Quests.

Technische Basis: Maven, modulare Config unter `plugins/Village/config/`, Soft-Dependencies: Vault, WorldEdit, WorldGuard, Citizens, ProtocolLib, BlueMap.

Weitere Details:

- Die zentrale Admin-/Server-Übersicht liegt in dieser Datei.
- Die komplette interne Projekt- und Arbeitsdokumentation ist im Ordner [`project-docs/`](project-docs/) gesammelt.

---

## Aktueller Stand (2026-07-15)

Die folgende Übersicht basiert auf der aktuellen Code- und Config-Prüfung im Workspace und ist durch die vorhandenen Klassen/Commands verifiziert:

- ✅ `/v schem list <building_type>` ist im Befehlssystem vorhanden und im Help-/Schematic-Flow dokumentiert.
- ✅ Die Schild-Vorlage für Gebäude unterstützt den Platzhalter `%building_name%` in der `BuildingService`-Vervollständigung/Template-Auswertung.
- ✅ Beim Tod eines Dorfbewohners wird der Eintrag im Dorf entfernt und der NPC-/Revival-Status sauber bereinigt; der letzte Tod bleibt als Revival-Fall hinterlegt.
- ✅ BlueMap-Integration für Dorf-/Gebäude-Marker ist vorhanden und wird beim Anlegen bzw. Auflösen von Dörfern/Gebäuden synchronisiert.
- ✅ Die Dorfgründung nutzt konfigurierbare Startoptionen aus `village.yml` (`founding.requirements`, `territory`, `selection-method`, `well.max-air-blocks` u. a.).
- ✅ Die verbleibende harte Fehlermeldung für `LEVEL_TOO_LOW` wurde auf den Sprach-Key `building-village-level-too-low` umgestellt.
- ✅ Das veraltete Root-Relikt `resources/quests-and-villagers.yml` wurde entfernt; der aktive Pfad ist `config/quests-and-villagers.yml`.
- ✅ Die aktiven Gebäude- und Upgrade-Configs wurden um `max_instances` bzw. fehlende `required-village-level`-Voraussetzungen ergänzt.
- ✅ Der Villager-Trade-Resolver ist robuster gegen Material-Namen/ID-Fallbacks und die Spieler-Nährstoff-Effektanzeige ist aktiv.
- ⚠️ Die eigentliche Playtest-/Ingame-Validierung der Quest- und Upgrade-Flow bleibt weiterhin ein Admin-/Tester-Check.

### Plan für die Admin-/Tester-Handreichung

Die vorhandene DOCX-Datei soll als lebendige Handreichung dienen und nicht nur als alter Bug-Tracker. Daher wird sie künftig in drei Bereiche gegliedert:

1. Aktueller Funktionsstand (implementiert, teilweise umgesetzt, offen).
2. Offene Bugs und bekannte Einschränkungen mit Priorität.
3. Test- und Rollout-Checkliste für Admins und Tester.

Diese README- und DOCX-Update-Arbeit bildet die Grundlage für den nächsten Schritt.

---

## Inhaltsverzeichnis

- [Installation](#installation)
- [Konfigurationsdateien](#konfigurationsdateien)
- [Einrichtung (Kurzüberblick)](#einrichtung-kurzüberblick)
- [Kernfunktionen (Stand)](#kernfunktionen-stand)
- [Quest-System](#quest-system)
- [Licht- & Dunkelmodus](#licht--dunkelmodus)
- [Ernährungssystem (Füttern & Nährstoffe)](#ernährungssystem-füttern--nährstoffe)
- [Gebäude & Jobs (Stand)](#gebäude--jobs-stand)
- [Performance & Debugging](#performance--debugging)
- [Offene TODOs / Roadmap](#offene-todos--roadmap)
- [Build & Tests](#build--tests)

---

## Installation

```bash
mvn -DskipTests clean package
```

JAR: `target/village-1.0.0.jar` → `plugins/`

Empfohlene Abhängigkeiten: **Vault**, **WorldEdit**, **WorldGuard** (optional: Citizens, ProtocolLib, BlueMap).

Hauptbefehl: `/village` (Alias: `/v`, `/dorf`)

---

## Konfigurationsdateien

| Datei | Inhalt |
|---|---|
| `config.yml` | Speicher (`YAML`/`MYSQL`), Sprache |
| `config/village.yml` | Dorf-Level, Upgrades (`required-village-level`), Grenzen, Gründung |
| `config/buildings.yml` | Gebäudetypen, Schematics, Block-Check, Workstations, optional `max_instances` |
| `config/villagers.yml` | Berufe, Tagesablauf, Nährstoffe & Fütterung, Skills, Revival |
| `config/players.yml` | Rollen, Berechtigungen |
| `config/currencies.yml` | Globale & lokale Währungen |
| `config/quests-and-villagers.yml` | Quests, Reputation, Performance-Parameter |
| `config/light-limits.yml` | Dunkelmodus, POIs, Minen-Dunkelheit (`underground-max-level`) |
| `config/debug.yml` | Debug-Flags (`building`, `chat`, `light`, `villager`) |
| `lang/de.yml`, `lang/en.yml` | Nachrichten |

> Konfigurations- und Projekt-Hinweise für Entwickler sind im internen Ordner [`project-docs/`](project-docs/) zusammengefasst.

---

## Einrichtung (Kurzüberblick)

1. Vault + Economy-Plugin installieren
2. `config.yml`: `storage.type: YAML`, `language: de`
3. Gründungskosten in `village.yml` → `founding` setzen
4. Währungen in `currencies.yml` konfigurieren
5. Berufe & Nährstoffe in `villagers.yml` anpassen
6. Gebäude in `buildings.yml` aktivieren
7. Licht-System in `light-limits.yml` (optional)
8. Quests in `quests-and-villagers.yml`

Häufige Fehler: `initial-size` muss **ungerade** sein; Material-Namen in **GROSSBUCHSTABEN**; Debug-Flags nach Tests wieder auf `false`.

---

## Kernfunktionen (Stand)

| Bereich | Status | Anmerkung |
|---|---|---|
| Dorfgründung & Glocke | ✅ | Inkl. auto-registriertes Dorfzentrum und konfigurierbare Startoptionen |
| Grenzen (Walk, Koordinaten, Fusion) | ✅ | |
| Gebäude (Schematic + Block-Check) | ✅ | Skelett-/Dach-Validierung für `hollow_structure` |
| Gebäude-Instanzlimits (`max_instances`) | ✅ | Code + Tests; optional pro Gebäude in `buildings.yml` |
| Schematic-Tool ingame (`/v schem`) | ✅ | inkl. `list <building_type>`-Unterstützung |
| Villager spawnen, Bett/Job zuweisen | ✅ | Linksklick auf Arbeitsblock |
| Berufe aus `villagers.yml` | ✅ | 17 Jobs im Enum + Config-Professions |
| Tagesablauf (`activity-sequence`) | ✅ | Alle 17 Berufe definiert |
| Produktion & Skill-Bäume | ✅ | |
| Quests & Handel | ✅ | Sammelquests, Fortschritts-Tracking und GUI-/Command-Flow |
| Upgrade-Level-Voraussetzungen | ✅ | `required-village-level` pro Upgrade (Default: 1) |
| Währungen (global/lokal) | ✅ | Vault-Integration |
| Nährstoff-/Fütterungssystem | ✅ | Vollständig laut Spezifikation |
| Licht- & Dunkelmodus | ✅ | Inkl. Minen-Dunkelheit unter Y=40 |
| NPC-Performance-Optimierung | ✅ | Queue-Ticking, Chunk-Optimierung |
| Citizens-NPC-Bewegung | ⚠️ | Optional, benötigt Citizens |

---

## Quest-System

Konfiguration: `config/quests-and-villagers.yml`

| Feature | Beschreibung |
|---|---|
| `objective-type` | Pflicht für alle Quests (`collect-items`, `feed-villagers`, `recruit-villager`, `trade`, …) |
| `required-items` | Bei `collect-items`: Items werden beim Abschluss aus dem Inventar abgezogen |
| `required-village-level` | Mindest-Dorflevel pro Quest |
| `villager-limits` | **Entfernt** – einheitliche Quelle ist `villagers.yml` → `base-max` |

Beispiel Sammelquest:

```yaml
starter-quest-1:
  objective-type: collect-items
  required-items:
    OAK_LOG: 10
```

---

## Licht- & Dunkelmodus

Konfiguration: `config/light-limits.yml`

- Entfernungsbasierte Helligkeitsstufen um Dörfer, POIs und Straßen
- **`underground-max-level: 40`** – unter Y=40 werden 2D-Lichtquellen ignoriert (Minen standardmäßig dunkel)
- Deaktivieren mit `-999`
- **`refresh-batch-size-chunks: 4`** – reduziert Performance-Spitzen bei vielen Spielern
- Adaptives Chunk-Batching skaliert mit Spieleranzahl

---

## Ernährungssystem (Füttern & Nährstoffe)

Zentrale Klassen: `VillagerNutritionService`, `VillagerScheduleManager`, `VillagerTickService`, `CustomVillager`.

Konfiguration: `config/villagers.yml` → `needs`, `nutrients`, `feeding`, `nutrition`, `schedule`.

### Implementierungs-Checkliste (Spezifikation vs. Code)

| Anforderung | Status |
|---|---|
| Hunger-Schaden unter Schwellwert | ✅ |
| Spieler-Fütterung / Kreativ | ✅ |
| Selbst-Fütterung | ✅ |
| Nährstoffwerte pro Lebensmittel | ✅ |
| Sofort + periodische Wiederherstellung | ✅ |
| Hunger = Durchschnitt der Nährstoffe | ✅ |
| Abbau pro Tick + am Aktivitätsende | ✅ |
| Aktivitäts-Sequenz pro Job (17 Berufe) | ✅ |
| Effekte (Prod., Lauf, XP, Bau, Beziehung) | ✅ |
| Negative Effekte & Dauer-Stacking | ✅ |
| Feed-Interval & Flags | ✅ |
| `hunger`-Key in recovery | ✅ |

**Revival:** Wiederbelebung beim Medikus, Berechtigung `village.revival.use`.

### Lebensmittel in `villagers.yml`

Alle gelisteten Items sind konfiguriert, inkl. COOKED_COD, COOKED_PORKCHOP, BEETROOT, SUSPICIOUS_STEW.

### Konfigurierbare Effekt-Schlüssel

| Effekt-Key | Verwendung |
|---|---|
| `production-speed` | Produktions-Multiplikator |
| `movement-speed` | Citizens-Navigator `speedModifier` |
| `xp-gain` | XP bei Produktion |
| `build-speed` | Konstruktionsdauer |
| `relationship` / `communication` | Reputation bei Fütterung |

Negative Werte (z. B. `production-speed: -0.05`) reduzieren den Multiplikator.

---

## Gebäude & Jobs (Stand)

### Gebäude-Validierung

- **SCHEMATIC:** WorldEdit-Schematic, blockweiser Bau, danach `completed = true`
- **HYBRID:** Schematic mit `.schem.meta` (STRUCTURE vs. DECORATION); `validation_mode: hybrid`
- **BLOCK_CHECK:** Blöcke/Hohlraum im definierten Bereich; optional **Ästhetik-Score** (`AestheticScoreService`)
- **hollow_structure:** Zusätzlich Skelett- (Wände/Boden) und Dach-Validierung (`BlockCheckValidator`)
- **Fundament-Stretching:** Schematics passen sich automatisch ans Terrain an (`FoundationStretchService`)

Details: siehe [`project-docs/gebaeude_konzept.md`](project-docs/gebaeude_konzept.md) für die interne Gebäude-Roadmap.

### Instanzlimits

```yaml
# buildings.yml – optional pro Gebäude
max_instances: 1   # -1 = unbegrenzt (Default)
```

Platzierung schlägt fehl mit `TOO_MANY_INSTANCES`, wenn das Limit erreicht ist.

### Modulare Schematic-Upgrades (`modular_extensions`)

Für Upgrade-Stufen kann ein Gebäude zusätzliche Module an benannten Ankerpunkten im Basis-Schematic einhängen. Das Prinzip ist:

1. In der `.schem.meta`-Datei werden Marker-Anker definiert, zum Beispiel `roof_center` oder `north_wall`.
2. Im `buildings.yml`-Upgrade wird `modular_extensions` ergänzt.
3. Beim Upgrade wird das Modul-Schematic an den benannten Anker des Basis-Schematics eingesetzt.

Beispiel:

```yaml
upgrades:
  tier_2:
    attach_at: "roof_center"
    module: "kartograph_tower.schem"
```

Wichtig:

- `attach_at` muss exakt dem Namen aus der `.schem.meta`-Datei entsprechen.
- `module` ist die Schematic-Datei, die am Ankerpunkt platziert werden soll.
- Das Basis-Schematic bleibt dabei unverändert; nur die Modul-Erweiterung wird hinzugefügt.

### Config-Bereinigungen (01/2026)

- Doppelte Keys in `general`/`production` → `deko_forst`, `deko_acker`, `deko_beet`, `deko_tierzucht`
- Rezept-Jobs korrigiert (Fischer, Schmied statt Händler/Bauer)
- `imkerei`/`imker`-Duplikat bereinigt, Material `BEEHIVE`
- Ungültiges Material `HOES` → `WOODEN_HOE`

### Berufe (Enum `VillagerJob`)

Bauer, Bergarbeiter, Holzfäller, Tischler, Schmied, Steinmetz, Imker, Bäcker, Brauer, Fischer, Jäger, Wache, Händler, Gelehrter, Kartograph, Medikus, Kurier (+ Arbeiter/none als Fallback).

Job-Zuweisung: Villager-Menü → **Job zuweisen** → Linksklick (oder Rechtsklick) auf Arbeitsblock eines **fertigen** Gebäudes. Abbruch: `/v abort`.

Workstation-Erkennung nutzt Config-Area **und** Schematic-Bounding-Box (`BuildingBoundsUtil`).

---

## Performance & Debugging

### Performance (`quests-and-villagers.yml` → `performance`)

| Feld | Default | Wirkung |
|---|---|---|
| `chunk-optimization` | `true` | Kein Tick in ungeladenen Chunks |
| `max-updates-per-tick` | `10` | CPU-Last pro Tick begrenzen |
| `batch-update-interval` | `20` | Intervall für Batch-Updates |

`VillagerTickService` nutzt eine interne Warteschlange mit `max-updates-per-tick`.

### Debug (`debug.yml`)

| Flag | Zweck |
|---|---|
| `building` | Platzierung, Validierung, Schematics |
| `chat` | Dialoge & Chat |
| `light` | Licht-Stages, Chunk-Refresh |
| `villager` | State-Wechsel, Tagesablauf, KI-Ticker |

> Debug-Flags im Live-Betrieb auf `false` lassen.

---

## Offene TODOs / Roadmap

### Aktuell offen

- Modulare Schematic-Upgrades über `modular_extensions` in `buildings.yml` mit Ankern aus `.schem.meta`
- Adaptives Dach bei starkem Terrain-Gefälle
- Ingame-Dekorations-Editor zum Erzeugen von `.schem.meta`-Dateien

### Bewusst zurückgestellt

- Vollständige Level-Prerequisites für alle Dorf-Level (2–4, 6–9, …) in `village.yml`
- Feinjustierung der `max_instances`-Werte in `buildings.yml` je Gebäude
- Optionales Produktivitäts-/UI-Polishing rund um `PlayerNutritionService` und den lokalen Villager-Trade-Resolver

---

## Build & Tests

```bash
mvn test                    # Unit-Tests (aktuell 31 Tests)
mvn -DskipTests clean package
```

Relevante Testklassen:

- `QuestConfigTest` – Quest-Objective & `required-items`
- `LightLimitsConfigTest` – `underground-max-level`
- `BuildingConfigMaxInstancesTest` – `max_instances`-Validierung

Siehe [`project-docs/TEST.md`](project-docs/TEST.md) für interne Testfälle und Server-Setup.
