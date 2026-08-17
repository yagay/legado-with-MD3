package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ModernExploreStructuralSelectTest {

    @Before
    fun setUp() {
        ModernExploreControlExtractor.resetStructureLearning()
    }

    @After
    fun tearDown() {
        ModernExploreControlExtractor.resetStructureLearning()
    }

    @Test
    fun `select that changes downstream structure becomes parent label`() {
        val initial = listOf(
            ExploreKind(
                title = "频道",
                type = ExploreKind.Type.select,
                chars = arrayOf("女频", "男频"),
                default = "女频",
                action = "setChannel();java.refreshExplore()",
            ),
            ExploreKind(
                title = "现代言情",
                url = "/category?gender=2&category_id=1&page={{page}}",
            ),
            ExploreKind(
                title = "总裁豪门",
                url = "/category?gender=2&category_id=8&page={{page}}",
            ),
        )
        ModernExploreControlExtractor.extractNativeControls(initial)

        val changed = listOf(
            ExploreKind(
                title = "频道",
                type = ExploreKind.Type.select,
                chars = arrayOf("女频", "男频"),
                default = "男频",
                action = "setChannel();java.refreshExplore()",
            ),
            ExploreKind(
                title = "历史",
                url = "/category?gender=1&category_id=56&page={{page}}",
            ),
            ExploreKind(
                title = "穿越历史",
                url = "/category?gender=1&category_id=57&page={{page}}",
            ),
        )
        ModernExploreControlExtractor.extractNativeControls(changed)

        val firstCategoryIndex = ModernExploreControlExtractor.sourceIndexOfTarget(
            title = "历史",
            url = "/category?gender=1&category_id=56&page={{page}}",
        )
        assertEquals(
            "男频",
            ModernExploreControlExtractor.structuralParentSelectionBefore(firstCategoryIndex),
        )
    }

    @Test
    fun `select that only changes url values stays independent`() {
        val initial = listOf(
            ExploreKind(
                title = "字数",
                type = ExploreKind.Type.select,
                chars = arrayOf("全部", "50万以下"),
                default = "全部",
                action = "setWords();java.refreshExplore()",
            ),
            ExploreKind(
                title = "现代言情",
                url = "/category?category_id=1&words=-99&page={{page}}",
            ),
            ExploreKind(
                title = "总裁豪门",
                url = "/category?category_id=8&words=-99&page={{page}}",
            ),
        )
        ModernExploreControlExtractor.extractNativeControls(initial)

        val changed = listOf(
            ExploreKind(
                title = "字数",
                type = ExploreKind.Type.select,
                chars = arrayOf("全部", "50万以下"),
                default = "50万以下",
                action = "setWords();java.refreshExplore()",
            ),
            ExploreKind(
                title = "现代言情",
                url = "/category?category_id=1&words=1&page={{page}}",
            ),
            ExploreKind(
                title = "总裁豪门",
                url = "/category?category_id=8&words=1&page={{page}}",
            ),
        )
        ModernExploreControlExtractor.extractNativeControls(changed)

        val firstCategoryIndex = ModernExploreControlExtractor.sourceIndexOfTarget(
            title = "现代言情",
            url = "/category?category_id=1&words=1&page={{page}}",
        )
        assertNull(
            ModernExploreControlExtractor.structuralParentSelectionBefore(firstCategoryIndex)
        )
    }
}
