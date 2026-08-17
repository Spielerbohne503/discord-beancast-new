package uk.spielerbohne.petodo.domain.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.spielerbohne.petodo.domain.recurrence.RecurrenceRule.Frequency
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceRuleTest {

    @Test
    fun einfache_regel_wird_gelesen() {
        val rule = RecurrenceRule.parse("FREQ=DAILY")
        assertEquals(RecurrenceRule(Frequency.DAILY), rule)
    }

    @Test
    fun rrule_praefix_stoert_nicht() {
        assertEquals(Frequency.WEEKLY, RecurrenceRule.parse("RRULE:FREQ=WEEKLY")?.frequency)
    }

    @Test
    fun alle_unterstuetzten_teile_werden_gelesen() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR;COUNT=10;UNTIL=20261231")

        assertEquals(Frequency.WEEKLY, rule?.frequency)
        assertEquals(2, rule?.interval)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), rule?.byDay)
        assertEquals(10, rule?.count)
        assertEquals(LocalDate.of(2026, 12, 31), rule?.until)
    }

    @Test
    fun unbekannte_bestandteile_werden_uebergangen_statt_abgelehnt() {
        // Eine Regel von einem späteren Sync darf die App nicht lahmlegen.
        val rule = RecurrenceRule.parse("FREQ=MONTHLY;WKST=SU;BYSETPOS=-1;INTERVAL=3")
        assertEquals(Frequency.MONTHLY, rule?.frequency)
        assertEquals(3, rule?.interval)
    }

    @Test
    fun kaputte_regeln_bedeuten_keine_wiederholung() {
        assertNull(RecurrenceRule.parse(null))
        assertNull(RecurrenceRule.parse(""))
        assertNull(RecurrenceRule.parse("   "))
        assertNull(RecurrenceRule.parse("Quatsch"))
        assertNull(RecurrenceRule.parse("FREQ=STUENDLICH"))
        assertNull(RecurrenceRule.parse("INTERVAL=2"))
    }

    @Test
    fun ungueltige_zahlen_fallen_auf_die_vorgabe_zurueck() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;INTERVAL=0;COUNT=abc")
        assertEquals(1, rule?.interval)
        assertNull(rule?.count)
    }

    @Test
    fun until_mit_zeitanteil_wird_verstanden() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;UNTIL=20261231T235959Z")
        assertEquals(LocalDate.of(2026, 12, 31), rule?.until)
    }

    @Test
    fun ordnungszahl_vor_dem_wochentag_wird_auf_den_tag_reduziert() {
        val rule = RecurrenceRule.parse("FREQ=MONTHLY;BYDAY=2MO")
        assertEquals(setOf(DayOfWeek.MONDAY), rule?.byDay)
    }

    @Test
    fun schreiben_und_lesen_ergibt_wieder_dieselbe_regel() {
        val rules = listOf(
            RecurrenceRule(Frequency.DAILY),
            RecurrenceRule(Frequency.DAILY, interval = 3),
            RecurrenceRule(Frequency.WEEKLY, byDay = RecurrenceRule.WEEKDAYS),
            RecurrenceRule(Frequency.MONTHLY, interval = 2, count = 5),
            RecurrenceRule(Frequency.YEARLY, until = LocalDate.of(2030, 1, 1)),
        )
        rules.forEach { rule ->
            assertEquals(rule, RecurrenceRule.parse(rule.toRfc5545()))
        }
    }

    @Test
    fun wochentage_werden_in_normreihenfolge_geschrieben() {
        val rule = RecurrenceRule(Frequency.WEEKLY, byDay = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,FR", rule.toRfc5545())
    }

    @Test
    fun eine_verbrauchte_wiederholung_zaehlt_count_herunter() {
        assertEquals(2, RecurrenceRule(Frequency.DAILY, count = 3).consumeOne()?.count)
        assertNull(RecurrenceRule(Frequency.DAILY, count = 1).consumeOne())
        // Ohne COUNT bleibt die Regel unverändert bestehen.
        val endless = RecurrenceRule(Frequency.DAILY)
        assertEquals(endless, endless.consumeOne())
    }
}
