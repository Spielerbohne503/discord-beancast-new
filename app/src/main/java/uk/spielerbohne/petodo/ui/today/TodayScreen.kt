package uk.spielerbohne.petodo.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun TodayRoute(container: AppContainer) {
    val viewModel: TodayViewModel = viewModel(factory = TodayViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    TodayScreen(
        state = state,
        onToggle = viewModel::toggleCompleted,
        onAdd = viewModel::addTask,
        onSave = viewModel::saveTask,
        onDelete = viewModel::deleteTask,
        onUndoDelete = viewModel::undoDelete,
        onUndoConsumed = viewModel::clearUndo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onToggle: (Task) -> Unit,
    onAdd: (String, LocalDate?, LocalTime?) -> Unit,
    onSave: (String, String, String?, LocalDate?, LocalTime?) -> Unit,
    onDelete: (String) -> Unit,
    onUndoDelete: () -> Unit,
    onUndoConsumed: () -> Unit,
) {
    var editing by remember { mutableStateOf<Task?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.task_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(state.lastDeletedTaskId) {
        val id = state.lastDeletedTaskId ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = deletedMessage, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) onUndoDelete() else onUndoConsumed()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.today_title)) }) },
        bottomBar = {
            QuickAddBar(onAdd = onAdd)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val board = state.board
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
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
                now = state.now,
                zone = state.zone,
                emphasize = true,
                onToggle = onToggle,
                onEdit = { editing = it },
            )
            taskSection(
                titleRes = R.string.section_today,
                tasks = board.today,
                now = state.now,
                zone = state.zone,
                onToggle = onToggle,
                onEdit = { editing = it },
            )
            taskSection(
                titleRes = R.string.section_later,
                tasks = board.later,
                now = state.now,
                zone = state.zone,
                onToggle = onToggle,
                onEdit = { editing = it },
            )
            taskSection(
                titleRes = R.string.section_done_today,
                tasks = board.doneToday,
                now = state.now,
                zone = state.zone,
                onToggle = onToggle,
                onEdit = { editing = it },
            )
        }
    }

    editing?.let { task ->
        TaskEditorDialog(
            task = task,
            zone = state.zone,
            onDismiss = { editing = null },
            onSave = { title, note, dueDate, dueTime ->
                onSave(task.id, title, note, dueDate, dueTime)
                editing = null
            },
            onDelete = {
                onDelete(task.id)
                editing = null
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.taskSection(
    titleRes: Int,
    tasks: List<Task>,
    now: Instant,
    zone: ZoneId,
    emphasize: Boolean = false,
    onToggle: (Task) -> Unit,
    onEdit: (Task) -> Unit,
) {
    if (tasks.isEmpty()) return

    item(key = "header-$titleRes") {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = if (emphasize) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(tasks, key = { it.id }) { task ->
        TaskRow(
            task = task,
            now = now,
            zone = zone,
            onToggle = { onToggle(task) },
            onEdit = { onEdit(task) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun TaskRow(
    task: Task,
    now: Instant,
    zone: ZoneId,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val due = task.dueLabel(now, zone)
    val overdue = task.overdueLabel(now, zone)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Abhaken per Tippen — der Stift daneben öffnet die Bearbeitung.
            .clickable(onClickLabel = stringResource(R.string.task_toggle_done), onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
            val subtitle = listOfNotNull(overdue ?: due, task.note?.let { stringResource(R.string.task_note_indicator) })
                .joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.task_open_details))
        }
    }
}

/** Schnell-Eingabe unten: Titel tippen, optional Fälligkeit, absenden. */
@Composable
private fun QuickAddBar(onAdd: (String, LocalDate?, LocalTime?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var dueTime by remember { mutableStateOf<LocalTime?>(null) }

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
                        onAdd(title, dueDate, dueTime)
                        title = ""
                        dueDate = null
                        dueTime = null
                    },
                    enabled = title.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.quick_add_submit),
                    )
                }
            }
            DuePicker(
                dueDate = dueDate,
                dueTime = dueTime,
                onDueDateChange = { dueDate = it },
                onDueTimeChange = { dueTime = it },
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}
