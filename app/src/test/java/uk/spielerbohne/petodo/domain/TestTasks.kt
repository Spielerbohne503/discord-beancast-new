package uk.spielerbohne.petodo.domain

import uk.spielerbohne.petodo.domain.model.Priority
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.domain.model.TaskList
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Werkzeugkasten für die Domänentests: Aufgaben knapp und lesbar bauen. */
object TestTasks {

    val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

    fun at(date: String, time: String = "00:00", zone: ZoneId = BERLIN): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    fun task(
        id: String = "t-1",
        title: String = "Aufgabe",
        dueAt: Instant? = null,
        hasTime: Boolean = dueAt != null,
        dueTimeLocal: LocalTime? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null,
        priority: Int = Priority.DEFAULT,
        listId: String = TaskList.ID_INBOX,
        sortKey: String = "i",
        createdAt: Instant = at("2026-01-01"),
        nagCount: Int = 0,
        nagLastAt: Instant? = null,
        snoozedUntil: Instant? = null,
    ): Task = Task(
        id = id,
        listId = listId,
        title = title,
        dueAt = dueAt,
        dueTimeLocal = dueTimeLocal,
        hasTime = hasTime,
        priority = priority,
        completedAt = completedAt,
        sortKey = sortKey,
        nagCount = nagCount,
        nagLastAt = nagLastAt,
        snoozedUntil = snoozedUntil,
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = deletedAt,
    )
}
