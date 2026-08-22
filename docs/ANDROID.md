# Die Android-Hülle

Eine App, die **nichts weiter tut**, als die Webseite anzuzeigen und einen Wecker zu
stellen. Sie kennt keine Aufgaben, keine Datenbank und keine Regeln.

## Warum es sie gibt

Eine Webseite kann sich nicht selbst wecken. Ohne Server und ohne Konto kann niemand einen
geschlossenen Tab um acht Uhr morgens aufwecken — ein Wecker im Betriebssystem kann es.

Das ist **kein Push**: Es gibt keinen Server, der etwas schickt, kein Konto und keine
Google-Dienste. Das Telefon weckt sich selbst zu Zeitpunkten, die die Webseite ausgerechnet
hat. Für den Nutzer sieht es gleich aus; für die Daten ist es der Unterschied zwischen
„liegt auf meinem Gerät“ und „liegt bei jemand anderem“.

## Wer was entscheidet

```
Webseite                          Hülle
--------                          -----
rechnet aus, wann gemahnt wird →  stellt Wecker
                                  klingelt, meldet
zählt die Eskalation nach     ←   schreibt auf, dass gemeldet wurde
hakt ab, verschiebt           ←   schreibt auf, was angetippt wurde
```

Die Hülle rechnet nichts. Stünde hier eine zweite Fassung der Regeln, wäre sie nach dem
ersten Wochenende von der ersten abgewichen — und die zweite wäre immer die falsche.

## Was die App nicht darf

**Die `INTERNET`-Berechtigung gibt es nur für den Geräteübergreifend-Abgleich**, und auch
den nur, wenn er in den Einstellungen eingeschaltet ist (siehe `docs/SYNC.md`). Der eigene
Ursprung geht dabei nie ins Netz: Jede Anfrage dorthin beantwortet `Vermittler.kt` aus dem
Paket, geladen wird die Webseite selbst also immer nur von dort. Was die Berechtigung
öffnet, ist ausschließlich der Weg, den die Seite selbst für den Abgleich geht — kein
Nachladen von Schrift, Skript oder irgendetwas anderem, kein Konto, keine Telemetrie.

Damit der Abgleich in der Hülle etwas findet, muss in den Einstellungen die **Adresse der
Ablage** eingetragen sein — der Ursprung der Hülle selbst ist keine echte Adresse im Netz,
„leer lassen“ träfe dort ins Leere.

Geladen wird sie über `https://appassets.androidplatform.net/web/…`, nicht über `file://`.
Der Grund ist nicht Schönheit: Eine `file://`-Adresse ist kein sicherer Ursprung, und ohne
sicheren Ursprung gibt es kein IndexedDB — also keine Daten.

**Der Ursprung darf sich nie ändern.** Er ist der Schlüssel, unter dem die Datenbank liegt.
Wer ihn austauscht, hat die Daten aller Nutzer weggeworfen.

## Bauen

```
cd android
./gradlew assembleRelease
```

Das Paket liegt danach in `app/build/outputs/apk/release/`. Es ist mit dem
Debug-Zertifikat signiert — die App wird seitlich installiert, nicht über einen Laden
verteilt.

Der Pfad zum SDK gehört in `android/local.properties` (nicht eingecheckt):

```
sdk.dir=/pfad/zum/android-sdk
```

Die Webseite wird beim Bauen aus dem Wurzelverzeichnis kopiert. Es gibt **eine** Fassung
der Webseite; im Android-Ordner liegt keine zweite.

## Prüfen

```
cd android && ./gradlew test    # die reine Kotlin-Logik
npm run bruecketest             # den Vertrag zwischen beiden Seiten, im Browser
```

In dieser Entwicklungsumgebung läuft **kein Emulator** — für KVM fehlt die Berechtigung.
Deshalb steht in der Hülle so wenig Logik wie möglich, und der Vertrag zwischen Webseite
und Hülle wird im Browser gegen eine nachgebaute Hülle geprüft. Was sich nur auf einem
Telefon zeigt, zeigt sich nur auf einem Telefon: die erste Installation gehört ausprobiert.

## Umzug aus dem Browser

Die App hat ihren eigenen Speicher. Wer die Webseite schon im Browser benutzt hat, holt
seine Daten so herüber:

1. Im Browser: Einstellungen → **Sicherung herunterladen**
2. In der App: Einstellungen → **Sicherung einlesen**

Die Sicherung fügt zusammen, statt zu überschreiben — zweimal einlesen schadet nicht.
