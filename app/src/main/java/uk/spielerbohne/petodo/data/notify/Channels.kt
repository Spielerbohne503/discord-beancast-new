package uk.spielerbohne.petodo.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import uk.spielerbohne.petodo.R

/**
 * Drei getrennte Notification Channels ab Tag eins (Projektplan, Abschnitt 6.6).
 *
 * Ein einziger Kanal heißt: Das Pet quatscht dich zu, du schaltest alles ab — und dann
 * auch die Fälligkeitserinnerungen. Deshalb kann man den Plauderkanal stumm schalten,
 * ohne die Erinnerungen zu verlieren.
 */
object Channels {

    /** Fälligkeiten und Nag. Hohe Priorität. */
    const val REMINDERS = "reminders"

    /** Timer und Statuszeile. Niedrig, still, dauerhaft. */
    const val FOCUS = "focus"

    /** Pet-Meldungen. Niedrig, still. */
    const val PET = "pet"

    /** Gruppenschlüssel der Sammelmeldung für überfällige Aufgaben. */
    const val GROUP_OVERDUE = "uk.spielerbohne.petodo.OVERDUE"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                REMINDERS,
                context.getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_reminders_description)
                enableVibration(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                FOCUS,
                context.getString(R.string.channel_focus_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_focus_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                PET,
                context.getString(R.string.channel_pet_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_pet_description)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }
}
