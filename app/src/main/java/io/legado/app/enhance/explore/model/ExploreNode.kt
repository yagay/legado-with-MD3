package io.legado.app.enhance.explore.model

import io.legado.app.data.entities.rule.ExploreKind

/**
 * 发现页统一类目节点
 */
data class ExploreNode(
    val title: String,
    val url: String? = null,
    val children: List<ExploreNode> = emptyList(),
    val originalKind: ExploreKind? = null,
    val level: Int = 0
) {
    val isLeaf: Boolean get() = children.isEmpty() && !url.isNullOrBlank()
    val isHeader: Boolean get() = url.isNullOrBlank() && children.isNotEmpty()
}
