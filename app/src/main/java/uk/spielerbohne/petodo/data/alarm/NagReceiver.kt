package uk.spielerbohne.petodo.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.spielerbohne.petodo.PetodoApplication
import uk.spielerbohne.petodo.di.launchGuarded

/**
 * Empfängt den exakten Alarm einer Aufgabe.
 *
 * Der Receiver entscheidet nichts. Er holt die Aufgabe, lässt
 * `domain/nag/NagDecision` entscheiden und führt aus — mehr steht hier bewusst nicht.
 */
class NagReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val container = (context.applicationContext as PetodoApplication).container

        // Ein Broadcast darf nur ~10 Sekunden laufen: goAsync() hält den Prozess so
        // lange am Leben, bis die Datenbankarbeit fertig ist.
        val result = goAsync()
        container.applicationScope.launchGuarded(
            onFinally = { result.finish() },
            onError = { Log.e(TAG, "Nag für $taskId fehlgeschlagen", it) },
        ) {
            withContext(Dispatchers.IO) {
                container.nagCoordinator.onAlarmFired(taskId)
            }
        }
    }

    companion object {
        const val ACTION_NAG = "uk.spielerbohne.petodo.action.NAG"
        const val EXTRA_TASK_ID = "uk.spielerbohne.petodo.extra.TASK_ID"

        /** Macht den PendingIntent je Aufgabe eindeutig — Extras allein zählen dabei nicht. */
        fun taskUri(taskId: String): Uri = Uri.parse("petodo://task/$taskId")

        private const val TAG = "NagReceiver"
    }
}
