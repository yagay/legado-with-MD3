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
 * Narrow overload used by the enhanced explore source-kind preview.
 *
 * The preview content owns its scrolling and fills the native sheet viewport directly.
 * Keeping this wrapper constraint-free avoids stacking a second custom measurement layer on
 * top of the LazyColumn when the sheet moves between collapsed and expanded anchors.
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
                .fillMaxSize()
                .background(containerColor),
            content = content,
        )
    }
}
