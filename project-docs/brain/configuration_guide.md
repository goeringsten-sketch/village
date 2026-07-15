# Village Plugin – Konfigurationsleitfaden

> **Step-by-step Anleitung**: Folge den Schritten in dieser Reihenfolge für eine optimale Einrichtung.

---

## Schritt 1: Basisinstallation & Speicher (`config.yml`)

**Ziel:** Plugin startet und speichert Daten korrekt.

```yaml
storage:
  type: YAML   # → für den Start immer YAML; MySQL erst wenn nötig
language: de
```

**Checkliste:**
- [ ] Sprachdatei `resources/lang/de.yml` vorhanden
- [ ] Vault-Plugin installiert (für globale Währung zwingend nötig)
- [ ] Economy-Plugin installiert (z.B. EssentialsX Economy oder OpenEco)

---

## Schritt 2: Dorfgründung konfigurieren (`village.yml` → `founding`)

**Ziel:** Festlegen, was ein Spieler braucht, um ein Dorf zu gründen.

```yaml
founding:
  requirements:
    permission: "village.create"   # → In LuckPerms o.ä. vergeben
    money:
      global: 1000.0              # → Startkosten in globaler Währung
    items:
      - material: EMERALD
        amount: 1
```

**Empfehlungen:**
- Gründungskosten an die Server-Wirtschaft anpassen (Wie viel Geld haben neue Spieler?)
- Permission `village.create` an eine Gruppe (z.B. `member`) vergeben
- Zum Test auf `0.0` und `items: []` setzen, danach wieder erhöhen

---

## Schritt 3: Territorium & Grenzen (`village.yml` → `territory`)

**Ziel:** Dorfgebiet-Größe und Steuerung der Grenzauswahl festlegen.

```yaml
territory:
  max-area: 10000         # Startfläche (100×100 Blöcke)
  initial-size: 21        # Quadrat um den Brunnen (MUSS ungerade!)
  selection-method: BOTH  # WALK | COORDINATES | BOTH
```

**Wichtig:**
- `initial-size` muss **ungerade** sein (z.B. 21, 31, 41)
- `max-area` wird durch das `border-expansion`-Upgrade erweitert
- Vorschau-Blöcke (`preview-block: REDSTONE_ORE`) sollten gut sichtbar aber nicht störend sein

---

## Schritt 4: Währungssystem einrichten (`currencies.yml`)

**Ziel:** Globale und lokale Währung definieren.

### 4a. Globale Währung
```yaml
global:
  name: "Goldmünze"
  startingAmount: 100      # Startkapital neuer Spieler
  conversion:
    enabled: true
    rate: 1.25             # 125 Taler = 100 Goldmünzen
```

### 4b. Handelssystem
```yaml
trading:
  villagerTrades:
    enabled: true
    localCurrencyTrades:
      enabled: true
    globalCurrencyTrades:
      enabled: true
      externalPlayersCanBuy: true
```

**Tipp:** Mit `externalPlayersCanBuy: false` kann man den Handel auf Dorfmitglieder beschränken.

---

## Schritt 5: Level-System (`village.yml` → `levels`)

**Ziel:** Progression festlegen – wie schnell wächst ein Dorf?

```yaml
levels:
  max-level: 50
  points-per-level:
    - level: 1
      points: 100
    # ... weitere Einträge
  point-sources:
    block-place: 1
    building-complete: 50  # Gebäude bauen = Hauptpunktequelle
```

**Tipps:**
- `building-complete: 50` sorgt dafür, dass Bauen die schnellste Punktequelle ist
- `points-per-level` interpoliert: Level 2–4 brauchen den Wert von Level 1 → Level 5
- Voraussetzungen in `prerequisites` nutzen, um Gebäude als Pflichtziel zu setzen

---

## Schritt 6: Dorfbewohner-Berufe konfigurieren (`villagers.yml`)

**Ziel:** Berufe, Produktionszeiten und Tagesabläufe anpassen.

### 6a. Einfache Anpassungen
```yaml
# Produktionsintervall eines Berufes ändern:
professions:
  farmer:
    production-interval-seconds: 300  # 5 Minuten pro Item
    produces:
      - WHEAT
      - CARROT
      - POTATO
```

### 6b. Aktivitäts-Sequenz anpassen
```yaml
activity-sequence:
  work-morning:
    type: "WORK"          # TRAVEL_TO | WORK | PREPARE | GATHER | SLEEP | IDLE | TRADE
    location-key: "workstation"
    duration-ticks: 1200  # 60 Sekunden Arbeit
    interruptable: false
```

### 6c. Nährstoffsystem deaktivieren (für einfachere Einrichtung)
```yaml
needs:
  hunger:
    entity-damage:
      enabled: false      # Kein echter Schaden durch Hunger
nutrition:
  balanced-diet:
    enabled: false        # Keine Malus-Effekte durch Nährstoffmangel
warnings:
  enabled: false          # Keine Hunger-Warnmeldungen
```

---

## Schritt 7: Gebäude-System vorbereiten (`buildings.yml`)

**Ziel:** Wichtigste Gebäude aktivieren und Baukosten anpassen.

### Validierungsmodus wählen
| Modus | Beschreibung | Empfehlung |
|-------|-------------|------------|
| `block_check` | Prüft vorhandene Blöcke | Für Spieler-gebaute Strukturen |
| `schematic` | Vergleicht mit Schematic-Datei | Für exakt vorgegebene Gebäude |

