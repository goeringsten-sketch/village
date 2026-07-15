# 🚀 Setup & Getting Started Guide

## Installation

### 1. Voraussetzungen

Stelle sicher, dass dein Server diese Plugins haben:
- ✅ **Paper 1.21.4+** (oder Spigot/Bukkit)
- ✅ **Citizens 2.0+**
- ✅ **Vault** (für Economy)
- ✅ **WorldEdit** (für Schematics)
- ✅ **WorldGuard** (für Schutz)
- ✅ **ProtocolLib** (Optional, für Effekte)

### 2. Plugin bauen & deployen

```bash
cd /workspaces/village
mvn clean package
```

Das generierte JAR findest du unter:
```
target/village-1.0.0.jar
```

Kopiere es in deinen `plugins/` Ordner:
```bash
cp target/village-1.0.0.jar /path/to/server/plugins/
```

### 3. Server starten

```bash
java -Xmx2G -Xms1G -jar paper-1.21.4.jar nogui
```

Das Plugin wird automatisch initialisiert.

### 4. Konfigurieren

Passe diese Dateien im `plugins/Village/` Ordner an:

```
plugins/Village/
├── config.yml                    # Basis-Konfiguration
├── quests-and-villagers.yml      # Quests, Skills, Dialoge
├── light-limits.yml              # Light-System (ignorieren für jetzt)
└── schematics/                   # Gebäudeschematics
```

---

## Erste Schritte

### 1. Dorf gründen (bestehende Feature)

```
/village
→ Fenster öffnet sich
→ Wähle Brunnen-Standort
→ Definiere Grenze
```

### 2. Villager rekrutieren (NEU)

```
/villager recruit FARMER "Max"
```

**Verfügbare Jobs:** FARMER, MERCHANT, GUARD, LIBRARIAN, MILLER, PRIEST, LABORER

### 3. Mit Villager interagieren (NEU)

```
Rechtsklick auf den NPC
→ Menü öffnet sich
→ Wähle eine Option
```

### 4. Skill upgraden

```
Menü → "Skills"
→ Wähle einen Skill
→ Rechtsklick zum Upgrade
```

### 5. Quest annehmen

```
Menü → "Quest"
→ Quest-Liste öffnet sich
→ Linksklick zum Annehmen
```

---

## Befehle (Quick Reference)

```bash
# Listes Villager
/villager list

# Zeigt Villager-Info
/villager info Max

# Rekrutiert neuen Villager
/villager recruit FARMER "Neuer Name"

# Löscht Villager
/villager remove Max

# Zeigt Quests
/villager quest list

# Upgradet Skill
/villager skill Max Anbau

# Hilfe
/villager help
```

---

## Konfiguration anpassen

### Beispiel 1: Neue Quest hinzufügen

**Datei:** `plugins/Village/quests-and-villagers.yml`

```yaml
quests:
  farming-advanced:
    title: "Fortgeschrittene Landwirtschaft"
    description: "Hilf dem Bauern mit einer speziellen Ernte."
    objective: "200x Weizen sammeln"
    required-village-level: 5
    min-reputation: 0
    is-daily: false
    reward:
      village-points: 100
      money: 250
      villager-xp: 30
      items:
        GOLDEN_HOE: 1
    prerequisites:
      - farming-mission
```

Dann Server neu starten oder `/reload` nutzen.

### Beispiel 2: Neue Dialog-Zeile

```yaml
messages:
  dialogues:
    WORKING:
      working-custom: "Ich bin gerade sehr beschäftigt mit {job}!"
```

### Beispiel 3: Villager-Limits ändern

```yaml
villager-limits:
  base-max: 10          # Start: 10 statt 5
  per-level: 2          # +2 pro Level statt +1
  per-house: 3          # +3 pro Haus statt +2
```

---

## Performance-Tuning

### Für kleine Server (< 50 Villager)

```yaml
performance:
  chunk-optimization: false
  max-updates-per-tick: 20
  batch-update-interval: 10
```

### Für große Server (> 100 Villager)

```yaml
performance:
  chunk-optimization: true
  max-updates-per-tick: 5
  batch-update-interval: 40
```

### Debug-Informationen

Aktiviere Debug-Logging in `config.yml`:

```yaml
debug:
  villager-updates: true
  quest-assignments: true
  skill-upgrades: true
```

Logs werden geloggt in:
```
logs/latest.log
```

---

## Troubleshooting

### Problem: "Nicht genug Dorfpunkte"

→ Quest-Belohnungen bringen Dorfpunkte  
→ Gebäude komplettieren bringt Dorfpunkte  
→ Admin kann manuell geben: `/village admin addpoints <Menge>`

### Problem: Villager spawnt nicht

**Checklist:**
- [ ] Citizens plugin aktiv? → `/citizensnpcs reload`
- [ ] Genug Betten? → `/villager list` und Beds zählen
- [ ] Villager-Limit erreicht? → `/village admin setlimit <amount>`
- [ ] Dorf-Level niedrig? → Gebäude bauen für Level-Up

### Problem: Menü öffnet sich nicht

