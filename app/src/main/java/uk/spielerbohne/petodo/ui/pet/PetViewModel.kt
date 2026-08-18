package uk.spielerbohne.petodo.ui.pet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.spielerbohne.petodo.data.pet.PetRepository
import uk.spielerbohne.petodo.data.pet.PetSnapshot
import uk.spielerbohne.petodo.data.settings.SettingsRepository
import uk.spielerbohne.petodo.di.AppContainer
import uk.spielerbohne.petodo.domain.pet.PetSpeech
import uk.spielerbohne.petodo.domain.pet.RewardType
import uk.spielerbohne.petodo.domain.pet.SpeechCategory
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class PetUiState(
    val snapshot: PetSnapshot,
    val name: String = "",
    val cooldowns: Map<RewardType, Duration> = emptyMap(),
) {
    val moodCategory: SpeechCategory get() = PetSpeech.moodCategory(snapshot.state.values)

    fun isAllowed(type: RewardType): Boolean =
        (cooldowns[type] ?: Duration.ZERO).isZero
}

/**
 * Der Pet-Bildschirm.
 *
 * Gerechnet wird nicht hier: [PetRepository.recompute] schreibt den Stand fort, dieser
 * Bildschirm zeigt ihn. Angestoßen wird das beim Öffnen und nach jeder Handlung — es
 * tickt nichts im Hintergrund.
 */
class PetViewModel(
    private val petRepository: PetRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Wechselt nur, wenn die Sperrzeiten neu abgelesen werden sollen. */
    private val tick = MutableStateFlow(Instant.now(clock))

    /** Die zuletzt gezeigte Sprechblase — damit sich das Pet nicht ständig wiederholt. */
    private val _bubble = MutableStateFlow<SpeechCategory?>(null)
    val bubble: StateFlow<SpeechCategory?> = _bubble.asStateFlow()

    init {
        refresh()
    }

    val state: StateFlow<PetUiState> = combine(
        petRepository.observe(),
        settingsRepository.petName,
        tick,
    ) { snapshot, name, now ->
        PetUiState(
            snapshot = snapshot,
            name = name,
            cooldowns = CARE_ACTIONS.associateWith {
                petRepository.remainingCooldown(snapshot.state, it, now)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PetUiState(PetSnapshot.empty(Instant.now(clock))),
    )

    /** Stand auf jetzt fortschreiben — beim Öffnen des Bildschirms. */
    fun refresh() {
        viewModelScope.launch {
            petRepository.recompute()
            tick.value = Instant.now(clock)
        }
    }

    /**
     * Füttern, Spielen, Streicheln. Läuft die Sperre noch, passiert nichts — der Knopf
     * sagt selbst, wie lange noch.
     */
    fun care(type: RewardType) {
        viewModelScope.launch {
            val done = petRepository.care(type)
            tick.value = Instant.now(clock)
            if (done) {
                _bubble.value = when (type) {
                    RewardType.FEED -> SpeechCategory.FED
                    RewardType.PLAY -> SpeechCategory.PLAYED
                    else -> SpeechCategory.PATTED
                }
            }
        }
    }

    fun clearBubble() {
        _bubble.value = null
    }

    fun rename(name: String) {
        viewModelScope.launch { settingsRepository.setPetName(name) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        val CARE_ACTIONS = listOf(RewardType.FEED, RewardType.PLAY, RewardType.PAT)

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PetViewModel(
                    petRepository = container.petRepository,
                    settingsRepository = container.settingsRepository,
                    clock = container.clock,
                )
            }
        }
    }
}
