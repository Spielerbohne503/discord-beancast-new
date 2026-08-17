package uk.spielerbohne.petodo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Zusätzlicher Erinnerungszeitpunkt zu einer Aufgabe ("30 Minuten vorher").
 *
 * Die Nag-Kette selbst hängt an der Aufgabe (`dueAt` + `dueTimeLocal` + `nagCount`) und
 * braucht diese Tabelle nicht. Sie deckt den anderen Fall ab: mehrere Vorwarnungen zu
 * *einem* Termin. Ohne eigene Tabelle ginge das nicht, und sie nachzureichen kostet
 * genau die Migration, die die Einbahnstraßen-Liste vermeiden soll.
 *
 * v1 legt keine Zeilen an — wie bei `rrule`.
 */
@Entity(
    tableName = "reminders",
    indices = [Index("taskId"), Index("deletedAt")],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    /** Minuten **vor** der Fälligkeit. 0 bedeutet: genau zur Fälligkeit. */
    val offsetMinutes: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
