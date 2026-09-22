package moe.polariss.betteram.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

data class AppSettings(
    val language: String = "system",
    val appearance: String = "system",
    val blackBackground: Boolean = false,
    val systemColors: Boolean = true,
) {
    fun save(context: Context) {
        context.getSharedPreferences("manager_settings", Context.MODE_PRIVATE).edit {
            putString("language", language)
            putString("appearance", appearance)
            putBoolean("black_background", blackBackground)
            putBoolean("system_colors", systemColors)
        }
    }

    fun localizedContext(context: Context): Context {
        if (language == "system") return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(language)))
        return context.createConfigurationContext(configuration)
    }

    companion object {
        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences("manager_settings", Context.MODE_PRIVATE)
            return AppSettings(
                language = prefs.getString("language", "system").orEmpty()
                    .takeIf { it in listOf("system", "zh-Hans", "zh-Hant", "en", "ja") } ?: "system",
                appearance = prefs.getString("appearance", "system").orEmpty()
                    .takeIf { it in listOf("system", "light", "dark") } ?: "system",
                blackBackground = prefs.getBoolean("black_background", false),
                systemColors = prefs.getBoolean("system_colors", true)
            )
        }
    }
}
