package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreNode

object ExploreTreeBuilder {
    fun build(list: List<ExploreKind>, level: Int = 0): List<ExploreNode> {
        return list.map { item ->
            ExploreNode(
                title = item.title,
                url = item.action ?: item.url,
                children = item.children?.let { build(it, level + 1) } ?: emptyList(),
                originalKind = item,
                level = level
            )
        }
    }
}
