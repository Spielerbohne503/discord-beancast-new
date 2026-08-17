package uk.spielerbohne.petodo.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {

    private fun task(id: String, updatedAt: Long, deleted: Long? = null) = JsonValue.obj(
        "id" to JsonValue.of(id),
        "title" to JsonValue.of("Aufgabe $id"),
        "updatedAt" to JsonValue.of(updatedAt),
        "deletedAt" to JsonValue.of(deleted),
    )

    private val document = BackupDocument(
        version = BackupFormat.VERSION,
        exportedAt = 1_787_000_000_000L,
        tables = mapOf(
            "tasks" to listOf(task("a", 1_000L), task("b", 2_000L)),
            "task_lists" to listOf(JsonValue.obj("id" to JsonValue.of("inbox"))),
        ),
    )

    @Test
    fun eine_sicherung_uebersteht_schreiben_und_lesen() {
        val gelesen = BackupFormat.decode(BackupFormat.encode(document))

        assertNotNull(gelesen)
        assertEquals(BackupFormat.VERSION, gelesen!!.version)
        assertEquals(1_787_000_000_000L, gelesen.exportedAt)
        assertEquals(2, gelesen.rows("tasks").size)
        assertEquals("Aufgabe a", gelesen.rows("tasks").first().string("title"))
    }

    @Test
    fun tombstones_stehen_mit_in_der_sicherung() {
        // Ohne sie käme eine gelöschte Aufgabe beim Wiederherstellen zurück.
        val mitTombstone = document.copy(
            tables = mapOf("tasks" to listOf(task("a", 5_000L, deleted = 4_000L)))
        )
        val gelesen = BackupFormat.decode(BackupFormat.encode(mitTombstone))!!

        assertEquals(4_000L, gelesen.rows("tasks").first().long("deletedAt"))
    }

    @Test
    fun alle_tabellen_stehen_in_der_datei_auch_die_leeren() {
        val gelesen = BackupFormat.decode(BackupFormat.encode(document))!!
        BackupFormat.TABLES.forEach { tabelle ->
            assertTrue("$tabelle fehlt", gelesen.tables.containsKey(tabelle))
        }
    }

    @Test
    fun eine_fremde_json_datei_wird_abgelehnt() {
        assertNull(BackupFormat.decode("""{"format":"etwas-anderes","version":1,"tables":{}}"""))
        assertNull(BackupFormat.decode("""{"version":1,"tables":{}}"""))
    }

    @Test
    fun eine_neuere_sicherung_wird_abgelehnt_statt_falsch_gelesen() {
        val zukunft = """{"format":"petodo-backup","version":99,"exportedAt":0,"tables":{}}"""
        assertNull(BackupFormat.decode(zukunft))
    }

    @Test
    fun eine_kaputte_datei_ueberschreibt_nichts() {
        assertNull(BackupFormat.decode(""))
        assertNull(BackupFormat.decode("{"))
        assertNull(BackupFormat.decode("Das ist keine Sicherung"))
    }

    @Test
    fun leere_sicherung_ist_gueltig_und_enthaelt_nichts() {
        val leer = BackupDocument(BackupFormat.VERSION, 0L, emptyMap())
        val gelesen = BackupFormat.decode(BackupFormat.encode(leer))!!

        assertEquals(0, gelesen.rowCount)
    }

    // ------------------------------------------------------------------ Zusammenführen

    @Test
    fun die_juengere_zeile_gewinnt() {
        assertTrue(BackupMerge.shouldReplace(existingUpdatedAt = 1_000L, incomingUpdatedAt = 2_000L))
        assertFalse(BackupMerge.shouldReplace(existingUpdatedAt = 2_000L, incomingUpdatedAt = 1_000L))
    }

    @Test
    fun bei_gleichem_stand_bleibt_das_geraet_bei_seiner_fassung() {
        assertFalse(BackupMerge.shouldReplace(existingUpdatedAt = 1_000L, incomingUpdatedAt = 1_000L))
    }

    @Test
    fun was_es_noch_nicht_gibt_wird_immer_uebernommen() {
        assertTrue(BackupMerge.shouldReplace(existingUpdatedAt = null, incomingUpdatedAt = 1L))
    }

    @Test
    fun eine_zeile_ohne_zeitstempel_wird_nicht_uebernommen() {
        // Sonst überschreibt eine kaputte Zeile eine gute.
        assertFalse(BackupMerge.shouldReplace(existingUpdatedAt = 1_000L, incomingUpdatedAt = null))
        assertFalse(BackupMerge.shouldReplace(existingUpdatedAt = null, incomingUpdatedAt = null))
    }

    @Test
    fun ein_juengerer_tombstone_gewinnt_gegen_eine_aeltere_aenderung() {
        val vorhanden = task("a", 1_000L)
        val eingelesen = task("a", 2_000L, deleted = 2_000L)

        assertTrue(
            BackupMerge.shouldReplace(
                BackupMerge.updatedAtOf(vorhanden),
                BackupMerge.updatedAtOf(eingelesen),
            )
        )
    }

    @Test
    fun ein_aelterer_tombstone_holt_eine_wiederbelebte_aufgabe_nicht_zurueck() {
        val vorhanden = task("a", 3_000L)
        val eingelesen = task("a", 2_000L, deleted = 2_000L)

        assertFalse(
            BackupMerge.shouldReplace(
                BackupMerge.updatedAtOf(vorhanden),
                BackupMerge.updatedAtOf(eingelesen),
            )
        )
    }

    @Test
    fun der_bericht_zaehlt_zusammen() {
        val bericht = RestoreReport(inserted = 2, updated = 1) + RestoreReport(inserted = 1, skipped = 3)
        assertEquals(3, bericht.inserted)
        assertEquals(1, bericht.updated)
        assertEquals(3, bericht.skipped)
        assertEquals(4, bericht.touched)
    }
}
