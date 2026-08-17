package uk.spielerbohne.petodo.data.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import uk.spielerbohne.petodo.PetodoApplication
import java.util.concurrent.TimeUnit

/**
 * Täglicher Abgleich der registrierten Alarme gegen die Datenbank.
 *
 * Sicherheitsnetz gegen verlorene Alarme: Hersteller-Prozesskiller, aggressive
 * Akkuverwaltung und abgebrochene Broadcasts kosten hier und da einen Alarm. Der Job
 * setzt sie einfach wieder — er ist absichtlich stumpf und ohne Zustand.
 */
class AlarmSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PetodoApplication).container
        return try {
            val count = container.nagCoordinator.rescheduleAll()
            Log.i(TAG, "Abgleich abgeschlossen: $count Alarme")
            Result.success()
        } catch (throwable: Throwable) {
            Log.e(TAG, "Abgleich fehlgeschlagen", throwable)
            // Erneut versuchen statt aufgeben: ein stummer Tag ist teurer als ein
            // zweiter Anlauf.
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AlarmSyncWorker"
        private const val UNIQUE_NAME = "alarm-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlarmSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
