/**
 * Der Fokus-Timer.
 *
 * Gespeichert wird ein **absoluter Endzeitpunkt**, nie ein heruntergezählter Rest. Ein
 * Countdown driftet, steht im Energiesparmodus still und ist nach einem Neuladen der
 * Seite weg; ein Zeitpunkt überlebt beides. Anhalten merkt sich `pausedAt`; der Rest
 * ergibt sich daraus.
 *
 * Ein abgelaufener Endzeitpunkt heißt **fertig**, nicht „läuft noch“.
 */

import { Balance } from "./balance.js";

const MS_PER_MINUTE = 60_000;

export const FocusPhase = Object.freeze({
  FOCUS: "focus",
  SHORT_BREAK: "short_break",
  LONG_BREAK: "long_break",
});

export function isBreak(phase) {
  return phase === FocusPhase.SHORT_BREAK || phase === FocusPhase.LONG_BREAK;
}

/** Einstellungen des Timers. Die Vorgaben stehen in `Balance`, nicht hier. */
export const DEFAULT_FOCUS_SETTINGS = Object.freeze({
  focusMinutes: Balance.FOCUS_MINUTES,
  shortBreakMinutes: Balance.SHORT_BREAK_MINUTES,
  longBreakMinutes: Balance.LONG_BREAK_MINUTES,
  roundsBeforeLongBreak: Balance.ROUNDS_BEFORE_LONG_BREAK,
});

export function phaseDurationMs(phase, settings = DEFAULT_FOCUS_SETTINGS) {
  const minutes =
    phase === FocusPhase.FOCUS
      ? settings.focusMinutes
      : phase === FocusPhase.SHORT_BREAK
        ? settings.shortBreakMinutes
        : settings.longBreakMinutes;
  return Math.max(1, Number(minutes) || 1) * MS_PER_MINUTE;
}

export const FocusState = Object.freeze({
  READY: "ready",
  RUNNING: "running",
  PAUSED: "paused",
  /** Endzeitpunkt erreicht oder überschritten, der Abschluss steht noch aus. */
  ELAPSED: "elapsed",
});

export function isSessionFinished(session) {
  return Boolean(session?.completedAt || session?.abortedAt);
}

/** Abgebrochene Runden geben keine Belohnung — das entscheidet sich hier. */
export function earnsReward(session) {
  return Boolean(session?.completedAt) && !session?.abortedAt && session?.phase === FocusPhase.FOCUS;
}

/** Verbleibende Zeit in Millisekunden, nie negativ. */
export function remainingAt(session, at) {
  return Math.max(0, session.endsAt - at);
}

/**
 * Der Zustand zu einem Zeitpunkt.
 *
 * Gerechnet wird immer gegen den gespeicherten Endzeitpunkt, nie gegen einen mitlaufenden
 * Zähler.
 */
export function focusStateOf(session, now) {
  if (!session || isSessionFinished(session)) return { state: FocusState.READY, session: null, remaining: 0 };

  if (session.pausedAt !== null && session.pausedAt !== undefined) {
    return { state: FocusState.PAUSED, session, remaining: remainingAt(session, session.pausedAt) };
  }

  const remaining = remainingAt(session, now);
  if (remaining <= 0) return { state: FocusState.ELAPSED, session, remaining: 0 };
  return { state: FocusState.RUNNING, session, remaining };
}

/** Endzeitpunkt eines neuen Abschnitts. */
export function endsAt(start, phase, settings = DEFAULT_FOCUS_SETTINGS) {
  return start + phaseDurationMs(phase, settings);
}

/**
 * Fortsetzen nach einer Pause: Der Rest bleibt gleich, der Endzeitpunkt wandert um die
 * Standzeit nach hinten.
 */
export function resumedEndsAt(session, now) {
  if (session.pausedAt === null || session.pausedAt === undefined) return session.endsAt;
  return now + remainingAt(session, session.pausedAt);
}

/**
 * Welche Pause nach einer Fokusrunde folgt.
 *
 * `completedFocusRounds` zählt die heute abgeschlossenen Fokusrunden **einschließlich**
 * der gerade beendeten.
 */
export function breakAfterFocus(completedFocusRounds, settings = DEFAULT_FOCUS_SETTINGS) {
  const every = Math.max(1, settings.roundsBeforeLongBreak);
  return completedFocusRounds > 0 && completedFocusRounds % every === 0
    ? FocusPhase.LONG_BREAK
    : FocusPhase.SHORT_BREAK;
}

/**
 * Was nach dem Ablauf des Abschnitts ansteht.
 *
 * Nach der Fokusrunde beginnt die Pause von selbst; nach der Pause geht der Timer in den
 * Bereitzustand zurück und läuft **nicht** selbsttätig weiter — der Wiedereinstieg bleibt
 * eine Entscheidung.
 */
export function nextAfterPhase(phase, completedFocusRounds, settings = DEFAULT_FOCUS_SETTINGS) {
  if (isBreak(phase)) return { kind: "back_to_ready" };
  const breakPhase = breakAfterFocus(completedFocusRounds, settings);
  return { kind: "start_break", phase: breakPhase, duration: phaseDurationMs(breakPhase, settings) };
}

/** „18:42“ — die Anzeige im Fokus-Bildschirm und im Titel des Tabs. */
export function formatRemaining(remainingMs) {
  const seconds = Math.max(0, Math.ceil(remainingMs / 1000));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
}
