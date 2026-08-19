package uk.spielerbohne.petodo.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import uk.spielerbohne.petodo.domain.text.MarkdownLinks
import uk.spielerbohne.petodo.ui.common.LinkedText
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import uk.spielerbohne.petodo.ui.theme.GlassCard
import uk.spielerbohne.petodo.ui.theme.Palette
import uk.spielerbohne.petodo.ui.theme.SectionLabel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.domain.model.Tag
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.domain.model.TaskList
import uk.spielerbohne.petodo.domain.recurrence.RecurrenceRule
import uk.spielerbohne.petodo.ui.common.PriorityPicker
import uk.spielerbohne.petodo.ui.common.RecurrencePicker
import uk.spielerbohne.petodo.ui.today.DuePicker
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TaskDetailRoute(container: AppContainer, taskId: String, onBack: () -> Unit) {
    val viewModel: TaskDetailViewModel = viewModel(
        key = "task-$taskId",
        factory = TaskDetailViewModel.factory(container, taskId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Gelöschte Aufgabe: zurück, statt eine leere Seite zu zeigen.
    LaunchedEffect(state.gone, state.task) {
        if (state.gone && state.task == null) onBack()
    }

    state.task?.let { task ->
        TaskDetailScreen(
            task = task,
            state = state,
            onBack = onBack,
            onTitleChange = viewModel::setTitle,
            onNoteChange = viewModel::setNote,
            onDueChange = viewModel::setDue,
            onPriorityChange = viewModel::setPriority,
            onRecurrenceChange = viewModel::setRecurrence,
            onListChange = viewModel::moveToList,
            onToggleCompleted = viewModel::toggleCompleted,
            onLeave = viewModel::flushPendingEdits,
            onDelete = {
                viewModel.delete()
                onBack()
            },
            onAddSubtask = viewModel::addSubtask,
            onToggleSubtask = viewModel::toggleSubtask,
            onDeleteSubtask = viewModel::deleteSubtask,
            onAddTag = viewModel::addTag,
            onRemoveTag = viewModel::removeTag,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    task: Task,
    state: TaskDetailUiState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDueChange: (LocalDate?, LocalTime?) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onRecurrenceChange: (RecurrenceRule?) -> Unit,
    onListChange: (String) -> Unit,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit,
    onLeave: (String, String) -> Unit,
    onAddSubtask: (String) -> Unit,
    onToggleSubtask: (Task) -> Unit,
    onDeleteSubtask: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    // Nur an der Aufgabenkennung hängen, nicht am Text: Sonst setzt der aus der Datenbank
    // zurückfließende Wert das Feld beim Tippen neu und der Schreibcursor springt.
    var title by remember(task.id) { mutableStateOf(task.title) }
    var note by remember(task.id) { mutableStateOf(task.note.orEmpty()) }

    // Was beim Verlassen noch in der Warteschlange steht, wird sofort gespeichert.
    // Ohne das verlöre man die letzten Zeichen, wenn man schnell zurückgeht.
    val letzterTitel by rememberUpdatedState(title)
    val letzteNotiz by rememberUpdatedState(note)
    DisposableEffect(task.id) {
        onDispose { onLeave(letzterTitel, letzteNotiz) }
    }
    val zone = state.zone

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { ListSwitcher(lists = state.lists, current = state.list, onListChange = onListChange) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                actions = {
                    PriorityPicker(priority = task.priority, onPriorityChange = onPriorityChange)
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.task_delete))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Kopfzeile: Abhaken plus Fälligkeit — genau die zwei Dinge, die man zuerst sucht.
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = task.isCompleted, onCheckedChange = { onToggleCompleted() })
                    DueSummary(task = task, zone = zone, now = state.now)
                }
            }

            LinkableField(
                value = title,
                onValueChange = {
                    title = it
                    onTitleChange(it)
                },
                textStyle = MaterialTheme.typography.headlineSmall,
            )

            LinkableField(
                value = note,
                onValueChange = {
                    note = it
                    onNoteChange(it)
                },
                placeholder = stringResource(R.string.detail_description_hint),
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 2,
            )

            DuePicker(
                dueDate = task.dueDate(zone),
                dueTime = if (task.hasTime) task.dueAt?.atZone(zone)?.toLocalTime() else null,
                onDueDateChange = { date ->
                    onDueChange(date, if (task.hasTime) task.dueAt?.atZone(zone)?.toLocalTime() else null)
                },
                onDueTimeChange = { time -> onDueChange(task.dueDate(zone), time) },
            )

            // Eine Wiederholung ohne Fälligkeit hätte keinen Anker — deshalb nur mit Datum.
            RecurrencePicker(
                rule = RecurrenceRule.parse(task.rrule),
                enabled = task.dueAt != null,
                onRuleChange = onRecurrenceChange,
            )
            if (task.dueAt == null) {
                Text(
                    text = stringResource(R.string.recurrence_needs_due),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.missedCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.recurrence_missed,
                        task.missedCount,
                        task.missedCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel(
                text = stringResource(R.string.detail_subtasks),
                accent = Palette.Sky,
                modifier = Modifier.padding(top = 8.dp),
            )

            SubtaskSection(
                subtasks = state.subtasks,
                doneCount = state.progress.done,
                total = state.progress.total,
                onAdd = onAddSubtask,
                onToggle = onToggleSubtask,
                onDelete = onDeleteSubtask,
            )

            SectionLabel(
                text = stringResource(R.string.detail_tags),
                accent = Palette.Violet,
                modifier = Modifier.padding(top = 8.dp),
            )

            TagSection(tags = state.tags, onAdd = onAddTag, onRemove = onRemoveTag)
        }
    }
}

/**
 * Ein Textfeld, das Verweise anklickbar macht.
 *
 * Solange kein Verweis drinsteht, ist es ein ganz gewöhnliches Eingabefeld — Tippen,
 * Schreiben, fertig. Sobald einer drinsteht, zeigt es den Text gelesen an: Aus
 * `[Reel](https://…)` wird ein anklickbares „Reel“, aus einer nackten Adresse eine
 * kurze. Zum Ändern gibt es den Stift daneben.
 *
 * Der Grund für die zwei Zustände: Ein Textfeld kann keine anklickbaren Stellen haben,
 * und ein Text, der gleichzeitig Verweis und Eingabefeld ist, trifft man nie richtig.
 */
@Composable
private fun LinkableField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    placeholder: String? = null,
    minLines: Int = 1,
) {
    // Nicht am Text hängen: Sonst kippt das Feld beim Tippen mitten im Wort zurück in
    // die Leseansicht, sobald der eingegebene Text zum ersten Mal wie ein Verweis aussieht.
    var editing by rememberSaveable { mutableStateOf(false) }
    val hatVerweis = remember(value) { MarkdownLinks.hasLink(value) }

    val focusRequester = remember { FocusRequester() }

    // Ob das Feld überhaupt schon einmal den Finger hatte.
    //
    // Ohne diese Unterscheidung ist der Stift-Knopf wirkungslos: Ein frisch erschienenes
    // Textfeld meldet sofort „nicht fokussiert“, und die Leseansicht käme im selben
    // Atemzug zurück. Genau das war der Fehler — man tippte auf den Stift und es
    // passierte nichts.
    var hatteFokus by remember { mutableStateOf(false) }

    if (hatVerweis && !editing) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            LinkedText(
                text = value,
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 14.dp, bottom = 14.dp),
            )
            IconButton(
                onClick = {
                    hatteFokus = false
                    editing = true
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.task_edit_text),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder?.let { { Text(it) } },
        textStyle = textStyle,
        colors = transparentFieldColors(),
        minLines = minLines,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { zustand ->
                if (zustand.isFocused) {
                    hatteFokus = true
                } else if (hatteFokus) {
                    // Weggetippt heißt fertig: Danach steht der Verweis wieder
                    // anklickbar da.
                    hatteFokus = false
                    editing = false
                }
            },
    )

    // Beim Umschalten in den Bearbeitungsmodus bekommt das Feld den Finger, sonst müsste
    // man nach dem Stift noch einmal ins Feld tippen.
    LaunchedEffect(editing) {
        if (editing) runCatching { focusRequester.requestFocus() }
    }
}

