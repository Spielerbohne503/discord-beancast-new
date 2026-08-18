package uk.spielerbohne.petodo.ui.browse

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import uk.spielerbohne.petodo.ui.theme.Palette
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.filter.TaskScope
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.ui.common.PriorityUi
import uk.spielerbohne.petodo.ui.common.rememberReorderState
import uk.spielerbohne.petodo.ui.common.reorderable
import uk.spielerbohne.petodo.ui.today.dueLabel
import uk.spielerbohne.petodo.ui.today.overdueLabel

@Composable
fun BrowseRoute(
    container: AppContainer,
    initialScope: TaskScope,
    startInSearch: Boolean,
    onOpenTask: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: BrowseViewModel = viewModel(
        key = "browse-${initialScope.hashCode()}",
        factory = BrowseViewModel.factory(container, initialScope),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    BrowseScreen(
        state = state,
        startInSearch = startInSearch,
        onScopeChange = viewModel::setScope,
        onQueryChange = viewModel::setQuery,
        onToggle = viewModel::toggleCompleted,
        onOpenTask = onOpenTask,
        onBack = onBack,
        onDragMove = viewModel::onDragMove,
        onDragDrop = viewModel::onDragDrop,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    state: BrowseUiState,
    startInSearch: Boolean,
    onScopeChange: (TaskScope) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggle: (Task) -> Unit,
    onOpenTask: (String) -> Unit,
    onBack: () -> Unit,
    onDragMove: (Int, Int) -> Unit = { _, _ -> },
    onDragDrop: () -> Unit = {},
) {
    var searching by remember { mutableStateOf(startInSearch) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderState(
        listState = listState,
        onMove = onDragMove,
        onDrop = onDragDrop,
    )

    LaunchedEffect(searching) {
        if (searching) runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                title = {
                    if (searching) {
                        TextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            placeholder = { Text(stringResource(R.string.browse_search_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    } else {
                        Text(state.currentList?.name ?: stringResource(R.string.browse_title))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (searching) onQueryChange("")
                            searching = !searching
                        }
                    ) {
                        Icon(
                            imageVector = if (searching) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(
                                if (searching) R.string.browse_search_close else R.string.browse_search
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScopeChips(
                current = state.scope,
                lists = state.lists,
                onScopeChange = onScopeChange,
            )

            if (state.tasks.isEmpty()) {
                Text(
                    text = stringResource(
                        if (state.query.isBlank()) R.string.browse_empty else R.string.browse_empty_search
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                Text(
                    text = androidx.compose.ui.platform.LocalContext.current.resources
                        .getQuantityString(R.plurals.browse_count, state.tasks.size, state.tasks.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                )
            }

            LazyColumn(
                state = listState,
                // Ziehen nur dort, wo die Reihenfolge von Hand gilt — in einer nach
                // Fälligkeit sortierten Ansicht wäre ein Zug sofort wieder weg.
                modifier = if (state.manuallyOrdered) Modifier.reorderable(reorderState) else Modifier,
            ) {
                itemsIndexed(state.tasks, key = { _, task -> task.id }) { index, task ->
                    val dragged = reorderState.draggedIndex == index
                    Box(
                        modifier = Modifier
                            .zIndex(if (dragged) 1f else 0f)
                            .graphicsLayer { translationY = reorderState.offsetFor(index) }
                            .padding(horizontal = 16.dp, vertical = 3.dp)
                    ) {
                        BrowseRow(
                            task = task,
                            state = state,
                            dragged = dragged,
                            onToggle = { onToggle(task) },
                            onOpen = { onOpenTask(task.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeChips(
    current: TaskScope,
    lists: List<uk.spielerbohne.petodo.domain.model.TaskList>,
    onScopeChange: (TaskScope) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            ScopeChip(
                selected = current == TaskScope.AllOpen,
                label = stringResource(R.string.browse_scope_all),
                onClick = { onScopeChange(TaskScope.AllOpen) },
            )
            ScopeChip(
                selected = current == TaskScope.NextSevenDays,
                label = stringResource(R.string.browse_scope_seven_days),
                onClick = { onScopeChange(TaskScope.NextSevenDays) },
            )
            ScopeChip(
                selected = current == TaskScope.Completed,
                label = stringResource(R.string.browse_scope_completed),
                onClick = { onScopeChange(TaskScope.Completed) },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
        ) {
            lists.forEach { list ->
                ScopeChip(
                    selected = (current as? TaskScope.InList)?.listId == list.id,
                    label = list.name,
                    accent = list.colorArgb?.let(::Color) ?: Palette.Sky,
                    onClick = { onScopeChange(TaskScope.InList(list.id)) },
                )
            }
        }
    }
}

/**
 * Eine Filterkapsel.
 *
 * Der `FilterChip` von Material bringt Haken, Rahmen und eine eigene Höhe mit; nebeneinander
 * ergeben sie eine Werkzeugleiste. Hier ist die Auswahl nur Farbe — das reicht, und die
 * Zeile bleibt ruhig.
 */
@Composable
private fun ScopeChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    accent: Color = Palette.Sky,
) {
    val hintergrund by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer,
        label = "kapselHintergrund",
    )
    val inhalt by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "kapselInhalt",
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(hintergrund)
            .border(
                BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline),
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = inhalt)
    }
}

@Composable
private fun BrowseRow(
    task: Task,
    state: BrowseUiState,
    dragged: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val due = task.dueLabel(state.now, state.zone)
    val overdue = task.overdueLabel(state.now, state.zone)
    val listColor = state.listColors[task.listId]

    // Die gezogene Karte hebt ab: heller Rand statt grauem Hintergrund. Auf dunklem
    // Grund ist Licht das einzige, was Höhe glaubhaft macht.
    val rand by animateColorAsState(
        targetValue = if (dragged) Palette.Sky else MaterialTheme.colorScheme.outline,
        label = "ziehRand",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(BorderStroke(if (dragged) 1.5.dp else 1.dp, rand), MaterialTheme.shapes.medium),
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listColor?.let {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color(it))
            )
        }
        BrowseCheckDot(
            checked = task.isCompleted,
            onClick = onToggle,
            modifier = Modifier.padding(start = 14.dp, end = 12.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
            (overdue ?: due)?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue != null) Palette.Amber
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

/** Derselbe runde Haken wie auf „Heute“ — zwei Formen für dieselbe Handlung wären Unfug. */
@Composable
private fun BrowseCheckDot(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val akzent = MaterialTheme.colorScheme.primary
    val fuellung by animateColorAsState(
        targetValue = if (checked) akzent else Color.Transparent,
        label = "hakenFuellung",
    )
    val rand by animateColorAsState(
        targetValue = if (checked) akzent else MaterialTheme.colorScheme.outline,
        label = "hakenRand",
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(fuellung)
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
