package uk.spielerbohne.petodo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Liste. v1 zeigt nur die Inbox; "irgendwann" existiert mit [excludeFromNag] = true als
 * Druckventil und zählt später nicht in die Überfälligkeitslast.
 */
@Entity(tableName = "task_lists", indices = [Index("parentId")])
data class TaskListEntity(
    @PrimaryKey val id: String,
    val name: String,
    /**
     * Übergeordneter Ordner. v1 kennt keine Ordner und schreibt immer null — wie bei
     * `listId` steht das Feld von Anfang an da.
     */
    val parentId: String? = null,
    val colorArgb: Int? = null,
    val sortKey: String,
    val excludeFromNag: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
