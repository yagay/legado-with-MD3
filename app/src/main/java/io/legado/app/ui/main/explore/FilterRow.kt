package io.legado.app.ui.main.explore

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.widget.components.explore.DiscoverySuiteHeader

@Composable
fun FilterRow(
    group: FilterGroup,
    selectedIndex: Int,
    onNodeClick: (ExploreNode, String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DiscoverySuiteHeader(title = group.title, modifier = Modifier.weight(1f))

            if (group.nodes.size > 8) {
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand/Collapse",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        ExploreLayoutEngine(
            renderMode = ExploreRenderMode.WATERFALL,
            nodes = group.nodes,
            sourceUrl = null,
            onNodeClick = { onNodeClick(it, group.title) },
            selectedNodeTitles = setOf(group.nodes.getOrNull(selectedIndex)?.title ?: ""),
            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
