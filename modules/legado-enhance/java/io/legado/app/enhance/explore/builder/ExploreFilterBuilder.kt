package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreNode
import io.legado.app.enhance.explore.model.FilterGroup
import kotlinx.collections.immutable.toImmutableList

/**
 * 发现页筛选行构建器。
 * 只消费 enhance 的结构判断，不向上游 ExploreKind 增加辅助协议。
 */
object ExploreFilterBuilder {

    fun build(list: List<ExploreKind>): List<FilterGroup> {
        val result = mutableListOf<FilterGroup>()
        var currentHeader: String? = null
        var currentNodes = mutableListOf<ExploreNode>()

        fun flush() {
            val header = currentHeader ?: return
            result += FilterGroup(
                title = header,
                nodes = currentNodes.toImmutableList()
            )
            currentNodes = mutableListOf()
        }

        list.forEach { item ->
            if (item.isModernSectionHeader()) {
                flush()
                currentHeader = item.title
            } else {
                val node = ExploreNode(
                    title = item.title,
                    url = item.modernTargetUrl(),
                    originalKind = item,
                    level = result.size
                )

                if (currentHeader == null) {
                    currentHeader = "分类"
                }
                currentNodes += node
            }
        }

        flush()
        return result
    }
}
