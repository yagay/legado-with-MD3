package io.legado.app.ui.main.explore

import io.legado.app.data.entities.rule.ExploreKind

/**
 * 分段渲染器构建逻辑
 */
object ExploreSectionBuilder {
    fun build(list: List<ExploreKind>): List<ExploreNode> {
        val result = mutableListOf<ExploreNode>()
        var currentHeader: ExploreNode? = null
        val currentChildren = mutableListOf<ExploreNode>()

        list.forEach { item ->
            val url = item.action ?: item.url
            val isHeader = item.isGroupHeader()

            if (isHeader) {
                if (currentHeader != null) {
                    result.add(currentHeader!!.copy(children = currentChildren.toList()))
                    currentChildren.clear()
                }
                currentHeader = ExploreNode(item.title, null, originalKind = item)
            } else {
                val node = ExploreNode(item.title, url, originalKind = item)
                if (currentHeader != null) {
                    currentChildren.add(node)
                } else {
                    result.add(node)
                }
            }
        }

        if (currentHeader != null) {
            result.add(currentHeader!!.copy(children = currentChildren.toList()))
        }

        return result
    }
}
