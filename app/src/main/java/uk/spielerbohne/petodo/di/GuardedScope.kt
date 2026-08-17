package uk.spielerbohne.petodo.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Startet Arbeit, die auf jeden Fall zu Ende geführt werden muss — auch wenn sie
 * scheitert.
 *
 * Hintergrund: Ein `BroadcastReceiver` muss `goAsync().finish()` immer aufrufen, sonst
 * hält er den Prozess fest, bis Android ihn abschießt. Eine Ausnahme im Coroutine-Body
 * darf das nicht verhindern — "Ausfall eines Bausteins führt zu eingeschränktem, nicht
 * zu gestörtem Betrieb".
 */
fun CoroutineScope.launchGuarded(
    onFinally: () -> Unit,
    onError: (Throwable) -> Unit,
    block: suspend () -> Unit,
): Job = launch {
    try {
        block()
    } catch (throwable: Throwable) {
        onError(throwable)
    } finally {
        onFinally()
    }
}
