package uk.spielerbohne.petodo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.spielerbohne.petodo.data.db.entity.TagEntity
import uk.spielerbohne.petodo.data.db.entity.TaskTagEntity

@Dao
interface TagDao {

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY sortKey")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY sortKey")
    suspend fun all(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL AND name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT sortKey FROM tags WHERE deletedAt IS NULL ORDER BY sortKey DESC LIMIT 1")
    suspend fun highestSortKey(): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Update
    suspend fun update(tag: TagEntity)

    // ------------------------------------------------------------------ Zuordnungen

    @Query("SELECT * FROM task_tags WHERE deletedAt IS NULL")
    fun observeAssignments(): Flow<List<TaskTagEntity>>

    @Query("SELECT * FROM task_tags WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun findAssignment(taskId: String, tagId: String): TaskTagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(assignment: TaskTagEntity)
}
