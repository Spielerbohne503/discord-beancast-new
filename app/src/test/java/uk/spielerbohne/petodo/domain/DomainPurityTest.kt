package uk.spielerbohne.petodo.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wacht über die Architekturregel: `domain/` bleibt frei von Android.
 *
 * Der Test liest die Quelldateien, nicht den Bytecode — so schlägt er auch dann fehl,
 * wenn ein Import nur "vorsichtshalber" dasteht. Er wird nicht repariert, indem man ihn
 * lockert: Wer einen Android-Typ in der Domäne braucht, hat die Grenze falsch gezogen.
 */
class DomainPurityTest {

    @Test
    fun domain_enthaelt_keinen_einzigen_android_import() {
        val verbotene = domainSources().flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) ->
                    val l = line.trim()
                    l.startsWith("import android.") || l.startsWith("import androidx.")
                }
                .map { (index, line) -> "${file.name}:${index + 1}  ${line.trim()}" }
        }

        assertTrue(
            "domain/ muss frei von Android-Importen bleiben, gefunden:\n" +
                verbotene.joinToString("\n"),
            verbotene.isEmpty(),
        )
    }

    @Test
    fun domain_liest_die_zeit_nie_aus_der_systemuhr() {
        val verbotene = domainSources().flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) ->
                    line.contains("System.currentTimeMillis()") ||
                        line.contains("Instant.now()") ||
                        line.contains("LocalDate.now()") ||
                        line.contains("LocalDateTime.now()")
                }
                .map { (index, line) -> "${file.name}:${index + 1}  ${line.trim()}" }
        }

        assertTrue(
            "Zeit gehört als Parameter herein, nicht aus der Systemuhr gelesen:\n" +
                verbotene.joinToString("\n"),
            verbotene.isEmpty(),
        )
    }

    @Test
    fun domain_quellen_werden_ueberhaupt_gefunden() {
        assertTrue("Keine Quelldateien unter domain/ gefunden", domainSources().isNotEmpty())
    }

    private fun domainSources(): List<File> =
        domainDir().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun domainDir(): File {
        val relative = "src/main/java/uk/spielerbohne/petodo/domain"
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            File(candidate, relative).takeIf(File::isDirectory)?.let { return it }
            File(candidate, "app/$relative").takeIf(File::isDirectory)?.let { return it }
            candidate = candidate.parentFile
        }
        error("Verzeichnis domain/ nicht gefunden (Arbeitsverzeichnis: ${File("").absolutePath})")
    }
}
