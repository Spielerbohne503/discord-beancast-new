package uk.spielerbohne.petodo.data.mapper

import uk.spielerbohne.petodo.data.db.entity.TaskEntity
import uk.spielerbohne.petodo.data.db.entity.TagEntity
import uk.spielerbohne.petodo.data.db.entity.TaskListEntity
import uk.spielerbohne.petodo.domain.model.Priority
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.domain.model.Tag
import uk.spielerbohne.petodo.domain.model.TaskList
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Grenze zwischen Datenbank (primitive Typen) und Domäne (`java.time`).
 *
 * Kaputte Werte in der Datenbank dürfen den Start nicht verhindern (Projektplan,
 * Abschnitt 13): Eine unlesbare Uhrzeit wird zu `null`, nicht zu einer Ausnahme.
 */
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun String?.toLocalTimeOrNull(): LocalTime? =
    this?.let { runCatching { LocalTime.parse(it, HHMM) }.getOrNull() }

fun LocalTime?.toHhMmOrNull(): String? = this?.format(HHMM)

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    listId = listId,
    parentId = parentId,
    title = title,
    note = note,
    dueAt = dueAt?.let(Instant::ofEpochMilli),
    dueTimeLocal = dueTimeLocal.toLocalTimeOrNull(),
    hasTime = hasTime,
    priority = Priority.coerce(priority),
    rrule = rrule,
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    sortKey = sortKey,
    nagCount = nagCount,
    nagLastAt = nagLastAt?.let(Instant::ofEpochMilli),
    snoozedUntil = snoozedUntil?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    listId = listId,
    parentId = parentId,
    title = title,
    note = note,
    dueAt = dueAt?.toEpochMilli(),
    dueTimeLocal = dueTimeLocal.toHhMmOrNull(),
    hasTime = hasTime,
    priority = priority,
    rrule = rrule,
    completedAt = completedAt?.toEpochMilli(),
    sortKey = sortKey,
    nagCount = nagCount,
    nagLastAt = nagLastAt?.toEpochMilli(),
    snoozedUntil = snoozedUntil?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
)

fun TaskListEntity.toDomain(): TaskList = TaskList(
    id = id,
    parentId = parentId,
    name = name,
    colorArgb = colorArgb,
    sortKey = sortKey,
    excludeFromNag = excludeFromNag,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
)

fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    colorArgb = colorArgb,
    sortKey = sortKey,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
)
