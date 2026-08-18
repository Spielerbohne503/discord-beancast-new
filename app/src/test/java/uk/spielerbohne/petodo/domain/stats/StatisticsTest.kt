package uk.spielerbohne.petodo.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatisticsTest {

    private val heute = LocalDate.of(2026, 8, 17)

    private fun tage(vararg iso: String) = iso.map(LocalDate::parse).toSet()

    @Test
    fun ohne_erledigtes_gibt_es_keine_serie() {
        assertEquals(0, Statistics.streak(emptySet(), heute))
    }

    @Test
    fun heute_erledigtes_beginnt_eine_serie() {
        assertEquals(1, Statistics.streak(tage("2026-08-17"), heute))
    }

    @Test
    fun der_heutige_tag_zaehlt_nicht_gegen_einen() {
        // Gestern geschafft, heute noch nichts: Die Serie steht. Sonst stünde jeden
        // Morgen eine Null da, und die App bestraft niemanden fürs Aufwachen.
        assertEquals(3, Statistics.streak(tage("2026-08-14", "2026-08-15", "2026-08-16"), heute))
    }

    @Test
    fun ein_ausgelassener_tag_beendet_die_serie() {
        // Vorgestern und davor, aber nicht gestern und nicht heute.
        assertEquals(0, Statistics.streak(tage("2026-08-14", "2026-08-15"), heute))
    }

    @Test
    fun eine_luecke_zaehlt_nur_bis_zur_luecke() {
        assertEquals(2, Statistics.streak(tage("2026-08-17", "2026-08-16", "2026-08-14", "2026-08-13"), heute))
    }

    @Test
    fun die_laengste_serie_wird_ueber_den_ganzen_zeitraum_gesucht() {
        val alle = tage(
            "2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04",
            "2026-03-10", "2026-03-11",
        )
        assertEquals(4, Statistics.longestStreak(alle))
        assertEquals(0, Statistics.longestStreak(emptySet()))
        assertEquals(1, Statistics.longestStreak(tage("2026-05-05")))
    }

    @Test
    fun das_diagramm_enthaelt_auch_die_leeren_tage() {
        val erledigt = listOf(
            LocalDate.parse("2026-08-17"),
            LocalDate.parse("2026-08-17"),
            LocalDate.parse("2026-08-15"),
        )
        val gezeigt = Statistics.perDay(erledigt, heute.minusDays(2), heute)

        assertEquals(3, gezeigt.size)
        assertEquals(listOf(1, 0, 2), gezeigt.map { it.count })
        assertEquals(heute, gezeigt.last().date)
    }

    @Test
    fun ein_umgedrehter_zeitraum_ergibt_nichts_statt_einer_endlosschleife() {
        assertEquals(emptyList<DayCount>(), Statistics.perDay(emptyList(), heute, heute.minusDays(3)))
    }

    @Test
    fun der_gesamtstand_zaehlt_jede_aufgabe_die_serie_nur_die_tage() {
        val erledigt = listOf("2026-08-17", "2026-08-17", "2026-08-16").map(LocalDate::parse)
        val stats = Statistics.of(erledigt, heute, windowDays = 7)

        assertEquals(3, stats.totalCompleted)
        assertEquals(2, stats.streak)
        assertEquals(7, stats.days.size)
        assertEquals(3, stats.periodCompleted)
        assertEquals(heute, stats.busiestDay?.date)
        assertEquals(2, stats.busiestDay?.count)
    }

    @Test
    fun ohne_erledigtes_gibt_es_keinen_besten_tag() {
        val stats = Statistics.of(emptyList(), heute, windowDays = 7)

        assertEquals(null, stats.busiestDay)
        assertEquals(0, stats.streak)
        assertEquals(7, stats.days.size)
    }
}
