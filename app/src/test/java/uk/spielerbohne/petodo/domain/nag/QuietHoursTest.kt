package uk.spielerbohne.petodo.domain.nag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.spielerbohne.petodo.domain.TestTasks.BERLIN
import uk.spielerbohne.petodo.domain.TestTasks.at
import java.time.LocalTime

class QuietHoursTest {

    private val nachts = QuietHours(LocalTime.of(23, 0), LocalTime.of(8, 0))

    @Test
    fun vorgabe_ist_23_bis_8() {
        assertEquals(LocalTime.of(23, 0), QuietHours.DEFAULT.start)
        assertEquals(LocalTime.of(8, 0), QuietHours.DEFAULT.end)
        assertTrue(QuietHours.DEFAULT.enabled)
    }

    @Test
    fun fenster_ueber_mitternacht_umfasst_beide_seiten() {
        assertTrue(nachts.contains(LocalTime.of(23, 30)))
        assertTrue(nachts.contains(LocalTime.of(3, 0)))
        assertTrue(nachts.contains(LocalTime.of(23, 0)))
        assertFalse(nachts.contains(LocalTime.of(8, 0)))
        assertFalse(nachts.contains(LocalTime.of(12, 0)))
        assertFalse(nachts.contains(LocalTime.of(22, 59)))
    }

    @Test
    fun fenster_innerhalb_eines_tages_funktioniert_ebenso() {
        val mittags = QuietHours(LocalTime.of(13, 0), LocalTime.of(14, 0))
        assertTrue(mittags.contains(LocalTime.of(13, 30)))
        assertFalse(mittags.contains(LocalTime.of(12, 59)))
        assertFalse(mittags.contains(LocalTime.of(14, 0)))
        assertFalse(mittags.contains(LocalTime.of(3, 0)))
    }

    @Test
    fun ausgeschaltete_ruhezeit_haelt_nichts_auf() {
        assertFalse(QuietHours.OFF.contains(LocalTime.of(3, 0)))
    }

    @Test
    fun gleicher_anfang_und_ende_bedeutet_kein_fenster() {
        val leer = QuietHours(LocalTime.of(8, 0), LocalTime.of(8, 0))
        assertFalse(leer.contains(LocalTime.of(8, 0)))
        assertFalse(leer.contains(LocalTime.of(3, 0)))
    }

    @Test
    fun nag_am_spaeten_abend_wandert_auf_den_naechsten_morgen() {
        val verschoben = QuietHoursPolicy.shiftOutOfQuietHours(at("2026-08-17", "23:30"), BERLIN, nachts)
        assertEquals(at("2026-08-18", "08:00"), verschoben)
    }

    @Test
    fun nag_mitten_in_der_nacht_wandert_auf_denselben_morgen() {
        val verschoben = QuietHoursPolicy.shiftOutOfQuietHours(at("2026-08-18", "02:00"), BERLIN, nachts)
        assertEquals(at("2026-08-18", "08:00"), verschoben)
    }

    @Test
    fun nag_ausserhalb_der_ruhezeit_bleibt_unveraendert() {
        val termin = at("2026-08-17", "10:00")
        assertEquals(termin, QuietHoursPolicy.shiftOutOfQuietHours(termin, BERLIN, nachts))
    }

    @Test
    fun ein_verschobener_nag_wird_nie_verworfen() {
        // Egal wann in der Nacht: es kommt immer ein Zeitpunkt heraus, der danach liegt.
        listOf("23:00", "23:59", "00:00", "04:44", "07:59").forEach { uhrzeit ->
            val termin = at("2026-08-17", uhrzeit)
            val verschoben = QuietHoursPolicy.shiftOutOfQuietHours(termin, BERLIN, nachts)
            assertTrue("$uhrzeit wurde nicht nach vorn geschoben", verschoben > termin)
            assertFalse(nachts.contains(verschoben.atZone(BERLIN).toLocalTime()))
        }
    }

    @Test
    fun verschieben_ist_stabil_wenn_man_es_zweimal_anwendet() {
        val einmal = QuietHoursPolicy.shiftOutOfQuietHours(at("2026-08-17", "23:30"), BERLIN, nachts)
        val zweimal = QuietHoursPolicy.shiftOutOfQuietHours(einmal, BERLIN, nachts)
        assertEquals(einmal, zweimal)
    }
}
