package uk.spielerbohne.petodo.data.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.spielerbohne.petodo.PetodoApplication
import uk.spielerbohne.petodo.di.launchGuarded
import uk.spielerbohne.petodo.domain.Balance
import uk.spielerbohne.petodo.domain.notify.NotificationIds
import uk.spielerbohne.petodo.domain.notify.TaskNotificationAction
import java.time.Instant

/**
 * Die Aktionsknöpfe aus der Benachrichtigung: Erledigt · +1 Std · Morgen — und ab Tag 7
 * die Antworten auf die Aufräum-Frage.
 *
 * Alles ohne die App zu öffnen. Die Meldung wird in jedem Fall sofort zurückgenommen:
 * Ein Knopf, der nichts sichtbar tut, wird beim nächsten Mal nicht mehr gedrückt.
 */
class TaskActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?.let { runCatching { TaskNotificationAction.valueOf(it) }.getOrNull() }
            ?: return

        val container = (context.applicationContext as PetodoApplication).container
        val result = goAsync()

        container.applicationScope.launchGuarded(
            onFinally = { result.finish() },
            onError = { Log.e(TAG, "Aktion $action für $taskId fehlgeschlagen", it) },
        ) {
            withContext(Dispatchers.IO) {
                val tasks = container.taskRepository
                val coordinator = container.nagCoordinator
                container.nagNotifications.cancel(taskId)

                when (action) {
                    TaskNotificationAction.DONE -> {
                        tasks.setCompleted(taskId, completed = true)
                        coordinator.onTaskCompleted(taskId)
                    }

                    TaskNotificationAction.SNOOZE_HOUR -> {
                        val until = Instant.now(container.clock)
                            .plusSeconds(Balance.SNOOZE_MINUTES * 60)
                        tasks.snooze(taskId, until)
                        coordinator.syncTask(taskId)
                    }

                    TaskNotificationAction.TOMORROW -> {
                        tasks.postponeToTomorrow(taskId)
                        coordinator.syncTask(taskId)
                    }

                    TaskNotificationAction.RESCHEDULE -> {
                        // Antwort auf die Aufräum-Frage: entschieden ist entschieden.
                        tasks.postponeToTomorrow(taskId)
                        coordinator.syncTask(taskId)
                    }

                    TaskNotificationAction.DELETE -> {
                        tasks.delete(taskId)
                        coordinator.onTaskCompleted(taskId)
                    }

                    TaskNotificationAction.OPEN -> Unit
                }
            }
        }
    }

    companion object {
        const val ACTION_TASK = "uk.spielerbohne.petodo.action.TASK_ACTION"
        const val EXTRA_TASK_ID = "uk.spielerbohne.petodo.extra.TASK_ID"
        const val EXTRA_ACTION = "uk.spielerbohne.petodo.extra.ACTION"

        private const val TAG = "TaskActionReceiver"

        fun pendingIntent(
            context: Context,
            taskId: String,
            action: TaskNotificationAction,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            NotificationIds.requestCode(taskId, action),
            Intent(context, TaskActionReceiver::class.java).apply {
                this.action = ACTION_TASK
                data = Uri.parse("petodo://task/$taskId/${action.name}")
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_ACTION, action.name)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
