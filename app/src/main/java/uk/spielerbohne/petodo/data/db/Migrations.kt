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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
