package uk.spielerbohne.petodo.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.spielerbohne.petodo.data.repo.FocusRepository
import uk.spielerbohne.petodo.data.repo.TaskRepository
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.stats.Statistics
import uk.spielerbohne.petodo.domain.stats.Stats
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Der Rückblick.
 *
 * Gerechnet wird in `domain/stats`; hier wird nur eingesammelt und in Ortszeit
 * umgerechnet. Die Serie hängt an Kalendertagen, nicht an Zeitpunkten — sonst zerrisse
 * sie eine Zeitzone.
 */
class StatsViewModel(
    private val taskRepository: TaskRepository,
    private val focusRepository: FocusRepository,
    private val clock: Clock,
) : ViewModel() {

    private val focusRounds = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            val seit = Instant.now(clock).minusSeconds(FOCUS_WINDOW_DAYS * 24 * 3600)
            focusRounds.value = focusRepository.completedRoundsSince(seit)
        }
    }

    val state: StateFlow<Stats> = combine(
        taskRepository.observeTasks(),
        focusRounds,
    ) { tasks, runden ->
        val zone = clock.zone
        val erledigt = tasks
            .filter { !it.isDeleted }
            .mapNotNull { it.completedAt?.atZone(zone)?.toLocalDate() }

        Statistics.of(
            completedDates = erledigt,
            today = LocalDate.now(clock),
            windowDays = WINDOW_DAYS,
            focusRounds = runden,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = Stats(),
    )

    companion object {
        /** Zwei Wochen passen als Balken nebeneinander auf ein Telefon. */
        const val WINDOW_DAYS = 14L
        private const val FOCUS_WINDOW_DAYS = 30L
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StatsViewModel(
                    taskRepository = container.taskRepository,
                    focusRepository = container.focusRepository,
                    clock = container.clock,
                )
            }
        }
    }
}
