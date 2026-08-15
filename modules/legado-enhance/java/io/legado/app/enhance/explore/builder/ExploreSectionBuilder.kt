package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreNode

object ExploreSectionBuilder {
    fun build(list: List<ExploreKind>): List<ExploreNode> {
        val result = mutableListOf<ExploreNode>()
        var currentSection: ExploreNode? = null

        list.forEach { item ->
            val node = ExploreNode(
                title = item.title,
                url = item.action ?: item.url,
                originalKind = item
            )

            if (item.isGroupHeader()) {
                currentSection = node
                result.add(node)
            } else {
                if (currentSection == null) {
                    currentSection = ExploreNode(title = "其他", originalKind = null)
                    result.add(currentSection!!)
                }
                val updatedChildren = currentSection!!.children.orEmpty() + node
                val index = result.indexOf(currentSection)
                if (index >= 0) {
                    val updatedSection = currentSection!!.copy(children = updatedChildren)
                    result[index] = updatedSection
                    currentSection = updatedSection
                }
            }
        }
        return result
    }
}
