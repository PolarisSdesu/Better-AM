@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.polariss.betteram.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import moe.polariss.betteram.R
import moe.polariss.betteram.settings.AppSettings
import moe.polariss.betteram.status.ModuleStatus

// Layout reference: RikkaApps/Shizuku b844bc491f1790c72328e1a8e5b2349f8978f0ea
// manager home_*.xml, HomeActivity.kt and values/styles.xml.
// Keep the Manager's 28dp cards, 40dp icon discs, 16/20dp padding and 16sp titles.
// The manager app bar is an actual MaterialToolbar, matching Shizuku's native
// action-menu layout, overflow popup and platform long-press tooltips.
@Composable
fun ModuleScreen(
    version: String,
    musicVersion: String,
    hookedVersion: String?,
    logs: String,
    status: ModuleStatus,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenMusic: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localizedContext = remember(context, configuration, settings.language) { settings.localizedContext(context) }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        // stringResource/pluralStringResource read LocalResources directly.
        // Provide it explicitly so retained navigation entries and popups also
        // observe the selected language instead of inheriting host resources.
        LocalResources provides localizedContext.resources,
    ) {
        ModuleContent(
            version,
            musicVersion,
            hookedVersion,
            logs,
            status,
            settings,
            onSettingsChange,
            onOpenMusic,
            onCopyLogs,
            onClearLogs,
            (context as Activity),
        )
    }
}

// Navigation 3 destinations. The keys are serializable so the back stack can be
// restored across configuration changes and process death.
@Serializable
internal data object HomeRoute : NavKey

@Serializable
internal data object SettingsRoute : NavKey

@Serializable
internal data object LogsRoute : NavKey

@Composable
private fun ModuleContent(version: String, musicVersion: String, hookedVersion: String?, logs: String, status: ModuleStatus,
    settings: AppSettings, onSettingsChange: (AppSettings) -> Unit,
    onOpenMusic: () -> Unit, onCopyLogs: () -> Unit, onClearLogs: () -> Unit, activity: Activity) {
    val dark = when (settings.appearance) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    @SuppressLint("NewApi")
    val baseColors = when {
        (settings.systemColors && dynamicColorSupported && dark) -> dynamicDarkColorScheme(context)
        (settings.systemColors && dynamicColorSupported) -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }
    val colors = if (dark && settings.blackBackground) baseColors.copy(
        background = Color.Black, surface = Color.Black, surfaceContainer = Color.Black,
        surfaceContainerLow = Color(0xFF121212), surfaceContainerHigh = Color(0xFF1C1C1C)
    ) else baseColors
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = colors.surface.luminance() > 0.5f
            isAppearanceLightNavigationBars = colors.background.luminance() > 0.5f
        }
    }
    val backStack = rememberNavBackStack(HomeRoute)
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val logScroll = rememberLazyListState()
    // Navigation 3 owns the system back gesture; at the root destination it is
    // disabled, so back falls through and closes the manager via the platform,
    // which turns it into system predictive back (enableOnBackInvokedCallback
    // in the manifest).
    val navigateBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    MaterialExpressiveTheme(colorScheme = colors) {
        // Keep the transparent system bars backed by the current theme, including
        // the area exposed while predictive back transforms a destination.
        Box(Modifier.fillMaxSize().background(colors.background)) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = navigateBack,
                // Match Shizuku's platform-owned motion: forward/pop use the
                // Navigator 3 default fade (the system activity transition
                // Shizuku inherits), and the predictive pop uses Navigation 3's
                // default fade + scale-out seeked by the back gesture, mirroring
                // the platform predictive-back transform.
                entryProvider = entryProvider {
                    entry<HomeRoute> {
                        HomeScreen(
                            version = version,
                            musicVersion = musicVersion,
                            hookedVersion = hookedVersion,
                            status = status,
                            dark = dark,
                            systemColors = settings.systemColors,
                            blackBackground = settings.blackBackground && dark,
                            dynamicColorsSupported = dynamicColorSupported,
                            onOpenSettings = { backStack.add(SettingsRoute) },
                            onOpenLogs = { backStack.add(LogsRoute) },
                            onShowAbout = { showAbout = true },
                            onOpenMusic = onOpenMusic,
                        )
                    }
                    entry<SettingsRoute> {
                        SubPage(
                            title = stringResource(R.string.settings),
                            dark = dark,
                            systemColors = settings.systemColors,
                            blackBackground = settings.blackBackground && dark,
                            dynamicColorsSupported = dynamicColorSupported,
                            onBack = navigateBack,
                        ) {
                            SettingsPage(settings, onSettingsChange)
                        }
                    }
                    entry<LogsRoute> {
                        SubPage(
                            title = stringResource(R.string.logs_title),
                            dark = dark,
                            systemColors = settings.systemColors,
                            blackBackground = settings.blackBackground && dark,
                            dynamicColorsSupported = dynamicColorSupported,
                            onBack = navigateBack,
                        ) {
                            LogPage(logs, logScroll, onCopyLogs, onClearLogs)
                        }
                    }
                },
            )
            if (showAbout) {
                AlertDialog(onDismissRequest = { showAbout = false }, title = { Text("Better AM") },
                    text = { Text(stringResource(R.string.about_description, version)) },
                    confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { showAbout = false }) { Text(stringResource(R.string.ok)) } })
            }
        }
    }
}

