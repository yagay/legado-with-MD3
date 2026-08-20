package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Discovery-only compatibility overload.
 *
 * The upstream AppModalBottomSheet stays byte-for-byte unchanged. Discovery
 * previously supplied a custom container color; keeping that extra argument in
 * a separate overload prevents the shared bottom-sheet implementation from
 * affecting every screen.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AppModalBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        content = content,
    )
}
