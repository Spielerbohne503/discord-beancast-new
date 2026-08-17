package uk.spielerbohne.petodo.domain.focus

import java.time.Duration
import java.time.Instant

/**
 * Was der Timer gerade tut.
 *
 * [Elapsed] ist der wichtige Fall: Die Zeit ist abgelaufen, aber noch niemand hat es
 * verbucht — weil das Gerät schlief, die App weg war oder der Dienst abgeschossen wurde.
 * Dieser Zustand heißt **fertig**, nicht "läuft noch".
 */
sealed interface FocusState {

    /** Kein Durchlauf aktiv. */
    data object Ready : FocusState

    data class Running(val session: FocusSession, val remaining: Duration) : FocusState

    data class Paused(val session: FocusSession, val remaining: Duration) : FocusState

    /** Endzeitpunkt erreicht oder überschritten, Abschluss steht noch aus. */
    data class Elapsed(val session: FocusSession) : FocusState
}

/** Was nach dem Ablauf eines Abschnitts geschehen soll. */
sealed interface FocusNext {

    /** Nach der Fokusrunde beginnt die Pause von selbst. */
    data class StartBreak(val phase: FocusPhase, val duration: Duration) : FocusNext

    /**
     * Nach der Pause geht der Timer in den Bereitzustand zurück und läuft **nicht**
     * selbsttätig weiter — der Wiedereinstieg bleibt eine Entscheidung.
     */
    data object BackToReady : FocusNext
}

object FocusTimer {

    /**
     * Der Zustand zu einem Zeitpunkt.
     *
     * Gerechnet wird immer gegen den gespeicherten Endzeitpunkt, nie gegen einen
     * mitlaufenden Zähler: Ein Countdown driftet und steht im Energiesparmodus still.
     */
    fun stateOf(session: FocusSession?, now: Instant): FocusState {
        if (session == null || session.isFinished) return FocusState.Ready

        session.pausedAt?.let { pausedAt ->
            return FocusState.Paused(session, remainingAt(session, pausedAt))
        }

        val remaining = remainingAt(session, now)
        return if (remaining.isZero || remaining.isNegative) {
            FocusState.Elapsed(session)
        } else {
            FocusState.Running(session, remaining)
        }
    }

    /** Verbleibende Zeit, nie negativ. */
    fun remainingAt(session: FocusSession, at: Instant): Duration {
        val remaining = Duration.between(at, session.endsAt)
        return if (remaining.isNegative) Duration.ZERO else remaining
    }

    /** Endzeitpunkt eines neuen Abschnitts. */
    fun endsAt(start: Instant, phase: FocusPhase, settings: FocusSettings): Instant =
        start.plus(settings.durationOf(phase))

    /**
     * Fortsetzen nach einer Pause: Der Rest bleibt gleich, der Endzeitpunkt wandert um
     * die Standzeit nach hinten.
     */
    fun resumedEndsAt(session: FocusSession, now: Instant): Instant {
        val pausedAt = session.pausedAt ?: return session.endsAt
        return now.plus(remainingAt(session, pausedAt))
    }

    /**
     * Welche Pause nach einer Fokusrunde folgt.
     *
     * [completedFocusRounds] zählt die heute bereits abgeschlossenen Fokusrunden
     * **einschließlich** der gerade beendeten.
     */
    fun breakAfterFocus(completedFocusRounds: Int, settings: FocusSettings): FocusPhase {
        val every = settings.roundsBeforeLongBreak.coerceAtLeast(1)
        return if (completedFocusRounds > 0 && completedFocusRounds % every == 0) {
            FocusPhase.LONG_BREAK
        } else {
            FocusPhase.SHORT_BREAK
        }
    }

    /** Was nach dem Ablauf des Abschnitts ansteht. */
    fun next(phase: FocusPhase, completedFocusRounds: Int, settings: FocusSettings): FocusNext =
        if (phase.isBreak) {
            FocusNext.BackToReady
        } else {
            val breakPhase = breakAfterFocus(completedFocusRounds, settings)
            FocusNext.StartBreak(breakPhase, settings.durationOf(breakPhase))
        }

    /** "18:42" — die Anzeige für Statuszeile und Fokus-Screen. */
    fun format(remaining: Duration): String {
        val total = remaining.seconds.coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }
}
