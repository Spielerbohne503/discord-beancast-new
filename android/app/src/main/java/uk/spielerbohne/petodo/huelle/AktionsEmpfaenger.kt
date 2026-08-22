package uk.spielerbohne.petodo.huelle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * „Erledigt“ oder „Morgen“ an der Meldung angetippt.
 *
 * Die Hülle kann die Aufgabe nicht abhaken — die Datenbank liegt im Speicher des WebView
 * und die Regeln liegen in der Webseite. Sie schreibt deshalb nur auf, **was** wann
 * angetippt wurde, und nimmt die Meldung zurück. Verbucht wird beim nächsten Öffnen.
 *
 * Das ist keine Notlösung, sondern die einzige Bauart, bei der es nicht zwei Wahrheiten
 * gibt. Der Zeitstempel fährt mit, damit nichts verrutscht.
 */
class AktionsEmpfaenger : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AKTION) return

        val taskId = intent.getStringExtra(Wecker.EXTRA_TASK_ID) ?: return
        val art = Aktion.Art.von(intent.getStringExtra(EXTRA_ART).orEmpty()) ?: return

        Ablage(context).aktionMerken(art, taskId, System.currentTimeMillis())
        Meldungen.zuruecknehmen(context, taskId)
    }

    companion object {
        const val AKTION = "uk.spielerbohne.petodo.MELDUNGSAKTION"
        const val EXTRA_ART = "art"
    }
}
