package io.legado.app.ui.main.explore

import io.legado.app.data.entities.rule.ExploreKind
import kotlinx.collections.immutable.toImmutableList

/**
 * 发现页筛选过滤器构建器
 */
object ExploreFilterBuilder {

    fun build(list: List<ExploreKind>): List<FilterGroup> {
        val result = mutableListOf<FilterGroup>()
        var currentHeader: String? = null
        var currentNodes = mutableListOf<ExploreNode>()

        list.forEach { item ->
            val url = item.action ?: item.url
            val isHeader = item.isGroupHeader()

            if (isHeader) {
                if (currentHeader != null) {
                    result.add(FilterGroup(
                        title = currentHeader!!,
                        nodes = currentNodes.toImmutableList()
                    ))
                    currentNodes = mutableListOf()
                }
                currentHeader = item.title
            } else {
                val node = ExploreNode(
                    title = item.title,
                    url = url,
                    originalKind = item,
                    level = result.size
                )

                if (currentHeader != null) {
                    currentNodes.add(node)
                } else {
                    currentHeader = "全部类目"
                    currentNodes.add(node)
                }
            }
        }

        if (currentHeader != null) {
            result.add(FilterGroup(
                title = currentHeader!!,
                nodes = currentNodes.toImmutableList()
            ))
        }

        return result
    }
}
