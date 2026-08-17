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
import uk.spielerbohne.petodo.data.repo.TagRepository
import uk.spielerbohne.petodo.data.repo.TaskListRepository
import uk.spielerbohne.petodo.data.settings.SettingsRepository
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.model.Tag
import uk.spielerbohne.petodo.domain.model.TaskList
import uk.spielerbohne.petodo.domain.nag.QuietHours
import java.time.LocalTime

class MoreViewModel(
    private val settingsRepository: SettingsRepository,
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
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MoreViewModel(
                    settingsRepository = container.settingsRepository,
                    taskListRepository = container.taskListRepository,
                    tagRepository = container.tagRepository,
                    nagCoordinator = container.nagCoordinator,
                )
            }
        }
    }
}
