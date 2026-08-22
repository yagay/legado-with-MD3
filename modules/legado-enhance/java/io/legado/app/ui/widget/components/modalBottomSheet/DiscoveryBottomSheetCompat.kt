package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Compatibility overload for modern discovery.
 * The sheet itself stays owned by the upstream AppModalBottomSheet implementation.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    animateContentSize: Boolean = true,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    contentPaddingEnabled: Boolean = true,
    sheetGesturesEnabled: Boolean = true,
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title,
        startAction = startAction,
        endAction = endAction,
        animateContentSize = animateContentSize,
        contentWindowInsets = contentWindowInsets,
        contentPaddingEnabled = contentPaddingEnabled,
        sheetGesturesEnabled = sheetGesturesEnabled,
        content = content,
    )
}
