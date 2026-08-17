package uk.spielerbohne.petodo

import android.app.Application
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.spielerbohne.petodo.data.focus.FocusAction
import uk.spielerbohne.petodo.data.focus.FocusService
import uk.spielerbohne.petodo.data.notify.Channels
import uk.spielerbohne.petodo.data.work.AlarmSyncWorker
import uk.spielerbohne.petodo.di.AppContainer

class PetodoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Kanäle existieren, bevor die erste Meldung kommt — sonst verschluckt Android sie.
        Channels.ensureCreated(this)

        // Sicherheitsnetz gegen verlorene Alarme.
        AlarmSyncWorker.schedule(this)

        // Die Statuszeile ist dauerhaft — sie zeigt auch ohne Timer den Tagesstand.
        runCatching { FocusService.send(this, FocusAction.RESUME_DISPLAY) }
            .onFailure { Log.w(TAG, "Statuszeile konnte nicht gestartet werden", it) }

        // Beim Start einmal aufräumen: Alarme an den Datenbankstand angleichen und die
        // Sammelmeldung aktualisieren. Ein Fehler hier darf den Start nicht verhindern.
        container.applicationScope.launch(Dispatchers.IO) {
            runCatching { container.nagCoordinator.rescheduleAll() }
                .onFailure { Log.e(TAG, "Alarme konnten beim Start nicht abgeglichen werden", it) }
        }
    }

    private companion object {
        const val TAG = "PetodoApplication"
    }
}
