package moe.polariss.betteram.log

import android.content.Context
import android.os.Bundle
import androidx.core.content.edit

/** The target reports its installed build independently of the user-clearable log. */
internal object HookReportStore {
    private const val PREFS = "hook_report"
    const val VERSION = "version"
    const val VERSION_CODE = "version_code"
    const val UPDATED_AT = "updated_at"

    fun write(context: Context, report: Bundle) {
        val version = report.getString(VERSION) ?: return
        if (!report.containsKey(VERSION_CODE) || !report.containsKey(UPDATED_AT)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(VERSION, version)
            putLong(VERSION_CODE, report.getLong(VERSION_CODE))
            putLong(UPDATED_AT, report.getLong(UPDATED_AT))
        }
    }

    fun readCurrentVersion(context: Context): String? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = prefs.getString(VERSION, null) ?: return@runCatching null
        val installed = context.packageManager.getPackageInfo("com.apple.android.music", 0)
        // An update/reinstall invalidates a report from the previous target installation.
        if ((prefs.getLong(VERSION_CODE, -1L) != installed.longVersionCode) ||
            (prefs.getLong(UPDATED_AT, -1L) != installed.lastUpdateTime)) return@runCatching null
        "$version (${installed.longVersionCode})"
    }.getOrNull()
}
