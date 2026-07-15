# Village Plugin Schematics Guide

## Schematics erstellen

Verwende WorldEdit um Schematics zu erstellen:

1. **Auswahl treffen**: `//pos1` und `//pos2` setzen (oder `//sel cuboid`)
2. **Schematic speichern**: `//schem save house.schem`
3. **Datei platzieren**: Kopiere die .schem Datei in den `resources/schematics/` Ordner
4. **Plugin neu kompilieren**: `sdk use java 21.0.6-tem && mvn clean package`

## Standard-Schematics

Das Plugin wird automatisch folgende Schematics aus dem JAR extrahieren (falls vorhanden):

- `house.schem` - Standard Wohnhaus
- `farm.schem` - Farm
- `shop.schem` - Shop
- `barracks.schem` - Kaserne
- `watchtower.schem` - Wachturm
- `wall.schem` - Mauer
- `factory.schem` - Fabrik
- `storage.schem` - Lager
- `road.schem` - Straße
- `library.schem` - Bibliothek
- `tavern.schem` - Taverne
- `marketplace.schem` - Marktplatz

## Wichtig: Echte Schematics müssen hinzugefügt werden

Die mitgelieferten `.schem.txt` Dateien sind **PLATZHALTER** und keine echten Schematics!

Um echte Schematics zu nutzen:

1. Erstelle Strukturen mit WorldEdit in deiner Minecraft-Welt
2. Speichere sie mit `//schem save <name>.schem`
3. Kopiere die echten `.schem` Dateien in `resources/schematics/`
4. **Ersetze die `.schem.txt` Dateien** mit den echten `.schem` Dateien
5. Kompiliere das Plugin neu

## Plugin-Verhalten

Beim Start von VillagePlugin:

1. Der `schematics/` Ordner wird erstellt (falls nicht vorhanden)
2. WorldEdit-Schematics aus dem JAR werden extrahiert
3. Bereits vorhandene Dateien werden **NICHT überschrieben** (Admins können ihre eigenen verwenden)
4. Fehlende Schematics werden protokolliert, aber das Plugin lädt trotzdem

## Schematics in der config.yml konfigurieren

```yaml
buildings:
  house:
    display-name: "Wohnhaus"
    cost: 5000
    required-level: 1
    schematic: "house.schem"  # Dateiname OHNE Pfad
  farm:
    display-name: "Farm"
    cost: 3000
    required-level: 1
    schematic: "farm.schem"
```

Das Plugin sucht automatisch im `schematics/` Ordner nach der Datei.

## Richtungsunterstützung

Schematics werden basierend auf der ausgewählten Richtung automatisch rotiert:
- **N (Nord)**: Keine Rotation
- **E (Ost)**: 90° Rotation
- **S (Süd)**: 180° Rotation
- **W (West)**: 270° Rotation


