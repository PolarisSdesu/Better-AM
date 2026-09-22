package moe.polariss.betteram.hook

import android.app.Activity
import moe.polariss.betteram.log.LogBridge
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class BetterAmModule : XposedModule() {
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if ((param.packageName != APPLE_MUSIC) || (param.applicationInfo.processName != APPLE_MUSIC)) return

        runCatching {
            val onPostResume = Activity::class.java.getDeclaredMethod("onPostResume")
            hook(onPostResume)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .setId("better-am-install-glass")
                .intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? Activity)?.let { activity ->
                        LogBridge.append(activity, "Activity resumed: ${activity.javaClass.name}")
                        LogBridge.reportHook(activity)
                        runCatching { GlassInstaller.install(activity) }
                            .onFailure { LogBridge.append(activity, "Install failed", it) }
                    }
                    result
                }
        }
    }

    private companion object {
        const val APPLE_MUSIC = "com.apple.android.music"
    }
}
