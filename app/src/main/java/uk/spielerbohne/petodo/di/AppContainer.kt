package uk.spielerbohne.petodo.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import uk.spielerbohne.petodo.data.alarm.AlarmScheduler
import uk.spielerbohne.petodo.data.alarm.NagCoordinator
import uk.spielerbohne.petodo.data.db.PetodoDatabase
import uk.spielerbohne.petodo.data.notify.NagNotifications
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
        )
    }

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
