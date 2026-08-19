package uk.spielerbohne.petodo.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import uk.spielerbohne.petodo.data.db.dao.HabitDao
import uk.spielerbohne.petodo.data.db.entity.HabitCheckinEntity
import uk.spielerbohne.petodo.data.db.entity.HabitEntity
import uk.spielerbohne.petodo.data.pet.RewardSink
import uk.spielerbohne.petodo.domain.Balance
import uk.spielerbohne.petodo.domain.habit.HabitSchedule
import uk.spielerbohne.petodo.domain.model.Habit
import uk.spielerbohne.petodo.domain.model.HabitWithCheckins
import uk.spielerbohne.petodo.domain.sort.FractionalIndex
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Gewohnheiten und ihre Haken.
 *
 * Dieselben Regeln wie überall: UUID als Schlüssel, Löschen ist ein Tombstone, jede
 * Änderung setzt `updatedAt`. Ein Haken gehört zu einem **Kalendertag**, gespeichert als
 * Epochentag — nicht als Zeitpunkt.
 */
class HabitRepository(
    private val habitDao: HabitDao,
    private val clock: Clock,
    /** Faul hereingereicht, damit kein Ring zwischen Gewohnheiten und Pet entsteht. */
    private val rewards: () -> RewardSink? = { null },
) {

    /**
     * Gewohnheiten samt Einträgen der letzten [Balance.HABIT_HISTORY_DAYS] Tage.
     *
     * Nicht die ganze Geschichte: Für Serien und Rückblick reicht ein gutes Jahr, und
     * alles zu laden würde mit jedem Jahr langsamer.
     */
    fun observeHabits(): Flow<List<HabitWithCheckins>> {
        val seit = LocalDate.now(clock).minusDays(Balance.HABIT_HISTORY_DAYS).toEpochDay()

        return combine(
            habitDao.observeHabits(),
            habitDao.observeCheckins(seit),
        ) { habits, checkins ->
            val nachGewohnheit = checkins.groupBy { it.habitId }
            habits.map { entity ->
                HabitWithCheckins(
                    habit = entity.toDomain(),
                    checkins = nachGewohnheit[entity.id]
                        .orEmpty()
                        .map { LocalDate.ofEpochDay(it.day) }
                        .toSet(),
                )
            }
        }
    }

    suspend fun createHabit(
        name: String,
        schedule: HabitSchedule,
        colorArgb: Int? = null,
    ): String {
        val now = Instant.now(clock).toEpochMilli()
        val id = UUID.randomUUID().toString()

        habitDao.insertHabit(
            HabitEntity(
                id = id,
                name = name.trim(),
                scheduleMask = schedule.mask,
                colorArgb = colorArgb,
                sortKey = FractionalIndex.after(habitDao.highestSortKey()),
                createdAt = now,
                updatedAt = now,
            )
        )
        return id
    }

    suspend fun updateHabit(
        id: String,
        name: String? = null,
        schedule: HabitSchedule? = null,
        colorArgb: Int? = null,
    ) {
        val entity = habitDao.findHabit(id) ?: return
        habitDao.updateHabit(
            entity.copy(
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: entity.name,
                scheduleMask = schedule?.mask ?: entity.scheduleMask,
                colorArgb = colorArgb ?: entity.colorArgb,
                updatedAt = Instant.now(clock).toEpochMilli(),
            )
        )
    }

    /** Löschen heißt Tombstone setzen — die Haken bleiben stehen, sie stören niemanden. */
    suspend fun deleteHabit(id: String) {
        val entity = habitDao.findHabit(id) ?: return
        val now = Instant.now(clock).toEpochMilli()
        habitDao.updateHabit(entity.copy(deletedAt = now, updatedAt = now))
    }

    /**
     * Einen Tag abhaken oder den Haken zurücknehmen.
     *
     * @return ob der Tag danach abgehakt ist.
     */
    suspend fun toggleCheckin(habitId: String, day: LocalDate): Boolean {
        val now = Instant.now(clock).toEpochMilli()
        val epochDay = day.toEpochDay()
        val vorhanden = habitDao.findCheckin(habitId, epochDay)

        // Dieselbe Zeile lebt wieder auf, statt eine zweite für denselben Tag anzulegen —
        // der Index über (habitId, day) ist eindeutig.
        val abgehakt = vorhanden == null || vorhanden.deletedAt != null

        if (vorhanden == null) {
            habitDao.insertCheckin(
                HabitCheckinEntity(
                    id = UUID.randomUUID().toString(),
                    habitId = habitId,
                    day = epochDay,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        } else {
            habitDao.updateCheckin(
                vorhanden.copy(
                    deletedAt = if (abgehakt) null else now,
                    updatedAt = now,
                )
            )
        }

        // Nur das Abhaken zahlt beim Pet ein. Zurücknehmen nimmt nichts weg — das Log
        // kennt keine Stornos, und ein Vertipper soll nicht bestraft werden.
        if (abgehakt) rewards()?.onHabitChecked()

        return abgehakt
    }

    private fun HabitEntity.toDomain() = Habit(
        id = id,
        name = name,
        schedule = HabitSchedule.of(scheduleMask),
        colorArgb = colorArgb,
        sortKey = sortKey,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    )
}
