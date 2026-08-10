package io.legado.app.ui.main.explore

import io.legado.app.data.entities.rule.ExploreKind

/**
 * 递归树构建器
 */
object ExploreTreeBuilder {
    fun build(kinds: List<ExploreKind>, level: Int = 0): List<ExploreNode> {
        return kinds.map { kind ->
            ExploreNode(
                title = kind.title,
                url = kind.action ?: kind.url,
                children = kind.children?.let { build(it, level + 1) } ?: emptyList(),
                originalKind = kind,
                level = level
            )
        }
    }
}
