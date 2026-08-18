package uk.spielerbohne.petodo.domain.quickadd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Deutsche Datumserkennung in der Schnell-Eingabe.
 *
 * Der Maßstab: Was erkannt wird, muss **verschwinden**. Ein Termin daneben und derselbe
 * Text noch einmal im Titel wäre schlimmer als gar keine Erkennung.
 */
class QuickAddParserTest {

    /** Ein Montag, damit die Wochentagsfälle nachvollziehbar sind. */
    private val jetzt = LocalDateTime.of(2026, 8, 17, 10, 0)

    private fun parse(text: String) = QuickAddParser.parse(text, jetzt)

    @Test
    fun ohne_zeitangabe_bleibt_alles_wie_getippt() {
        val ergebnis = parse("Rasen mähen")

        assertEquals("Rasen mähen", ergebnis.title)
        assertNull(ergebnis.date)
        assertNull(ergebnis.time)
    }

    @Test
    fun morgen_neun_uhr_zahnarzt_wird_zu_zahnarzt() {
        val ergebnis = parse("morgen 9 Uhr Zahnarzt")

        assertEquals("Zahnarzt", ergebnis.title)
        assertEquals(LocalDate.of(2026, 8, 18), ergebnis.date)
        assertEquals(LocalTime.of(9, 0), ergebnis.time)
    }

    @Test
    fun die_erkannten_woerter_verschwinden_auch_mitten_im_satz() {
        val ergebnis = parse("Bericht morgen abgeben")

        assertEquals("Bericht abgeben", ergebnis.title)
        assertEquals(LocalDate.of(2026, 8, 18), ergebnis.date)
    }

    @Test
    fun heute_und_uebermorgen_werden_erkannt() {
        assertEquals(LocalDate.of(2026, 8, 17), parse("heute einkaufen").date)
        assertEquals(LocalDate.of(2026, 8, 19), parse("übermorgen einkaufen").date)
        assertEquals("einkaufen", parse("übermorgen einkaufen").title)
    }

    @Test
    fun ein_wochentag_meint_immer_den_naechsten_nie_heute() {
        // Der 17.8.2026 ist ein Montag. "Montag" an einem Montag meint den 24.
        assertEquals(LocalDate.of(2026, 8, 24), parse("Montag Sport").date)
        assertEquals(LocalDate.of(2026, 8, 21), parse("Freitag Sport").date)
        assertEquals("Sport", parse("Freitag Sport").title)
    }

    @Test
    fun naechsten_montag_ist_dasselbe_wie_montag() {
        assertEquals(LocalDate.of(2026, 8, 24), parse("nächsten Montag Sport").date)
        assertEquals("Sport", parse("nächsten Montag Sport").title)
    }

    @Test
    fun in_drei_tagen_und_in_zwei_wochen() {
        assertEquals(LocalDate.of(2026, 8, 20), parse("in 3 Tagen anrufen").date)
        assertEquals(LocalDate.of(2026, 8, 31), parse("in 2 Wochen anrufen").date)
        assertEquals("anrufen", parse("in 3 Tagen anrufen").title)
    }

    @Test
    fun ein_datum_mit_punkten_wird_gelesen() {
        assertEquals(LocalDate.of(2026, 9, 12), parse("12.9. Werkstatt").date)
        assertEquals(LocalDate.of(2026, 9, 12), parse("12.09.2026 Werkstatt").date)
        assertEquals("Werkstatt", parse("12.9. Werkstatt").title)
    }

    @Test
    fun ein_datum_ohne_jahr_meint_das_naechste_vorkommen() {
        // Am 17. August ist der 2. Januar erst im nächsten Jahr fällig.
        assertEquals(LocalDate.of(2027, 1, 2), parse("2.1. Steuer").date)
    }

    @Test
    fun ein_monatsname_wird_gelesen() {
        assertEquals(LocalDate.of(2026, 9, 3), parse("3. September Konzert").date)
        assertEquals("Konzert", parse("3. September Konzert").title)
        assertEquals(LocalDate.of(2027, 3, 1), parse("1. März 2027 Frist").date)
    }

