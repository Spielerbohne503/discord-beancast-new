package uk.spielerbohne.petodo.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Bewegung an einer Stelle — dieselbe Regel wie für Farben und Zahlen.
 *
 * Drei Dauern reichen. Wer eine vierte einführt, bekommt eine App, in der jeder
 * Bildschirm sein eigenes Tempo hat; das merkt man, ohne sagen zu können, woran es liegt.
 *
 * Gestaltungsregel dahinter: **Animiert wird, was sich bewegt hat.** Eine Zeile, die von
 * „heute“ nach „erledigt“ wandert, wandert sichtbar. Ein Wert, der gestiegen ist, wächst
 * sichtbar. Alles andere — Ränder, Schatten, Farben ohne Bedeutungswechsel — bleibt still.
 */
object Motion {

    /** Rückmeldung auf einen Fingertipp. So kurz, dass man sie fühlt und nicht sieht. */
    const val QUICK = 140

    /** Der Normalfall: Zustandswechsel, Ein- und Ausblenden. */
    const val STANDARD = 260

    /** Wenn eine Strecke zurückgelegt wird — Zeilen, die umziehen; Werte, die wachsen. */
    const val SLOW = 480

    /**
     * Beschleunigt langsam, bremst spät. Das Material-„emphasized“-Profil, ausgeschrieben,
     * damit es nicht von einer Bibliotheksversion abhängt.
     */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Für Dinge, die hereinkommen: schnell da, weich stehend. */
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    fun <T> quick(): FiniteAnimationSpec<T> = tween(QUICK, easing = Emphasized)

    fun <T> standard(): FiniteAnimationSpec<T> = tween(STANDARD, easing = Emphasized)

    fun <T> slow(): FiniteAnimationSpec<T> = tween(SLOW, easing = Emphasized)

    /**
     * Der Federweg fürs Abhaken.
     *
     * Ein Haken, der einfach erscheint, ist eine Zustandsänderung. Einer, der kurz über
     * seine Größe hinausschießt, ist eine Belohnung — und genau das soll Abhaken sein.
     */
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
}

/**
 * Ob das Gerät Animationen überhaupt will.
 *
 * Wer in den Entwickleroptionen oder in den Bedienungshilfen die Animationsskala auf null
 * stellt, hat dafür meist einen Grund — Bewegungsempfindlichkeit oder ein langsames
 * Gerät. Compose berücksichtigt das bei einzelnen Übergängen von selbst; für alles, was
 * **dauerhaft** läuft (Pulsieren, Leuchten), muss man selbst nachsehen, sonst dreht sich
 * dort etwas endlos, das ausdrücklich abgeschaltet wurde.
 */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current

    return remember(context, inspecting) {
        if (inspecting) return@remember false
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) != 0f
        }.getOrDefault(true)
    }
}
