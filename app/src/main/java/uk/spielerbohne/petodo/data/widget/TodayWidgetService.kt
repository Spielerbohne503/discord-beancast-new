package uk.spielerbohne.petodo.data.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import uk.spielerbohne.petodo.PetodoApplication
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.domain.text.MarkdownLinks
import uk.spielerbohne.petodo.domain.today.TodayBoard
import uk.spielerbohne.petodo.domain.today.TodayGrouping
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Füllt die Liste im Widget.
 *
 * Android ruft [RemoteViewsFactory.onDataSetChanged] auf einem Hintergrundfaden — dort
 * darf die Datenbank blockierend gelesen werden, und genau dafür ist diese Klasse da.
 */
class TodayWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TodayWidgetFactory(this)
}

private class TodayWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    /** Was gerade angezeigt wird. Zwischen zwei Aktualisierungen ändert sich nichts. */
    private var eintraege: List<WidgetRow> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val container = (context.applicationContext as PetodoApplication).container
        val zone = container.clock.zone
        val jetzt = Instant.now(container.clock)

        val board = runBlocking {
            TodayGrouping.group(container.taskRepository.allTasks(), jetzt, zone)
        }
        eintraege = rows(board, jetzt, zone)
    }

    /**
     * Überfällig zuerst, dann heute, dann das heute Erledigte.
     *
     * Der Rückblick auf frühere Tage bleibt draußen: Auf dem Homescreen zählt, was noch
     * offen ist — und ein Widget, das man scrollen muss, um das Offene zu sehen, ist
     * ein Widget, das man nicht benutzt.
     */
    private fun rows(board: TodayBoard, now: Instant, zone: ZoneId): List<WidgetRow> = buildList {
        board.overdue.forEach { add(WidgetRow(it, ueberfaellig = true, zone = zone, now = now)) }
        board.today.forEach { add(WidgetRow(it, ueberfaellig = false, zone = zone, now = now)) }
        board.doneToday.forEach { add(WidgetRow(it, ueberfaellig = false, zone = zone, now = now)) }
    }

    override fun getCount(): Int = eintraege.size

    override fun getViewAt(position: Int): RemoteViews {
        val zeile = eintraege.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_task_row)
        val views = RemoteViews(context.packageName, R.layout.widget_task_row)

        views.setTextViewText(R.id.row_title, zeile.titel)
        views.setImageViewResource(
            R.id.row_check,
            if (zeile.erledigt) R.drawable.ic_widget_check_on else R.drawable.ic_widget_check_off,
        )

        if (zeile.termin == null) {
            views.setViewVisibility(R.id.row_due, android.view.View.GONE)
        } else {
            views.setViewVisibility(R.id.row_due, android.view.View.VISIBLE)
            views.setTextViewText(R.id.row_due, zeile.termin)
            views.setTextColor(
                R.id.row_due,
                context.getColor(if (zeile.ueberfaellig) R.color.widget_overdue else R.color.widget_muted),
            )
        }

        views.setTextColor(
            R.id.row_title,
            context.getColor(if (zeile.erledigt) R.color.widget_muted else R.color.widget_text),
        )

        // Die Kennung wird erst hier eingesetzt — die Vorlage dafür liegt beim Provider.
        views.setOnClickFillInIntent(
            R.id.row_root,
            Intent().putExtra(TodayWidgetProvider.EXTRA_TASK_ID, zeile.id),
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = eintraege.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        eintraege = emptyList()
    }
}

/** Eine fertig aufbereitete Zeile — das Zeichnen soll nichts mehr rechnen müssen. */
private class WidgetRow(task: Task, val ueberfaellig: Boolean, zone: ZoneId, now: Instant) {

    val id: String = task.id
    val erledigt: Boolean = task.isCompleted

    /** In der Liste steht die Kurzform eines Verweises, nicht die ganze Adresse. */
    val titel: String = MarkdownLinks.plainText(task.title)

    val termin: String? = task.dueAt?.let { faellig ->
        val datum = faellig.atZone(zone)
        val heute = now.atZone(zone).toLocalDate()
        when {
            datum.toLocalDate() != heute -> datum.toLocalDate()
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
            task.hasTime -> datum.toLocalTime()
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            else -> null
        }
    }
}
