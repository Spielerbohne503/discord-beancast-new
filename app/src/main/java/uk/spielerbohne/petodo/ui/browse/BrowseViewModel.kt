package uk.spielerbohne.petodo.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.spielerbohne.petodo.data.alarm.NagCoordinator
import uk.spielerbohne.petodo.data.repo.TaskListRepository
import uk.spielerbohne.petodo.data.repo.TaskRepository
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.filter.TaskFilter
import uk.spielerbohne.petodo.domain.filter.TaskScope
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.domain.model.TaskList
import uk.spielerbohne.petodo.domain.sort.Reorder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

data class BrowseUiState(
    val scope: TaskScope = TaskScope.AllOpen,
    val query: String = "",
    val tasks: List<Task> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val listColors: Map<String, Int> = emptyMap(),
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Nur in einer konkreten Liste und ohne Suche lässt sich von Hand sortieren. */
    val manuallyOrdered: Boolean = false,
) {
    val currentList: TaskList?
        get() = (scope as? TaskScope.InList)?.let { inList -> lists.firstOrNull { it.id == inList.listId } }
}

/**
 * Listen, Smart Lists und Suche — alles dieselbe Ansicht über demselben Bestand.
 *
 * Die Auswahl trifft die reine Funktion [TaskFilter.apply] in `domain/`; hier wird nur
 * eingesammelt, was sie braucht.
 */
class BrowseViewModel(
    initialScope: TaskScope,
    private val repository: TaskRepository,
    private val taskListRepository: TaskListRepository,
    private val nagCoordinator: NagCoordinator,
    private val clock: Clock,
) : ViewModel() {

    private val scope = MutableStateFlow(initialScope)
    private val query = MutableStateFlow("")

    /** Reihenfolge während eines Ziehens — erst beim Loslassen wird gespeichert. */
    private val dragOrder = MutableStateFlow<List<Task>?>(null)
    private var dragOriginalIds: List<String>? = null
    private var dragFrom: Int? = null
    private var dragTaskId: String? = null

    val state: StateFlow<BrowseUiState> = combine(
        repository.observeTasks(),
        taskListRepository.observeLists(),
        scope,
        query,
        dragOrder,
    ) { tasks, lists, currentScope, currentQuery, pending ->
        val now = Instant.now(clock)
        @Suppress("UNCHECKED_CAST")
        BrowseUiState(
            scope = currentScope,
            query = currentQuery,
            tasks = (pending as? List<Task>)
                ?: TaskFilter.apply(tasks, currentScope, currentQuery, now, clock.zone),
            lists = lists,
            listColors = lists.mapNotNull { list -> list.colorArgb?.let { list.id to it } }.toMap(),
            now = now,
            zone = clock.zone,
            manuallyOrdered = TaskFilter.isManuallyOrdered(currentScope, currentQuery),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        // Mit dem Vorgabewert Instant.EPOCH stünde im ersten Bild an jeder Aufgabe
        // "20000 Tage überfällig".
        initialValue = BrowseUiState(
            scope = initialScope,
            zone = clock.zone,
            now = Instant.now(clock),
        ),
    )

    fun setScope(newScope: TaskScope) {
        cancelDrag()
        scope.value = newScope
    }

    // ------------------------------------------------------------------- Umsortieren

    /**
     * Ein Zug während des Ziehens. Die Liste ordnet sich sofort neu, gespeichert wird
     * erst beim Loslassen — ein abgebrochener Zug hinterlässt nichts.
     */
    fun onDragMove(from: Int, to: Int) {
        val current = dragOrder.value ?: state.value.tasks
        if (dragFrom == null) {
            dragOriginalIds = current.map { it.id }
            dragFrom = from
            dragTaskId = current.getOrNull(from)?.id
        }
        dragOrder.value = Reorder.move(current, from, to)
    }

    fun onDragDrop() {
        val originalIds = dragOriginalIds
        val from = dragFrom
        val movedId = dragTaskId
        val finalOrder = dragOrder.value

        cancelDrag()

        if (originalIds == null || from == null || movedId == null || finalOrder == null) return
        val to = finalOrder.indexOfFirst { it.id == movedId }
        if (to < 0) return

        viewModelScope.launch { repository.moveTask(originalIds, from, to) }
    }

    fun cancelDrag() {
        dragOrder.value = null
        dragOriginalIds = null
        dragFrom = null
        dragTaskId = null
    }

    fun setQuery(text: String) {
        cancelDrag()
        query.value = text
    }

    fun toggleCompleted(task: Task) {
        viewModelScope.launch {
            val completed = !task.isCompleted
            repository.setCompleted(task.id, completed = completed)
            withContext(Dispatchers.IO) {
                if (completed) nagCoordinator.onTaskCompleted(task.id) else nagCoordinator.syncTask(task.id)
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer, initialScope: TaskScope): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    BrowseViewModel(
                        initialScope = initialScope,
                        repository = container.taskRepository,
                        taskListRepository = container.taskListRepository,
                        nagCoordinator = container.nagCoordinator,
                        clock = container.clock,
                    )
                }
            }
    }
}
