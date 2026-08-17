package uk.spielerbohne.petodo.domain.model

/**
 * Priorität einer Aufgabe. v1 hat dafür keine UI — das Feld existiert trotzdem von Anfang
 * an (Einbahnstraße), weil es die Überfälligkeitslast gewichtet.
 */
object Priority {
    const val LOW = 0
    const val NORMAL = 1
    const val HIGH = 2
    const val URGENT = 3

    const val DEFAULT = NORMAL

    fun isValid(value: Int): Boolean = value in LOW..URGENT

    /** Auf den gültigen Bereich begrenzen — fremde Daten dürfen nicht abstürzen lassen. */
    fun coerce(value: Int): Int = value.coerceIn(LOW, URGENT)
}
