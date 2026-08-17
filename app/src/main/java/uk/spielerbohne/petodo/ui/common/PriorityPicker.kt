package uk.spielerbohne.petodo.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.domain.model.Priority

/** Flaggen-Knopf mit Auswahlmenü — vier Stufen, wie im Datenmodell. */
@Composable
fun PriorityPicker(
    priority: Int,
    onPriorityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = if (PriorityUi.hasVisibleFlag(priority)) {
                    Icons.Filled.Flag
                } else {
                    Icons.Outlined.Flag
                },
                contentDescription = stringResource(R.string.detail_priority),
                tint = PriorityUi.color(priority),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PriorityUi.ALL.forEach { level ->
                DropdownMenuItem(
                    text = { Text(PriorityUi.label(level)) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (level == Priority.NORMAL) {
                                Icons.Outlined.Flag
                            } else {
                                Icons.Filled.Flag
                            },
                            contentDescription = null,
                            tint = PriorityUi.color(level),
                        )
                    },
                    onClick = {
                        onPriorityChange(level)
                        expanded = false
                    },
                )
            }
        }
    }
}
