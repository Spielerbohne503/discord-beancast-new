package uk.spielerbohne.petodo.data.focus

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.spielerbohne.petodo.PetodoApplication
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.focus.FocusNext
import uk.spielerbohne.petodo.domain.focus.FocusPhase
import uk.spielerbohne.petodo.domain.focus.FocusState
import uk.spielerbohne.petodo.domain.focus.FocusTimer
import kotlinx.coroutines.flow.first
import uk.spielerbohne.petodo.domain.notify.NotificationIds
import uk.spielerbohne.petodo.domain.today.TodayGrouping
import java.time.Instant

/**
 * Foreground Service für den Fokus-Timer.
 *
 * Der Dienst zählt nichts herunter — er zeichnet nur einmal pro Sekunde neu, was
 * [FocusTimer] aus dem gespeicherten Endzeitpunkt errechnet. Wird er abgeschossen und
 * später neu gestartet, stimmt die Anzeige trotzdem: Der Zeitpunkt steht in der
 * Datenbank, nicht im Arbeitsspeicher.
 */
class FocusService : Service() {

    private lateinit var container: AppContainer
    private lateinit var status: FocusStatusNotification
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as PetodoApplication).container
        status = FocusStatusNotification(this)
        // Sofort in den Vordergrund, sonst schießt Android den Dienst ab.
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(EXTRA_ACTION)
            ?.let { runCatching { FocusAction.valueOf(it) }.getOrNull() }

        scope.launch {
            action?.let { handle(it, intent.getStringExtra(EXTRA_TASK_ID)) }
            startTicking()
        }
        return START_STICKY
    }

    private suspend fun handle(action: FocusAction, taskId: String?) {
        val focus = container.focusRepository
        val settings = container.settingsRepository.currentFocusSettings()

        when (action) {
            FocusAction.START_FOCUS -> focus.start(FocusPhase.FOCUS, settings, taskId)
            FocusAction.PAUSE -> focus.pause()
            FocusAction.RESUME -> focus.resume()
            // Abbrechen und Überspringen enden beide im Bereitzustand; die Statuszeile
            // bleibt stehen, sie ist keine Timer-Anzeige, sondern der Tagesstand.
            FocusAction.STOP, FocusAction.SKIP -> focus.abort()
            // Nichts ändern, nur weiterzeichnen.
            FocusAction.ADD_TASK, FocusAction.RESUME_DISPLAY -> Unit
        }
    }

    /**
     * Zeichnet die Statuszeile neu und verbucht abgelaufene Abschnitte.
     *
     * Läuft ein Abschnitt, wird sekündlich neu gezeichnet; im Bereitzustand nur noch
     * jede Minute — dort ändert sich nur die Zahl der erledigten Aufgaben.
     */
    private fun startTicking() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                val running = refresh()
                delay(if (running) TICK_RUNNING_MILLIS else TICK_IDLE_MILLIS)
            }
        }
    }

    /** @return true, solange ein Abschnitt läuft. */
    private suspend fun refresh(): Boolean {
        val focus = container.focusRepository
        val session = focus.activeSession()
        val state = FocusTimer.stateOf(session, Instant.now(container.clock))

        if (state is FocusState.Elapsed) {
            onElapsed(state)
            return true
        }

        val taskTitle = session?.taskId?.let { container.taskRepository.findTask(it)?.title }
        notify(state, taskTitle, todayCounts())
        return state is FocusState.Running
    }

    /**
     * "3 von 7 heute": erledigte von heute anstehenden Aufgaben — überfällige, heutige
     * und die bereits abgehakten.
     */
    private suspend fun todayCounts(): Pair<Int, Int> {
        val board = TodayGrouping.group(
            container.taskRepository.observeTasks().first(),
            Instant.now(container.clock),
            container.clock.zone,
        )
        val done = board.doneToday.size
        return done to (done + board.overdue.size + board.today.size)
    }

    /**
     * Ein Abschnitt ist abgelaufen: Fokusrunde abschließen und die Pause automatisch
     * starten. Nach der Pause bleibt der Timer im Bereitzustand stehen — der
     * Wiedereinstieg ist eine Entscheidung, keine Automatik.
     */
    private suspend fun onElapsed(state: FocusState.Elapsed) {
        val focus = container.focusRepository
        val settings = container.settingsRepository.currentFocusSettings()

        focus.complete(state.session.id)
        val rounds = focus.completedRoundsToday()

        when (val next = FocusTimer.next(state.session.phase, rounds, settings)) {
            is FocusNext.StartBreak -> focus.start(next.phase, settings, state.session.taskId)
            FocusNext.BackToReady -> Unit
        }
    }

    /**
     * Ohne Benachrichtigungserlaubnis bleibt die Statuszeile unsichtbar — der Dienst
     * läuft trotzdem weiter, und der Timer stimmt, weil er am Endzeitpunkt hängt.
     */
    private fun notify(state: FocusState, taskTitle: String?, counts: Pair<Int, Int>) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = status.build(state, taskTitle, counts.first, counts.second)
        try {
            NotificationManagerCompat.from(this).notify(NotificationIds.FOCUS_STATUS, notification)
        } catch (security: SecurityException) {
            Log.w(TAG, "Statuszeile konnte nicht aktualisiert werden", security)
        }
    }

    private fun startForegroundCompat() {
        val notification = status.build(FocusState.Ready, null, 0, 0)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NotificationIds.FOCUS_STATUS, notification, type)
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FocusService"
        private const val TICK_RUNNING_MILLIS = 1_000L
        private const val TICK_IDLE_MILLIS = 60_000L

        const val EXTRA_ACTION = "uk.spielerbohne.petodo.extra.FOCUS_ACTION"
        const val EXTRA_TASK_ID = "uk.spielerbohne.petodo.extra.FOCUS_TASK_ID"

        fun intent(context: Context, action: FocusAction, taskId: String? = null): Intent =
            Intent(context, FocusService::class.java).apply {
                putExtra(EXTRA_ACTION, action.name)
                taskId?.let { putExtra(EXTRA_TASK_ID, it) }
            }

        fun send(context: Context, action: FocusAction, taskId: String? = null) {
            context.startForegroundService(intent(context, action, taskId))
        }

        fun pendingIntent(context: Context, action: FocusAction): PendingIntent =
            PendingIntent.getForegroundService(
                context,
                NotificationIds.requestCodeForFocus(action.name),
                intent(context, action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
