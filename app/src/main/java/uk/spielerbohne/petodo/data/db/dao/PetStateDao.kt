package uk.spielerbohne.petodo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.spielerbohne.petodo.data.db.entity.PetStateEntity

@Dao
interface PetStateDao {

    @Query("SELECT * FROM pet_state WHERE id = :id")
    fun observe(id: Int = PetStateEntity.SINGLETON_ID): Flow<PetStateEntity?>

    @Query("SELECT * FROM pet_state WHERE id = :id")
    suspend fun get(id: Int = PetStateEntity.SINGLETON_ID): PetStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PetStateEntity)

    @Update
    suspend fun update(state: PetStateEntity)
}
