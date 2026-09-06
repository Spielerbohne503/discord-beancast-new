# Geräteübergreifend

Aufgaben auf Telefon und Rechner, ohne Konto und ohne dass jemand mitlesen kann.

## Was das mit der Gründungsregel macht

Die App war von Anfang an „offline, ohne Konto, ohne Telemetrie“. Abgleich zwischen
Geräten braucht **irgendetwas** dazwischen — das ist ein echter Widerspruch, und er wird
hier nicht wegdiskutiert, sondern eingegrenzt:

- **Der Abgleich ist aus**, bis man ihn einschaltet. Ohne ihn ändert sich nichts.
- **Der Server ist deiner.** Derselbe Worker, der die Seite ausliefert.
- **Er kann nichts lesen.** Was dort liegt, ist mit einem Schlüssel verschlüsselt, der aus
  einem Geheimnis entsteht, das auf deinen Geräten bleibt.
- **Es gibt kein Konto**, keine Anmeldung, keine Kennung, die dich beschreibt.

Was der Server sieht: eine 43 Zeichen lange Kennung und einen Klumpen Bytes. Nicht, wem er
gehört, nicht wie viele Aufgaben darin stehen, nicht wovon sie handeln.

## Einschalten

**Am Server ist nichts einzurichten.** Pushen genügt: Cloudflare baut neu, legt den
Speicher beim Ausrollen selbst an, und `/sync/…` antwortet.

**Auf dem ersten Gerät:** Einstellungen → Geräteübergreifend → **Abgleich einschalten**.
Ein Knopf. Nichts auszudenken, nichts zu tippen, nichts abzuwarten.

**Auf jedem weiteren Gerät:** Den **Koppel-Link** vom ersten Gerät öffnen. Fertig.

In der Android-Hülle lässt sich kein Link „öffnen“ — sie wohnt an einem eigenen Ursprung.
Dort gibt es stattdessen ein Feld: Link einfügen, **Koppeln**. Ein Handgriff, und die
Adresse der Ablage kommt gleich mit.

## Die offene Seite

**Wer die Adresse aufruft, ist drin.** Kein Koppeln, kein Link, keine Rückfrage — die Seite
gibt das Geheimnis jedem heraus, der danach fragt. Das ist keine Schwachstelle, sondern der
Zweck; wer sie anschaltet, hat sich dafür entschieden.

Das heißt im Klartext: **Wer die Adresse kennt oder errät, sieht deine Aufgaben und kann sie
ändern.** Die Adresse steht in keiner Suchmaschine, solange niemand sie verlinkt — aber sie
ist die einzige Hürde, die bleibt.

### Anschalten

```
npx wrangler secret put OEFFENTLICH
```

Eingegeben wird das Geheimnis aus dem Koppel-Link — die 43 Zeichen hinter `#koppeln=`. Wer
das nimmt, was schon auf einem Gerät liegt, behält seinen Bestand; ein frisches Geheimnis
fängt einen neuen Raum an.

**Als Secret und nicht in `wrangler.toml`.** Herausgegeben wird es ohnehin an jeden
Besucher — aber in der Datei stünde es zusätzlich in der Projektgeschichte, und die ist
auch dann noch da, wenn die Seite längst wieder zu ist.

### Ausschalten

```
npx wrangler secret delete OEFFENTLICH
```

Danach ist `/offen` wieder `{ offen: false }`, und neue Besucher bekommen nichts mehr.
**Geräte, die schon drin sind, bleiben drin** — sie haben das Geheimnis gespeichert. Wer
auch die aussperren will, braucht ein neues: In der App **Trennen**, neu einschalten, und
das neue Geheimnis als Secret hinterlegen.

### Was auch dann noch gilt

- **Abgelegt wird weiterhin verschlüsselt.** Cloudflare sieht nach wie vor nur einen
  Klumpen Bytes; das Geheimnis geht an den Browser, nicht in den Speicher.
