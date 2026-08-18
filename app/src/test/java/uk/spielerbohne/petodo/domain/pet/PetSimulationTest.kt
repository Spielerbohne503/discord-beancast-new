package uk.spielerbohne.petodo.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.spielerbohne.petodo.domain.TestTasks.at
import java.time.Duration
import java.time.Instant

class PetSimulationTest {

    private val start = at("2026-08-17", "10:00")

    private fun state(
        energy: Double = 100.0,
        satiety: Double = 100.0,
        mood: Double = 100.0,
        xp: Int = 0,
        at: Instant = start,
    ) = PetState(PetValues.of(energy, satiety, mood), xp, at)

    // ---------------------------------------------------------------------------- Verfall

    @Test
    fun energie_faellt_in_zwoelf_stunden_von_hundert_auf_null() {
        val nachher = PetSimulation.decay(PetValues.INITIAL, start, start.plus(Duration.ofHours(12)), load = 0.0)
        assertEquals(0.0, nachher.energy, 0.001)
    }

    @Test
    fun die_drei_basiszeiten_stimmen() {
        val sechsStunden = PetSimulation.decay(PetValues.INITIAL, start, start.plus(Duration.ofHours(6)), 0.0)

        // Energie 12 h, Sättigung 16 h, Laune 24 h — nach sechs Stunden entsprechend.
        assertEquals(50.0, sechsStunden.energy, 0.001)
        assertEquals(62.5, sechsStunden.satiety, 0.001)
        assertEquals(75.0, sechsStunden.mood, 0.001)
    }

    @Test
    fun verstrichene_zeit_wird_auf_vierundzwanzig_stunden_gedeckelt() {
        // Nach zwei Wochen Urlaub darf das Pet nicht schlechter dran sein als nach einem Tag.
        val einTag = PetSimulation.decay(PetValues.INITIAL, start, start.plus(Duration.ofHours(24)), 0.0)
        val zweiWochen = PetSimulation.decay(PetValues.INITIAL, start, start.plus(Duration.ofDays(14)), 0.0)

        assertEquals(einTag, zweiWochen)
    }

    @Test
    fun ohne_verstrichene_zeit_aendert_sich_nichts() {
        assertEquals(PetValues.INITIAL, PetSimulation.decay(PetValues.INITIAL, start, start, 0.0))
    }

    @Test
    fun eine_ruecklaufende_uhr_laesst_die_werte_in_ruhe() {
        val zurueck = PetSimulation.decay(PetValues.INITIAL, start, start.minus(Duration.ofHours(5)), 0.0)
        assertEquals(PetValues.INITIAL, zurueck)
    }

    @Test
    fun hohe_last_beschleunigt_den_verfall() {
        val ohneLast = PetSimulation.decay(PetValues.INITIAL, start, start.plus(Duration.ofHours(3)), load = 0.0)
        val volleLast = PetSimulation.decay(PetValues.INITIAL, start, start.plus(Duration.ofHours(3)), load = 10.0)

        assertTrue(volleLast.energy < ohneLast.energy)
        // mBasis geht von 1,0 auf 3,0 — der Verfall verdreifacht sich.
        assertEquals(100.0 - 25.0 * 3, volleLast.energy, 0.001)
    }

    // -------------------------------------------------------------------------- Kappung

    @Test
    fun kein_wert_kann_unter_null_fallen() {
        val lange = PetSimulation.decay(PetValues.INITIAL, start, start.plus(Duration.ofDays(30)), load = 10.0)

        assertTrue(lange.energy >= 0.0)
        assertTrue(lange.satiety >= 0.0)
        assertTrue(lange.mood >= 0.0)
    }

    @Test
    fun kein_wert_kann_ueber_hundert_steigen() {
        val gefuettert = PetSimulation.compute(
            previous = state(),
            events = List(20) { index ->
                RewardEvent.of("e$index", start.plusSeconds(index.toLong()), RewardType.FEED)
            },
            now = start.plusSeconds(30),
            load = 0.0,
        )

        assertTrue(gefuettert.values.energy <= 100.0)
        assertTrue(gefuettert.values.satiety <= 100.0)
        assertTrue(gefuettert.values.mood <= 100.0)
    }

