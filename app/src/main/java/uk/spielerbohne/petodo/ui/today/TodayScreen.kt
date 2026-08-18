package uk.spielerbohne.petodo.ui.today

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import uk.spielerbohne.petodo.domain.text.MarkdownLinks
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import uk.spielerbohne.petodo.ui.theme.Brand
import uk.spielerbohne.petodo.ui.theme.CircleIconButton
import uk.spielerbohne.petodo.ui.theme.CountBadge
import uk.spielerbohne.petodo.ui.theme.GlassCard
import uk.spielerbohne.petodo.ui.theme.Palette
import uk.spielerbohne.petodo.ui.theme.ScreenGlow
import uk.spielerbohne.petodo.ui.theme.SectionLabel
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import uk.spielerbohne.petodo.ui.pet.PetStrip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun TodayRoute(
    container: AppContainer,
    onOpenTask: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenPet: () -> Unit = {},
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
        // Der Streifen wird hereingereicht, damit der Screen selbst nichts vom Container
        // wissen muss — er bleibt eine reine Anzeige seines Zustands.
        petStrip = { PetStrip(container = container, onOpen = onOpenPet) },
    )
}

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
    petStrip: (@Composable () -> Unit)? = null,
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

    // Der Rückblick ist zugeklappt, bis jemand ihn aufmacht. Er soll die Liste nicht
    // verlängern, sondern nur beweisen, dass nichts verlorengegangen ist.
    var archiveOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { QuickAddBar(onAdd = onAdd) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val board = state.board
        Box(Modifier.fillMaxSize()) {
            // Der Lichtschein liegt hinter der Liste und wandert nicht mit — er gehört
            // zum Bildschirm, nicht zum Inhalt.
            ScreenGlow(colors = Brand.Cool, alpha = 0.16f, modifier = Modifier.align(Alignment.TopCenter))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item("kopf") {
                    TodayHeader(
                        done = board.doneToday.size,
                        open = board.overdue.size + board.today.size,
                        onSearch = onSearch,
                    )
                }

                petStrip?.let { streifen ->
                    item("pet") {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { streifen() }
                    }
                }

                if (board.isEmpty) {
                    item("leer") { EmptyState() }
                }

                taskSection(
                    titleRes = R.string.section_overdue,
                    tasks = board.overdue,
                    state = state,
                    accent = Palette.Amber,
                    // "Verschieben" räumt den ganzen Block auf einmal auf — die Fluchttür
                    // aus einer schlechten Woche.
                    bulkAction = if (board.overdue.isNotEmpty()) postponeLabel to onPostponeOverdue else null,
                    onToggle = onToggle,
                    onOpen = onOpenTask,
                )
                taskSection(
                    R.string.section_today, board.today, state,
                    accent = Palette.Sky, onToggle = onToggle, onOpen = onOpenTask,
                )
                taskSection(
                    R.string.section_later, board.later, state,
                    onToggle = onToggle, onOpen = onOpenTask,
                )
                taskSection(
                    R.string.section_done_today, board.doneToday, state,
                    accent = Palette.Lime, onToggle = onToggle, onOpen = onOpenTask,
                )

                archiveSection(
                    tasks = board.doneEarlier,
                    state = state,
                    expanded = archiveOpen,
                    onToggleExpanded = { archiveOpen = !archiveOpen },
                    onToggle = onToggle,
                    onOpen = onOpenTask,
                )
            }
        }
    }
}

/**
 * Der Kopf: Anrede, Tagesstand, Suche.
 *
 * Statt einer Titelleiste, die auf jedem Bildschirm gleich aussieht, steht hier die eine
 * Zahl, die zählt — wie viel von heute schon weg ist.
 */
@Composable
private fun TodayHeader(done: Int, open: Int, onSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.today_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (done + open == 0) {
                    stringResource(R.string.today_progress_clear)
                } else {
                    stringResource(R.string.today_progress, done, done + open)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        CircleIconButton(
            icon = Icons.Filled.Search,
            contentDescription = stringResource(R.string.browse_search),
            onClick = onSearch,
        )
    }
}

