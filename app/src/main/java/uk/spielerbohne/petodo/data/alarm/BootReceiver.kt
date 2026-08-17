package uk.spielerbohne.petodo.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.spielerbohne.petodo.PetodoApplication
import uk.spielerbohne.petodo.di.launchGuarded

/**
 * Registriert alle offenen Alarme nach einem Neustart neu.
 *
 * Alarme überleben keinen Neustart — das ist der Klassiker, an dem selbstgebaute
 * Task-Apps sterben. Ohne diesen Receiver wird die App nach dem ersten Neustart stumm,
 * ohne dass es jemand merkt.
 *
 * Reagiert auch auf `MY_PACKAGE_REPLACED`: Nach einem App-Update sind die Alarme
 * ebenfalls weg.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val container = (context.applicationContext as PetodoApplication).container
        val result = goAsync()

        container.applicationScope.launchGuarded(
            onFinally = { result.finish() },
            onError = { Log.e(TAG, "Alarme konnten nach ${intent.action} nicht neu gesetzt werden", it) },
        ) {
            withContext(Dispatchers.IO) {
                val count = container.nagCoordinator.rescheduleAll()
                Log.i(TAG, "Nach ${intent.action}: $count Alarme wiederhergestellt")
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"

        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            // Hersteller-Variante, u. a. bei HTC
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
