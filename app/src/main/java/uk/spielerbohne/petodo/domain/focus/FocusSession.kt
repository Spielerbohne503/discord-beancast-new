package uk.spielerbohne.petodo.domain.focus

import uk.spielerbohne.petodo.domain.Balance
import java.time.Duration
import java.time.Instant

/** Die drei Abschnitte eines Pomodoro-Zyklus. */
enum class FocusPhase(val isBreak: Boolean) {
    FOCUS(isBreak = false),
    SHORT_BREAK(isBreak = true),
    LONG_BREAK(isBreak = true);

    companion object {
        /** Unbekannte Werte aus der Datenbank sind kein Absturzgrund. */
        fun parse(value: String?): FocusPhase? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Einstellungen des Timers. Die Vorgaben stehen in [Balance], nicht hier.
 */
data class FocusSettings(
    val focusMinutes: Long = Balance.FOCUS_DEFAULT_MINUTES,
    val shortBreakMinutes: Long = Balance.SHORT_BREAK_DEFAULT_MINUTES,
    val longBreakMinutes: Long = Balance.LONG_BREAK_DEFAULT_MINUTES,
    val roundsBeforeLongBreak: Int = Balance.ROUNDS_BEFORE_LONG_BREAK,
) {
    fun durationOf(phase: FocusPhase): Duration = Duration.ofMinutes(
        when (phase) {
            FocusPhase.FOCUS -> focusMinutes
            FocusPhase.SHORT_BREAK -> shortBreakMinutes
            FocusPhase.LONG_BREAK -> longBreakMinutes
        }.coerceAtLeast(1)
    )

    companion object {
        val DEFAULT = FocusSettings()
    }
}

/**
 * Eine Fokussitzung.
 *
 * [endsAt] ist der **absolute** Endzeitpunkt — kein heruntergezählter Rest. Ein Countdown
 * driftet und steht im Energiesparmodus still; ein Zeitpunkt tut das nicht. Beim Anhalten
 * merkt sich [pausedAt] den Moment, damit der Rest beim Fortsetzen wieder stimmt.
 */
data class FocusSession(
    val id: String,
    val taskId: String? = null,
    val phase: FocusPhase,
    val startedAt: Instant,
    val endsAt: Instant,
    val pausedAt: Instant? = null,
    val completedAt: Instant? = null,
    val abortedAt: Instant? = null,
) {
    val isPaused: Boolean get() = pausedAt != null
    val isFinished: Boolean get() = completedAt != null || abortedAt != null

    /** Abgebrochene Runden geben keine Belohnung — das entscheidet sich hier. */
    val earnsReward: Boolean get() = completedAt != null && abortedAt == null && phase == FocusPhase.FOCUS
}
