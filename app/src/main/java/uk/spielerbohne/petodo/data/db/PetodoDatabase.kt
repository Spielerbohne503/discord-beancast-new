package uk.spielerbohne.petodo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import uk.spielerbohne.petodo.data.db.dao.FocusSessionDao
import uk.spielerbohne.petodo.data.db.dao.PetStateDao
import uk.spielerbohne.petodo.data.db.dao.RewardEventDao
import uk.spielerbohne.petodo.data.db.dao.TaskDao
import uk.spielerbohne.petodo.data.db.dao.TaskListDao
import uk.spielerbohne.petodo.data.db.entity.FocusSessionEntity
import uk.spielerbohne.petodo.data.db.entity.PetStateEntity
import uk.spielerbohne.petodo.data.db.entity.RewardEventEntity
import uk.spielerbohne.petodo.data.db.entity.TaskEntity
import uk.spielerbohne.petodo.data.db.entity.TaskListEntity

@Database(
    entities = [
        TaskEntity::class,
        TaskListEntity::class,
        RewardEventEntity::class,
        PetStateEntity::class,
        FocusSessionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PetodoDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
    abstract fun rewardEventDao(): RewardEventDao
    abstract fun petStateDao(): PetStateDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        const val NAME = "petodo.db"

        fun build(context: Context, nowMillis: () -> Long): PetodoDatabase =
            Room.databaseBuilder(context, PetodoDatabase::class.java, NAME)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Beim allerersten Start entstehen die beiden Listen in derselben
                        // Transaktion wie das Schema — danach kann die App nie ohne Inbox
                        // dastehen.
                        SeedData.seedSql(nowMillis()).forEach(db::execSQL)
                    }
                })
                .build()
    }
}
