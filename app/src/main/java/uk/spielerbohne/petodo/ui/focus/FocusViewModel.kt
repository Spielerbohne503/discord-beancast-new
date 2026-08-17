package uk.spielerbohne.petodo.ui.focus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.spielerbohne.petodo.data.focus.FocusAction
import uk.spielerbohne.petodo.data.focus.FocusService
import uk.spielerbohne.petodo.data.repo.FocusRepository
import uk.spielerbohne.petodo.data.repo.TaskRepository
import uk.spielerbohne.petodo.data.settings.SettingsRepository
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.focus.FocusSettings
import uk.spielerbohne.petodo.domain.focus.FocusState
import uk.spielerbohne.petodo.domain.focus.FocusTimer
import uk.spielerbohne.petodo.domain.model.Task
import java.time.Clock
import java.time.Instant

data class FocusUiState(
    val state: FocusState = FocusState.Ready,
    val settings: FocusSettings = FocusSettings.DEFAULT,
    val completedRoundsToday: Int = 0,
    val linkedTask: Task? = null,
    val openTasks: List<Task> = emptyList(),
)

/**
 * Der Fokus-Screen liest denselben Zustand wie die Statuszeile: gespeicherter
 * Endzeitpunkt plus [FocusTimer]. Es gibt keinen zweiten Zähler, der auseinanderlaufen
 * könnte.
 */
class FocusViewModel(
    private val context: Context,
    private val focusRepository: FocusRepository,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val now = MutableStateFlow(Instant.now(clock))

    init {
        viewModelScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                now.value = Instant.now(clock)
            }
        }
    }

    val state: StateFlow<FocusUiState> = combine(
        focusRepository.observeActiveSession(),
        focusRepository.observeCompletedRoundsToday(),
        settingsRepository.focusSettings,
        taskRepository.observeTasks(),
        now,
    ) { session, rounds, settings, tasks, instant ->
        FocusUiState(
            state = FocusTimer.stateOf(session, instant),
            settings = settings,
            completedRoundsToday = rounds,
            linkedTask = session?.taskId?.let { id -> tasks.firstOrNull { it.id == id } },
            openTasks = tasks.filter { it.isOpen && !it.isSubtask }.take(MAX_TASK_CHOICES),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = FocusUiState(),
    )

    /** Alle Befehle laufen über den Dienst — er ist der einzige Besitzer der Statuszeile. */
    fun startFocus(taskId: String?) = FocusService.send(context, FocusAction.START_FOCUS, taskId)

    fun pause() = FocusService.send(context, FocusAction.PAUSE)

    fun resume() = FocusService.send(context, FocusAction.RESUME)

    fun stop() = FocusService.send(context, FocusAction.STOP)

    fun skipBreak() = FocusService.send(context, FocusAction.SKIP)

    fun setSettings(settings: FocusSettings) {
        viewModelScope.launch { settingsRepository.setFocusSettings(settings) }
    }

    companion object {
        private const val TICK_MILLIS = 1_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val MAX_TASK_CHOICES = 20

        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    FocusViewModel(
                        context = context.applicationContext,
                        focusRepository = container.focusRepository,
                        taskRepository = container.taskRepository,
                        settingsRepository = container.settingsRepository,
                        clock = container.clock,
                    )
                }
            }
    }
}
