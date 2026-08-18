package uk.spielerbohne.petodo.domain.pet

import uk.spielerbohne.petodo.domain.Balance
import java.time.Duration
import java.time.Instant

/**
 * Die Belohnungstabelle aus Abschnitt 5.5 des Projektplans.
 *
 * Die Zahlen stehen in [Balance] — hier steht nur, welcher Anlass welche bekommt.
 */
enum class RewardType(
    val dEnergy: Int,
    val dSatiety: Int,
    val dMood: Int,
    val dXp: Int,
    /** Sperrzeit in Minuten, `null` = keine. */
    val cooldownMinutes: Long?,
) {
    TASK_DONE(
        Balance.REWARD_TASK_DONE_ENERGY,
        Balance.REWARD_TASK_DONE_SATIETY,
        Balance.REWARD_TASK_DONE_MOOD,
        Balance.REWARD_TASK_DONE_XP,
        null,
    ),

    /**
     * Aufgeräumt heißt **verschoben ODER gelöscht** — und nur bei überfälligen Aufgaben.
     * Dass Löschen dasselbe gibt wie Erledigen, ist kein Fehler: Ohne diese Regel
     * bestraft man Ehrlichkeit und belohnt heimliches Löschen.
     */
    TASK_CLEANED(
        Balance.REWARD_TASK_CLEANED_ENERGY,
        Balance.REWARD_TASK_CLEANED_SATIETY,
        Balance.REWARD_TASK_CLEANED_MOOD,
        Balance.REWARD_TASK_CLEANED_XP,
        null,
    ),

    FOCUS_DONE(
        Balance.REWARD_FOCUS_DONE_ENERGY,
        Balance.REWARD_FOCUS_DONE_SATIETY,
        Balance.REWARD_FOCUS_DONE_MOOD,
        Balance.REWARD_FOCUS_DONE_XP,
        null,
    ),

    FEED(
        Balance.REWARD_FEED_ENERGY,
        Balance.REWARD_FEED_SATIETY,
        Balance.REWARD_FEED_MOOD,
        Balance.REWARD_FEED_XP,
        Balance.COOLDOWN_FEED_MINUTES,
    ),

    PLAY(
        Balance.REWARD_PLAY_ENERGY,
        Balance.REWARD_PLAY_SATIETY,
        Balance.REWARD_PLAY_MOOD,
        Balance.REWARD_PLAY_XP,
        Balance.COOLDOWN_PLAY_MINUTES,
    ),

    PAT(
        Balance.REWARD_PAT_ENERGY,
        Balance.REWARD_PAT_SATIETY,
        Balance.REWARD_PAT_MOOD,
        Balance.REWARD_PAT_XP,
        Balance.COOLDOWN_PAT_MINUTES,
    ),

    /**
     * XP fürs Erfassen. Wenn Eintragen nur schaden kann, trägt man nichts mehr ein — und
     * die App ist tot. Krankheit hängt deshalb ausschließlich an überfälligen Aufgaben.
     */
    TASK_CREATED(0, 0, 0, Balance.REWARD_TASK_CREATED_XP, null);

    companion object {
        /** Unbekannte Typen aus der Datenbank werden übergangen, nicht geworfen. */
        fun parse(value: String?): RewardType? = entries.firstOrNull { it.name == value }
    }
}

/** Ein verbuchtes Ereignis aus dem Append-only-Log. */
data class RewardEvent(
    val id: String,
    val at: Instant,
    val type: RewardType,
    val dEnergy: Int,
    val dSatiety: Int,
    val dMood: Int,
    val dXp: Int,
    val refId: String? = null,
) {
    companion object {
        fun of(id: String, at: Instant, type: RewardType, refId: String? = null) = RewardEvent(
            id = id,
            at = at,
            type = type,
            dEnergy = type.dEnergy,
            dSatiety = type.dSatiety,
            dMood = type.dMood,
            dXp = type.dXp,
            refId = refId,
        )
    }
}

object RewardRules {

    /**
     * Ob eine Handlung gerade erlaubt ist.
     *
     * Ohne Sperrzeiten tippt man sich aus jeder Krankheit heraus, und die gesamte
     * Kopplung an die Arbeit ist wertlos.
     */
    fun isAllowed(type: RewardType, lastAt: Instant?, now: Instant): Boolean {
        val cooldown = type.cooldownMinutes ?: return true
        if (lastAt == null) return true
        return !now.isBefore(lastAt.plus(Duration.ofMinutes(cooldown)))
    }

    /** Wie lange die Sperre noch läuft — für die Anzeige am Knopf. */
    fun remainingCooldown(type: RewardType, lastAt: Instant?, now: Instant): Duration {
        val cooldown = type.cooldownMinutes ?: return Duration.ZERO
        if (lastAt == null) return Duration.ZERO
        val remaining = Duration.between(now, lastAt.plus(Duration.ofMinutes(cooldown)))
        return if (remaining.isNegative) Duration.ZERO else remaining
    }

    /** Höchstens zehn Erfassungs-XP pro Tag. */
    fun isTaskCreationRewardAllowed(alreadyToday: Int): Boolean =
        alreadyToday < Balance.REWARD_TASK_CREATED_MAX_PER_DAY
}
