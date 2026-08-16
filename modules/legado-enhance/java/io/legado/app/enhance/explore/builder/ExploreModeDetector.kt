package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreMode

/**
 * Plain upstream ExploreKind does not own hierarchy.
 * TREE is detected only while parsing explicit raw JSON children in
 * ModernExploreClassificationEngine.
 */
object ExploreModeDetector {
    fun detect(list: List<ExploreKind>): ExploreMode =
        if (list.any { it.isModernSectionHeader() }) ExploreMode.SECTION else ExploreMode.FLAT
}
