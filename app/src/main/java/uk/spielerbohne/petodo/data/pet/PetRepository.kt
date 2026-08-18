package uk.spielerbohne.petodo.data.pet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import uk.spielerbohne.petodo.data.db.dao.PetStateDao
import uk.spielerbohne.petodo.data.db.dao.RewardEventDao
import uk.spielerbohne.petodo.data.db.entity.PetStateEntity
import uk.spielerbohne.petodo.data.db.entity.RewardEventEntity
import uk.spielerbohne.petodo.data.repo.TaskRepository
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.domain.pet.OverdueLoad
import uk.spielerbohne.petodo.domain.pet.PetSimulation
import uk.spielerbohne.petodo.domain.pet.PetState
import uk.spielerbohne.petodo.domain.pet.PetValues
import uk.spielerbohne.petodo.domain.pet.RewardEvent
import uk.spielerbohne.petodo.domain.pet.RewardRules
import uk.spielerbohne.petodo.domain.pet.RewardType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Was auf dem Pet-Bildschirm steht: der gespeicherte Stand plus die Lage drumherum.
 */
data class PetSnapshot(
    val state: PetState,
    val load: Double,
    val overdueCount: Int,
) {
    companion object {
        fun empty(at: Instant) = PetSnapshot(PetState.initial(at), load = 0.0, overdueCount = 0)
    }
}

/**
 * Pet-Zustand und Belohnungs-Log.
 *
 * Zwei Regeln aus dem Projektplan halten hier zusammen:
 *
 *  - `reward_events` wird **nur beschrieben, nie geändert** — das Log ist die Wahrheit.
 *  - Gerechnet wird ausschließlich in [PetSimulation.compute]. Die Zeile in `pet_state`
 *    ist ein Zwischenstand, damit nicht jedes Mal das ganze Log gelesen werden muss.
 *
 * Es tickt nichts: Gerechnet wird beim Öffnen der App und bei jedem Ereignis.
 */
