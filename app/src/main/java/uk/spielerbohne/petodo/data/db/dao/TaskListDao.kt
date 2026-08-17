package uk.spielerbohne.petodo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.spielerbohne.petodo.data.db.entity.TaskListEntity

@Dao
interface TaskListDao {

    @Query("SELECT * FROM task_lists WHERE deletedAt IS NULL ORDER BY sortKey")
    fun observeAll(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM task_lists WHERE deletedAt IS NULL ORDER BY sortKey")
    suspend fun all(): List<TaskListEntity>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    suspend fun findById(id: String): TaskListEntity?

    /** IDs der Listen, die vom Nag und von der Überfälligkeitslast ausgenommen sind. */
    @Query("SELECT id FROM task_lists WHERE excludeFromNag = 1 AND deletedAt IS NULL")
    suspend fun idsExcludedFromNag(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(list: TaskListEntity)

    @Update
    suspend fun update(list: TaskListEntity)
}
