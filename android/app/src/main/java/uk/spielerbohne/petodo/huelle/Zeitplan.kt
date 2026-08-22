package uk.spielerbohne.petodo.huelle

/**
 * Ein Weckruf.
 *
 * Mehr weiß die Hülle über eine Aufgabe nicht — und mehr braucht sie auch nicht. Was
 * fällig ist, wann gemahnt wird und wie laut, rechnet die Webseite aus.
 */
data class Weckruf(
    val taskId: String,
    val at: Long,
    val titel: String,
    val stufe: String,
    val ton: Boolean,
)

/** Was an einer Meldung angetippt wurde, während die Seite zu war. */
data class Aktion(
    val id: String,
    val art: Art,
    val taskId: String,
    val at: Long,
) {
    enum class Art(val schluessel: String) {
        /** Am Haken in der Meldung. */
        ERLEDIGT("erledigt"),

        /** Einen Kalendertag weiter. */
        MORGEN("morgen"),

        /**
         * Es wurde gemeldet, ohne dass jemand zusah.
         *
         * Ohne diesen Nachtrag bliebe die Erinnerungskette auf Tag 1 stehen und würde nie
         * lauter — die Eskalation lebt davon, dass die Webseite mitzählt.
         */
        GEMAHNT("gemahnt");

        companion object {
            fun von(schluessel: String): Art? = entries.firstOrNull { it.schluessel == schluessel }
        }
    }
}

/**
 * Wie Weckrufe und Handlungen in den Einstellungen stehen.
 *
 * Kein JSON, sondern Felder mit einem Trennzeichen — und zwar mit einem, das über keine
 * Tastatur einzugeben ist (`U+001F`, „unit separator“). Der einzige Wert, in dem beliebiger
 * Text stehen kann, ist der Titel; er steht **hinten** und wird mit `limit` in einem Stück
 * zurückgelesen. Damit braucht es keine Maskierung, und das Ganze lässt sich ohne Android
 * prüfen — anders als `org.json`, das im Unit-Test nur Attrappen liefert.
 */
object Kodierung {

    /** Steuerzeichen. Kommt in keinem getippten Titel vor. */
    const val TRENNER = "\u001F"

    fun schreiben(aktion: Aktion): String =
        listOf(aktion.art.schluessel, aktion.taskId, aktion.at.toString()).joinToString(TRENNER)

    /** Gibt `null` zurück, wenn die Zeile unbrauchbar ist — eine kaputte Zeile fliegt raus. */
    fun lesen(id: String, zeile: String): Aktion? {
        val teile = zeile.split(TRENNER)
        if (teile.size != 3) return null

        val art = Aktion.Art.von(teile[0]) ?: return null
        val taskId = teile[1].takeIf { it.isNotEmpty() } ?: return null
        val at = teile[2].toLongOrNull() ?: return null

        return Aktion(id = id, art = art, taskId = taskId, at = at)
    }

    fun schreiben(ruf: Weckruf): String = listOf(
        ruf.taskId,
        ruf.at.toString(),
        if (ruf.ton) "1" else "0",
        ruf.stufe,
        ruf.titel,
    ).joinToString(TRENNER)

    fun weckrufLesen(zeile: String): Weckruf? {
        // `limit = 5`: Steht doch einmal ein Trennzeichen im Titel, bleibt es dort, statt
        // die Zeile unbrauchbar zu machen.
        val teile = zeile.split(TRENNER, limit = 5)
        if (teile.size != 5) return null

        return Weckruf(
            taskId = teile[0].takeIf { it.isNotEmpty() } ?: return null,
            at = teile[1].toLongOrNull() ?: return null,
            ton = teile[2] == "1",
            stufe = teile[3],
            titel = teile[4],
        )
    }

    /**
     * Die Kennung eines Wecktermins.
     *
     * Sie muss für dieselbe Aufgabe **stabil** sein — sonst legt ein zweiter Zeitplan einen
     * zweiten Wecker an, statt den ersten zu ersetzen, und es klingelt doppelt. Und sie darf
     * nicht negativ sein: `PendingIntent` nimmt sie als Anfragenummer.
     */
    fun weckerKennung(taskId: String): Int = taskId.hashCode() and 0x7fff_ffff
}
