package uk.spielerbohne.petodo.domain.nag

import uk.spielerbohne.petodo.domain.Balance
import uk.spielerbohne.petodo.domain.model.Task
import java.time.Instant
import java.time.ZoneId

/**
 * Was beim Feuern eines Alarms zu tun ist.
 *
 * Der Receiver entscheidet nichts — er ruft [NagDecision.decide] auf und führt das
 * Ergebnis aus. Damit ist die gesamte Nag-Logik ohne Emulator testbar.
 */
sealed interface NagOutcome {

    /**
     * Nichts melden, bestehende Meldung zurücknehmen, keinen neuen Alarm setzen.
     * Gilt für erledigte, gelöschte, undatierte Aufgaben und für Listen mit
     * `excludeFromNag`.
     */
    data class Cancel(val reason: Reason) : NagOutcome {
        enum class Reason { COMPLETED, DELETED, NO_DUE_DATE, LIST_EXCLUDED }
    }

    /** Noch nicht melden, nur neu einplanen. */
    data class Reschedule(val at: Instant, val reason: Reason) : NagOutcome {
        enum class Reason { SNOOZED, NOT_DUE_YET, QUIET_HOURS }
    }

    /** Melden, `nagCount` hochzählen und den nächsten Termin setzen. */
    data class Post(
        val stage: NagStage,
        /** Der wievielte Nag-Tag gemeldet wird (1-basiert). */
        val day: Int,
        /** Neuer Zählerstand, der in die Datenbank geschrieben wird. */
        val nagCount: Int,
        val nextNagAt: Instant,
    ) : NagOutcome
}

object NagDecision {

    /**
     * Die eine Entscheidung, die der Alarm-Receiver braucht.
     *
     * Reihenfolge der Prüfungen ist bewusst so gewählt:
     * 1. Gibt es überhaupt noch etwas zu mahnen?
     * 2. Hat der Nutzer aufgeschoben? (Sein Wille schlägt den Zeitplan.)
     * 3. Ist der Termin überhaupt schon erreicht?
     * 4. Ist gerade Ruhezeit? (Verschieben, nie verwerfen.)
     * 5. Sonst: melden.
     */
    fun decide(
        task: Task,
        listExcludedFromNag: Boolean,
        now: Instant,
        zone: ZoneId,
        quietHours: QuietHours,
    ): NagOutcome {
        if (task.isDeleted) return NagOutcome.Cancel(NagOutcome.Cancel.Reason.DELETED)
        if (task.isCompleted) return NagOutcome.Cancel(NagOutcome.Cancel.Reason.COMPLETED)
        if (listExcludedFromNag) return NagOutcome.Cancel(NagOutcome.Cancel.Reason.LIST_EXCLUDED)
        val due = task.dueAt ?: return NagOutcome.Cancel(NagOutcome.Cancel.Reason.NO_DUE_DATE)

        task.snoozedUntil?.takeIf { it.isAfter(now) }?.let { snoozed ->
            return NagOutcome.Reschedule(
                at = QuietHoursPolicy.shiftOutOfQuietHours(snoozed, zone, quietHours),
                reason = NagOutcome.Reschedule.Reason.SNOOZED,
            )
        }

        val firstNag = NagSchedule.firstNagAt(task, zone) ?: due
        if (firstNag.isAfter(now)) {
            return NagOutcome.Reschedule(
                at = QuietHoursPolicy.shiftOutOfQuietHours(firstNag, zone, quietHours),
                reason = NagOutcome.Reschedule.Reason.NOT_DUE_YET,
            )
        }

        if (quietHours.contains(now.atZone(zone).toLocalTime())) {
            return NagOutcome.Reschedule(
                at = QuietHoursPolicy.shiftOutOfQuietHours(now, zone, quietHours),
                reason = NagOutcome.Reschedule.Reason.QUIET_HOURS,
            )
        }

        val stage = NagEscalation.stageFor(task.nagCount)
        return NagOutcome.Post(
            stage = stage,
            day = NagEscalation.dayFor(task.nagCount),
            nagCount = task.nagCount + 1,
            nextNagAt = QuietHoursPolicy.shiftOutOfQuietHours(
                NagSchedule.nextNagAt(task, now, zone),
                zone,
                quietHours,
            ),
        )
    }

    /**
     * Ab drei überfälligen Aufgaben eine gruppierte Meldung statt Einzelvibrationen —
     * sonst schaltet man nach einer Woche alles stumm.
     */
    fun shouldGroup(overdueCount: Int): Boolean = overdueCount >= Balance.NAG_GROUP_THRESHOLD
}
