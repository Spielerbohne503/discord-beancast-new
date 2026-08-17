package uk.spielerbohne.petodo.ui.common

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Umsortieren per Ziehen in einer [LazyListState]-Liste.
 *
 * Ablauf: langes Drücken hebt eine Zeile an, das Ziehen verschiebt sie optisch, und
 * sobald ihre Mitte die Mitte des Nachbarn überschreitet, wird [onMove] gemeldet — die
 * Liste ordnet sich also schon während des Ziehens neu. Erst beim Loslassen wird über
 * [onDrop] gespeichert; ein abgebrochener Zug schreibt nichts.
 */
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    /** Index der angehobenen Zeile, oder `null`, wenn gerade nicht gezogen wird. */
    var draggedIndex by mutableStateOf<Int?>(null)
        private set

    private var draggedDistance by mutableFloatStateOf(0f)
    private var initialOffset = 0
    private var initialSize = 0

    /** Verschiebung der angehobenen Zeile in Pixeln. */
    fun offsetFor(index: Int): Float = if (index == draggedIndex) draggedDistance else 0f

    fun onDragStart(offsetY: Float) {
        val item = itemAt(offsetY) ?: return
        draggedIndex = item.index
        initialOffset = item.offset
        initialSize = item.size
        draggedDistance = 0f
    }

    fun onDrag(deltaY: Float) {
        val from = draggedIndex ?: return
        draggedDistance += deltaY

        val top = initialOffset + draggedDistance
        val bottom = top + initialSize
        val middle = (top + bottom) / 2f

        val target = listState.layoutInfo.visibleItemsInfo
            .filter { it.index != from }
            .firstOrNull { candidate ->
                val candidateMiddle = candidate.offset + candidate.size / 2f
                if (candidate.index > from) middle > candidateMiddle else middle < candidateMiddle
            }
            ?: return

        // Die Zeile soll unter dem Finger bleiben: Der sichtbare obere Rand bleibt
        // gleich, während der Bezugspunkt auf den neuen Platz wandert.
        val visibleTop = initialOffset + draggedDistance
        onMove(from, target.index)
        draggedIndex = target.index
        initialOffset = target.offset
        draggedDistance = visibleTop - target.offset
    }

    fun onDragEnd() {
        if (draggedIndex != null) onDrop()
        reset()
    }

    fun reset() {
        draggedIndex = null
        draggedDistance = 0f
    }

    private fun itemAt(offsetY: Float): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            offsetY.toInt() in item.offset..(item.offset + item.size)
        }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
): ReorderState = remember(listState) { ReorderState(listState, onMove, onDrop) }

/**
 * Hängt die Zieh-Gesten an die Liste. Bewusst erst nach langem Drücken — sonst
 * verschiebt man beim Blättern versehentlich Aufgaben.
 */
fun Modifier.reorderable(state: ReorderState): Modifier = this.pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.onDragStart(offset.y) },
        onDrag = { change, amount ->
            change.consume()
            state.onDrag(amount.y)
        },
        onDragEnd = { state.onDragEnd() },
        onDragCancel = { state.reset() },
    )
}
