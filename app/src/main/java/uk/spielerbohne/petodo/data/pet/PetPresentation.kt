package uk.spielerbohne.petodo.data.pet

import androidx.annotation.StringRes
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.domain.pet.HealthStage

/**
 * Stufe → Text und Zeichen.
 *
 * Steht hier und nicht in `ui/`, weil auch die Statuszeile im Fokus-Dienst das Pet-Icon
 * braucht — und weil `domain/` weder Texte noch Ressourcen kennen darf.
 */
@get:StringRes
val HealthStage.labelRes: Int
    get() = when (this) {
        HealthStage.HEALTHY -> R.string.pet_stage_healthy
        HealthStage.WEAKENED -> R.string.pet_stage_weakened
        HealthStage.SICK -> R.string.pet_stage_sick
        HealthStage.MISERABLE -> R.string.pet_stage_miserable
    }

/**
 * v1 zeigt keine Sprites (Projektplan, Abschnitt 8.1). Bis es welche gibt, trägt das
 * Zeichen die Stufe — überall dasselbe: Statustafel, Heute-Streifen, Statuszeile.
 */
@get:StringRes
val HealthStage.emojiRes: Int
    get() = when (this) {
        HealthStage.HEALTHY -> R.string.pet_emoji_healthy
        HealthStage.WEAKENED -> R.string.pet_emoji_weakened
        HealthStage.SICK -> R.string.pet_emoji_sick
        HealthStage.MISERABLE -> R.string.pet_emoji_miserable
    }
