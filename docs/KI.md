# Die Schnittstelle für eine KI

Ein Chat, der deine Aufgaben liest, welche anlegt und abhakt — und das ganze Vault als
Markdown-Datei holt, damit es in Obsidian aktuell bleibt.

Es läuft **nichts auf deinem Rechner**. Alles hängt am selben Cloudflare-Worker, der auch
die Webseite ausliefert. Einzurichten ist eine Zeile.

## Was das kostet — vorweg, nicht im Kleingedruckten

Der Abgleich zwischen deinen Geräten ist Ende zu Ende verschlüsselt: Der Server bekommt
einen Klumpen Bytes und kann nichts damit anfangen.

**Für diese Schnittstelle gilt das nicht.** Sie muss den Klumpen aufmachen, also braucht
der Worker den Schlüssel. Er kommt bei jeder Anfrage mit, wird benutzt und wieder
vergessen — gespeichert wird er nirgends: nicht in einer Bindung, nicht im Durable Object,
nicht im Protokoll. Aber während einer Anfrage liegen deine Aufgaben im Klartext im
Speicher des Workers.

Das ist der Preis dafür, dass ein Chat von irgendwoher drankommt, ohne dass bei dir ein
Rechner laufen muss. Wer den Tausch nicht will, braucht stattdessen ein Programm auf dem
eigenen Rechner, das den Schlüssel hält — dann bleibt die Verschlüsselung unangetastet,
und der Zugriff endet, sobald der Rechner aus ist.

**Wer den Schlüssel hat, hat die Aufgaben.** Es gibt kein zweites Token: Der Zugang *ist*
das Geheimnis aus dem Koppel-Link. Ein eigener Schlüssel daneben wäre ein zweiter Ort zum
Verlieren, ohne etwas zu schützen. Widerrufen heißt deshalb: in der App **Trennen**, dann
neu einschalten — danach ist der alte Schlüssel wertlos.

## Einrichten

**1.** In der App: Einstellungen → Geräteübergreifend → **Abgleich einschalten**, und
einmal abgleichen lassen. Ohne einen abgelegten Stand hat die Schnittstelle nichts zu
lesen und sagt das auch.

**2.** Den Koppel-Link kopieren. Was hinter `#koppeln=` steht, sind 43 Zeichen — das ist
der Schlüssel:

```
https://petodo.beispiel.workers.dev/#koppeln=kJ8x…
                                              ▲
                                              das hier
```

**3.** Die Zeitzone in `wrangler.toml` prüfen. Sie steht dort, weil ein Worker nirgends
steht, wo jemand wohnt — seine Ortszeit ist UTC. Ohne die Angabe läge „18:30“ im Sommer
zwei Stunden daneben:

```toml
[vars]
ZEITZONE = "Europe/Berlin"
```

Mehr gibt es nicht einzurichten.

## Die Wege

Alle brauchen den Kopf `Authorization: Bearer <die 43 Zeichen>`. **Nie in der Adresse** —
dort landet der Schlüssel in jedem Zugriffsprotokoll dazwischen.

| Weg | Was er tut |
| --- | --- |
| `GET /api/aufgaben` | alles Offene, dazu Listen und Etiketten |
| `GET /api/aufgaben?alles=1` | auch das Erledigte |
| `GET /api/markdown` | das ganze Vault als **eine** `.md`-Datei |
| `GET /api/sicherung` | die vollständige Sicherung als JSON |
| `POST /api/aufgaben` | eine Aufgabe anlegen |
| `PATCH /api/aufgaben/<id>` | ändern, abhaken, löschen |

### Lesen

```
curl -H "Authorization: Bearer $GEHEIMNIS" https://petodo.beispiel.workers.dev/api/aufgaben
```

```json
{
  "stand": "2026-09-05T17:30:00.000Z",
  "listen": [{ "id": "…", "name": "Posteingang" }],
  "etiketten": [{ "id": "…", "name": "haus" }],
  "aufgaben": [
    {
      "id": "…",
      "titel": "Reifen wechseln",
      "notiz": "Werkstatt anrufen",
      "liste": "…",
      "erledigt": false,
      "faellig": "2026-07-07",
      "uhrzeit": 1110,
      "prioritaet": 1,
      "etiketten": []
    }
  ]
}
```

`uhrzeit` ist die Minute des Tages — `1110` ist 18:30. `faellig` ist ein Kalendertag in
**deiner** Zeitzone, kein Zeitstempel: Millisekunden laden zum Falschrechnen ein.

### Anlegen

