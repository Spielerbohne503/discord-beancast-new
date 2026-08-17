package uk.spielerbohne.petodo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Belohnungsereignis — **append only**.
 *
 * Es gibt bewusst kein UPDATE und kein DELETE auf dieser Tabelle: Die Werte des Pets
 * haben genau einen Besitzer, und das ist die reine Rechenfunktion, die dieses Log liest.
 * Niemand rechnet an zweiter Stelle nach.
 *
 * [updatedAt] und [deletedAt] existieren nur, weil die Tabelle später synchronisiert wird
 * (Tombstone beim Aufräumen alter Ereignisse) — nicht, um Ereignisse zu korrigieren.
 */
@Entity(
    tableName = "reward_events",
    indices = [Index("at"), Index("type")],
)
data class RewardEventEntity(
    @PrimaryKey val id: String,
    val at: Long,
    /** TASK_DONE · TASK_CLEANED · FOCUS_DONE · FEED · PLAY · PAT · TASK_CREATED */
    val type: String,
    val dEnergy: Int,
    val dSatiety: Int,
    val dMood: Int,
    val dXp: Int,
    val refId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
