package uk.spielerbohne.petodo.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.focus.FocusPhase
import uk.spielerbohne.petodo.domain.focus.FocusState
import uk.spielerbohne.petodo.domain.focus.FocusTimer
import uk.spielerbohne.petodo.domain.model.Task

@Composable
fun FocusRoute(container: AppContainer) {
    val context = LocalContext.current
    val viewModel: FocusViewModel = viewModel(factory = FocusViewModel.factory(container, context))
    val state by viewModel.state.collectAsStateWithLifecycle()

    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    FocusScreen(
        state = state,
        selectedTaskId = selectedTaskId,
        onSelectTask = { selectedTaskId = it },
        onStart = { viewModel.startFocus(selectedTaskId) },
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onStop = viewModel::stop,
        onSkip = viewModel::skipBreak,
    )
}

@Composable
fun FocusScreen(
    state: FocusUiState,
    selectedTaskId: String?,
    onSelectTask: (String?) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = phaseLabel(state.state),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = remainingLabel(state),
            style = MaterialTheme.typography.displayLarge,
        )

        Text(
            text = stringResource(
                R.string.focus_rounds_today,
                state.completedRoundsToday,
                state.settings.roundsBeforeLongBreak,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val current = state.state) {
            is FocusState.Ready, is FocusState.Elapsed -> {
                TaskPicker(
                    tasks = state.openTasks,
                    selectedTaskId = selectedTaskId,
                    onSelect = onSelectTask,
                )
                Button(onClick = onStart) { Text(stringResource(R.string.focus_start)) }
            }

            is FocusState.Running -> {
                state.linkedTask?.let { LinkedTask(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current.session.phase.isBreak) {
                        OutlinedButton(onClick = onSkip) { Text(stringResource(R.string.focus_skip)) }
                    } else {
                        OutlinedButton(onClick = onPause) { Text(stringResource(R.string.focus_pause)) }
                        OutlinedButton(onClick = onStop) { Text(stringResource(R.string.focus_stop)) }
                    }
                }
            }

            is FocusState.Paused -> {
                state.linkedTask?.let { LinkedTask(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onResume) { Text(stringResource(R.string.focus_resume)) }
                    OutlinedButton(onClick = onStop) { Text(stringResource(R.string.focus_stop)) }
                }
            }
        }
    }
}

@Composable
private fun phaseLabel(state: FocusState): String = when (state) {
    is FocusState.Ready -> stringResource(R.string.focus_state_ready)
    is FocusState.Running -> phaseName(state.session.phase)
    is FocusState.Paused -> phaseName(state.session.phase)
    is FocusState.Elapsed -> phaseName(state.session.phase)
}

@Composable
private fun phaseName(phase: FocusPhase): String = stringResource(
    when (phase) {
        FocusPhase.FOCUS -> R.string.focus_phase_focus
        FocusPhase.SHORT_BREAK -> R.string.focus_phase_short_break
        FocusPhase.LONG_BREAK -> R.string.focus_phase_long_break
    }
)

@Composable
private fun remainingLabel(state: FocusUiState): String = when (val current = state.state) {
    is FocusState.Running -> FocusTimer.format(current.remaining)
    is FocusState.Paused -> FocusTimer.format(current.remaining)
    // Im Bereitzustand steht dort die volle Fokusdauer — was der nächste Start bringt.
    else -> FocusTimer.format(state.settings.durationOf(FocusPhase.FOCUS))
}

@Composable
private fun LinkedTask(task: Task) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.focus_linked_task),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = task.title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TaskPicker(
    tasks: List<Task>,
    selectedTaskId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = tasks.firstOrNull { it.id == selectedTaskId }

    AssistChip(
        onClick = { expanded = true },
        label = { Text(selected?.title ?: stringResource(R.string.focus_no_task)) },
    )

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.focus_no_task)) },
            onClick = {
                onSelect(null)
                expanded = false
            },
        )
        tasks.forEach { task ->
            DropdownMenuItem(
                text = { Text(task.title) },
                onClick = {
                    onSelect(task.id)
                    expanded = false
                },
            )
        }
    }
}
