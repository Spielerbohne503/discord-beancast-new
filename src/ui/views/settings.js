/**
 * Einstellungen.
 *
 * Am Schreibtisch stehen sie **ganz**, ohne Untermenü — was auf dem Telefon hinter „Mehr“
 * liegt, hat auf einem großen Bildschirm keinen Grund, versteckt zu sein.
 */

import { Balance } from "../../domain/balance.js";
import { parseHhMm } from "../../domain/time.js";
import * as repo from "../../data/repo.js";
import { clearAll } from "../../data/db.js";
import { backupFileName, exportBackup, importBackup } from "../../data/backupstore.js";
import { dateiSichern, erinnerungenErlaubt, erlaubnisAnfragen, exakteWeckerErlaubt, huellenFassung, inHuelle } from "../bruecke.js";
import { ausGeheimnis, neuesGeheimnis } from "../../data/krypto.js";
import { ausKoppelLink, koppelLink } from "../../domain/koppeln.js";
import { SKINS, naechsteGestalt, verfuegbar } from "../../domain/skins.js";
import { levelForXp } from "../../domain/pet.js";
import { anstossen, letzterAusgang } from "../sync.js";
import { aktualisieren, state } from "../store.js";
import { fuellen, h } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";
import { meldung } from "../toast.js";

export function settingsView() {
  const element = h("div.einstellungen");

  async function setzenUndNeu(schluessel, wert) {
    await repo.saveSetting(schluessel, wert);
    await aktualisieren();
  }

  function update() {
    if (!state.bereit) return;

    fuellen(
      element,
      abgleich(setzenUndNeu, update),
      fokusgruppe(setzenUndNeu),
      ruhezeit(setzenUndNeu),
      erinnerungen(setzenUndNeu),
      darstellung(setzenUndNeu),
      daten(),
      ueber(),
    );
  }

  return { el: element, update };
}

/**
 * Geräteübergreifend.
 *
 * Steht ganz oben, weil es die einzige Einstellung ist, die etwas an der Welt außerhalb
 * dieses Geräts ändert. Standardmäßig aus — und der Hinweis darunter sagt, was passiert,
 * bevor man einschaltet, nicht danach.
 *
 * **Ein Knopf.** Es gibt nichts auszudenken, nichts zu tippen und nichts abzuwarten: Das
 * Geheimnis kommt aus dem Zufallsgenerator. Wer ein zweites Gerät dazunimmt, kopiert einen
 * Link — die Adresse der Ablage steckt darin, also gibt es auch die nicht mehr einzutragen.
 */
function abgleich(setzenUndNeu, neuZeichnen) {
  const huelle = inHuelle();
  const verbunden = Boolean(state.settings.syncRaum);

  async function einschalten() {
    await koppeln(neuesGeheimnis(), eigeneAdresse());
  }

  /** Beide Wege enden hier: frisch gewürfelt oder aus einem Link gelesen. */
  async function koppeln(geheimnis, adresse) {
    const abgeleitet = await ausGeheimnis(geheimnis);

    await repo.saveSetting("syncAdresse", adresse);
    await repo.saveSetting("syncGeheimnis", geheimnis);
    await repo.saveSetting("syncRaum", abgeleitet.raum);
    await repo.saveSetting("syncSchluessel", abgeleitet.schluessel);
    await repo.saveSetting("syncAktiv", true);
    await aktualisieren();

    await anstossen({ still: false });
    neuZeichnen();
  }

  async function trennen() {
    // Der Stand auf dem Server bleibt liegen — er gehört auch den anderen Geräten.
    for (const schluessel of ["syncAdresse", "syncGeheimnis", "syncRaum", "syncSchluessel"]) {
      await repo.saveSetting(schluessel, "");
    }
    await repo.saveSetting("syncStand", null);
    await setzenUndNeu("syncAktiv", false);
    neuZeichnen();
  }

  return verbunden
    ? gruppe(S.settings_sync, ...eingerichtet(setzenUndNeu, trennen))
    : gruppe(S.settings_sync, ...nochNicht(huelle, einschalten, koppeln));
}

/**
 * Der Zustand vor dem ersten Mal: ein Knopf, und darunter der Weg fürs zweite Gerät.
 *
 * Die Reihenfolge ist Absicht. Wer hier zum ersten Mal steht, hat noch kein anderes Gerät —
 * für den ist der obere Knopf der richtige. Das Einfügefeld steht darunter und erklärt
 * sich selbst.
 */
