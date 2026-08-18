package uk.spielerbohne.petodo.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.spielerbohne.petodo.PetodoApplication
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.data.pet.emojiRes
import uk.spielerbohne.petodo.di.launchGuarded
import uk.spielerbohne.petodo.domain.today.TodayGrouping
import uk.spielerbohne.petodo.ui.MainActivity
import java.time.Instant

/**
 * Das Homescreen-Widget: die Heute-Liste ohne die App zu öffnen.
 *
 * Bewusst mit `RemoteViews` statt mit einer Widget-Bibliothek — die wäre eine weitere
 * Abhängigkeit für etwas, das die Plattform seit Jahren kann. Der Preis sind XML-Layouts
 * statt Compose; der Gewinn ist, dass das APK nicht wächst.
 *
 * Angetippt wird abgehakt, und zwar über dasselbe Repository wie überall sonst: Auch vom
 * Homescreen aus zahlt eine erledigte Aufgabe beim Pet ein.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Ein Widget-Fehler darf die App nicht mitreißen: Ein Provider läuft im Prozess
        // der App, und eine Ausnahme hier beendet ihn — mitsamt der App, die man gerade
        // öffnen wollte.
        runCatching {
            // Erst den Rahmen — der braucht keine Datenbank und steht sofort.
            ids.forEach { id -> renderFrame(context, manager, id) }
            // Die Zahlen der Kopfzeile kommen nach. onUpdate läuft auf dem Hauptfaden;
            // dort die Datenbank zu lesen wäre ein hängender Startbildschirm.
            updateHeader(context, manager, ids)
        }.onFailure { Log.e(TAG, "Widget konnte nicht gezeichnet werden", it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        runCatching { super.onReceive(context, intent) }
            .onFailure { Log.e(TAG, "Widget-Nachricht ${intent.action} fehlgeschlagen", it) }

        if (intent.action != ACTION_TOGGLE) return

        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val container = (context.applicationContext as PetodoApplication).container
        val result = goAsync()

        container.applicationScope.launchGuarded(
            onFinally = { result.finish() },
            onError = { Log.e(TAG, "Abhaken aus dem Widget fehlgeschlagen", it) },
        ) {
            withContext(Dispatchers.IO) {
                val aufgabe = container.taskRepository.findTask(taskId) ?: return@withContext
                container.taskRepository.setCompleted(taskId, completed = !aufgabe.isCompleted)
                container.nagCoordinator.onTaskCompleted(taskId)
            }
            refresh(context)
        }
    }

    /**
     * Zeichnet den Rahmen: Liste, Leermeldung und die beiden Knöpfe. Die Zeilen darunter
     * liefert [TodayWidgetService] nach — eine `ListView` in einem Widget füllt sich nur so.
     */
    private fun renderFrame(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_today)

        views.setRemoteAdapter(
            R.id.widget_list,
            Intent(context, TodayWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                // Die eigene Adresse als Daten: Ohne sie hält Android zwei Widgets für
                // dasselbe und zeigt in beiden dieselbe Liste.
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            },
        )
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        views.setOnClickPendingIntent(R.id.widget_header, openApp(context, quickAdd = false))
        views.setOnClickPendingIntent(R.id.widget_add, openApp(context, quickAdd = true))
        views.setPendingIntentTemplate(R.id.widget_list, toggleTemplate(context))

        manager.updateAppWidget(widgetId, views)
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
    }

    /**
     * Kopfzeile nachreichen: Tagesstand und Pet-Zeichen — dieselbe Zahl wie in der
     * Statuszeile des Fokus-Timers.
     *
     * `partiallyUpdateAppWidget` ersetzt nur die beiden Textfelder und lässt die Liste
     * in Ruhe; ein vollständiges Neuzeichnen würde sie beim Scrollen nach oben reißen.
     */
    private fun updateHeader(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val container = (context.applicationContext as PetodoApplication).container

        container.applicationScope.launchGuarded(
            onFinally = {},
            onError = { Log.w(TAG, "Kopfzeile des Widgets konnte nicht gefüllt werden", it) },
        ) {
            val stand = withContext(Dispatchers.IO) { container.widgetSnapshot() }
            val kopf = RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(
                    R.id.widget_count,
                    context.getString(R.string.widget_progress, stand.done, stand.total),
                )
                setTextViewText(R.id.widget_pet, context.getString(stand.stageEmojiRes))
            }
            ids.forEach { id -> manager.partiallyUpdateAppWidget(id, kopf) }
        }
    }

    private fun openApp(context: Context, quickAdd: Boolean): PendingIntent =
        PendingIntent.getActivity(
            context,
            if (quickAdd) REQUEST_ADD else REQUEST_OPEN,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (quickAdd) putExtra(MainActivity.EXTRA_QUICK_ADD, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Die Vorlage für den Tipp auf eine Zeile.
     *
     * `FLAG_MUTABLE` ist Pflicht: Die Zeile ergänzt die Aufgabenkennung erst beim Tippen,
     * und genau dafür muss der Absichtsentwurf veränderbar sein.
     */
    private fun toggleTemplate(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_TOGGLE,
        Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_TOGGLE),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "TodayWidget"
        private const val REQUEST_OPEN = 9001
        private const val REQUEST_ADD = 9002
        private const val REQUEST_TOGGLE = 9003

        const val ACTION_TOGGLE = "uk.spielerbohne.petodo.action.WIDGET_TOGGLE"
        const val EXTRA_TASK_ID = "uk.spielerbohne.petodo.extra.WIDGET_TASK_ID"

        /**
         * Alle Widgets neu zeichnen.
         *
         * Wird nach jeder Änderung an den Aufgaben gerufen — egal ob aus der App, aus
         * einer Benachrichtigung oder vom Homescreen selbst. Ein Widget, das die Liste
         * von gestern zeigt, ist schlimmer als keins.
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            if (ids.isEmpty()) return

            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            context.sendBroadcast(
                Intent(context, TodayWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}

/** Was in der Kopfzeile des Widgets steht. */
internal data class WidgetSnapshot(val done: Int, val total: Int, val stageEmojiRes: Int)

/** Der Tagesstand für die Kopfzeile. */
internal suspend fun uk.spielerbohne.petodo.di.AppContainer.widgetSnapshot(): WidgetSnapshot {
    val jetzt = Instant.now(clock)
    val board = TodayGrouping.group(taskRepository.allTasks(), jetzt, clock.zone)
    val stufe = petRepository.storedState().stage
    return WidgetSnapshot(
        done = board.doneToday.size,
        total = board.doneToday.size + board.overdue.size + board.today.size,
        stageEmojiRes = stufe.emojiRes,
    )
}
