package uk.spielerbohne.petodo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagTest {

    @Test
    fun fuehrende_rauten_und_leerzeichen_fallen_weg() {
        assertEquals("Arbeit", Tag.normalizeName("  #Arbeit  "))
        assertEquals("Arbeit", Tag.normalizeName("#Arbeit"))
        assertEquals("Arbeit", Tag.normalizeName("Arbeit"))
    }

    @Test
    fun leere_eingaben_ergeben_kein_etikett() {
        assertNull(Tag.normalizeName(""))
        assertNull(Tag.normalizeName("   "))
        assertNull(Tag.normalizeName("#"))
        assertNull(Tag.normalizeName(" # "))
    }

    @Test
    fun innere_leerzeichen_bleiben_erhalten() {
        assertEquals("Wichtige Post", Tag.normalizeName("#Wichtige Post"))
    }
}
