package uk.spielerbohne.petodo.domain.sort

/**
 * Umsortieren von Hand.
 *
 * Beim Verschieben wird **genau eine** Zeile geschrieben: Der bewegte Eintrag bekommt
 * einen neuen Fractional Index zwischen seinen künftigen Nachbarn. Genau dafür wurde der
 * Schlüssel vor dem ersten Commit so gewählt — mit Integer-Positionen müsste hier die
 * halbe Tabelle neu geschrieben werden, und beim späteren Sync wäre der Konflikt
 * unauflösbar.
 */
object Reorder {

    /**
     * Der neue Schlüssel für das Element, das von [from] nach [to] wandert.
     *
     * [keys] ist die aktuelle Reihenfolge der Sortierschlüssel. Gibt `null` zurück, wenn
     * sich nichts ändert oder die Indizes nicht passen — dann wird auch nichts
     * geschrieben.
     */
    fun keyForMove(keys: List<String>, from: Int, to: Int): String? {
        if (from == to) return null
        if (from !in keys.indices) return null
        if (to !in keys.indices) return null

        // Die Nachbarn ergeben sich aus der Liste *ohne* das bewegte Element.
        val remaining = keys.toMutableList().apply { removeAt(from) }
        val previous = remaining.getOrNull(to - 1)
        val next = remaining.getOrNull(to)

        // Ein beschädigter Schlüssel — etwa aus einer von Hand bearbeiteten Sicherung —
        // kostet diesen einen Zug, nicht die App: [FractionalIndex.between] besteht zu
        // Recht auf gültigen Nachbarn, also wird hier vorher nachgesehen.
        if (previous != null && !FractionalIndex.isValid(previous)) return null
        if (next != null && !FractionalIndex.isValid(next)) return null
        if (previous != null && next != null && previous >= next) return null

        return FractionalIndex.between(previous, next)
    }

    /**
     * Wendet eine Verschiebung auf eine Liste an — für die Vorschau während des Ziehens,
     * bevor irgendetwas gespeichert wird.
     */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from == to) return items
        if (from !in items.indices || to !in items.indices) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }
}