@Composable
private fun HomeScreen(
    version: String,
    musicVersion: String,
    hookedVersion: String?,
    status: ModuleStatus,
    dark: Boolean,
    systemColors: Boolean,
    blackBackground: Boolean,
    dynamicColorsSupported: Boolean,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onShowAbout: () -> Unit,
    onOpenMusic: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ManagerToolbar(
                title = "Better AM",
                dark = dark,
                systemColors = systemColors,
                blackBackground = blackBackground,
                dynamicColorsSupported = dynamicColorsSupported,
                onOpenSettings = onOpenSettings,
                onOpenLogs = onOpenLogs,
                onShowAbout = onShowAbout,
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ManagerCard {
                AnimatedContent(
                    targetState = status,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                    contentAlignment = Alignment.TopStart,
                    label = "moduleConnectionStatus"
                ) { current ->
                    val title = when (current.state) {
                        ModuleStatus.State.CHECKING -> stringResource(R.string.status_checking)
                        ModuleStatus.State.ENABLED -> stringResource(R.string.status_enabled)
                        ModuleStatus.State.NO_SCOPE -> stringResource(R.string.status_no_scope)
                        ModuleStatus.State.DISCONNECTED -> stringResource(R.string.status_disconnected)
                        ModuleStatus.State.ERROR -> stringResource(R.string.status_error)
                    }
                    ManagerRow(
                        if (current.state == ModuleStatus.State.ENABLED) R.drawable.ic_symbol_check_circle else R.drawable.ic_symbol_info,
                        title, when (current.state) {
                            ModuleStatus.State.CHECKING -> stringResource(R.string.detail_checking)
                            ModuleStatus.State.ENABLED -> stringResource(R.string.detail_enabled, current.frameworkName)
                            ModuleStatus.State.NO_SCOPE -> stringResource(R.string.detail_no_scope)
                            ModuleStatus.State.DISCONNECTED -> stringResource(R.string.detail_disconnected)
                            ModuleStatus.State.ERROR -> stringResource(R.string.detail_error)
                        }, checking = current.state == ModuleStatus.State.CHECKING
                    )
                }
            }
            ManagerCard(onClick = onOpenLogs) {
                ManagerRow(R.drawable.ic_symbol_list, stringResource(R.string.logs_title), stringResource(R.string.logs_summary))
            }
            ManagerCard {
                ManagerRow(R.drawable.ic_symbol_settings, stringResource(R.string.enable_title))
                Text(stringResource(R.string.enable_description),
                    Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.restart_description),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onOpenMusic,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(painterResource(R.drawable.ic_symbol_play_arrow), null,
                        Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.open_music))
                }
            }
            ManagerCard {
                ManagerRow(R.drawable.ic_symbol_build, stringResource(R.string.hook_info))
                val hookInfo = when {
                    hookedVersion != null -> stringResource(R.string.hooked_version, hookedVersion)
                    musicVersion == "未安装" -> stringResource(R.string.music_not_installed)
                    else -> stringResource(R.string.waiting_hook)
                }
                Text(hookInfo,
                    Modifier.padding(top = 24.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ManagerCard(onClick = onShowAbout) {
                ManagerRow(R.drawable.ic_symbol_info, stringResource(R.string.about_app), stringResource(R.string.version_label, version))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SubPage(
    title: String,
    dark: Boolean,
    systemColors: Boolean,
    blackBackground: Boolean,
    dynamicColorsSupported: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = {
        ManagerToolbar(
            title = title,
            dark = dark,
            systemColors = systemColors,
            blackBackground = blackBackground,
            dynamicColorsSupported = dynamicColorsSupported,
            onBack = onBack,
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            content()
        }
    }
}

/**
 * Shizuku uses a MaterialToolbar plus an XML Menu rather than Compose icon
 * buttons. Keeping the same native widgets is important here: ActionMenuView
 * owns the 48dp targets, overflow placement, pressed state, haptics, and the
 * platform tooltip shown after Android's standard long-press timeout.
 */
@Composable
private fun ManagerToolbar(
    title: String,
    dark: Boolean,
    systemColors: Boolean,
    blackBackground: Boolean,
    dynamicColorsSupported: Boolean,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenLogs: (() -> Unit)? = null,
    onShowAbout: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val contentColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val backDescription = stringResource(R.string.back)
    val settingsTitle = stringResource(R.string.settings)
    val logsTitle = stringResource(R.string.logs_title)
    val aboutTitle = stringResource(R.string.about)
    val moreOptions = stringResource(R.string.more_options)

    key(context, dark, systemColors, blackBackground, dynamicColorsSupported, onBack != null) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                // Shizuku's Material 3 actionBarSize resolves to the compact
                // top-app-bar token (64dp), rather than Compose's old 56dp bar.
                .height(64.dp),
            factory = { baseContext ->
                // Rebuild with the theme matching the resolved scheme so the
                // native popup menu backgrounds, text and inks use the same
                // colors as the Compose pages (including system/dynamic colors
                // and the black appearance).
                val themedContext = toolbarThemeContext(
                    baseContext, dark, systemColors, blackBackground, dynamicColorsSupported,
                )
                MaterialToolbar(themedContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    elevation = 0f
                    if (onBack == null) {
                        inflateMenu(R.menu.main)
                        overflowIcon = AppCompatResources.getDrawable(themedContext, R.drawable.ic_symbol_more_vert)
                    } else {
                        navigationIcon = AppCompatResources.getDrawable(themedContext, R.drawable.ic_symbol_arrow_back)
                    }
                }
            },
            update = { toolbar ->
                toolbar.title = title
                toolbar.setBackgroundColor(surfaceColor)
                toolbar.setTitleTextColor(contentColor)
                toolbar.navigationIcon?.setTint(contentColor)
                toolbar.navigationContentDescription = backDescription
                toolbar.setNavigationOnClickListener { onBack?.invoke() }

                toolbar.menu.findItem(R.id.action_settings)?.apply {
                    this.title = settingsTitle
                    icon?.setTint(contentColor)
                }
                toolbar.menu.findItem(R.id.action_logs)?.title = logsTitle
                toolbar.menu.findItem(R.id.action_about)?.title = aboutTitle
                toolbar.overflowIcon?.setTint(contentColor)
                toolbar.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_settings -> onOpenSettings?.invoke()
                        R.id.action_logs -> onOpenLogs?.invoke()
                        R.id.action_about -> onShowAbout?.invoke()
                        else -> return@setOnMenuItemClickListener false
                    }
                    true
                }

                // AppCompat supplies this automatically from its own locale.
                // Replace it with the manager-localized string so changing the
                // in-app language updates accessibility and long-press text now.
                if (onBack == null) {
                    toolbar.post { toolbar.setOverflowDescription(moreOptions) }
                }
            },
        )
    }
}

