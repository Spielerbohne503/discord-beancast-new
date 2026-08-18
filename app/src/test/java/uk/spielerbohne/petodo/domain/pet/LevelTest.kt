package uk.spielerbohne.petodo.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Levelkurve. Level schalten Skins frei — sonst nichts. Deshalb darf die Kurve
 * ruhig steil sein: Wer nie Level 10 erreicht, verliert dadurch keine Funktion.
 */
class LevelTest {

    @Test
    fun die_erste_stufe_kostet_hundert_und_jede_weitere_das_anderthalbfache() {
        assertEquals(100, Level.costOf(1))
        assertEquals(150, Level.costOf(2))
        assertEquals(225, Level.costOf(3))
        assertEquals(338, Level.costOf(4)) // 337,5 kaufmännisch gerundet
    }

    @Test
    fun die_gesamtkosten_sind_die_summe_der_einzelnen_stufen() {
        assertEquals(0, Level.totalXpFor(1))
        assertEquals(100, Level.totalXpFor(2))
        assertEquals(250, Level.totalXpFor(3))
        assertEquals(475, Level.totalXpFor(4))
        assertEquals(813, Level.totalXpFor(5))
    }

    @Test
    fun das_level_ergibt_sich_aus_den_gesammelten_xp() {
        assertEquals(1, Level.forXp(0))
        assertEquals(1, Level.forXp(99))
        assertEquals(2, Level.forXp(100))
        assertEquals(2, Level.forXp(249))
        assertEquals(3, Level.forXp(250))
        assertEquals(4, Level.forXp(475))
    }

    @Test
    fun negative_xp_bleiben_level_eins() {
        // Kann nicht vorkommen, weil das Log nur addiert — aber ein Lesefehler in der
        // Datenbank darf keinen Absturz auslösen.
        assertEquals(1, Level.forXp(-5))
        assertEquals(0.0, Level.progressWithin(-5), 0.001)
    }

    @Test
    fun der_fortschritt_laeuft_von_null_bis_eins_und_springt_beim_levelaufstieg_zurueck() {
        assertEquals(0.0, Level.progressWithin(0), 0.001)
        assertEquals(0.5, Level.progressWithin(50), 0.001)
        assertEquals(0.99, Level.progressWithin(99), 0.001)
        assertEquals(0.0, Level.progressWithin(100), 0.001) // Stufe 2 beginnt bei null
        assertEquals(0.5, Level.progressWithin(175), 0.001) // 75 von 150
    }

    @Test
    fun die_fehlenden_xp_passen_zum_fortschritt() {
        assertEquals(100, Level.xpToNextLevel(0))
        assertEquals(1, Level.xpToNextLevel(99))
        assertEquals(150, Level.xpToNextLevel(100))
        assertEquals(75, Level.xpToNextLevel(175))
    }

    @Test
    fun die_kurve_waechst_streng_monoton() {
        var previous = 0
        for (level in 1..30) {
            val cost = Level.costOf(level)
            assertTrue("Stufe $level kostet nicht mehr als die vorige", cost > previous)
            previous = cost
        }
    }

    @Test
    fun absurde_xp_zahlen_laufen_nicht_ins_endlose_sondern_an_die_obergrenze() {
        // Ohne Deckel würde die Schleife bei Int.MAX_VALUE ewig weiterzählen.
        val hoechstes = Level.forXp(Int.MAX_VALUE)
        assertEquals(hoechstes, Level.forXp(Int.MAX_VALUE - 1))
        assertTrue("Das Level muss gedeckelt sein", hoechstes in 2..1_000)
    }
}
