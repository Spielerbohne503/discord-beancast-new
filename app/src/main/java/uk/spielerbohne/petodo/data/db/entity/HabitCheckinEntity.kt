package uk.spielerbohne.petodo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein Haken an einem Tag.
 *
 * [day] ist der **Kalendertag** als Epochentag in Ortszeit, kein Zeitpunkt. Eine
 * Gewohnheit gehört zu einem Tag, nicht zu einer Uhrzeit — mit einem Zeitstempel würde
 * ein Zeitzonenwechsel Haken auf den Vortag schieben.
 *
 * Der zusammengesetzte Index über (`habitId`, `day`) ist eindeutig: Ein Tag wird einmal
 * abgehakt. Das Zurücknehmen setzt wie überall [deletedAt] statt zu löschen; beim
 * erneuten Abhaken lebt dieselbe Zeile wieder auf.
 */
@Entity(
    tableName = "habit_checkins",
    indices = [
        Index(value = ["habitId", "day"], unique = true),
        Index("habitId"),
        Index("deletedAt"),
    ],
)
data class HabitCheckinEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val day: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
