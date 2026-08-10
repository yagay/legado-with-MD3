package io.legado.app.ui.main.explore

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem
import io.legado.app.ui.widget.components.explore.calculateExploreKindRows
import io.legado.app.utils.findActivity

enum class ExploreRenderMode {
    FLEX,
    GRID,
    WATERFALL,
    CHIP_ROW
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreLayoutEngine(
    renderMode: ExploreRenderMode,
    nodes: List<ExploreNode>,
    sourceUrl: String?,
    onNodeClick: (ExploreNode) -> Unit,
    modifier: Modifier = Modifier,
    selectedNodeTitles: Set<String> = emptySet(),
    maxLines: Int = Int.MAX_VALUE,
    isMiuix: Boolean = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
) {
    val context = LocalContext.current
    val activity = context.findActivity() as? AppCompatActivity

    when (renderMode) {
        ExploreRenderMode.FLEX -> {
            val kinds = nodes.mapNotNull { it.originalKind }
            val rows = calculateExploreKindRows(kinds, maxSpan = 6)
            Column(modifier = modifier.fillMaxWidth()) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (kind, span) ->
                            ExploreKindMultiTypeItem(
                                kind = kind,
                                sourceUrl = sourceUrl,
                                activity = activity,
                                onOpenUrl = { _ ->
                                    val node = nodes.find { it.title == kind.title }
                                    if (node != null) onNodeClick(node)
                                },
                                modifier = Modifier.weight(span.toFloat()),
                                isMiuix = isMiuix,
                                isSelected = kind.title in selectedNodeTitles
                            )
                        }
                        val totalSpan = rowItems.sumOf { it.second }
                        if (totalSpan < 6) {
                            Spacer(modifier = Modifier.weight((6 - totalSpan).toFloat()))
                        }
                    }
                }
            }
        }

        ExploreRenderMode.GRID -> {
            FlowRow(
                modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3
            ) {
                nodes.forEach { node ->
                    ExploreNodeRenderer(node, node.title in selectedNodeTitles, onNodeClick, Modifier.weight(1f))
                }
            }
        }

        ExploreRenderMode.WATERFALL -> {
            FlowRow(
                modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxLines = maxLines
            ) {
                nodes.forEach { node ->
                    ExploreNodeRenderer(node, node.title in selectedNodeTitles, onNodeClick)
                }
            }
        }

        ExploreRenderMode.CHIP_ROW -> {
            FlowRow(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                nodes.forEach { node ->
                    ExploreNodeRenderer(
                        node = node,
                        isSelected = node.title in selectedNodeTitles,
                        onNodeClick = onNodeClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreNodeRenderer(
    node: ExploreNode,
    isSelected: Boolean,
    onNodeClick: (ExploreNode) -> Unit,
    modifier: Modifier = Modifier
) {
    ExploreFilterChip(
        selected = isSelected,
        onClick = { onNodeClick(node) },
        label = node.title,
        modifier = modifier
    )
}
