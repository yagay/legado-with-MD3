package io.legado.app.ui.widget.components.settingItem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.SplicedColumnDivider
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

@Composable
fun InputSettingItem(
    title: String,
    value: String,
    defaultValue: String? = null,
    description: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    highlightKey: String? = null,
    onConfirm: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var inputValue by remember(value) { mutableStateOf(value) }

    SplicedColumnDivider()

    SettingItem(
        title = title,
        description = description,
        imageVector = Icons.Default.Edit,
        highlightKey = highlightKey,
        option = value,
        expanded = expanded,
        onExpandChange = { expanded = it },
        color = if (highlightKey != null && title.contains(highlightKey, ignoreCase = true)) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else null,
        expandContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                MiuixTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = title,
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier.fillMaxWidth()
                )

                ConfirmDismissButtonsRow(
                    onConfirm = {
                        onConfirm(inputValue)
                        expanded = false
                    },
                    onDismiss = {
                        inputValue = value
                        expanded = false
                    },
                    confirmText = stringResource(R.string.ok),
                    dismissText = stringResource(R.string.cancel)
                )
            }
        }
    )
}
