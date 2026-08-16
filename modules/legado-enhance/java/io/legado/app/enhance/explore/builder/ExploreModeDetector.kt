package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreMode

object ExploreModeDetector {
    fun detect(list: List<ExploreKind>): ExploreMode {
        if (list.any { it.hasModernChildren() }) {
            return ExploreMode.TREE
        }
        if (list.any { it.isModernSectionHeader() }) {
            return ExploreMode.SECTION
        }
        return ExploreMode.FLAT
    }
}
