package com.example.todo.ui.settings

import com.example.todo.R
import com.example.todo.i18n.t
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.todo.data.settings.UserSettings
import com.example.todo.ui.theme.parseAccent
import kotlin.math.roundToInt

@Composable
fun LanguageSetting() {
    var open by remember { mutableStateOf(false) }
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("language-setting")) { Text(t(R.string.app_language)) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(t(R.string.app_language)) }, text = {
        Column {
            listOf("" to t(R.string.system_default), "zh-CN" to t(R.string.simplified_chinese), "en" to "English").forEach { (tag, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(current == tag, onClick = {
                        open = false
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }, modifier = Modifier.testTag("language-${tag.ifEmpty { "system" }}"))
                    Text(label)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text(t(R.string.cancel)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteSettings(settings: UserSettings, onSelect: (Long) -> Unit) {
    var custom by rememberSaveable { mutableStateOf(false) }
    Text(t(R.string.color_palette), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0xFFA8583D to t(R.string.terracotta), 0xFF52785E to t(R.string.sage), 0xFF3D6F9E to t(R.string.ocean), 0xFF7A5A97 to t(R.string.lavender), 0xFFA74E70 to t(R.string.rose)).forEach { (color, name) ->
            FilterChip(selected = !settings.dynamicColor && settings.accentColor == color, onClick = { onSelect(color) },
                leadingIcon = { Box(Modifier.size(16.dp).background(Color(color), CircleShape)) },
                label = { Text(name) }, modifier = Modifier.testTag("accent-$color"))
        }
    }
    TextButton(onClick = { custom = true }, modifier = Modifier.testTag("custom-accent")) { Text(t(R.string.custom_accent_color)) }
    Text(t(R.string.choosing_a_color_turns_off_wallpaper_colors_text_and_buttons_adju), style = MaterialTheme.typography.bodySmall)
    if (custom) AccentDialog(settings.accentColor, onDismiss = { custom = false }, onApply = { onSelect(it); custom = false })
}

@Composable
private fun AccentDialog(initial: Long, onDismiss: () -> Unit, onApply: (Long) -> Unit) {
    var hex by rememberSaveable { mutableStateOf("#%06X".format(initial and 0xFFFFFF)) }
    val parsed = parseAccent(hex)
    val color = parsed ?: initial
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t(R.string.custom_accent_color)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(44.dp).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline))
            OutlinedTextField(hex, { value -> hex = value.take(7) }, label = { Text(t(R.string.hex_color)) },
                placeholder = { Text("#A8583D") }, singleLine = true, isError = parsed == null,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("accent-hex"))
            listOf(16 to t(R.string.red), 8 to t(R.string.green), 0 to t(R.string.blue)).forEach { (shift, label) ->
                Text(label, style = MaterialTheme.typography.bodySmall)
                Slider(value = ((color shr shift) and 255).toFloat(), valueRange = 0f..255f,
                    onValueChange = { value ->
                        val updated = (color and (255L shl shift).inv()) or (value.roundToInt().toLong() shl shift)
                        hex = "#%06X".format(updated and 0xFFFFFF)
                    }, modifier = Modifier.testTag("accent-channel-$shift"))
            }
        }
    }, confirmButton = { TextButton(enabled = parsed != null, onClick = { parsed?.let(onApply) }) { Text(t(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t(R.string.cancel)) } })
}