function nochNicht(huelle, einschalten, koppeln) {
  const feld = h("input.eingabe", {
    type: "text",
    autocomplete: "off",
    spellcheck: false,
    placeholder: S.settings_sync_link_platzhalter,
    onkeydown: (ereignis) => {
      if (ereignis.key === "Enter") void einfuegen();
    },
  });

  async function einfuegen() {
    const gelesen = ausKoppelLink(feld.value);
    if (gelesen === null) {
      meldung(S.settings_sync_link_unbrauchbar);
      return;
    }

    // Ohne Adresse im Link gilt die eigene. In der Hülle gibt es die nicht — dort ist der
    // ganze Link erforderlich, und das steht dann auch da.
    const adresse = gelesen.adresse ?? eigeneAdresse();
    if (adresse.length === 0) {
      meldung(S.settings_sync_link_ohne_adresse);
      return;
    }

    feld.value = "";
    await koppeln(gelesen.geheimnis, adresse);
  }

  return [
    h("span.feld__hinweis", {}, S.settings_sync_hint),
    // In der Hülle ist der obere Weg der seltenere: Wer die App aufs Telefon holt, hat den
    // Rechner meist schon eingerichtet.
    huelle ? null : h("button.knopf.knopf--haupt", { onclick: () => void einschalten() }, icon("wiederholen", 16), S.settings_sync_einschalten),
    h(
      "div.feld",
      {},
      h("span.feld__beschriftung", {}, S.settings_sync_link_einfuegen),
      h(
        "div.schnell__zeile",
        { style: { gap: "8px" } },
        feld,
        h(
          huelle ? "button.knopf.knopf--haupt" : "button.knopf",
          { onclick: () => void einfuegen() },
          S.settings_sync_koppeln,
        ),
      ),
      h("span.feld__hinweis", {}, huelle ? S.settings_sync_link_hint_huelle : S.settings_sync_link_hint),
    ),
    huelle
      ? h("button.knopf.knopf--still", { onclick: () => void einschalten() }, S.settings_sync_einschalten_allein)
      : null,
  ];
}

/**
 * Der eingerichtete Zustand.
 *
 * Obenan steht der Koppel-Link, denn das ist das Einzige, was man hier noch braucht: ihn
 * aufs nächste Gerät bringen.
 */
function eingerichtet(setzenUndNeu, trennen) {
  const adresse = state.settings.syncAdresse || eigeneAdresse();
  const link = koppelLink(adresse, state.settings.syncGeheimnis);

  const linkfeld = h("input.eingabe", {
    type: "text",
    readonly: true,
    value: link,
    onclick: () => linkfeld.select(),
  });

  async function kopieren() {
    // `navigator.clipboard` gibt es nicht überall und nicht immer mit Erlaubnis. Das Feld
    // ist dann immer noch da und markiert sich beim Antippen selbst.
    try {
      await navigator.clipboard.writeText(link);
      meldung(S.settings_sync_link_kopiert);
    } catch {
      linkfeld.select();
      meldung(S.settings_sync_link_von_hand);
    }
  }

  return [
    schalter(S.settings_sync_toggle, state.settings.syncAktiv, (an) => setzenUndNeu("syncAktiv", an)),
    h(
      "div.feld",
      {},
      h("span.feld__beschriftung", {}, S.settings_sync_link_zeigen),
      h(
        "div.schnell__zeile",
        { style: { gap: "8px" } },
        linkfeld,
        h("button.knopf.knopf--haupt", { onclick: () => void kopieren() }, S.settings_sync_link_kopieren),
      ),
      h("span.feld__hinweis", {}, S.settings_sync_link_zeigen_hint),
    ),
    h(
      "div.chips",
      {},
      h(
        "button.knopf",
        { onclick: () => void anstossen({ still: false }) },
        icon("wiederholen", 16),
        S.settings_sync_jetzt,
      ),
      h("button.knopf.knopf--gefahr", { onclick: () => void trennen() }, S.settings_sync_trennen),
    ),
    h(
      "span.feld__hinweis",
      {},
      state.settings.syncStand
        ? S.settings_sync_stand(new Date(state.settings.syncStand).toLocaleString("de-DE"))
        : S.settings_sync_nie,
    ),
    letzterAusgang ? h("span.feld__hinweis", {}, S.settings_sync_ergebnis[letzterAusgang.ergebnis] ?? "") : null,
  ];
}

/**
 * Die eigene Adresse — in der Hülle gibt es keine.
 *
 * Ihr Ursprung (`appassets.androidplatform.net`) ist eine örtliche Kennung und keine
 * Adresse im Netz. Dort muss die Adresse aus dem Koppel-Link kommen.
 */
function eigeneAdresse() {
  if (inHuelle()) return "";
  return globalThis.location?.origin ?? "";
}

function gruppe(titel, ...kinder) {
  return h("section.karte.gruppe", {}, h("h2.gruppe__titel", {}, titel), ...kinder);
}

