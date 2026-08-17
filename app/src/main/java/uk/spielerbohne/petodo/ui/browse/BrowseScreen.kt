package uk.spielerbohne.petodo.ui.browse

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
) {
    var searching by remember { mutableStateOf(startInSearch) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(searching) {
        if (searching) runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
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

            LazyColumn(state = listState) {
                items(state.tasks, key = { it.id }) { task ->
                    BrowseRow(
                        task = task,
                        state = state,
                        onToggle = { onToggle(task) },
                        onOpen = { onOpenTask(task.id) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
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
            FilterChip(
                selected = current == TaskScope.AllOpen,
                onClick = { onScopeChange(TaskScope.AllOpen) },
                label = { Text(stringResource(R.string.browse_scope_all)) },
            )
            FilterChip(
                selected = current == TaskScope.NextSevenDays,
                onClick = { onScopeChange(TaskScope.NextSevenDays) },
                label = { Text(stringResource(R.string.browse_scope_seven_days)) },
            )
            FilterChip(
                selected = current == TaskScope.Completed,
                onClick = { onScopeChange(TaskScope.Completed) },
                label = { Text(stringResource(R.string.browse_scope_completed)) },
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
                FilterChip(
                    selected = (current as? TaskScope.InList)?.listId == list.id,
                    onClick = { onScopeChange(TaskScope.InList(list.id)) },
                    label = { Text(list.name) },
                )
            }
        }
    }
}

@Composable
private fun BrowseRow(
    task: Task,
    state: BrowseUiState,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val due = task.dueLabel(state.now, state.zone)
    val overdue = task.overdueLabel(state.now, state.zone)
    val listColor = state.listColors[task.listId]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(vertical = 10.dp),
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
