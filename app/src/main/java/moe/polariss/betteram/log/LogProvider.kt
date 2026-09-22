package moe.polariss.betteram.log

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

class LogProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = when (method) {
        LogBridge.METHOD_APPEND -> Bundle().apply { LogStore.append(requireNotNull(context), extras?.getString(LogBridge.KEY_MESSAGE).orEmpty()) }
        LogBridge.METHOD_READ -> Bundle().apply { putString(LogBridge.KEY_LOGS, LogStore.read(requireNotNull(context))) }
        LogBridge.METHOD_CLEAR -> Bundle().apply { LogStore.clear(requireNotNull(context)) }
        LogBridge.METHOD_REPORT_HOOK -> Bundle().apply {
            extras?.let { HookReportStore.write(requireNotNull(context), it) }
        }
        else -> Bundle()
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
