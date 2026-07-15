# TODO: Multilanguage-Lücken

Neue Features aus dem letzten Implementierungszyklus sind **noch nicht vollständig
in das Sprachsystem** (`lang/de.yml`, `lang/en.yml`) eingebettet.
Alle unten gelisteten Strings sind **direkt im Java-Code hartkodiert** und müssen
auf `configManager.message("<key>")` bzw. `MessageUtil.send(...)` umgestellt werden.

---

## 1. QuestManager.java — hardkodierte Spielernachrichten

**Datei:** `java/com/example/village/service/QuestManager.java`

| Zeile | Hardkodierter String | Vorgeschlagener lang-Key |
|---|---|---|
| ~295 | `"§a+" + format + " erhalten!"` (Vault-Geld-Belohnung) | `quest-reward-money-vault` |
| ~297 | `"§a+" + format + " Münzen erhalten!"` (Fallback ohne Vault) | `quest-reward-money-fallback` |
| ~303 | `"§a+" + amount + " " + currencyName + " erhalten!"` (lokale Währung) | `quest-reward-local-money` |
| ~392 | `"§aQuest-Ziel erreicht: §f" + title + " ..."` | `quest-goal-reached` |
| ~395 | `"§7Fütterung: §e" + count + "§7/§e" + total` | `quest-feed-progress` |
| ~414 | `"Quest-Ziel noch nicht erreicht: X/Y Fütterungen"` (Exception) | `quest-goal-not-reached` |
| ~429 | `"Du benötigst Xx MAT (besitzt: Y)"` (collect-items Fehler) | `quest-collect-items-missing` |

**Platzhalter:**
- `quest-reward-money-vault`: `%amount%`
- `quest-reward-money-fallback`: `%amount%`
- `quest-reward-local-money`: `%amount%`, `%currency%`
- `quest-goal-reached`: `%title%`, `%current%`, `%total%`
- `quest-feed-progress`: `%current%`, `%total%`
- `quest-goal-not-reached`: `%current%`, `%total%`
- `quest-collect-items-missing`: `%amount%`, `%material%`, `%has%`

---

## 2. GuiClickListener.java — hardkodierte Upgrade-Fehlermeldungen

**Datei:** `java/com/example/village/listener/GuiClickListener.java`

| Zeile | Case | Hardkodierter String | Vorgeschlagener lang-Key |
|---|---|---|---|
| ~1352 | `MAX_LEVEL_REACHED` | `"§cMax Level erreicht!"` | `upgrade-max-level` |
| ~1356 | `NOT_ENOUGH_POINTS` | `"§cNicht genuegend Dorfpunkte!"` | `upgrade-not-enough-points` |
| ~1358 | `VILLAGE_LEVEL_TOO_LOW` | `"§cDas Dorflevel ist zu niedrig für dieses Upgrade!"` | `upgrade-village-level-too-low` |
| ~2283 | `MAX_LEVEL_REACHED` (Rollen) | `"§eDiese Rolle ist bereits freigeschaltet."` | `upgrade-role-already-unlocked` |
| ~2287 | `NOT_ENOUGH_POINTS` (Rollen) | `"§cNicht genuegend Dorfpunkte!"` | `upgrade-not-enough-points` |
| ~2289 | `VILLAGE_LEVEL_TOO_LOW` (Rollen) | `"§cDas Dorflevel ist zu niedrig für dieses Upgrade!"` | `upgrade-village-level-too-low` |
| ~2291 | `default` (Rollen-Fehler) | `"§cFreischaltung fehlgeschlagen."` | `upgrade-failed-generic` |

> `upgrade-too-expensive` wird bereits korrekt aus der Config gelesen — analog ergänzen.

---

## 3. BuildingInteractListener.java — hardkodierte Gebäude-Fehlermeldung

**Datei:** `java/com/example/village/listener/BuildingInteractListener.java`

| Zeile | Case | Hardkodierter String | Vorgeschlagener lang-Key |
|---|---|---|---|
| ~199 | `TOO_MANY_INSTANCES` | `"§cDie maximale Anzahl dieses Gebäudetyps ..."` | `building-max-instances-reached` |

---

## 4. Benötigte Ergänzungen in lang/de.yml und lang/en.yml

### `lang/de.yml` — unter `messages:` ergänzen:

```yaml
# --- Quests ---
quest-reward-money-vault:     "&a+%amount% erhalten!"
quest-reward-money-fallback:  "&a+%amount% Münzen erhalten!"
quest-reward-local-money:     "&a+%amount% %currency% erhalten!"
quest-goal-reached:           "&aQuest-Ziel erreicht: &f%title% &7(%current%/%total%)"
quest-feed-progress:          "&7Fütterung: &e%current%&7/&e%total%"
quest-goal-not-reached:       "&cQuest-Ziel noch nicht erreicht: %current%/%total% Fütterungen"
quest-collect-items-missing:  "&cDu benötigst %amount%x %material% (besitzt: %has%)"

# --- Upgrades ---
upgrade-max-level:            "&cMax Level erreicht!"
upgrade-not-enough-points:    "&cNicht genügend Dorfpunkte!"
upgrade-village-level-too-low:"&cDas Dorflevel ist zu niedrig für dieses Upgrade!"
upgrade-role-already-unlocked:"&eDiese Rolle ist bereits freigeschaltet."
upgrade-failed-generic:       "&cFreischaltung fehlgeschlagen."

# --- Gebäude ---
building-max-instances-reached: "&cDie maximale Anzahl dieses Gebäudetyps in deinem Dorf wurde bereits erreicht."
```

### `lang/en.yml` — unter `messages:` ergänzen:

```yaml
# --- Quests ---
quest-reward-money-vault:     "&a+%amount% received!"
quest-reward-money-fallback:  "&a+%amount% coins received!"
quest-reward-local-money:     "&a+%amount% %currency% received!"
quest-goal-reached:           "&aQuest goal reached: &f%title% &7(%current%/%total%)"
quest-feed-progress:          "&7Feeding: &e%current%&7/&e%total%"
quest-goal-not-reached:       "&cQuest goal not yet reached: %current%/%total% feedings"
quest-collect-items-missing:  "&cYou need %amount%x %material% (you have: %has%)"

# --- Upgrades ---
upgrade-max-level:            "&cMax level reached!"
upgrade-not-enough-points:    "&cNot enough village points!"
upgrade-village-level-too-low:"&cVillage level too low for this upgrade!"
upgrade-role-already-unlocked:"&eThis role is already unlocked."
upgrade-failed-generic:       "&cUnlock failed."

# --- Buildings ---
building-max-instances-reached: "&cThe maximum number of this building type in your village has already been reached."
```

---

## 5. Implementierungsschritte

- [ ] Keys in `lang/de.yml` unter `messages:` ergänzen
- [ ] Keys in `lang/en.yml` unter `messages:` ergänzen
- [ ] `QuestManager.java`: `player.sendMessage(...)` → `MessageUtil.send(player, prefix, configManager.message("quest-..."))` mit Platzhalter-Ersetzung
- [ ] `GuiClickListener.java`: 7 hardkodierte Strings → `configManager.message("...")`
- [ ] `BuildingInteractListener.java`: `TOO_MANY_INSTANCES`-String → `configManager.message("building-max-instances-reached")`
- [ ] `mvn clean package` nach Änderungen

