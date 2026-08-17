package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.SplicedColumnDivider
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

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
    highlightKey: String? = null,
    onValuePreviewChange: ((Float) -> Unit)? = null,
    onValueChange: (Float) -> Unit
) {

    var expanded by remember { mutableStateOf(false) }
    var isInputMode by remember { mutableStateOf(false) }
    var sliderValue by remember(value) { mutableFloatStateOf(value) }

    SplicedColumnDivider()

    SettingItem(
        title = title,
        description = description,
        imageVector = Icons.Default.LinearScale,
        highlightKey = highlightKey,
        option = valueLabel?.invoke(if (expanded) sliderValue else value)
            ?: if (decimal) (if (expanded) sliderValue else value).toString()
            else (if (expanded) sliderValue else value).roundToInt().toString(),
        expanded = expanded,
        onExpandChange = { expanded = it },
        color = if (highlightKey != null && title.contains(highlightKey, ignoreCase = true)) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else color,
        expandContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isInputMode) {
                    MiuixTextField(
                        value = sliderValue.toString(),
                        onValueChange = {
                            it.toFloatOrNull()?.let { v ->
                                sliderValue = v.coerceIn(valueRange)
                                onValuePreviewChange?.invoke(sliderValue)
                            }
                        },
                        label = stringResource(R.string.edit),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            onValuePreviewChange?.invoke(it)
                        },
                        valueRange = valueRange,
                        steps = steps,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isInputMode = !isInputMode }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        sliderValue = defaultValue
                        onValuePreviewChange?.invoke(defaultValue)
                    }) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = stringResource(R.string.restore_default),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    ConfirmDismissButtonsRow(
                        onConfirm = {
                            onValueChange(sliderValue)
                            expanded = false
                        },
                        onDismiss = {
                            sliderValue = value
                            onValuePreviewChange?.invoke(value)
                            expanded = false
                        },
                        confirmText = stringResource(R.string.ok),
                        dismissText = stringResource(R.string.cancel)
                    )
                }
            }
        }
    )
}
