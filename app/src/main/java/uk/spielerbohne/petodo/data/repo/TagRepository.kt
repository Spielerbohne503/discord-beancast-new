package uk.spielerbohne.petodo.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import uk.spielerbohne.petodo.data.db.dao.TagDao
import uk.spielerbohne.petodo.data.db.entity.TagEntity
import uk.spielerbohne.petodo.data.db.entity.TaskTagEntity
import uk.spielerbohne.petodo.data.mapper.toDomain
import uk.spielerbohne.petodo.domain.model.Tag
import uk.spielerbohne.petodo.domain.sort.FractionalIndex
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Etiketten und ihre Zuordnung zu Aufgaben.
 *
 * Entfernen ist auch hier ein Tombstone: Ohne ihn käme ein abgenommenes Etikett beim
 * späteren Sync vom anderen Gerät zurück.
 */
class TagRepository(
    private val tagDao: TagDao,
    private val clock: Clock,
) {

    fun observeTags(): Flow<List<Tag>> =
        tagDao.observeAll().map { tags -> tags.map(TagEntity::toDomain) }

    /** Etiketten je Aufgabe, fertig zugeordnet. */
    fun observeTagsByTask(): Flow<Map<String, List<Tag>>> =
        combine(tagDao.observeAll(), tagDao.observeAssignments()) { tags, assignments ->
            val byId = tags.associateBy { it.id }
            assignments
                .groupBy { it.taskId }
                .mapValues { (_, list) -> list.mapNotNull { byId[it.tagId]?.toDomain() } }
        }

    /** Etikett anlegen oder das gleichnamige wiederverwenden. */
    suspend fun ensureTag(rawName: String): Tag? {
        val name = Tag.normalizeName(rawName) ?: return null
        tagDao.findByName(name)?.let { return it.toDomain() }

        val now = Instant.now(clock).toEpochMilli()
        val entity = TagEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            colorArgb = null,
            sortKey = FractionalIndex.afterOrInitial(tagDao.highestSortKey()),
            createdAt = now,
            updatedAt = now,
        )
        tagDao.insert(entity)
        // Bei einem Wettlauf gewinnt der bestehende Eintrag.
        return (tagDao.findByName(name) ?: entity).toDomain()
    }

    suspend fun assign(taskId: String, tagId: String) {
        val now = Instant.now(clock).toEpochMilli()
        val existing = tagDao.findAssignment(taskId, tagId)
        tagDao.upsertAssignment(
            TaskTagEntity(
                taskId = taskId,
                tagId = tagId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            )
        )
    }

    suspend fun unassign(taskId: String, tagId: String) {
        val existing = tagDao.findAssignment(taskId, tagId) ?: return
        val now = Instant.now(clock).toEpochMilli()
        tagDao.upsertAssignment(existing.copy(deletedAt = now, updatedAt = now))
    }

    /** Etikett per Name an eine Aufgabe hängen — legt es bei Bedarf an. */
    suspend fun addTagByName(taskId: String, rawName: String): Tag? {
        val tag = ensureTag(rawName) ?: return null
        assign(taskId, tag.id)
        return tag
    }

    suspend fun renameTag(tagId: String, rawName: String) {
        val name = Tag.normalizeName(rawName) ?: return
        val entity = tagDao.all().firstOrNull { it.id == tagId } ?: return
        tagDao.update(entity.copy(name = name, updatedAt = Instant.now(clock).toEpochMilli()))
    }

    suspend fun deleteTag(tagId: String) {
        val entity = tagDao.all().firstOrNull { it.id == tagId } ?: return
        val now = Instant.now(clock).toEpochMilli()
        tagDao.update(entity.copy(deletedAt = now, updatedAt = now))
    }
}