```
curl -X POST -H "Authorization: Bearer $GEHEIMNIS" -H "Content-Type: application/json" \
  -d '{"titel":"Reifen wechseln","faellig":"2026-07-07","uhrzeit":1110,"liste":"Posteingang"}' \
  https://petodo.beispiel.workers.dev/api/aufgaben
```

Alles außer `titel` darf fehlen. `liste` nimmt die Kennung **oder** den Namen; ohne Angabe
die erste. `faellig` ist `JJJJ-MM-TT` — alles andere wird abgewiesen, statt still auf einem
falschen Tag zu landen.

### Ändern

```
curl -X PATCH -H "Authorization: Bearer $GEHEIMNIS" -H "Content-Type: application/json" \
  -d '{"erledigt":true}' https://petodo.beispiel.workers.dev/api/aufgaben/<id>
```

Möglich sind `titel`, `notiz`, `faellig`, `uhrzeit`, `prioritaet`, `wiederholung`,
`erledigt` und `geloescht`. Gelöscht wird als **Grabstein**, nie wirklich — sonst käme die
Zeile beim nächsten Abgleich vom Telefon zurück.

## Das Vault

`GET /api/markdown` gibt eine Datei, die Obsidian ohne Zutun versteht:

```markdown
---
quelle: petodo
fassung: 1
stand: 2026-09-05T17:30:00.000Z
offen: 12
erledigt: 40
---

# PeTodo

## Posteingang

- [ ] Reifen wechseln 📅 2026-07-07 18:30 #auto
    Werkstatt anrufen
- [ ] Wocheneinkauf
    - [ ] Milch
    - [ ] Brot
```

**Eine** Datei, nicht eine je Aufgabe. Wer sie ins Vault legt, will einen Stand
überschreiben können, ohne vorher aufzuräumen — eine Datei je Aufgabe hinterlässt bei jedem
Umbenennen eine Leiche, und die räumt niemand weg.

Der Vorspann ist der eigentliche Zweck: Er macht die Datei für Dataview abfragbar und, was
mehr wiegt, **wiedererkennbar**. Eine KI, die das Vault pflegt, soll nicht raten müssen,
welche Datei von ihr ist.

Zwei Abrufe hintereinander sind zeichengleich bis auf `stand`. Ohne das meldete eine
Versionsverwaltung bei jedem Export eine Änderung, die keine ist.

`[[Titel]]` in einer Notiz bleibt stehen: Das ist in Obsidian derselbe Verweis wie in der
App — dieselbe Schreibweise, kein Umbau.

**Die Datei wird überschrieben.** Was jemand von Hand hineinschreibt, ist beim nächsten
Export weg; der Satz steht auch in der Datei selbst. Wer aus dem Vault heraus etwas ändern
will, nimmt `POST` und `PATCH`.

## Was der Chat wissen muss

Kurz genug, um es in eine Anweisung zu kopieren:

```
Meine Aufgaben liegen unter https://petodo.beispiel.workers.dev/api/
Schlüssel: Authorization: Bearer <43 Zeichen>

GET  /api/aufgaben              was offen ist
GET  /api/markdown              alles als Markdown fürs Vault
POST /api/aufgaben              {"titel":"…","faellig":"JJJJ-MM-TT","uhrzeit":Minute des Tages}
PATCH /api/aufgaben/<id>        {"erledigt":true}

Datumsangaben sind Kalendertage in meiner Zeitzone, keine Zeitstempel.
```

## Wenn zwei gleichzeitig schreiben

Die Schnittstelle liest den Stand, ändert ihn und schreibt zurück — mit dem Stempel des
gelesenen Stands. Kommt in der Zwischenzeit ein Gerät dazwischen, weist der Raum ab, und der
ganze Vorgang läuft neu auf dem neuen Stand. Die Änderung wird dabei **noch einmal
angewandt**, nicht der alte Stand hochgeschoben. Lautlos überschrieben wird nie.

## Prüfen

```
npx wrangler dev --port 8788      # Worker samt Speicher, nichts einzurichten
node tools/kitest.mjs             # 17 Punkte gegen den echten Worker
npm test                          # darunter 21 für die Wegewahl, 8 für die Zeitzonen
```

`tools/kitest.mjs` fährt den ganzen Weg: Ein echter Browser richtet den Abgleich ein, die
Schnittstelle liest, legt an und hakt ab — und danach wird auf dem **Gerät** nachgesehen,
ob es angekommen ist. Dazu die Gegenprobe, dass `/sync/…` weiterhin nur einen verschlüsselten
Klumpen herausgibt, in dem kein Aufgabentitel im Klartext steht.
