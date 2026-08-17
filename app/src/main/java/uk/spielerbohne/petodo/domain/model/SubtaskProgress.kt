package uk.spielerbohne.petodo.domain.model

/**
 * Fortschritt einer Aufgabe mit Unteraufgaben ("2/5").
 *
 * Gelöschte Unteraufgaben zählen nicht mit — sonst steht dort für immer eine Zahl, die
 * niemand mehr erreichen kann.
 */
data class SubtaskProgress(val done: Int, val total: Int) {

    val hasSubtasks: Boolean get() = total > 0
    val allDone: Boolean get() = total > 0 && done == total

    companion object {
        val NONE = SubtaskProgress(done = 0, total = 0)

        fun of(subtasks: List<Task>): SubtaskProgress {
            val relevant = subtasks.filterNot { it.isDeleted }
            return SubtaskProgress(
                done = relevant.count { it.isCompleted },
                total = relevant.size,
            )
        }
    }
}
