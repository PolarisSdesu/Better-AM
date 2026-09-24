package moe.polariss.betteram.log

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogBridge {
    const val METHOD_APPEND = "append"
    const val METHOD_READ = "read"
    const val METHOD_CLEAR = "clear"
    const val KEY_MESSAGE = "message"
    const val KEY_LOGS = "logs"
    const val METHOD_REPORT_HOOK = "report_hook"
    private val uri = "content://moe.polariss.betteram.logs".toUri()
    private const val TAG = "BetterAMBridge"

    /** Called by the manager: its <queries> entry does not grant reverse visibility. */
    fun grantTargetAccess(context: Context) {
        runCatching {
            context.packageManager.getPackageInfo("com.apple.android.music", 0)
            context.grantUriPermission(
                "com.apple.android.music",
                uri.buildUpon().appendPath("hook").build(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure {
            if (it !is PackageManager.NameNotFoundException) {
                append(context, "Unable to grant hook provider visibility", it)
            }
        }
    }

    fun append(context: Context, message: String, error: Throwable? = null) {
        runCatching {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val detail = buildString {
                append('[').append(time).append("] [").append(android.app.Application.getProcessName()).append("] ").append(message)
                if (error != null) {
                    append('\n').append(error.stackTraceToString())
                }
            }
            context.contentResolver.call(
                uri,
                METHOD_APPEND,
                null,
                Bundle().apply { putString(KEY_MESSAGE, detail) }
            )
        }.onFailure {
            // The provider may itself be unreachable; do not report its failure
            // only through that same provider.
            Log.e(TAG, "Log delivery failed: $message", it)
            if (error != null) Log.e(TAG, message, error)
        }
    }
    fun read(context: Context): String = runCatching { context.contentResolver.call(uri, METHOD_READ, null, null)?.getString(KEY_LOGS).orEmpty() }.getOrDefault("")
    fun clear(context: Context) { runCatching { context.contentResolver.call(uri, METHOD_CLEAR, null, null) } }

    fun reportHook(context: Context) {
        runCatching {
            val info = context.packageManager.getPackageInfo("com.apple.android.music", 0)
            context.contentResolver.call(
                uri,
                METHOD_REPORT_HOOK,
                null,
                Bundle().apply {
                    putString(HookReportStore.VERSION, info.versionName ?: "?")
                    putLong(HookReportStore.VERSION_CODE, info.longVersionCode)
                    putLong(HookReportStore.UPDATED_AT, info.lastUpdateTime)
                },
            )
        }.onFailure { append(context, "Hook report failed", it) }
    }
}

internal object LogStore {
    private const val PREFS = "hook_logs"
    private const val KEY = "entries"
    private const val MAX_CHARS = 120_000
    @Synchronized fun append(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY, (prefs.getString(KEY, "").orEmpty() + message + "\n\n").takeLast(MAX_CHARS))
        }
    }
    fun read(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
    fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(KEY) } }
}
