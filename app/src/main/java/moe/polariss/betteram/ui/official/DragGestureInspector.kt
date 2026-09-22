// Adapted from Kyant0/AndroidLiquidGlass (Apache-2.0), commit 65ab177e90e5c1d8c62e70cf7755841982da65f6.
package moe.polariss.betteram.ui.official

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.util.fastFirstOrNull

/**
 * Catalog behavior: observe the down during the Initial pass so the lens claims the
 * gesture before the tab row's clickable, start dragging on the first movement, and
 * release when the finger lifts.
 *
 * Two deliberate differences from the catalog version, both required inside Apple
 * Music:
 *
 * 1. Movement is measured with raw positions. Compose's
 *    [PointerInputChange.positionChange] returns [Offset.Zero] for any change that
 *    somebody consumed, and inside the host the tab row consumes freely - which
 *    silently froze the lens.
 * 2. We never consume and never give up when somebody else consumes. The host gets
 *    to keep its own gesture handling, and the lens keeps tracking the finger.
 */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onDragStart(down)

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.fastFirstOrNull { it.id == down.id }
            if (change == null) {
                onDragCancel()
                return@awaitEachGesture
            }
            if (!change.pressed) {
                if (change.changedToUpIgnoreConsumed()) onDragEnd(change) else onDragCancel()
                return@awaitEachGesture
            }
            val delta = change.position - change.previousPosition
            if (delta != Offset.Zero) onDrag(change, delta)
        }
    }
}
