package uk.spielerbohne.petodo.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.domain.recurrence.RecurrenceRule
import uk.spielerbohne.petodo.domain.recurrence.RecurrenceRule.Frequency

/**
 * Voreinstellungen für Wiederholungen.
 *
 * Bewusst wenige: Täglich, Werktags, Wöchentlich, Monatlich, Jährlich. Eine Regel, die
 * aus einem späteren Sync kommt und nicht in dieses Raster passt, wird angezeigt und
 * unverändert gelassen, statt beim Antippen still überschrieben zu werden.
 */
object RecurrencePresets {

    val ALL: List<RecurrenceRule?> = listOf(
        null,
        RecurrenceRule(Frequency.DAILY),
        RecurrenceRule(Frequency.WEEKLY, byDay = RecurrenceRule.WEEKDAYS),
        RecurrenceRule(Frequency.WEEKLY),
        RecurrenceRule(Frequency.MONTHLY),
        RecurrenceRule(Frequency.YEARLY),
    )

    @Composable
    fun label(rule: RecurrenceRule?): String = when {
        rule == null -> stringResource(R.string.recurrence_none)
        rule == RecurrenceRule(Frequency.DAILY) -> stringResource(R.string.recurrence_daily)
        rule == RecurrenceRule(Frequency.WEEKLY, byDay = RecurrenceRule.WEEKDAYS) ->
            stringResource(R.string.recurrence_weekdays)
        rule == RecurrenceRule(Frequency.WEEKLY) -> stringResource(R.string.recurrence_weekly)
        rule == RecurrenceRule(Frequency.MONTHLY) -> stringResource(R.string.recurrence_monthly)
        rule == RecurrenceRule(Frequency.YEARLY) -> stringResource(R.string.recurrence_yearly)
        else -> stringResource(R.string.recurrence_custom)
    }
}

@Composable
fun RecurrencePicker(
    rule: RecurrenceRule?,
    enabled: Boolean,
    onRuleChange: (RecurrenceRule?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    AssistChip(
        onClick = { expanded = true },
        enabled = enabled,
        modifier = modifier,
        leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null) },
        label = { Text(RecurrencePresets.label(rule)) },
    )

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        RecurrencePresets.ALL.forEach { preset ->
            DropdownMenuItem(
                text = { Text(RecurrencePresets.label(preset)) },
                onClick = {
                    onRuleChange(preset)
                    expanded = false
                },
            )
        }
    }
}
