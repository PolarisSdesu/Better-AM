// Adapted from Kyant0/AndroidLiquidGlass (Apache-2.0), commit 65ab177e90e5c1d8c62e70cf7755841982da65f6.
package moe.polariss.betteram.ui.official

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

enum class LiquidTabMotionPhase { REST, PRESS, DRAG, SETTLE }

/** How long the lens waits for Apple Music to route before giving up and closing. */
private const val ROUTE_TIMEOUT_MS = 3000L

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    val routedSelection: () -> Float,
) {

    // Catalog values (AndroidLiquidGlass 65ab177e). These are the curves the
    // reference app's bottom tabs animate with, so keep them rather than
    // re-tuning by feel: value and press are critically damped at stiffness 1000,
    // and the X/Y lens scales use their own softer springs.
    private val valueAnimationSpec =
        spring(1f, 1000f, visibilityThreshold)
    private val pressedValueAnimationSpec =
        spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec =
        spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec =
        spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec =
        spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec =
        spring(0.7f, 250f, 0.001f)

    private val valueAnimation =
        Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation =
        Animatable(0f, 5f)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    // One owner per animation channel: a new gesture cancels pending release
    // work instead of letting several detached coroutines fight over the lens.
    private var valueJob: Job? = null
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var velocityJob: Job? = null
    // Pointer events arrive far faster than animation coroutines can start, so the
    // requested slot is accumulated synchronously here.
    private var gestureTarget = initialValue
    // A spring that is cancelled and relaunched on every event never advances a
    // single frame - the lens looked frozen and the bar appeared not to slide at
    // all. While a finger is down the position comes straight from this synchronous
    // value; the spring takes over again for settle and host sync.
    private var dragValue: Float? by mutableStateOf(null)
    // Menu selection is acknowledged before the fragment is visible. Release
    // requires both this acknowledgment and the separate routedSelection probe.
    private var hostSelection by mutableFloatStateOf(Float.NaN)
    var phase: LiquidTabMotionPhase = LiquidTabMotionPhase.REST
        private set

    private val velocityTracker = VelocityTracker()

    val value: Float get() = dragValue ?: valueAnimation.value
    val targetValue: Float get() = gestureTarget
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                phase = LiquidTabMotionPhase.PRESS
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            },
        ) { _, dragAmount ->
            if (dragAmount != Offset.Zero) phase = LiquidTabMotionPhase.DRAG
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        releaseJob?.cancel()
        velocityTracker.resetTracking()
        animatePress(pressedScale)
    }

    fun release() {
        releaseJob?.cancel()
        // Let the press springs finish expanding while routing is pending.
        releaseJob = animationScope.launch {
            // Hold the expanded lens until both the slide has all but arrived and
            // Apple Music has routed to the destination. The catalog can release as
            // soon as its own selection animation lands because it owns selection;
            // here the page switch is the slow half, and contracting on arrival alone
            // shrank the lens while the destination was still coming up.
            awaitFrame()
            val target = gestureTarget
            val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
            // Bound failures (including an unsupported host view hierarchy) so the
            // lens cannot remain stuck open indefinitely.
            withTimeoutOrNull(ROUTE_TIMEOUT_MS.milliseconds) {
                snapshotFlow { Triple(valueAnimation.value, hostSelection, routedSelection()) }
                    .filter { (value, host, routed) ->
                        (abs(value - target) < threshold) && (abs(host - target) < 0.5f) &&
                            (abs(routed - target) < 0.5f)
                    }
                    .first()
            }
            pressJob?.cancel()
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
        velocityJob?.cancel()
        velocityJob = animationScope.launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
    }

    private fun animatePress(scale: Float) {
        pressJob?.cancel()
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(scale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(scale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        gestureTarget = target
        valueJob?.cancel()
        valueJob = null
        // Assign synchronously: this is what makes the lens track the finger.
        dragValue = target
        updateVelocity()
    }

    /** Move to the item under the initial press with the short catalog spring. */
    fun animatePressedValue(value: Float) {
        val target = value.coerceIn(valueRange)
        gestureTarget = target
        dragValue = null
        valueJob?.cancel()
        valueJob = animationScope.launch {
            valueAnimation.animateTo(target, pressedValueAnimationSpec)
        }
    }

    // Coolapk models selection as an explicit motion phase. Settling owns the
    // position channel until it completes, so host-state observations cannot
    // restart the same spring and produce a visible twitch.
    fun settleToValue(value: Float) {
        val target = value.coerceIn(valueRange)
        gestureTarget = target
        phase = LiquidTabMotionPhase.SETTLE
        valueJob?.cancel()
        valueJob = animationScope.launch {
            dragValue?.let { valueAnimation.snapTo(it) }
            dragValue = null
            valueAnimation.animateTo(target, valueAnimationSpec)
            phase = LiquidTabMotionPhase.REST
        }
    }

    fun syncToValue(value: Float, animate: Boolean) {
        val target = value.coerceIn(valueRange)
        hostSelection = target
        if (phase == LiquidTabMotionPhase.PRESS || phase == LiquidTabMotionPhase.DRAG) return
        if (phase == LiquidTabMotionPhase.SETTLE && target == valueAnimation.targetValue) return
        gestureTarget = target
        valueJob?.cancel()
        valueJob = animationScope.launch {
            dragValue?.let { valueAnimation.snapTo(it) }
            dragValue = null
            if (animate) {
                phase = LiquidTabMotionPhase.SETTLE
                valueAnimation.animateTo(target, valueAnimationSpec)
            } else {
                valueAnimation.snapTo(target)
            }
            phase = LiquidTabMotionPhase.REST
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            SystemClock.uptimeMillis(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        velocityJob?.cancel()
        velocityJob = animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}
