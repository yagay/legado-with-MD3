package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout

/**
 * Narrow overload used by the enhanced explore source-kind preview.
 *
 * The source-kind preview currently contains a LazyColumn with a legacy 560.dp max-height cap.
 * In the draggable native sheet that cap leaves the rest of the sheet blank and makes the
 * category content look clipped. Measure the single preview content subtree with the sheet's
 * exact viewport constraints so the lazy list always owns the whole visible content area.
 *
 * This host intentionally does not add another verticalScroll: the preview LazyColumn is the
 * scroll owner, avoiding nested vertical scroll and stale height constraints while the sheet
 * moves between collapsed and expanded anchors.
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
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .background(containerColor),
            content = {
                PreviewColumnScope(content)
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth.coerceAtLeast(constraints.minWidth)
            val height = constraints.maxHeight.coerceAtLeast(constraints.minHeight)
            val exactConstraints = constraints.copy(
                minWidth = width,
                maxWidth = width,
                minHeight = height,
                maxHeight = height,
            )
            val placeables = measurables.map { measurable ->
                measurable.measure(exactConstraints)
            }
            layout(width, height) {
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, 0)
                }
            }
        }
    }
}

@Composable
private fun PreviewColumnScope(
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        content()
    }
}
