package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Compatibility overload for discovery settings.
 * Upstream SliderSettingItem remains the actual renderer and commits values on release.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SliderSettingItem(
    title: String,
    color: Color? = null,
    value: Float,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    description: String? = null,
    valueLabel: ((Float) -> String)? = null,
    decimal: Boolean = false,
    onValuePreviewChange: (Float) -> Unit,
    onValueChange: (Float) -> Unit,
) {
    SliderSettingItem(
        title = title,
        color = color,
        value = value,
        defaultValue = defaultValue,
        valueRange = valueRange,
        steps = steps,
        description = description,
        valueLabel = valueLabel,
        decimal = decimal,
        onValueChange = onValueChange,
    )
}
