package moe.polariss.betteram.status

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/** Framework connection is process-local; never infer activation from persistent logs. */
data class ModuleStatus(val state: State, val detail: String, val frameworkName: String = "") {
    enum class State { CHECKING, ENABLED, NO_SCOPE, DISCONNECTED, ERROR }

    companion object {
        val Checking = ModuleStatus(State.CHECKING, "正在读取框架连接与作用域…")
    }
}

object ModuleStatusReader {
    private val services = CopyOnWriteArraySet<XposedService>()

    // The helper requires one listener per process. Do not register Activity instances:
    // they are recreated on rotation and would be retained by its static listener.
    init {
        XposedServiceHelper.registerListener(
            object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) { services.add(service) }
                override fun onServiceDied(service: XposedService) { services.remove(service) }
            },
        )
    }

    /** Binder reads belong on an IO dispatcher. An unavailable service is not proof of disablement. */
    fun read(): ModuleStatus {
        if (services.isEmpty()) return ModuleStatus(ModuleStatus.State.DISCONNECTED,
            "无法确认启用状态。请在 LSPosed 中启用 Better AM，然后重新打开本应用。")
        val connected = services.mapNotNull { service ->
            runCatching { service.frameworkName to service.scope.contains("com.apple.android.music") }.getOrNull()
        }
        val enabled = connected.firstOrNull { it.second }
        return when {
            enabled != null -> ModuleStatus(ModuleStatus.State.ENABLED,
                "${enabled.first} 已连接，Apple Music 已在作用域中。更改模块后请重启 Apple Music。", enabled.first)
            connected.isNotEmpty() -> ModuleStatus(ModuleStatus.State.NO_SCOPE,
                "模块已连接框架，但尚未对 Apple Music 启用。请在 LSPosed 作用域中勾选 Apple Music。")
            else -> ModuleStatus(ModuleStatus.State.ERROR, "框架状态读取失败，请稍后重试或重新打开本应用。")
        }
    }
}
