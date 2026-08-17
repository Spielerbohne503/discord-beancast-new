package uk.spielerbohne.petodo.domain.nag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.spielerbohne.petodo.domain.TestTasks.BERLIN
import uk.spielerbohne.petodo.domain.TestTasks.at
import uk.spielerbohne.petodo.domain.TestTasks.task
import java.time.LocalTime

class NagDecisionTest {

    private val jetzt = at("2026-08-17", "10:00")
    private val ruhezeit = QuietHours.DEFAULT

    private fun entscheide(
        aufgabe: uk.spielerbohne.petodo.domain.model.Task,
        listeAusgenommen: Boolean = false,
        now: java.time.Instant = jetzt,
        quietHours: QuietHours = ruhezeit,
    ) = NagDecision.decide(aufgabe, listeAusgenommen, now, BERLIN, quietHours)

    // ------------------------------------------------------------------ Abbruchgründe

    @Test
    fun erledigte_aufgabe_nimmt_ihre_meldung_zurueck() {
        val ergebnis = entscheide(
            task(dueAt = at("2026-08-17", "09:00"), hasTime = true, completedAt = at("2026-08-17", "09:30"))
        )
        assertEquals(NagOutcome.Cancel(NagOutcome.Cancel.Reason.COMPLETED), ergebnis)
    }

    @Test
    fun geloeschte_aufgabe_nagt_nicht_weiter() {
        val ergebnis = entscheide(
            task(dueAt = at("2026-08-17", "09:00"), hasTime = true, deletedAt = at("2026-08-17", "09:30"))
        )
        assertEquals(NagOutcome.Cancel(NagOutcome.Cancel.Reason.DELETED), ergebnis)
    }

    @Test
    fun aufgabe_in_der_irgendwann_liste_nagt_nie() {
        val ergebnis = entscheide(
            task(dueAt = at("2026-08-01", "09:00"), hasTime = true),
            listeAusgenommen = true,
        )
        assertEquals(NagOutcome.Cancel(NagOutcome.Cancel.Reason.LIST_EXCLUDED), ergebnis)
    }

    @Test
    fun aufgabe_ohne_faelligkeit_nagt_nie() {
        assertEquals(NagOutcome.Cancel(NagOutcome.Cancel.Reason.NO_DUE_DATE), entscheide(task()))
    }

    // -------------------------------------------------------------------- Verschieben

    @Test
    fun aufgeschobene_aufgabe_wird_nur_neu_eingeplant() {
        val ergebnis = entscheide(
            task(
                dueAt = at("2026-08-17", "09:00"),
                hasTime = true,
                snoozedUntil = at("2026-08-17", "11:00"),
            )
        )
        assertEquals(
            NagOutcome.Reschedule(at("2026-08-17", "11:00"), NagOutcome.Reschedule.Reason.SNOOZED),
            ergebnis,
        )
    }

    @Test
    fun noch_nicht_faellige_aufgabe_wird_auf_ihren_termin_gelegt() {
        val ergebnis = entscheide(task(dueAt = at("2026-08-17", "18:00"), hasTime = true))
        assertEquals(
            NagOutcome.Reschedule(at("2026-08-17", "18:00"), NagOutcome.Reschedule.Reason.NOT_DUE_YET),
            ergebnis,
        )
    }

    @Test
    fun waehrend_der_ruhezeit_wird_ans_fensterende_geschoben_statt_verworfen() {
        val ergebnis = entscheide(
            task(dueAt = at("2026-08-17", "23:15"), hasTime = true),
            now = at("2026-08-17", "23:20"),
        )
        assertEquals(
            NagOutcome.Reschedule(at("2026-08-18", "08:00"), NagOutcome.Reschedule.Reason.QUIET_HOURS),
            ergebnis,
        )
    }

    @Test
    fun ein_nag_mitten_in_der_nacht_geht_nie_verloren() {
        var ergebnis = entscheide(
            task(dueAt = at("2026-08-18", "02:00"), hasTime = true),
            now = at("2026-08-18", "02:00"),
        )
        assertTrue(ergebnis is NagOutcome.Reschedule)
        // Am Fensterende wird dann tatsächlich gemeldet.
        ergebnis = entscheide(
            task(dueAt = at("2026-08-18", "02:00"), hasTime = true),
            now = at("2026-08-18", "08:00"),
        )
        assertTrue(ergebnis is NagOutcome.Post)
    }

    // --------------------------------------------------------------------- Eskalation

    @Test
    fun tag_1_meldet_lautlos() {
        val ergebnis = entscheide(task(dueAt = at("2026-08-17", "09:00"), hasTime = true)) as NagOutcome.Post

        assertEquals(NagStage.FIRST, ergebnis.stage)
        assertEquals(1, ergebnis.day)
        assertEquals(1, ergebnis.nagCount)
        assertEquals(false, ergebnis.stage.makesSound)
        assertEquals(false, ergebnis.stage.asksCleanupQuestion)
    }

