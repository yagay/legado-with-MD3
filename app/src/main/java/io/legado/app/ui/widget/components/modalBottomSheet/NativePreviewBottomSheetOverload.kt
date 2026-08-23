package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Dedicated native draggable sheet host used by the enhanced explore source-kind preview.
 *
 * Keep this function name distinct from AppModalBottomSheet. The generic AppModalBottomSheet
 * has defaults for the same named arguments, so using the same function name makes calls with
 * show/onDismissRequest/title/containerColor/content ambiguous to Kotlin overload resolution.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceKindPreviewBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String?,
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    NativeDraggableComposeBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(containerColor),
            content = content,
        )
    }
}
