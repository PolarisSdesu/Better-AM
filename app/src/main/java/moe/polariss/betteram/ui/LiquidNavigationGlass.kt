package moe.polariss.betteram.ui

import android.annotation.SuppressLint
import android.graphics.RenderNode
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import moe.polariss.betteram.ui.official.LiquidBottomTab
import moe.polariss.betteram.ui.official.LiquidBottomTabs

/** Bridges Apple Music's View hierarchy into Backdrop's Compose canvas. */
internal enum class LiquidGlassStyle { NAVIGATION, MINI_PLAYER }

/**
 * Extra height recorded below the glass, as a fraction of its height.
 *
 * The lens is scaled past the capsule while pressed, and it also refracts by up to
 * its own radius, so its lower edge samples below the glass. The recording only
 * covered the glass itself, so those samples fell through to the solid base rect:
 * the bottom of the lens stayed black in dark theme (white in light theme) and
 * never refracted. The top needs nothing because the recording is already drawn
 * 12dp above the view.
 */
private const val CAPTURE_PAD = 0.6f

internal fun createLiquidGlass(
    container: ViewGroup,
    sampledContent: View,
    dark: Boolean,
    style: LiquidGlassStyle,
    tabs: List<View>? = null,
): View {
    val frame: MutableIntState = mutableIntStateOf(0)
    val recording = RenderNode("BetterAM-$style")
    val selectedTab = mutableIntStateOf(tabs?.indexOfFirst { it.isSelected || it.isActivated || it.drawableState.contains(android.R.attr.state_checked) }?.coerceAtLeast(0) ?: 0)
    val routedTab = mutableIntStateOf(-1)
    val routeProbe = tabs?.let { NavigationRouteProbe(container.rootView, it) }
    val composeOwner = InjectedComposeOwner()
    // WindowRecomposer resolves owners from the window root, not only from the
    // ComposeView. Apple Music's activity does not publish them itself.
    container.rootView.setViewTreeLifecycleOwner(composeOwner)
    container.rootView.setViewTreeSavedStateRegistryOwner(composeOwner)
    container.rootView.setViewTreeViewModelStoreOwner(composeOwner)
    val composeView = ComposeView(container.context).apply {
        clipChildren = false
        clipToPadding = false
        // A navigation glass surface owns pointer input for the selected lens.
        // The mini-player glass stays passive so native playback controls keep
        // receiving touches.
        isClickable = tabs != null
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setViewTreeLifecycleOwner(composeOwner)
        setViewTreeSavedStateRegistryOwner(composeOwner)
        setViewTreeViewModelStoreOwner(composeOwner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            if (tabs != null) NativeLiquidTabs(recording, frame, selectedTab, routedTab, tabs, dark)
            else LiquidGlassSurface(recording, frame, dark)
        }
        addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    // The catalog lens grows outside the 64dp bar while pressed.
                    // Both the bridge and its AndroidComposeView must allow overflow.
                    (v as ViewGroup).clipChildren = false
                    v.clipToPadding = false
                    for (index in 0 until v.childCount) {
                        (v.getChildAt(index) as? ViewGroup)?.apply {
                            clipChildren = false
                            clipToPadding = false
                        }
                    }
                }
                override fun onViewDetachedFromWindow(v: View) {
                    recording.discardDisplayList()
                    composeOwner.destroy()
                }
            },
        )
    }

    var lastCaptureAt = 0L
    container.viewTreeObserver.addOnPreDrawListener {
        routeProbe?.let { routedTab.intValue = it.visibleTab() }
        tabs?.indexOfFirst { it.isSelected || it.isActivated || it.drawableState.contains(android.R.attr.state_checked) }
            ?.takeIf { it >= 0 }?.let { selectedTab.intValue = it }
        val now = SystemClock.uptimeMillis()
        if (composeView.isAttachedToWindow && (now - lastCaptureAt >= 16L)) {
            lastCaptureAt = now
            if (captureBehind(composeView, sampledContent, recording)) {
                if (frame.intValue == 0) Log.i("BetterAMCapture", "Captured $style ${composeView.width}x${composeView.height} source=${sampledContent.width}x${sampledContent.height}")
                frame.intValue++
            }
        }
        true
    }
    return composeView
}

