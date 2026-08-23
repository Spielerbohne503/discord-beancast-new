# Geräteübergreifend

Aufgaben auf Telefon und Rechner, ohne Konto und ohne dass jemand mitlesen kann.

## Was das mit der Gründungsregel macht

Die App war von Anfang an „offline, ohne Konto, ohne Telemetrie“. Abgleich zwischen
Geräten braucht **irgendetwas** dazwischen — das ist ein echter Widerspruch, und er wird
hier nicht wegdiskutiert, sondern eingegrenzt:

- **Der Abgleich ist aus**, bis man ihn einschaltet. Ohne ihn ändert sich nichts.
- **Der Server ist deiner.** Derselbe Worker, der die Seite ausliefert.
- **Er kann nichts lesen.** Was dort liegt, ist mit einem Schlüssel verschlüsselt, der aus
  deiner Losung entsteht und das Gerät nie verlässt.
- **Es gibt kein Konto**, keine Anmeldung, keine Kennung, die dich beschreibt.

Was der Server sieht: eine 43 Zeichen lange Kennung und einen Klumpen Bytes. Nicht, wem er
gehört, nicht wie viele Aufgaben darin stehen, nicht wovon sie handeln.

## Einschalten

**Am Server ist nichts einzurichten.** Pushen genügt: Cloudflare baut neu, legt das
Durable Object beim Ausrollen selbst an, und `/sync/…` antwortet.

**In der App:** Einstellungen → Geräteübergreifend → eine Losung eintippen → **Verbinden**.
Auf jedem weiteren Gerät dieselbe Losung. Mehr gehört nicht dazu.

In der Android-Hülle kommt genau ein Feld dazu, siehe unten: die **Adresse der Ablage**.

### Warum hier nichts einzurichten ist

Vorher lag der Stand in KV, und dafür musste jemand von Hand im Dashboard eine Namespace
anlegen und ihre Kennung in `wrangler.toml` eintragen. Wer den Schritt nicht kannte, hatte
einen Abgleich, der nie funktioniert hat — und nichts sagte das, außer einer Meldung
„nicht erreichbar“, die nach einer vorübergehenden Störung aussah.

Ein Durable Object entsteht beim Ausrollen aus der Angabe in `wrangler.toml`. Es gibt keine
Kennung, die man abschreiben, und keinen Schritt, den man vergessen kann.

Der zweite Grund wiegt schwerer: **In KV hielt die Prüfung gegen gleichzeitiges Schreiben
nicht, was sie versprach.** KV ist letztlich-konsistent — zwei Geräte konnten denselben
Stempel lesen, beide für aktuell halten und beide schreiben. Genau der lautlose
Datenverlust, gegen den der Stempel da ist. Ein Durable Object arbeitet einen Aufruf nach
dem anderen ab; „lies den Stempel, vergleiche, schreibe“ ist dort **ein** Schritt.

## Die Losung

Sie ist das Einzige, was zählt — und es gibt **keine Wiederherstellung**. Ist sie weg, ist
der abgelegte Stand nicht mehr lesbar. Das ist kein Versäumnis, sondern die Folge davon,
dass der Server nichts weiß: Wer sie zurücksetzen könnte, könnte auch mitlesen.

Die Losung selbst wird nirgends gespeichert. Aus ihr werden 512 Bit abgeleitet und in der
Mitte geteilt: vorn der Schlüssel, hinten die Raumkennung. Beides aus demselben teuren
Schritt (PBKDF2, 310 000 Runden) — wer die Kennung erraten will, muss denselben Aufwand
treiben wie für den Schlüssel.

Nimm einen Satz, keine acht Zeichen.

## Wie zusammengeführt wird

**Je Zeile gewinnt der jüngere `updatedAt`.** Nicht je Datei, nicht je Tabelle — je Zeile.
Wer auf dem Telefon eine Aufgabe abhakt und am Rechner eine andere anlegt, behält beides.

Dafür wurden die Einbahnstraßen von Anfang an so gebaut: UUIDs als Schlüssel (zwei Geräte
können ohne Absprache anlegen), `updatedAt` in jeder Zeile, und **Tombstones** statt echtem
Löschen. Ohne den letzten Punkt käme jede gelöschte Aufgabe beim nächsten Abgleich vom
anderen Gerät zurück — der Fehler, an dem selbstgebauter Abgleich fast immer scheitert.
Grabsteine bleiben ein Jahr stehen, danach fallen sie weg.

Gegen gleichzeitiges Schreiben hilft ein Stempel: Wer auf einem veralteten Stand aufsetzt,
bekommt eine Abfuhr und fängt von vorn an. Lautlos überschrieben wird nie.

## Wann abgeglichen wird

Beim Start, bei der Rückkehr in den Tab, und vier Sekunden nach der letzten Änderung. Kein
Takt: Es ändert sich ja nichts, wenn niemand etwas tut. Dazu der Knopf in den Einstellungen.

## Prüfen

```
npm test                                   # Fachlogik und Server, ohne Netz
npx wrangler dev --port 8787               # nichts zu entkommentieren
node tools/synctest.mjs                    # zwei Browser-Kontexte, ein Worker
```

`tools/synctest.mjs` fährt durch, woran es scheitern würde: gleichzeitige Arbeit auf beiden
Seiten, ein Haken, der ankommt, eine Löschung, die gelöscht bleibt — und die Probe, dass im
abgelegten Klumpen kein Aufgabentitel im Klartext steht.

## Die Android-Hülle

Die Hülle hat die `INTERNET`-Berechtigung — ausschließlich für diesen Abgleich, und auch
den nur, wenn er hier in den Einstellungen eingeschaltet ist. Zwei Dinge sind dabei anders
als im Browser:

- **Die Adresse der Ablage muss eingetragen sein.** Der Ursprung der Hülle
  (`appassets.androidplatform.net`) ist eine örtliche Kennung, keine echte Adresse im
  Netz — „leer lassen“ funktioniert nur im Browser, wo die Webseite und die Ablage
  tatsächlich am selben Ursprung liegen. In der Hülle steht deshalb dieselbe Adresse wie
  im Browser: die des eigenen Workers.
- **Der Server lässt genau diesen einen zusätzlichen Ursprung an die Antwort heran**
  (`worker/index.js`, `HUELLEN_URSPRUNG`) — die Ursprungsregel des Browsers gilt sonst
  auch für einen WebView, und ohne diese Freigabe käme die Antwort zwar an, aber niemand
  dürfte sie lesen.

Erinnerungen bei geschlossener App bleiben ein Unterschied zur installierten Webseite im
Browser — den hat die Hülle für sich, dafür gibt es sie.
