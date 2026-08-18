package uk.spielerbohne.petodo.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wacht darüber, dass reguläre Ausdrücke auch auf dem Telefon übersetzt werden können.
 *
 * Der Anlass ist ein Absturz, der jede Fassung der App unstartbar machte: `(?U)` schaltet
 * in Javas Regex-Maschine die Wortgrenzen auf Unicode. Android führt reguläre Ausdrücke
 * über ICU aus, und **ICU kennt diesen Schalter nicht** — der Ausdruck lässt sich dort
 * nicht übersetzen, das umgebende Objekt kommt nicht hoch, und die App startet nicht mehr.
 *
 * Kein Unit-Test hätte das je gemerkt: Er läuft auf der JVM, also mit genau der Maschine,
 * auf der `(?U)` funktioniert. Deshalb prüft dieser Test nicht das Verhalten, sondern den
 * Quelltext — er ist die einzige Stelle, an der der Unterschied überhaupt sichtbar wird.
 *
 * Wer Unicode-Wortgrenzen braucht, schreibt sie aus:
 * `(?<![\p{L}\p{N}_])` und `(?![\p{L}\p{N}_])`.
 */
class RegexPortabilityTest {

    /** Muster, die Java kennt und ICU nicht. */
    private val nurAufDerJvm = listOf(
        "(?U)" to "Unicode-Wortgrenzen ausschreiben statt (?U)",
        "(?U:" to "Unicode-Wortgrenzen ausschreiben statt (?U:)",
        "\\p{IsAlphabetic}" to "\\p{L} benutzen",
        "\\p{IsDigit}" to "\\p{N} benutzen",
        "\\p{javaLowerCase}" to "\\p{Ll} benutzen",
    )

    @Test
    fun kein_muster_benutzt_etwas_das_nur_die_jvm_kennt() {
        val funde = sources().flatMap { file ->
            file.readLines().withIndex()
                // Kommentare dürfen die Falle beim Namen nennen — genau das tun sie an
                // der Stelle, an der sie erklärt wird.
                .filterNot { (_, line) -> line.isComment() }
                .flatMap { (index, line) ->
                    nurAufDerJvm.mapNotNull { (muster, rat) ->
                        if (line.contains(muster)) "${file.name}:${index + 1}  $muster — $rat" else null
                    }
                }
        }

        assertTrue(
            "Diese Muster übersetzt Android nicht — die App startet damit nicht mehr:\n" +
                funde.joinToString("\n"),
            funde.isEmpty(),
        )
    }

    @Test
    fun die_quellen_werden_ueberhaupt_gefunden() {
        assertTrue("Keine Quelldateien gefunden", sources().isNotEmpty())
    }

    private fun String.isComment(): Boolean {
        val gekuerzt = trimStart()
        return gekuerzt.startsWith("//") || gekuerzt.startsWith("*") || gekuerzt.startsWith("/*")
    }

    private fun sources(): List<File> {
        val wurzel = listOf(File("src/main/java"), File("app/src/main/java")).first(File::exists)
        return wurzel.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }
}
