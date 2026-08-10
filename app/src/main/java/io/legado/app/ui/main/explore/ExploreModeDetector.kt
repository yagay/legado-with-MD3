package io.legado.app.ui.main.explore

import io.legado.app.data.entities.rule.ExploreKind

/**
 * 发现类目模式识别。
 * 注意：layout_flexBasisPercent 只表示布局，不参与层级判定。
 */
object ExploreModeDetector {
    fun detect(kinds: List<ExploreKind>): ExploreMode {
        if (kinds.isEmpty()) return ExploreMode.FLAT
        if (kinds.any { it.hasChildren() }) return ExploreMode.TREE
        if (kinds.any { it.isGroupHeader() }) return ExploreMode.SECTION
        return ExploreMode.FLAT
    }
}
