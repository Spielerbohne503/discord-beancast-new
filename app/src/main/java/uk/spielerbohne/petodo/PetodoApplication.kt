package uk.spielerbohne.petodo

import android.app.Application
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import uk.spielerbohne.petodo.data.focus.FocusAction
import uk.spielerbohne.petodo.data.focus.FocusService
import uk.spielerbohne.petodo.data.notify.Channels
import uk.spielerbohne.petodo.data.widget.TodayWidgetProvider
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

            // Es tickt nichts: Der Wertestand wird beim Start einmal fortgeschrieben und
            // danach nur noch bei Ereignissen.
            runCatching { container.petRepository.recompute() }
                .onFailure { Log.e(TAG, "Pet-Zustand konnte nicht fortgeschrieben werden", it) }
        }

        // Das Widget hängt an derselben Quelle wie alles andere: Ändert sich eine
        // Aufgabe — egal ob in der App, über eine Benachrichtigung oder vom
        // Startbildschirm aus —, zeichnet es sich neu. Ein Widget, das die Liste von
        // gestern zeigt, ist schlimmer als keins.
        container.applicationScope.launch(Dispatchers.IO) {
            container.taskRepository.observeTasks()
                .distinctUntilChanged()
                // Während neu gezeichnet wird, fallen zwischenzeitliche Stände weg —
                // gezeichnet wird ohnehin nur der letzte.
                .conflate()
                .collect {
                    runCatching { TodayWidgetProvider.refresh(this@PetodoApplication) }
                        .onFailure { fehler -> Log.w(TAG, "Widget konnte nicht aktualisiert werden", fehler) }
                }
        }
    }

    private companion object {
        const val TAG = "PetodoApplication"
    }
}
