/**
 * Der Fokus-Timer.
 *
 * Gerechnet wird gegen einen gespeicherten Endzeitpunkt, nie gegen einen Zähler: Eine
 * Runde überlebt damit einen Tabwechsel, einen Neustart der Seite und einen zugeklappten
 * Rechner. Der Sekundentakt zeichnet nur — er entscheidet nichts.
 */

import {
  FocusPhase, FocusState, breakAfterFocus, earnsReward, endsAt, focusStateOf,
  formatRemaining, isBreak, nextAfterPhase, phaseDurationMs, remainingAt, resumedEndsAt,
} from "../../domain/focus.js";
import { RewardType, SpeechCategory } from "../../domain/pet.js";
import { uuid } from "../../domain/ids.js";
import { isOpen } from "../../domain/tasks.js";
import * as repo from "../../data/repo.js";
import { award } from "../../data/petstore.js";
import { aktualisieren, reagieren, state } from "../store.js";
import { fuellen, h, svg } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";
import { meldung } from "../toast.js";

const UMFANG = 2 * Math.PI * 46;

const PHASENNAMEN = {
  [FocusPhase.FOCUS]: S.focus_phase_focus,
  [FocusPhase.SHORT_BREAK]: S.focus_phase_short,
  [FocusPhase.LONG_BREAK]: S.focus_phase_long,
};

