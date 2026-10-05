# Mamas Telefon

Eine einfache Startseite für Android-Handys, gemacht für einen Menschen, der nur telefonieren möchte.

- **Große Uhr** mit Wochentag, Uhrzeit, Tageszeit (Morgen, Mittag, Abend …) und Datum
- **Foto-Kacheln** zum Anrufen mit einem Tipp
- **Nachfrage vor dem Anruf** („Anna anrufen? Ja / Nein“), abschaltbar
- **Akku-Hinweis**, wenn das Handy aufgeladen werden muss
- **Lautstärke-Schutz**: Die Lautstärketasten sind gesperrt. Lautlos, Vibration und „Nicht stören“ werden sofort wieder ausgeschaltet.
- **Einstellungen mit PIN**: auf der Startseite **5× schnell auf die Uhr tippen**

Keine Werbung, kein Konto und keine Internetverbindung. Alle Kontakte und Fotos bleiben auf dem Handy.

## Installieren

1. Auf dem Handy diesen Link öffnen:
   `https://github.com/Marissa-stack/mamas-telefon/releases/latest/download/Mamas-Telefon.apk`
2. Herunterladen und antippen. Beim ersten Mal fragt Android, ob der Browser Apps installieren darf: **erlauben**.
3. **Installieren** und danach **Öffnen**.
4. **Einrichtung starten**: PIN festlegen, dann alle Punkte unter „1. Einrichtung“ auf grün bringen.
5. Kontakte hinzufügen. Fotos werden aus dem Telefonbuch übernommen oder lassen sich aus der Galerie wählen.

## Update

Den gleichen Link wie oben öffnen und die neue Version über die alte installieren. Einstellungen und Kontakte bleiben erhalten.

## PIN vergessen?

Handy-Einstellungen → Apps → Mamas Telefon → Speicher → **Daten löschen**. Danach ist alles zurückgesetzt, und die Einrichtung startet neu.

## Grenzen

- Den Lautstärkeregler im Schnellmenü kann keine normale App ausblenden. Die App stellt die Lautstärke aber sofort wieder zurück.
- Wenn es klingelt, schaltet ein Druck auf die Lautstärketaste bei Android den Klingelton *dieses einen* Anrufs stumm. Das kann eine App nicht verhindern. Die eingestellte Lautstärke bleibt dabei aber gleich.

## Technisches

Android 8 oder neuer, Java, keine Fremdbibliotheken. Jeder Push baut die App über GitHub Actions und veröffentlicht sie als Release.
