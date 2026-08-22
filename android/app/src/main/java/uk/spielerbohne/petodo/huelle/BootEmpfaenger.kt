package uk.spielerbohne.petodo.huelle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Nach einem Neustart des Geräts sind alle Wecker weg.
 *
 * Der Zeitplan nicht — er liegt in den Einstellungen der App. Ohne diesen Empfänger wäre
 * die App nach jedem Neustart stumm, bis man sie einmal von Hand öffnet, und genau das
 * merkt man erst, wenn man einen Termin verpasst hat.
 */
class BootEmpfaenger : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val gemeint = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        if (!gemeint) return

        Meldungen.kanaeleAnlegen(context)
        Wecker.ausAblageWiederherstellen(context)
    }
}
