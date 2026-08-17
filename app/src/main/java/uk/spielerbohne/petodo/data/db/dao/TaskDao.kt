package uk.spielerbohne.petodo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.spielerbohne.petodo.data.db.entity.TaskEntity

@Dao
interface TaskDao {

    /** Alle nicht gelöschten Aufgaben. Gelöschte bleiben als Tombstone in der Tabelle. */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND completedAt IS NULL AND dueAt IS NOT NULL")
    suspend fun openWithDueDate(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    /** Höchster vergebener Sortierschlüssel — Anker fürs Anhängen am Listenende. */
    @Query("SELECT sortKey FROM tasks WHERE deletedAt IS NULL ORDER BY sortKey DESC LIMIT 1")
    suspend fun highestSortKey(): String?

    @Insert
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)
}
