package uk.spielerbohne.petodo.domain.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.spielerbohne.petodo.domain.recurrence.RecurrenceRule.Frequency
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceTest {

    private fun date(text: String) = LocalDate.parse(text)

    // ------------------------------------------------------------------ Nächster Termin

    @Test
    fun taeglich_geht_einen_tag_weiter() {
        assertEquals(
            date("2026-08-18"),
            Recurrence.nextAfter(RecurrenceRule(Frequency.DAILY), date("2026-08-17")),
        )
    }

    @Test
    fun alle_drei_tage_geht_drei_tage_weiter() {
        assertEquals(
            date("2026-08-20"),
            Recurrence.nextAfter(RecurrenceRule(Frequency.DAILY, interval = 3), date("2026-08-17")),
        )
    }

    @Test
    fun woechentlich_ohne_wochentage_bleibt_am_selben_wochentag() {
        val next = Recurrence.nextAfter(RecurrenceRule(Frequency.WEEKLY), date("2026-08-17"))
        assertEquals(date("2026-08-24"), next)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
    }

    @Test
    fun werktags_springt_von_freitag_auf_montag() {
        val regel = RecurrenceRule(Frequency.WEEKLY, byDay = RecurrenceRule.WEEKDAYS)
        // 21.08.2026 ist ein Freitag.
        assertEquals(date("2026-08-24"), Recurrence.nextAfter(regel, date("2026-08-21")))
    }

    @Test
    fun werktags_geht_innerhalb_der_woche_taeglich_weiter() {
        val regel = RecurrenceRule(Frequency.WEEKLY, byDay = RecurrenceRule.WEEKDAYS)
        assertEquals(date("2026-08-18"), Recurrence.nextAfter(regel, date("2026-08-17")))
        assertEquals(date("2026-08-19"), Recurrence.nextAfter(regel, date("2026-08-18")))
    }

    @Test
    fun zweiwoechentlich_mit_wochentag_ueberspringt_eine_woche() {
        val regel = RecurrenceRule(Frequency.WEEKLY, interval = 2, byDay = setOf(DayOfWeek.MONDAY))
        assertEquals(date("2026-08-31"), Recurrence.nextAfter(regel, date("2026-08-17")))
    }

    @Test
    fun monatlich_am_31_wird_im_februar_abgeschnitten_statt_uebersprungen() {
        // Eine Monatsaufgabe soll auch im Februar erscheinen.
        val regel = RecurrenceRule(Frequency.MONTHLY)
        assertEquals(date("2027-02-28"), Recurrence.nextAfter(regel, date("2027-01-31")))
    }

    @Test
    fun jaehrlich_am_29_februar_landet_im_normaljahr_auf_dem_28() {
        val regel = RecurrenceRule(Frequency.YEARLY)
        assertEquals(date("2029-02-28"), Recurrence.nextAfter(regel, date("2028-02-29")))
    }

    // ------------------------------------------------------------------------ Vorrücken

    @Test
    fun abhaken_am_faelligkeitstag_ueberspringt_nichts() {
        val ergebnis = Recurrence.advance(
            rule = RecurrenceRule(Frequency.DAILY),
            currentDue = date("2026-08-17"),
            today = date("2026-08-17"),
        )
        assertEquals(date("2026-08-18"), ergebnis.nextDue)
        assertEquals(0, ergebnis.skipped)
    }

    @Test
    fun nach_zwei_wochen_urlaub_werden_verpasste_termine_uebersprungen_und_gezaehlt() {
        // Genau die Regel aus dem Projektplan: sonst tötet eine tägliche Aufgabe im
        // Urlaub das Pet.
        val ergebnis = Recurrence.advance(
            rule = RecurrenceRule(Frequency.DAILY),
            currentDue = date("2026-08-01"),
            today = date("2026-08-15"),
        )
        assertEquals(date("2026-08-16"), ergebnis.nextDue)
        assertEquals(14, ergebnis.skipped)
    }

    @Test
    fun es_entsteht_immer_nur_ein_naechster_termin() {
        // Egal wie lange man weg war: heraus kommt genau ein Datum, keine Nachholliste.
        val ergebnis = Recurrence.advance(
            rule = RecurrenceRule(Frequency.DAILY),
            currentDue = date("2026-01-01"),
            today = date("2026-08-17"),
        )
        assertEquals(date("2026-08-18"), ergebnis.nextDue)
        assertTrue(ergebnis.skipped > 200)
    }

    @Test
    fun eine_serie_mit_until_endet_am_stichtag() {
        val ergebnis = Recurrence.advance(
            rule = RecurrenceRule(Frequency.DAILY, until = date("2026-08-17")),
            currentDue = date("2026-08-17"),
            today = date("2026-08-17"),
        )
        assertNull(ergebnis.nextDue)
        assertTrue(ergebnis.isFinished)
    }

    @Test
    fun eine_serie_mit_count_zaehlt_herunter_und_endet() {
        var regel: RecurrenceRule? = RecurrenceRule(Frequency.DAILY, count = 3)
        var faellig = date("2026-08-17")
        val termine = mutableListOf<LocalDate>()

        while (regel != null) {
            val ergebnis = Recurrence.advance(regel, faellig, faellig)
            regel = ergebnis.remainingRule
            val next = ergebnis.nextDue ?: break
            termine += next
            faellig = next
        }

        assertEquals(listOf(date("2026-08-18"), date("2026-08-19"), date("2026-08-20")), termine)
    }

    @Test
    fun werktagsserie_ueberspringt_das_wochenende_beim_nachholen() {
        val regel = RecurrenceRule(Frequency.WEEKLY, byDay = RecurrenceRule.WEEKDAYS)
        // Fällig am Donnerstag, abgehakt erst am Sonntag: der nächste ist der Montag.
        val ergebnis = Recurrence.advance(
            rule = regel,
            currentDue = date("2026-08-20"),
            today = date("2026-08-23"),
        )
        assertEquals(date("2026-08-24"), ergebnis.nextDue)
        assertEquals(DayOfWeek.MONDAY, ergebnis.nextDue?.dayOfWeek)
    }

    @Test
    fun der_naechste_termin_liegt_immer_echt_nach_heute() {
        val regeln = listOf(
            RecurrenceRule(Frequency.DAILY),
            RecurrenceRule(Frequency.DAILY, interval = 5),
            RecurrenceRule(Frequency.WEEKLY, byDay = RecurrenceRule.WEEKDAYS),
            RecurrenceRule(Frequency.MONTHLY),
            RecurrenceRule(Frequency.YEARLY),
        )
        val heute = date("2026-08-17")
        regeln.forEach { regel ->
            val ergebnis = Recurrence.advance(regel, date("2026-01-15"), heute)
            assertTrue("$regel: ${ergebnis.nextDue}", ergebnis.nextDue!!.isAfter(heute))
        }
    }
}
