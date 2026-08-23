package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Narrow overload used by the enhanced explore source-kind preview.
 *
 * Source-native category previews are commonly composed from a long static Column/Row tree
 * rather than a LazyColumn. Keep this host vertically scrollable so content taller than the
 * current draggable sheet viewport is not clipped at the sheet boundary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
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
                .fillMaxWidth()
                .background(containerColor)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}
