package uk.spielerbohne.petodo.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.domain.Balance
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Datum und Uhrzeit wählen. Uhrzeit ist optional: ohne sie ist die Aufgabe ein
 * Tagestermin und wird erst nach Ablauf des Tages überfällig.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuePicker(
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    onDueDateChange: (LocalDate?) -> Unit,
    onDueTimeChange: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { showDatePicker = true },
            leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) },
            label = {
                Text(
                    dueDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        ?: stringResource(R.string.task_pick_date)
                )
            },
        )
        AssistChip(
            onClick = { showTimePicker = true },
            enabled = dueDate != null,
            leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
            label = {
                Text(
                    dueTime?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                        ?: stringResource(R.string.task_pick_time)
                )
            },
        )
        if (dueDate != null) {
            TextButton(onClick = {
                onDueDateChange(null)
                onDueTimeChange(null)
            }) { Text(stringResource(R.string.task_clear_due)) }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dueDate
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Der DatePicker liefert UTC-Mitternacht des gewählten Tages; daraus
                    // wird hier wieder ein Kalendertag, kein Zeitpunkt.
                    onDueDateChange(
                        state.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                    )
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = dueTime?.hour ?: Balance.DEFAULT_DUE_HOUR,
            initialMinute = dueTime?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.task_pick_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onDueTimeChange(LocalTime.of(state.hour, state.minute))
                    showTimePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDueTimeChange(null)
                    showTimePicker = false
                }) { Text(stringResource(R.string.task_no_due)) }
            },
        )
    }
}
