package uk.spielerbohne.petodo.huelle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Die Meldungen.
 *
 * Zwei Kanäle, weil die Erinnerungskette eskaliert: Die ersten Tage lautlos, ab Tag vier
 * mit Ton. In **einem** Kanal ließe sich das nicht trennen — der Nutzer kann einen Kanal
 * stummschalten, und dann wäre entweder alles laut oder alles leise.
 */
object Meldungen {

    private const val KANAL_LEISE = "erinnerungen-leise"
    private const val KANAL_LAUT = "erinnerungen-laut"

    fun kanaeleAnlegen(context: Context) {
        val verwaltung = context.getSystemService(NotificationManager::class.java) ?: return

        verwaltung.createNotificationChannel(
            NotificationChannel(
                KANAL_LEISE,
                context.getString(R.string.kanal_leise),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.kanal_leise_beschreibung)
                setShowBadge(true)
            },
        )

        verwaltung.createNotificationChannel(
            NotificationChannel(
                KANAL_LAUT,
                context.getString(R.string.kanal_laut),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.kanal_laut_beschreibung)
                setShowBadge(true)
            },
        )
    }

    /**
     * Baut die Meldung zu einem Weckruf.
     *
     * Der Text kommt aus der Webseite — auch die Stufe („steht immer noch offen“, „Willst
     * du das noch?“). Die Hülle hat keine eigenen Formulierungen; sonst gäbe es die
     * sichtbaren Texte an zwei Stellen.
     */
    fun bauen(context: Context, ruf: Weckruf): Notification =
        Notification.Builder(context, if (ruf.ton) KANAL_LAUT else KANAL_LEISE)
            .setSmallIcon(R.drawable.ic_meldung)
            .setContentTitle(ruf.titel)
            .setContentText(ruf.stufe)
            .setAutoCancel(true)
            .setWhen(ruf.at)
            .setContentIntent(oeffnen(context))
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.aktion_erledigt),
                    handlung(context, Aktion.Art.ERLEDIGT, ruf.taskId),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.aktion_morgen),
                    handlung(context, Aktion.Art.MORGEN, ruf.taskId),
                ).build(),
            )
            .build()

    fun zeigen(context: Context, ruf: Weckruf) {
        val verwaltung = context.getSystemService(NotificationManager::class.java) ?: return
        // Ohne die Erlaubnis wirft `notify` nicht, es passiert schlicht nichts. Das ist in
        // Ordnung: Die Einstellungen der App sagen dann, woran es liegt.
        verwaltung.notify(Kodierung.weckerKennung(ruf.taskId), bauen(context, ruf))
    }

    fun zuruecknehmen(context: Context, taskId: String) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(Kodierung.weckerKennung(taskId))
    }

    /** Antippen öffnet die Seite — mehr nicht, so war es gedacht. */
    private fun oeffnen(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * „Erledigt“ und „Morgen“ direkt aus der Meldung.
     *
     * Die Hülle kann die Datenbank nicht anfassen — sie schreibt nur auf, **was** wann
     * angetippt wurde. Verbucht wird beim nächsten Öffnen, mit dem Zeitstempel von damals:
     * Wer am Dienstag abhakt und am Freitag öffnet, hat am Dienstag abgehakt.
     */
    private fun handlung(context: Context, art: Aktion.Art, taskId: String): PendingIntent {
        val intent = Intent(context, AktionsEmpfaenger::class.java).apply {
            action = AktionsEmpfaenger.AKTION
            data = Uri.parse("petodo://aktion/${art.schluessel}/$taskId")
            putExtra(AktionsEmpfaenger.EXTRA_ART, art.schluessel)
            putExtra(Wecker.EXTRA_TASK_ID, taskId)
        }

        return PendingIntent.getBroadcast(
            context,
            Kodierung.weckerKennung(taskId + art.schluessel),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