private fun MaterialToolbar.setOverflowDescription(description: String) {
    fun update(view: View) {
        if (view is ImageView) {
            view.contentDescription = description
            view.tooltipText = description
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) update(view.getChildAt(index))
        }
    }
    update(this)
}

/**
 * Builds the context used to inflate the MaterialToolbar and its overflow
 * popup so it renders with the scheme the Compose pages resolved. The base
 * theme is chosen from the resolved appearance (Light/Dark baselines, or the
 * Android 12+ dynamic color themes), then the black overlay is applied for the
 * black evening theme. This mirrors Shizuku, whose whole activity theme (and
 * therefore toolbar popup) re-resolves from its DayNight + dynamic color setup.
 */
private fun toolbarThemeContext(
    baseContext: Context,
    dark: Boolean,
    systemColors: Boolean,
    blackBackground: Boolean,
    dynamicColorsSupported: Boolean,
): Context {
    var context: Context = ContextThemeWrapper(
        baseContext,
        when {
            systemColors && dynamicColorsSupported && dark -> R.style.Theme_BetterAM_Toolbar_DynamicColors_Dark
            systemColors && dynamicColorsSupported -> R.style.Theme_BetterAM_Toolbar_DynamicColors_Light
            dark -> R.style.Theme_BetterAM_Toolbar_Dark
            else -> R.style.Theme_BetterAM_Toolbar_Light
        }
    )
    if (systemColors && dynamicColorsSupported && DynamicColors.isDynamicColorAvailable()) {
        // On Android 12+ this wraps the context so the dynamic color resources
        // the theme references resolve to the current wallpaper palette, the
        // same colors Compose's dynamic schemes use.
        context = DynamicColors.wrapContextIfAvailable(
            context,
            DynamicColorsOptions.Builder()
                .setThemeOverlay(
                    if (dark) R.style.Theme_BetterAM_Toolbar_DynamicColors_Dark
                    else R.style.Theme_BetterAM_Toolbar_DynamicColors_Light
                )
                .build(),
        )
    }
    if (dark && blackBackground) {
        context.theme.applyStyle(R.style.Theme_BetterAM_Toolbar_BlackOverlay, true)
    }
    return context
}