export function focusView() {
  const element = h("div.karte.karte--erhoben.fokus");

  /** Marschierende Ameisen am Rand, solange die Runde läuft — sichtbar von weitem. */
  const laufendZeigen = (zustand) =>
    element.classList.toggle("laeuft", zustand.state === FocusState.RUNNING);
  const aufgabenwahl = h("select.eingabe", { "aria-label": S.focus_pick_task });

  function einstellungen() {
    return {
      focusMinutes: state.settings.focusMinutes,
      shortBreakMinutes: state.settings.shortBreakMinutes,
      longBreakMinutes: state.settings.longBreakMinutes,
      roundsBeforeLongBreak: state.settings.roundsBeforeLongBreak,
    };
  }

  async function starten(phase = FocusPhase.FOCUS) {
    const jetzt = Date.now();
    await repo.saveFocus({
      id: uuid(),
      taskId: aufgabenwahl.value || null,
      phase,
      startedAt: jetzt,
      endsAt: endsAt(jetzt, phase, einstellungen()),
      pausedAt: null,
      completedAt: null,
      abortedAt: null,
    });
    await aktualisieren();
    if (!isBreak(phase)) reagieren(SpeechCategory.FOCUS_BEGINS);
  }

  async function anhalten() {
    const sitzung = state.focusSession;
    if (!sitzung) return;
    await repo.saveFocus({ ...sitzung, pausedAt: Date.now() });
    await aktualisieren();
  }

  async function weiter() {
    const sitzung = state.focusSession;
    if (!sitzung) return;
    const jetzt = Date.now();
    await repo.saveFocus({ ...sitzung, endsAt: resumedEndsAt(sitzung, jetzt), pausedAt: null });
    await aktualisieren();
  }

  async function abbrechen() {
    const sitzung = state.focusSession;
    if (!sitzung) return;
    await repo.saveFocus({ ...sitzung, abortedAt: Date.now() });
    await aktualisieren();
    meldung(S.focus_aborted_notice);
  }

  /**
   * Eine abgelaufene Runde abschließen.
   *
   * Läuft auch dann, wenn niemand zugesehen hat: Beim nächsten Öffnen der Seite steht die
   * Runde auf „abgelaufen“ und wird hier verbucht.
   */
  async function abschliessen(sitzung) {
    const jetzt = Date.now();
    await repo.saveFocus({ ...sitzung, completedAt: jetzt });

    if (earnsReward({ ...sitzung, completedAt: jetzt })) {
      await award(RewardType.FOCUS_DONE, jetzt, state.tasks, sitzung.taskId);
    }
    await aktualisieren();

    const weiterMit = nextAfterPhase(sitzung.phase, state.focusRounds, einstellungen());
    if (weiterMit.kind === "start_break") {
      await starten(weiterMit.phase);
      reagieren(SpeechCategory.FOCUS_ENDS);
      meldung(S.focus_done_notice);
    } else {
      reagieren(SpeechCategory.BREAK_OVER);
    }
  }

  function update() {
    if (!state.bereit) return;

    const zustand = focusStateOf(state.focusSession, state.now);
    document.body.dataset.fokus = zustand.state;

    if (zustand.state === FocusState.ELAPSED) {
      void abschliessen(zustand.session);
      return;
    }

    const phase = zustand.session?.phase ?? FocusPhase.FOCUS;
    const gesamt = phaseDurationMs(phase, einstellungen());
    const rest =
      zustand.state === FocusState.READY ? gesamt : remainingAt(zustand.session, zustand.state === FocusState.PAUSED ? zustand.session.pausedAt : state.now);
    const anteil = gesamt === 0 ? 0 : 1 - rest / gesamt;

    laufendZeigen(zustand);
    aufgabenwahlFuellen(zustand);

    fuellen(
      element,
      h("span.fokus__phase", {}, PHASENNAMEN[phase]),
      h(
        "div.fokus__uhr",
        {},
        ring(anteil),
        h("span.fokus__zahl", {}, formatRemaining(rest)),
      ),
      h("span.marke", {}, S.focus_rounds_today(state.focusRounds)),
      h("div.fokus__knoepfe", {}, ...knoepfe(zustand)),
      zustand.state === FocusState.READY ? aufgabenwahl : hinweisAufAufgabe(zustand),
    );
  }

  function knoepfe(zustand) {
    if (zustand.state === FocusState.READY) {
      return [
        h(
          "button.knopf.knopf--haupt",
          { onclick: () => void starten() },
          icon("start", 18),
          S.focus_start,
        ),
      ];
    }

    const laufend = zustand.state === FocusState.RUNNING;
    return [
      h(
        "button.knopf.knopf--haupt",
        { onclick: () => void (laufend ? anhalten() : weiter()) },
        icon(laufend ? "pause" : "start", 18),
        laufend ? S.focus_pause : S.focus_resume,
      ),
      h(
        "button.knopf",
        { onclick: () => void abschliessen(zustand.session) },
        icon("ueberspringen", 18),
        S.focus_skip,
      ),
      h("button.knopf.knopf--still", { onclick: () => void abbrechen() }, icon("stopp", 18), S.focus_stop),
    ];
  }

  function aufgabenwahlFuellen(zustand) {
    if (zustand.state !== FocusState.READY) return;
    const offene = state.tasks.filter(isOpen);
    const kennung = offene.map((task) => task.id).join(",");
    if (aufgabenwahl.dataset.kennung === kennung) return;

    const gewaehlt = aufgabenwahl.value;
    aufgabenwahl.dataset.kennung = kennung;
    fuellen(aufgabenwahl, [
      h("option", { value: "" }, S.focus_no_task),
      ...offene.map((task) => h("option", { value: task.id }, task.title)),
    ]);
    aufgabenwahl.value = offene.some((task) => task.id === gewaehlt) ? gewaehlt : "";
  }

  function hinweisAufAufgabe(zustand) {
    const task = state.tasks.find((eintrag) => eintrag.id === zustand.session?.taskId);
    return h("span.marke", {}, task ? task.title : S.focus_no_task);
  }

  return { el: element, update };
}

function ring(anteil) {
  return svg(
    "svg",
    { class: "fokus__ring", viewBox: "0 0 100 100", "aria-hidden": "true" },
    svg("circle", { class: "fokus__ring-spur", cx: 50, cy: 50, r: 46 }),
    // Zwei Linien übereinander: Tinte trägt, Acid liegt obenauf. Acid allein verschwindet
    // auf hellem Papier.
    svg("circle", {
      class: "fokus__ring-kante",
      cx: 50,
      cy: 50,
      r: 46,
      "stroke-dasharray": UMFANG.toFixed(2),
      "stroke-dashoffset": (UMFANG * (1 - anteil)).toFixed(2),
    }),
    svg("circle", {
      class: "fokus__ring-fortschritt",
      cx: 50,
      cy: 50,
      r: 46,
      "stroke-dasharray": UMFANG.toFixed(2),
      "stroke-dashoffset": (UMFANG * (1 - anteil)).toFixed(2),
    }),
  );
}

export { breakAfterFocus };
