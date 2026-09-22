package moe.polariss.betteram.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.polariss.betteram.R
import moe.polariss.betteram.settings.AppSettings

@Composable
internal fun SettingsPage(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        SettingsCategory(stringResource(R.string.language))
        ChoicePreference(
            title = stringResource(R.string.language),
            value = settings.language,
            choices = listOf(
                "system" to stringResource(R.string.follow_system),
                "zh-Hans" to "简体中文",
                "zh-Hant" to "繁體中文",
                "en" to "English",
                "ja" to "日本語",
            ),
            onSelect = { onChange(settings.copy(language = it)) }
        )
        SettingsCategory(stringResource(R.string.appearance))
        ChoicePreference(
            title = stringResource(R.string.dark_theme),
            value = settings.appearance,
            choices = listOf(
                "system" to stringResource(R.string.follow_system),
                "light" to stringResource(R.string.theme_light),
                "dark" to stringResource(R.string.theme_dark),
            ),
            onSelect = { onChange(settings.copy(appearance = it)) }
        )
        if (settings.appearance != "light") {
            SwitchPreference(
                stringResource(R.string.black_background),
                stringResource(R.string.black_background_summary),
                settings.blackBackground
            ) {
                onChange(settings.copy(blackBackground = it))
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            SwitchPreference(
                stringResource(R.string.system_colors),
                null,
                settings.systemColors
            ) {
                onChange(settings.copy(systemColors = it))
            }
        }
    }
}

@Composable
private fun SettingsCategory(title: String) {
    Text(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ChoicePreference(title: String, value: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(value = false) }
    Box {
        PreferenceRow(title, choices.firstOrNull { it.first == value }?.second.orEmpty(), { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (key, label) ->
                DropdownMenuItem(text = { Text(label) },
                    leadingIcon = { RadioButton(selected = value == key, onClick = null) },
                    onClick = { expanded = false; onSelect(key) })
            }
        }
    }
}

@Composable
private fun SwitchPreference(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    PreferenceRow(title, summary, { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PreferenceRow(title: String, summary: String?, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 72.dp)
        .padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    summary,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}
