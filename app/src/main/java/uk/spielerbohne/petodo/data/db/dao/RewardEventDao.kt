package uk.spielerbohne.petodo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.spielerbohne.petodo.data.db.entity.RewardEventEntity

/**
 * Append-only: bewusst ohne `@Update` und ohne `@Delete`. Wer hier ein UPDATE ergänzt,
 * hebelt die Einbahnstraße aus Abschnitt 4.3 des Projektplans aus.
 */
@Dao
interface RewardEventDao {

    @Insert
    suspend fun insert(event: RewardEventEntity)

    /**
     * Ereignisse **nach** einem Zeitpunkt — echt größer, nicht größer-gleich.
     *
     * Ein Ereignis genau auf dem letzten Rechenzeitpunkt wurde dort bereits verrechnet;
     * mit `>=` käme es ein zweites Mal an. Frisch eingefügte Ereignisse reicht das
     * Repository stattdessen direkt herein.
     */
    @Query("SELECT * FROM reward_events WHERE deletedAt IS NULL AND at > :since ORDER BY at")
    suspend fun since(since: Long): List<RewardEventEntity>

    @Query("SELECT * FROM reward_events WHERE deletedAt IS NULL ORDER BY at DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<RewardEventEntity>>

    @Query("SELECT COUNT(*) FROM reward_events WHERE deletedAt IS NULL AND type = :type AND at >= :since")
    suspend fun countSince(type: String, since: Long): Int
}
