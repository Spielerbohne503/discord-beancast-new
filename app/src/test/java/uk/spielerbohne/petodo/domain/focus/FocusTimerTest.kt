package uk.spielerbohne.petodo.domain.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.spielerbohne.petodo.domain.TestTasks.at
import java.time.Duration
import java.time.Instant

class FocusTimerTest {

    private val settings = FocusSettings.DEFAULT
    private val start = at("2026-08-17", "10:00")

    private fun session(
        phase: FocusPhase = FocusPhase.FOCUS,
        startedAt: Instant = start,
        endsAt: Instant = start.plus(Duration.ofMinutes(25)),
        pausedAt: Instant? = null,
        completedAt: Instant? = null,
        abortedAt: Instant? = null,
    ) = FocusSession(
        id = "s1",
        phase = phase,
        startedAt = startedAt,
        endsAt = endsAt,
        pausedAt = pausedAt,
        completedAt = completedAt,
        abortedAt = abortedAt,
    )

    // ------------------------------------------------------------------------- Zustand

    @Test
    fun ein_endzeitpunkt_in_der_vergangenheit_bedeutet_abgeschlossen_nicht_laeuft_noch() {
        // Der Kern des absoluten Endzeitpunkts: Auch wenn das Gerät zwischendurch schlief,
        // die App weggewischt oder der Dienst abgeschossen wurde — abgelaufen ist
        // abgelaufen.
        val zustand = FocusTimer.stateOf(session(), now = start.plus(Duration.ofHours(3)))

        assertTrue("Erwartet Elapsed, war $zustand", zustand is FocusState.Elapsed)
    }

    @Test
    fun genau_am_endzeitpunkt_gilt_der_durchlauf_als_abgelaufen() {
        val zustand = FocusTimer.stateOf(session(), now = start.plus(Duration.ofMinutes(25)))
        assertTrue(zustand is FocusState.Elapsed)
    }

    @Test
    fun waehrend_der_runde_laeuft_der_timer_und_zaehlt_richtig_herunter() {
        val zustand = FocusTimer.stateOf(session(), now = start.plus(Duration.ofMinutes(6)))

        assertTrue(zustand is FocusState.Running)
        assertEquals(Duration.ofMinutes(19), (zustand as FocusState.Running).remaining)
    }

    @Test
    fun ohne_sitzung_ist_der_timer_bereit() {
        assertEquals(FocusState.Ready, FocusTimer.stateOf(null, now = start))
    }

    @Test
    fun eine_abgeschlossene_oder_abgebrochene_sitzung_laesst_den_timer_bereit() {
        assertEquals(
            FocusState.Ready,
            FocusTimer.stateOf(session(completedAt = start.plus(Duration.ofMinutes(25))), start),
        )
        assertEquals(
            FocusState.Ready,
            FocusTimer.stateOf(session(abortedAt = start.plus(Duration.ofMinutes(3))), start),
        )
    }

    // -------------------------------------------------------------- Anhalten, Fortsetzen

    @Test
    fun eine_angehaltene_runde_friert_den_rest_ein() {
        val angehalten = session(pausedAt = start.plus(Duration.ofMinutes(10)))

        // Auch eine Stunde später steht der Rest noch bei 15 Minuten.
        val zustand = FocusTimer.stateOf(angehalten, now = start.plus(Duration.ofHours(1)))

        assertTrue(zustand is FocusState.Paused)
        assertEquals(Duration.ofMinutes(15), (zustand as FocusState.Paused).remaining)
    }

    @Test
    fun fortsetzen_schiebt_den_endzeitpunkt_um_die_standzeit_nach_hinten() {
        val angehalten = session(pausedAt = start.plus(Duration.ofMinutes(10)))
        val fortgesetzt = start.plus(Duration.ofHours(2))

        val neuesEnde = FocusTimer.resumedEndsAt(angehalten, now = fortgesetzt)

        assertEquals(fortgesetzt.plus(Duration.ofMinutes(15)), neuesEnde)
    }

    @Test
    fun der_rest_wird_nie_negativ() {
        val rest = FocusTimer.remainingAt(session(), at = start.plus(Duration.ofHours(5)))
        assertEquals(Duration.ZERO, rest)
    }

    // --------------------------------------------------------------------------- Zyklus

