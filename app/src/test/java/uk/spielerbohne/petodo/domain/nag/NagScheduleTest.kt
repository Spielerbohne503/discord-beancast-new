package uk.spielerbohne.petodo.domain.nag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.spielerbohne.petodo.domain.TestTasks.BERLIN
import uk.spielerbohne.petodo.domain.TestTasks.at
import uk.spielerbohne.petodo.domain.TestTasks.task
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId

class NagScheduleTest {

    @Test
    fun nag_termin_bleibt_ueber_die_zeitumstellung_bei_gleicher_uhrzeit() {
        // In der Nacht auf den 29.03.2026 wird in Berlin die Uhr vorgestellt.
        val anchor = LocalTime.of(8, 0)
        val vorher = at("2026-03-28", "08:00")

        val nachher = NagSchedule.nextNagAt(vorher, BERLIN, anchor)

        assertEquals(LocalTime.of(8, 0), nachher.atZone(BERLIN).toLocalTime())
        assertEquals("2026-03-29", nachher.atZone(BERLIN).toLocalDate().toString())
        // 23 Stunden statt 24: genau der Fehler, den +86.400.000 ms machen würde.
        assertEquals(Duration.ofHours(23), Duration.between(vorher, nachher))
    }

    @Test
    fun nag_termin_bleibt_auch_beim_zurueckstellen_bei_gleicher_uhrzeit() {
        // In der Nacht auf den 25.10.2026 wird die Uhr zurückgestellt.
        val anchor = LocalTime.of(8, 0)
        val vorher = at("2026-10-24", "08:00")

        val nachher = NagSchedule.nextNagAt(vorher, BERLIN, anchor)

        assertEquals(LocalTime.of(8, 0), nachher.atZone(BERLIN).toLocalTime())
        assertEquals(Duration.ofHours(25), Duration.between(vorher, nachher))
    }

    @Test
    fun nag_faellt_nicht_aus_wenn_die_ankeruhrzeit_wegen_der_umstellung_fehlt() {
        // 02:30 gibt es am 29.03.2026 in Berlin nicht. Der Nag darf trotzdem nicht
        // verschwinden — java.time schiebt ihn nach vorn.
        val nachher = NagSchedule.nextNagAt(at("2026-03-28", "02:30"), BERLIN, LocalTime.of(2, 30))

        assertEquals("2026-03-29", nachher.atZone(BERLIN).toLocalDate().toString())
        assertEquals(LocalTime.of(3, 30), nachher.atZone(BERLIN).toLocalTime())
    }

    @Test
    fun ueber_hundert_tage_bleibt_die_uhrzeit_stehen() {
        val anchor = LocalTime.of(8, 0)
        var termin = at("2026-01-01", "08:00")
        repeat(200) {
            termin = NagSchedule.nextNagAt(termin, BERLIN, anchor)
            assertEquals(anchor, termin.atZone(BERLIN).toLocalTime())
        }
        assertEquals("2026-07-20", termin.atZone(BERLIN).toLocalDate().toString())
    }

    @Test
    fun anker_ist_die_gespeicherte_ortszeit_nicht_der_zeitstempel() {
        val aufgabe = task(
            dueAt = at("2026-08-17", "08:00"),
            hasTime = true,
            dueTimeLocal = LocalTime.of(8, 0),
        )
        // Dieselbe Aufgabe, in Tokio betrachtet: die Ortszeit des Ankers gewinnt.
        assertEquals(LocalTime.of(8, 0), NagSchedule.anchorTime(aufgabe, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun tagestermin_ohne_uhrzeit_bekommt_die_vorgabeuhrzeit() {
        val aufgabe = task(dueAt = at("2026-08-17", "00:00"), hasTime = false)

        assertEquals(LocalTime.of(9, 0), NagSchedule.anchorTime(aufgabe, BERLIN))
        assertEquals(at("2026-08-17", "09:00"), NagSchedule.firstNagAt(aufgabe, BERLIN))
    }

    @Test
    fun aufgabe_mit_uhrzeit_wird_zur_faelligkeit_gemahnt() {
        val aufgabe = task(dueAt = at("2026-08-17", "14:30"), hasTime = true)
        assertEquals(at("2026-08-17", "14:30"), NagSchedule.firstNagAt(aufgabe, BERLIN))
    }

    @Test
    fun ohne_faelligkeit_gibt_es_keinen_alarm() {
        assertNull(NagSchedule.firstNagAt(task(), BERLIN))
    }

    @Test
    fun morgen_schiebt_die_faelligkeit_um_einen_kalendertag() {
        val aufgabe = task(
            dueAt = at("2026-08-17", "08:00"),
            hasTime = true,
            dueTimeLocal = LocalTime.of(8, 0),
        )
        val neu = NagSchedule.postponeToTomorrow(aufgabe, at("2026-08-17", "09:15"), BERLIN)

        assertEquals("2026-08-18", neu.atZone(BERLIN).toLocalDate().toString())
        assertEquals(LocalTime.of(8, 0), neu.atZone(BERLIN).toLocalTime())
    }

    @Test
    fun morgen_landet_auch_bei_laengst_ueberfaelligen_aufgaben_auf_morgen() {
        // Sonst schiebt "Morgen" eine drei Wochen alte Aufgabe auf vorgestern.
        val aufgabe = task(
            dueAt = at("2026-07-01", "08:00"),
            hasTime = true,
            dueTimeLocal = LocalTime.of(8, 0),
        )
        val neu = NagSchedule.postponeToTomorrow(aufgabe, at("2026-08-17", "10:00"), BERLIN)

        assertEquals("2026-08-18", neu.atZone(BERLIN).toLocalDate().toString())
    }
}
