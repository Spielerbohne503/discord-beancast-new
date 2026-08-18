package uk.spielerbohne.petodo

import android.app.Application
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import uk.spielerbohne.petodo.data.debug.CrashLog
import uk.spielerbohne.petodo.data.notify.Channels
import uk.spielerbohne.petodo.data.widget.TodayWidgetProvider
import uk.spielerbohne.petodo.data.work.AlarmSyncWorker
import uk.spielerbohne.petodo.di.AppContainer

class PetodoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Als Allererstes: Ab hier wird jeder Absturz festgehalten, auch einer, der beim
        // Aufbauen der App selbst passiert.
        CrashLog.install(this)

        container = AppContainer(this)

        // Kanäle existieren, bevor die erste Meldung kommt — sonst verschluckt Android sie.
        Channels.ensureCreated(this)

        // Sicherheitsnetz gegen verlorene Alarme.
        AlarmSyncWorker.schedule(this)

        // Die Statuszeile wird bewusst NICHT hier gestartet.
        //
        // `Application.onCreate` läuft auch dann, wenn der Prozess im Hintergrund
        // hochkommt — durch einen Alarm, das Widget oder MY_PACKAGE_REPLACED direkt nach
        // einem Update. Ein Foreground Service darf ab Android 12 aus dem Hintergrund
        // nicht starten; der Dienst stirbt dann und wird über START_STICKY endlos neu
        // gestartet. Das ist die Schleife, die sich als "App wird wiederholt beendet"
        // zeigt. Die Statuszeile startet deshalb aus der Activity — dort ist die App
        // garantiert im Vordergrund.

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
                // Auch ein Fehler in der Quelle selbst darf die App nicht mitreißen.
                .catch { fehler -> Log.e(TAG, "Aufgabenstrom für das Widget abgebrochen", fehler) }
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
