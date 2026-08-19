package uk.spielerbohne.petodo.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Die Sicherung muss **jede** Tabelle enthalten.
 *
 * Eine vergessene Tabelle merkt niemand beim Sichern — sondern erst beim
 * Wiederherstellen, wenn die Daten weg sind. Deshalb vergleicht dieser Test die Liste in
 * [BackupFormat] mit dem tatsächlichen Schema der Datenbank und schlägt fehl, sobald
 * jemand eine Tabelle anlegt und die Sicherung vergisst.
 *
 * Gelesen wird die exportierte Schemadatei — dieselbe Quelle, gegen die auch die
 * Migrationen geprüft werden.
 */
class BackupCoverageTest {

    @Test
    fun die_sicherung_deckt_alle_tabellen_des_schemas_ab() {
        val schema = neuesteSchemadatei().readText()

        // Kein JSON-Parser im Test: Die Tabellennamen stehen als "tableName": "…" drin.
        val tabellen = Regex(""""tableName"\s*:\s*"([^"]+)"""")
            .findAll(schema)
            .map { it.groupValues[1] }
            .filterNot { it.startsWith("room_") }
            .toSortedSet()

        assertEquals(
            "Die Sicherung kennt nicht dieselben Tabellen wie die Datenbank",
            tabellen,
            BackupFormat.TABLES.toSortedSet(),
        )
    }

    private fun neuesteSchemadatei(): File {
        val ordner = listOf(
            File("schemas/uk.spielerbohne.petodo.data.db.PetodoDatabase"),
            File("app/schemas/uk.spielerbohne.petodo.data.db.PetodoDatabase"),
        ).first(File::exists)

        return ordner.listFiles { datei -> datei.extension == "json" }
            .orEmpty()
            .maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: 0 }
            ?: error("Keine exportierte Schemadatei gefunden")
    }
}
