package uk.spielerbohne.petodo.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.spielerbohne.petodo.data.alarm.NagCoordinator
import uk.spielerbohne.petodo.data.repo.TaskRepository
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.domain.today.TodayBoard
import uk.spielerbohne.petodo.domain.today.TodayGrouping
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class TodayUiState(
    val board: TodayBoard = TodayBoard(),
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneId.systemDefault(),
    val lastDeletedTaskId: String? = null,
)

/**
 * Hält nur den Zustand zusammen; die Einteilung in die drei Blöcke macht die reine
 * Funktion [TodayGrouping.group] in `domain/`.
 */
class TodayViewModel(
    private val repository: TaskRepository,
    private val nagCoordinator: NagCoordinator,
    private val clock: Clock,
) : ViewModel() {

    private val now = MutableStateFlow(Instant.now(clock))
    private val lastDeleted = MutableStateFlow<String?>(null)

    init {
        // Minütlich neu einordnen, damit eine Aufgabe zur Fälligkeit von "heute" nach
        // "überfällig" wandert, ohne dass man den Screen verlassen muss.
        viewModelScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                now.value = Instant.now(clock)
            }
        }
    }

    val state: StateFlow<TodayUiState> =
        combine(repository.observeTasks(), now, lastDeleted) { tasks, instant, deletedId ->
            TodayUiState(
                board = TodayGrouping.group(tasks, instant, clock.zone),
                now = instant,
                zone = clock.zone,
                lastDeletedTaskId = deletedId,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TodayUiState(now = Instant.now(clock), zone = clock.zone),
        )

    fun refreshNow() {
        now.value = Instant.now(clock)
    }

    fun addTask(title: String, dueDate: LocalDate?, dueTime: LocalTime?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val id = repository.createTask(title = title, dueDate = dueDate, dueTime = dueTime)
            syncAlarm(id)
        }
    }

    /**
     * Abhaken nimmt die Benachrichtigung sofort zurück — wieder öffnen setzt den Alarm
     * neu.
     */
    fun toggleCompleted(task: Task) {
        viewModelScope.launch {
            val completed = !task.isCompleted
            repository.setCompleted(task.id, completed = completed)
            withContext(Dispatchers.IO) {
                if (completed) {
                    nagCoordinator.onTaskCompleted(task.id)
                } else {
                    nagCoordinator.syncTask(task.id)
                }
            }
        }
    }

    fun saveTask(id: String, title: String, note: String?, dueDate: LocalDate?, dueTime: LocalTime?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.updateTask(id, title, note, dueDate, dueTime)
            syncAlarm(id)
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            lastDeleted.value = id
            withContext(Dispatchers.IO) { nagCoordinator.onTaskCompleted(id) }
        }
    }

    fun undoDelete() {
        val id = lastDeleted.value ?: return
        viewModelScope.launch {
            repository.restore(id)
            lastDeleted.value = null
            syncAlarm(id)
        }
    }

    private suspend fun syncAlarm(taskId: String) {
        withContext(Dispatchers.IO) { nagCoordinator.syncTask(taskId) }
    }

    fun clearUndo() {
        lastDeleted.value = null
    }

    companion object {
        private const val TICK_MILLIS = 60_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TodayViewModel(
                    repository = container.taskRepository,
                    nagCoordinator = container.nagCoordinator,
                    clock = container.clock,
                )
            }
        }
    }
}