class PetRepository(
    private val petStateDao: PetStateDao,
    private val rewardEventDao: RewardEventDao,
    private val taskRepository: TaskRepository,
    private val clock: Clock,
) : RewardSink {

    /**
     * Der angezeigte Stand. Es wird hier **nicht** nachgerechnet — sonst gäbe es eine
     * zweite Rechenstelle. [recompute] schreibt den Stand fort, dieser Fluss zeigt ihn.
     */
    fun observe(): Flow<PetSnapshot> = combine(
        petStateDao.observe(),
        taskRepository.observeTasks(),
        taskRepository.observeLists(),
    ) { entity, tasks, lists ->
        val now = Instant.now(clock)
        val excluded = lists.filter { it.excludeFromNag }.map { it.id }.toSet()
        val overdue = tasks.filter {
            it.isOpen && it.listId !in excluded && it.isOverdue(now, clock.zone)
        }
        PetSnapshot(
            state = entity?.toDomain() ?: PetState.initial(now),
            load = OverdueLoad.of(tasks, excluded, now, clock.zone),
            overdueCount = overdue.size,
        )
    }

    /**
     * Rechnet den Stand auf jetzt fort und schreibt ihn zurück.
     *
     * [extra] sind Ereignisse, die gerade erst eingefügt wurden — sie stehen zwar schon
     * im Log, könnten aber je nach Millisekunde am Filter unten vorbeirutschen. Doppelt
     * gezählt wird nichts: [RewardEvent.id] entscheidet.
     */
    suspend fun recompute(extra: List<RewardEvent> = emptyList()): PetState {
        val now = Instant.now(clock)
        val stored = petStateDao.get()
        val previous = stored?.toDomain() ?: PetState.initial(now)

        val pending = rewardEventDao
            .since(previous.lastComputedAt.toEpochMilli())
            .mapNotNull(RewardEventEntity::toDomainOrNull)
        val events = (pending + extra).distinctBy { it.id }

        val next = PetSimulation.compute(previous, events, now, currentLoad(now))
        persist(next, stored)
        return next
    }

    /** Der aktuelle Stand, auf jetzt fortgeschrieben. */
    suspend fun currentState(): PetState = recompute()

    /**
     * Der zuletzt gespeicherte Stand, **ohne** nachzurechnen.
     *
     * Für die Statuszeile: Sie zeichnet sich sekündlich neu, und jedes Neuzeichnen wäre
     * sonst ein Schreibzugriff. Angezeigt wird damit der Stand der letzten Rechnung —
     * die passiert beim Start der App und bei jedem Ereignis.
     */
    suspend fun storedState(): PetState =
        petStateDao.get()?.toDomain() ?: PetState.initial(Instant.now(clock))

    // ------------------------------------------------------------------ Pflege-Handlungen

    /**
     * Füttern, Spielen, Streicheln. Gibt `false` zurück, wenn die Sperrzeit noch läuft —
     * ohne sie tippt man sich aus jeder Krankheit heraus.
     */
    suspend fun care(type: RewardType): Boolean {
        val now = Instant.now(clock)
        val state = recompute()
        if (!RewardRules.isAllowed(type, state.lastAt(type), now)) return false
        award(type)
        return true
    }

    /** Wie lange die Sperre am Knopf noch läuft. */
    fun remainingCooldown(state: PetState, type: RewardType, now: Instant = Instant.now(clock)): Duration =
        RewardRules.remainingCooldown(type, state.lastAt(type), now)

    // ------------------------------------------------------------------------ RewardSink

    override suspend fun onTaskCompleted(task: Task) {
        award(RewardType.TASK_DONE, refId = task.id)
    }

    override suspend fun onTaskCleaned(task: Task) {
        award(RewardType.TASK_CLEANED, refId = task.id)
    }

    override suspend fun onTaskCreated() {
        val since = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val heute = rewardEventDao.countSince(RewardType.TASK_CREATED.name, since)
        if (!RewardRules.isTaskCreationRewardAllowed(heute)) return
        award(RewardType.TASK_CREATED)
    }

    override suspend fun onFocusCompleted() {
        award(RewardType.FOCUS_DONE)
    }

    // ----------------------------------------------------------------------------- Intern

    /** Schreibt ins Log und rechnet einmal nach. Der einzige Weg, der Werte verändert. */
    private suspend fun award(type: RewardType, refId: String? = null): RewardEvent {
        val now = Instant.now(clock)
        val event = RewardEvent.of(UUID.randomUUID().toString(), now, type, refId)
        rewardEventDao.insert(event.toEntity(now.toEpochMilli()))
        recompute(extra = listOf(event))
        return event
    }

    private suspend fun currentLoad(now: Instant): Double {
        val excluded = taskRepository.listIdsExcludedFromNag()
        return OverdueLoad.of(taskRepository.openTasksWithDueDate(), excluded, now, clock.zone)
    }

    private suspend fun persist(state: PetState, stored: PetStateEntity?) {
        val millis = Instant.now(clock).toEpochMilli()
        petStateDao.upsert(
            PetStateEntity(
                lastComputedAt = state.lastComputedAt.toEpochMilli(),
                energy = state.values.energy.toFloat(),
                satiety = state.values.satiety.toFloat(),
                mood = state.values.mood.toFloat(),
                xp = state.xp,
                level = state.level,
                skinId = stored?.skinId ?: DEFAULT_SKIN,
                lastFedAt = state.lastFedAt?.toEpochMilli(),
                lastPlayedAt = state.lastPlayedAt?.toEpochMilli(),
                lastPattedAt = state.lastPattedAt?.toEpochMilli(),
                createdAt = stored?.createdAt ?: millis,
                updatedAt = millis,
            )
        )
    }

    private fun PetStateEntity.toDomain() = PetState(
        values = PetValues.of(energy.toDouble(), satiety.toDouble(), mood.toDouble()),
        xp = xp,
        lastComputedAt = Instant.ofEpochMilli(lastComputedAt),
        lastFedAt = lastFedAt?.let(Instant::ofEpochMilli),
        lastPlayedAt = lastPlayedAt?.let(Instant::ofEpochMilli),
        lastPattedAt = lastPattedAt?.let(Instant::ofEpochMilli),
    )

    private fun RewardEvent.toEntity(now: Long) = RewardEventEntity(
        id = id,
        at = at.toEpochMilli(),
        type = type.name,
        dEnergy = dEnergy,
        dSatiety = dSatiety,
        dMood = dMood,
        dXp = dXp,
        refId = refId,
        createdAt = now,
        updatedAt = now,
    )

    private companion object {
        const val DEFAULT_SKIN = "default"
    }
}

/** Ein unbekannter Typ aus der Datenbank wird übergangen, nicht geworfen. */
private fun RewardEventEntity.toDomainOrNull(): RewardEvent? {
    val parsed = RewardType.parse(type) ?: return null
    return RewardEvent(
        id = id,
        at = Instant.ofEpochMilli(at),
        type = parsed,
        dEnergy = dEnergy,
        dSatiety = dSatiety,
        dMood = dMood,
        dXp = dXp,
        refId = refId,
    )
}
