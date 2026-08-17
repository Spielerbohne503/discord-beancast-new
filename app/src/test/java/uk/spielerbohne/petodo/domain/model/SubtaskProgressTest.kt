package uk.spielerbohne.petodo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.spielerbohne.petodo.domain.TestTasks.at
import uk.spielerbohne.petodo.domain.TestTasks.task

class SubtaskProgressTest {

    @Test
    fun ohne_unteraufgaben_gibt_es_keinen_fortschritt() {
        val progress = SubtaskProgress.of(emptyList())
        assertFalse(progress.hasSubtasks)
        assertFalse(progress.allDone)
        assertEquals(SubtaskProgress.NONE, progress)
    }

    @Test
    fun erledigte_und_offene_werden_gezaehlt() {
        val progress = SubtaskProgress.of(
            listOf(
                task(id = "a", completedAt = at("2026-08-17")),
                task(id = "b"),
                task(id = "c", completedAt = at("2026-08-17")),
            )
        )
        assertEquals(SubtaskProgress(done = 2, total = 3), progress)
        assertTrue(progress.hasSubtasks)
        assertFalse(progress.allDone)
    }

    @Test
    fun geloeschte_unteraufgaben_zaehlen_nicht_mit() {
        // Sonst steht dort für immer "1 von 3", ohne dass man je auf 3 kommt.
        val progress = SubtaskProgress.of(
            listOf(
                task(id = "a", completedAt = at("2026-08-17")),
                task(id = "b", deletedAt = at("2026-08-17")),
                task(id = "c", deletedAt = at("2026-08-17")),
            )
        )
        assertEquals(SubtaskProgress(done = 1, total = 1), progress)
        assertTrue(progress.allDone)
    }

    @Test
    fun alles_erledigt_meldet_sich_als_fertig() {
        val progress = SubtaskProgress.of(
            listOf(
                task(id = "a", completedAt = at("2026-08-17")),
                task(id = "b", completedAt = at("2026-08-17")),
            )
        )
        assertTrue(progress.allDone)
    }
}
