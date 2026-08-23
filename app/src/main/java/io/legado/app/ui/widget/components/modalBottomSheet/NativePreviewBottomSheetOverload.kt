package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
 * The source-kind preview in ExploreScreen still carries a legacy 560.dp max-height cap.
 * In the draggable sheet that leaves a large blank area below long category lists. Keep the
 * actual preview LazyColumn as a direct child of a custom Layout and measure it with the exact
 * current sheet viewport. Exact parent constraints override that legacy max-height constraint,
 * while the LazyColumn remains the only vertical scroll owner.
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
        ) {
            val previewColumnScope = this
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    with(previewColumnScope) {
                        content()
                    }
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
}
