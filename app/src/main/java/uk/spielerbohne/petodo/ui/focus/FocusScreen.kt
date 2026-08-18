package uk.spielerbohne.petodo.ui.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import uk.spielerbohne.petodo.ui.theme.Brand
import uk.spielerbohne.petodo.ui.theme.GradientCard
import uk.spielerbohne.petodo.ui.theme.Palette
import uk.spielerbohne.petodo.ui.theme.ScreenGlow

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
    Box(Modifier.fillMaxSize()) {
        ScreenGlow(colors = Brand.Focus, alpha = 0.26f, modifier = Modifier.align(Alignment.TopCenter))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.focus_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp),
            )

            // Die Uhr: ein Ring, kein Balken. Ein Ring zeigt, wie viel von einer Sache
            // übrig ist, ohne dass man eine Zahl lesen müsste.
            TimerDial(state = state)

            ControlCard(
                state = state,
                selectedTaskId = selectedTaskId,
                onSelectTask = onSelectTask,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                onSkip = onSkip,
            )
        }
    }
}

/**
 * Der Ring läuft von voll nach leer.
 *
 * Gezeichnet wird aus dem Rest, den [FocusTimer] aus dem gespeicherten Endzeitpunkt
 * errechnet — hier läuft kein zweiter Zähler mit, der auseinanderlaufen könnte.
 */
@Composable
private fun TimerDial(state: FocusUiState) {
    val gesamt = state.settings.durationOf(currentPhase(state)).seconds.coerceAtLeast(1)
    val rest = when (val aktuell = state.state) {
        is FocusState.Running -> aktuell.remaining.seconds
        is FocusState.Paused -> aktuell.remaining.seconds
        is FocusState.Elapsed -> 0L
        FocusState.Ready -> gesamt
    }
    val ziel = (rest.toFloat() / gesamt.toFloat()).coerceIn(0f, 1f)
    val anteil by animateFloatAsState(targetValue = ziel, animationSpec = tween(600), label = "ringAnteil")

    val spurFarbe = MaterialTheme.colorScheme.surfaceContainerHighest
    val verlauf = Brush.sweepGradient(listOf(Palette.Sky, Palette.Indigo, Palette.Violet, Palette.Sky))

    Box(
        modifier = Modifier
            .fillMaxWidth(0.78f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dicke = size.minDimension * 0.055f
            val einzug = dicke / 2f
            val bogen = Size(size.width - dicke, size.height - dicke)

            drawArc(
                color = spurFarbe,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(einzug, einzug),
                size = bogen,
                style = Stroke(width = dicke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = verlauf,
                startAngle = -90f,
                sweepAngle = 360f * anteil,
                useCenter = false,
                topLeft = Offset(einzug, einzug),
                size = bogen,
                style = Stroke(width = dicke, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = phaseLabel(state.state).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = remainingLabel(state),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.focus_rounds_today,
                    state.completedRoundsToday,
                    state.settings.roundsBeforeLongBreak,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Die Bedienkarte.
 *
 * Alles, was man während einer Runde antippt, liegt zusammen an einer Stelle — unten,
 * wo der Daumen ist. Der tiefe Indigo-Verlauf hält sie ruhig: Eine schreiende Fläche
 * neben einer Uhr, auf die man 25 Minuten schaut, wäre eine Zumutung.
 */
@Composable
private fun ControlCard(
    state: FocusUiState,
    selectedTaskId: String?,
    onSelectTask: (String?) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit,
) {
    GradientCard(colors = Brand.Focus, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val aktuell = state.state) {
                is FocusState.Ready, is FocusState.Elapsed -> {
                    TaskPicker(
                        tasks = state.openTasks,
                        selectedTaskId = selectedTaskId,
                        onSelect = onSelectTask,
                    )
                    PrimaryAction(
                        label = stringResource(R.string.focus_start),
                        icon = Icons.Filled.PlayArrow,
                        onClick = onStart,
                    )
                }

                is FocusState.Running -> {
                    state.linkedTask?.let { LinkedTask(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (aktuell.session.phase.isBreak) {
                            PrimaryAction(
                                label = stringResource(R.string.focus_skip),
                                icon = Icons.Filled.SkipNext,
                                onClick = onSkip,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            PrimaryAction(
                                label = stringResource(R.string.focus_pause),
                                icon = Icons.Filled.Pause,
                                onClick = onPause,
                                modifier = Modifier.weight(1f),
                            )
                            GhostAction(
                                contentDescription = stringResource(R.string.focus_stop),
                                icon = Icons.Filled.Close,
                                onClick = onStop,
                            )
                        }
                    }
                }

                is FocusState.Paused -> {
                    state.linkedTask?.let { LinkedTask(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryAction(
                            label = stringResource(R.string.focus_resume),
                            icon = Icons.Filled.PlayArrow,
                            onClick = onResume,
                            modifier = Modifier.weight(1f),
                        )
                        GhostAction(
                            contentDescription = stringResource(R.string.focus_stop),
                            icon = Icons.Filled.Close,
                            onClick = onStop,
                        )
                    }
                }
            }
        }
    }
}

/** Die eine Handlung, die man treffen können muss — hell, breit, mit Zeichen. */
@Composable
private fun PrimaryAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Palette.Chalk)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Palette.Ink,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Ink,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Abbrechen ist immer erreichbar, aber nie das Erste, was man trifft. */
@Composable
private fun GhostAction(contentDescription: String, icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Palette.Chalk,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun currentPhase(state: FocusUiState): FocusPhase = when (val aktuell = state.state) {
    is FocusState.Running -> aktuell.session.phase
    is FocusState.Paused -> aktuell.session.phase
    is FocusState.Elapsed -> aktuell.session.phase
    FocusState.Ready -> FocusPhase.FOCUS
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
    Column {
        Text(
            text = stringResource(R.string.focus_linked_task).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Chalk.copy(alpha = 0.7f),
        )
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Chalk,
        )
    }
}

/** Die Aufgabe zur Runde — eine Kapsel, kein Formularfeld. */
@Composable
private fun TaskPicker(
    tasks: List<Task>,
    selectedTaskId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = tasks.firstOrNull { it.id == selectedTaskId }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.focus_session_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Chalk.copy(alpha = 0.65f),
            )
            Text(
                text = selected?.title ?: stringResource(R.string.focus_no_task),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Chalk,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

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
}
