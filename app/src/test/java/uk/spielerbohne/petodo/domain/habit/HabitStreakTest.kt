package uk.spielerbohne.petodo.domain.habit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Serien für Gewohnheiten.
 *
 * Die Regel, die alles trägt: Gezählt werden nur Tage, an denen die Gewohnheit
 * **anstand**. Ohne sie wäre jeder Zeitplan außer „täglich“ eine eingebaute Niederlage.
 */
class HabitStreakTest {

    /** Der 17.8.2026 ist ein Montag. */
    private val montag = LocalDate.of(2026, 8, 17)
    private val mittwoch = LocalDate.of(2026, 8, 19)

    private val moMiFr = HabitSchedule.NONE
        .toggle(DayOfWeek.MONDAY)
        .toggle(DayOfWeek.WEDNESDAY)
        .toggle(DayOfWeek.FRIDAY)

    private fun tage(vararg iso: String) = iso.map(LocalDate::parse).toSet()

    // ------------------------------------------------------------------- Zeitplan

    @Test
    fun taeglich_steht_an_jedem_wochentag_an() {
        DayOfWeek.entries.forEach { tag ->
            assertTrue("$tag", HabitSchedule.DAILY.isDueOn(tag))
        }
        assertEquals(7, HabitSchedule.DAILY.timesPerWeek)
        assertTrue(HabitSchedule.DAILY.isDaily)
    }

    @Test
    fun werktags_laesst_das_wochenende_aus() {
        assertTrue(HabitSchedule.WEEKDAYS.isDueOn(DayOfWeek.FRIDAY))
        assertFalse(HabitSchedule.WEEKDAYS.isDueOn(DayOfWeek.SATURDAY))
        assertFalse(HabitSchedule.WEEKDAYS.isDueOn(DayOfWeek.SUNDAY))
        assertEquals(5, HabitSchedule.WEEKDAYS.timesPerWeek)
    }

    @Test
    fun ein_wochentag_laesst_sich_an_und_abschalten() {
        val nurDienstag = HabitSchedule.NONE.toggle(DayOfWeek.TUESDAY)

        assertTrue(nurDienstag.isDueOn(DayOfWeek.TUESDAY))
        assertEquals(1, nurDienstag.timesPerWeek)
        assertTrue(nurDienstag.toggle(DayOfWeek.TUESDAY).isEmpty)
    }

    @Test
    fun eine_beschaedigte_maske_wird_beschnitten() {
        // Bits jenseits der sieben Tage dürfen keine Endlossuche auslösen.
        assertEquals(HabitSchedule.DAILY.mask, HabitSchedule.of(Int.MAX_VALUE).mask)
        assertEquals(HabitSchedule.NONE.mask, HabitSchedule.of(1 shl 20).mask)
    }

    // --------------------------------------------------------------------- Serie

    @Test
    fun ohne_eintraege_gibt_es_keine_serie() {
        assertEquals(0, HabitStreak.current(emptySet(), HabitSchedule.DAILY, montag))
    }

    @Test
    fun ein_leerer_zeitplan_ergibt_keine_serie() {
        assertEquals(0, HabitStreak.current(tage("2026-08-17"), HabitSchedule.NONE, montag))
    }

    @Test
    fun heute_abgehakt_beginnt_die_serie() {
        assertEquals(1, HabitStreak.current(tage("2026-08-17"), HabitSchedule.DAILY, montag))
    }

    @Test
    fun der_heutige_tag_zaehlt_nicht_gegen_einen() {
        // Gestern und vorgestern erledigt, heute noch offen: Die Serie steht bei zwei.
        val eintraege = tage("2026-08-15", "2026-08-16")
        assertEquals(2, HabitStreak.current(eintraege, HabitSchedule.DAILY, montag))
    }

    @Test
    fun ein_ausgelassener_tag_beendet_die_serie() {
        // Der 15. fehlt, also zählt nur der 16.
        val eintraege = tage("2026-08-14", "2026-08-16")
        assertEquals(1, HabitStreak.current(eintraege, HabitSchedule.DAILY, montag))
    }

    @Test
    fun ein_zeitplan_ueberspringt_die_tage_dazwischen() {
        // Montag und Mittwoch erledigt — der Dienstag stand nie an und bricht nichts.
        val eintraege = tage("2026-08-17", "2026-08-19")
        assertEquals(2, HabitStreak.current(eintraege, moMiFr, mittwoch))
    }

    @Test
    fun auch_beim_zeitplan_zaehlt_der_heutige_tag_nicht_gegen_einen() {
        // Heute ist Mittwoch und noch offen; Montag stand und wurde erledigt.
        assertEquals(1, HabitStreak.current(tage("2026-08-17"), moMiFr, mittwoch))
    }

    @Test
    fun ein_verpasster_termin_im_zeitplan_beendet_die_serie() {
        // Freitag (14.) wurde ausgelassen, Montag (17.) und Mittwoch (19.) erledigt.
        val eintraege = tage("2026-08-12", "2026-08-17", "2026-08-19")
        assertEquals(2, HabitStreak.current(eintraege, moMiFr, mittwoch))
    }

    @Test
    fun ein_eintrag_an_einem_freien_tag_verlaengert_nichts() {
        // Dienstag stand nicht an — abgehakt zählt er trotzdem nicht als Termin.
        val eintraege = tage("2026-08-18", "2026-08-19")
        assertEquals(1, HabitStreak.current(eintraege, moMiFr, mittwoch))
    }

    @Test
    fun die_laengste_serie_wird_ueber_alles_gesucht() {
        val eintraege = tage(
            "2026-08-10", "2026-08-12", "2026-08-14", "2026-08-17", // Mo Mi Fr Mo
            "2026-07-06", "2026-07-08", // eine kürzere Serie davor
        )
        assertEquals(4, HabitStreak.longest(eintraege, moMiFr))
        assertEquals(0, HabitStreak.longest(emptySet(), moMiFr))
    }

    @Test
    fun die_laengste_serie_ignoriert_eintraege_an_freien_tagen() {
        val eintraege = tage("2026-08-17", "2026-08-18", "2026-08-19")
        assertEquals(2, HabitStreak.longest(eintraege, moMiFr))
    }

    // ------------------------------------------------------------------- Woche

    @Test
    fun der_wochenfortschritt_zaehlt_nur_die_anstehenden_tage() {
        // Woche vom Montag, den 17., bis Sonntag, den 23. Mo/Mi/Fr stehen an.
        val eintraege = tage("2026-08-17", "2026-08-19")
        assertEquals(2 to 3, HabitStreak.weekProgress(eintraege, moMiFr, mittwoch))
    }

    @Test
    fun die_woche_beginnt_am_montag() {
        // Sonntag, der 16., gehört zur Vorwoche und zählt nicht mit.
        val eintraege = tage("2026-08-16", "2026-08-17")
        assertEquals(1 to 7, HabitStreak.weekProgress(eintraege, HabitSchedule.DAILY, montag))
    }

    @Test
    fun ohne_anstehende_tage_ist_die_woche_leer() {
        assertEquals(0 to 0, HabitStreak.weekProgress(emptySet(), HabitSchedule.NONE, montag))
    }
}
