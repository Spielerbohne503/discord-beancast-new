package uk.spielerbohne.petodo.data.notify

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.data.alarm.TaskActionReceiver
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.domain.nag.NagStage
import uk.spielerbohne.petodo.domain.notify.NotificationIds
import uk.spielerbohne.petodo.domain.notify.TaskNotificationAction
import uk.spielerbohne.petodo.ui.MainActivity

/**
 * Baut und postet die Erinnerungen.
 *
 * Hier steckt keine Entscheidung: *Ob* und *auf welcher Stufe* gemeldet wird, hat
 * `domain/nag/NagDecision` bereits entschieden. Diese Klasse übersetzt das Ergebnis nur
 * in eine Android-Benachrichtigung.
 */
class NagNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Meldung zu einer Aufgabe posten bzw. die bestehende aktualisieren. */
    fun postNag(task: Task, stage: NagStage, day: Int, grouped: Boolean) {
        if (!hasPermission()) return

        val builder = NotificationCompat.Builder(context, Channels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(bodyFor(stage, day))
            .setContentIntent(openAppIntent(task.id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(!stage.makesSound)
            .setSilent(!stage.makesSound)
            .setPriority(if (stage.makesSound) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        if (grouped) {
            builder.setGroup(Channels.GROUP_OVERDUE)
        }

        task.note?.takeIf { it.isNotBlank() }?.let {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }

        // Aktionsknöpfe: Erledigt / +1 Std / Morgen — direkt aus der Meldung heraus,
        // ohne die App zu öffnen. Ab Tag 7 zusätzlich die Aufräum-Frage.
        builder.addAction(action(task.id, TaskNotificationAction.DONE, R.string.notification_action_done))
        if (stage.asksCleanupQuestion) {
            builder.addAction(action(task.id, TaskNotificationAction.RESCHEDULE, R.string.notification_action_reschedule))
            builder.addAction(action(task.id, TaskNotificationAction.DELETE, R.string.notification_action_delete))
        } else {
            builder.addAction(action(task.id, TaskNotificationAction.SNOOZE_HOUR, R.string.notification_action_snooze_hour))
            builder.addAction(action(task.id, TaskNotificationAction.TOMORROW, R.string.notification_action_tomorrow))
        }

        notifySafely(NotificationIds.forTask(task.id), builder.build())
    }

    /**
     * Sammelmeldung ab drei überfälligen Aufgaben: eine Meldung mit aufklappbarer Liste
     * statt fünf Einzelvibrationen.
     */
    fun postSummary(overdue: List<Task>) {
        if (!hasPermission()) return

        val style = NotificationCompat.InboxStyle()
            .setSummaryText(context.resources.getQuantityString(R.plurals.notification_summary_title, overdue.size, overdue.size))
        overdue.take(MAX_SUMMARY_LINES).forEach { style.addLine(it.title) }

        val notification = NotificationCompat.Builder(context, Channels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.resources.getQuantityString(R.plurals.notification_summary_title, overdue.size, overdue.size)
            )
            .setStyle(style)
            .setGroup(Channels.GROUP_OVERDUE)
            .setGroupSummary(true)
            .setContentIntent(openAppIntent(null))
            .setSilent(true)
            .setAutoCancel(true)
            .build()

        notifySafely(NotificationIds.SUMMARY_OVERDUE, notification)
    }

    /** Erledigen nimmt die Meldung sofort zurück (Projektplan, Phase-2-Punkt 10). */
    fun cancel(taskId: String) {
        manager.cancel(NotificationIds.forTask(taskId))
    }

    fun cancelSummary() {
        manager.cancel(NotificationIds.SUMMARY_OVERDUE)
    }

    /**
     * Posten mit doppeltem Boden: Die Erlaubnis wird geprüft, und falls sie zwischen
     * Prüfung und Zustellung entzogen wird, fällt die Meldung aus — die App aber nicht.
     */
    private fun notifySafely(id: Int, notification: Notification) {
        if (!hasPermission()) return
        try {
            manager.notify(id, notification)
        } catch (security: SecurityException) {
            Log.w(TAG, "Meldung $id konnte nicht gepostet werden", security)
        }
    }

    private fun bodyFor(stage: NagStage, day: Int): String = when (stage) {
        NagStage.FIRST -> context.getString(R.string.notification_body_first)
        NagStage.AGAIN -> context.getString(R.string.notification_body_again)
        NagStage.LOUD -> context.resources.getQuantityString(R.plurals.notification_body_loud, day, day)
        NagStage.CLEANUP -> context.getString(R.string.notification_body_cleanup)
    }

    private fun action(taskId: String, action: TaskNotificationAction, labelRes: Int) =
        NotificationCompat.Action.Builder(
            0,
            context.getString(labelRes),
            TaskActionReceiver.pendingIntent(context, taskId, action),
        ).build()

    private fun openAppIntent(taskId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            taskId?.let { putExtra(EXTRA_TASK_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            taskId?.let { NotificationIds.requestCode(it, TaskNotificationAction.OPEN) } ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val EXTRA_TASK_ID = "uk.spielerbohne.petodo.extra.TASK_ID"
        private const val MAX_SUMMARY_LINES = 6
        private const val TAG = "NagNotifications"
    }
}
