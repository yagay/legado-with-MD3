package io.legado.app.enhance.explore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreNode
import io.legado.app.ui.widget.components.card.TextCard

@Composable
fun ExploreSectionLayout(
    sections: List<ExploreNode>,
    onNodeClick: (ExploreNode) -> Unit
) {
    sections.forEach { section ->
        if (section.title.isNotBlank()) {
            io.legado.app.ui.widget.components.divider.PillHeaderDivider(title = section.title)
        }
        val chunks = section.children.chunked(3)
        chunks.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { node ->
                    TextCard(
                        text = node.title,
                        modifier = Modifier.weight(1f),
                        onClick = { onNodeClick(node) }
                    )
                }
                if (row.size < 3) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight((3 - row.size).toFloat()))
                }
            }
        }
    }
}

@Composable
fun ExploreFlatLayout(
    nodes: List<ExploreNode>,
    onNodeClick: (ExploreNode) -> Unit
) {
    val chunks = nodes.chunked(3)
    chunks.forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { node ->
                TextCard(
                    text = node.title,
                    modifier = Modifier.weight(1f),
                    onClick = { onNodeClick(node) }
                )
            }
            if (row.size < 3) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight((3 - row.size).toFloat()))
            }
        }
    }
}