- **Ein Koppel-Link schlägt die offene Seite.** Wer einen anklickt, meint genau diesen
  Bestand — dann zählt der Link.
- **Ein Gerät, das schon einen Bestand hat, wechselt nicht.** Sonst hätte es seine Aufgaben
  scheinbar verloren.
- Die Einstellungen sagen es. Oben in „Geräteübergreifend“ steht **„Diese Seite ist offen“**,
  jedes Mal — nicht im Kleingedruckten.

## Warum es keine Losung mehr gibt

Vorher tippte man auf jedem Gerät denselben ausgedachten Satz. Daraus wurden mit PBKDF2
über 310 000 Runden Schlüssel und Raumkennung abgeleitet — teuer sein **musste** das, weil
in einem ausgedachten Satz wenig Zufall steckt und man das Raten künstlich verteuern muss.

Der Preis dafür war überall spürbar: einen Satz ausdenken, ihn auf dem zweiten Gerät
fehlerfrei abtippen, eine Sekunde warten — und beim Vertippen eine Meldung bekommen, die
wie ein Serverfehler aussah. Dazu auf dem Telefon noch die Adresse der Ablage von Hand.

Das Geheimnis kommt jetzt aus `crypto.getRandomValues`. **32 Byte echter Zufall sind nicht
zu raten**, egal wie billig die Ableitung ist; PBKDF2 fällt damit weg und mit ihm die
Wartezeit. Geteilt wird mit HKDF in Schlüssel und Raumkennung.

Auf das zweite Gerät kommt es über den Koppel-Link:

```
https://petodo.beispiel.workers.dev/#koppeln=<43 Zeichen>
```

**Hinter der Raute ist kein Zufall.** Was dort steht, schickt ein Browser nie an einen
Server — es taucht in keinem Zugriffsprotokoll auf, in keinem `Referer`, in keinem
Zwischenspeicher unterwegs. Und weil die Adresse mit im Link steht, ist auch die auf dem
Telefon nichts mehr zum Abtippen.

Der Tausch, ehrlich benannt: **Wer den Link hat, hat die Aufgaben.** Vorher war das
Geheimnis in einem Kopf, jetzt ist es in einer Zeile, die man verschicken kann. Für einen
Link an sich selbst ist das der richtige Tausch; er gehört nicht in einen Gruppenchat. Steht
so auch in der App unter dem Link.

Verloren gegangen ist damit nichts, was man wiederherstellen könnte — das ging vorher auch
nicht. Ist das Geheimnis auf allen Geräten weg, ist der abgelegte Stand nicht mehr lesbar.
Wer das ändern wollte, müsste jemanden einbauen, der mitlesen kann.

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

- **Eingerichtet wird über den Koppel-Link, nicht über einen Knopf.** Der Ursprung der
  Hülle (`appassets.androidplatform.net`) ist eine örtliche Kennung, keine Adresse im Netz.
  Die Adresse der Ablage muss also von außen kommen — und genau die bringt der Link mit.
- **In der Hülle gilt keine Sicherheitsrichtlinie aus `_headers`.** Die gilt nur für die
  Webseite im Netz, wo `connect-src 'self'` richtig ist, weil Seite und Ablage denselben
  Ursprung haben. In der Hülle haben sie das nie. Wer in `Vermittler.kt` eine Richtlinie
  nachrüstet, muss `connect-src` für die Ablage öffnen — sonst stirbt der Abgleich lautlos.
- **Der Server lässt genau diesen einen zusätzlichen Ursprung an die Antwort heran**
  (`worker/index.js`, `HUELLEN_URSPRUNG`) — die Ursprungsregel des Browsers gilt sonst
  auch für einen WebView, und ohne diese Freigabe käme die Antwort zwar an, aber niemand
  dürfte sie lesen.

Erinnerungen bei geschlossener App bleiben ein Unterschied zur installierten Webseite im
Browser — den hat die Hülle für sich, dafür gibt es sie.
