package uk.spielerbohne.petodo.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Pet-Zustand — genau eine Zeile ([SINGLETON_ID]).
 *
 * Die Zeile ist ein Zwischenstand, kein Besitzer der Wahrheit: Sie hält den zuletzt
 * berechneten Stand samt Zeitpunkt, damit die Simulation nicht jedes Mal das gesamte
 * Ereignis-Log lesen muss.
 */
@Entity(tableName = "pet_state")
data class PetStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lastComputedAt: Long,
    val energy: Float,
    val satiety: Float,
    val mood: Float,
    val xp: Int,
    val level: Int,
    val skinId: String = "default",
    val lastFedAt: Long? = null,
    val lastPlayedAt: Long? = null,
    val lastPattedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
