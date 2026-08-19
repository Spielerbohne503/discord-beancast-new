package uk.spielerbohne.petodo.ui.habits

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
import uk.spielerbohne.petodo.data.repo.HabitRepository
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.habit.HabitSchedule
import uk.spielerbohne.petodo.domain.habit.HabitStreak
import uk.spielerbohne.petodo.domain.model.Habit
import java.time.Clock
import java.time.LocalDate

/** Eine Gewohnheit, fertig für die Anzeige. */
data class HabitRow(
    val habit: Habit,
    val checkedToday: Boolean,
    val dueToday: Boolean,
    val streak: Int,
    val longestStreak: Int,
    val weekDone: Int,
    val weekTotal: Int,
    val checkins: Set<LocalDate>,
)

data class HabitsUiState(
    val habits: List<HabitRow> = emptyList(),
    // Nicht LocalDate.EPOCH: Die Konstante gibt es erst ab Android 14, minSdk ist 26.
    val today: LocalDate = LocalDate.ofEpochDay(0),
) {
    val dueToday: List<HabitRow> get() = habits.filter { it.dueToday }
    val doneToday: Int get() = dueToday.count { it.checkedToday }
    val isEmpty: Boolean get() = habits.isEmpty()
}

/**
 * Gewohnheiten.
 *
 * Gerechnet wird in `domain/habit`; hier wird nur zusammengetragen. Der heutige Tag kommt
 * aus der Uhr des Containers und wird minütlich nachgezogen, damit ein Tageswechsel bei
 * offener App ankommt.
 */
class HabitsViewModel(
    private val repository: HabitRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock))

    fun refreshToday() {
        today.value = LocalDate.now(clock)
    }

    val state: StateFlow<HabitsUiState> = combine(
        repository.observeHabits(),
        today,
    ) { habits, heute ->
        HabitsUiState(
            today = heute,
            habits = habits.map { (habit, checkins) ->
                val (wocheErledigt, wocheGesamt) =
                    HabitStreak.weekProgress(checkins, habit.schedule, heute)

                HabitRow(
                    habit = habit,
                    checkedToday = heute in checkins,
                    dueToday = habit.isDueOn(heute),
                    streak = HabitStreak.current(checkins, habit.schedule, heute),
                    longestStreak = HabitStreak.longest(checkins, habit.schedule),
                    weekDone = wocheErledigt,
                    weekTotal = wocheGesamt,
                    checkins = checkins,
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HabitsUiState(today = LocalDate.now(clock)),
    )

    fun toggle(habit: Habit) {
        viewModelScope.launch {
            repository.toggleCheckin(habit.id, LocalDate.now(clock))
        }
    }

    fun create(name: String, schedule: HabitSchedule) {
        if (name.isBlank() || schedule.isEmpty) return
        viewModelScope.launch { repository.createHabit(name, schedule) }
    }

    fun update(id: String, name: String, schedule: HabitSchedule) {
        if (name.isBlank() || schedule.isEmpty) return
        viewModelScope.launch { repository.updateHabit(id, name = name, schedule = schedule) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteHabit(id) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HabitsViewModel(
                    repository = container.habitRepository,
                    clock = container.clock,
                )
            }
        }
    }
}
