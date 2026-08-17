package uk.spielerbohne.petodo.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import uk.spielerbohne.petodo.domain.notify.NotificationIds
import java.time.Instant

/**
 * Exakte Alarme (Projektplan, Abschnitt 3: der einzige Weg, der Doze überlebt).
 *
 * Ein fehlendes Recht auf exakte Alarme darf nicht zum Absturz führen: Dann wird der
 * Alarm ungenau gesetzt statt gar nicht. Lieber eine Erinnerung mit ein paar Minuten
 * Versatz als keine.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    fun canScheduleExactAlarms(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> alarmManager?.canScheduleExactAlarms() == true
        else -> true
    }

    /** Setzt (oder ersetzt) den Alarm einer Aufgabe. */
    fun schedule(taskId: String, at: Instant) {
        val manager = alarmManager ?: return
        // FLAG_UPDATE_CURRENT legt an, wenn nötig — hier kommt nie null zurück.
        val pendingIntent = pendingIntent(taskId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return

        try {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at.toEpochMilli(),
                    pendingIntent,
                )
            } else {
                // Ohne das Recht auf exakte Alarme: ungenau, aber vorhanden.
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at.toEpochMilli(),
                    pendingIntent,
                )
            }
        } catch (security: SecurityException) {
            Log.w(TAG, "Alarm für $taskId konnte nicht exakt gesetzt werden", security)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pendingIntent)
        }
    }

    /** Nimmt den Alarm einer Aufgabe zurück. */
    fun cancel(taskId: String) {
        val manager = alarmManager ?: return
        val pendingIntent = pendingIntent(taskId, PendingIntent.FLAG_NO_CREATE) ?: return
        manager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** Prüft, ob für die Aufgabe überhaupt ein Alarm registriert ist. */
    fun isScheduled(taskId: String): Boolean =
        pendingIntent(taskId, PendingIntent.FLAG_NO_CREATE) != null

    private fun pendingIntent(taskId: String, extraFlags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            NotificationIds.alarmRequestCode(taskId),
            Intent(context, NagReceiver::class.java).apply {
                action = NagReceiver.ACTION_NAG
                // Die Daten-URI macht den Intent für PendingIntent-Vergleiche eindeutig;
                // Extras allein zählen dabei nicht.
                data = NagReceiver.taskUri(taskId)
                putExtra(NagReceiver.EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_IMMUTABLE or extraFlags,
        )

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
