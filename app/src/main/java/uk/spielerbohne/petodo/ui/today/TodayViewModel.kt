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
import uk.spielerbohne.petodo.data.repo.TagRepository
import uk.spielerbohne.petodo.data.repo.TaskListRepository
import uk.spielerbohne.petodo.data.repo.TaskRepository
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.model.Priority
import uk.spielerbohne.petodo.domain.model.SubtaskProgress
import uk.spielerbohne.petodo.domain.model.Tag
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
    val listColors: Map<String, Int> = emptyMap(),
    val tagsByTask: Map<String, List<Tag>> = emptyMap(),
    val subtaskProgress: Map<String, SubtaskProgress> = emptyMap(),
    val lastDeletedTaskId: String? = null,
    val lastPostponedCount: Int? = null,
)

/**
 * Hält nur den Zustand zusammen; die Einteilung in die drei Blöcke macht die reine
 * Funktion [TodayGrouping.group] in `domain/`.
 */
class TodayViewModel(
    private val repository: TaskRepository,
    private val taskListRepository: TaskListRepository,
    private val tagRepository: TagRepository,
    private val nagCoordinator: NagCoordinator,
    private val clock: Clock,
) : ViewModel() {

    private val now = MutableStateFlow(Instant.now(clock))
    // Kommt aus dem Repository: Gelöscht wird auch auf der Detailseite, und die
    // Rückgängig-Leiste erscheint hier.
    private val lastDeleted = repository.lastDeleted
    private val lastPostponed = MutableStateFlow<Int?>(null)

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

    private val signals = combine(lastDeleted, lastPostponed) { deleted, postponed ->
        deleted to postponed
    }

    val state: StateFlow<TodayUiState> = combine(
        repository.observeTasks(),
        taskListRepository.observeLists(),
        tagRepository.observeTagsByTask(),
        now,
        signals,
    ) { tasks, lists, tagsByTask, instant, (deletedId, postponedCount) ->
        val subtasksByParent = tasks.filter { it.isSubtask && !it.isDeleted }.groupBy { it.parentId }

        TodayUiState(
            board = TodayGrouping.group(tasks, instant, clock.zone),
            now = instant,
            zone = clock.zone,
            listColors = lists.mapNotNull { list -> list.colorArgb?.let { list.id to it } }.toMap(),
            tagsByTask = tagsByTask,
            subtaskProgress = subtasksByParent
                .mapValues { (_, subtasks) -> SubtaskProgress.of(subtasks) }
                .filterKeys { it != null }
                .mapKeys { (parentId, _) -> parentId!! },
            lastDeletedTaskId = deletedId,
            lastPostponedCount = postponedCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TodayUiState(now = Instant.now(clock), zone = clock.zone),
    )

    fun refreshNow() {
        now.value = Instant.now(clock)
    }

    fun addTask(title: String, dueDate: LocalDate?, dueTime: LocalTime?, priority: Int = Priority.DEFAULT) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val id = repository.createTask(
                title = title,
                dueDate = dueDate,
                dueTime = dueTime,
                priority = priority,
            )
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

    fun undoDelete() {
        val id = lastDeleted.value ?: return
        viewModelScope.launch {
            repository.restore(id)
            syncAlarm(id)
        }
    }

    fun clearUndo() {
        repository.clearLastDeleted()
    }

    /**
     * Den ganzen Überfällig-Block auf morgen schieben.
     *
     * Das ist bewusst eine Handlung, keine Verdrängung: Aufräumen ist im Projektplan
     * genauso viel wert wie Erledigen.
     */
    fun postponeOverdue() {
        viewModelScope.launch {
            val ids = state.value.board.overdue.map { it.id }
            if (ids.isEmpty()) return@launch
            val moved = repository.postponeAllToTomorrow(ids)
            withContext(Dispatchers.IO) { ids.forEach { nagCoordinator.syncTask(it) } }
            lastPostponed.value = moved
        }
    }

    /**
     * Eine einzelne Aufgabe auf morgen schieben — die Wischgeste nach links.
     *
     * Zählt für das Pet wie Aufräumen, wenn die Aufgabe überfällig war; das entscheidet
     * das Repository, nicht dieser Aufruf.
     */
    fun postpone(task: Task) {
        viewModelScope.launch {
            repository.postponeToTomorrow(task.id)
            syncAlarm(task.id)
            lastPostponed.value = 1
        }
    }

    fun clearPostponed() {
        lastPostponed.value = null
    }

    private suspend fun syncAlarm(taskId: String) {
        withContext(Dispatchers.IO) { nagCoordinator.syncTask(taskId) }
    }

    companion object {
        private const val TICK_MILLIS = 60_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TodayViewModel(
                    repository = container.taskRepository,
                    taskListRepository = container.taskListRepository,
                    tagRepository = container.tagRepository,
                    nagCoordinator = container.nagCoordinator,
                    clock = container.clock,
                )
            }
        }
    }
}
