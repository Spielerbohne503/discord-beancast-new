package uk.spielerbohne.petodo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.spielerbohne.petodo.data.db.entity.HabitCheckinEntity
import uk.spielerbohne.petodo.data.db.entity.HabitEntity

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY sortKey")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY sortKey")
    suspend fun habits(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun findHabit(id: String): HabitEntity?

    @Query("SELECT sortKey FROM habits WHERE deletedAt IS NULL ORDER BY sortKey DESC LIMIT 1")
    suspend fun highestSortKey(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    // ------------------------------------------------------------------- Einträge

    /**
     * Nur die Einträge ab einem Tag — der Rückblick braucht nicht die ganze Geschichte.
     * Der Tag ist ein Epochentag, kein Zeitstempel.
     */
    @Query("SELECT * FROM habit_checkins WHERE deletedAt IS NULL AND day >= :sinceDay")
    fun observeCheckins(sinceDay: Long): Flow<List<HabitCheckinEntity>>

    @Query("SELECT * FROM habit_checkins WHERE habitId = :habitId AND day = :day")
    suspend fun findCheckin(habitId: String, day: Long): HabitCheckinEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckin(checkin: HabitCheckinEntity)

    @Update
    suspend fun updateCheckin(checkin: HabitCheckinEntity)
}
