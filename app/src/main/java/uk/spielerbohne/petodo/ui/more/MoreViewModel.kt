package uk.spielerbohne.petodo.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.spielerbohne.petodo.data.alarm.NagCoordinator
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import uk.spielerbohne.petodo.data.backup.BackupRepository
import uk.spielerbohne.petodo.data.repo.TagRepository
import uk.spielerbohne.petodo.data.repo.TaskListRepository
import uk.spielerbohne.petodo.data.settings.SettingsRepository
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.model.Tag
import uk.spielerbohne.petodo.domain.model.TaskList
import uk.spielerbohne.petodo.domain.nag.QuietHours
import java.time.LocalTime

/** Rückmeldung nach einer Sicherung oder Wiederherstellung. */
sealed interface BackupMessage {
    data class Exported(val rows: Int) : BackupMessage
    data class Restored(val inserted: Int, val updated: Int, val skipped: Int) : BackupMessage
    data object NotABackup : BackupMessage
    data object ExportFailed : BackupMessage
    data object RestoreFailed : BackupMessage
}

class MoreViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val contentResolver: ContentResolver,
    private val taskListRepository: TaskListRepository,
    private val tagRepository: TagRepository,
    private val nagCoordinator: NagCoordinator,
) : ViewModel() {

    val lists: StateFlow<List<TaskList>> = taskListRepository.observeLists().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
    )

    val tags: StateFlow<List<Tag>> = tagRepository.observeTags().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
    )

    fun createList(name: String, colorArgb: Int?, excludeFromNag: Boolean) {
        viewModelScope.launch { taskListRepository.createList(name, colorArgb, excludeFromNag) }
    }

    fun setListColor(id: String, colorArgb: Int) {
        viewModelScope.launch { taskListRepository.updateList(id, colorArgb = colorArgb) }
    }

    /** Eine Liste auf "nicht mahnen" umzustellen verstummt ihre Alarme sofort. */
    fun setExcludeFromNag(id: String, exclude: Boolean) {
        viewModelScope.launch {
            taskListRepository.updateList(id, excludeFromNag = exclude)
            withContext(Dispatchers.IO) { nagCoordinator.rescheduleAll() }
        }
    }

    fun deleteList(id: String) {
        viewModelScope.launch { taskListRepository.deleteList(id) }
    }

    fun deleteTag(id: String) {
        viewModelScope.launch { tagRepository.deleteTag(id) }
    }

    // ------------------------------------------------------------------------ Sicherung

    private val _backupMessage = MutableStateFlow<BackupMessage?>(null)
    val backupMessage: StateFlow<BackupMessage?> = _backupMessage

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _backupMessage.value = withContext(Dispatchers.IO) {
                runCatching {
                    val rows = backupRepository.export().rowCount
                    contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        backupRepository.writeTo(output)
                    } ?: error("Kein Schreibzugriff auf $uri")
                    BackupMessage.Exported(rows)
                }.getOrElse { throwable ->
                    Log.e(TAG, "Sicherung fehlgeschlagen", throwable)
                    BackupMessage.ExportFailed
                }
            }
        }
    }

    fun restoreFrom(uri: Uri) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val report = contentResolver.openInputStream(uri)?.use { input ->
                        backupRepository.restoreFrom(input)
                    }
                    when (report) {
                        null -> BackupMessage.NotABackup
                        else -> BackupMessage.Restored(report.inserted, report.updated, report.skipped)
                    }
                }.getOrElse { throwable ->
                    Log.e(TAG, "Wiederherstellen fehlgeschlagen", throwable)
                    BackupMessage.RestoreFailed
                }
            }
            _backupMessage.value = message
            // Wiederhergestellte Fälligkeiten brauchen ihre Alarme zurück.
            if (message is BackupMessage.Restored) {
                withContext(Dispatchers.IO) { nagCoordinator.rescheduleAll() }
            }
        }
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    val quietHours: StateFlow<QuietHours> = settingsRepository.quietHours.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = QuietHours.DEFAULT,
    )

    fun setQuietHours(start: LocalTime, end: LocalTime, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setQuietHours(start, end, enabled)
            // Eine geänderte Ruhezeit verschiebt bereits gesetzte Alarme.
            withContext(Dispatchers.IO) { nagCoordinator.rescheduleAll() }
        }
    }

    companion object {
        private const val TAG = "MoreViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MoreViewModel(
                    settingsRepository = container.settingsRepository,
                    backupRepository = container.backupRepository,
                    contentResolver = container.contentResolver,
                    taskListRepository = container.taskListRepository,
                    tagRepository = container.tagRepository,
                    nagCoordinator = container.nagCoordinator,
                )
            }
        }
    }
}
