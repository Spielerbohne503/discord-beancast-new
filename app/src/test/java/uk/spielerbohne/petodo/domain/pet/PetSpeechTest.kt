package uk.spielerbohne.petodo.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.spielerbohne.petodo.domain.Balance
import java.io.File

/**
 * Sprechblasen (Projektplan, Abschnitt 8.3).
 *
 * `domain/` entscheidet nur, **welche** Kategorie dran ist — die Texte liegen in
 * `strings.xml`. Der letzte Test hier geht den erreichbaren Zustandsraum ab und prüft,
 * dass zu jeder Lage wirklich ein Text hinterlegt ist.
 */
class PetSpeechTest {

    private fun werte(energie: Double, saettigung: Double, laune: Double) =
        PetValues.of(energie, saettigung, laune)

    @Test
    fun beduerfnisse_gehen_stimmungen_vor() {
        // Ein hungriges Pet erzählt nicht, wie gut gelaunt es ist.
        assertEquals(SpeechCategory.HUNGRY, PetSpeech.moodCategory(werte(100.0, 10.0, 100.0)))
        assertEquals(SpeechCategory.TIRED, PetSpeech.moodCategory(werte(10.0, 100.0, 100.0)))
    }

    @Test
    fun hunger_geht_muedigkeit_vor_und_muedigkeit_der_krankheit() {
        // Beides unter der Schwelle: Hunger zuerst.
        assertEquals(SpeechCategory.HUNGRY, PetSpeech.moodCategory(werte(10.0, 10.0, 10.0)))
        // Müde und krank: erst die Müdigkeit, sie ist die konkretere Aussage.
        assertEquals(SpeechCategory.TIRED, PetSpeech.moodCategory(werte(10.0, 50.0, 10.0)))
    }

    @Test
    fun die_schwellen_liegen_bei_dreissig_und_fuenfundachtzig() {
        val hungerschwelle = Balance.BUBBLE_HUNGRY_BELOW
        assertEquals(SpeechCategory.HUNGRY, PetSpeech.moodCategory(werte(100.0, hungerschwelle - 0.1, 50.0)))
        // Genau auf der Schwelle ist noch kein Hunger.
        assertTrue(PetSpeech.moodCategory(werte(100.0, hungerschwelle, 50.0)) != SpeechCategory.HUNGRY)

        val launeschwelle = Balance.BUBBLE_HAPPY_ABOVE
        assertEquals(SpeechCategory.HAPPY, PetSpeech.moodCategory(werte(100.0, 100.0, launeschwelle + 0.1)))
        assertEquals(SpeechCategory.BORED, PetSpeech.moodCategory(werte(100.0, 100.0, launeschwelle)))
    }

    @Test
    fun ein_krankes_pet_spricht_seine_krankheit_an() {
        // Werte satt und wach, aber im Schnitt krank: 100 / 100 / 0 ergibt 66,7 → geschwächt,
        // also noch nicht krank. Erst tiefer greift die Krankheitsmeldung.
        val geschwaecht = werte(60.0, 60.0, 60.0)
        assertEquals(HealthStage.WEAKENED, geschwaecht.stage)
        assertEquals(SpeechCategory.BORED, PetSpeech.moodCategory(geschwaecht))

        val krank = werte(35.0, 35.0, 35.0)
        assertEquals(HealthStage.SICK, krank.stage)
        assertEquals(SpeechCategory.SICK, PetSpeech.moodCategory(krank))
    }

    @Test
    fun eine_leere_kategorie_heisst_das_pet_sagt_nichts() {
        assertNull(PetSpeech.pick(emptyList(), previous = null, roll = 0.0, chooser = { 0 }))
    }

    @Test
    fun bei_einem_einzigen_text_gibt_es_nichts_zu_wechseln() {
        assertEquals("Hallo", PetSpeech.pick(listOf("Hallo"), previous = "Hallo", roll = 0.9, chooser = { 0 }))
    }

