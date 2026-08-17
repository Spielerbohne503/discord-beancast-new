package uk.spielerbohne.petodo.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.spielerbohne.petodo.data.db.dao.FocusSessionDao
import uk.spielerbohne.petodo.data.db.entity.FocusSessionEntity
import uk.spielerbohne.petodo.domain.focus.FocusPhase
import uk.spielerbohne.petodo.domain.focus.FocusSession
import uk.spielerbohne.petodo.domain.focus.FocusSettings
import uk.spielerbohne.petodo.domain.focus.FocusTimer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Fokussitzungen.
 *
 * Gespeichert wird immer der **absolute** Endzeitpunkt. Anhalten merkt sich den Moment
 * in `pausedAt`; daraus rechnet [FocusTimer] den Rest — nirgends läuft ein Zähler mit,
 * der im Energiesparmodus stehen bleiben könnte.
 */
class FocusRepository(
    private val focusSessionDao: FocusSessionDao,
    private val clock: Clock,
) {

    fun observeActiveSession(): Flow<FocusSession?> =
        focusSessionDao.observeActive().map { it?.toDomain() }

    suspend fun activeSession(): FocusSession? = focusSessionDao.active()?.toDomain()

    /** Abgeschlossene Fokusrunden von heute. */
    fun observeCompletedRoundsToday(): Flow<Int> {
        val today = LocalDate.now(clock)
        val start = today.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return focusSessionDao.observeCompletedFocusCount(start, end)
    }

    suspend fun completedRoundsToday(): Int {
        val today = LocalDate.now(clock)
        val start = today.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return focusSessionDao.completedFocusCount(start, end)
    }

    /**
     * Startet einen Abschnitt. Ein noch offener Durchlauf gilt dabei als abgebrochen —
     * es gibt immer höchstens einen.
     */
    suspend fun start(
        phase: FocusPhase,
        settings: FocusSettings,
        taskId: String? = null,
    ): FocusSession {
        val now = Instant.now(clock)
        abortActive(now)

        val session = FocusSession(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            phase = phase,
            startedAt = now,
            endsAt = FocusTimer.endsAt(now, phase, settings),
        )
        focusSessionDao.insert(session.toEntity(now.toEpochMilli()))
        return session
    }

    suspend fun pause(): FocusSession? {
        val entity = focusSessionDao.active() ?: return null
        if (entity.pausedAt != null) return entity.toDomain()

        val now = Instant.now(clock).toEpochMilli()
        val paused = entity.copy(pausedAt = now, updatedAt = now)
        focusSessionDao.update(paused)
        return paused.toDomain()
    }

    /** Fortsetzen: Der Rest bleibt gleich, der Endzeitpunkt wandert nach hinten. */
    suspend fun resume(): FocusSession? {
        val entity = focusSessionDao.active() ?: return null
        val session = entity.toDomain()
        if (!session.isPaused) return session

        val now = Instant.now(clock)
        val resumed = entity.copy(
            endsAt = FocusTimer.resumedEndsAt(session, now).toEpochMilli(),
            pausedAt = null,
            updatedAt = now.toEpochMilli(),
        )
        focusSessionDao.update(resumed)
        return resumed.toDomain()
    }

    /** Abbrechen. Abgebrochene Runden geben keine Belohnung. */
    suspend fun abort(): FocusSession? {
        val now = Instant.now(clock)
        return abortActive(now)
    }

    /** Regulär abschließen — nur das zählt als Runde. */
    suspend fun complete(sessionId: String): FocusSession? {
        val entity = focusSessionDao.findById(sessionId) ?: return null
        if (entity.completedAt != null || entity.abortedAt != null) return entity.toDomain()

        val now = Instant.now(clock).toEpochMilli()
        val completed = entity.copy(completedAt = now, pausedAt = null, updatedAt = now)
        focusSessionDao.update(completed)
        return completed.toDomain()
    }

    private suspend fun abortActive(now: Instant): FocusSession? {
        val entity = focusSessionDao.active() ?: return null
        val millis = now.toEpochMilli()
        val aborted = entity.copy(abortedAt = millis, updatedAt = millis)
        focusSessionDao.update(aborted)
        return aborted.toDomain()
    }

    // ------------------------------------------------------------------------- Umwandeln

    private fun FocusSessionEntity.toDomain(): FocusSession = FocusSession(
        id = id,
        taskId = taskId,
        // Eine unbekannte Phase aus der Datenbank darf den Start nicht verhindern.
        phase = FocusPhase.parse(kind) ?: FocusPhase.FOCUS,
        startedAt = Instant.ofEpochMilli(startedAt),
        endsAt = Instant.ofEpochMilli(endsAt),
        pausedAt = pausedAt?.let(Instant::ofEpochMilli),
        completedAt = completedAt?.let(Instant::ofEpochMilli),
        abortedAt = abortedAt?.let(Instant::ofEpochMilli),
    )

    private fun FocusSession.toEntity(now: Long): FocusSessionEntity = FocusSessionEntity(
        id = id,
        taskId = taskId,
        startedAt = startedAt.toEpochMilli(),
        endsAt = endsAt.toEpochMilli(),
        kind = phase.name,
        pausedAt = pausedAt?.toEpochMilli(),
        completedAt = completedAt?.toEpochMilli(),
        abortedAt = abortedAt?.toEpochMilli(),
        createdAt = now,
        updatedAt = now,
    )
}
