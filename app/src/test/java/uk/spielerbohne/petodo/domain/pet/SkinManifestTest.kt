package uk.spielerbohne.petodo.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Rückfallregel aus Abschnitt 8.2: `idle_sick` fehlt → `idle`.
 *
 * Ohne diese Regel müsste jeder Skin vier Fassungen jeder Animation mitbringen, bevor er
 * überhaupt startet. Mit ihr reicht ein einziges `idle.png`.
 */
class SkinManifestTest {

    private fun manifest(vararg keys: String) = SkinManifest(
        id = "test",
        name = "Test",
        frameWidth = 32,
        frameHeight = 32,
        scale = 4,
        fps = 8,
        animations = keys.associateWith { SkinAnimation("$it.png", 4, true) },
    )

    @Test
    fun die_stufe_hat_einen_eigenen_namenszusatz_gesund_hat_keinen() {
        assertNull(SkinManifest.suffixFor(HealthStage.HEALTHY))
        assertEquals("weak", SkinManifest.suffixFor(HealthStage.WEAKENED))
        assertEquals("sick", SkinManifest.suffixFor(HealthStage.SICK))
        assertEquals("miserable", SkinManifest.suffixFor(HealthStage.MISERABLE))
    }

    @Test
    fun fehlt_die_stufenfassung_wird_die_grundform_genommen() {
        val skin = manifest("idle", "idle_sick")

        assertEquals("idle_sick", skin.resolvedKey("idle", HealthStage.SICK))
        // Für geschwächt und elend gibt es nichts Eigenes — also die Grundform.
        assertEquals("idle", skin.resolvedKey("idle", HealthStage.WEAKENED))
        assertEquals("idle", skin.resolvedKey("idle", HealthStage.MISERABLE))
        assertEquals("idle", skin.resolvedKey("idle", HealthStage.HEALTHY))
    }

    @Test
    fun ein_skin_mit_nur_einer_animation_funktioniert_in_jeder_stufe() {
        val skin = manifest("idle")
        HealthStage.entries.forEach { stufe ->
            assertEquals("idle.png", skin.resolve("idle", stufe)?.file)
        }
    }

    @Test
    fun gesund_greift_nie_auf_eine_stufenfassung_zurueck() {
        // Sonst sähe ein kerngesundes Pet krank aus, nur weil die Datei existiert.
        val skin = manifest("idle", "idle_sick", "idle_weak", "idle_miserable")
        assertEquals("idle", skin.resolvedKey("idle", HealthStage.HEALTHY))
        assertEquals("idle_weak", skin.resolvedKey("idle", HealthStage.WEAKENED))
        assertEquals("idle_sick", skin.resolvedKey("idle", HealthStage.SICK))
        assertEquals("idle_miserable", skin.resolvedKey("idle", HealthStage.MISERABLE))
    }

    @Test
    fun ein_unbekannter_zustand_liefert_nichts_statt_zu_werfen() {
        val skin = manifest("idle")
        assertNull(skin.resolve("tanzen", HealthStage.HEALTHY))
        assertNull(skin.resolvedKey("tanzen", HealthStage.SICK))
    }

    @Test
    fun das_beispiel_aus_dem_projektplan_laesst_sich_lesen() {
        val json = """
            {
              "id": "beispiel",
              "name": "Beispielwesen",
              "frameWidth": 32, "frameHeight": 32, "scale": 4, "fps": 8,
              "animations": {
                "idle":       { "file": "idle.png",       "frames": 4, "loop": true  },
                "idle_sick":  { "file": "idle_sick.png",  "frames": 4, "loop": true  },
                "eat":        { "file": "eat.png",        "frames": 4, "loop": false }
              }
            }
        """.trimIndent()

        val skin = SkinManifest.parse(json)!!
        assertEquals("beispiel", skin.id)
        assertEquals("Beispielwesen", skin.name)
        assertEquals(32, skin.frameWidth)
        assertEquals(4, skin.scale)
        assertEquals(8, skin.fps)
        assertEquals(3, skin.animations.size)
        assertEquals(false, skin.animations.getValue("eat").loop)
        assertEquals("idle_sick.png", skin.resolve("idle", HealthStage.SICK)?.file)
        // Fürs Essen gibt es keine kranke Fassung — die gesunde tut es auch.
        assertEquals("eat.png", skin.resolve("eat", HealthStage.SICK)?.file)
    }

    @Test
    fun eine_kaputte_datei_verhindert_den_start_nicht() {
        // Projektplan, Abschnitt 13: beschädigter Skin → Standard, kein Absturz.
        assertNull(SkinManifest.parse("kein json"))
        assertNull(SkinManifest.parse(""))
        assertNull(SkinManifest.parse("[1, 2, 3]"))
        assertNull(SkinManifest.parse("""{ "name": "ohne id" }"""))
        assertNull(SkinManifest.parse("""{ "id": "" }"""))
    }

    @Test
    fun unvollstaendige_eintraege_werden_uebergangen_der_rest_bleibt_nutzbar() {
        val skin = SkinManifest.parse(
            """
            {
              "id": "halb",
              "animations": {
                "idle": { "file": "idle.png" },
                "kaputt": { "frames": 4 },
                "auchkaputt": 7
              }
            }
            """.trimIndent()
        )!!

        assertEquals("halb", skin.name) // Ohne Namen zählt die Kennung.
        assertEquals(setOf("idle"), skin.animations.keys)
        assertEquals(1, skin.animations.getValue("idle").frames) // Vorgabe
        assertTrue(skin.animations.getValue("idle").loop)
    }
}
