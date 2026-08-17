package uk.spielerbohne.petodo.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.ui.common.PriorityUi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun TodayRoute(
    container: AppContainer,
    onOpenTask: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val viewModel: TodayViewModel = viewModel(factory = TodayViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    TodayScreen(
        state = state,
        onToggle = viewModel::toggleCompleted,
        onAdd = viewModel::addTask,
        onOpenTask = onOpenTask,
        onDelete = viewModel::deleteTask,
        onUndoDelete = viewModel::undoDelete,
        onUndoConsumed = viewModel::clearUndo,
        onPostponeOverdue = viewModel::postponeOverdue,
        onPostponeConsumed = viewModel::clearPostponed,
        onSearch = onSearch,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onToggle: (Task) -> Unit,
    onAdd: (String, LocalDate?, LocalTime?, Int) -> Unit,
    onOpenTask: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUndoDelete: () -> Unit,
    onUndoConsumed: () -> Unit,
    onPostponeOverdue: () -> Unit,
    onPostponeConsumed: () -> Unit,
    onSearch: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.task_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(state.lastDeletedTaskId) {
        val id = state.lastDeletedTaskId ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = deletedMessage, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) onUndoDelete() else onUndoConsumed()
    }

    val postponedMessage = state.lastPostponedCount?.let { count ->
        androidx.compose.ui.platform.LocalContext.current.resources
            .getQuantityString(R.plurals.overdue_postponed, count, count)
    }
    LaunchedEffect(state.lastPostponedCount) {
        postponedMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(postponedMessage)
        onPostponeConsumed()
    }

    val postponeLabel = stringResource(R.string.overdue_postpone_all)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.browse_search),
                        )
                    }
                },
            )
        },
        bottomBar = { QuickAddBar(onAdd = onAdd) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val board = state.board
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (board.isEmpty) {
                item {
                    Text(
                        text = stringResource(R.string.today_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            taskSection(
                titleRes = R.string.section_overdue,
                tasks = board.overdue,
                state = state,
                emphasize = true,
                // "Verschieben" räumt den ganzen Block auf einmal auf — die Fluchttür
                // aus einer schlechten Woche.
                bulkAction = if (board.overdue.isNotEmpty()) postponeLabel to onPostponeOverdue else null,
                onToggle = onToggle,
                onOpen = onOpenTask,
            )
            taskSection(R.string.section_today, board.today, state, onToggle = onToggle, onOpen = onOpenTask)
            taskSection(R.string.section_later, board.later, state, onToggle = onToggle, onOpen = onOpenTask)
            taskSection(R.string.section_done_today, board.doneToday, state, onToggle = onToggle, onOpen = onOpenTask)
        }
    }
}

private fun LazyListScope.taskSection(
    titleRes: Int,
    tasks: List<Task>,
    state: TodayUiState,
    emphasize: Boolean = false,
    bulkAction: Pair<String, () -> Unit>? = null,
    onToggle: (Task) -> Unit,
    onOpen: (String) -> Unit,
) {
    if (tasks.isEmpty()) return

    item(key = "header-$titleRes") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (emphasize) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                bulkAction?.let { (label, action) ->
                    TextButton(onClick = action) { Text(label) }
                }
                Text(
                    text = tasks.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    items(tasks, key = { it.id }) { task ->
        TaskRow(
            task = task,
            state = state,
            onToggle = { onToggle(task) },
            onOpen = { onOpen(task.id) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun TaskRow(
    task: Task,
    state: TodayUiState,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val zone = state.zone
    val now = state.now
    val due = task.dueLabel(now, zone)
    val overdue = task.overdueLabel(now, zone)
    val listColor = state.listColors[task.listId]
    val progress = state.subtaskProgress[task.id]
    val tags = state.tagsByTask[task.id].orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Farbstreifen der Liste — der schnellste Weg zu sehen, wohin etwas gehört.
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .background(
                    color = listColor?.let(::Color) ?: Color.Transparent,
                    shape = RoundedCornerShape(2.dp),
                )
        )
        // Abhaken per Tippen auf die Zeile, Öffnen über den Titelbereich.
        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.task_open_details), onClick = onOpen)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )

            val details = buildList {
                (overdue ?: due)?.let(::add)
                if (progress != null && progress.hasSubtasks) {
                    add(stringResource(R.string.detail_subtask_progress, progress.done, progress.total))
                }
                tags.forEach { add("#${it.name}") }
                task.note?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.task_note_indicator)) }
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (task.rrule != null) {
            Icon(
                imageVector = Icons.Filled.Repeat,
                contentDescription = stringResource(R.string.recurrence_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        if (PriorityUi.hasVisibleFlag(task.priority)) {
            Icon(
                imageVector = Icons.Filled.Flag,
                contentDescription = PriorityUi.label(task.priority),
                tint = PriorityUi.color(task.priority),
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

/** Schnell-Eingabe unten: Titel, Fälligkeit, Priorität — die drei Angaben mit Tagesnutzen. */
@Composable
private fun QuickAddBar(onAdd: (String, LocalDate?, LocalTime?, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var dueTime by remember { mutableStateOf<LocalTime?>(null) }
    var priority by remember { mutableStateOf(uk.spielerbohne.petodo.domain.model.Priority.DEFAULT) }

    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
                IconButton(
                    onClick = {
                        onAdd(title, dueDate, dueTime, priority)
                        title = ""
                        dueDate = null
                        dueTime = null
                        priority = uk.spielerbohne.petodo.domain.model.Priority.DEFAULT
                    },
                    enabled = title.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.quick_add_submit),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DuePicker(
                    dueDate = dueDate,
                    dueTime = dueTime,
                    onDueDateChange = { dueDate = it },
                    onDueTimeChange = { dueTime = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 4.dp),
                )
                uk.spielerbohne.petodo.ui.common.PriorityPicker(
                    priority = priority,
                    onPriorityChange = { priority = it },
                )
            }
        }
    }
}
