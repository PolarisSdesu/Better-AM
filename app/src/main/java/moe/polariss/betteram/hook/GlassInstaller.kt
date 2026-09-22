package moe.polariss.betteram.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import moe.polariss.betteram.log.LogBridge
import moe.polariss.betteram.ui.LiquidGlassStyle
import moe.polariss.betteram.ui.createLiquidGlass
import java.lang.ref.WeakReference
import kotlin.math.abs
import java.util.WeakHashMap
import java.util.Collections

/** Host-safe renderer. It deliberately has no Compose references. */
internal object GlassInstaller {
    private const val BOTTOM_NAVIGATION_ID = "bottom_navigation"
    private const val BOTTOM_NAVIGATION_TABS_FRAME_ID = "bottom_navigation_tabs_frame"
    private const val NAVIGATION_DIVIDER_ID = "navigation_tabs_divider"
    private const val MINI_PLAYER_ID = "mini_player"
    private const val PLAYER_SHEET_CONTAINER_ID = "player_sheet_container"
    private const val PLAYER_FRAGMENTS_HOST_ID = "player_fragments_host"
    private const val PLAYER_BACKGROUND_LAYERS_ID = "background_layers"
    private const val PLAYER_TOP_SHADOW_ID = "player_top_shadow"
    private const val STACKED_ROOT_ID = "bottom_navigation_root_stacked"
    /** How long the sheet must hold still before its position counts as a rest. */
    private const val REST_MS = 200L
    /** Movement at or below this many pixels still counts as holding still. */
    private const val REST_TOLERANCE_PX = 2
    /** Gap kept below the navigation capsule when the device has no navigation bar. */
    private const val DEFAULT_BOTTOM_GAP_DP = 8f
    /** How far the mini player is lifted clear of the navigation capsule. */
    private const val MINI_PLAYER_LIFT_DP = 12f
    /** Side of the collapsed player's artwork. */
    private const val MINI_PLAYER_ARTWORK_DP = 30f
    private val installed = WeakHashMap<View, WeakReference<View>>()
    private val backdropPrepared = Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>())

    fun install(activity: Activity) {
        // Scan immediately rather than from a post: onPostResume runs before the
        // first draw, so the injected bar is already in place for the first frame
        // instead of the native one being shown and then swapped out.
        scan(activity)
        // The mini player and the player sheet are not always inflated by then.
        // The installed map makes the second pass cheap and idempotent.
        activity.window.decorView.post { scan(activity) }
    }

    private fun scan(activity: Activity) {
        runCatching {
            val root = (activity.window.decorView as? ViewGroup) ?: return@runCatching
            var navigationCount = 0
            visit(root) { view ->
                when {
                    ((view.javaClass.name == "com.google.android.material.bottomnavigation.BottomNavigationView") ||
                        (resourceName(view) == BOTTOM_NAVIGATION_ID)) -> {
                        if (attach(view, BarKind.Navigation)) navigationCount++
                    }
                    resourceName(view) == MINI_PLAYER_ID -> attach(view, BarKind.MiniPlayer)
                }
            }
            LogBridge.append(
                activity,
                "Scan complete: navigation=$navigationCount, activity=${activity.javaClass.simpleName}",
            )
        }.onFailure { LogBridge.append(activity, "View scan failed", it) }
    }

    private fun attach(target: View, kind: BarKind): Boolean = synchronized(installed) {
        if (installed.containsKey(target)) return@synchronized false
        runCatching {
            if (kind == BarKind.Navigation && installNavigationCapsule(target)) {
                installed[target] = WeakReference(target)
                return@runCatching
            }
            if (kind == BarKind.MiniPlayer && installMiniPlayerCapsule(target)) {
                installed[target] = WeakReference(target)
                return@runCatching
            }
            val density = target.resources.displayMetrics.density
            val radius = (if (kind == BarKind.Navigation) 28f else 18f) * density
            val dark = (target.resources.configuration.uiMode and 0x30) == 0x20
            val base = if (dark) Color.argb(210, 20, 20, 27) else Color.argb(215, 247, 247, 250)
            val middle = if (dark) Color.argb(120, 95, 47, 66) else Color.argb(120, 255, 176, 190)
            val edge = if (dark) Color.argb(150, 255, 255, 255) else Color.argb(205, 255, 255, 255)
            val fill = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(base, middle, base),
            ).apply {
                cornerRadius = radius
                setStroke((0.8f * density).toInt().coerceAtLeast(1), edge)
            }
            val sheen = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(80, 255, 255, 255), Color.TRANSPARENT),
            ).apply { cornerRadius = radius }
            target.background = LayerDrawable(arrayOf(fill, sheen))
            target.elevation = 0f
            installed[target] = WeakReference(target)
        }.fold(onSuccess = { true }) { false }
    }

    /**
     * Apple Music 6.5.3's bottom_navigation includes the gesture inset. Painting
     * that whole view also exposes the collapsed player sheet underneath it.
     * Keep the native navigation interactive and place a bounded material layer
     * behind its menu instead.
     */
    private fun installNavigationCapsule(navigation: View): Boolean {
        val tabsFrame = findAncestor(navigation, BOTTOM_NAVIGATION_TABS_FRAME_ID) as? FrameLayout
            ?: return false
        if (tabsFrame.findViewWithTag<View>(NAVIGATION_GLASS_TAG) != null) return true

        val density = navigation.resources.displayMetrics.density
        val dark = (navigation.resources.configuration.uiMode and 0x30) == 0x20
        val radius = 28f * density
        val fill = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (dark) {
                intArrayOf(Color.argb(238, 55, 51, 59), Color.argb(232, 35, 34, 40))
            } else {
                intArrayOf(Color.argb(238, 252, 252, 255), Color.argb(230, 224, 226, 232))
            }
        ).apply {
            cornerRadius = radius
            setStroke(
                (0.8f * density).toInt().coerceAtLeast(1),
                if (dark) Color.argb(150, 255, 255, 255) else Color.argb(110, 90, 94, 104)
            )
        }
        val highlight = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(82, 255, 255, 255), Color.TRANSPARENT)
        ).apply { cornerRadius = radius }
        val activity = navigation.context as? Activity
            ?: generateSequence(navigation.context) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
        val sampledContent = activity?.let(::prepareNavigationBackdrop)
        val glass = if (sampledContent != null) {
            createLiquidGlass(
                tabsFrame,
                sampledContent,
                dark,
                LiquidGlassStyle.NAVIGATION,
                tabs = ((navigation as? ViewGroup)?.getChildAt(0) as? ViewGroup)?.let { menu ->
                    (0 until menu.childCount).map(menu::getChildAt)
                }?.takeIf { it.isNotEmpty() }
            )
        } else View(navigation.context).apply {
            background = LayerDrawable(arrayOf(fill, highlight))
        }.apply {
            tag = NAVIGATION_GLASS_TAG
            isClickable = false
            isFocusable = false
            elevation = 0f
        }
        glass.tag = NAVIGATION_GLASS_TAG
        val horizontalInset = (14f * density).toInt()
        val glassParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (88f * density).toInt(),
        ).apply {
            topMargin = (-12f * density).toInt()
            leftMargin = horizontalInset
            rightMargin = horizontalInset
        }
        // Keep the capsule clear of the system navigation area. With gesture
        // navigation that area holds the home pill, which otherwise sat on the
        // glass. Only the bottom edge moves: the top stays put so the bar never
        // grows upwards towards the mini player. Without any navigation bar the
        // inset is zero and a small fixed gap takes its place instead of letting
        // the glass sit flush on the bottom edge.
        fun currentNavigationBarInset(): Int {
            val fromWindow = ViewCompat.getRootWindowInsets(tabsFrame)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            // The host already folded the same inset into its own navigation padding,
            // and that is readable even before the window dispatches insets to our
            // injected view.
            return maxOf(fromWindow, navigation.paddingBottom)
        }
        fun applyNavigationBarInset(navigationBarInset: Int = currentNavigationBarInset()) {
            if (tabsFrame.height <= 0) return
            val bottomGap = navigationBottomGap(navigationBarInset, density)
            val location = IntArray(2)
            tabsFrame.getLocationInWindow(location)
            val frameBottomGap = (tabsFrame.rootView.height - location[1] - tabsFrame.height)
                .coerceAtLeast(0)
            val height = (tabsFrame.height - frameBottomGap - bottomGap - glassParams.topMargin)
                .coerceAtLeast((56f * density).toInt())
            if (glassParams.height != height) {
                glassParams.height = height
                glass.layoutParams = glassParams
            }
        }
        tabsFrame.addView(glass, 0, glassParams)
        // The scan now runs before the first layout, so the frame has no height yet
        // and the inset cannot be resolved to a height. Re-apply once it does.
        tabsFrame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyNavigationBarInset()
        }
        ViewCompat.setOnApplyWindowInsetsListener(glass) { _, insets ->
            applyNavigationBarInset(insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
        ViewCompat.getRootWindowInsets(glass)?.let {
            applyNavigationBarInset(it.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
        }
        glass.post { ViewCompat.requestApplyInsets(glass) }
        navigation.background = null
        navigation.outlineProvider = null
        tabsFrame.background = null
        tabsFrame.foreground = null
        tabsFrame.outlineProvider = null
        // Keep Apple Music's native icons and labels above both glass layers.
        navigation.elevation = 3f * density
        navigation.translationZ = 3f * density
        resourceView(tabsFrame, NAVIGATION_DIVIDER_ID)?.visibility = View.GONE
        tabsFrame.clipChildren = false
        tabsFrame.clipToPadding = false
        // The pressed lens grows above the injected 88dp drawing area. Android
        // clips at every ViewGroup boundary, so disabling it only on tabsFrame still
        // lets bottom_navigation_root_stacked or the activity coordinator shave off
        // the lens and the capsule's upper outline.
        allowOverflow(tabsFrame)
        tabsFrame.elevation = 30f * density
        tabsFrame.translationZ = 30f * density
        if (sampledContent != null) {
            navigation.alpha = 0f
            navigation.isEnabled = false
            navigation.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            glass.elevation = 8f * density
            glass.translationZ = 0f
        }
        return true
    }

    private const val MINI_LINE_HEIGHT_DP = 16f

    private fun installMiniPlayerCapsule(target: View): Boolean {
        val miniPlayer = target as? FrameLayout ?: return false
        if (miniPlayer.findViewWithTag<View>(MINI_PLAYER_GLASS_TAG) != null) return true
        val activity = generateSequence(target.context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull() ?: return false
        val sampledContent = prepareNavigationBackdrop(activity) ?: return false
        val density = target.resources.displayMetrics.density
        val dark = (target.resources.configuration.uiMode and 0x30) == 0x20
        val glass = createLiquidGlass(
            miniPlayer,
            sampledContent,
            dark,
            LiquidGlassStyle.MINI_PLAYER,
        ).apply { tag = MINI_PLAYER_GLASS_TAG }
        val inset = (14f * density).toInt()
        // Keep the native content at child zero: the host uses it when sizing
        // the collapsed sheet. Z ordering places the glass underneath it.
        val glassParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (46f * density).toInt(),
        ).apply {
            leftMargin = inset
            rightMargin = inset
        }
        miniPlayer.addView(glass, miniPlayer.childCount, glassParams)
        miniPlayer.background = null
        miniPlayer.outlineProvider = null
        miniPlayer.clipChildren = false
        miniPlayer.clipToPadding = false
        miniPlayer.elevation = 31f * density
        miniPlayer.translationZ = 31f * density
        // The collapsed player's capsule used to run straight into the navigation
        // capsule below it (they overlapped by about 10px and read as one slab).
        // The navigation capsule cannot give ground - shrinking it below the 64dp
        // tab row would push the icons out of the glass - so the mini player is
        // lifted instead. Translating the whole view moves the native row and the
        // injected glass together, which keeps alignGlass working in the view's own
        // coordinates.
        miniPlayer.translationY = -(MINI_PLAYER_LIFT_DP * density)
        allowOverflow(miniPlayer)
        configureCollapsedPlayerSheet(miniPlayer)
        val playerContent = resourceView(miniPlayer, "mini_player_content")
        // The host decides where its own row sits inside the mini player, and it is
        // not centred in that container. Margins guessed against the container left
        // the capsule visibly off the row, so track the row instead and centre a
        // shorter capsule on it.
        if (playerContent != null) {
            val alignGlass = {
                if (playerContent.height > 0) {
                    val containerLocation = IntArray(2)
                    val contentLocation = IntArray(2)
                    miniPlayer.getLocationInWindow(containerLocation)
                    playerContent.getLocationInWindow(contentLocation)
                    val contentTop = contentLocation[1] - containerLocation[1]
                    val center = contentTop + playerContent.height / 2f
                    val top = (center - glassParams.height / 2f).toInt()
                    if (glassParams.topMargin != top) {
                        glassParams.topMargin = top
                        glass.layoutParams = glassParams
                    }
                }
            }
            miniPlayer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> alignGlass() }
            playerContent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> alignGlass() }
            miniPlayer.post(alignGlass)
        }
        playerContent?.let { content ->
            content.translationZ = density
            (content.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val contentInset = (14f * density).toInt()
                params.leftMargin = maxOf(params.leftMargin, contentInset)
                params.rightMargin = maxOf(params.rightMargin, contentInset)
                content.layoutParams = params
            }
        }
        (resourceView(miniPlayer, "video_surface_container"))?.let { artwork ->
            artwork.layoutParams = artwork.layoutParams.apply {
                width = (MINI_PLAYER_ARTWORK_DP * density).toInt()
                height = (MINI_PLAYER_ARTWORK_DP * density).toInt()
            }
        }
        listOf("mini_player_play_btn", "mini_player_next_btn").forEach { name ->
            (resourceView(miniPlayer, name) as? android.widget.ImageView)?.let { button ->
                button.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                button.layoutParams = button.layoutParams.apply {
                    width = (26f * density).toInt()
                    height = (26f * density).toInt()
                }
            }
        }
        val title = resourceView(miniPlayer, "mini_player_title") as? android.widget.TextView
        if (title != null) {
            val miniTextSize = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, 12f, miniPlayer.resources.displayMetrics,
            )
            // The title is the only line, so it is centred on its own row instead of
            // sitting in a two line block. A fixed line box keeps it in the same place
            // for every track: CJK and Latin glyphs have different ascent and descent,
            // so without it the wrap content box measured 61px for a Chinese title and
            // 50px for an English one and the title moved on each track change.
            val miniLineHeight = (MINI_LINE_HEIGHT_DP * density).toInt()
            (title.parent as? android.widget.LinearLayout)?.apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            title.maxLines = 1
            title.visibility = View.VISIBLE
            // Where the ink sits cannot be derived from the layout. Layout.getLineAscent
            // and getLineDescent describe the font's line box, not the glyphs: CJK and
            // kana fill the em box while Latin rarely reaches below the baseline, so at a
            // shared baseline CJK read about four pixels low and Latin looked right.
            // Deriving a correction from those metrics therefore just confirmed its own
            // assumption - the probe reported the ink centre as equal to the row centre
            // no matter how the text actually looked.
            //
            // The ink is measured from real pixels instead: the laid out text is drawn
            // into an offscreen bitmap and the topmost and bottommost inked rows are read
            // back. That is what the screen actually shows, so it needs no per-script
            // special casing. The title is pinned to one line with ellipsis, so its
            // layout never spills past the view.
            var inkScratch: android.graphics.Bitmap? = null
            fun measureInkCentre(view: android.widget.TextView): Float? {
                val layout = view.layout ?: return null
                if (layout.lineCount <= 0 || view.width <= 0 || view.height <= 0) return null
                var bitmap = inkScratch
                if (bitmap == null || bitmap.width != view.width || bitmap.height != view.height) {
                    bitmap?.recycle()
                    bitmap = createBitmap(
                        view.width, view.height, android.graphics.Bitmap.Config.ALPHA_8,
                    )
                    inkScratch = bitmap
                }
                bitmap.eraseColor(0)
                bitmap.applyCanvas { layout.draw(this) }
                val pixels = IntArray(view.width)
                var top = -1
                var bottom = -1
                for (y in 0 until view.height) {
                    bitmap.getPixels(pixels, 0, view.width, 0, y, view.width, 1)
                    var inked = false
                    for (x in 0 until view.width) {
                        // Ignore the faintest anti-aliasing so this matches where the eye
                        // reads the edge of the glyphs.
                        if ((pixels[x] ushr 24) > 40) {
                            inked = true
                            break
                        }
                    }
                    if (inked) {
                        if (top < 0) top = y
                        bottom = y
                    }
                }
                if (top < 0) return null
                return (top + bottom) / 2f
            }

            fun centerTitleInk() {
                val inkCentre = measureInkCentre(title) ?: return
                val shift = title.height / 2f - inkCentre
                if (title.translationY != shift) title.translationY = shift
            }

            // Keyed on the text so this runs once per track rather than once per frame.
            // It cannot hang off a layout listener: the host rebinds the title with
            // setText, which does not change its size, so layout never fires again after
            // the first pass and the measurement would never see a real title.
            var measuredKey: String? = null

            miniPlayer.viewTreeObserver.addOnPreDrawListener {
                // The host rebinds the title, at its own much larger size, on every track
                // change, so all of this is re-asserted rather than set once.
                title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, miniTextSize)
                title.setTypeface(title.typeface, android.graphics.Typeface.NORMAL)
                title.includeFontPadding = false
                title.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                if (title.layoutParams?.height != miniLineHeight) {
                    title.layoutParams = title.layoutParams.apply { height = miniLineHeight }
                }
                // Runs here, and not from a layout listener, because the host rebinds the
                // title with setText - same size, so layout never fires again after the
                // first pass and the measurement would never see a real title.
                val key = title.text.toString() + "@" + title.width + "x" + title.height
                if (key != measuredKey && title.width > 0 && title.height > 0) {
                    measuredKey = key
                    centerTitleInk()
                }
                true
            }
        }
        return true
    }

    @SuppressLint("DiscouragedApi")
    @Suppress("DEPRECATION")
    private fun prepareNavigationBackdrop(activity: Activity): ViewGroup? {
        val root = (activity.window.decorView as? ViewGroup) ?: return null
        activity.window.setDecorFitsSystemWindows(false)
        activity.window.navigationBarColor = Color.TRANSPARENT
        activity.window.isNavigationBarContrastEnforced = false
        activity.window.decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val hostId = root.resources.getIdentifier("navigation_host_group", "id", "com.apple.android.music")
        val coordinatorId = root.resources.getIdentifier("coordinator_layout", "id", "com.apple.android.music")
        if (hostId == 0) return null
        val candidates = mutableListOf<ViewGroup>()
        fun collect(view: View) {
            if (view.id == hostId && view is ViewGroup) candidates += view
            if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index))
        }
        collect(root)
        val host = candidates.firstOrNull { (it.parent as? View)?.id == coordinatorId }
            ?: candidates.maxByOrNull(View::getHeight)
            ?: return null
        syncNavigationUnderlap(host, hostId)
        if (backdropPrepared.add(host)) {
            host.viewTreeObserver.addOnGlobalLayoutListener {
                syncNavigationUnderlap(host, hostId)
            }
        }
        return host
    }

    private fun syncNavigationUnderlap(host: ViewGroup, hostId: Int) {
        (host.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            var changed = false
            if (params.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                changed = true
            }
            if (params.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                changed = true
            }
            if (params.bottomMargin != 0) {
                params.bottomMargin = 0
                changed = true
            }
            if (changed) host.layoutParams = params
        }
        if (host.paddingBottom != 0) {
            host.setPadding(host.paddingLeft, host.paddingTop, host.paddingRight, 0)
        }
        host.clipChildren = false
        host.clipToPadding = false
        expandHostPath(host, hostId)
    }

    private fun expandHostPath(view: View, hostId: Int): Boolean {
        var childContainsHost = false
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (expandHostPath(view.getChildAt(index), hostId)) childContainsHost = true
            }
        }
        val containsHost = view.id == hostId || childContainsHost
        if (!containsHost) return false
        view.layoutParams?.let { params ->
            if (params.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                params.height != ViewGroup.LayoutParams.MATCH_PARENT
            ) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                view.layoutParams = params
            }
        }
        if (view is ViewGroup) {
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)
            view.clipChildren = false
            view.clipToPadding = false
        }
        return true
    }

    @SuppressLint("DiscouragedApi")
    private fun configureCollapsedPlayerSheet(miniPlayer: View) {
        val sheet = resourceView(miniPlayer.rootView, PLAYER_SHEET_CONTAINER_ID)
        if (sheet == null) {
            // The normal scan runs after decor attachment, but keep a deferred
            // retry for the rare case where Apple Music has not inflated its
            // player sheet yet.
            miniPlayer.post { configureCollapsedPlayerSheet(miniPlayer) }
            return
        }
            val stackedRoot = resourceView(sheet.rootView, STACKED_ROOT_ID)
            stackedRoot?.apply {
                elevation = 32f * resources.displayMetrics.density
                translationZ = 0f
                outlineProvider = null
            }
            val surfaces = mutableListOf<View>()
            var current: View? = miniPlayer
            while (current != null) {
                surfaces += current
                if (current === stackedRoot) break
                current = current.parent as? View
            }
            if (sheet !in surfaces) surfaces += sheet
            val savedBackgrounds = WeakHashMap<View, android.graphics.drawable.Drawable?>().apply {
                surfaces.forEach { put(it, it.background) }
            }
            val savedBackgroundAlphas = savedBackgrounds.mapValues { (_, drawable) ->
                drawable?.alpha ?: 255
            }
            val savedBackgroundTints = surfaces.associateWith { it.backgroundTintList }
            val savedOutlines = surfaces.associateWith { it.outlineProvider }
            val savedForegrounds = surfaces.associateWith { it.foreground }
            val savedForegroundAlphas = savedForegrounds.mapValues { (_, drawable) ->
                drawable?.alpha ?: 255
            }
            val fullPlayer = resourceView(sheet.rootView, PLAYER_FRAGMENTS_HOST_ID)
            val backgroundLayers = resourceView(sheet.rootView, PLAYER_BACKGROUND_LAYERS_ID)
            val playerTopShadow = resourceView(sheet.rootView, PLAYER_TOP_SHADOW_ID)
            // The collapsed sheet also contains legibility gradients and other
            // full-player siblings. Hiding just background_layers leaves their
            // rounded rectangular scrim visible behind both capsules.
            val decorative = mutableListOf<View>()
            var branch: View = miniPlayer
            while (branch !== sheet) {
                val parent = branch.parent as? ViewGroup ?: break
                for (index in 0 until parent.childCount) {
                    val sibling = parent.getChildAt(index)
                    if (sibling !== branch) decorative += sibling
                }
                branch = parent
            }
            // Everything the sheet draws behind the player content makes up the
            // player's own backdrop: Apple Music paints it with a bitmap shader of
            // the artwork plus legibility gradients, and with the sheet collapsed its
            // top slice showed behind both capsules as a coloured slab. It is hidden
            // while collapsed and restored as soon as the sheet is dragged, which
            // reads as a fast return to full opacity. The player content itself -
            // cover included - is never touched.
            val backdropLayers = (decorative + listOfNotNull(
                backgroundLayers,
                playerTopShadow,
                fullPlayer,
            )).distinct()
            val savedLayerVisibility = backdropLayers.associateWith { it.visibility }
            val savedLayerAlpha = backdropLayers.associateWith { it.alpha }
            var hierarchyDumped = false
            val sync = { progress: Float ->
                val collapsed = progress <= 0.001f
                // Apple Music's artwork transition writes these drawables back after
                // our state edge. Keep the whole collapsed path transparent on every
                // draw, just as we do for the named backdrop layers below.
                surfaces.forEach { surface ->
                    val background = if (collapsed) null else savedBackgrounds[surface]
                    background?.alpha = ((savedBackgroundAlphas[surface] ?: 255) * progress)
                        .toInt().coerceIn(0, 255)
                    if (surface.background !== background) surface.background = background
                    val backgroundTint = if (collapsed) null else savedBackgroundTints[surface]
                    if (surface.backgroundTintList !== backgroundTint) {
                        surface.backgroundTintList = backgroundTint
                    }
                    val outline = if (collapsed) null else savedOutlines[surface]
                    if (surface.outlineProvider !== outline) surface.outlineProvider = outline
                    val foreground = if (collapsed) null else savedForegrounds[surface]
                    foreground?.alpha = ((savedForegroundAlphas[surface] ?: 255) * progress)
                        .toInt().coerceIn(0, 255)
                    if (surface.foreground !== foreground) surface.foreground = foreground
                }
                // Re-asserted on every frame, not only when the collapsed state flips:
                // Apple Music rewrites these layers from its own transition, so a
                // one-shot write never survives to the draw. INVISIBLE rather than
                // GONE because the sheet constrains several layers to these.
                backdropLayers.forEach { layer ->
                    val originalVisibility = savedLayerVisibility[layer] ?: View.VISIBLE
                    val visibility = if (collapsed && originalVisibility == View.VISIBLE) {
                        View.INVISIBLE
                    } else {
                        originalVisibility
                    }
                    if (layer.visibility != visibility) layer.visibility = visibility
                    val alpha = (savedLayerAlpha[layer] ?: 1f) * progress
                    if (layer.alpha != alpha) layer.alpha = alpha
                }
            }
            // The decompiled PlayerBottomSheetBehavior positions its child with
            // offsetTopAndBottom, so View.top is the stable state signal.
            //
            // The collapsed coordinate cannot simply be "the largest top seen".
            // The sheet slides in from the bottom of the window on first show, so
            // an in-flight position (2756 here, against a real collapsed top of
            // 2265) was recorded as collapsed. The value only ever grew, so
            // progress stayed pinned near 0.18 forever: the player was never
            // treated as collapsed and its backdrop layers stayed faintly visible
            // as an opaque rounded slab behind both capsules.
            //
            // Learn it from a position the sheet actually rests at instead, and
            // re-learn on every rest. The expanded rest lives in the upper half of
            // the window, so it can never overwrite the collapsed coordinate. A
            // deliberate pause in the middle of a drag can at worst fade the
            // backdrop out for a moment; releasing re-learns the real rest.
            var collapsedTop = 0
            var lastTop = Int.MIN_VALUE
            var lastTopChangeAt = 0L
            val currentProgress = {
                val rootHeight = sheet.rootView.height
                // BottomSheetBehavior positions the sheet with offsetTopAndBottom.
                // View.y also includes transient translation used by Apple Music and
                // can alternate between 0 and the collapsed coordinate during draw.
                val visibleTop = sheet.top
                val now = SystemClock.uptimeMillis()
                if (lastTopChangeAt == 0L || abs(visibleTop - lastTop) > REST_TOLERANCE_PX) {
                    lastTopChangeAt = now
                }
                lastTop = visibleTop
                val belowMiddle = visibleTop > rootHeight / 2 && visibleTop < rootHeight - 1
                // Re-learn the collapsed coordinate from wherever the sheet actually
                // comes to rest. This has to be time based rather than a frame count:
                // once the app is idle no more frames are drawn, so a count never
                // completed and the coordinate kept whatever it picked up while the
                // sheet was still sliding - which left the player's backdrop slab on
                // screen until the next scroll forced a frame.
                if (belowMiddle && now - lastTopChangeAt >= REST_MS) collapsedTop = visibleTop
                when {
                    // Parked at the bottom of the window: nothing of the player shows.
                    visibleTop >= rootHeight - 1 -> 0f
                    collapsedTop != 0 ->
                        ((collapsedTop - visibleTop).toFloat() / collapsedTop).coerceIn(0f, 1f)
                    // Not learned yet. Only the lower half can hold a collapsed rest
                    // (the expanded rest sits at the top of the window), so a low
                    // sheet counts as collapsed and an open one does not. Assuming
                    // "open" for a low sheet is what used to flash the slab on launch.
                    visibleTop > rootHeight / 2 -> 0f
                    else -> 1f
                }
            }
            sheet.viewTreeObserver.addOnPreDrawListener {
                sync(currentProgress())
                if (!hierarchyDumped) {
                    hierarchyDumped = true
                    fun dump(view: View, depth: Int) {
                        val name = resourceName(view) ?: "-"
                        val location = IntArray(2)
                        view.getLocationInWindow(location)
                        if (view.width >= 1000 && location[1] < 2780 && location[1] + view.height > 2100) {
                            android.util.Log.i(
                                "BetterAMLayers",
                                "$name ${view.javaClass.simpleName} global=${location[0]},${location[1]} " +
                                    "size=${view.width}x${view.height} translationY=${view.translationY} " +
                                    "alpha=${view.alpha} vis=${view.visibility} " +
                                    "bg=${view.background?.javaClass?.simpleName ?: "null"} " +
                                    "fg=${view.foreground?.javaClass?.simpleName ?: "null"}",
                            )
                        }
                        if (view is ViewGroup) {
                            for (index in 0 until view.childCount) dump(view.getChildAt(index), depth + 1)
                        }
                    }
                    dump(miniPlayer.rootView, 0)
                }
                true
            }
            sheet.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> sync(currentProgress()) }
            miniPlayer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> sync(currentProgress()) }
            // Do not wait for the first pre-draw. The player backdrop otherwise
            // contributes one green rounded slab to the first captured frame and
            // therefore appears behind both independently injected capsules.
            sync(currentProgress())
    }

    /** Bottom gap the capsules keep clear of the system navigation area. */
    private fun navigationBottomGap(navigationBarInset: Int, density: Float): Int =
        if (navigationBarInset > 0) navigationBarInset else (DEFAULT_BOTTOM_GAP_DP * density).toInt()

    /** Lets [view]'s children draw outside their bounds all the way to the window. */
    private fun allowOverflow(view: View) {
        var parent = view.parent as? ViewGroup
        while (parent != null) {
            parent.clipChildren = false
            parent.clipToPadding = false
            parent = parent.parent as? ViewGroup
        }
    }

    private fun findAncestor(view: View, @Suppress("UNUSED_PARAMETER") resourceName: String): View? {
        var current: View? = view
        while (current != null) {
            if (resourceName(current) == resourceName) return current
            current = current.parent as? View
        }
        return null
    }

    @SuppressLint("DiscouragedApi")
    private fun resourceView(root: View, name: String): View? {
        val id = root.resources.getIdentifier(name, "id", "com.apple.android.music")
        return id.takeIf { it != 0 }?.let(root::findViewById)
    }

    private fun visit(view: View, action: (View) -> Unit) {
        action(view)
        if (view !is ViewGroup) return
        for (index in 0 until view.childCount) visit(view.getChildAt(index), action)
    }

    private fun resourceName(view: View): String? = runCatching {
        if (view.id == View.NO_ID) null else view.resources.getResourceEntryName(view.id)
    }.getOrNull()

    private const val NAVIGATION_GLASS_TAG = "better-am-navigation-glass"
    private const val MINI_PLAYER_GLASS_TAG = "better-am-mini-player-glass"
}

private enum class BarKind { Navigation, MiniPlayer }
