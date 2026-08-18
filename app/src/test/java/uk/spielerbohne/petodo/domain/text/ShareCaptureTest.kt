package uk.spielerbohne.petodo.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Teilen an PeTodo.
 *
 * Die Fälle stammen aus dem, was Android tatsächlich schickt: Browser senden Titel plus
 * Adresse, Instagram nur die Adresse, Notiz-Apps mehrere Absätze ohne Betreff.
 */
class ShareCaptureTest {

    @Test
    fun betreff_und_adresse_ergeben_einen_beschrifteten_verweis() {
        val geteilt = ShareCapture.capture(
            subject = "Kotlin Coroutines erklärt",
            text = "https://example.org/video/42",
        )

        assertEquals(
            SharedTask("[Kotlin Coroutines erklärt](https://example.org/video/42)"),
            geteilt,
        )
        // Und in der Liste steht dann der Titel, nicht die Adresse.
        assertEquals("Kotlin Coroutines erklärt", MarkdownLinks.plainText(geteilt!!.title))
    }

    @Test
    fun eine_adresse_ohne_betreff_bleibt_die_adresse() {
        val geteilt = ShareCapture.capture(subject = null, text = "https://www.instagram.com/reel/DbBO")

        assertEquals(SharedTask("https://www.instagram.com/reel/DbBO"), geteilt)
    }

    @Test
    fun ein_betreff_der_die_adresse_wiederholt_wird_nicht_zur_beschriftung() {
        // Manche Apps setzen als Betreff dieselbe Adresse — daraus "[url](url)" zu
        // bauen sähe in der Liste aus wie ein Fehler.
        val adresse = "https://example.org/x"
        assertEquals(SharedTask(adresse), ShareCapture.capture(subject = adresse, text = adresse))
    }

    @Test
    fun freitext_mit_betreff_wird_titel_und_notiz() {
        val geteilt = ShareCapture.capture(
            subject = "Einkaufsliste",
            text = "Milch\nBrot\nKaffee",
        )

        assertEquals(SharedTask("Einkaufsliste", "Milch\nBrot\nKaffee"), geteilt)
    }

    @Test
    fun freitext_ohne_betreff_nimmt_die_erste_zeile_als_titel() {
        val geteilt = ShareCapture.capture(
            subject = null,
            text = "Termin beim Zahnarzt\nDienstag, Praxis Mitte\nVersichertenkarte mitnehmen",
        )

        assertEquals(
            SharedTask("Termin beim Zahnarzt", "Dienstag, Praxis Mitte\nVersichertenkarte mitnehmen"),
            geteilt,
        )
    }

    @Test
    fun eine_einzige_zeile_bekommt_keine_leere_notiz() {
        assertEquals(SharedTask("Rasen mähen"), ShareCapture.capture(null, "Rasen mähen"))
    }

    @Test
    fun ein_mehrzeiliger_betreff_wird_zu_einer_zeile() {
        // Titel sind einzeilig; ein Betreff mit Umbruch würde die Liste zerreißen.
        assertEquals(
            SharedTask("Ein langer Titel mit Umbruch"),
            ShareCapture.capture(subject = "Ein langer Titel\n  mit Umbruch", text = null),
        )
    }

    @Test
    fun text_mit_adresse_und_drumherum_bleibt_wie_geteilt() {
        // Nur eine *alleinstehende* Adresse wird zur Beschriftung umgebaut. Sonst würde
        // aus einem Satz mit Link plötzlich ein Link mit fremdem Namen.
        val geteilt = ShareCapture.capture(null, "Schau mal https://example.org/x an")

        assertEquals(SharedTask("Schau mal https://example.org/x an"), geteilt)
    }

    @Test
    fun leeres_teilen_ergibt_keine_aufgabe() {
        assertNull(ShareCapture.capture(null, null))
        assertNull(ShareCapture.capture("   ", "  \n "))
    }

    @Test
    fun leerzeichen_am_rand_verschwinden() {
        assertEquals(SharedTask("Rasen mähen"), ShareCapture.capture(null, "  Rasen mähen \n"))
    }
}
