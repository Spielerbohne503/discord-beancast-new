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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.spielerbohne.petodo.BuildConfig
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.ui.theme.GlassCard
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.nag.QuietHours
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MoreRoute(
    container: AppContainer,
    onOpenPermissions: () -> Unit,
    onOpenList: (String) -> Unit,
    onOpenStats: () -> Unit = {},
) {
    val viewModel: MoreViewModel = viewModel(factory = MoreViewModel.factory(container))
    val quietHours by viewModel.quietHours.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val focusSettings by viewModel.focusSettings.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(backupMessage) {
        val message = backupMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.toText(context))
        viewModel.clearBackupMessage()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
    MoreScreen(
        modifier = Modifier.padding(padding),
        quietHours = quietHours,
        lists = lists,
        tags = tags,
        focusSettings = focusSettings,
        onFocusSettingsChange = viewModel::setFocusSettings,
        onQuietHoursChange = viewModel::setQuietHours,
        onCreateList = viewModel::createList,
        onListColorChange = viewModel::setListColor,
        onListExcludeChange = viewModel::setExcludeFromNag,
        onDeleteList = viewModel::deleteList,
        onDeleteTag = viewModel::deleteTag,
        onOpenPermissions = onOpenPermissions,
        onOpenList = onOpenList,
        onOpenStats = onOpenStats,
        onExport = viewModel::exportTo,
        onRestore = viewModel::restoreFrom,
    )
    }
}

/** Übersetzt das Ergebnis in einen Satz — die Texte liegen wie alle anderen in strings.xml. */
private fun BackupMessage.toText(context: android.content.Context): String = when (this) {
    is BackupMessage.Exported -> context.getString(R.string.backup_exported, rows)
    is BackupMessage.Restored -> context.getString(R.string.backup_restored, inserted, updated, skipped)
    BackupMessage.NotABackup -> context.getString(R.string.backup_restore_invalid)
    BackupMessage.ExportFailed -> context.getString(R.string.backup_export_failed)
    BackupMessage.RestoreFailed -> context.getString(R.string.backup_restore_failed)
}

@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    quietHours: QuietHours,
    lists: List<uk.spielerbohne.petodo.domain.model.TaskList>,
    tags: List<uk.spielerbohne.petodo.domain.model.Tag>,
    focusSettings: uk.spielerbohne.petodo.domain.focus.FocusSettings =
        uk.spielerbohne.petodo.domain.focus.FocusSettings.DEFAULT,
    onFocusSettingsChange: (uk.spielerbohne.petodo.domain.focus.FocusSettings) -> Unit = {},
    onQuietHoursChange: (LocalTime, LocalTime, Boolean) -> Unit,
    onCreateList: (String, Int?, Boolean) -> Unit,
    onListColorChange: (String, Int) -> Unit,
    onListExcludeChange: (String, Boolean) -> Unit,
    onDeleteList: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenList: (String) -> Unit = {},
    onOpenStats: () -> Unit = {},
    onExport: (android.net.Uri) -> Unit = {},
    onRestore: (android.net.Uri) -> Unit = {},
) {
    var picking by remember { mutableStateOf<QuietHoursEdge?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
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

        FocusSettingsSection(settings = focusSettings, onChange = onFocusSettingsChange)

        ListsSection(
            lists = lists,
            onOpen = onOpenList,
            onCreate = onCreateList,
            onUpdateColor = onListColorChange,
            onToggleExclude = onListExcludeChange,
            onDelete = onDeleteList,
        )

        TagsSection(tags = tags, onDelete = onDeleteTag)

        BackupSection(onExport = onExport, onRestore = onRestore)

        // Der Rückblick steht über den Einstellungen: Er ist etwas, das man anschaut,
        // keins, das man einstellt.
        GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenStats) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.stats_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.stats_open),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
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
