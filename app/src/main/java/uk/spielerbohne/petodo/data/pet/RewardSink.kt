package uk.spielerbohne.petodo.data.pet

import uk.spielerbohne.petodo.domain.model.Task

/**
 * Die Stelle, an der Arbeit zu Belohnung wird.
 *
 * Sie ist eine eigene Schnittstelle, damit [uk.spielerbohne.petodo.data.repo.TaskRepository]
 * nichts vom Pet weiß: Das Pet braucht die Aufgaben (für die Überfälligkeitslast), die
 * Aufgaben brauchen das Pet nicht. Ohne diese Trennung entstünde ein Ring.
 *
 * Verbucht wird **im Repository**, nicht im ViewModel — sonst zahlt die Benachrichtigung
 * mit „Erledigt“ nicht ein, und das Pet hungert, obwohl gearbeitet wurde.
 */
interface RewardSink {

    /** Aufgabe abgehakt. */
    suspend fun onTaskCompleted(task: Task)

    /**
     * Überfällige Aufgabe aufgeräumt — verschoben ODER gelöscht.
     *
     * Dass Löschen dasselbe gibt wie Erledigen, ist Absicht: Sonst belohnt die App
     * heimliches Wegräumen und bestraft Ehrlichkeit.
     */
    suspend fun onTaskCleaned(task: Task)

    /** Aufgabe erfasst. Gibt nur XP, und höchstens zehnmal am Tag. */
    suspend fun onTaskCreated()

    /** Fokusrunde regulär beendet. Abgebrochene Runden zählen nicht. */
    suspend fun onFocusCompleted()
}
