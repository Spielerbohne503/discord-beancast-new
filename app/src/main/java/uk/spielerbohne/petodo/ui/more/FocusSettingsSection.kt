package uk.spielerbohne.petodo.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.ui.theme.GlassCard
import uk.spielerbohne.petodo.domain.focus.FocusSettings

/**
 * Die Zeiten des Pomodoro-Zyklus. Vorgabe 25 / 5 / 20, lange Pause nach vier Runden —
 * die Zahlen stehen in `domain/Balance.kt`, hier stehen nur die Grenzen der Bedienung.
 */
@Composable
fun FocusSettingsSection(
    settings: FocusSettings,
    onChange: (FocusSettings) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.focus_settings_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Stepper(
                labelRes = R.string.focus_settings_focus,
                value = settings.focusMinutes.toInt(),
                range = 1..180,
                step = 5,
                onValue = { onChange(settings.copy(focusMinutes = it.toLong())) },
            )
            Stepper(
                labelRes = R.string.focus_settings_short,
                value = settings.shortBreakMinutes.toInt(),
                range = 1..60,
                step = 1,
                onValue = { onChange(settings.copy(shortBreakMinutes = it.toLong())) },
            )
            Stepper(
                labelRes = R.string.focus_settings_long,
                value = settings.longBreakMinutes.toInt(),
                range = 1..120,
                step = 5,
                onValue = { onChange(settings.copy(longBreakMinutes = it.toLong())) },
            )
            Stepper(
                labelRes = R.string.focus_settings_rounds,
                value = settings.roundsBeforeLongBreak,
                range = 2..12,
                step = 1,
                onValue = { onChange(settings.copy(roundsBeforeLongBreak = it)) },
            )
        }
    }
}

@Composable
private fun Stepper(
    labelRes: Int,
    value: Int,
    range: IntRange,
    step: Int,
    onValue: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(
            onClick = { onValue((value - step).coerceIn(range)) },
            enabled = value > range.first,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = null)
        }
        Text(
            text = value.toString(),
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(
            onClick = { onValue((value + step).coerceIn(range)) },
            enabled = value < range.last,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}