**Checklist:**
- [ ] GUI-Plugins konflikt? → `/pl` und nach anderen GUI-Plugins suchen
- [ ] Berechtigungen? → `Gib Spieler `OP`
- [ ] Inventar voll? → Einige Items droppen

### Problem: Nur zu 20 FPS laufen

→ Zu viele Villager aktiv  
→ Aktiviere Chunk-Optimierung: `chunk-optimization: true`  
→ Oder reduziere `max-updates-per-tick: 3`

### Problem: Quests lädt nicht

**Checklist:**
- [ ] `quests-and-villagers.yml` existiert?
- [ ] YAML-Syntax korrekt? (Kein Tab statt Spaces!)
- [ ] War in `config.yml` unter `quests:`?

---

## Häufig gestellte Fragen

**Q: Kann ich Villager zwischen Dörfern verschieben?**  
A: Ja! Menü → Transfer → Zieldorf wählen

**Q: Was passiert mit Villager-Daten bei Plugin-Reload?**  
A: Alles wird in der Datenbank gespeichert (YAML oder MySQL)

**Q: Können Spieler gegen Villager kämpfen?**  
A: Ja! Schaden reduziert Health-Bedürfnis. Villager flieht zum Wachturm.

**Q: Wie lange dauert es, bis Villager ein Item produzieren?**  
A: Abhängig von Skill/Morale, Standard: 5 Minuten

**Q: Können Villager sterben?**  
A: Ja, wenn Hunger zu lange kritisch ist. Mit Respawn-Mechanik (manuell).

---

## Admin-Commands (Geplant)

Diese Commands sind noch nicht implementiert, aber geplant:

```bash
# Admin-Panel öffnen
/village admin

# Villager-Debug-Info
/village admin debug <Name>

# Force-Produktivität
/village admin produce <Name> <Amount>

# Reputation ändern
/village admin setreputation <Player> <Villager> <Value>

# Erzwinge Quest-Complete
/village admin completequest <Player> <QuestId>
```

---

## API für Plugin-Entwickler

Lade dir die benötigten Services:

```java
VillagePlugin village = (VillagePlugin) Bukkit.getPluginManager()
    .getPlugin("Village");

// Advanced Services
VillagerManager villagerManager = village.getAdvancedVillagerManager();
QuestManager questManager = village.getQuestManager();
SkillTreeManager skillManager = village.getSkillTreeManager();
DialogueSystem dialogueSystem = village.getDialogueSystem();
```

Beispiel - Externe Quest-Completion:

```java
public void completePlayerQuest(Player player, String questId) {
    Village village = villageManager.getPlayerVillage(player.getUniqueId());
    if (village == null) return;
    
    CustomVillager villager = village.getVillagers().get(0);
    Quest quest = questManager.getPlayerQuests(player.getUniqueId()).stream()
        .filter(q -> q.getQuestId().equals(questId))
        .findFirst()
        .orElse(null);
    
    if (quest != null) {
        questManager.completeQuest(player, quest, villager, village);
    }
}
```

---

## Support & Bugs

Bei Problemen oder Bugs:

1. **Logs überprüfen:** `logs/latest.log"
2. **config.yml überprüfen:** Syntax und Werte
3. **Plugin-Konflikte?** `/plugins` und Testing
4. **Version überprüfen:** `/plugins village`

---

## Migration von Old System

Falls es ein altes Villager-System gibt:

1. **Backup machen!** `cp villages.yml villages.old.yml`
2. **Alte Daten convertieren**
3. **Neue Datenbank formatieren** (Optional)

*Migration-Script wird bei Bedarf bereitgestellt.*

---

## Best Practices

### 1. Balance die Kosten

```yaml
# Nicht zu teuer (Spieler geben auf)
recruitment: 50      ✅

# Nicht zu billig (Exploit)
recruitment: 1000    ❌
```

### 2. Variantenreiche Quests

```yaml
quests:
  quest-1: { ... }
  quest-2: { ... }      # Verschiedene Typen
  quest-3: { ... }
```

### 3. Skill-Balance anpassen

```yaml
skills:
  anbau:
    max-level: 20      # Nicht zu hoch
    base-cost: 100     # Nicht zu niedrig
```

### 4. Regelmäßige Backups

```bash
# Cron-Job (täglich um 3 Uhr)
0 3 * * * cp /server/plugins/Village/villages.yml /backups/villages-$(date +%Y-%m-%d).yml
```

---

## Support & Documentation

- 📖 **Ausführliche Doku:** [VILLAGER_AND_QUEST_SYSTEM.md](VILLAGER_AND_QUEST_SYSTEM.md)
- 📋 **Implementierungs-Details:** [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)
- 💻 **Source Code:** `/java/com/example/village/`
- 🎓 **API-Docs:** Inline JavaDoc

---

**Viel Spaß mit dem neuen System!** 🎉

Bei Fragen: Siehe Dokumentation oder öffne ein Issue.

---

*Letztes Update: April 2026*  
*Version: 1.0.0*  
*Für: Paper 1.21.4+*
