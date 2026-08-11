package io.legado.app.ui.widget.components.settingItem

import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.SplicedColumnDivider
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference

@Composable
fun DropdownListSettingItem(
    title: String,
    selectedValue: String,
    displayEntries: Array<String>,
    entryValues: Array<String>,
    description: String? = null,
    imageVector: ImageVector? = null,
    highlightKey: String? = null,
    onValueChange: (String) -> Unit
) {
    val composeEngine = LegadoTheme.composeEngine
    SplicedColumnDivider()

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        val selectedIndex = entryValues.indexOf(selectedValue).coerceAtLeast(0)
        val spinnerItems = displayEntries.map { display ->
            DropdownItem(title = display)
        }

        OverlaySpinnerPreference(
            title = title,
            summary = description,
            items = spinnerItems,
            selectedIndex = selectedIndex,
            modifier = if (highlightKey != null && title.contains(highlightKey, ignoreCase = true)) {
                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            } else Modifier,
            startAction = imageVector?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null
                    )
                }
            },
            onSelectedIndexChange = { index ->
                onValueChange(entryValues[index])
            }
        )
    } else {
        SettingItem(
            title = title,
            description = description,
            imageVector = imageVector,
            highlightKey = highlightKey,
            option = displayEntries.getOrNull(entryValues.indexOf(selectedValue)),
            dropdownMenu = { onDismiss ->
                displayEntries.forEachIndexed { index, display ->
                    RoundDropdownMenuItem(
                        text = display,
                        isSelected = entryValues[index] == selectedValue,
                        onClick = {
                            onValueChange(entryValues[index])
                            onDismiss()
                        }
                    )
                }
            }
        )
    }
}
