package uk.spielerbohne.petodo.huelle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Der Wecker hat geklingelt.
 *
 * Hier wird nicht entschieden, ob gemeldet werden soll — das hat die Webseite längst
 * entschieden, als sie den Termin gestellt hat. Hier wird gemeldet und aufgeschrieben,
 * dass gemeldet wurde.
 */
class AlarmEmpfaenger : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Wecker.AKTION_KLINGELN) return
        val taskId = intent.getStringExtra(Wecker.EXTRA_TASK_ID) ?: return

        val ablage = Ablage(context)
        // Ohne Eintrag im Zeitplan ist der Wecker ein Überbleibsel — etwa nach einer
        // Aufgabe, die zwischenzeitlich abgehakt wurde. Dann bleibt es still.
        val ruf = ablage.weckruf(taskId) ?: return

        Meldungen.kanaeleAnlegen(context)
        Meldungen.zeigen(context, ruf)

        // Der Nachtrag für die Webseite: Ohne ihn bliebe die Erinnerungskette auf Tag 1
        // stehen und würde nie lauter.
        ablage.aktionMerken(Aktion.Art.GEMAHNT, taskId, System.currentTimeMillis())
    }
}
