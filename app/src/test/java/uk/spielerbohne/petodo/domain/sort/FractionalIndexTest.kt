package uk.spielerbohne.petodo.domain.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FractionalIndexTest {

    @Test
    fun schluessel_zwischen_zwei_nachbarn_liegt_echt_dazwischen() {
        val a = FractionalIndex.initial()
        val b = FractionalIndex.after(a)
        val mitte = FractionalIndex.between(a, b)

        assertTrue("$a < $mitte", a < mitte)
        assertTrue("$mitte < $b", mitte < b)
    }

    @Test
    fun anhaengen_am_ende_ergibt_immer_groessere_schluessel() {
        var last: String? = null
        val keys = (1..500).map {
            FractionalIndex.after(last).also { key -> last = key }
        }
        assertEquals(keys.sorted(), keys)
    }

    @Test
    fun einfuegen_am_anfang_ergibt_immer_kleinere_schluessel() {
        var first: String? = null
        val keys = (1..500).map {
            FractionalIndex.before(first).also { key -> first = key }
        }
        assertEquals(keys.sortedDescending(), keys)
    }

    @Test
    fun wiederholtes_einfuegen_zwischen_denselben_nachbarn_bleibt_sortiert() {
        val links = FractionalIndex.initial()
        val rechts = FractionalIndex.after(links)

        var oben = rechts
        val eingefuegt = mutableListOf<String>()
        repeat(200) {
            val neu = FractionalIndex.between(links, oben)
            assertTrue("$links < $neu", links < neu)
            assertTrue("$neu < $oben", neu < oben)
            eingefuegt += neu
            oben = neu
        }
        assertEquals(eingefuegt.sortedDescending(), eingefuegt)
    }

    @Test
    fun schluessel_endet_nie_auf_dem_kleinsten_zeichen() {
        // Sonst ließe sich davor irgendwann nichts mehr einfügen.
        var last: String? = null
        repeat(200) {
            val key = FractionalIndex.after(last)
            assertTrue("$key endet auf '0'", !key.endsWith(FractionalIndex.ALPHABET.first()))
            last = key
        }

        var first: String? = null
        repeat(200) {
            val key = FractionalIndex.before(first)
            assertTrue("$key endet auf '0'", !key.endsWith(FractionalIndex.ALPHABET.first()))
            first = key
        }
    }

    @Test
    fun benachbarte_zeichen_erzwingen_eine_stelle_mehr() {
        // "a" und "b" liegen im Alphabet direkt nebeneinander: der neue Schlüssel muss
        // länger werden, statt zu scheitern.
        val mitte = FractionalIndex.between("a", "b")
        assertTrue("$mitte länger als 1", mitte.length > 1)
        assertTrue("a" < mitte)
        assertTrue(mitte < "b")
    }

    @Test
    fun ohne_nachbarn_entsteht_ein_schluessel_in_der_mitte_des_raums() {
        val key = FractionalIndex.initial()
        assertTrue("" < key)
        assertTrue(key < "z")
    }

    @Test(expected = IllegalArgumentException::class)
    fun verdrehte_grenzen_werden_abgelehnt() {
        FractionalIndex.between("b", "a")
    }

    @Test(expected = IllegalArgumentException::class)
    fun ungueltige_zeichen_werden_abgelehnt() {
        FractionalIndex.between("A!", null)
    }

    @Test
    fun sortierung_bleibt_ueber_gemischte_operationen_stabil() {
        val keys = mutableListOf(FractionalIndex.initial())
        repeat(100) { i ->
            val neu = when (i % 3) {
                0 -> FractionalIndex.after(keys.last())
                1 -> FractionalIndex.before(keys.first())
                else -> FractionalIndex.between(keys[keys.size / 2 - 1], keys[keys.size / 2])
            }
            keys += neu
            keys.sort()
        }
        assertEquals(keys.distinct().size, keys.size)
        assertEquals(keys.sorted(), keys)
    }
}
