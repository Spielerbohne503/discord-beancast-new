package uk.spielerbohne.petodo.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Datenbankmigrationen.
 *
 * Nie `fallbackToDestructiveMigration`: Ein Schemafehler darf Aufgaben nicht löschen.
 * Wer eine Migration schreibt, prüft sie gegen die exportierten Schemadateien unter
 * `app/schemas` — `tools/verify_migrations.py` macht genau das mit sqlite3.
 */
object Migrations {

    /**
     * v1 → v2: Felder, die der Projektplan als "im Schema vorbereitet" führt, aber in
     * Abschnitt 4 nicht enthielt — Unteraufgaben, Ordner, Tags — plus die Tabelle für
     * mehrere Erinnerungszeitpunkte pro Aufgabe.
     *
     * Reines Hinzufügen: keine Spalte wird umbenannt, keine Zeile angefasst.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `parentId` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_parentId` ON `tasks` (`parentId`)")

            db.execSQL("ALTER TABLE `task_lists` ADD COLUMN `parentId` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_lists_parentId` ON `task_lists` (`parentId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tags` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `colorArgb` INTEGER,
                    `sortKey` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_sortKey` ON `tags` (`sortKey`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `task_tags` (
                    `taskId` TEXT NOT NULL,
                    `tagId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`taskId`, `tagId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tags_tagId` ON `task_tags` (`tagId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reminders` (
                    `id` TEXT NOT NULL,
                    `taskId` TEXT NOT NULL,
                    `offsetMinutes` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_taskId` ON `reminders` (`taskId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_deletedAt` ON `reminders` (`deletedAt`)")
        }
    }

    /**
     * v2 → v3: Zähler für übersprungene Termine einer Wiederholung.
     *
     * NOT NULL mit Vorgabe 0, damit bestehende Zeilen ohne Nacharbeit gültig bleiben.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `missedCount` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v3 → v4: Anhalten des Fokus-Timers. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `pausedAt` INTEGER")
        }
    }

    /**
     * v4 → v5: Gewohnheiten (Projektplan, v2.0).
     *
     * Zwei neue Tabellen, keine bestehende wird angefasst. Der Tag eines Hakens ist ein
     * **Epochentag** und kein Zeitstempel: Eine Gewohnheit gehört zu einem Kalendertag,
     * und mit einem Zeitpunkt würde ein Zeitzonenwechsel Haken auf den Vortag schieben.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `habits` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `scheduleMask` INTEGER NOT NULL,
                    `colorArgb` INTEGER,
                    `sortKey` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_habits_sortKey` ON `habits` (`sortKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_habits_deletedAt` ON `habits` (`deletedAt`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `habit_checkins` (
                    `id` TEXT NOT NULL,
                    `habitId` TEXT NOT NULL,
                    `day` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_checkins_habitId_day` " +
                    "ON `habit_checkins` (`habitId`, `day`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_habit_checkins_habitId` ON `habit_checkins` (`habitId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_habit_checkins_deletedAt` ON `habit_checkins` (`deletedAt`)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
