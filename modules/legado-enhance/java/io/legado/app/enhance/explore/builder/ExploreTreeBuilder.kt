package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreNode

/** Converts a plain upstream ExploreKind list into root-level enhance nodes. */
object ExploreTreeBuilder {
    fun build(list: List<ExploreKind>): List<ExploreNode> =
        list.mapIndexed { index, item ->
            ExploreNode(
                title = item.title,
                url = item.modernTargetUrl(),
                originalKind = item,
                level = 0,
                sourceIndex = index,
            )
        }
}