    @Test
    fun derselbe_gedanke_wiederholt_sich_nur_mit_der_erlaubten_wahrscheinlichkeit() {
        val texte = listOf("a", "b", "c")

        // Würfel unter der Schwelle: Wiederholung erlaubt.
        assertEquals("a", PetSpeech.pick(texte, previous = "a", roll = 0.0, chooser = { 0 }))
        // Würfel darüber: es muss etwas anderes kommen.
        assertEquals("b", PetSpeech.pick(texte, previous = "a", roll = 0.9, chooser = { 0 }))
    }

    @Test
    fun ein_kaputter_index_faellt_nicht_aus_der_liste() {
        val texte = listOf("a", "b")
        assertEquals("b", PetSpeech.pick(texte, previous = null, roll = 0.5, chooser = { 99 }))
        assertEquals("a", PetSpeech.pick(texte, previous = null, roll = 0.5, chooser = { -3 }))
    }

    @Test
    fun ueber_hundert_wuerfe_bleibt_die_wiederholung_die_ausnahme() {
        val texte = listOf("a", "b", "c")
        val wiederholt = (0 until 100).count { wurf ->
            PetSpeech.pick(texte, previous = "a", roll = wurf / 100.0, chooser = { 0 }) == "a"
        }
        assertEquals((Balance.BUBBLE_REPEAT_CHANCE * 100).toInt(), wiederholt)
    }

    /**
     * Der Abdeckungstest: Jede erreichbare Wertelage muss zu einem Text führen.
     *
     * Der Test liest `strings.xml` als Datei — ein JVM-Test hat kein `Resources`. Genau
     * das ist der Punkt: Er prüft die ausgelieferte Ressourcendatei, nicht eine Kopie.
     */
    @Test
    fun zu_jeder_erreichbaren_lage_gibt_es_einen_sprechblasentext() {
        val kategorien = speechArrays()

        var geprueft = 0
        for (energie in 0..100 step 5) {
            for (saettigung in 0..100 step 5) {
                for (laune in 0..100 step 5) {
                    val lage = werte(energie.toDouble(), saettigung.toDouble(), laune.toDouble())
                    val kategorie = PetSpeech.moodCategory(lage)
                    val texte = kategorien[kategorie.key]
                    assertNotNull(
                        "Keine Texte für ${kategorie.key} bei $energie/$saettigung/$laune",
                        texte,
                    )
                    assertTrue(
                        "Leere Kategorie ${kategorie.key} bei $energie/$saettigung/$laune",
                        texte!! > 0,
                    )
                    geprueft++
                }
            }
        }

        assertEquals(21 * 21 * 21, geprueft)
    }

    @Test
    fun auch_jede_sofortige_reaktion_hat_texte() {
        val kategorien = speechArrays()
        SpeechCategory.entries.forEach { kategorie ->
            val texte = kategorien[kategorie.key]
            assertNotNull("Kategorie ${kategorie.key} fehlt in strings.xml", texte)
            assertTrue("Kategorie ${kategorie.key} ist leer", texte!! >= 5)
        }
    }

    @Test
    fun eine_kategorie_aus_lauter_gleichen_texten_stuerzt_nicht_ab() {
        // Kommt in den mitgelieferten Texten nicht vor, wäre aber ein Absturz beim Pet:
        // Ohne Alternative würfelte der Code aus einer leeren Liste.
        val gleich = listOf("Hallo", "Hallo", "Hallo")

        assertEquals("Hallo", PetSpeech.pick(gleich, previous = "Hallo", roll = 0.9, chooser = { 0 }))
    }

    /** Kategorie-Schlüssel → Anzahl der Texte, gelesen aus `res/values/strings.xml`. */
    private fun speechArrays(): Map<String, Int> {
        val datei = listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("strings.xml nicht gefunden — Arbeitsverzeichnis ${File(".").absolutePath}")

        val text = datei.readText()
        val block = Regex("""<string-array name="pet_speech_([a-z_]+)">(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL)
        return block.findAll(text).associate { treffer ->
            val key = treffer.groupValues[1]
            val anzahl = Regex("<item>").findAll(treffer.groupValues[2]).count()
            key to anzahl
        }
    }
}