@Composable
private fun DueSummary(task: Task, zone: ZoneId, now: Instant) {
    val overdue = task.overdueDays(now, zone)
    val text = task.dueDate(zone)?.let { date ->
        val base = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        if (overdue > 0) {
            "$base · " + pluralStringResource(R.plurals.due_overdue, overdue.toInt(), overdue.toInt())
        } else {
            base
        }
    } ?: stringResource(R.string.task_no_due)

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListSwitcher(
    lists: List<TaskList>,
    current: TaskList?,
    onListChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(current?.name ?: stringResource(R.string.detail_list)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.name) },
                    onClick = {
                        onListChange(list.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SubtaskSection(
    subtasks: List<Task>,
    doneCount: Int,
    total: Int,
    onAdd: (String) -> Unit,
    onToggle: (Task) -> Unit,
    onDelete: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.detail_subtasks), style = MaterialTheme.typography.titleSmall)
            if (total > 0) {
                Text(
                    text = stringResource(R.string.detail_subtask_progress, doneCount, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        subtasks.forEach { subtask ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = subtask.isCompleted, onCheckedChange = { onToggle(subtask) })
                Text(
                    // Auch hier die Kurzform: Eine Unteraufgabe, die aus einer
                    // dreizeiligen Adresse besteht, sprengt die Liste.
                    text = MarkdownLinks.plainText(subtask.title),
                    modifier = Modifier.weight(1f),
                    textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else null,
                    color = if (subtask.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color.Unspecified
                    },
                )
                IconButton(onClick = { onDelete(subtask.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.task_delete))
                }
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text(stringResource(R.string.detail_subtask_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onAdd(draft)
                draft = ""
            }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSection(
    tags: List<Tag>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.detail_tags), style = MaterialTheme.typography.titleSmall)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(tag.id) },
                    label = { Text(tag.name) },
                    leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.detail_tag_remove),
                        )
                    },
                )
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text(stringResource(R.string.detail_tag_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onAdd(draft)
                draft = ""
            }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)