    @Test
    fun die_werte_bleiben_ueber_zufaellige_ereignisfolgen_im_rahmen() {
        var current = state(energy = 40.0, satiety = 30.0, mood = 50.0)
        val typen = RewardType.entries

        repeat(300) { index ->
            val ereignis = RewardEvent.of("e$index", current.lastComputedAt, typen[index % typen.size])
            current = PetSimulation.compute(
                previous = current,
                events = listOf(ereignis),
                now = current.lastComputedAt.plus(Duration.ofMinutes(37)),
                load = (index % 11).toDouble(),
            )
            listOf(current.values.energy, current.values.satiety, current.values.mood).forEach { wert ->
                assertTrue("Wert außerhalb 0..100: $wert", wert in 0.0..100.0)
            }
        }
    }

    // ------------------------------------------------------------------------ Ereignisse

    @Test
    fun ereignisse_werden_auf_den_verfallenen_stand_gerechnet() {
        val ergebnis = PetSimulation.compute(
            previous = state(),
            events = listOf(RewardEvent.of("e1", start.plus(Duration.ofHours(6)), RewardType.TASK_DONE)),
            now = start.plus(Duration.ofHours(6)),
            load = 0.0,
        )

        // Sättigung: 100 → 62,5 durch Verfall, dann +12 durch die erledigte Aufgabe.
        assertEquals(74.5, ergebnis.values.satiety, 0.001)
        assertEquals(5, ergebnis.xp)
    }

    @Test
    fun fuettern_spielen_streicheln_merken_sich_ihren_zeitpunkt() {
        val ergebnis = PetSimulation.compute(
            previous = state(),
            events = listOf(
                RewardEvent.of("e1", start.plusSeconds(60), RewardType.FEED),
                RewardEvent.of("e2", start.plusSeconds(120), RewardType.PLAY),
                RewardEvent.of("e3", start.plusSeconds(180), RewardType.PAT),
            ),
            now = start.plusSeconds(200),
            load = 0.0,
        )

        assertEquals(start.plusSeconds(60), ergebnis.lastFedAt)
        assertEquals(start.plusSeconds(120), ergebnis.lastPlayedAt)
        assertEquals(start.plusSeconds(180), ergebnis.lastPattedAt)
    }

    @Test
    fun xp_wird_nie_negativ() {
        val ergebnis = PetSimulation.compute(
            previous = state(xp = 3),
            events = listOf(RewardEvent("e1", start, RewardType.PLAY, 0, 0, 0, dXp = -99)),
            now = start,
            load = 0.0,
        )
        assertEquals(0, ergebnis.xp)
    }

    // ---------------------------------------------------------- Die geforderte Fluchttür

    @Test
    fun fuenf_ueberfaellige_aufgaben_aufraeumen_hebt_den_zustand_um_mindestens_eine_stufe() {
        // Der Abnahmepunkt aus dem Projektplan: +30 auf alle Werte, von Elend auf
        // Angeschlagen in zwei Minuten. Das ist die Fluchttür, und sie muss existieren.
        val vorher = state(energy = 8.0, satiety = 8.0, mood = 8.0)
        assertEquals(HealthStage.MISERABLE, vorher.values.stage)

        val ergebnis = PetSimulation.compute(
            previous = vorher,
            events = List(5) { index ->
                RewardEvent.of("c$index", start.plusSeconds(index.toLong()), RewardType.TASK_CLEANED)
            },
            now = start.plusSeconds(10),
            load = 0.0,
        )

        assertTrue(
            "Zustand blieb ${ergebnis.stage}",
            ergebnis.stage.rank > vorher.values.stage.rank,
        )
        assertEquals(HealthStage.SICK, ergebnis.stage)
        assertEquals(38.0, ergebnis.values.energy, 0.001)
    }
}
