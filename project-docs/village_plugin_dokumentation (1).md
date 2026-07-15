# Village Plugin – Vollständige Testdokumentation

> Plugin-Version: 1.0.0 · API: Spigot 1.20  
> Soft-Dependencies: Vault, WorldEdit, WorldGuard, Citizens, ProtocolLib, BlueMap

---

## Inhaltsverzeichnis

1. [Permissions-Übersicht](#1-permissions)
2. [Dorf-Grundfunktionen](#2-dorf-grundfunktionen)
3. [Grenz-System](#3-grenz-system)
4. [Gebäude-System](#4-gebäude-system)
5. [Upgrade-System](#5-upgrade-system)
6. [Villager-System](#6-villager-system)
7. [Quest-System](#7-quest-system)
8. [Skill-System](#8-skill-system)
9. [Währungs-System](#9-währungs-system)
10. [Handels-System](#10-handels-system)
11. [Rollen-System](#11-rollen-system)
12. [Dorf-Beziehungen](#12-dorf-beziehungen)
13. [Licht-System](#13-licht-system)
14. [Admin-Befehle](#14-admin-befehle)
15. [Bereichsschutz](#15-bereichsschutz)
16. [Neue Gebäudetypen (block_check / Pfade)](#16-neue-gebäudetypen)
17. [Bekannte Einschränkungen & Testnoten](#17-bekannte-einschränkungen)

---

## 1. Permissions

| Permission | Standard | Beschreibung |
|---|---|---|
| `village.admin` | OP | Voller Admin-Zugriff auf alle Befehle |
| `village.admin.trade` | OP | Umgeht Trade-Timeout und max. Trade-Entfernung |
| `village.create` | true (alle) | Darf ein Dorf gründen |
| `village.manage` | false | Darf Dorf verwalten (Upgrades, Gebäude) |
| `village.bypass` | OP | Umgeht alle Dorf-Einschränkungen (Gebietsschutz) |
| `village.building.general` | – | Alle Gebäude der Kategorie Allgemein |
| `village.building.production` | – | Alle Produktionsgebäude |
| `village.building.defense` | – | Alle Verteidigungsgebäude |

**Setup für Tester:** OP-Status geben oder per `/lp user <name> permission set village.admin true`.


## 4. Gebäude-System

### 4.1 Verfügbare Gebäudetypen (buildings.yml → `buildings.types`)

| Schlüssel | Name | Req. Level | Villager-Slots | Schematic |
|---|---|---|---|---|
| `house` | Wohnhaus | 1 | 2 | house.schem |
| `farm` | Farm | 2 | 1 | farm.schem |
| `shop` | Geschäft | 5 | 1 | shop.schem |
| `barracks` | Kaserne | 8 | 3 | barracks.schem |
| `watchtower` | Wachturm | 6 | 1 | watchtower.schem |
| `wall` | Mauer | 3 | 0 | wall.schem |
| `factory` | Fabrik | 10 | 2 | factory.schem |
| `storage` | Lager | 3 | 1 | storage.schem |
| `road` | Weg | 1 | 0 | road.schem |
| `library` | Bibliothek | 7 | 2 | library.schem |
| `tavern` | Taverne | 4 | 1 | tavern.schem |
| `marketplace` | Marktplatz | – | 3 | marketplace.schem |

### 4.2 Gebäude platzieren

**Voraussetzung:** WorldEdit muss installiert und aktiviert sein. Die `.schem`-Datei muss im Ordner `plugins/Village/schematics/` liegen.

**GUI-Weg (empfohlen):**
1. `/village menu` → Gebäude-Button
2. Gebäudetyp aus Liste wählen
3. Standort per Rechtsklick im GUI bestätigen oder Chat-Koordinaten eingeben
4. Vorschau erscheint → Bestätigung per `/village buildconfirm ja`

**Chat-Bestätigung:**
```
/village buildconfirm ja     → Gebäude platzieren
/village buildconfirm nein   → Abbrechen
```

**Erwartetes Verhalten:**
- Schematic wird an Spieler-Position eingefügt
- Gebäude erscheint als "Im Bau" (`completed = false`)
- WorldGuard-Region wird erstellt (falls WG aktiv)
- Gebäude-Schild wird gesetzt

### 4.3 Gebäude abschließen (Validierung)

Nachdem der Spieler die Schematic-Blöcke gebaut hat (oder Schematic automatisch eingefügt wurde):
- Rechtsklick auf das Gebäude-Schild → Validierungsprüfung
- Bei Erfolg: `Gebäude <Name> wurde fertiggestellt!`
- Gebäude wechselt auf `completed = true`

### 4.4 Gebäude-Befehle

```
/village building list              → Listet alle Gebäude mit Nr., Status, Level
/village building upgrade <Nr>      → Gebäude upgraden (Founder/Admin)
/village building remove <Nr>       → Gebäude entfernen (Founder/Admin)
/village building movesign <Nr>     → Gebäude-Schild neu positionieren
/village building reposition <Nr>   → (Alias für movesign)
```

### 4.5 Gebäude upgraden

**Voraussetzungen:** Gebäude muss `completed = true` sein. Dorf muss das nötige Level und Punkte haben.

**Test-Sequenz:**
1. `/village building list` → Nummer des Gebäudes notieren
2. `/village building upgrade 1` → Plugin prüft Kosten
3. Bei Erfolg: Level wird erhöht, WorldGuard + BlueMap werden aktualisiert
4. Bei Fehler: Meldung mit Grund (Level, Punkte, Status)

---

## 5. Upgrade-System

Dorf-weite Verbesserungen, die mit Punkten und Geld freigekauft werden.

### 5.1 Verfügbare Upgrades

| Schlüssel | Name | Max-Level | Kosten/Level | Punkte/Level | Effekt |
|---|---|---|---|---|---|
| `border-expansion` | Grenzerweiterung | 10 | 500⊕ | 100 | +2.000 Blöcke² Fläche |
| `max-villagers` | Max. Dorfbewohner | – | 50⊕ | 50 | +2 Villager-Slots |
| `production-speed` | Produktionsgeschwindigkeit | 5 | 1.000⊕ | 200 | +10% Produktionsgeschwindigkeit |
| `new-professions` | Neue Berufe | 5 | 750⊕ | 150 | Neue Villager-Jobs freischalten |
| `taxes` | Steuern & Zölle | 3 | 2.000⊕ | 500 | 5% Steuer pro Level |
| `building-unlock` | Gebäude freischalten | 8 | 600⊕ | 120 | Neue Gebäudetypen |
| `max-members` | Max. Spieler | 10 | 400⊕ | 80 | +3 Spieler-Slots |
| `defense` | Verteidigung | 5 | 1.500⊕ | 300 | Verbesserte Verteidigung |
| `house_bed_expansion` | Wohnhaus-Ausbau | 1 | 900⊕ + 150Ɖ | 120 | Wohnhaus-Upgrades freischalten |
| `farm_composting` | Farm-Kompostierung | 1 | 1.100⊕ + 180Ɖ | 140 | Farm-Upgrades freischalten |
| `role_hr` | HR-Rolle | – | – | – | HR-Rolle freischalten |
| `role_baumeister` | Baumeister-Rolle | – | – | – | Baumeister-Rolle freischalten |
| `role_builder` | Builder-Rolle | – | – | – | Builder-Rolle freischalten |
| `role_haendler` | Händler-Rolle | – | – | – | Händler-Rolle freischalten |
| `role_trainer` | Trainer-Rolle | – | – | – | Trainer-Rolle freischalten |

### 5.2 Upgrade kaufen (GUI)

```
/village menu → Upgrades-Button → Upgrade auswählen → Kaufen
```

**Test-Sequenz:**
1. `/village admin addpoints TestDorf 500` → Punkte geben
2. `/village admin money add <Spieler> global 2000` → Geld geben
3. `/village menu` → Upgrades → z.B. "Grenzerweiterung" kaufen
4. Bestätigung: `Upgrade border-expansion wurde freigeschaltet!`

---

## 6. Villager-System

### 6.1 Villager rekrutieren

```
/villager recruit <Name>       → Rekrutiert einen NPCmit Citizens
```

**Voraussetzung:** Citizens muss installiert sein. Spieler muss in einem Dorf sein.

**Test-Sequenz:**
1. `/villager recruit Hans` → NPC "Hans" wird gespawnt
2. NPC erscheint an Spieler-Position
3. `Villager-gespawnt`-Meldung erscheint

### 6.2 Villager-Liste

```
/villager list                 → Listet alle Villager des eigenen Dorfes
/villager list <Dorfname>      → (Admin) Villager eines anderen Dorfes
```

Zeigt: Name · Job · State · Level · Hunger · Happiness

### 6.3 Villager-Info

```
/villager info <Nr>            → Detailinfo (Job, Skills, Needs, Beziehung, XP)
```

### 6.4 Villager entfernen

```
/villager remove <Nr>          → Villager aus Dorf entfernen (Founder/Admin)
```

### 6.5 Villager-Interaktion (GUI)

Rechtsklick auf einen Villager-NPC öffnet `VillagerMenuGui` mit folgenden Optionen:

| Slot | Funktion | Wer darf |
|---|---|---|
| Umbenennen | Chat-Input → neuer Name | Founder, Baumeister |
| Job wechseln | JobSelectionGui öffnen | Founder, Trainer |
| Heimatort setzen | Spieler-Position als Home | Founder |
| Inventar anzeigen | Ressourcen des Villagers | Alle Mitglieder |
| Skilltree | SkillTreeGui öffnen | Alle Mitglieder |
| Quests | QuestMenuGui öffnen | Alle Mitglieder |
| Handel | Trade-UI öffnen | Founder, Händler |
| Transfer | Villager in anderes Dorf | Founder |
| Heilen | Alle Needs auf 100 | Founder |
| Verbannen | Villager entfernen | Founder |

### 6.6 Villager-Jobs

| Job-Enum | Anzeigename | Arbeitsgebäude | Skill-Trees |
|---|---|---|---|
| `FARMER` | Bauer | farm | Anbau, Ernte, Effizienz |
| `MERCHANT` | Händler | marketplace | Verhandlung, Preise, Bestand |
| `GUARD` | Wächter | watchtower | Kampf, Reichweite, Reflexe |
| `LIBRARIAN` | Buchhalter | library | Wissen, Forschung, Speicherung |
| `MILLER` | Müller | mill | Produktion, Geschwindigkeit, Qualität |
| `PRIEST` | Priester | tavern | Heilung, Moral, Zeremonien |
| `LABORER` | Arbeiter | storage | Tragkraft, Geschwindigkeit, Ausdauer |

### 6.7 Villager-States (automatisch)

| State | Auslöser | Verhalten |
|---|---|---|
| IDLE | Standard | Nichts tun, ggf. nach Arbeit wechseln |
| WORKING | Automatisch nach 30–60s | Produziert, XP-Gewinn |
| EATING | Hunger < 20 | Verbraucht Items aus Inventar (Brot, Fleisch, …) |
| SLEEPING | Happiness < 20 | Happiness steigt langsam |
| FLEEING | Feindliche Mobs in 16 Blöcken | Läuft in Richtung Dorfzentrum |
| INTERACTING | Spieler-Interaktion | 10s, dann IDLE |
| GOSSIPING | Zufällig | 20s, dann IDLE |

### 6.8 Villager-Needs (automatisch)

| Need | Decay/Min | Kritisch bei | Folge |
|---|---|---|---|
| HUNGER | 2.0 | 20 | EATING-State, Moral sinkt |
| HAPPINESS | 1.0 | 30 | SLEEPING-State |

---

## 7. Quest-System

### 7.1 Verfügbare Quests (quests-and-villagers.yml)

| Quest-ID | Titel | Req. Level | Täglich | Belohnung |
|---|---|---|---|---|
| `starter-quest-1` | Willkommen! | 1 | nein | 50 Punkte, 100⊕, 10 VillagerXP, 2 Emerald |
| `recruitment-quest` | Neue Bürger | 5 | nein | 100 Punkte, 250⊕, 20 VillagerXP (Voraussetzung: starter-quest-1) |
| `trade-quest` | Handel lernen | 3 | **ja** | 25 Punkte, 75⊕, 5 VillagerXP |
| `farming-mission` | Ernte | 2 | **ja** | 30 Punkte, 50⊕, 8 VillagerXP |

### 7.2 Quest-Befehle

```
/villager quest list           → Zeigt verfügbare und aktive Quests
/villager quest accept <ID>    → Quest annehmen
/villager quest complete <ID>  → Quest abschließen (nach Erfüllung)
```

### 7.3 Quest-Test-Sequenz

1. Spieler muss Dorf-Mitglied sein
2. `/villager quest list` → starter-quest-1 angezeigt
3. `/villager quest accept starter-quest-1`
4. 10× OAK_LOG sammeln
5. `/villager quest complete starter-quest-1`
6. Belohnungen: Punkte, Geld (Vault), Items ins Inventar

### 7.4 Tägliche Quests

- Können 1× pro Tag abgeschlossen werden
- Reset nach Mitternacht (Server-Zeit)
- Test: `trade-quest` zweimal an einem Tag abschließen → zweiter Versuch abgelehnt

---

## 8. Skill-System

### 8.1 Verfügbare Skills (quests-and-villagers.yml → `skills`)

| Skill | Max-Level | Kosten/Level | Beschreibung |
|---|---|---|---|
| `anbau` | 20 | 100⊕ | Anbau-Effizienz |
| `ernte` | 20 | 100⊕ | Ernte-Ausbeute |
| `effizienz` | 20 | 150⊕ | Bearbeitungszeit |
| `verhandlung` | 15 | 200⊕ | NPC-Handel |
| `preise` | 15 | 150⊕ | Preisgestaltung |

### 8.2 Skill-Befehle

```
/villager skill list <Nr>      → Zeigt Skills eines Villagers
/villager skill upgrade <Nr> <Skill>  → Skill upgraden (kostet Geld)
```

### 8.3 Skill-GUI (Rechtsklick auf Villager)

SkillTreeGui zeigt alle verfügbaren Skill-Trees des aktuellen Jobs. Klick auf einen Skill → Level erhöhen, Kosten werden abgezogen.

---

## 9. Währungs-System

Das Plugin nutzt **zwei Währungsebenen**:

| Ebene | Name | Symbol | Scope | Beschreibung |
|---|---|---|---|---|
| Global | Goldmünze | ⊕ / `[GP]` | Server-weit | Via Vault, für dorfübergreifenden Handel |
| Lokal | \{Dorfname\}-Taler | Ɖ / `[DT]` | Dorf-intern | Via OpenEco, nur innerhalb des Dorfes |

### 9.1 Balance-Befehle

```
/balance                       → Eigenes globales Guthaben
/balance <Spieler>             → Guthaben eines anderen Spielers (Admin)
/balances                      → Rangliste aller Guthaben
/village balance               → Dorf-Guthaben (lokal + global)
/village balances              → Alle Spieler-Guthaben im Dorf
```

### 9.2 Geld senden

```
/sendmoney <Spieler> <Betrag>  → Globales Geld senden
/village sendmoney <Spieler> <Betrag> [global|lokal]
```

**Test-Sequenz:**
1. `/sendmoney SpielerB 100` → Bestätigungsfrage
2. `/village sendmoneyconfirm ja` → Überweisung
3. Beide Spieler erhalten Meldung

### 9.3 Währungskonversion

- 100 lokale Taler → 80 Goldmünzen (20% Maklergebühr, konfigurierbar)
- Mindestbetrag: 10 Taler
- Konversion per `/trade` → Menü-Option

### 9.4 Startwerte

- Neue Spieler: 100 Goldmünzen
- Neues Dorf-Mitglied: 100 lokale Taler + 50 Villager-Taler

---

## 10. Handels-System

### 10.1 Trade-Befehl Übersicht

```
/trade balance                 → Aktuelles Guthaben (lokal + global)
/trade local                   → Lokaler Villager-Handel (nur Dorf-Mitglieder)
/trade external                → Externer Villager-Handel (andere Dörfer, globale Währung)
/trade player <Spieler>        → Spieler-zu-Spieler-Handel
/trade blocked                 → Geblockte Handelspartner anzeigen
/trade shop                    → Marktplatz öffnen
/trade history                 → Handelshistorie der letzten Transaktionen
```

### 10.2 Lokaler Villager-Handel (`/trade local`)

**Voraussetzung:** Spieler ist Dorf-Mitglied, Villager hat Angebote im Inventar.

**Test-Sequenz:**
1. `/trade local` → GUI öffnet sich mit verfügbaren Villager-Angeboten
2. Item auswählen → Preis in lokaler Währung angezeigt
3. Kaufen-Button → lokale Taler werden abgezogen
4. Item erscheint im Spieler-Inventar

### 10.3 Externer Villager-Handel (`/trade external`)

- Spieler außerhalb des Dorfes zahlen mit **globaler Währung** (Goldmünzen)
- Nur Angebote die mit `global`-Flag markiert sind, sind sichtbar

### 10.4 Spieler-zu-Spieler-Handel (`/trade player <Spieler>`)

**Test-Sequenz:**
1. Spieler A: `/trade player SpielerB`
2. Beide erhalten Trade-GUI (ähnlich Vanilla-Tausch)
3. Beide legen Items / Währung in ihre Seite
4. Beide klicken "Bereit" → Handel abgeschlossen
5. Items werden getauscht, Transaktion in History gespeichert

### 10.5 Marktplatz (`/trade shop`)

Öffnet den dorfübergreifenden Marktplatz. Spieler können Angebote einstellen und kaufen.

### 10.6 Block-Funktionalität

- Spieler kann andere Spieler vom Handel blocken
- Geblockte Spieler können keine Trade-Anfragen stellen
- `/trade blocked` zeigt die eigene Block-Liste

---

## 11. Rollen-System

### 11.1 Verfügbare Rollen

| Rolle | Enum | Rechte |
|---|---|---|
| Gründer | `FOUNDER` | Alle Rechte, kann nicht verlassen |
| HR | `HR` | Mitglieder verwalten, Rollen zuweisen |
| Baumeister | `BAUMEISTER` | Gebäude verwalten und platzieren |
| Builder | `BUILDER` | Auf Baustellen bauen |
| Händler | `HAENDLER` | Mit Villagern handeln |
| Trainer | `TRAINER` | Villager upgraden |
| Mitglied | `MEMBER` | Basis-Zugriff |

### 11.2 Rolle zuweisen

```
/village promote <Spieler> <Rolle>
/village befoerdern <Spieler> <Rolle>
```

**Rollen-Schlüsselwörter:** `HR`, `Baumeister`, `Builder`, `Haendler`, `Trainer`, `Mitglied`

**Test-Sequenz:**
1. Spieler B ist MEMBER im Dorf
2. Spieler A (FOUNDER): `/village promote SpielerB HR`
3. Spieler B kann jetzt Beitrittsanfragen bearbeiten

### 11.3 Rollen-Upgrades freischalten

Rollen (außer FOUNDER/MEMBER) müssen als Dorf-Upgrade freigekauft werden:

| Upgrade-Key | Freischaltet |
|---|---|
| `role_hr` | HR-Rolle vergeben |
| `role_baumeister` | Baumeister-Rolle |
| `role_builder` | Builder-Rolle |
| `role_haendler` | Händler-Rolle |
| `role_trainer` | Trainer-Rolle |

---

## 12. Dorf-Beziehungen

Dörfer können diplomatische Beziehungen eingehen.

### 12.1 Beziehungstypen

| Typ | Enum | Beschreibung |
|---|---|---|
| Freundschaft | `FRIENDSHIP` | Gegenseitige Freundschaft |
| Handel | `TRADE` | Handelsbündnis |
| Krieg | `WAR` | Kriegszustand |
| Durchgangssperre | `CURFEW` | Keine Durchfahrt |

### 12.2 Beziehungszustände

| Zustand | Beschreibung |
|---|---|
| NONE | Keine Beziehung |
| REQUESTED | Anfrage gestellt |
| ACTIVE | Aktiv |
| PENDING_PEACE | Friedensanfrage offen |

### 12.3 Verwaltung

Beziehungen werden im Dorf-Menü (`/village menu`) verwaltet. Aktuell noch kein dedizierter Befehl – über GUI erreichbar.

---

## 13. Licht-System

Das Plugin kann Lichtlevels für Spieler außerhalb von Dörfern begrenzen (via ProtocolLib).

### 13.1 Funktionsweise

- Spieler außerhalb der Dorfgrenze: Licht wird mit zunehmender Entfernung gedimmt
- Distanzstufen (konfigurierbar in `light-limits.yml`):

| Entfernung | Max-Lichtlevel |
|---|---|
| 0 Blöcke (Dorfgrenze) | 15 (voll) |
| 10 Blöcke außerhalb | 14 |
| 50 Blöcke außerhalb | 10 |
| 100 Blöcke außerhalb | 5 |
| 110+ Blöcke | 3 (Grundhelligkeit) |

### 13.2 Test

**Voraussetzung:** ProtocolLib installiert, `light-control.enabled: true` in `light-limits.yml`.

1. Dorf mit Grenze erstellen
2. Innerhalb der Grenze: normales Licht
3. Grenze überschreiten → Licht dimmt schrittweise
4. 100+ Blöcke entfernt → Lichtlevel 5

### 13.3 Reload

```
/village reload                → Lädt config.yml und light-limits.yml neu
```

---

## 14. Admin-Befehle

Alle Admin-Befehle erfordern `village.admin`.

```
/village admin setlevel <Dorf> <Level>
   → Setzt das Level eines Dorfes direkt

/village admin addpoints <Dorf> <Punkte>
   → Gibt einem Dorf Punkte

/village admin money add <Ziel> <Währung> <Betrag>
/village admin money remove <Ziel> <Währung> <Betrag>
   → Ziel: Spielername | "all" | "village:Dorfname"
   → Währung: "global" | Dorfname (für lokale Währung)
   → Beispiel: /village admin money add SpielerA global 1000

/village admin delete <Dorf>
   → Löscht ein Dorf ohne Bestätigung

/village admin saveall
   → Speichert alle Dörfer sofort

/village admin resyncwg
   → Synchronisiert alle WorldGuard-Regionen neu

/village reload
   → Lädt Konfiguration neu (inkl. Light-System)
```

### 14.1 Admin Money – Test-Sequenz

```
/village admin money add SpielerA global 500        → +500 Goldmünzen
/village admin money remove SpielerA global 100     → -100 Goldmünzen
/village admin money add all global 50             → Alle Spieler +50
/village admin money add village:TestDorf global 200 → Dorfkasse +200
```

---

## 15. Bereichsschutz

### 15.1 Automatischer Schutz

Innerhalb der Dorfgrenzen gilt:
- Kein Bauen/Abbauen/Interagieren für Nicht-Mitglieder (wenn WorldGuard aktiv)
- Grenzbereiche mit Besitzern: nur Besitzer und Gründer dürfen interagieren
- Grenzbereiche ohne Besitzer: alle dürfen interagieren

### 15.2 Test-Sequenz Bereichsschutz

1. Dorf mit Grenze erstellen
2. Mit einem anderen Spieler (Nicht-Mitglied) in den Bereich gehen
3. Versuch Block zu setzen → abgebrochen, Fehlermeldung: `Du hast hier keine Rechte`
4. Spieler dem Dorf hinzufügen → Bauen jetzt möglich

### 15.3 Glocken-Schutz

```
BellProtectionListener: Schützt die Dorf-Glocke vor Zerstörung
```
Nicht-Mitglieder können die Gründungsglocke nicht zerstören.

---

## 16. Neue Gebäudetypen (block_check / Pfade)

*(Implementiert im BuildingConfigLoader-System – separate von `buildings.types`)*

### 16.1 Block-Check-Gebäude

Diese Gebäude benötigen keine Schematic. Stattdessen müssen bestimmte Blöcke im Bereich vorhanden sein.

**Beispiele aus buildings.yml (`categories`):**

| Gebäude | Validierung | Voraussetzung |
|---|---|---|
| `acker` | block_check | Mindestens 16 FARMLAND + 40% der Fläche |
| `tierzucht` | block_check | Mindestens 16 OAK_FENCE |
| `fischerei` | block_check | Mindestens 20 WATER |
| `baracke` | block_check | Hohlraum ≥ 8 Luftblöcke |

### 16.2 Pfad-Gebäude (Walk-Session)

```
/village path start            → Pfad-Aufzeichnung starten
/village path done             → Abschließen und validieren
/village path cancel           → Abbrechen
```

**Trampelpfad-Test:**
1. `/village path start` → Meldung: `Pfad-Aufzeichnung gestartet!`
2. Mittellinie der Strecke ablaufen (3 Blöcke breit werden automatisch erfasst)
3. Randblöcke werden mit gelben Betonpulver-Blöcken visualisiert (temporär)
4. 60% der Oberfläche muss DIRT_PATH sein
5. `/village path done` → Validierungsprüfung
6. Erfolg: Speed-I-Bonus beim Betreten (60 Ticks Nachlauf nach Verlassen)

**Straßen-Test:**
1. Wie Trampelpfad, aber Oberfläche muss 70% COBBLESTONE sein
2. Breite upgradeable auf 5 Blöcke
3. Speed-II-Bonus (120 Ticks Nachlauf)

### 16.3 Truhen-System (BuildingChestManager)

Gebäude mit `chest`-Konfiguration haben verwaltete Truhen:

- **Startkapazität:** 1×9 (9 Slots)
- **Max. Kapazität:** 6×9 (54 Slots) nach 5 Upgrades
- **Zugriff:** PUBLIC (alle Mitglieder), PRIVATE (nur Besitzer), VILLAGER_ONLY

Rechtsklick auf Truchen-Workstation-Block → Truhe öffnet sich (Access-Check).

### 16.4 Produktionssystem (ProductionService)

Gebäude mit `recipes` produzieren automatisch alle 5 Sekunden:

**Test-Sequenz:**
1. `acker`-Gebäude (block_check) registrieren
2. WHEAT_SEEDS in öffentliche Dorftruhe legen
3. FARMER-Villager dem Gebäude zuweisen
4. Nach 180 Sekunden: WHEAT erscheint in Gebäude-Truhe

---

## 17. Bekannte Einschränkungen & Testnoten

### Abhängigkeiten

| Funktion | Benötigt | Verhalten ohne Abhängigkeit |
|---|---|---|
| Schematic-Gebäude | WorldEdit | Gebäude nicht baubar |
| Bereichsschutz | WorldGuard | Kein automatischer Schutz |
| Villager-NPCs | Citizens | `/villager recruit` nicht verfügbar |
| Licht-System | ProtocolLib | Kein Licht-Dimming |
| BlueMap-Marker | BlueMap | Keine Karten-Einträge |
| Globale Währung | Vault | Kein Geld-System |
| Lokale Währung | OpenEco | Nur globale Währung verfügbar |

### Bekannte Bugs / Limitierungen

- **Grenzziehung:** Diagonalbewegungen werden während der Walk-Session abgebrochen. Immer in Himmelsrichtungen laufen (N/S/O/W).
- **Schematic-Validierung:** Wenn `.schem`-Datei fehlt, ist Gebäude-Platzierung trotzdem möglich, aber ohne BlueMap-Marker (seit v5 kein Crash mehr).
- **Villager-KI:** Derzeit eher Framework – vollständiges Wegfinde- und Tagesablaufverhalten hängt von Citizens-Integration ab.
- **Truhen-GUI:** Virtuelle Truhen (ohne echten Chest-Block) synchronisieren sich beim Schließen in `chest-data.yml`.
- **Produktionssystem:** Läuft asynchron. Output wird synchron in Bukkit-Haupt-Thread eingelagert.

### Test-Reihenfolge (empfohlen)

```
1. Dorf gründen                (Bell + Brunnen)
2. Grenze setzen               (Walk oder Koordinaten)
3. Mitglied einladen           (join/invite)
4. Admin-Geld geben            (/village admin money add)
5. Upgrade kaufen              (border-expansion)
6. Gebäude platzieren          (house, benötigt WorldEdit)
7. Gebäude upgraden            (/village building upgrade 1)
8. Villager rekrutieren        (/villager recruit, benötigt Citizens)
9. Villager-Quest             (/villager quest list / accept / complete)
10. Handel testen              (/trade local / player)
11. Währung senden             (/sendmoney)
12. Licht-System prüfen        (Grenze verlassen, ProtocolLib)
13. Bereichsschutz prüfen      (Nicht-Mitglied versucht zu bauen)
```
