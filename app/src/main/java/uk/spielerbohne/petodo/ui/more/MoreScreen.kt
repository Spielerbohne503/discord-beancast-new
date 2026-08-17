package uk.spielerbohne.petodo.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.BuildConfig
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.nag.QuietHours
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MoreRoute(container: AppContainer, onOpenPermissions: () -> Unit) {
    val viewModel: MoreViewModel = viewModel(factory = MoreViewModel.factory(container))
    val quietHours by viewModel.quietHours.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()

    MoreScreen(
        quietHours = quietHours,
        lists = lists,
        tags = tags,
        onQuietHoursChange = viewModel::setQuietHours,
        onCreateList = viewModel::createList,
        onListColorChange = viewModel::setListColor,
        onListExcludeChange = viewModel::setExcludeFromNag,
        onDeleteList = viewModel::deleteList,
        onDeleteTag = viewModel::deleteTag,
        onOpenPermissions = onOpenPermissions,
    )
}

@Composable
fun MoreScreen(
    quietHours: QuietHours,
    lists: List<uk.spielerbohne.petodo.domain.model.TaskList>,
    tags: List<uk.spielerbohne.petodo.domain.model.Tag>,
    onQuietHoursChange: (LocalTime, LocalTime, Boolean) -> Unit,
    onCreateList: (String, Int?, Boolean) -> Unit,
    onListColorChange: (String, Int) -> Unit,
    onListExcludeChange: (String, Boolean) -> Unit,
    onDeleteList: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    var picking by remember { mutableStateOf<QuietHoursEdge?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.settings_quiet_hours_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_quiet_hours_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_quiet_hours_enabled))
                    Switch(
                        checked = quietHours.enabled,
                        onCheckedChange = {
                            onQuietHoursChange(quietHours.start, quietHours.end, it)
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { picking = QuietHoursEdge.START },
                        enabled = quietHours.enabled,
                        label = {
                            Text(
                                stringResource(R.string.settings_quiet_hours_from) + " " +
                                    quietHours.start.format(SHORT_TIME)
                            )
                        },
                    )
                    AssistChip(
                        onClick = { picking = QuietHoursEdge.END },
                        enabled = quietHours.enabled,
                        label = {
                            Text(
                                stringResource(R.string.settings_quiet_hours_to) + " " +
                                    quietHours.end.format(SHORT_TIME)
                            )
                        },
                    )
                }
            }
        }

        ListsSection(
            lists = lists,
            onCreate = onCreateList,
            onUpdateColor = onListColorChange,
            onToggleExclude = onListExcludeChange,
            onDelete = onDeleteList,
        )

        TagsSection(tags = tags, onDelete = onDeleteTag)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.settings_permissions_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_permissions_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenPermissions) {
                    Text(stringResource(R.string.onboarding_reopen))
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    picking?.let { edge ->
        QuietHoursTimeDialog(
            initial = if (edge == QuietHoursEdge.START) quietHours.start else quietHours.end,
            onDismiss = { picking = null },
            onConfirm = { time ->
                if (edge == QuietHoursEdge.START) {
                    onQuietHoursChange(time, quietHours.end, quietHours.enabled)
                } else {
                    onQuietHoursChange(quietHours.start, time, quietHours.enabled)
                }
                picking = null
            },
        )
    }
}

private enum class QuietHoursEdge { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursTimeDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val SHORT_TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
