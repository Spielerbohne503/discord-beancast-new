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

/**
 * Der Start der App.
 *
 * Alles hier ist einzeln abgesichert. Der Grund ist bitter erfahren: Eine einzige
 * Ausnahme an dieser Stelle macht die App **unstartbar** — der Prozess stirbt, bevor
 * irgendein Bildschirm erscheint, und der Nutzer sieht nur „App wird wiederholt beendet“.
 * Kein Baustein hier ist so wichtig, dass sein Ausfall das Öffnen der App verhindern
 * darf.
 *
 * Die Statuszeile wird bewusst **nicht** hier gestartet: `onCreate` läuft auch, wenn der
 * Prozess im Hintergrund hochkommt — durch einen Alarm, das Widget oder
 * `MY_PACKAGE_REPLACED` nach einem Update. Ab Android 12 darf ein Foreground Service von
 * dort nicht starten. Sie startet in `MainActivity`, wo die App im Vordergrund ist.
 */
class PetodoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Als Allererstes: Ab hier wird jeder Absturz festgehalten — auch einer, der
        // beim Hochfahren der App selbst passiert.
        runCatching { CrashLog.install(this) }

        container = AppContainer(this)

        // Steckt die App in einer Absturzschleife, wird nur das Nötigste aufgebaut.
        // Alles, was den letzten Start umgebracht haben könnte, bleibt aus, damit
        // MainActivity überhaupt so weit kommt, den Bericht zu zeigen.
        val schleife = runCatching { CrashLog.crashedRecently(this) }.getOrDefault(false)
        if (schleife) {
            Log.w(TAG, "Absturzschleife erkannt — Hintergrundarbeit bleibt aus")
            return
        }

        // Kanäle existieren, bevor die erste Meldung kommt — sonst verschluckt Android sie.
        runCatching { Channels.ensureCreated(this) }
            .onFailure { Log.e(TAG, "Benachrichtigungskanäle konnten nicht angelegt werden", it) }

        // Sicherheitsnetz gegen verlorene Alarme.
        runCatching { AlarmSyncWorker.schedule(this) }
            .onFailure { Log.e(TAG, "Alarm-Abgleich konnte nicht eingeplant werden", it) }

        // Beim Start einmal aufräumen: Alarme an den Datenbankstand angleichen und den
        // Wertestand des Pets fortschreiben. Es tickt nichts — gerechnet wird hier und
        // bei Ereignissen.
        container.applicationScope.launch(Dispatchers.IO) {
            runCatching { container.nagCoordinator.rescheduleAll() }
                .onFailure { Log.e(TAG, "Alarme konnten beim Start nicht abgeglichen werden", it) }

            runCatching { container.petRepository.recompute() }
                .onFailure { Log.e(TAG, "Pet-Zustand konnte nicht fortgeschrieben werden", it) }
        }

        // Das Widget hängt an derselben Quelle wie alles andere: Ändert sich eine
        // Aufgabe — egal ob in der App, über eine Benachrichtigung oder vom
        // Startbildschirm aus —, zeichnet es sich neu.
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
