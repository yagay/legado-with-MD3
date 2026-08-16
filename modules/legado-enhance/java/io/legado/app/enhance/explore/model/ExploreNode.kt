package io.legado.app.enhance.explore.model

import io.legado.app.data.entities.rule.ExploreKind

/**
 * 现代发现页自己的展示树节点。
 *
 * 层级、父子关系和 sourceIndex 都属于 enhance 展示元数据；
 * originalKind 始终保留上游 ExploreKind 的原始协议行为。
 */
data class ExploreNode(
    val title: String,
    val url: String? = null,
    val children: List<ExploreNode> = emptyList(),
    val originalKind: ExploreKind? = null,
    val level: Int = 0,
    val sourceIndex: Int = -1,
) {
    val isLeaf: Boolean get() = children.isEmpty() && !url.isNullOrBlank()
    val isHeader: Boolean get() = url.isNullOrBlank() && children.isNotEmpty()
}
