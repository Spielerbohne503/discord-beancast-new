package uk.spielerbohne.petodo.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.spielerbohne.petodo.domain.TestTasks.BERLIN
import uk.spielerbohne.petodo.domain.TestTasks.at
import uk.spielerbohne.petodo.domain.TestTasks.task
import uk.spielerbohne.petodo.domain.model.Priority

class TaskFilterTest {

    private val jetzt = at("2026-08-17", "10:00")

    private fun ids(scope: TaskScope, query: String = "", tasks: List<uk.spielerbohne.petodo.domain.model.Task>) =
        TaskFilter.apply(tasks, scope, query, jetzt, BERLIN).map { it.id }

    @Test
    fun alle_offenen_zeigen_keine_erledigten_und_keine_geloeschten() {
        val tasks = listOf(
            task(id = "offen"),
            task(id = "erledigt", completedAt = at("2026-08-17", "09:00")),
            task(id = "geloescht", deletedAt = at("2026-08-16")),
        )
        assertEquals(listOf("offen"), ids(TaskScope.AllOpen, tasks = tasks))
    }

    @Test
    fun eine_liste_zeigt_nur_ihre_eigenen_aufgaben() {
        val tasks = listOf(
            task(id = "inbox", listId = "inbox"),
            task(id = "arbeit", listId = "arbeit"),
        )
        assertEquals(listOf("arbeit"), ids(TaskScope.InList("arbeit"), tasks = tasks))
    }

    @Test
    fun sieben_tage_umfasst_ueberfaellige_und_die_kommende_woche() {
        val tasks = listOf(
            task(id = "ueberfaellig", dueAt = at("2026-08-01", "09:00"), hasTime = true),
            task(id = "heute", dueAt = at("2026-08-17", "18:00"), hasTime = true),
            task(id = "in6tagen", dueAt = at("2026-08-23", "09:00"), hasTime = true),
            task(id = "in7tagen", dueAt = at("2026-08-24", "09:00"), hasTime = true),
            task(id = "in8tagen", dueAt = at("2026-08-25", "09:00"), hasTime = true),
            task(id = "ohnedatum"),
        )
        assertEquals(
            listOf("ueberfaellig", "heute", "in6tagen", "in7tagen"),
            ids(TaskScope.NextSevenDays, tasks = tasks),
        )
    }

    @Test
    fun erledigte_stehen_mit_dem_juengsten_zuerst() {
        val tasks = listOf(
            task(id = "alt", completedAt = at("2026-08-15", "09:00")),
            task(id = "neu", completedAt = at("2026-08-17", "09:00")),
            task(id = "offen"),
        )
        assertEquals(listOf("neu", "alt"), ids(TaskScope.Completed, tasks = tasks))
    }

    @Test
    fun suche_ignoriert_gross_und_kleinschreibung() {
        val tasks = listOf(
            task(id = "a", title = "Ausbildungsnachweise nachholen"),
            task(id = "b", title = "Einkaufen"),
        )
        assertEquals(listOf("a"), ids(TaskScope.AllOpen, query = "AUSBILDUNG", tasks = tasks))
        assertEquals(listOf("a"), ids(TaskScope.AllOpen, query = "nachweise", tasks = tasks))
    }

    @Test
    fun suche_findet_auch_in_der_notiz() {
        val tasks = listOf(task(id = "a", title = "Anruf").copy(note = "Wegen der Rechnung"))
        assertEquals(listOf("a"), ids(TaskScope.AllOpen, query = "rechnung", tasks = tasks))
    }

    @Test
    fun suche_findet_unteraufgaben_die_liste_zeigt_sie_nicht() {
        val tasks = listOf(
            task(id = "eltern", title = "Umzug"),
            task(id = "kind", parentId = "eltern", title = "Kartons kaufen"),
        )
        assertEquals(listOf("eltern"), ids(TaskScope.AllOpen, tasks = tasks))
        assertEquals(listOf("kind"), ids(TaskScope.AllOpen, query = "Kartons", tasks = tasks))
    }

    @Test
    fun leere_suche_filtert_nicht() {
        val tasks = listOf(task(id = "a"), task(id = "b", sortKey = "j"))
        assertEquals(2, ids(TaskScope.AllOpen, query = "   ", tasks = tasks).size)
    }

    @Test
    fun offene_werden_nach_faelligkeit_dann_prioritaet_sortiert() {
        val tasks = listOf(
            task(id = "ohne_normal", sortKey = "a"),
            task(id = "ohne_dringend", priority = Priority.URGENT, sortKey = "b"),
            task(id = "spaet", dueAt = at("2026-09-01", "09:00"), hasTime = true),
            task(id = "frueh", dueAt = at("2026-08-18", "09:00"), hasTime = true),
        )
        assertEquals(
            listOf("frueh", "spaet", "ohne_dringend", "ohne_normal"),
            ids(TaskScope.AllOpen, tasks = tasks),
        )
    }

    @Test
    fun eine_suche_ohne_treffer_ergibt_eine_leere_liste() {
        val treffer = ids(TaskScope.AllOpen, query = "zzz", tasks = listOf(task(id = "a", title = "Einkaufen")))
        assertTrue(treffer.isEmpty())
    }
}
