package io.legado.app.enhance.explore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.enhance.explore.model.ExploreNode
import io.legado.app.enhance.explore.model.FilterGroup
import io.legado.app.ui.widget.components.button.ToggleChip
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun FilterRow(
    group: FilterGroup,
    selectedIndex: Int,
    onNodeClick: (ExploreNode, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppText(
            text = group.title,
            modifier = Modifier.padding(end = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(group.nodes) { index, node ->
                ToggleChip(
                    label = node.title,
                    selected = index == selectedIndex,
                    onToggle = { onNodeClick(node, group.title) }
                )
            }
        }
    }
}
