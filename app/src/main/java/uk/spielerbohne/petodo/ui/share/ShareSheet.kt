package uk.spielerbohne.petodo.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.domain.model.Priority
import uk.spielerbohne.petodo.domain.text.SharedTask
import uk.spielerbohne.petodo.ui.common.PriorityPicker
import uk.spielerbohne.petodo.ui.theme.Brand
import uk.spielerbohne.petodo.ui.theme.GlassCard
import uk.spielerbohne.petodo.ui.theme.SectionLabel
import uk.spielerbohne.petodo.ui.today.DuePicker
import java.time.LocalDate
import java.time.LocalTime

/**
 * Der Dialog beim Teilen.
 *
 * Bewusst klein: Titel, Fälligkeit, Priorität — mehr braucht das Erfassen nicht, und
 * jedes weitere Feld verlängert den Weg zurück in die App, aus der man kam. Alles andere
 * lässt sich später auf der Detailseite nachtragen.
 */
@Composable
internal fun ShareSheet(
    initial: SharedTask,
    onSave: OnShareSave,
    onCancel: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var note by remember { mutableStateOf(initial.note.orEmpty()) }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var dueTime by remember { mutableStateOf<LocalTime?>(null) }
    var priority by remember { mutableStateOf(Priority.DEFAULT) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionLabel(
                    text = stringResource(R.string.share_title),
                    accent = Brand.Cool.first(),
                )

                TextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = transparentColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                TextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text(stringResource(R.string.share_note_hint)) },
                    colors = transparentColors(),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DuePicker(
                        dueDate = dueDate,
                        dueTime = dueTime,
                        onDueDateChange = { dueDate = it },
                        onDueTimeChange = { dueTime = it },
                        modifier = Modifier.weight(1f),
                    )
                    PriorityPicker(priority = priority, onPriorityChange = { priority = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(
                        onClick = { onSave(title.trim(), note.trim().ifEmpty { null }, dueDate, dueTime, priority) },
                        enabled = title.isNotBlank(),
                    ) { Text(stringResource(R.string.share_save)) }
                }
            }
        }
    }
}

@Composable
private fun transparentColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)
