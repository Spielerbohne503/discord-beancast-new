package uk.spielerbohne.petodo.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.domain.model.Priority
import uk.spielerbohne.petodo.ui.theme.Palette

/**
 * Darstellung der Priorität. Die Stufen selbst stehen in `domain/model/Priority`; hier
 * steht nur, wie sie aussehen.
 */
object PriorityUi {

    val ALL = listOf(Priority.URGENT, Priority.HIGH, Priority.NORMAL, Priority.LOW)

    @Composable
    fun label(priority: Int): String = stringResource(
        when (Priority.coerce(priority)) {
            Priority.LOW -> R.string.priority_low
            Priority.HIGH -> R.string.priority_high
            Priority.URGENT -> R.string.priority_urgent
            else -> R.string.priority_normal
        }
    )

    /**
     * Normal bekommt bewusst keine Farbe — sonst ist alles bunt und nichts fällt auf.
     *
     * Die drei übrigen kommen aus der Palette der App, nicht aus dem Material-Standard:
     * Ein fremdes Rot neben dem Bernstein-Verlauf der überfälligen Aufgaben sieht aus
     * wie ein Fehler.
     */
    @Composable
    fun color(priority: Int): Color = when (Priority.coerce(priority)) {
        Priority.URGENT -> Palette.Ember
        Priority.HIGH -> Palette.Amber
        Priority.LOW -> Palette.Sky
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    fun hasVisibleFlag(priority: Int): Boolean = Priority.coerce(priority) != Priority.NORMAL
}
