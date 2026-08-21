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
 * Die Erlaubnis wird erst beim Einschalten erfragt, nie beim ersten Start: Ein Dialog, den
 * man nicht erwartet hat, wird weggeklickt — und danach ist er für immer weg.
 */
function erinnerungen(setzenUndNeu) {
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

function darstellung(setzenUndNeu) {
  return gruppe(
    S.settings_motion,
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
  const blob = new Blob([text], { type: "application/json" });
  const adresse = URL.createObjectURL(blob);

  const verweis = h("a", { href: adresse, download: backupFileName(jetzt) });
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
