package uk.spielerbohne.petodo.ui.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.habit.HabitSchedule
import uk.spielerbohne.petodo.domain.model.Habit
import uk.spielerbohne.petodo.ui.theme.Brand
import uk.spielerbohne.petodo.ui.theme.GlassCard
import uk.spielerbohne.petodo.ui.theme.GradientCard
import uk.spielerbohne.petodo.ui.theme.Motion
import uk.spielerbohne.petodo.ui.theme.Palette
import uk.spielerbohne.petodo.ui.theme.ScreenGlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitsRoute(container: AppContainer, onBack: () -> Unit) {
    val viewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    HabitsScreen(
        state = state,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onCreate = viewModel::create,
        onUpdate = viewModel::update,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitsScreen(
    state: HabitsUiState,
    onBack: () -> Unit,
    onToggle: (Habit) -> Unit,
    onCreate: (String, HabitSchedule) -> Unit,
    onUpdate: (String, String, HabitSchedule) -> Unit,
    onDelete: (String) -> Unit,
) {
    var bearbeite by remember { mutableStateOf<Habit?>(null) }
    var neu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.habits_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { neu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.habits_new))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            ScreenGlow(colors = Brand.Cool, alpha = 0.16f, modifier = Modifier.align(Alignment.TopCenter))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.dueToday.isNotEmpty()) {
                    item("heute") { TodaySummary(done = state.doneToday, total = state.dueToday.size) }
                }

                items(state.habits, key = { it.habit.id }) { row ->
                    HabitCard(
                        row = row,
                        today = state.today,
                        onToggle = { onToggle(row.habit) },
                        onEdit = { bearbeite = row.habit },
                        modifier = Modifier.animateItem(placementSpec = Motion.slow()),
                    )
                }

                if (state.isEmpty) {
                    item("leer") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.habits_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }

                item("hinweis") {
                    Text(
                        text = stringResource(R.string.habits_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (neu) {
        HabitDialog(
            initialName = "",
            initialSchedule = HabitSchedule.DAILY,
            onDismiss = { neu = false },
            onConfirm = { name, plan ->
                onCreate(name, plan)
                neu = false
            },
        )
    }

    bearbeite?.let { habit ->
        HabitDialog(
            initialName = habit.name,
            initialSchedule = habit.schedule,
            onDelete = {
                onDelete(habit.id)
                bearbeite = null
            },
            onDismiss = { bearbeite = null },
            onConfirm = { name, plan ->
                onUpdate(habit.id, name, plan)
                bearbeite = null
            },
        )
    }
}

/** Der Tagesstand — die eine Zahl, die zählt, wenn man den Bildschirm aufmacht. */
@Composable
private fun TodaySummary(done: Int, total: Int) {
    GradientCard(
        colors = if (done == total) Brand.Pet else Brand.Cool,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.habits_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Chalk.copy(alpha = 0.75f),
            )
            Text(
                text = stringResource(R.string.habits_today_done, done, total),
                style = MaterialTheme.typography.displaySmall,
                color = Palette.Chalk,
            )
        }
    }
}

@Composable
private fun HabitCard(
    row: HabitRow,
    today: LocalDate,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), onClick = onEdit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = row.habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = (
                        if (row.streak == 0) {
                            stringResource(R.string.habits_streak_none)
                        } else {
                            pluralStringResource(R.plurals.habits_streak, row.streak, row.streak)
                        }
                        ) + " · " + stringResource(R.string.habits_week, row.weekDone, row.weekTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeekDots(row = row, today = today)
            }

            Spacer(Modifier.width(12.dp))

            if (row.dueToday) {
                CheckCircle(checked = row.checkedToday, onClick = onToggle)
            } else {
                Text(
                    text = stringResource(R.string.habits_free_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Die Woche als sieben Punkte.
 *
 * Gefüllt = erledigt, umrandet = steht an, blass = frei. Man sieht in einem Blick, ob
 * der Zeitplan zum Leben passt — und das ist die einzige Frage, die bei Gewohnheiten zählt.
 */
@Composable
private fun WeekDots(row: HabitRow, today: LocalDate) {
    // Montag dieser Woche — dieselbe Wochendefinition wie in HabitStreak. Der heutige
    // Tag kommt von außen herein, nicht aus der Systemuhr: sonst zeigten Diagramm und
    // Serie an einem Tageswechsel Verschiedenes.
    val wochenstart = today.minusDays((today.dayOfWeek.value - 1).toLong())

    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (0L..6L).forEach { versatz ->
            val tag = wochenstart.plusDays(versatz)
            val stehtAn = row.habit.isDueOn(tag)
            val erledigt = tag in row.checkins

            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            erledigt -> Palette.Lime
                            stehtAn -> Color.Transparent
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            if (stehtAn && !erledigt) MaterialTheme.colorScheme.outline else Color.Transparent,
                        ),
                        CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean, onClick: () -> Unit) {
    val fuellung by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = Motion.bouncy(),
        label = "gewohnheitHaken",
    )
    val rand by animateColorAsState(
        targetValue = if (checked) Palette.Lime else MaterialTheme.colorScheme.outline,
        animationSpec = Motion.standard(),
        label = "gewohnheitRand",
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(BorderStroke(2.dp, rand), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = fuellung
                    scaleY = fuellung
                }
                .clip(CircleShape)
                .background(Palette.Lime)
        )
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = stringResource(R.string.habits_check),
            tint = if (checked) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HabitDialog(
    initialName: String,
    initialSchedule: HabitSchedule,
    onDismiss: () -> Unit,
    onConfirm: (String, HabitSchedule) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var plan by remember { mutableStateOf(initialSchedule) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (onDelete == null) R.string.habits_new else R.string.habits_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.habits_name_hint)) },
                )

                Text(
                    text = stringResource(R.string.habits_days),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HabitSchedule.ALL_DAYS.forEach { tag ->
                        DayToggle(
                            day = tag,
                            selected = plan.isDueOn(tag),
                            onClick = { plan = plan.toggle(tag) },
                        )
                    }
                }
                if (plan.isEmpty) {
                    Text(
                        text = stringResource(R.string.habits_needs_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                onDelete?.let { loeschen ->
                    TextButton(onClick = loeschen) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.habits_delete),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, plan) },
                enabled = name.isNotBlank() && !plan.isEmpty,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DayToggle(day: DayOfWeek, selected: Boolean, onClick: () -> Unit) {
    val hintergrund by animateColorAsState(
        targetValue = if (selected) Palette.Sky.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = Motion.quick(),
        label = "tagHintergrund",
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(hintergrund)
            .border(
                BorderStroke(1.dp, if (selected) Palette.Sky else MaterialTheme.colorScheme.outline),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Palette.Sky else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
