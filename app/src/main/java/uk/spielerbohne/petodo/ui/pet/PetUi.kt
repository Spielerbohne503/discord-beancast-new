package uk.spielerbohne.petodo.ui.pet

import androidx.annotation.ArrayRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.data.pet.emojiRes
import uk.spielerbohne.petodo.domain.pet.HealthStage
import uk.spielerbohne.petodo.domain.pet.PetSpeech
import uk.spielerbohne.petodo.domain.pet.SpeechCategory
import uk.spielerbohne.petodo.ui.theme.Brand
import uk.spielerbohne.petodo.ui.theme.Motion
import uk.spielerbohne.petodo.ui.theme.Palette
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

/**
 * Der Verlauf zur Krankheitsstufe.
 *
 * Er ist die eigentliche Anzeige: Gesund leuchtet magenta, geschwächt kühlt ins Violette
 * ab, krank kippt ins Bernsteinfarbene, elend wird fast grau. Man erkennt den Zustand,
 * bevor man ein Wort gelesen hat — und weil derselbe Verlauf im Heute-Streifen und im
 * Lichtschein steckt, überall auf dieselbe Weise.
 */
fun HealthStage.gradient(): List<Color> = when (this) {
    HealthStage.HEALTHY -> Brand.Pet
    HealthStage.WEAKENED -> listOf(Palette.Violet, Palette.IndigoDeep)
    HealthStage.SICK -> Brand.Overdue
    HealthStage.MISERABLE -> listOf(Palette.Ember, Color(0xFF2A1520))
}

/** Die Akzentfarbe der Stufe — für Balken und Ränder außerhalb der Verlaufskarte. */
fun HealthStage.accent(): Color = gradient().first()

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

/**
 * Ein kurzes Aufleuchten, wenn ein Wert gestiegen ist.
 *
 * Das ist die Belohnung, um die es in der ganzen App geht: Man hakt etwas ab, und das
 * Pet *reagiert sichtbar*. Ohne diesen Moment ist die Kopplung zwischen Arbeit und
 * Begleiter eine Behauptung im Datenmodell.
 *
 * Bewusst nur nach oben: Verfall passiert langsam und über Stunden — ihn zu blitzen wäre
 * eine Strafe für Nichtstun, und die App bestraft niemanden.
 *
 * @return 0 im Ruhezustand, kurzzeitig bis 1 nach einem Zugewinn.
 */
@Composable
fun rememberGainPulse(value: Double): Float {
    val puls = remember { Animatable(0f) }
    val vorher = remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (value > vorher.value + SCHWELLE) {
            puls.animateTo(1f, tween(durationMillis = Motion.QUICK, easing = Motion.Decelerate))
            puls.animateTo(0f, tween(durationMillis = Motion.SLOW, easing = Motion.Emphasized))
        }
        vorher.value = value
    }

    return puls.value
}

/** Kleiner als eine Rundungsungenauigkeit soll nichts auslösen. */
private const val SCHWELLE = 0.05
