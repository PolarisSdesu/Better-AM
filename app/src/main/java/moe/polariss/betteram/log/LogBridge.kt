package moe.polariss.betteram.log

import android.content.Context
import android.os.Bundle
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

    fun append(context: Context, message: String, error: Throwable? = null) {
        runCatching {
            val time = SimpleDateFormat("yyyy-MM-dd HH:ss.SSS", Locale.US).format(Date())
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
