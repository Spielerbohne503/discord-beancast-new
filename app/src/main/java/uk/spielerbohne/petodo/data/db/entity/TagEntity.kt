package uk.spielerbohne.petodo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tag. v1 hat dafür keine UI — die Tabelle existiert, weil eine Mehrfachzuordnung
 * (eine Aufgabe, mehrere Tags) sich nicht in eine Spalte quetschen lässt und
 * nachträglich sowohl Migration als auch Sync-Nacharbeit kostet.
 */
@Entity(tableName = "tags", indices = [Index("sortKey")])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Int? = null,
    val sortKey: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Zuordnung Aufgabe ↔ Tag.
 *
 * Zusammengesetzter Primärschlüssel statt Auto-Increment; [deletedAt] als Tombstone,
 * damit ein entfernter Tag beim späteren Sync nicht wieder auftaucht.
 */
@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    indices = [Index("tagId")],
)
data class TaskTagEntity(
    val taskId: String,
    val tagId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
