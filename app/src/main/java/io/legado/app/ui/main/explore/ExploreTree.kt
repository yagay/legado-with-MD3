package io.legado.app.ui.main.explore

import io.legado.app.data.entities.rule.ExploreKind

data class ExploreTree(
    val rootNodes: List<ExploreNode>,
    val mode: ExploreMode,
    val filterGroups: List<FilterGroup> = emptyList()
) {
    fun flattenOriginalKinds(): List<ExploreKind> {
        val result = mutableListOf<ExploreKind>()
        fun traverse(node: ExploreNode) {
            node.originalKind?.let { result.add(it) }
            node.children.forEach { traverse(it) }
        }
        rootNodes.forEach { traverse(it) }
        return result
    }
}
