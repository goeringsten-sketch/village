# Feature Request: Quest-System-Erweiterungen

**Datum:** 2026-07-02  
**Bereich:** Quest-System & Gameplay-Progression  
**Priorität:** Mittel (Phase 2)

---

## Zusammenfassung

Das aktuelle Quest-System unterstützt folgende Objective-Typen:
- `collect-items` ✅ (Items sammeln)
- `feed-villagers` ✅ (Villager füttern)
- `trade` ✅ (Handelsvorgänge)
- `recruit-villager` ✅ (Villager rekrutieren)

**Gewünscht sind zusätzlich:**
1. Gebäude-Freischaltungs-Quests (z.B. "Errichte Farm" → Farm freigeschaltet)
2. Upgrade-Freischaltungs-Quests (z.B. "Sammle 500 Dorfpunkte" → Upgrade freigeben)
3. Permanente/temporäre Bonusstat-Quests für Villager-Jobs
4. Dorfpunkte-basierte Quests

---

## Feature 1: Gebäude-Unlock-Quest

**Use Case:**
- Spieler muss "Farm" erfolgreich bauen
- Danach wird "Marktplatz" Quest-gebunden freigegeben
- Belohnung: Marktplatz-Bauerlaubnis, Dorfpunkte, Geld

**Benötigte Änderungen:**
- Neuer Objective-Type: `build-building`
- Quest-Konfiguration:
  ```yaml
  unlock-marketplace:
    title: "Marktplatz erschließen"
    objective-type: build-building
    required-building: marketplace
    objective-count: 1
    reward:
      unlock-building: marketplace
  ```
- Code: `QuestManager.recordBuildingCompletion(Player, BuildingType)`
- Listener: `BuildingService.finalizeBuildingPlacement()` → Aufruf zum Fortschritt

**Aufwand:** Mittel (~3h)

---

## Feature 2: Upgrade-Unlock-Quest

**Use Case:**
- Quest: "Sammle 500 Dorfpunkte" (über mehrere andere Quests)
- Belohnung: Freischaltung von `production-speed`-Upgrade
- GUI sollte diese Quests klar als "Upgrade-Voraussetzung" anzeigen

**Benötigte Änderungen:**
- Neue Quest-Belohnung: `unlock-upgrade`
- Upgrade-System: Vor Kauf prüfen, ob Quest abgeschlossen ist
- Config:
  ```yaml
  boost-production:
    title: "Produktionssteigerung freischalten"
    objective-type: collect-items  # Dorfpunkte als verstecktes Item
    reward:
      unlock-upgrade: production-speed
  ```
- Code: `UpgradeService.purchaseUpgrade()` → Prüfe `questManager.hasCompletedQuest()`

**Aufwand:** Mittel (~2-3h)

---

## Feature 3: Permanente/Temporäre Villager-Bonusstat-Quest

**Use Case:**
- Quest: "Trainiere 3 Bauern auf Level 5"
- Belohnung: +10% Baugeschwindigkeit für alle Bauern (permanent)
  - Alternativ: +20% für 7 Tage (temporär mit Timer)
- Oder: "+10% Production-Speed für einen spezifischen Villager (z.B. Farmer-Name)"

**Benötigte Änderungen:**
- Neuer Reward-Typ: `job-bonus` oder `villager-effect`
- Effekt-System muss Quest-belohnete Effekte speichern/laden
- Config:
  ```yaml
  farmer-excellence:
    title: "Baumeister-Exzellenz"
    objective-type: some-job-quest  # noch zu definieren
    reward:
      job-bonus:
        job: BAUER
        effect-key: build-speed
        bonus: 0.10
        duration-days: 7  # 0 = permanent
  ```
- Persistierung: `quest-progress.yml` → Bonus-Einträge speichern
- Code: `StateEngine`, `NutrientEffectService` → Quest-Boni laden & anwenden

**Aufwand:** Hoch (~6h) – erfordert neue Persistierungs-Logik

---

## Feature 4: Dorfpunkte-basierte Quest

**Use Case:**
- Quest: "Sammle 100 Dorfpunkte insgesamt"
- Problem: Dorfpunkte sind keine Items → `collect-items` funktioniert nicht
- Lösung: Neuer Objective-Type `collect-village-points`

**Benötigte Änderungen:**
- Neuer Objective-Type: `collect-village-points`
- Progress-Tracking: `VillageManager` → Aufruf beim Punktegewinn
- Config:
  ```yaml
  ambitiousness:
    title: "Ehrgeiz"
    objective-type: collect-village-points
    objective-count: 100
    reward:
      village-points: 200
      money:
        global: 1000
  ```
- Code: `QuestManager.recordVillagePointsProgress(Village, int pointsGained)`

**Aufwand:** Klein (~1-2h)

---

## Implementierungs-Roadmap

### Phase 2.1 (Kurzfristig, ~3-4h)
- Feature 4: `collect-village-points` Objective
- Earlygame-Quests verfeinern (aktuell bereits in Config, aber unvollständig)

### Phase 2.2 (Mittelfristig, ~5-6h)
- Feature 1: `build-building` Objective
- Feature 2: `unlock-upgrade` Belohnungs-System

### Phase 2.3 (Längerfristig, ~6-8h)
- Feature 3: Permanente/temporäre Job-Boni
- Persistierungs-Mechanik für Quest-Boni
- GUI-Anpassungen zur Anzeige von Quest-basierten Effekten

---

## Geschätzte Gesamtarbeit

- **Kurzfristig:** 3–4 Stunden
- **Mittelfristig:** 5–6 Stunden
- **Langfristig:** 6–8 Stunden
- **Total:** ~15–18 Stunden (über mehrere Iterationen)

---

## Abhängigkeiten & Risiken

- **Abhängigkeiten:** Existing Quest-System ist stabil, kann ohne Bruch erweitert werden
- **Risiken:**
  - Persistierung von Quest-Boni könnte Race-Conditions verursachen → Braucht Lock-Mechanismus
  - Job-Boni müssen in `StateEngine` korrekt gewichtet werden, um nicht zu overpowered zu sein
  - Dorfpunkte-Quest könnte zu Farm-Mechanik führen → Balance-Check nötig

---

## Tests & Validierung

- Unit-Tests für neue Objective-Types
- Ingame-Test mit allen Earlygame-Quests
- Balance-Check: Sind Belohnungen sinnvoll ohne zu dominant zu sein?

---

## Nächste Schritte

1. Feature 4 implementieren (~2h) → Sofort einsatzbereit
2. Earlygame-Quest-Runde 1 testen (aktuell in Config)
3. Feedback sammeln vor Features 1–3
