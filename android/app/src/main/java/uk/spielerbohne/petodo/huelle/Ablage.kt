package uk.spielerbohne.petodo.huelle

import android.content.Context
import android.content.SharedPreferences

/**
 * Was die Hülle sich merkt.
 *
 * Zwei Dinge, mehr nicht: den zuletzt von der Webseite gestellten Zeitplan und die
 * Handlungen, die an einer Meldung angetippt wurden.
 *
 * **Hier liegen keine Aufgaben.** Die Datenbank steckt im Speicher des WebView, die Regeln
 * stecken in der Webseite. Würde die Hülle mitschreiben, gäbe es zwei Wahrheiten — und die
 * zweite wäre immer die falsche.
 */
class Ablage(context: Context) {

    private val einstellungen: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // -------------------------------------------------------------------- Zeitplan

    /**
     * Der Zeitplan wird immer **ganz** ersetzt.
     *
     * Einzelne Termine nachzupflegen hieße, den Stand an zwei Stellen zu führen. Dann
     * klingelt irgendwann etwas für eine Aufgabe, die längst abgehakt ist.
     */
    fun zeitplanSetzen(rufe: List<Weckruf>) {
        einstellungen.edit()
            .putStringSet(SCHLUESSEL_PLAN, rufe.map(Kodierung::schreiben).toSet())
            .apply()
    }

    fun zeitplan(): List<Weckruf> =
        einstellungen.getStringSet(SCHLUESSEL_PLAN, emptySet())
            .orEmpty()
            .mapNotNull(Kodierung::weckrufLesen)
            .sortedBy { it.at }

    fun weckruf(taskId: String): Weckruf? = zeitplan().firstOrNull { it.taskId == taskId }

    // ------------------------------------------------------------------ Handlungen

    fun aktionMerken(art: Aktion.Art, taskId: String, at: Long) {
        val id = "${at}_${Kodierung.weckerKennung(taskId)}_${art.schluessel}"
        einstellungen.edit()
            .putString(PRAEFIX_AKTION + id, Kodierung.schreiben(Aktion(id, art, taskId, at)))
            .apply()
    }

    fun offeneAktionen(): List<Aktion> =
        einstellungen.all
            .filterKeys { it.startsWith(PRAEFIX_AKTION) }
            .mapNotNull { (schluessel, wert) ->
                (wert as? String)?.let { Kodierung.lesen(schluessel.removePrefix(PRAEFIX_AKTION), it) }
            }
            .sortedBy { it.at }

    /**
     * Erst wenn die Webseite quittiert hat, fliegt eine Handlung raus.
     *
     * Andersherum — löschen beim Ausliefern — ginge eine Handlung verloren, sobald die Seite
     * zwischen Abholen und Verbuchen abstürzt. Zweimal verbuchen ist bei „erledigt“ und
     * „gemahnt“ folgenlos; verlieren wäre es nicht.
     */
    fun aktionenVergessen(ids: Collection<String>) {
        val schreiber = einstellungen.edit()
        for (id in ids) schreiber.remove(PRAEFIX_AKTION + id)
        schreiber.apply()
    }

    companion object {
        private const val NAME = "petodo-huelle"
        private const val SCHLUESSEL_PLAN = "zeitplan"
        private const val PRAEFIX_AKTION = "aktion."
    }
}
