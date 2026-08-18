package uk.spielerbohne.petodo.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthStageTest {

    @Test
    fun alle_vier_stufen_liegen_an_den_richtigen_grenzen() {
        assertEquals(HealthStage.HEALTHY, HealthStage.of(100.0))
        assertEquals(HealthStage.HEALTHY, HealthStage.of(70.1))
        assertEquals(HealthStage.WEAKENED, HealthStage.of(70.0))
        assertEquals(HealthStage.WEAKENED, HealthStage.of(40.0))
        assertEquals(HealthStage.SICK, HealthStage.of(39.9))
        assertEquals(HealthStage.SICK, HealthStage.of(15.0))
        assertEquals(HealthStage.MISERABLE, HealthStage.of(14.9))
        assertEquals(HealthStage.MISERABLE, HealthStage.of(0.0))
    }

    @Test
    fun die_stufe_haengt_am_durchschnitt_der_drei_werte() {
        // Nicht direkt an der Last: Aufgaben abbauen stoppt die Blutung, füttern heilt.
        val werte = PetValues.of(energy = 90.0, satiety = 10.0, mood = 20.0)
        assertEquals(40.0, werte.average, 0.001)
        assertEquals(HealthStage.WEAKENED, werte.stage)
    }

    @Test
    fun es_gibt_keine_stufe_unterhalb_von_elend() {
        // Harte Regel: kein Tod, kein Punkt ohne Wiederkehr.
        assertEquals(HealthStage.MISERABLE, HealthStage.of(0.0))
        assertEquals(4, HealthStage.entries.size)
        assertEquals(HealthStage.MISERABLE, HealthStage.entries.last())
    }

    @Test
    fun besser_heisst_hoeherer_rang() {
        assertTrue(HealthStage.HEALTHY.rank > HealthStage.WEAKENED.rank)
        assertTrue(HealthStage.WEAKENED.rank > HealthStage.SICK.rank)
        assertTrue(HealthStage.SICK.rank > HealthStage.MISERABLE.rank)
    }

    @Test
    fun werte_lassen_sich_nur_im_gueltigen_bereich_bauen() {
        assertThrows(IllegalArgumentException::class.java) { PetValues(120.0, 50.0, 50.0) }
        assertThrows(IllegalArgumentException::class.java) { PetValues(50.0, -1.0, 50.0) }
        // of() kappt statt zu werfen — das ist der Weg für berechnete Werte.
        assertEquals(100.0, PetValues.of(140.0, 50.0, 50.0).energy, 0.001)
        assertEquals(0.0, PetValues.of(50.0, -20.0, 50.0).satiety, 0.001)
    }
}