### Baukosten anpassen
```yaml
buildings:
  dorfzentrum:
    build_cost:
      items: { OAK_PLANKS: 32, STONE: 16 }
      money: 0            # Optional: keine Geldkosten
      money_local: 0      # Optional: keine lokale Währung
```

### Rezepte definieren (für Produktionsgebäude)
```yaml
recipes:
  weizen:
    inputs: { WHEAT_SEEDS: 2 }
    outputs: { WHEAT: 6, WHEAT_SEEDS: 2 }
    duration_seconds: 180
    required_villager_job: FARMER  # Groß: FARMER, MINER, BLACKSMITH, ...
    required_building_level: 1
```

---

## Schritt 8: Licht-System konfigurieren (`light-limits.yml`)

**Ziel:** Dunkelmodus für Spieler außerhalb von Dörfern einrichten.

### Einfache Konfiguration (schrittweise Dunkelheit)
```yaml
light-control:
  enabled: on
  default-max-light-level: 0    # Vollständige Dunkelheit weit draußen
  stage-shape: square           # square | circle
  stages:
    - distance: 0
      max-light-level: 15       # Am Dorfrand: volle Helligkeit
    - distance: 50
      max-light-level: 8        # 50 Blöcke draußen: Dämmerung
    - distance: 100
      max-light-level: 3        # 100 Blöcke draußen: fast dunkel
    - distance: 1000
      max-light-level: 0        # Sehr weit draußen: komplett dunkel
```

### Spawn-Bereich beleuchten
```yaml
points-of-interest:
  spawn:
    world: world
    x: 0     # X-Koordinate des Spawns
    z: 0     # Z-Koordinate des Spawns
    radius: 100
    stages:
      - distance: 0
        max-light-level: 15
      - distance: 100
        max-light-level: 5
```

---

## Schritt 9: Quests & Reputation (`quests-and-villagers.yml`)

**Ziel:** Einsteiger-Quests und Reputationssystem einrichten.

```yaml
quests:
  starter-quest-1:
    is-daily: false              # false = einmalig | true = täglich
    required-village-level: 1
    min-reputation: -100         # Für alle zugänglich
    reward:
      village-points: 50
      money:
        global: 100

relationships:
  reputation-thresholds:
    neutral: 20      # Ab diesem Wert: normales Verhalten
    friendly: 50     # Ab diesem Wert: bessere Preise
  reputation-rewards:
    quest-complete: 10
    hit: -10
```

---

## Schritt 10: Upgrades & Rollen freischalten (`village.yml` + `players.yml`)

**Ziel:** Wichtige Upgrades und Spieler-Rollen konfigurieren.

### Reihenfolge der wichtigsten Upgrades
1. `border-expansion` – Mehr Platz für das Dorf
2. `max-villagers` – Mehr NPC-Kapazität
3. `new-professions` – Höherstufige Berufe freischalten
4. `max-members` – Mehr Spieler im Dorf

```yaml
upgrades:
  border-expansion:
    max-level: 10
    cost-per-level:
      global: 500.0
    area-per-level: 2000   # +2000 Blöcke² pro Stufe
```

### Rollen-Upgrades (players.yml)
```yaml
role-upgrades:
  role_builder:              # Spieler kann einfache Gebäude bauen
    money-cost:
      global: 1500.0
    points-cost: 350
```

---

## Schnell-Referenz: Wichtigste Keywords

| Feld | Mögliche Werte |
|------|---------------|
| `storage.type` | `YAML` \| `MYSQL` |
| `territory.selection-method` | `WALK` \| `COORDINATES` \| `BOTH` |
| `territory.stage-shape` | `square` \| `circle` |
| `validation_mode` | `schematic` \| `block_check` |
| `area.shape` | `circle` \| `rectangle` \| `hollow_structure` |
| `type` (Gebäude) | `path` \| `area` \| `outpost` |
| `activity-sequence type` | `TRAVEL_TO` \| `WORK` \| `PREPARE` \| `GATHER` \| `SLEEP` \| `IDLE` \| `TRADE` \| `GET_FOOD` \| `EAT_FOOD` |
| `currency-type` | `global` \| `local` |
| `objective-type` | `collect-items` \| `feed-villagers` \| `kill-mobs` \| `trade` \| `recruit-villager` \| `build-building` |
| `is-daily` | `true` \| `false` |
| `interruptable` | `true` \| `false` |
| `enabled` | `true` \| `false` \| `on` \| `off` |
| `surface_check_mode` | `skylight` \| `all` |

---

## Häufige Fehler

> [!CAUTION]
> **`initial-size` muss ungerade sein!** Bei geraden Zahlen (z.B. 20) wird der Brunnen nicht korrekt zentriert.

> [!WARNING]
> **Material-Namen immer in GROSSBUCHSTABEN!** `oak_planks` funktioniert nicht → `OAK_PLANKS` verwenden.

> [!WARNING]
> **Vault muss installiert sein**, bevor das Plugin startet. Ohne Vault schlägt die globale Währung fehl.

> [!NOTE]
> **Debug-Flags nach Tests zurücksetzen.** `debug.building: true` kann bei vielen Gebäuden die Konsole fluten und die Performance beeinträchtigen.
