package uk.spielerbohne.petodo.domain.sort

/**
 * Fractional Index (Lexorank-artig) für die Sortierung.
 *
 * Einbahnstraße aus dem Projektplan: Sortierung als Schlüssel zwischen zwei Nachbarn,
 * nicht als Integer-Position. Beim Umsortieren wird genau eine Zeile geschrieben, und
 * beim späteren Sync gibt es keinen unauflösbaren Positionskonflikt.
 *
 * Schlüssel werden lexikografisch verglichen (`String.compareTo`). Das Alphabet ist so
 * gewählt, dass die Zeichenreihenfolge der ASCII-Reihenfolge entspricht.
 *
 * Invarianten:
 *  - `between(a, b)` liegt echt zwischen `a` und `b`
 *  - erzeugte Schlüssel enden nie auf dem kleinsten Zeichen ('0'), damit vor jedem
 *    Schlüssel noch beliebig oft eingefügt werden kann
 */
object FractionalIndex {

    /** Base-36, aufsteigend sortiert und ASCII-konform. */
    const val ALPHABET: String = "0123456789abcdefghijklmnopqrstuvwxyz"

    private val MIN_CHAR = ALPHABET.first()

    /** Schlüssel für das erste Element einer leeren Liste. */
    fun initial(): String = between(null, null)

    /**
     * Erzeugt einen Schlüssel echt zwischen [prev] und [next].
     *
     * `null` bedeutet "kein Nachbar": [prev] = null heißt Anfang der Liste,
     * [next] = null heißt Ende der Liste.
     */
    fun between(prev: String?, next: String?): String {
        require(prev == null || isValid(prev)) { "Ungültiger Schlüssel: $prev" }
        require(next == null || isValid(next)) { "Ungültiger Schlüssel: $next" }
        require(prev == null || next == null || prev < next) {
            "prev muss kleiner als next sein, war: $prev >= $next"
        }

        val lower = prev.orEmpty()
        var upper = next
        val result = StringBuilder()
        var i = 0

        while (true) {
            val a = lower.getOrElse(i) { MIN_CHAR }
            val b = upper?.getOrNull(i)

            if (b != null && a == b) {
                // Gemeinsames Präfix: übernehmen und eine Stelle tiefer weitersuchen.
                result.append(a)
                i++
                continue
            }

            val aIndex = ALPHABET.indexOf(a)
            // Fehlt die Obergrenze, ist das Ende des Alphabets die (exklusive) Schranke.
            val bIndex = b?.let { ALPHABET.indexOf(it) } ?: ALPHABET.length

            if (bIndex - aIndex > 1) {
                result.append(ALPHABET[(aIndex + bIndex) / 2])
                return result.toString()
            }

            // Die beiden Zeichen sind benachbart: das untere übernehmen. Alles, was danach
            // kommt, ist automatisch kleiner als die Obergrenze — die entfällt damit.
            result.append(a)
            i++
            upper = null
        }
    }

    /** Schlüssel hinter dem letzten Element. */
    fun after(last: String?): String = between(last, null)

    /**
     * Schlüssel hinter dem letzten Element, auch wenn der unbrauchbar ist.
     *
     * [between] besteht zu Recht auf gültigen Schlüsseln — es ist eine reine Funktion mit
     * einer Vorbedingung. An der Grenze zur Datenbank gilt aber etwas anderes: Dort kann
     * ein beschädigter Wert liegen, etwa aus einer von Hand bearbeiteten Sicherung. Dann
     * kostet das die Reihenfolge, aber nicht die Fähigkeit, überhaupt etwas anzulegen.
     */
    fun afterOrInitial(last: String?): String =
        if (last == null || !isValid(last)) initial() else after(last)

    /** Schlüssel vor dem ersten Element. */
    fun before(first: String?): String = between(null, first)

    fun isValid(key: String): Boolean =
        key.isNotEmpty() && key.all { it in ALPHABET }
}
