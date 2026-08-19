package uk.spielerbohne.petodo.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
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
import uk.spielerbohne.petodo.domain.model.SubtaskProgress
import uk.spielerbohne.petodo.domain.model.Tag
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.domain.model.TaskList
import uk.spielerbohne.petodo.domain.recurrence.RecurrenceRule
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId

data class TaskDetailUiState(
    val task: Task? = null,
    val subtasks: List<Task> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Kommt aus der Uhr des Containers — der Bildschirm liest nie die Systemuhr. */
    val now: Instant = Instant.EPOCH,
    val gone: Boolean = false,
) {
    val progress: SubtaskProgress get() = SubtaskProgress.of(subtasks)
    val list: TaskList? get() = lists.firstOrNull { it.id == task?.listId }
}

class TaskDetailViewModel(
    private val taskId: String,
    private val taskRepository: TaskRepository,
    private val taskListRepository: TaskListRepository,
    private val tagRepository: TagRepository,
    private val nagCoordinator: NagCoordinator,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<TaskDetailUiState> = combine(
        taskRepository.observeTask(taskId),
        taskRepository.observeSubtasks(taskId),
        tagRepository.observeTagsByTask(),
        taskListRepository.observeLists(),
    ) { task, subtasks, tagsByTask, lists ->
        TaskDetailUiState(
            task = task?.takeIf { !it.isDeleted },
            subtasks = subtasks,
            tags = tagsByTask[taskId].orEmpty(),
            lists = lists,
            zone = clock.zone,
            now = Instant.now(clock),
            // Gelöscht oder verschwunden: der Screen schließt sich, statt leer dazustehen.
            gone = task == null || task.isDeleted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TaskDetailUiState(zone = clock.zone, now = Instant.now(clock)),
    )

    fun setTitle(title: String) = edit { task ->
        taskRepository.updateTask(
            id = task.id,
            title = title,
            note = task.note,
            dueDate = task.dueDate(clock.zone),
            dueTime = if (task.hasTime) task.dueAt?.atZone(clock.zone)?.toLocalTime() else null,
        )
    }

    fun setNote(note: String) = edit { task ->
        taskRepository.updateTask(
            id = task.id,
            title = task.title,
            note = note,
            dueDate = task.dueDate(clock.zone),
            dueTime = if (task.hasTime) task.dueAt?.atZone(clock.zone)?.toLocalTime() else null,
        )
    }

    fun setDue(date: LocalDate?, time: LocalTime?) = edit { task ->
        taskRepository.updateTask(
            id = task.id,
            title = task.title,
            note = task.note,
            dueDate = date,
            dueTime = time,
        )
    }

    fun setPriority(priority: Int) = edit { taskRepository.setPriority(it.id, priority) }

    fun setRecurrence(rule: RecurrenceRule?) = edit { taskRepository.setRecurrence(it.id, rule) }

    fun moveToList(listId: String) = edit { taskRepository.moveToList(it.id, listId) }

    fun toggleCompleted() = edit { task ->
        val completed = !task.isCompleted
        taskRepository.setCompleted(task.id, completed)
        withContext(Dispatchers.IO) {
            if (completed) nagCoordinator.onTaskCompleted(task.id) else nagCoordinator.syncTask(task.id)
        }
    }

    fun delete() {
        viewModelScope.launch {
            taskRepository.delete(taskId)
            withContext(Dispatchers.IO) { nagCoordinator.onTaskCompleted(taskId) }
        }
    }

    // --------------------------------------------------------------------- Unteraufgaben

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val parent = taskRepository.findTask(taskId) ?: return@launch
            taskRepository.createTask(
                title = title,
                listId = parent.listId,
                parentId = taskId,
            )
        }
    }

    fun toggleSubtask(subtask: Task) {
        viewModelScope.launch {
            taskRepository.setCompleted(subtask.id, completed = !subtask.isCompleted)
        }
    }

    fun deleteSubtask(subtaskId: String) {
        viewModelScope.launch { taskRepository.delete(subtaskId) }
    }

    // -------------------------------------------------------------------------- Etiketten

    fun addTag(name: String) {
        viewModelScope.launch { tagRepository.addTagByName(taskId, name) }
    }

    fun removeTag(tagId: String) {
        viewModelScope.launch { tagRepository.unassign(taskId, tagId) }
    }

    /**
     * Jede Änderung geht durch dieselbe Schleuse: erst schreiben, dann den Alarm an den
     * neuen Stand angleichen. So kann eine verschobene Fälligkeit den Nag nicht vergessen.
     */
    private fun edit(block: suspend (Task) -> Unit) {
        viewModelScope.launch {
            val task = taskRepository.findTask(taskId) ?: return@launch
            block(task)
            withContext(Dispatchers.IO) { nagCoordinator.syncTask(taskId) }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer, taskId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TaskDetailViewModel(
                        taskId = taskId,
                        taskRepository = container.taskRepository,
                        taskListRepository = container.taskListRepository,
                        tagRepository = container.tagRepository,
                        nagCoordinator = container.nagCoordinator,
                        clock = container.clock,
                    )
                }
            }
    }
}