    @Test
    fun die_stufen_folgen_der_tabelle_aus_dem_projektplan() {
        val erwartet = mapOf(
            1 to NagStage.FIRST,
            2 to NagStage.AGAIN,
            3 to NagStage.AGAIN,
            4 to NagStage.LOUD,
            5 to NagStage.LOUD,
            6 to NagStage.LOUD,
            7 to NagStage.CLEANUP,
            8 to NagStage.CLEANUP,
            30 to NagStage.CLEANUP,
        )
        erwartet.forEach { (tag, stufe) ->
            val ergebnis = entscheide(
                task(dueAt = at("2026-08-01", "09:00"), hasTime = true, nagCount = tag - 1)
            ) as NagOutcome.Post
            assertEquals("Tag $tag", stufe, ergebnis.stage)
            assertEquals("Tag $tag", tag, ergebnis.day)
        }
    }

    @Test
    fun ab_tag_4_kommt_ton_statt_lautlos() {
        assertEquals(false, NagEscalation.stageFor(2).makesSound) // Tag 3
        assertEquals(true, NagEscalation.stageFor(3).makesSound)  // Tag 4
    }

    @Test
    fun ab_tag_2_kommentiert_das_pet() {
        assertEquals(false, NagEscalation.stageFor(0).petComments)
        assertEquals(true, NagEscalation.stageFor(1).petComments)
    }

    @Test
    fun an_tag_7_steht_die_aufraeum_frage() {
        val ergebnis = entscheide(
            task(dueAt = at("2026-08-01", "09:00"), hasTime = true, nagCount = 6)
        ) as NagOutcome.Post

        assertEquals(NagStage.CLEANUP, ergebnis.stage)
        assertTrue(ergebnis.stage.asksCleanupQuestion)
    }

    @Test
    fun die_kette_erreicht_tag_7_wenn_man_sie_laufen_laesst() {
        // Sieben Alarme hintereinander, jeder verschiebt den nächsten um einen Tag.
        var aufgabe = task(
            dueAt = at("2026-08-17", "08:00"),
            hasTime = true,
            dueTimeLocal = LocalTime.of(8, 0),
        )
        var uhr = at("2026-08-17", "08:00")
        val stufen = mutableListOf<NagStage>()

        repeat(7) {
            val ergebnis = entscheide(aufgabe, now = uhr) as NagOutcome.Post
            stufen += ergebnis.stage
            aufgabe = aufgabe.copy(nagCount = ergebnis.nagCount)
            uhr = ergebnis.nextNagAt
            assertEquals(LocalTime.of(8, 0), uhr.atZone(BERLIN).toLocalTime())
        }

        assertEquals(
            listOf(
                NagStage.FIRST,
                NagStage.AGAIN,
                NagStage.AGAIN,
                NagStage.LOUD,
                NagStage.LOUD,
                NagStage.LOUD,
                NagStage.CLEANUP,
            ),
            stufen,
        )
        assertEquals("2026-08-24", uhr.atZone(BERLIN).toLocalDate().toString())
    }

    @Test
    fun der_naechste_termin_liegt_morgen_zur_selben_uhrzeit() {
        val ergebnis = entscheide(
            task(dueAt = at("2026-08-17", "09:00"), hasTime = true, dueTimeLocal = LocalTime.of(9, 0))
        ) as NagOutcome.Post

        assertEquals(at("2026-08-18", "09:00"), ergebnis.nextNagAt)
    }

    @Test
    fun ein_nachttermin_wird_dauerhaft_ans_fensterende_gelegt() {
        // Fällig um 23:30, also mitten in der Ruhezeit: Der nächste Nag wäre am 19. um
        // 23:30 — er wird ans nächste Fensterende geschoben, den Morgen des 20.
        // Später als geplant, aber nie verworfen.
        val ergebnis = entscheide(
            task(dueAt = at("2026-08-17", "23:30"), hasTime = true, dueTimeLocal = LocalTime.of(23, 30)),
            now = at("2026-08-18", "10:00"),
        ) as NagOutcome.Post

        assertEquals(at("2026-08-20", "08:00"), ergebnis.nextNagAt)
        assertEquals(false, QuietHours.DEFAULT.contains(ergebnis.nextNagAt.atZone(BERLIN).toLocalTime()))
    }

    // ----------------------------------------------------------------- Sammelmeldung

    @Test
    fun ab_drei_ueberfaelligen_aufgaben_wird_gruppiert() {
        assertEquals(false, NagDecision.shouldGroup(2))
        assertEquals(true, NagDecision.shouldGroup(3))
        assertEquals(true, NagDecision.shouldGroup(9))
    }
}