    @Test
    fun nach_vier_fokusrunden_folgt_die_lange_pause() {
        assertEquals(FocusPhase.SHORT_BREAK, FocusTimer.breakAfterFocus(1, settings))
        assertEquals(FocusPhase.SHORT_BREAK, FocusTimer.breakAfterFocus(2, settings))
        assertEquals(FocusPhase.SHORT_BREAK, FocusTimer.breakAfterFocus(3, settings))
        assertEquals(FocusPhase.LONG_BREAK, FocusTimer.breakAfterFocus(4, settings))
        assertEquals(FocusPhase.SHORT_BREAK, FocusTimer.breakAfterFocus(5, settings))
        assertEquals(FocusPhase.LONG_BREAK, FocusTimer.breakAfterFocus(8, settings))
    }

    @Test
    fun nach_der_fokusrunde_beginnt_die_pause_von_selbst() {
        val naechstes = FocusTimer.next(FocusPhase.FOCUS, completedFocusRounds = 1, settings = settings)

        assertTrue(naechstes is FocusNext.StartBreak)
        assertEquals(FocusPhase.SHORT_BREAK, (naechstes as FocusNext.StartBreak).phase)
        assertEquals(Duration.ofMinutes(5), naechstes.duration)
    }

    @Test
    fun nach_der_pause_geht_der_timer_in_den_bereitzustand_zurueck() {
        // Der Wiedereinstieg bleibt eine Entscheidung — der Timer läuft nicht durch.
        assertEquals(
            FocusNext.BackToReady,
            FocusTimer.next(FocusPhase.SHORT_BREAK, completedFocusRounds = 1, settings = settings),
        )
        assertEquals(
            FocusNext.BackToReady,
            FocusTimer.next(FocusPhase.LONG_BREAK, completedFocusRounds = 4, settings = settings),
        )
    }

    @Test
    fun die_vorgaben_sind_25_5_20_und_lange_pause_nach_vier_runden() {
        assertEquals(Duration.ofMinutes(25), settings.durationOf(FocusPhase.FOCUS))
        assertEquals(Duration.ofMinutes(5), settings.durationOf(FocusPhase.SHORT_BREAK))
        assertEquals(Duration.ofMinutes(20), settings.durationOf(FocusPhase.LONG_BREAK))
        assertEquals(4, settings.roundsBeforeLongBreak)
    }

    @Test
    fun eigene_dauern_werden_uebernommen_und_nie_null() {
        val eigene = FocusSettings(focusMinutes = 50, shortBreakMinutes = 0, longBreakMinutes = 30)

        assertEquals(Duration.ofMinutes(50), eigene.durationOf(FocusPhase.FOCUS))
        // Eine Null-Minuten-Pause wäre kein Timer, sondern ein Fehler.
        assertEquals(Duration.ofMinutes(1), eigene.durationOf(FocusPhase.SHORT_BREAK))
    }

    @Test
    fun der_endzeitpunkt_ergibt_sich_aus_start_und_dauer() {
        assertEquals(
            start.plus(Duration.ofMinutes(25)),
            FocusTimer.endsAt(start, FocusPhase.FOCUS, settings),
        )
    }

    // ------------------------------------------------------------------------ Belohnung

    @Test
    fun nur_eine_abgeschlossene_fokusrunde_gibt_belohnung() {
        assertTrue(session(completedAt = start).earnsReward)
        // Abgebrochene Runden geben keine Belohnung.
        assertTrue(!session(completedAt = start, abortedAt = start).earnsReward)
        assertTrue(!session(abortedAt = start).earnsReward)
        // Pausen sind keine Arbeit.
        assertTrue(!session(phase = FocusPhase.SHORT_BREAK, completedAt = start).earnsReward)
    }

    // -------------------------------------------------------------------------- Anzeige

    @Test
    fun die_anzeige_zeigt_minuten_und_sekunden() {
        assertEquals("18:42", FocusTimer.format(Duration.ofSeconds(18 * 60 + 42)))
        assertEquals("0:07", FocusTimer.format(Duration.ofSeconds(7)))
        assertEquals("25:00", FocusTimer.format(Duration.ofMinutes(25)))
        assertEquals("0:00", FocusTimer.format(Duration.ofSeconds(-5)))
    }

    @Test
    fun eine_unbekannte_phase_aus_der_datenbank_stuerzt_nicht_ab() {
        assertEquals(null, FocusPhase.parse("QUATSCH"))
        assertEquals(null, FocusPhase.parse(null))
        assertEquals(FocusPhase.LONG_BREAK, FocusPhase.parse("LONG_BREAK"))
    }
}
