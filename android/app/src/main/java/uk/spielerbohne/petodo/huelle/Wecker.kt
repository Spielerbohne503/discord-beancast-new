package uk.spielerbohne.petodo.huelle

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Der Wecker.
 *
 * Das ist der ganze Grund, warum es diese App gibt: Eine Webseite kann sich nicht selbst
 * wecken. Ohne Server und ohne Konto kann niemand einen geschlossenen Tab aufwecken — ein
 * Wecker im Betriebssystem kann es.
 *
 * Der Wecker entscheidet nichts. Er bekommt eine Liste von Zeitpunkten und klingelt.
 */
object Wecker {

    /**
     * Stellt alle Wecker neu.
     *
     * Erst alles löschen, dann alles setzen. Ein Abgleich „was hat sich geändert“ hieße,
     * den Stand an zwei Stellen zu führen — und dann klingelt eines Tages etwas für eine
     * Aufgabe, die es nicht mehr gibt.
     */
    fun neuStellen(context: Context, rufe: List<Weckruf>) {
        val ablage = Ablage(context)
        alleLoeschen(context, ablage.zeitplan())

        ablage.zeitplanSetzen(rufe)
        for (ruf in rufe) stellen(context, ruf)
    }

    /** Nach einem Neustart des Geräts sind alle Wecker weg — der gespeicherte Plan nicht. */
    fun ausAblageWiederherstellen(context: Context) {
        for (ruf in Ablage(context).zeitplan()) stellen(context, ruf)
    }

    private fun stellen(context: Context, ruf: Weckruf) {
        val alarme = context.getSystemService(AlarmManager::class.java) ?: return

        // Ein Termin in der Vergangenheit klingelt sofort. Das ist richtig so: Er kommt
        // vor, wenn das Gerät aus war, und eine verpasste Erinnerung soll nicht ausfallen.
        val wann = maxOf(ruf.at, System.currentTimeMillis() + 1_000)

        if (genauErlaubt(context)) {
            alarme.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wann, absicht(context, ruf))
        } else {
            // Ohne die Erlaubnis für genaue Wecker verschiebt Android um einige Minuten.
            // Ein paar Minuten zu spät ist besser als gar nicht — deshalb kein Abbruch.
            alarme.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wann, absicht(context, ruf))
        }
    }

    private fun alleLoeschen(context: Context, rufe: List<Weckruf>) {
        val alarme = context.getSystemService(AlarmManager::class.java) ?: return
        for (ruf in rufe) alarme.cancel(absicht(context, ruf))
    }

    /**
     * Darf minutengenau geweckt werden?
     *
     * Seit Android 12 ist das eine eigene Erlaubnis. `USE_EXACT_ALARM` im Manifest deckt
     * den Fall einer Erinnerungs-App ab; wo das nicht greift, fragt
     * `canScheduleExactAlarms` nach.
     */
    fun genauErlaubt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarme = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarme.canScheduleExactAlarms()
    }

    private fun absicht(context: Context, ruf: Weckruf): PendingIntent {
        val intent = Intent(context, AlarmEmpfaenger::class.java).apply {
            action = AKTION_KLINGELN
            // Ohne eigene Daten hielte Android zwei Wecker für denselben und überschriebe
            // den ersten — `filterEquals` sieht `extras` nicht an.
            data = android.net.Uri.parse("petodo://wecker/${ruf.taskId}")
            putExtra(EXTRA_TASK_ID, ruf.taskId)
        }

        return PendingIntent.getBroadcast(
            context,
            Kodierung.weckerKennung(ruf.taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val AKTION_KLINGELN = "uk.spielerbohne.petodo.KLINGELN"
    const val EXTRA_TASK_ID = "taskId"
}