function zahl(name, schluessel, { min = 1, max = 180 } = {}) {
  const feld = h("input.eingabe", {
    type: "number",
    min,
    max,
    value: String(state.settings[schluessel]),
    onchange: async () => {
      const wert = Math.min(max, Math.max(min, Number(feld.value) || min));
      feld.value = String(wert);
      await repo.saveSetting(schluessel, wert);
      await aktualisieren();
    },
  });
  return h("label.feld", {}, h("span.feld__beschriftung", {}, name), feld);
}

function fokusgruppe() {
  return gruppe(
    S.settings_focus,
    h(
      "div.zahlenpaar",
      {},
      zahl(S.settings_focus_minutes, "focusMinutes"),
      zahl(S.settings_short_break, "shortBreakMinutes"),
      zahl(S.settings_long_break, "longBreakMinutes"),
      zahl(S.settings_rounds, "roundsBeforeLongBreak", { min: 1, max: 12 }),
    ),
  );
}

function ruhezeit(setzenUndNeu) {
  const von = h("input.eingabe", {
    type: "time",
    value: state.settings.quietHoursStart,
    onchange: () => {
      if (parseHhMm(von.value) !== null) void setzenUndNeu("quietHoursStart", von.value);
    },
  });
  const bis = h("input.eingabe", {
    type: "time",
    value: state.settings.quietHoursEnd,
    onchange: () => {
      if (parseHhMm(bis.value) !== null) void setzenUndNeu("quietHoursEnd", bis.value);
    },
  });

  return gruppe(
    S.settings_quiet,
    schalter(S.settings_quiet_enabled, state.settings.quietHoursEnabled, (an) =>
      setzenUndNeu("quietHoursEnabled", an),
    ),
    h(
      "div.zahlenpaar",
      {},
      h("label.feld", {}, h("span.feld__beschriftung", {}, S.settings_quiet_start), von),
      h("label.feld", {}, h("span.feld__beschriftung", {}, S.settings_quiet_end), bis),
    ),
    h("span.feld__hinweis", {}, S.settings_quiet_hint),
  );
}

/**
 * Erinnerungen.
 *
 * Zwei Welten, ein Schalter. Im Browser erinnert die Seite, solange sie offen ist; in der
 * Android-Hülle stellt das Betriebssystem einen Wecker und meldet sich auch bei
 * geschlossener App. Was gerade gilt, steht darunter — nicht in einer Fußnote irgendwo.
 *
 * Die Erlaubnis wird erst beim Einschalten erfragt, nie beim ersten Start: Ein Dialog, den
 * man nicht erwartet hat, wird weggeklickt — und danach ist er für immer weg.
 */
function erinnerungen(setzenUndNeu) {
  return inHuelle() ? erinnerungenInDerHuelle(setzenUndNeu) : erinnerungenImBrowser(setzenUndNeu);
}

function erinnerungenInDerHuelle(setzenUndNeu) {
  const erlaubt = erinnerungenErlaubt();
  const genau = exakteWeckerErlaubt();

  return gruppe(
    S.settings_notifications,
    schalter(S.settings_notifications_toggle, state.settings.notifications && erlaubt, async (an) => {
      if (!an) {
        await setzenUndNeu("notifications", false);
        return;
      }
      if (!erlaubt) {
        // Android beantwortet die Frage in einem eigenen Dialog; die Antwort steht beim
        // nächsten Zeichnen da, nicht hier.
        erlaubnisAnfragen();
      }
      await setzenUndNeu("notifications", true);
    }),
    h(
      "span.feld__hinweis",
      {},
      erlaubt ? S.settings_notifications_hint_app : S.settings_notifications_denied_app,
    ),
    erlaubt && !genau ? h("span.feld__hinweis", {}, S.settings_exact_alarms_missing) : null,
  );
}

function erinnerungenImBrowser(setzenUndNeu) {
  const unterstuetzt = typeof Notification !== "undefined";
  const abgelehnt = unterstuetzt && Notification.permission === "denied";

  return gruppe(
    S.settings_notifications,
    schalter(
      S.settings_notifications_toggle,
      state.settings.notifications && !abgelehnt,
      async (an) => {
        if (!an) {
          await setzenUndNeu("notifications", false);
          return;
        }
        if (!unterstuetzt) return;
        const antwort = await Notification.requestPermission();
        await setzenUndNeu("notifications", antwort === "granted");
      },
      !unterstuetzt || abgelehnt,
    ),
    h(
      "span.feld__hinweis",
      {},
      abgelehnt ? S.settings_notifications_denied : S.settings_notifications_hint,
    ),
  );
}

/**
 * Darstellung.
 *
 * Zwei Fassungen, dieselbe Sprache: harte Kanten, drei Akzente, kein Weichzeichner. Die
 * Wahl steht neben „Bewegung reduzieren“, weil beides dasselbe ist — wie viel die
 * Oberfläche einem zumutet.
 */
