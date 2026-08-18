package uk.spielerbohne.petodo.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verweise in Titeln und Notizen.
 *
 * Der Anlass ist ein echter Fall: Ein aus Instagram geteilter Link stand als Titel in
 * der Liste und war drei Zeilen lang, ohne anklickbar zu sein.
 */
class MarkdownLinksTest {

    @Test
    fun text_ohne_verweis_bleibt_ein_stueck() {
        assertEquals(listOf(TextSegment.Plain("Einkaufen gehen")), MarkdownLinks.parse("Einkaufen gehen"))
        assertFalse(MarkdownLinks.hasLink("Einkaufen gehen"))
    }

    @Test
    fun markdown_schreibweise_wird_zu_beschriftung_und_ziel() {
        val stuecke = MarkdownLinks.parse("Schau dir [das Reel](https://example.org/reel/42) an")

        assertEquals(
            listOf(
                TextSegment.Plain("Schau dir "),
                TextSegment.Link("das Reel", "https://example.org/reel/42"),
                TextSegment.Plain(" an"),
            ),
            stuecke,
        )
    }

    @Test
    fun eine_nackte_adresse_wird_erkannt_und_gekuerzt_beschriftet() {
        val stuecke = MarkdownLinks.parse("https://www.instagram.com/reel/DbBO_fiCJm8/?igsh=MWtqano2am95")

        assertEquals(1, stuecke.size)
        val link = stuecke.single() as TextSegment.Link
        assertEquals("https://www.instagram.com/reel/DbBO_fiCJm8/?igsh=MWtqano2am95", link.url)
        assertEquals("instagram.com/reel/…", link.label)
    }

    @Test
    fun der_satzpunkt_hinter_einer_adresse_gehoert_zum_satz() {
        val stuecke = MarkdownLinks.parse("Nachlesen unter https://example.org.")

        assertEquals("https://example.org", (stuecke[1] as TextSegment.Link).url)
        assertEquals(TextSegment.Plain("."), stuecke[2])
    }

    @Test
    fun eine_adresse_in_klammern_verliert_die_schliessende_klammer_nicht_falsch() {
        // Klammern kommen in Wikipedia-Adressen vor — die schließende darf nur weg,
        // wenn die Adresse selbst keine öffnende enthält.
        val mitKlammer = MarkdownLinks.parse("https://de.wikipedia.org/wiki/Kotlin_(Programmiersprache)")
        assertEquals(
            "https://de.wikipedia.org/wiki/Kotlin_(Programmiersprache)",
            (mitKlammer.single() as TextSegment.Link).url,
        )

        val inSatzklammer = MarkdownLinks.parse("(siehe https://example.org)")
        assertEquals("https://example.org", (inSatzklammer[1] as TextSegment.Link).url)
    }

    @Test
    fun die_adresse_in_einer_markdown_klammer_wird_nicht_zweimal_gefunden() {
        val stuecke = MarkdownLinks.parse("[Doku](https://example.org/doku)")

        assertEquals(1, stuecke.size)
        assertEquals(TextSegment.Link("Doku", "https://example.org/doku"), stuecke.single())
    }

    @Test
    fun eine_leere_beschriftung_faellt_auf_die_kurzform_zurueck() {
        val link = MarkdownLinks.parse("[](https://example.org/pfad)").single() as TextSegment.Link
        assertEquals("example.org/pfad", link.label)
    }

    @Test
    fun mehrere_verweise_bleiben_in_ihrer_reihenfolge() {
        val links = MarkdownLinks.links("[A](https://a.example) und dann https://b.example/x")

        assertEquals(listOf("https://a.example", "https://b.example/x"), links.map { it.url })
        assertEquals(listOf("A", "b.example/x"), links.map { it.label })
    }

    @Test
    fun die_kurzform_wirft_schema_und_www_weg() {
        assertEquals("example.org", MarkdownLinks.shorten("https://example.org"))
        assertEquals("example.org", MarkdownLinks.shorten("https://www.example.org/"))
        assertEquals("example.org/pfad", MarkdownLinks.shorten("http://example.org/pfad"))
        assertEquals("example.org/a/…", MarkdownLinks.shorten("https://example.org/a/b/c"))
        assertEquals("example.org/pfad…", MarkdownLinks.shorten("https://example.org/pfad?x=1"))
    }

    @Test
    fun eine_sehr_lange_kennung_im_pfad_wird_abgeschnitten() {
        val kurz = MarkdownLinks.shorten("https://example.org/DbBO_fiCJm8QweRtZuIoPasDfGhJkL")
        assertTrue("zu lang: $kurz", kurz.length <= 32)
        assertTrue(kurz.startsWith("example.org/"))
        assertTrue(kurz.endsWith("…"))
    }

    @Test
    fun der_listentext_zeigt_beschriftungen_statt_adressen() {
        assertEquals(
            "Schau dir das Reel an",
            MarkdownLinks.plainText("Schau dir [das Reel](https://example.org/reel/42) an"),
        )
        assertEquals(
            "instagram.com/reel/…",
            MarkdownLinks.plainText("https://www.instagram.com/reel/DbBO_fiCJm8/?igsh=MWtqano2am95"),
        )
        assertEquals("Einkaufen", MarkdownLinks.plainText("Einkaufen"))
    }

    @Test
    fun leerer_text_stuerzt_nicht_ab() {
        assertEquals(listOf(TextSegment.Plain("")), MarkdownLinks.parse(""))
        assertEquals("", MarkdownLinks.plainText(""))
        assertFalse(MarkdownLinks.hasLink(""))
    }
}
