package uk.spielerbohne.petodo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fokussitzung. [endsAt] ist der **absolute** Endzeitpunkt — kein heruntergezählter Rest.
 * Ein Countdown driftet und steht im Energiesparmodus still.
 */
@Entity(
    tableName = "focus_sessions",
    indices = [Index("startedAt"), Index("taskId")],
)
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val taskId: String? = null,
    val startedAt: Long,
    val endsAt: Long,
    /** FOCUS · SHORT_BREAK · LONG_BREAK */
    val kind: String,
    val completedAt: Long? = null,
    val abortedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
