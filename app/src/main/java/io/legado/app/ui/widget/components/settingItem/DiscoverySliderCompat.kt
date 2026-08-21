package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Compatibility overload used only by the modern discovery layout.
 * Keeps the upstream SliderSettingItem API untouched.
 */
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
        onValueChange = { newValue ->
            onValuePreviewChange(newValue)
            onValueChange(newValue)
        },
    )
}
