package moe.polariss.betteram

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.polariss.betteram.log.LogBridge
import moe.polariss.betteram.settings.AppSettings
import moe.polariss.betteram.status.ModuleStatus
import moe.polariss.betteram.ui.ManagerPage
import moe.polariss.betteram.ui.ModuleScreen

/** Separate windows let Android own predictive back, as in Shizuku's manager. */
class ManagerPageActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PAGE = "manager_page"
    }

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null
    private var settings by mutableStateOf(AppSettings())
    private var logs by mutableStateOf("")

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val page = when (intent.getStringExtra(EXTRA_PAGE)) {
            ManagerPage.SETTINGS.name -> ManagerPage.SETTINGS
            ManagerPage.LOGS.name -> ManagerPage.LOGS
            else -> { finish(); return }
        }
        settings = AppSettings.load(this)
        window.isNavigationBarContrastEnforced = false
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContentView(ComposeView(this).apply {
            setContent {
                ModuleScreen(
                    version = version,
                    musicVersion = "",
                    hookedVersion = null,
                    logs = logs,
                    status = ModuleStatus.Checking,
                    settings = settings,
                    onSettingsChange = { settings = it; it.save(this@ManagerPageActivity) },
                    onOpenMusic = {},
                    onCopyLogs = {
                        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Better AM logs", logs))
                        Toast.makeText(this@ManagerPageActivity,
                            settings.localizedContext(this@ManagerPageActivity).getString(R.string.logs_copied),
                            Toast.LENGTH_SHORT).show()
                    },
                    onClearLogs = {
                        LogBridge.clear(this@ManagerPageActivity)
                        logs = LogBridge.read(this@ManagerPageActivity)
                    },
                    page = page,
                )
            }
        })
    }

    override fun onResume() {
        super.onResume()
        settings = AppSettings.load(this)
        if (intent.getStringExtra(EXTRA_PAGE) == ManagerPage.LOGS.name) {
            refreshJob?.cancel()
            refreshJob = uiScope.launch {
                while (isActive) {
                    logs = withContext(Dispatchers.IO) { LogBridge.read(this@ManagerPageActivity) }
                    delay(1500)
                }
            }
        }
    }

    override fun onPause() {
        refreshJob?.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }
}