    @Test
    fun ein_unmoegliches_datum_wird_nicht_erkannt() {
        // 31.2. gibt es nicht — dann lieber gar kein Termin als ein falscher.
        val ergebnis = parse("31.2. Unsinn")

        assertNull(ergebnis.date)
        assertEquals("31.2. Unsinn", ergebnis.title)
    }

    @Test
    fun uhrzeiten_in_allen_gebraeuchlichen_schreibweisen() {
        assertEquals(LocalTime.of(14, 30), parse("Termin 14:30").time)
        assertEquals(LocalTime.of(14, 30), parse("Termin um 14:30").time)
        assertEquals(LocalTime.of(9, 0), parse("Termin 9 Uhr").time)
        assertEquals(LocalTime.of(9, 30), parse("Termin 9.30 Uhr").time)
        assertEquals("Termin", parse("Termin um 14:30").title)
    }

    @Test
    fun ungefaehre_tageszeiten_bekommen_feste_uhrzeiten() {
        assertEquals(LocalTime.of(12, 0), parse("mittags essen").time)
        assertEquals(LocalTime.of(18, 0), parse("abends laufen").time)
        assertEquals("laufen", parse("abends laufen").title)
    }

    @Test
    fun morgens_ist_nicht_morgen() {
        // Die beiden Wörter unterscheiden sich um einen Buchstaben und um einen Tag.
        val morgens = parse("morgens joggen")
        assertEquals(LocalTime.of(8, 0), morgens.time)
        assertEquals(LocalDate.of(2026, 8, 18), morgens.date) // 8 Uhr ist um 10 Uhr vorbei

        val morgen = parse("morgen joggen")
        assertNull(morgen.time)
        assertEquals(LocalDate.of(2026, 8, 18), morgen.date)
    }

    @Test
    fun eine_uhrzeit_ohne_tag_meint_heute_solange_sie_nicht_vorbei_ist() {
        // Es ist 10:00 Uhr.
        assertEquals(LocalDate.of(2026, 8, 17), parse("14 Uhr Termin").date)
        assertEquals(LocalDate.of(2026, 8, 18), parse("8 Uhr Termin").date)
    }

    @Test
    fun eine_uhrzeit_mit_tag_laesst_den_tag_stehen() {
        val ergebnis = parse("Freitag 8 Uhr Termin")

        assertEquals(LocalDate.of(2026, 8, 21), ergebnis.date)
        assertEquals(LocalTime.of(8, 0), ergebnis.time)
        assertEquals("Termin", ergebnis.title)
    }

    @Test
    fun ein_titel_der_nur_wie_ein_datum_aussieht_wird_nicht_zerpflueckt() {
        // "Freitagsessen" enthält "Freitag", ist aber ein Wort — Wortgrenzen schützen davor.
        val ergebnis = parse("Freitagsessen planen")

        assertNull(ergebnis.date)
        assertEquals("Freitagsessen planen", ergebnis.title)
    }

    @Test
    fun unsinnige_uhrzeiten_werden_uebergangen() {
        assertNull(parse("Termin 99:99").time)
        assertNull(parse("Termin 25 Uhr").time)
    }

    @Test
    fun bindestriche_und_kommas_bleiben_nicht_als_rest_stehen() {
        assertEquals("Zahnarzt", parse("Zahnarzt, morgen").title)
        assertEquals("Zahnarzt", parse("morgen - Zahnarzt").title)
    }

    @Test
    fun ein_leerer_titel_bleibt_leer() {
        val ergebnis = parse("morgen")

        assertEquals("", ergebnis.title)
        assertEquals(LocalDate.of(2026, 8, 18), ergebnis.date)
    }

    @Test
    fun grossschreibung_ist_egal() {
        assertEquals(LocalDate.of(2026, 8, 18), parse("MORGEN Zahnarzt").date)
        assertEquals("Zahnarzt", parse("MORGEN Zahnarzt").title)
    }
}
