package uk.spielerbohne.petodo.domain.backup

/**
 * Das Sicherungsformat: eine JSON-Datei mit einer Zeilenliste je Tabelle.
 *
 * Bewusst tabellennah statt hübsch: Was hier drinsteht, ist genau das, was in der
 * Datenbank steht — inklusive Tombstones und `updatedAt`. Damit ist die Sicherung
 * zugleich die Vorstufe des späteren Sync (Projektplan v3.0, Last-Write-Wins über
 * `updatedAt`), und eine Wiederherstellung kann zusammenführen statt zu überschreiben.
 */
data class BackupDocument(
    val version: Int,
    val exportedAt: Long,
    val tables: Map<String, List<JsonValue.Object>>,
) {
    fun rows(table: String): List<JsonValue.Object> = tables[table].orEmpty()

    val rowCount: Int get() = tables.values.sumOf { it.size }
}

object BackupFormat {

    /** Kennung, damit eine fremde JSON-Datei nicht versehentlich eingelesen wird. */
    const val MAGIC = "petodo-backup"

    const val VERSION = 1

    private const val KEY_FORMAT = "format"
    private const val KEY_VERSION = "version"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_TABLES = "tables"

    /** Reihenfolge der Tabellen in der Datei — nur der Lesbarkeit halber fest. */
    val TABLES = listOf(
        "task_lists",
        "tasks",
        "tags",
        "task_tags",
        "reminders",
        "reward_events",
        "pet_state",
        "focus_sessions",
        "habits",
        "habit_checkins",
    )

    fun encode(document: BackupDocument, indent: Int = 2): String {
        val tables = TABLES.associateWith { table ->
            JsonValue.Array(document.rows(table)) as JsonValue
        }
        return Json.write(
            JsonValue.obj(
                KEY_FORMAT to JsonValue.of(MAGIC),
                KEY_VERSION to JsonValue.of(document.version),
                KEY_EXPORTED_AT to JsonValue.of(document.exportedAt),
                KEY_TABLES to JsonValue.Object(tables),
            ),
            indent = indent,
        )
    }

    /**
     * Liest eine Sicherung.
     *
     * Gibt `null` zurück, wenn die Datei kaputt ist, keine PeTodo-Sicherung ist oder aus
     * einer neueren Version stammt, die dieses Programm nicht kennt. Eine unlesbare Datei
     * darf nichts überschreiben.
     */
    fun decode(text: String): BackupDocument? {
        val root = Json.parse(text) as? JsonValue.Object ?: return null
        if (root.string(KEY_FORMAT) != MAGIC) return null

        val version = root.int(KEY_VERSION) ?: return null
        if (version > VERSION) return null

        val tables = (root[KEY_TABLES] as? JsonValue.Object) ?: return null

        return BackupDocument(
            version = version,
            exportedAt = root.long(KEY_EXPORTED_AT) ?: 0L,
            tables = tables.entries.mapValues { (_, value) ->
                (value as? JsonValue.Array)?.items?.filterIsInstance<JsonValue.Object>().orEmpty()
            },
        )
    }
}

/**
 * Wie eine eingelesene Zeile mit einer vorhandenen verrechnet wird.
 *
 * Last-Write-Wins über `updatedAt` — dieselbe Regel, die der spätere Sync benutzt. Ein
 * Tombstone ist dabei kein Sonderfall: Er trägt ein `updatedAt` wie jede andere Änderung
 * und gewinnt, wenn er jünger ist.
 */
object BackupMerge {

    const val FIELD_UPDATED_AT = "updatedAt"

    /** Nimmt die eingelesene Zeile nur, wenn sie neuer ist als die vorhandene. */
    fun shouldReplace(existingUpdatedAt: Long?, incomingUpdatedAt: Long?): Boolean {
        if (incomingUpdatedAt == null) return false
        if (existingUpdatedAt == null) return true
        return incomingUpdatedAt > existingUpdatedAt
    }

    fun updatedAtOf(row: JsonValue.Object): Long? = row.long(FIELD_UPDATED_AT)
}

/** Was eine Wiederherstellung bewirkt hat. */
data class RestoreReport(
    val inserted: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
) {
    val touched: Int get() = inserted + updated

    operator fun plus(other: RestoreReport) = RestoreReport(
        inserted = inserted + other.inserted,
        updated = updated + other.updated,
        skipped = skipped + other.skipped,
    )
}
