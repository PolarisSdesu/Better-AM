package moe.polariss.betteram

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import moe.polariss.betteram.log.LogBridge
import moe.polariss.betteram.log.HookReportStore
import moe.polariss.betteram.ui.ModuleScreen
import moe.polariss.betteram.ui.ManagerPage
import moe.polariss.betteram.status.ModuleStatus
import moe.polariss.betteram.status.ModuleStatusReader
import kotlinx.coroutines.*
import moe.polariss.betteram.settings.AppSettings
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    private companion object {
        // Process-local only: activity recreation and foreground returns reuse the last result.
        // A new process must confirm the framework connection again.
        var lastModuleStatus = ModuleStatus.Checking
        val initialLoadingUntil = SystemClock.elapsedRealtime() + 600L
    }

    private var settings by mutableStateOf(AppSettings())
    private var logs by mutableStateOf("")
    private var moduleStatus by mutableStateOf(lastModuleStatus)
    private var musicVersion by mutableStateOf("正在读取")
    private var hookedVersion by mutableStateOf<String?>(null)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition {
            // Keep the splash framed in its themed background while the loading
            // state is still resolving on a fresh process.
            SystemClock.elapsedRealtime() < initialLoadingUntil
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settings = AppSettings.load(this)
        val dark = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        window.isNavigationBarContrastEnforced = false
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        LogBridge.append(this, "Better AM UI opened; version=$version, api=${Build.VERSION.SDK_INT}")
        logs = LogBridge.read(this)
        setContentView(ComposeView(this).apply {
            setContent {
                ModuleScreen(
                    version = version,
                    settings = settings,
                    onSettingsChange = { settings = it; it.save(this@MainActivity) },
                    musicVersion = musicVersion,
                    hookedVersion = hookedVersion,
                    logs = logs,
                    status = moduleStatus,
                    onOpenSettings = { startActivity(Intent(this@MainActivity, ManagerPageActivity::class.java).putExtra(ManagerPageActivity.EXTRA_PAGE, ManagerPage.SETTINGS.name)) },
                    onOpenLogs = { startActivity(Intent(this@MainActivity, ManagerPageActivity::class.java).putExtra(ManagerPageActivity.EXTRA_PAGE, ManagerPage.LOGS.name)) },
                    onOpenMusic = {
                        runCatching {
                            startActivity(
                                Intent.makeMainActivity(
                                    ComponentName(
                                        "com.apple.android.music",
                                        "com.apple.android.music.onboarding.activities.SplashActivity",
                                    )
                                )
                            )
                        }.onFailure {
                            Toast.makeText(
                                this@MainActivity,
                                settings.localizedContext(this@MainActivity).getString(R.string.open_music_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onCopyLogs = {
                        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Better AM logs", logs))
                        Toast.makeText(this@MainActivity, settings.localizedContext(this@MainActivity).getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
                    },
                    onClearLogs = {
                        LogBridge.clear(this@MainActivity)
                        logs = LogBridge.read(this@MainActivity)
                    }
                )
            }
        })
    }

    override fun onResume() {
        super.onResume()
        settings = AppSettings.load(this)
        // Grants can disappear after reboot/reinstallation. Re-establish access
        // before the user returns to Apple Music and its next resume reports in.
        LogBridge.grantTargetAccess(this)
        refreshJob?.cancel()
        refreshJob = uiScope.launch {
            if (lastModuleStatus.state == ModuleStatus.State.CHECKING) {
                // If interrupted, resume the original interval instead of restarting it.
                withContext(Dispatchers.IO) { ModuleStatusReader.read() }
                delay((initialLoadingUntil - SystemClock.elapsedRealtime()).milliseconds.coerceAtLeast(0.milliseconds))
            }
            musicVersion = withContext(Dispatchers.IO) {
                try {
                    val info = packageManager.getPackageInfo("com.apple.android.music", 0)
                    "${info.versionName ?: "未知版本"} (${info.longVersionCode})"
                } catch (_: PackageManager.NameNotFoundException) {
                    "未安装"
                } catch (_: Exception) {
                    "版本信息暂不可用"
                }
            }
            while (isActive) {
                val snapshot = withContext(Dispatchers.IO) {
                    Triple(LogBridge.read(this@MainActivity), ModuleStatusReader.read(),
                        HookReportStore.readCurrentVersion(this@MainActivity))
                }
                logs = snapshot.first
                moduleStatus = snapshot.second
                lastModuleStatus = snapshot.second
                hookedVersion = snapshot.third
                delay(1500.milliseconds)
            }
        }
    }
    override fun onPause() { refreshJob?.cancel(); super.onPause() }
    override fun onDestroy() { uiScope.cancel(); super.onDestroy() }
}