@Composable
private fun EmptyState() {
    GlassCard(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.today_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

private fun LazyListScope.taskSection(
    titleRes: Int,
    tasks: List<Task>,
    state: TodayUiState,
    accent: Color? = null,
    bulkAction: Pair<String, () -> Unit>? = null,
    onToggle: (Task) -> Unit,
    onOpen: (String) -> Unit,
) {
    if (tasks.isEmpty()) return

    item(key = "header-$titleRes") {
        SectionHeader(
            titleRes = titleRes,
            count = tasks.size,
            accent = accent,
            bulkAction = bulkAction,
        )
    }
    items(tasks, key = { it.id }) { task ->
        TaskCard(
            task = task,
            state = state,
            accent = accent,
            onToggle = { onToggle(task) },
            onOpen = { onOpen(task.id) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
        )
    }
}

/**
 * Der Rückblick ganz unten: was an den Tagen davor erledigt wurde.
 *
 * Bewusst leise gebaut — keine Karten, keine Farbe, kleinere Schrift. Erledigtes ist
 * kein offener Punkt und darf nicht so aussehen. Sichtbar bleibt es trotzdem, weil eine
 * Aufgabe, die spurlos verschwindet, sich anfühlt wie eine verlorene Aufgabe.
 */
private fun LazyListScope.archiveSection(
    tasks: List<Task>,
    state: TodayUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggle: (Task) -> Unit,
    onOpen: (String) -> Unit,
) {
    if (tasks.isEmpty()) return

    item(key = "archiv-kopf") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.done_earlier_collapse else R.string.done_earlier_expand
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.section_done_earlier).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                text = tasks.size.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
    }

    if (!expanded) return

    // Nach Tagen gebündelt: „Gestern“ trägt mehr als sechs gleich aussehende Zeilen.
    val byDay = tasks.groupBy { it.completedAt?.atZone(state.zone)?.toLocalDate() }

    byDay.forEach { (day, dayTasks) ->
        if (day == null) return@forEach

        item(key = "archiv-tag-$day") {
            Text(
                text = completedDayLabel(day, state.now, state.zone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
            )
        }
        items(dayTasks, key = { "archiv-${it.id}" }) { task ->
            ArchiveRow(
                task = task,
                onToggle = { onToggle(task) },
                onOpen = { onOpen(task.id) },
            )
        }
    }

    item(key = "archiv-fuss") {
        Text(
            text = stringResource(R.string.done_earlier_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
    }
}

/** Eine Zeile im Rückblick: durchgestrichen, gedämpft, ohne Karte. */
@Composable
private fun ArchiveRow(task: Task, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Der Haken bleibt bedienbar: Wer versehentlich abgehakt hat, macht es hier auf.
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.task_toggle_done),
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = MarkdownLinks.plainText(task.title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textDecoration = TextDecoration.LineThrough,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun SectionHeader(
    titleRes: Int,
    count: Int,
    accent: Color?,
    bulkAction: Pair<String, () -> Unit>?,
) {
    val farbe = accent ?: MaterialTheme.colorScheme.onSurfaceVariant
    SectionLabel(
        text = stringResource(titleRes),
        accent = farbe,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
    ) {
        bulkAction?.let { (label, action) ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = farbe,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = action)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        CountBadge(count = count, accent = farbe)
    }
}

/**
 * Eine Aufgabe als eigene Karte statt als Zeile mit Trennlinie.
 *
 * Trennlinien erzeugen eine Tabelle; Karten erzeugen Gegenstände, die man anfassen kann.
 * Der Preis sind ein paar Pixel Platz je Aufgabe — der Gewinn ist, dass Titel, Termin
 * und Fahne zusammen als ein Ding lesbar sind.
 */
@Composable
private fun TaskCard(
    task: Task,
    state: TodayUiState,
    accent: Color?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = state.zone
    val now = state.now
    val due = task.dueLabel(now, zone)
    val overdue = task.overdueLabel(now, zone)
    val listColor = state.listColors[task.listId]
    val progress = state.subtaskProgress[task.id]
    val tags = state.tagsByTask[task.id].orEmpty()

    GlassCard(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Farbstreifen der Liste — der schnellste Weg zu sehen, wohin etwas gehört.
            listColor?.let {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(Color(it))
                )
            }

            CheckDot(
                checked = task.isCompleted,
                accent = accent ?: MaterialTheme.colorScheme.primary,
                onClick = onToggle,
                modifier = Modifier.padding(start = 14.dp, end = 12.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.task_open_details), onClick = onOpen)
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // In der Liste steht die Kurzform: Aus `[Reel](https://…)` wird „Reel“,
                // aus einer nackten Adresse „instagram.com/reel/…“. Angetippt wird hier
                // die Aufgabe, nicht der Verweis — sonst trifft man ständig daneben.
                Text(
                    text = MarkdownLinks.plainText(task.title),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                val details = buildList {
                    (overdue ?: due)?.let(::add)
                    if (progress != null && progress.hasSubtasks) {
                        add(stringResource(R.string.detail_subtask_progress, progress.done, progress.total))
                    }
                    tags.forEach { add(stringResource(R.string.tag_hash, it.name)) }
                    task.note?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.task_note_indicator)) }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue != null) Palette.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (MarkdownLinks.hasLink(task.title) || MarkdownLinks.hasLink(task.note.orEmpty())) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = stringResource(R.string.task_has_link),
                    tint = Palette.Sky,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                )
            }
            if (task.rrule != null) {
                Icon(
                    imageVector = Icons.Filled.Repeat,
                    contentDescription = stringResource(R.string.recurrence_label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                )
            }
            if (PriorityUi.hasVisibleFlag(task.priority)) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = PriorityUi.label(task.priority),
                    tint = PriorityUi.color(task.priority),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(16.dp),
                )
            }
        }
    }
}

/**
 * Der Haken als Kreis.
 *
 * Die Material-Checkbox ist ein Quadrat mit eigener Umrandung und eigenem Anfasser —
 * daneben sieht jede runde Karte falsch aus. Der Kreis füllt sich beim Abhaken mit der
 * Abschnittsfarbe, damit man den Erfolg auch aus dem Augenwinkel sieht.
 */
@Composable
private fun CheckDot(
    checked: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill by animateColorAsState(
        targetValue = if (checked) accent else Color.Transparent,
        label = "hakenFuellung",
    )
    val rand by animateColorAsState(
        targetValue = if (checked) accent else MaterialTheme.colorScheme.outline,
        label = "hakenRand",
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(fill)
            .border(BorderStroke(1.5.dp, rand), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(15.dp),
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

    // Schwebt über dem Grund statt als Leiste anzukleben — dieselbe Sprache wie die
    // Navigationsleiste darunter.
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
    ) {
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
                // Der Knopf leuchtet erst, wenn es etwas anzulegen gibt.
                val bereit = title.isNotBlank()
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (bereit) Brand.brush(Brand.Cool)
                            else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                        .clickable(enabled = bereit) {
                            onAdd(title, dueDate, dueTime, priority)
                            title = ""
                            dueDate = null
                            dueTime = null
                            priority = uk.spielerbohne.petodo.domain.model.Priority.DEFAULT
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.quick_add_submit),
                        tint = if (bereit) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
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