private fun captureBehind(glass: View, source: View, recording: RenderNode): Boolean {
    if (glass.width <= 0 || glass.height <= 0 || source.width <= 0 || source.height <= 0) return false
    val glassLocation = IntArray(2)
    val sourceLocation = IntArray(2)
    glass.getLocationInWindow(glassLocation)
    source.getLocationInWindow(sourceLocation)
    return runCatching {
        val pad = (glass.height * CAPTURE_PAD).toInt()
        val captureHeight = glass.height + pad
        recording.setPosition(0, 0, glass.width, captureHeight)
        val canvas = recording.beginRecording(glass.width, captureHeight)
        try {
            // Only the bottom grows: the recording's top still lines up with the
            // glass, so every existing draw offset stays valid.
            canvas.translate(
                (sourceLocation[0] - glassLocation[0]).toFloat(),
                (sourceLocation[1] - glassLocation[1]).toFloat(),
            )
            source.draw(canvas)

        } finally {
            recording.endRecording()
        }
        true
    }.onFailure { Log.e("BetterAMCapture", "Backdrop capture failed", it) }.getOrDefault(defaultValue = false)}

@Composable
private fun LiquidGlassSurface(recording: RenderNode, frame: MutableIntState, dark: Boolean) {
    val backdrop = rememberCanvasBackdrop {
        val generation = frame.intValue
        drawRect(if (dark) Color.Black else Color.White)
        if (generation > 0 && recording.hasDisplayList()) {
            drawIntoCanvas { canvas ->
                if (canvas.nativeCanvas.isHardwareAccelerated) canvas.nativeCanvas.drawRenderNode(recording)
            }
        } else {
            drawRect(if (dark) Color(0xFF242129) else Color(0xFFF2F2F6))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    // Kept below half the capsule height. The catalog's 24dp radius
                    // is larger than this bar is tall, and a lens wider than the
                    // shape folds back on itself - it drew a 1px seam straight across
                    // the middle of the capsule.
                    lens(16.dp.toPx(), 16.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = { null },
                onDrawSurface = {
                    drawRect((if (dark) Color(0xFF121212) else Color(0xFFFAFAFA)).copy(alpha = 0.4f))
                },
            ),
    )
}

/** The catalog component owns the panel, combined backdrop, gestures and lens.
 * Native items only supply their artwork/labels and the actual navigation action.
 */
private class SelectionIntent {
    var animate = false

    /**
     * Slot already sent to the host for the current pointer gesture.
     *
     * A tap is observed twice: once by the tab's own clickable and once by the
     * bar's drag gesture, which deliberately never consumes and therefore also
     * sees the up. Both used to call performClick, and Material treats a second
     * click on an item that is already selected as a *reselection*. Apple Music
     * reloads the page from its reselect listener, so switching tabs flashed -
     * most visibly on the home tab. Only the first dispatch of a gesture reaches
     * the host; the bar resets this on the next pointer down.
     */
    var dispatched = -1
}

@SuppressLint("DiscouragedApi")
@Composable
private fun NativeLiquidTabs(
    recording: RenderNode,
    frame: MutableIntState,
    selectedTab: MutableIntState,
    routedTab: MutableIntState,
    items: List<View>,
    dark: Boolean,
) {
    // CanvasBackdrop deliberately ignores LayoutCoordinates and therefore starts
    // every small lens at source (0, 0). Record the full bar into a LayerBackdrop;
    // its localPositionOf mapping makes each moving lens sample its actual slot.
    val backdrop = rememberLayerBackdrop()
    Canvas(
        Modifier
            .fillMaxSize()
            .alpha(0f)
            .layerBackdrop(backdrop)
    ) {
        val generation = frame.intValue
        drawRect(if (dark) Color.Black else Color.White)
        if (generation >= 0 && recording.hasDisplayList()) drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.withTranslation(0f, -12.dp.toPx()) {
                // The injected view includes 12dp of breathing room above the bar. The
                // recording's top lines up with this view (only its bottom was padded),
                // so this offset is unchanged.
                canvas.drawRenderNode(recording)
            }
        }
    }
    val selectionIntent = remember { SelectionIntent() }
    val selectedReader = remember(selectedTab) { { selectedTab.intValue } }
    // Both the tab clickable and the bar drag gesture funnel through here, so a
    // single tap only ever reaches the host once. See [SelectionIntent].
    val dispatchNativeSelection: (Int) -> Unit = remember(items, selectedTab) {
        { index ->
            if (index != selectionIntent.dispatched) {
                selectionIntent.dispatched = index
                selectionIntent.animate = index != selectedTab.intValue
                items[index].performClick()
            }
        }
    }
    val selectNativeTab: (Int) -> Unit = remember(items, selectedTab) {
        { index ->
            // A drag release onto the current slot is not a user reselection.
            if (index != selectedTab.intValue) dispatchNativeSelection(index)
        }
    }
    LiquidBottomTabs(
        selectedTabIndex = selectedReader,
        routedTabIndex = { routedTab.intValue },
        onTabSelected = selectNativeTab,
        onGestureStart = { selectionIntent.dispatched = -1 },
        shouldAnimateSelection = {
            val animate = selectionIntent.animate
            selectionIntent.animate = false
            animate
        },
        backdrop = backdrop,
        tabsCount = items.size,
        // No vertical padding: every child is centred, so the bar keeps the same
        // geometry while the pressed lens is free to grow past the 64dp bar.
        modifier = Modifier.fillMaxSize(),
    ) {
        items.forEachIndexed { index, item ->
            LiquidBottomTab(
                onClick = { dispatchNativeSelection(index) },
                modifier = Modifier.semantics {
                    contentDescription = item.contentDescription?.toString().orEmpty()
                    selected = selectedTab.intValue == index
                },
            ) {
                val iconId = item.resources.getIdentifier("navigation_bar_item_icon_view", "id", "com.apple.android.music")
                val icon = item.findViewById<android.widget.ImageView>(iconId)
                val labelId = item.resources.getIdentifier("navigation_bar_item_small_label_view", "id", "com.apple.android.music")
                val label = item.findViewById<android.widget.TextView>(labelId)
                val color = if (selectedTab.intValue == index) Color(0xFFFF2D55)
                    else if (dark) Color.White else Color.Black
                // Apple Music draws its unselected icons at partial alpha, so a
                // full strength label reads darker than the icon next to it. Match
                // that alpha so the label and the icon read as one colour.
                val labelAlpha = if (selectedTab.intValue == index) 1f
                    else icon?.alpha?.takeIf { it in 0.1f..0.99f } ?: 0.75f
                Canvas(Modifier.size(28.dp).graphicsLayer(colorFilter = ColorFilter.tint(color))) {
                    frame.intValue
                    if (icon != null && icon.width > 0 && icon.height > 0) drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.withScale(size.width / icon.width, size.height / icon.height) {
                            icon.draw(native)
                        }
                    }
                }
                BasicText(
                    label?.text?.toString() ?: item.contentDescription?.toString().orEmpty(),
                    style = TextStyle(
                        color = color.copy(alpha = color.alpha * labelAlpha),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Apple Music 6.5.3 attaches one tagged NavHost per tab (K8.d.b in the host).
 * Read the host's Fragment tag reflectively: its AndroidX classes use a different
 * class loader. A checked menu item alone precedes the fragment transaction.
 */
private class NavigationRouteProbe(private val root: View, items: List<View>) {
    @SuppressLint("DiscouragedApi")
    private val hostId = root.resources.getIdentifier("navigation_host_group", "id", "com.apple.android.music")
    @SuppressLint("DiscouragedApi")
    private val fragmentTagId = root.resources.getIdentifier("fragment_container_view_tag", "id", "com.apple.android.music")
    private val tags = items.map { item ->
        when (runCatching { root.resources.getResourceEntryName(item.id) }.getOrNull()) {
            "action_listen_now" -> "HOME"
            "action_browse" -> "NEW"
            "action_library" -> "LIBRARY"
            "action_multiply_radio" -> "RADIO"
            "search_fragment" -> "SEARCH"
            else -> null
        }
    }
    private val tagMethods = mutableMapOf<Class<*>, java.lang.reflect.Method>()

    fun visibleTab(): Int = runCatching {
        val host = root.findViewById<ViewGroup>(hostId) ?: return -1
        if (fragmentTagId == 0) return -1
        for (i in host.childCount - 1 downTo 0) {
            val page = host.getChildAt(i)
            if (!page.isShown || page.width == 0 || page.height == 0 ||
                page.alpha < 0.99f || page.isLayoutRequested ||
                page.animation?.hasEnded() == false) continue
            val fragment = page.getTag(fragmentTagId) ?: continue
            val method = tagMethods.getOrPut(fragment.javaClass) { fragment.javaClass.getMethod("getTag") }
            val tag = method.invoke(fragment) as? String ?: continue
            val index = tags.indexOf(tag)
            if (index >= 0) return index
        }
        -1
    }.getOrDefault(-1)
}
