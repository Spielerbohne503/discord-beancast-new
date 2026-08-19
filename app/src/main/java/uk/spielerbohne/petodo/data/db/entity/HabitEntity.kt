package uk.spielerbohne.petodo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Eine Gewohnheit.
 *
 * [scheduleMask] ist die Wochentagsmaske aus `domain/habit/HabitSchedule` — sieben Bits
 * in einer Zahl statt einer zweiten Tabelle.
 */
@Entity(
    tableName = "habits",
    indices = [Index("sortKey"), Index("deletedAt")],
)
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val scheduleMask: Int,
    val colorArgb: Int? = null,
    val sortKey: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
