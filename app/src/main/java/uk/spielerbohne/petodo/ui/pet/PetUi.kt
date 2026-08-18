package uk.spielerbohne.petodo.ui.pet

import androidx.annotation.ArrayRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.data.pet.emojiRes
import uk.spielerbohne.petodo.domain.pet.HealthStage
import uk.spielerbohne.petodo.domain.pet.PetSpeech
import uk.spielerbohne.petodo.domain.pet.SpeechCategory
import java.time.Duration
import kotlin.random.Random

/**
 * Die Brücke zwischen Zustand und Anzeige.
 *
 * `domain/` kennt weder Texte noch Farben noch Emoji — es sagt nur, welche Stufe und
 * welche Kategorie gilt. Hier steht, wie das aussieht.
 */

/** Das Zeichen zur Stufe — die Zuordnung steht in `data/pet/PetPresentation.kt`. */
@Composable
fun HealthStage.emoji(): String = stringResource(emojiRes)

@get:ArrayRes
val SpeechCategory.arrayRes: Int
    get() = when (this) {
        SpeechCategory.START -> R.array.pet_speech_start
        SpeechCategory.FOCUS_BEGINS -> R.array.pet_speech_focus_begins
        SpeechCategory.FOCUS_ENDS -> R.array.pet_speech_focus_ends
        SpeechCategory.BREAK_OVER -> R.array.pet_speech_break_over
        SpeechCategory.TASK_DONE -> R.array.pet_speech_task_done
        SpeechCategory.LIST_EMPTY -> R.array.pet_speech_list_empty
        SpeechCategory.TASK_DUE -> R.array.pet_speech_task_due
        SpeechCategory.FED -> R.array.pet_speech_fed
        SpeechCategory.PLAYED -> R.array.pet_speech_played
        SpeechCategory.PATTED -> R.array.pet_speech_patted
        SpeechCategory.HUNGRY -> R.array.pet_speech_hungry
        SpeechCategory.TIRED -> R.array.pet_speech_tired
        SpeechCategory.SICK -> R.array.pet_speech_sick
        SpeechCategory.HAPPY -> R.array.pet_speech_happy
        SpeechCategory.BORED -> R.array.pet_speech_bored
    }

/**
 * Ein Text aus der Kategorie. Neu gewürfelt wird nur, wenn sich die Kategorie ändert —
 * sonst wechselt der Satz bei jedem Neuzeichnen, und das Pet wirkt zappelig.
 */
@Composable
fun rememberSpeech(category: SpeechCategory?, previous: String? = null): String? {
    if (category == null) return null
    val texts = stringArrayResource(category.arrayRes).toList()
    return remember(category, texts, previous) {
        PetSpeech.pick(
            texts = texts,
            previous = previous,
            roll = Random.nextDouble(),
            chooser = { size -> Random.nextInt(size) },
        )
    }
}

/** Die Balkenfarbe folgt der Stufe, nicht dem einzelnen Wert — sonst leuchtet es bunt. */
@Composable
fun HealthStage.color(): Color = when (this) {
    HealthStage.HEALTHY -> MaterialTheme.colorScheme.primary
    HealthStage.WEAKENED -> MaterialTheme.colorScheme.tertiary
    HealthStage.SICK, HealthStage.MISERABLE -> MaterialTheme.colorScheme.error
}

/** Restliche Sperrzeit als Text — Sekunden interessieren dabei niemanden. */
@Composable
fun formatCooldown(remaining: Duration): String {
    val minutes = remaining.toMinutes()
    val hours = minutes / 60
    return when {
        minutes <= 0 -> stringResource(R.string.pet_duration_seconds)
        hours > 0 -> stringResource(R.string.pet_duration_hours_minutes, hours, minutes % 60)
        else -> stringResource(R.string.pet_duration_minutes, minutes)
    }
}
