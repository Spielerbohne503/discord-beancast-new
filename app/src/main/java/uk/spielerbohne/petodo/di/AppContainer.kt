package uk.spielerbohne.petodo.di

import android.content.Context
import uk.spielerbohne.petodo.data.db.PetodoDatabase
import uk.spielerbohne.petodo.data.repo.TaskRepository
import java.time.Clock

/**
 * Manuelle Dependency Injection. Kein Hilt — bei einem Modul und einer Handvoll
 * Abhängigkeiten ist ein Container aus lazy-Feldern ehrlicher als ein Framework.
 *
 * Die Uhr liegt hier, damit Zeit nirgends direkt aus `System.currentTimeMillis()` gelesen
 * wird: Wer sie braucht, bekommt sie hereingereicht.
 */
class AppContainer(context: Context, val clock: Clock = Clock.systemDefaultZone()) {

    private val appContext: Context = context.applicationContext

    val database: PetodoDatabase by lazy {
        PetodoDatabase.build(appContext) { clock.millis() }
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(
            taskDao = database.taskDao(),
            taskListDao = database.taskListDao(),
            clock = clock,
        )
    }
}
