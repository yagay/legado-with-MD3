package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreNode

object ExploreSectionBuilder {
    fun build(list: List<ExploreKind>): List<ExploreNode> {
        val result = mutableListOf<ExploreNode>()
        var currentSectionIndex: Int? = null

        list.forEach { item ->
            val node = ExploreNode(
                title = item.title,
                url = item.modernTargetUrl(),
                originalKind = item
            )

            if (item.isModernSectionHeader()) {
                result += node
                currentSectionIndex = result.lastIndex
            } else {
                val sectionIndex = currentSectionIndex
                if (sectionIndex == null) {
                    result += ExploreNode(
                        title = "其他",
                        children = listOf(node),
                        originalKind = null
                    )
                    currentSectionIndex = result.lastIndex
                } else {
                    val section = result[sectionIndex]
                    result[sectionIndex] = section.copy(children = section.children + node)
                }
            }
        }
        return result
    }
}
