package uk.spielerbohne.petodo.data.backup

import androidx.sqlite.db.SimpleSQLiteQuery
import uk.spielerbohne.petodo.data.db.PetodoDatabase
import uk.spielerbohne.petodo.domain.backup.BackupDocument
import uk.spielerbohne.petodo.domain.backup.BackupFormat
import uk.spielerbohne.petodo.domain.backup.BackupMerge
import uk.spielerbohne.petodo.domain.backup.JsonValue
import uk.spielerbohne.petodo.domain.backup.RestoreReport
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock

/**
 * Sichern und Wiederherstellen.
 *
 * Gelesen und geschrieben wird tabellenweise über den rohen SQLite-Zugriff: Eine
 * Sicherung muss auch die Spalten mitnehmen, für die es (noch) keine Oberfläche gibt —
 * sonst verliert man beim Wiederherstellen genau die Felder, die als Einbahnstraße
 * angelegt wurden.
 *
 * Wiederhergestellt wird **zusammenführend**, nicht überschreibend: Je Zeile gewinnt der
 * jüngere `updatedAt`-Stand. Damit kostet ein versehentliches Einlesen einer alten
 * Sicherung keine neuere Arbeit.
 */
class BackupRepository(
    private val database: PetodoDatabase,
    private val clock: Clock,
) {

    /** Baut das Sicherungsdokument aus allen Tabellen — inklusive Tombstones. */
    suspend fun export(): BackupDocument {
        val tables = BackupFormat.TABLES.associateWith { table -> readTable(table) }
        return BackupDocument(
            version = BackupFormat.VERSION,
            exportedAt = clock.millis(),
            tables = tables,
        )
    }

    suspend fun writeTo(output: OutputStream) {
        val text = BackupFormat.encode(export())
        output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    /**
     * Liest eine Sicherung ein. `null` bedeutet: Die Datei ist keine gültige
     * PeTodo-Sicherung — dann wurde nichts angefasst.
     */
    suspend fun restoreFrom(input: InputStream): RestoreReport? {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val document = BackupFormat.decode(text) ?: return null

        var report = RestoreReport()
        database.runInTransaction {
            // Reihenfolge: erst Listen und Etiketten, dann alles, was darauf zeigt.
            BackupFormat.TABLES.forEach { table ->
                report += mergeTable(table, document.rows(table))
            }
        }
        return report
    }

    // ---------------------------------------------------------------------------- intern

    private fun readTable(table: String): List<JsonValue.Object> {
        val rows = mutableListOf<JsonValue.Object>()
        database.openHelper.readableDatabase
            .query(SimpleSQLiteQuery("SELECT * FROM `$table`"))
            .use { cursor ->
                while (cursor.moveToNext()) {
                    val entries = LinkedHashMap<String, JsonValue>()
                    for (index in 0 until cursor.columnCount) {
                        entries[cursor.getColumnName(index)] = cursor.valueAt(index)
                    }
                    rows += JsonValue.Object(entries)
                }
            }
        return rows
    }

    private fun android.database.Cursor.valueAt(index: Int): JsonValue = when (getType(index)) {
        android.database.Cursor.FIELD_TYPE_NULL -> JsonValue.Null
        android.database.Cursor.FIELD_TYPE_INTEGER -> JsonValue.Number(getLong(index).toString())
        android.database.Cursor.FIELD_TYPE_FLOAT -> JsonValue.Number(getDouble(index).toString())
        else -> JsonValue.Text(getString(index))
    }

    private fun mergeTable(table: String, rows: List<JsonValue.Object>): RestoreReport {
        if (rows.isEmpty()) return RestoreReport()

        val keys = primaryKeyOf(table)
        val writable = database.openHelper.writableDatabase
        var inserted = 0
        var updated = 0
        var skipped = 0

        rows.forEach { row ->
            val keyValues = keys.map { row[it] }
            if (keyValues.any { it == null }) {
                skipped++
                return@forEach
            }

            val where = keys.joinToString(" AND ") { "`$it` = ?" }
            val arguments = keys.map { row.string(it) ?: row.long(it)?.toString() }.toTypedArray()

            val existingUpdatedAt = writable
                .query(SimpleSQLiteQuery("SELECT `updatedAt` FROM `$table` WHERE $where", arguments))
                .use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null }

            val exists = writable
                .query(SimpleSQLiteQuery("SELECT 1 FROM `$table` WHERE $where", arguments))
                .use { it.moveToFirst() }

            if (exists && !BackupMerge.shouldReplace(existingUpdatedAt, BackupMerge.updatedAtOf(row))) {
                skipped++
                return@forEach
            }

            val columns = row.entries.keys.toList()
            val placeholders = columns.joinToString(",") { "?" }
            val values = columns.map { column ->
                when (val value = row[column]) {
                    null, JsonValue.Null -> null
                    is JsonValue.Text -> value.value
                    is JsonValue.Number -> value.asLong() ?: value.asDouble()
                    is JsonValue.Bool -> if (value.value) 1L else 0L
                    else -> null
                }
            }.toTypedArray()

            writable.execSQL(
                "INSERT OR REPLACE INTO `$table` (${columns.joinToString(",") { "`$it`" }}) " +
                    "VALUES ($placeholders)",
                values,
            )
            if (exists) updated++ else inserted++
        }

        return RestoreReport(inserted = inserted, updated = updated, skipped = skipped)
    }

    /** Die Primärschlüssel der Tabellen — nur `task_tags` ist zusammengesetzt. */
    private fun primaryKeyOf(table: String): List<String> = when (table) {
        "task_tags" -> listOf("taskId", "tagId")
        else -> listOf("id")
    }
}
