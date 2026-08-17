package uk.spielerbohne.petodo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.spielerbohne.petodo.data.db.entity.FocusSessionEntity

@Dao
interface FocusSessionDao {

    @Query("SELECT * FROM focus_sessions WHERE deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeLatest(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun latest(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE deletedAt IS NULL AND startedAt >= :since ORDER BY startedAt")
    fun observeSince(since: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun findById(id: String): FocusSessionEntity?

    @Insert
    suspend fun insert(session: FocusSessionEntity)

    @Update
    suspend fun update(session: FocusSessionEntity)
}
