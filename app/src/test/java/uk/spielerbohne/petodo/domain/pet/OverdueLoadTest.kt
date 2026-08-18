package uk.spielerbohne.petodo.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.spielerbohne.petodo.domain.TestTasks.BERLIN
import uk.spielerbohne.petodo.domain.TestTasks.at
import uk.spielerbohne.petodo.domain.TestTasks.task
import uk.spielerbohne.petodo.domain.model.Priority

class OverdueLoadTest {

    private val jetzt = at("2026-08-17", "10:00")

    private fun load(tasks: List<uk.spielerbohne.petodo.domain.model.Task>, excluded: Set<String> = emptySet()) =
        OverdueLoad.of(tasks, excluded, jetzt, BERLIN)

    @Test
    fun eine_ueberfaellige_aufgabe_mit_normaler_prioritaet_zaehlt_eins() {
        val tasks = listOf(task(dueAt = at("2026-08-16", "09:00"), hasTime = true))
        // Ein Tag überfällig = erste angefangene Woche: 1,0 + 0,2
        assertEquals(1.2, load(tasks), 0.001)
    }

    @Test
    fun die_prioritaetsfaktoren_stimmen() {
        val faktoren = mapOf(
            Priority.LOW to 0.5,
            Priority.NORMAL to 1.0,
            Priority.HIGH to 1.5,
            Priority.URGENT to 2.0,
        )
        faktoren.forEach { (prioritaet, faktor) ->
            val aufgabe = task(dueAt = at("2026-08-17", "09:00"), hasTime = true, priority = prioritaet)
            // Am selben Tag überfällig: keine angefangene Woche.
            assertEquals("Priorität $prioritaet", faktor, load(listOf(aufgabe)), 0.001)
        }
    }

    @Test
    fun je_angefangener_ueberfaelligkeitswoche_kommen_null_komma_zwei_dazu() {
        fun beitrag(faelligAm: String) =
            load(listOf(task(dueAt = at(faelligAm, "09:00"), hasTime = true)))

        // "Angefangene Woche" wie bei Parkgebühren: Sieben Tage sind EINE angefangene
        // Woche, erst der achte Tag fängt die zweite an.
        assertEquals(1.0, beitrag("2026-08-17"), 0.001) // heute, nicht überfällig
        assertEquals(1.2, beitrag("2026-08-16"), 0.001) //  1 Tag  → 1 Woche
        assertEquals(1.2, beitrag("2026-08-11"), 0.001) //  6 Tage → 1 Woche
        assertEquals(1.2, beitrag("2026-08-10"), 0.001) //  7 Tage → 1 Woche
        assertEquals(1.4, beitrag("2026-08-09"), 0.001) //  8 Tage → 2 Wochen
        assertEquals(1.4, beitrag("2026-08-03"), 0.001) // 14 Tage → 2 Wochen
        assertEquals(1.6, beitrag("2026-08-02"), 0.001) // 15 Tage → 3 Wochen
    }

    @Test
    fun die_last_ist_bei_zehn_gedeckelt() {
        val viele = (1..30).map {
            task(id = "t$it", dueAt = at("2026-01-01", "09:00"), hasTime = true, priority = Priority.URGENT)
        }
        assertEquals(10.0, load(viele), 0.001)
    }

    @Test
    fun aufgaben_ohne_faelligkeit_zaehlen_nie() {
        // Krankheit hängt an überfälligen, nie an offenen Aufgaben.
        assertEquals(0.0, load(listOf(task(), task(id = "b"), task(id = "c"))), 0.001)
    }

    @Test
    fun aufgaben_in_der_irgendwann_liste_zaehlen_nicht() {
        val tasks = listOf(
            task(id = "inbox", dueAt = at("2026-08-01", "09:00"), hasTime = true),
            task(id = "spaeter", listId = "irgendwann", dueAt = at("2026-08-01", "09:00"), hasTime = true),
        )
        val mitVentil = load(tasks, excluded = setOf("irgendwann"))
        val ohneVentil = load(tasks)

        assertTrue(mitVentil < ohneVentil)
        assertEquals(load(listOf(tasks.first())), mitVentil, 0.001)
    }

    @Test
    fun erledigte_und_geloeschte_aufgaben_zaehlen_nicht() {
        val tasks = listOf(
            task(id = "erledigt", dueAt = at("2026-08-01", "09:00"), hasTime = true, completedAt = jetzt),
            task(id = "geloescht", dueAt = at("2026-08-01", "09:00"), hasTime = true, deletedAt = jetzt),
        )
        assertEquals(0.0, load(tasks), 0.001)
    }

    // ------------------------------------------------------------------- Multiplikatoren

    @Test
    fun der_basismultiplikator_geht_von_eins_bis_drei() {
        assertEquals(1.0, OverdueLoad.baseMultiplier(0.0), 0.001)
        assertEquals(2.0, OverdueLoad.baseMultiplier(5.0), 0.001)
        assertEquals(3.0, OverdueLoad.baseMultiplier(10.0), 0.001)
        // Über der Deckelung bleibt es bei drei.
        assertEquals(3.0, OverdueLoad.baseMultiplier(99.0), 0.001)
    }

    @Test
    fun der_launenmultiplikator_haengt_an_den_beiden_anderen_werten() {
        // Voll versorgt: kein Aufschlag.
        assertEquals(1.0, OverdueLoad.moodMultiplier(0.0, energy = 100.0, satiety = 100.0), 0.001)
        // Vollständig ausgehungert: ×4 bei Last null.
        assertEquals(4.0, OverdueLoad.moodMultiplier(0.0, energy = 0.0, satiety = 0.0), 0.001)
        // Und mit voller Last zusätzlich ×3.
        assertEquals(12.0, OverdueLoad.moodMultiplier(10.0, energy = 0.0, satiety = 0.0), 0.001)
    }
}
