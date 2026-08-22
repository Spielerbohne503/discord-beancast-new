package uk.spielerbohne.petodo.huelle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Was sich ohne Telefon prüfen lässt, wird ohne Telefon geprüft.
 *
 * In dieser Umgebung läuft kein Emulator — für KVM fehlt die Berechtigung. Deshalb steht
 * in der Hülle so wenig Logik wie möglich, und das Wenige ist rein: Kodierung und
 * Weckerkennungen. Alles andere ist ein Aufruf ans Betriebssystem.
 *
 * Der Rest des Vertrags wird in `tools/bruecketest.mjs` im Browser geprüft.
 */
class KodierungTest {

    @Test
    fun `eine handlung ueberlebt schreiben und lesen`() {
        val aktion = Aktion("a1", Aktion.Art.ERLEDIGT, "3f1c-uuid", 1_787_000_000_000)
        assertEquals(aktion, Kodierung.lesen("a1", Kodierung.schreiben(aktion)))
    }

    @Test
    fun `jede art ueberlebt den weg`() {
        for (art in Aktion.Art.entries) {
            val aktion = Aktion("x", art, "uuid", 42L)
            assertEquals(art, Kodierung.lesen("x", Kodierung.schreiben(aktion))?.art)
        }
    }

    @Test
    fun `eine kaputte zeile fliegt raus statt zu werfen`() {
        assertNull(Kodierung.lesen("x", ""))
        assertNull(Kodierung.lesen("x", "erledigt"))
        assertNull(Kodierung.lesen("x", "unbekannt${Kodierung.TRENNER}uuid${Kodierung.TRENNER}1"))
        assertNull(Kodierung.lesen("x", "erledigt${Kodierung.TRENNER}${Kodierung.TRENNER}1"))
        assertNull(Kodierung.lesen("x", "erledigt${Kodierung.TRENNER}uuid${Kodierung.TRENNER}keineZahl"))
    }

    @Test
    fun `ein weckruf ueberlebt schreiben und lesen`() {
        val ruf = Weckruf("uuid", 1_787_000_000_000, "Zahnarzt", "ist fällig", ton = false)
        assertEquals(ruf, Kodierung.weckrufLesen(Kodierung.schreiben(ruf)))
    }

    @Test
    fun `ein titel mit sonderzeichen bleibt heil`() {
        // Ein Titel darf alles enthalten: Umlaute, Zeilenumbrüche, Klammern, Markdown.
        val titel = "Müll rausbringen\n[Reel](https://example.org/a_(b)) | ; \t"
        val ruf = Weckruf("uuid", 1L, titel, "Willst du das noch?", ton = true)
        assertEquals(titel, Kodierung.weckrufLesen(Kodierung.schreiben(ruf))?.titel)
    }

    @Test
    fun `sogar ein trennzeichen im titel macht die zeile nicht kaputt`() {
        // Über die Tastatur ist U+001F nicht einzugeben — aber eine eingelesene Sicherung
        // aus einer fremden Quelle könnte es enthalten. Dann bleibt es im Titel stehen,
        // statt die Zeile unbrauchbar zu machen: Der Titel steht hinten.
        val ruf = Weckruf("uuid", 7L, "vorn${Kodierung.TRENNER}hinten", "ist fällig", ton = false)
        val gelesen = Kodierung.weckrufLesen(Kodierung.schreiben(ruf))

        assertEquals("uuid", gelesen?.taskId)
        assertEquals(7L, gelesen?.at)
        assertEquals("vorn${Kodierung.TRENNER}hinten", gelesen?.titel)
    }

    @Test
    fun `eine kaputte weckzeile fliegt raus`() {
        assertNull(Kodierung.weckrufLesen(""))
        assertNull(Kodierung.weckrufLesen("nur${Kodierung.TRENNER}zwei"))
        assertNull(
            Kodierung.weckrufLesen(
                listOf("", "1", "0", "stufe", "titel").joinToString(Kodierung.TRENNER),
            ),
        )
        assertNull(
            Kodierung.weckrufLesen(
                listOf("uuid", "keineZahl", "0", "stufe", "titel").joinToString(Kodierung.TRENNER),
            ),
        )
    }

    @Test
    fun `die weckerkennung ist stabil und nie negativ`() {
        // Stabil, weil ein zweiter Zeitplan sonst einen zweiten Wecker anlegte statt den
        // ersten zu ersetzen — und es doppelt klingelte.
        val uuid = "0f8d2c11-5a6b-4c7d-8e9f-0a1b2c3d4e5f"
        assertEquals(Kodierung.weckerKennung(uuid), Kodierung.weckerKennung(uuid))
        assertNotEquals(Kodierung.weckerKennung(uuid), Kodierung.weckerKennung(uuid + "x"))

        for (probe in listOf("", "a", uuid, "ü".repeat(64), "\uffff\uffff")) {
            assertTrue(Kodierung.weckerKennung(probe) >= 0, "negativ für: $probe")
        }
    }
}
