package uk.spielerbohne.petodo.data.alarm

import android.util.Log
import uk.spielerbohne.petodo.data.notify.NagNotifications
import uk.spielerbohne.petodo.data.repo.TaskRepository
import uk.spielerbohne.petodo.data.settings.SettingsRepository
import uk.spielerbohne.petodo.domain.nag.NagDecision
import uk.spielerbohne.petodo.domain.nag.NagOutcome
import uk.spielerbohne.petodo.domain.nag.NagSchedule
import uk.spielerbohne.petodo.domain.nag.QuietHoursPolicy
import java.time.Clock
import java.time.Instant

/**
 * Führt aus, was `domain/nag/NagDecision` entschieden hat.
 *
 * Diese Klasse trifft keine eigene Entscheidung über Stufen, Termine oder Ruhezeiten —
 * sie liest die Aufgabe, fragt die reine Funktion und schreibt das Ergebnis in
 * Datenbank, AlarmManager und Benachrichtigungsschirm.
 */
class NagCoordinator(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val notifications: NagNotifications,
    private val clock: Clock,
) {

    /** Ein Alarm ist gefeuert. */
    suspend fun onAlarmFired(taskId: String) {
        val task = taskRepository.findTask(taskId) ?: run {
            notifications.cancel(taskId)
            scheduler.cancel(taskId)
            return
        }

        val outcome = NagDecision.decide(
            task = task,
            listExcludedFromNag = taskRepository.isListExcludedFromNag(task.listId),
            now = Instant.now(clock),
            zone = clock.zone,
            quietHours = settingsRepository.currentQuietHours(),
        )

        when (outcome) {
            is NagOutcome.Cancel -> {
                Log.d(TAG, "Kein Nag für $taskId: ${outcome.reason}")
                notifications.cancel(taskId)
                scheduler.cancel(taskId)
            }

            is NagOutcome.Reschedule -> {
                Log.d(TAG, "Nag für $taskId verschoben auf ${outcome.at} (${outcome.reason})")
                scheduler.schedule(taskId, outcome.at)
            }

            is NagOutcome.Post -> {
                val overdue = overdueTasks()
                notifications.postNag(
                    task = task,
                    stage = outcome.stage,
                    day = outcome.day,
                    grouped = NagDecision.shouldGroup(overdue.size),
                )
                taskRepository.markNagged(taskId, outcome.nagCount, Instant.now(clock))
                scheduler.schedule(taskId, outcome.nextNagAt)
                refreshSummary()
            }
        }
    }

    /**
     * Alarm einer einzelnen Aufgabe an ihren aktuellen Stand angleichen — nach dem
     * Anlegen, Bearbeiten, Abhaken oder Löschen.
     */
    suspend fun syncTask(taskId: String) {
        val task = taskRepository.findTask(taskId)
        if (task == null || !task.isOpen || task.dueAt == null ||
            taskRepository.isListExcludedFromNag(task.listId)
        ) {
            notifications.cancel(taskId)
            scheduler.cancel(taskId)
            refreshSummary()
            return
        }

        val now = Instant.now(clock)
        val quietHours = settingsRepository.currentQuietHours()
        val next = task.snoozedUntil?.takeIf { it.isAfter(now) }
            ?: NagSchedule.firstNagAt(task, clock.zone)?.takeIf { it.isAfter(now) }
            ?: NagSchedule.nextNagAt(task, now, clock.zone)

        scheduler.schedule(taskId, QuietHoursPolicy.shiftOutOfQuietHours(next, clock.zone, quietHours))
        refreshSummary()
    }

    /**
     * Alle offenen Alarme neu registrieren.
     *
     * Nach einem Neustart (Alarme überleben ihn nicht) und einmal täglich als
     * Sicherheitsnetz gegen verlorene Alarme.
     */
    suspend fun rescheduleAll(): Int {
        val tasks = taskRepository.openTasksWithDueDate()
        val excluded = taskRepository.listIdsExcludedFromNag()
        var count = 0
        tasks.forEach { task ->
            if (task.listId in excluded) {
                scheduler.cancel(task.id)
            } else {
                syncTask(task.id)
                count++
            }
        }
        Log.i(TAG, "$count Alarme neu registriert")
        return count
    }

    /** Erledigen nimmt die Meldung sofort zurück. */
    suspend fun onTaskCompleted(taskId: String) {
        notifications.cancel(taskId)
        scheduler.cancel(taskId)
        refreshSummary()
    }

    /** Sammelmeldung an den aktuellen Stand anpassen. */
    suspend fun refreshSummary() {
        val overdue = overdueTasks()
        if (NagDecision.shouldGroup(overdue.size)) {
            notifications.postSummary(overdue)
        } else {
            notifications.cancelSummary()
        }
    }

    private suspend fun overdueTasks() = taskRepository.overdueTasks(Instant.now(clock), clock.zone)

    private companion object {
        const val TAG = "NagCoordinator"
    }
}
