package io.legado.app.ui.main.explore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FilterPanel(
    groups: List<FilterGroup>,
    groupSelections: Map<String, Int>,
    onNodeClick: (ExploreNode, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        groups.forEach { group ->
            FilterRow(
                group = group,
                selectedIndex = groupSelections[group.title] ?: 0,
                onNodeClick = onNodeClick
            )
        }
    }
}
