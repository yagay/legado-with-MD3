package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.AdaptiveSwitch
import io.legado.app.ui.widget.components.SplicedColumnDivider
import top.yukonga.miuix.kmp.preference.SwitchPreference


@Composable
fun SwitchSettingItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    imageVector: ImageVector? = null,
    color: Color? = null,
    enabled: Boolean = true,
    highlightKey: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val composeEngine = LegadoTheme.composeEngine
    SplicedColumnDivider()

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        SwitchPreference(
            title = title,
            summary = description,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (highlightKey != null && title.contains(highlightKey, ignoreCase = true)) {
                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            } else Modifier,
            enabled = enabled,
        )
    } else {
        SettingItem(
            title = title,
            description = description,
            imageVector = imageVector,
            color = color,
            enabled = enabled,
            semanticRole = Role.Switch,
            semanticToggleState = checked,
            highlightKey = highlightKey,
            onClick = { if (enabled) onCheckedChange(!checked) },
            trailingContent = {
                AdaptiveSwitch(
                    modifier = Modifier.clearAndSetSemantics { },
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    includeStateSemantics = false
                )
            }
        )
    }
}
