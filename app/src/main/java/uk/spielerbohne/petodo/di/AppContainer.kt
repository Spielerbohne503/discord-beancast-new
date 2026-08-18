package uk.spielerbohne.petodo.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import uk.spielerbohne.petodo.data.alarm.AlarmScheduler
import uk.spielerbohne.petodo.data.alarm.NagCoordinator
import uk.spielerbohne.petodo.data.backup.BackupRepository
import uk.spielerbohne.petodo.data.db.PetodoDatabase
import uk.spielerbohne.petodo.data.notify.NagNotifications
import uk.spielerbohne.petodo.data.pet.PetRepository
import uk.spielerbohne.petodo.data.repo.FocusRepository
import uk.spielerbohne.petodo.data.repo.TagRepository
import uk.spielerbohne.petodo.data.repo.TaskListRepository
import uk.spielerbohne.petodo.data.repo.TaskRepository
import uk.spielerbohne.petodo.data.settings.SettingsRepository
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

    val contentResolver: android.content.ContentResolver get() = appContext.contentResolver

    /**
     * Lebt so lange wie der Prozess. Receiver benutzen ihn, damit ihre Arbeit nicht
     * abbricht, sobald `onReceive` zurückkehrt.
     */
    val applicationScope = CoroutineScope(SupervisorJob())

    val database: PetodoDatabase by lazy {
        PetodoDatabase.build(appContext) { clock.millis() }
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(
            taskDao = database.taskDao(),
            taskListDao = database.taskListDao(),
            clock = clock,
            // Faul: Das Pet braucht die Aufgaben, die Aufgaben nicht das Pet.
            rewards = { petRepository },
        )
    }

    val taskListRepository: TaskListRepository by lazy {
        TaskListRepository(database.taskListDao(), clock)
    }

    val tagRepository: TagRepository by lazy { TagRepository(database.tagDao(), clock) }

    val focusRepository: FocusRepository by lazy {
        FocusRepository(database.focusSessionDao(), clock, rewards = { petRepository })
    }

    /**
     * Belohnungen werden im Repository verbucht, nicht im ViewModel — sonst zahlt der
     * Weg über die Benachrichtigung nicht ein.
     */
    val petRepository: PetRepository by lazy {
        PetRepository(
            petStateDao = database.petStateDao(),
            rewardEventDao = database.rewardEventDao(),
            taskRepository = taskRepository,
            clock = clock,
        )
    }

    val backupRepository: BackupRepository by lazy { BackupRepository(database, clock) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(appContext) }

    val nagNotifications: NagNotifications by lazy { NagNotifications(appContext) }

    val nagCoordinator: NagCoordinator by lazy {
        NagCoordinator(
            taskRepository = taskRepository,
            settingsRepository = settingsRepository,
            scheduler = alarmScheduler,
            notifications = nagNotifications,
            clock = clock,
        )
    }
}