@Composable
private fun ManagerCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val shape = RoundedCornerShape(28.dp)
    if (onClick == null) {
        Card(Modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), content = content)
        }
    } else {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), content = content)
        }
    }
}

@Composable
private fun ManagerRow(@DrawableRes icon: Int, title: String, summary: String? = null, checking: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (checking) LoadingIndicator(Modifier.size(40.dp))
                else Icon(painterResource(icon), null, Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { heading() })
            if (summary != null) {
                Text(
                    summary,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val logTimestamp = Regex("""^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})][ \t]*""")

@Composable
private fun LogPage(logs: String, scrollState: LazyListState, onCopy: () -> Unit, onClear: () -> Unit) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val entries = remember(logs) {
        logs.splitToSequence("\n\n")
            .map { it.trimStart() }
            .filter { it.isNotBlank() }
            .toList()
    }
    Column(Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(pluralStringResource(R.plurals.logs_count, entries.size, entries.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onCopy, shapes = ButtonDefaults.shapes(), enabled = entries.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.copy_all)) }
            OutlinedButton(onClick = { confirmClear = true }, shapes = ButtonDefaults.shapes(), enabled = entries.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.clear_logs)) }
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_symbol_list), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.logs_empty), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.logs_empty_summary), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(state = scrollState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    itemsIndexed(entries) { index, entry ->
                        val timestamp = remember(entry) { logTimestamp.find(entry) }
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.log_entry, index + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                if (timestamp != null) {
                                    Text("[${timestamp.groupValues[1]}]", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(entry.drop(timestamp?.value?.length ?: 0),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_logs_title)) },
            text = { Text(stringResource(R.string.clear_logs_description)) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { onClear(); confirmClear = false }) { Text(stringResource(R.string.clear)) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
