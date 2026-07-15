# Offene Aufgaben – Village Plugin

> Stand: 15.07.2026  
> Zweck: Nur noch die aktuelle, offene Folgearbeit zur Weiterentwicklung des Plugins.

---

## Aktuelle offene Arbeit

### 1. Modulare Gebäude-Erweiterung

Die verbleibende größere Folgearbeit ist die Erweiterung des Gebäude-Upgrades um modulare Zusatzschematics:

1. In `.schem.meta` benannte Anker definieren (`attach_at`, z. B. `roof_center`).
2. Upgrade-Konfiguration in `buildings.yml` mit `modular_extensions` ergänzen.
3. Die Module im `BuildingService`-Upgrade-Pfad an den benannten Anker einhängen.
4. Zusätzliches Testen mit WorldEdit-Schematic und einem aktiven Upgrade-Flow.

### 2. Optionales Terrain-Feature

- Adaptives Dach bei starkem Terrain-Gefälle

### 3. Admin-/Tester-Check

- Ingame-Smoke-Test für neue Upgrade-/Anker-Logik nach jeder Konfig-Änderung

---

## Hinweis

Abgeschlossene Todos, historische Bug-Listen und alte Projekt-Statusnotizen wurden aus diesem Dokument entfernt. Für interne Architektur- und Projekt-Referenzen siehe [`../project-docs/README.md`](../project-docs/README.md).