function darstellung(setzenUndNeu) {
  const fassungen = [
    { wert: "hell", text: S.settings_fassung_hell },
    { wert: "dunkel", text: S.settings_fassung_dunkel },
  ];

  const level = levelForXp(state.pet?.xp ?? 0);
  const offen = new Set(verfuegbar(level).map((skin) => skin.id));
  const naechste = naechsteGestalt(level);

  return gruppe(
    S.settings_motion,
    h(
      "div.feld",
      {},
      h("span.feld__beschriftung", {}, S.skins_titel),
      h(
        "div.gestalten",
        {},
        SKINS.map((skin) =>
          h(
            "button.gestalt",
            {
              type: "button",
              disabled: !offen.has(skin.id),
              "aria-pressed": String(state.settings.skin === skin.id),
              title: offen.has(skin.id) ? skin.id : S.skins_gesperrt(skin.abLevel),
              style: { "--von": skin.von, "--bis": skin.bis },
              onclick: () => void setzenUndNeu("skin", skin.id),
            },
            offen.has(skin.id) ? null : h("span.gestalt__schloss", {}, String(skin.abLevel)),
          ),
        ),
      ),
      naechste ? h("span.feld__hinweis", {}, S.skins_naechste(naechste.abLevel)) : null,
    ),
    h(
      "div.feld",
      {},
      h("span.feld__beschriftung", {}, S.settings_fassung),
      h(
        "div.chips",
        {},
        fassungen.map((eintrag) =>
          h(
            "button.chip",
            {
              type: "button",
              "aria-pressed": String(state.settings.fassung === eintrag.wert),
              onclick: () => void setzenUndNeu("fassung", eintrag.wert),
            },
            eintrag.text,
          ),
        ),
      ),
    ),
    schalter(S.settings_reduce_motion, state.settings.reduceMotion, (an) =>
      setzenUndNeu("reduceMotion", an),
    ),
  );
}

function daten() {
  const dateiwahl = h("input", {
    type: "file",
    accept: "application/json,.json",
    style: { display: "none" },
    onchange: async () => {
      const datei = dateiwahl.files?.[0];
      if (!datei) return;

      const bericht = await importBackup(await datei.text());
      dateiwahl.value = "";

      if (bericht === null) {
        meldung(S.settings_backup_failed);
        return;
      }
      await aktualisieren();
      meldung(S.settings_backup_ok(bericht));
    },
  });

  return gruppe(
    S.settings_data,
    h(
      "div.chips",
      {},
      h(
        "button.knopf",
        { onclick: () => void herunterladen() },
        icon("pfeil_rechts", 16),
        S.settings_backup_export,
      ),
      h("button.knopf", { onclick: () => dateiwahl.click() }, icon("pfeil_links", 16), S.settings_backup_import),
      dateiwahl,
    ),
    h("span.feld__hinweis", {}, S.settings_backup_hint),
    h(
      "button.knopf.knopf--gefahr",
      {
        onclick: async () => {
          if (!globalThis.confirm(S.settings_reset_confirm)) return;
          await clearAll();
          await repo.seedIfEmpty(S);
          await aktualisieren();
        },
      },
      icon("papierkorb", 16),
      S.settings_reset,
    ),
  );
}

/**
 * Die Sicherung landet als Datei im Download-Ordner.
 *
 * Über einen kurzlebigen Objekt-Verweis statt über einen Server: Es gibt keinen Server,
 * und es soll auch keiner nötig werden.
 */
async function herunterladen() {
  const jetzt = Date.now();
  const text = await exportBackup(jetzt);
  const name = backupFileName(jetzt);

  // In der Hülle tut ein `blob:`-Verweis nichts — dort geht die Datei über die Brücke.
  if (dateiSichern(name, text)) return;

  const blob = new Blob([text], { type: "application/json" });
  const adresse = URL.createObjectURL(blob);

  const verweis = h("a", { href: adresse, download: name });
  document.body.append(verweis);
  verweis.click();
  verweis.remove();
  URL.revokeObjectURL(adresse);
}

function ueber() {
  return gruppe(
    S.settings_about,
    h("span.feld__hinweis", {}, S.settings_about_body),
    h("span.feld__hinweis", {}, `${S.app_name} · ${S.app_tagline}`),
    huellenFassung() ? h("span.feld__hinweis", {}, S.settings_app_version(huellenFassung())) : null,
  );
}

function schalter(name, an, beiAenderung, gesperrt = false) {
  const eingabe = h("input", {
    type: "checkbox",
    checked: an,
    disabled: gesperrt,
    onchange: (ereignis) => void beiAenderung(ereignis.target.checked),
  });
  return h("label.schalter", {}, h("span", {}, name), eingabe, h("span.schalter__gleis"));
}

export { Balance };
