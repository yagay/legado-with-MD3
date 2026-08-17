package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.FlexChildStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernExploreStandaloneUrlEntryTest {

    @Test
    fun `full width category header stays inside compact url family`() {
        val modernRomance = urlKind(
            title = "现代言情",
            url = "/category?gender=2&category_id=1&words=-99&sort=0&over=-99&need_filters=1&page={{page}}&need_category=0",
            basis = 1f,
        )
        val ceo = urlKind(
            title = "总裁豪门",
            url = "/category?gender=2&category_id=8&words=-99&sort=0&over=-99&need_filters=1&page={{page}}&need_category=0",
            basis = 0.25f,
        )
        val workplace = urlKind(
            title = "职场情缘",
            url = "/category?gender=2&category_id=11&words=-99&sort=0&over=-99&need_filters=1&page={{page}}&need_category=0",
            basis = 0.25f,
        )
        val ancientRomance = urlKind(
            title = "古代言情",
            url = "/category?gender=2&category_id=2&words=-99&sort=0&over=-99&need_filters=1&page={{page}}&need_category=0",
            basis = 1f,
        )

        ModernExploreControlExtractor.extractNativeControls(
            listOf(modernRomance, ceo, workplace, ancientRomance)
        )

        assertFalse(ModernExploreControlExtractor.isStandaloneUrlEntry(modernRomance))
        assertFalse(ModernExploreControlExtractor.isStandaloneUrlEntry(ancientRomance))
        assertTrue(ModernExploreControlExtractor.standaloneUrlEntries().isEmpty())
    }

    @Test
    fun `isolated full width destination remains standalone entry`() {
        val select = ExploreKind(
            title = "线路",
            type = ExploreKind.Type.select,
            chars = arrayOf("线路1", "线路2"),
        )
        val shelf = urlKind(
            title = "我的入口",
            url = "/user/bookshelf?page={{page}}",
            basis = 1f,
        )
        val button = ExploreKind(
            title = "刷新配置",
            type = ExploreKind.Type.button,
            action = "java.refreshExplore()",
        )
        val category = urlKind(
            title = "玄幻",
            url = "/category?id=1&page={{page}}",
            basis = 0.25f,
        )

        ModernExploreControlExtractor.extractNativeControls(
            listOf(select, shelf, button, category)
        )

        assertTrue(ModernExploreControlExtractor.isStandaloneUrlEntry(shelf))
        assertTrue(ModernExploreControlExtractor.standaloneUrlEntries().contains(shelf))
        assertFalse(ModernExploreControlExtractor.isStandaloneUrlEntry(category))
    }

    private fun urlKind(
        title: String,
        url: String,
        basis: Float,
    ) = ExploreKind(
        title = title,
        url = url,
        style = FlexChildStyle(
            layout_flexGrow = 1f,
            layout_flexBasisPercent = basis,
        ),
    )
}
