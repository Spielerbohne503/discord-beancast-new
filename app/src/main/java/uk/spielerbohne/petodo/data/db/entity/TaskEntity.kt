package uk.spielerbohne.petodo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import uk.spielerbohne.petodo.domain.model.Priority

/**
 * Aufgabe. Alle Felder aus Abschnitt 4.1 des Projektplans — auch die, die v1 nicht
 * benutzt. Nachträglich ergänzen kostet eine Datenmigration.
 *
 * [id] ist eine UUID, niemals Auto-Increment. [updatedAt] ist das Sync-Ticket,
 * [deletedAt] der Tombstone: gelöscht wird nie wirklich.
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index("listId"),
        Index("dueAt"),
        Index("deletedAt"),
        Index("sortKey"),
        Index("parentId"),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val listId: String,
    /**
     * Übergeordnete Aufgabe (Unteraufgaben). v1 hat dafür keine UI und schreibt immer
     * null — das Feld existiert, damit Unteraufgaben später keine Migration kosten.
     */
    val parentId: String? = null,
    val title: String,
    val note: String? = null,
    /** UTC-Millis. */
    val dueAt: Long? = null,
    /** "HH:mm" — Anker für den Nag, unabhängig von Zeitumstellungen. */
    val dueTimeLocal: String? = null,
    /** nur Datum vs. Datum + Uhrzeit */
    val hasTime: Boolean = false,
    /** 0 niedrig · 1 normal · 2 hoch · 3 dringend */
    val priority: Int = Priority.DEFAULT,
    /** RFC 5545, v1 immer null */
    val rrule: String? = null,
    val completedAt: Long? = null,
    /** Fractional Index (Lexorank), NICHT Integer-Position. */
    @ColumnInfo(name = "sortKey") val sortKey: String,
    /**
     * Wie viele Termine einer Wiederholung übersprungen wurden. Verpasste Termine
     * werden gezählt, erzeugen aber keine zusätzliche Überfälligkeitslast.
     */
    @ColumnInfo(defaultValue = "0") val missedCount: Int = 0,
    val nagCount: Int = 0,
    val nagLastAt: Long? = null,
    val snoozedUntil: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
