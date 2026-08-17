package uk.spielerbohne.petodo.domain.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderTest {

    /** Vier Schlüssel in aufsteigender Reihenfolge, wie sie die App vergeben würde. */
    private val keys: List<String> = buildList {
        var last: String? = null
        repeat(4) {
            last = FractionalIndex.after(last)
            add(last!!)
        }
    }

    @Test
    fun nach_unten_verschieben_ergibt_einen_schluessel_zwischen_den_neuen_nachbarn() {
        val neu = Reorder.keyForMove(keys, from = 0, to = 2)!!

        // Ohne das bewegte Element bleibt [k1, k2, k3]; Einfügen an Position 2 ergibt
        // [k1, k2, bewegt, k3] — die Nachbarn sind also k2 und k3.
        assertTrue("${keys[2]} < $neu", keys[2] < neu)
        assertTrue("$neu < ${keys[3]}", neu < keys[3])
    }

    @Test
    fun nach_oben_verschieben_ebenso() {
        val neu = Reorder.keyForMove(keys, from = 3, to = 1)!!

        assertTrue(keys[0] < neu)
        assertTrue(neu < keys[1])
    }

    @Test
    fun an_den_anfang_ziehen_ergibt_einen_schluessel_vor_allen() {
        val neu = Reorder.keyForMove(keys, from = 2, to = 0)!!
        assertTrue("$neu < ${keys[0]}", neu < keys[0])
    }

    @Test
    fun ans_ende_ziehen_ergibt_einen_schluessel_hinter_allen() {
        val neu = Reorder.keyForMove(keys, from = 0, to = 3)!!
        assertTrue("${keys[3]} < $neu", keys[3] < neu)
    }

    @Test
    fun keine_bewegung_schreibt_nichts() {
        assertNull(Reorder.keyForMove(keys, from = 1, to = 1))
    }

    @Test
    fun unsinnige_indizes_schreiben_nichts() {
        assertNull(Reorder.keyForMove(keys, from = -1, to = 2))
        assertNull(Reorder.keyForMove(keys, from = 0, to = 99))
        assertNull(Reorder.keyForMove(emptyList(), from = 0, to = 0))
    }

    @Test
    fun die_reihenfolge_bleibt_ueber_viele_verschiebungen_gueltig() {
        // Nach jedem Zug muss die Liste immer noch aufsteigend sortiert sein.
        var aktuell = keys.toMutableList()
        val zuege = listOf(0 to 3, 3 to 0, 1 to 2, 2 to 1, 0 to 2, 3 to 1)

        zuege.forEach { (from, to) ->
            val neu = Reorder.keyForMove(aktuell, from, to)!!
            aktuell = Reorder.move(aktuell, from, to).toMutableList()
            aktuell[to] = neu
            assertEquals("Reihenfolge nach $from → $to", aktuell.sorted(), aktuell)
            assertEquals("Schlüssel doppelt", aktuell.distinct().size, aktuell.size)
        }
    }

    @Test
    fun die_vorschau_verschiebt_das_richtige_element() {
        assertEquals(listOf("b", "c", "a"), Reorder.move(listOf("a", "b", "c"), from = 0, to = 2))
        assertEquals(listOf("c", "a", "b"), Reorder.move(listOf("a", "b", "c"), from = 2, to = 0))
        assertEquals(listOf("a", "b", "c"), Reorder.move(listOf("a", "b", "c"), from = 1, to = 1))
    }

    @Test
    fun die_vorschau_laesst_unsinnige_zuege_unveraendert() {
        val original = listOf("a", "b")
        assertEquals(original, Reorder.move(original, from = 0, to = 5))
        assertEquals(original, Reorder.move(original, from = -1, to = 1))
    }
}
