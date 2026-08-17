package uk.spielerbohne.petodo.domain.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.spielerbohne.petodo.domain.TestTasks.BERLIN
import uk.spielerbohne.petodo.domain.TestTasks.at
import uk.spielerbohne.petodo.domain.TestTasks.task

class TodayGroupingTest {

    private val jetzt = at("2026-08-17", "10:00")

    @Test
    fun aufgabe_mit_vergangener_uhrzeit_ist_ueberfaellig() {
        val board = TodayGrouping.group(
            listOf(task(id = "a", dueAt = at("2026-08-17", "09:00"), hasTime = true)),
            jetzt,
            BERLIN,
        )
        assertEquals(listOf("a"), board.overdue.map { it.id })
    }

    @Test
    fun tagestermin_heute_ist_nicht_schon_am_morgen_ueberfaellig() {
        // Ohne Uhrzeit zählt der Tag, nicht der Zeitpunkt: sonst wäre jede Tagesaufgabe
        // um 00:01 überfällig.
        val board = TodayGrouping.group(
            listOf(task(id = "a", dueAt = at("2026-08-17", "00:00"), hasTime = false)),
            jetzt,
            BERLIN,
        )
        assertTrue(board.overdue.isEmpty())
        assertEquals(listOf("a"), board.today.map { it.id })
    }

    @Test
    fun tagestermin_von_gestern_ist_ueberfaellig() {
        val board = TodayGrouping.group(
            listOf(task(id = "a", dueAt = at("2026-08-16", "00:00"), hasTime = false)),
            jetzt,
            BERLIN,
        )
        assertEquals(listOf("a"), board.overdue.map { it.id })
    }

    @Test
    fun aufgabe_mit_spaeterer_uhrzeit_heute_steht_im_heute_block() {
        val board = TodayGrouping.group(
            listOf(task(id = "a", dueAt = at("2026-08-17", "18:00"), hasTime = true)),
            jetzt,
            BERLIN,
        )
        assertEquals(listOf("a"), board.today.map { it.id })
    }

    @Test
    fun aufgabe_ohne_faelligkeit_landet_unter_spaeter() {
        val board = TodayGrouping.group(listOf(task(id = "a")), jetzt, BERLIN)
        assertEquals(listOf("a"), board.later.map { it.id })
    }

    @Test
    fun geloeschte_aufgabe_taucht_nirgends_auf() {
        val board = TodayGrouping.group(
            listOf(task(id = "a", dueAt = at("2026-08-01"), deletedAt = at("2026-08-05"))),
            jetzt,
            BERLIN,
        )
        assertTrue(board.isEmpty)
    }

    @Test
    fun heute_erledigte_aufgabe_bleibt_sichtbar_aeltere_nicht() {
        val board = TodayGrouping.group(
            listOf(
                task(id = "heute", completedAt = at("2026-08-17", "08:00")),
                task(id = "gestern", completedAt = at("2026-08-16", "08:00")),
            ),
            jetzt,
            BERLIN,
        )
        assertEquals(listOf("heute"), board.doneToday.map { it.id })
        assertEquals(0, board.openCount)
    }

    @Test
    fun erledigte_aufgabe_ist_nie_ueberfaellig() {
        val erledigt = task(
            id = "a",
            dueAt = at("2026-08-01", "09:00"),
            hasTime = true,
            completedAt = at("2026-08-17", "09:30"),
        )
        assertFalse(erledigt.isOverdue(jetzt, BERLIN))
    }

    @Test
    fun ueberfaellige_werden_nach_faelligkeit_sortiert() {
        val board = TodayGrouping.group(
            listOf(
                task(id = "neu", dueAt = at("2026-08-16", "09:00"), hasTime = true),
                task(id = "alt", dueAt = at("2026-08-01", "09:00"), hasTime = true),
            ),
            jetzt,
            BERLIN,
        )
        assertEquals(listOf("alt", "neu"), board.overdue.map { it.id })
    }

    @Test
    fun spaeter_block_zeigt_datierte_vor_undatierten() {
        val board = TodayGrouping.group(
            listOf(
                task(id = "ohne", sortKey = "a"),
                task(id = "mit", dueAt = at("2026-09-01", "09:00"), hasTime = true),
            ),
            jetzt,
            BERLIN,
        )
        assertEquals(listOf("mit", "ohne"), board.later.map { it.id })
    }

    @Test
    fun ueberfaelligkeit_wird_in_kalendertagen_gezaehlt() {
        val t = task(dueAt = at("2026-08-10", "09:00"), hasTime = true)
        assertEquals(7L, t.overdueDays(jetzt, BERLIN))
    }

    @Test
    fun zeitzone_entscheidet_ueber_die_einordnung() {
        // Derselbe Zeitpunkt: 2026-08-17 23:00 in Berlin ist in Tokio schon der 18.
        // Am Folgetag ist die Aufgabe deshalb in Berlin überfällig, in Tokio nicht.
        val jetztSpaeter = at("2026-08-18", "10:00")
        val aufgabe = task(id = "a", dueAt = at("2026-08-17", "23:00"), hasTime = false)

        val berlin = TodayGrouping.group(listOf(aufgabe), jetztSpaeter, BERLIN)
        assertEquals(listOf("a"), berlin.overdue.map { it.id })

        val tokio = TodayGrouping.group(listOf(aufgabe), jetztSpaeter, java.time.ZoneId.of("Asia/Tokyo"))
        assertEquals(listOf("a"), tokio.today.map { it.id })
    }

    @Test
    fun unteraufgaben_erscheinen_nicht_als_eigene_zeile() {
        // Sonst steht dieselbe Arbeit zweimal in der Liste: einmal als Aufgabe, einmal
        // als ihre eigene Unteraufgabe.
        val board = TodayGrouping.group(
            listOf(
                task(id = "eltern", dueAt = at("2026-08-17", "18:00"), hasTime = true),
                task(id = "kind", parentId = "eltern", dueAt = at("2026-08-17", "18:00"), hasTime = true),
            ),
            jetzt,
            BERLIN,
        )
        assertEquals(listOf("eltern"), board.today.map { it.id })
        assertEquals(1, board.openCount)
    }

    @Test
    fun leeres_brett_meldet_sich_als_leer() {
        assertTrue(TodayGrouping.group(emptyList(), jetzt, BERLIN).isEmpty)
        assertEquals(0, TodayGrouping.group(emptyList(), jetzt, BERLIN).openCount)
    }

    @Test
    fun alle_drei_bloecke_gleichzeitig() {
        val board = TodayGrouping.group(
            listOf(
                task(id = "ueberfaellig", dueAt = at("2026-08-01", "09:00"), hasTime = true),
                task(id = "heute", dueAt = at("2026-08-17", "18:00"), hasTime = true),
                task(id = "spaeter", dueAt = at("2026-08-20", "09:00"), hasTime = true),
                task(id = "ohne"),
            ),
            jetzt,
            BERLIN,
        )
        assertEquals(listOf("ueberfaellig"), board.overdue.map { it.id })
        assertEquals(listOf("heute"), board.today.map { it.id })
        assertEquals(listOf("spaeter", "ohne"), board.later.map { it.id })
        assertEquals(4, board.openCount)
    }
}
