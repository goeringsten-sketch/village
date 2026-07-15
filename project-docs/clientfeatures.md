# Client Features

Diese Liste sammelt Funktionen, die mit reinem Server-Code nur eingeschränkt oder gar nicht sauber umsetzbar sind.

## Schild-Placeholder-Editor

- Gewünschtes Verhalten: Ein Klick auf einen Platzhalter fügt ihn am aktuellen Cursor im Chat-Input ein.
- Aktueller Stand: Der Platzhalter wird per `suggest_command` als kompletter neuer Text vorgeschlagen und technisch ans Ende der Vorlage angehängt.
- Grund: Minecraft stellt für normale Chat-Klicks keinen zuverlässigen Server-API-Zugriff auf die Cursorposition im Texteingabefeld bereit.

## Hinweise

- Verfügbare interne Platzhalter für Gebäudeschilder sind aktuell `%building_type%`, `%building_name%`, `%owner%` und `%level%`.
- PlaceholderAPI-Variablen werden zur Laufzeit im Schildtext ersetzt, können aber nicht alle einzeln als Klick-Helfer angeboten werden.
