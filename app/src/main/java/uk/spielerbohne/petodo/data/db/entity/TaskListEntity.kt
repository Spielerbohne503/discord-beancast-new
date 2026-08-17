package uk.spielerbohne.petodo.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Liste. v1 zeigt nur die Inbox; "irgendwann" existiert mit [excludeFromNag] = true als
 * Druckventil und zählt später nicht in die Überfälligkeitslast.
 */
@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Int? = null,
    val sortKey: String,
    val excludeFromNag: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
