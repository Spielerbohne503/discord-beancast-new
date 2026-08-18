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

    /** Der laufende oder angehaltene Durchlauf — es gibt immer höchstens einen. */
    @Query(
        "SELECT * FROM focus_sessions WHERE deletedAt IS NULL AND completedAt IS NULL " +
            "AND abortedAt IS NULL ORDER BY startedAt DESC LIMIT 1"
    )
    fun observeActive(): Flow<FocusSessionEntity?>

    @Query(
        "SELECT * FROM focus_sessions WHERE deletedAt IS NULL AND completedAt IS NULL " +
            "AND abortedAt IS NULL ORDER BY startedAt DESC LIMIT 1"
    )
    suspend fun active(): FocusSessionEntity?

    /** Abgeschlossene Fokusrunden in einem Zeitraum — die Zahl in der Statuszeile. */
    @Query(
        "SELECT COUNT(*) FROM focus_sessions WHERE deletedAt IS NULL AND kind = 'FOCUS' " +
            "AND abortedAt IS NULL AND completedAt IS NOT NULL AND completedAt >= :since AND completedAt < :until"
    )
    fun observeCompletedFocusCount(since: Long, until: Long): Flow<Int>

    /** Abgeschlossene Fokusrunden seit einem Zeitpunkt — für den Rückblick. */
    @Query(
        "SELECT COUNT(*) FROM focus_sessions WHERE deletedAt IS NULL AND kind = 'FOCUS' " +
            "AND abortedAt IS NULL AND completedAt IS NOT NULL AND completedAt >= :since"
    )
    suspend fun completedFocusCountSince(since: Long): Int

    @Query(
        "SELECT COUNT(*) FROM focus_sessions WHERE deletedAt IS NULL AND kind = 'FOCUS' " +
            "AND abortedAt IS NULL AND completedAt IS NOT NULL AND completedAt >= :since AND completedAt < :until"
    )
    suspend fun completedFocusCount(since: Long, until: Long): Int

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun findById(id: String): FocusSessionEntity?

    @Insert
    suspend fun insert(session: FocusSessionEntity)

    @Update
    suspend fun update(session: FocusSessionEntity)
}
