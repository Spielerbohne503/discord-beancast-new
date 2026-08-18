package uk.spielerbohne.petodo.data.focus

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.data.notify.Channels
import uk.spielerbohne.petodo.domain.focus.FocusPhase
import uk.spielerbohne.petodo.data.pet.emojiRes
import uk.spielerbohne.petodo.data.pet.labelRes
import uk.spielerbohne.petodo.domain.focus.FocusState
import uk.spielerbohne.petodo.domain.pet.HealthStage
import uk.spielerbohne.petodo.domain.focus.FocusTimer
import uk.spielerbohne.petodo.domain.notify.NotificationIds
import uk.spielerbohne.petodo.ui.MainActivity

/**
 * Die persistente Statuszeile (Projektplan, Abschnitt 7).
 *
 * Nicht wischbar, niedrige Priorität, kein Ton — sie ist ohnehin Pflicht für den
 * Foreground Service, kostet also nichts extra.
 *
 * ```
 * Ohne Timer:  Bereit · 3 von 7 heute      [+ Aufgabe] [Fokus]
 * Mit Timer:   Fokus 18:42 · Krafttraining  [Pause] [Stopp]
 * In Pause:    Pause 4:12                   [Überspringen]
 * ```
 */
class FocusStatusNotification(private val context: Context) {

    fun build(
        state: FocusState,
        taskTitle: String?,
        completedRounds: Int,
        openTasks: Int,
        /** Das Pet-Icon wechselt mit dem Zustand (Projektplan, Abschnitt 7). */
        stage: HealthStage = HealthStage.HEALTHY,
    ): Notification {
        val builder = NotificationCompat.Builder(context, Channels.FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        when (state) {
            is FocusState.Ready -> {
                builder.setContentTitle(
                    context.getString(
                        R.string.focus_status_ready,
                        context.getString(stage.emojiRes) + " " + context.getString(stage.labelRes),
                        context.getString(R.string.focus_rounds_today, completedRounds, openTasks),
                    )
                )
                builder.addAction(
                    NotificationCompat.Action.Builder(
                        0,
                        context.getString(R.string.focus_add_task),
                        quickAdd(),
                    ).build()
                )
                builder.addAction(action(FocusAction.START_FOCUS, R.string.focus_phase_focus))
            }

            is FocusState.Running -> {
                builder.setContentTitle(
                    context.getString(
                        R.string.focus_notification_running,
                        phaseLabel(state.session.phase),
                        FocusTimer.format(state.remaining),
                    )
                )
                taskTitle?.let(builder::setContentText)

                if (state.session.phase.isBreak) {
                    builder.addAction(action(FocusAction.SKIP, R.string.focus_skip))
                } else {
                    builder.addAction(action(FocusAction.PAUSE, R.string.focus_pause))
                    builder.addAction(action(FocusAction.STOP, R.string.focus_stop))
                }
            }

            is FocusState.Paused -> {
                builder.setContentTitle(
                    context.getString(
                        R.string.focus_notification_running,
                        phaseLabel(state.session.phase),
                        FocusTimer.format(state.remaining),
                    )
                )
                taskTitle?.let(builder::setContentText)
                builder.addAction(action(FocusAction.RESUME, R.string.focus_resume))
                builder.addAction(action(FocusAction.STOP, R.string.focus_stop))
            }

            is FocusState.Elapsed -> {
                builder.setContentTitle(phaseLabel(state.session.phase))
                builder.addAction(action(FocusAction.STOP, R.string.focus_stop))
            }
        }

        return builder.build()
    }

    private fun phaseLabel(phase: FocusPhase): String = context.getString(
        when (phase) {
            FocusPhase.FOCUS -> R.string.focus_phase_focus
            FocusPhase.SHORT_BREAK -> R.string.focus_phase_short_break
            FocusPhase.LONG_BREAK -> R.string.focus_phase_long_break
        }
    )

    private fun action(action: FocusAction, labelRes: Int) =
        NotificationCompat.Action.Builder(
            0,
            context.getString(labelRes),
            FocusService.pendingIntent(context, action),
        ).build()

    /**
     * „+ Aufgabe“ öffnet die App mit dem Eingabefeld im Zugriff.
     *
     * Bewusst kein Umweg über den Dienst: Der könnte kein Fenster öffnen, und ein Knopf,
     * der nichts tut, ist schlimmer als kein Knopf.
     */
    private fun quickAdd(): PendingIntent = PendingIntent.getActivity(
        context,
        NotificationIds.requestCodeForFocus(FocusAction.ADD_TASK.name),
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_QUICK_ADD, true)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        NotificationIds.FOCUS_STATUS,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Die Knöpfe der Statuszeile. */
enum class FocusAction {
    START_FOCUS,
    PAUSE,
    RESUME,
    STOP,
    SKIP,
    ADD_TASK,

    /** Nur die Anzeige wieder aufnehmen — nach einem Neustart. */
    RESUME_DISPLAY,
}
