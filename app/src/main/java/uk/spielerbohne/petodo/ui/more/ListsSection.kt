package uk.spielerbohne.petodo.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import uk.spielerbohne.petodo.ui.theme.GlassCard
import uk.spielerbohne.petodo.domain.model.Tag
import uk.spielerbohne.petodo.domain.model.TaskList

/** Auswahl für Listenfarben — bewusst wenige, damit die Streifen unterscheidbar bleiben. */
val LIST_COLORS = listOf(
    0xFF8E24AA.toInt(),
    0xFF3949AB.toInt(),
    0xFF00897B.toInt(),
    0xFF7CB342.toInt(),
    0xFFF9A825.toInt(),
    0xFFE53935.toInt(),
)

@Composable
fun ListsSection(
    lists: List<TaskList>,
    onOpen: (String) -> Unit,
    onCreate: (String, Int?, Boolean) -> Unit,
    onUpdateColor: (String, Int) -> Unit,
    onToggleExclude: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showNew by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.lists_title), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showNew = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.lists_new))
                }
            }

            lists.forEach { list ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = list.name,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpen(list.id) },
                        )
                        if (list.id != TaskList.ID_INBOX) {
                            IconButton(onClick = { onDelete(list.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.lists_delete),
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LIST_COLORS.forEach { color ->
                            ColorDot(
                                color = color,
                                selected = list.colorArgb == color,
                                onClick = { onUpdateColor(list.id, color) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = list.excludeFromNag,
                            onCheckedChange = { onToggleExclude(list.id, it) },
                        )
                        Text(
                            text = stringResource(R.string.lists_exclude_from_nag),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showNew) {
        NewListDialog(
            onDismiss = { showNew = false },
            onCreate = { name, color, exclude ->
                onCreate(name, color, exclude)
                showNew = false
            },
        )
    }
}

@Composable
private fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(Color(color), CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun NewListDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int?, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(LIST_COLORS.first()) }
    var exclude by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lists_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.lists_name_hint)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LIST_COLORS.forEach { candidate ->
                        ColorDot(
                            color = candidate,
                            selected = candidate == color,
                            onClick = { color = candidate },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = exclude, onCheckedChange = { exclude = it })
                    Text(
                        text = stringResource(R.string.lists_exclude_from_nag),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, color, exclude) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun TagsSection(tags: List<Tag>, onDelete: (String) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.tags_title), style = MaterialTheme.typography.titleMedium)

            if (tags.isEmpty()) {
                Text(
                    text = stringResource(R.string.tags_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            tags.forEach { tag ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tag_hash, tag.name), modifier = Modifier.weight(1f))
                    IconButton(onClick = { onDelete(tag.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.tags_delete),
                        )
                    }
                }
            }
        }
    }
}
