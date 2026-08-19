package io.legado.app.enhance.explore.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * 发现页统一筛选分组模型
 */
@Immutable
data class FilterGroup(
    val title: String,
    val nodes: ImmutableList<ExploreNode>,
    val selectedIndex: Int = 0,
    val expanded: Boolean = false
) {
    val selectedNode: ExploreNode? get() = nodes.getOrNull(selectedIndex)
}
