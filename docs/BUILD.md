# Bauen und Signieren

## Voraussetzungen

- JDK 17 oder neuer (die App zielt auf Bytecode 17)
- Android SDK mit Platform 36 und Build-Tools 36; Pfad in `local.properties`:
  ```
  sdk.dir=/pfad/zum/android-sdk
  ```

## Debug-Build

```
./gradlew assembleDebug
```

Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`, signiert mit dem
Android-Debug-Schlüssel. Installierbar per Sideload, gut zum Ausprobieren — aber **nicht**
zum Aufbauen echter Daten: Der Debug-Schlüssel unterscheidet sich vom späteren
Release-Schlüssel, und Android erlaubt kein Update über eine andere Signatur hinweg. Der
Wechsel kostet dann eine Deinstallation samt allen Aufgaben.

## Release-Build mit eigenem Schlüssel

Einmalig einen Schlüssel erzeugen (25 Jahre Laufzeit, Passwörter selbst vergeben):

```
keytool -genkeypair -v \
  -keystore petodo-release.jks \
  -alias petodo \
  -keyalg RSA -keysize 4096 -validity 9125
```

Danach `keystore.properties` im Projektwurzelverzeichnis anlegen:

```
storeFile=petodo-release.jks
storePassword=…
keyAlias=petodo
keyPassword=…
```

Beides ist in `.gitignore` ausgenommen und gehört **nicht** ins Repository. Der Schlüssel
ist unersetzlich: Ohne ihn lässt sich keine installierte App mehr aktualisieren.

```
./gradlew assembleRelease
```

Ergebnis: `app/build/outputs/apk/release/app-release.apk`.

Fehlt `keystore.properties`, läuft der Release-Build trotzdem durch — das Ergebnis ist
dann unsigniert und heißt `app-release-unsigned.apk`. Ein fehlender Schlüssel darf den
Build nicht verhindern.

## Tests

```
./gradlew test
```

JVM-Unit-Tests für alles unter `domain/`. Keine UI-Tests, kein Emulator nötig.

## Verteilung

Sideload-APK, kein Play Store. Vorgesehener Weg: GitHub Releases plus Obtainium, damit
Updates ohne Store ankommen.
