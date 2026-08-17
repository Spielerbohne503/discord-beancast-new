package uk.spielerbohne.petodo.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.domain.model.Priority

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

    /** Normal bekommt bewusst keine Farbe — sonst ist alles bunt und nichts fällt auf. */
    @Composable
    fun color(priority: Int): Color = when (Priority.coerce(priority)) {
        Priority.URGENT -> Color(0xFFD32F2F)
        Priority.HIGH -> Color(0xFFF57C00)
        Priority.LOW -> Color(0xFF1976D2)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    fun hasVisibleFlag(priority: Int): Boolean = Priority.coerce(priority) != Priority.NORMAL
}
