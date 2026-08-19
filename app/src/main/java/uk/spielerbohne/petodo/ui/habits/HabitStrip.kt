package uk.spielerbohne.petodo.ui.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.ui.theme.Motion
import uk.spielerbohne.petodo.ui.theme.Palette
import uk.spielerbohne.petodo.ui.theme.SectionLabel

/**
 * Die heutigen Gewohnheiten über der Aufgabenliste.
 *
 * Eine Gewohnheit, die man nicht täglich sieht, ist keine. Sie stehen deshalb dort, wo
 * man ohnehin jeden Tag hinschaut — aber als schmale Reihe, nicht als zweite Liste:
 * Gewohnheiten sind der Rahmen des Tages, nicht sein Inhalt.
 *
 * Was heute nicht ansteht, taucht gar nicht erst auf. Kein Nachholen, keine roten Zahlen.
 */
@Composable
fun HabitStrip(container: AppContainer, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: HabitsViewModel = viewModel(
        factory = HabitsViewModel.factory(container),
        key = "habit-strip",
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val heute = state.dueToday

    if (heute.isEmpty()) return

    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionLabel(
            text = stringResource(R.string.habits_title),
            accent = Palette.Lime,
            modifier = Modifier
                .padding(start = 4.dp)
                .clickable(onClick = onOpen),
        ) {
            Text(
                text = stringResource(R.string.habits_today_done, state.doneToday, heute.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            heute.forEach { row ->
                HabitChip(
                    name = row.habit.name,
                    checked = row.checkedToday,
                    onClick = { viewModel.toggle(row.habit) },
                )
            }
        }
    }
}

@Composable
private fun HabitChip(name: String, checked: Boolean, onClick: () -> Unit) {
    val hintergrund by animateColorAsState(
        targetValue = if (checked) Palette.Lime.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = Motion.standard(),
        label = "gewohnheitChip",
    )
    val rand by animateColorAsState(
        targetValue = if (checked) Palette.Lime.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
        animationSpec = Motion.standard(),
        label = "gewohnheitChipRand",
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(hintergrund)
            .border(BorderStroke(1.dp, rand), CircleShape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) Palette.Lime else Color.Transparent)
                .border(
                    BorderStroke(1.5.dp, if (checked) Palette.Lime else MaterialTheme.colorScheme.outline),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = if (checked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
