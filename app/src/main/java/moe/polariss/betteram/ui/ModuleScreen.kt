@file:OptIn(ExperimentalMaterial3Api::class)

package moe.polariss.betteram.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import kotlinx.serialization.Serializable
import moe.polariss.betteram.R
import moe.polariss.betteram.settings.AppSettings
import moe.polariss.betteram.status.ModuleStatus

// Layout reference: RikkaApps/Shizuku b844bc491f1790c72328e1a8e5b2349f8978f0ea
// manager home_*.xml, HomeActivity.kt and values/styles.xml.
// Keep the Manager's 28dp cards, 40dp icon discs, 16/20dp padding and 16sp titles.
// The top app bar actions mirror the MaterialToolbar: icon buttons reveal their
// label as a tooltip (long press / hover) and the overflow (MoreVert) opens the
// scrollable DropdownMenu with the remaining destinations.
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
        else -> lightColorScheme()
    }
    val colors = if (dark && settings.blackBackground) baseColors.copy(
        background = Color.Black, surface = Color.Black, surfaceContainer = Color.Black,
        surfaceContainerLow = Color(0xFF121212), surfaceContainerHigh = Color(0xFF1C1C1C)
    ) else baseColors
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    val backStack = rememberNavBackStack(HomeRoute)
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val logScroll = rememberLazyListState()
    // Navigation 3 owns the system back gesture; at the root destination it is
    // disabled, so back falls through and closes the manager.
    val navigateBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = navigateBack,
                // Navigation 3's defaults animate entry, exit and predictive back.
                // The manager keeps the plain page swap for regular navigation;
                // only the predictive back gesture animates, sliding the page out
                // in the drag direction while the page underneath stays pinned.
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                predictivePopTransitionSpec = { swipeEdge ->
                    val slideOut = when (swipeEdge) {
                        NavigationEvent.EDGE_LEFT -> { width: Int -> width }
                        NavigationEvent.EDGE_RIGHT -> { width: Int -> -width }
                        else -> { _: Int -> 0 }
                    }
                    EnterTransition.None togetherWith
                        slideOutHorizontally(animationSpec = tween(700), targetOffsetX = slideOut)
                },
                entryProvider = entryProvider {
                    entry<HomeRoute> {
                        HomeScreen(
                            version = version,
                            musicVersion = musicVersion,
                            hookedVersion = hookedVersion,
                            status = status,
                            onOpenSettings = { backStack.add(SettingsRoute) },
                            onOpenLogs = { backStack.add(LogsRoute) },
                            onShowAbout = { showAbout = true },
                            onOpenMusic = onOpenMusic,
                        )
                    }
                    entry<SettingsRoute> {
                        SubPage(title = stringResource(R.string.settings), onBack = navigateBack) {
                            SettingsPage(settings, onSettingsChange)
                        }
                    }
                    entry<LogsRoute> {
                        SubPage(title = stringResource(R.string.logs_title), onBack = navigateBack) {
                            LogPage(logs, logScroll, onCopyLogs, onClearLogs)
                        }
                    }
                },
            )
            if (showAbout) {
                AlertDialog(onDismissRequest = { showAbout = false }, title = { Text("Better AM") },
                    text = { Text(stringResource(R.string.about_description, version)) },
                    confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.ok)) } })
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
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onShowAbout: () -> Unit,
    onOpenMusic: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Better AM", fontSize = 20.sp) },
                expandedHeight = 56.dp,
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.settings)) } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, stringResource(R.string.settings))
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.more_options)) } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                        }
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.logs_title)) }, onClick = { menuExpanded = false; onOpenLogs() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, onClick = { menuExpanded = false; onShowAbout() })
                    }
                }
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
                        if (current.state == ModuleStatus.State.ENABLED) Icons.Default.CheckCircle else Icons.Default.Info,
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
                ManagerRow(Icons.AutoMirrored.Filled.List, stringResource(R.string.logs_title), stringResource(R.string.logs_summary))
            }
            ManagerCard {
                ManagerRow(Icons.Default.Settings, stringResource(R.string.enable_title))
                Text(stringResource(R.string.enable_description),
                    Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.restart_description),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onOpenMusic,
                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.open_music),
                        modifier = Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.labelLarge.copy(
                            lineHeight = 18.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both
                            )
                        )
                    )
                }
            }
            ManagerCard {
                ManagerRow(Icons.Default.Build, stringResource(R.string.hook_info))
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
                ManagerRow(Icons.Default.Info, stringResource(R.string.about_app), stringResource(R.string.version_label, version))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title, fontSize = 20.sp) },
            expandedHeight = 56.dp, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            content()
        }
    }
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
private fun ManagerRow(icon: ImageVector, title: String, summary: String? = null, checking: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Icon(icon, null, Modifier.size(24.dp))
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
            FilledTonalButton(onClick = onCopy, enabled = entries.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.copy_all)) }
            OutlinedButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.clear_logs)) }
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
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
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text(stringResource(R.string.clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
