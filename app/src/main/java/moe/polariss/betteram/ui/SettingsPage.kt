@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.polariss.betteram.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShapes(),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                choices.forEachIndexed { index, (key, label) ->
                    SelectableDropdownMenuItem(
                        selected = value == key,
                        text = { Text(label) },
                        shapes = MenuDefaults.itemShape(index, choices.size),
                        leadingIcon = { RadioButton(selected = value == key, onClick = null) },
                        onClick = { expanded = false; onSelect(key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchPreference(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) {
        Text(title)
    }
}

@Composable
private fun PreferenceRow(title: String, summary: String?, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        supportingContent = summary?.let { { Text(it) } },
    ) {
        Text(title)
    }
}
