package uk.spielerbohne.petodo.huelle

import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONException

/**
 * Was die Webseite von der Hülle aus aufrufen darf.
 *
 * Die Gegenseite steht in `src/ui/bruecke.js`; die Namen hier und dort müssen Zeichen für
 * Zeichen übereinstimmen. `tools/bruecketest.mjs` baut genau dieses Objekt in JavaScript
 * nach und prüft den Vertrag im Browser — die Hälfte, die sich ohne Telefon prüfen lässt.
 *
 * Erreichbar ist die Brücke nur aus den mitgelieferten Dateien: Geladen wird ausschließlich
 * aus dem Paket, `Vermittler.kt` beantwortet jede Anfrage an den eigenen Ursprung selbst.
 * Die `INTERNET`-Berechtigung öffnet nur den Weg für den Abgleich zu einem anderen
 * Ursprung — nicht für ein Nachladen der Seite selbst.
 */
class Bruecke(
    private val ablage: Ablage,
    private val umgebung: Umgebung,
) {

    /** Was die Brücke von der Activity braucht — als Schnittstelle, damit sie prüfbar bleibt. */
    interface Umgebung {
        fun weckerNeuStellen(rufe: List<Weckruf>)
        fun meldungenErlaubt(): Boolean
        fun erlaubnisAnfragen()
        fun genaueWeckerErlaubt(): Boolean
        fun fassung(): String
    }

    /**
     * Der komplette Zeitplan, immer auf einmal.
     *
     * Eine kaputte Zeile wird übergangen statt geworfen: Eine Ausnahme in einer
     * `@JavascriptInterface`-Funktion nimmt sonst die aufrufende Seite mit.
     */
    @JavascriptInterface
    fun zeitplanSetzen(json: String) {
        val rufe = mutableListOf<Weckruf>()

        try {
            val liste = JSONArray(json)
            for (index in 0 until liste.length()) {
                val eintrag = liste.optJSONObject(index) ?: continue
                val taskId = eintrag.optString("id").takeIf { it.isNotEmpty() } ?: continue
                val at = eintrag.optLong("at", 0L).takeIf { it > 0L } ?: continue

                rufe += Weckruf(
                    taskId = taskId,
                    at = at,
                    titel = eintrag.optString("titel"),
                    stufe = eintrag.optString("stufe"),
                    ton = eintrag.optBoolean("ton", false),
                )
            }
        } catch (fehler: JSONException) {
            // Unlesbarer Zeitplan heißt: keine Wecker. Nicht: Absturz.
            return
        }

        umgebung.weckerNeuStellen(rufe)
    }

    @JavascriptInterface
    fun offeneAktionen(): String {
        val liste = JSONArray()
        for (aktion in ablage.offeneAktionen()) {
            liste.put(
                org.json.JSONObject()
                    .put("id", aktion.id)
                    .put("art", aktion.art.schluessel)
                    .put("taskId", aktion.taskId)
                    .put("at", aktion.at),
            )
        }
        return liste.toString()
    }

    @JavascriptInterface
    fun aktionenErledigt(json: String) {
        val ids = mutableListOf<String>()
        try {
            val liste = JSONArray(json)
            for (index in 0 until liste.length()) ids += liste.optString(index)
        } catch (fehler: JSONException) {
            return
        }
        ablage.aktionenVergessen(ids.filter { it.isNotEmpty() })
    }

    @JavascriptInterface
    fun erinnerungenErlaubt(): Boolean = umgebung.meldungenErlaubt()

    @JavascriptInterface
    fun erlaubnisAnfragen() = umgebung.erlaubnisAnfragen()

    @JavascriptInterface
    fun exakteWeckerErlaubt(): Boolean = umgebung.genaueWeckerErlaubt()

    @JavascriptInterface
    fun fassung(): String = umgebung.fassung()

    companion object {
        /** Unter diesem Namen steht die Brücke im JavaScript: `window.Petodo`. */
        const val NAME = "Petodo"
    }
}
