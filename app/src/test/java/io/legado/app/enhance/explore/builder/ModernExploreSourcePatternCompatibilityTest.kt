package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.FlexChildStyle
import io.legado.app.enhance.explore.model.ExploreMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Representative patterns distilled from the user's large Legado source collection.
 * These tests intentionally describe source structure, never source names.
 */
class ModernExploreSourcePatternCompatibilityTest {

    private val full = FlexChildStyle(layout_flexGrow = 1f, layout_flexBasisPercent = 1f)
    private val compact = FlexChildStyle(layout_flexGrow = 1f, layout_flexBasisPercent = 0.25f)

    @Test
    fun `plain url list always remains flat and ordered`() {
        val kinds = listOf(
            ExploreKind(title = "A", url = "/category/1"),
            ExploreKind(title = "B", url = "/category/2"),
            ExploreKind(title = "C", url = "/category/3"),
        )

        val result = ModernExploreClassificationEngine.classify(kinds, "")

        assertEquals(ExploreMode.FLAT, result.mode)
        assertEquals(listOf("A", "B", "C"), result.nodes.map { it.title })
        assertEquals(listOf(0, 1, 2), result.nodes.map { it.sourceIndex })
    }

    @Test
    fun `blank full width headings form independent sections without reordering`() {
        val kinds = listOf(
            ExploreKind(title = "一区", style = full),
            ExploreKind(title = "A", url = "/a", style = compact),
            ExploreKind(title = "B", url = "/b", style = compact),
            ExploreKind(title = "二区", style = full),
            ExploreKind(title = "C", url = "/c", style = compact),
        )

        val result = ModernExploreClassificationEngine.classify(kinds, "")

        assertEquals(ExploreMode.SECTION, result.mode)
        assertEquals(listOf("一区", "二区"), result.nodes.map { it.title })
        assertEquals(listOf("A", "B"), result.nodes[0].children.map { it.title })
        assertEquals(listOf("C"), result.nodes[1].children.map { it.title })
    }

    @Test
    fun `verified cartesian matrix is factored inside ordinary section`() {
        fun item(title: String, rank: Int, status: String) = ExploreKind(
            title = title,
            url = "/list?rank=$rank&status=$status&page={{page}}",
            style = compact,
        )
        val kinds = listOf(
            ExploreKind(title = "榜单", style = full),
            item("推荐", 0, "all"), item("完结", 0, "done"), item("连载", 0, "loading"),
            item("评分", 1, "all"), item("完结", 1, "done"), item("连载", 1, "loading"),
            item("热门", 2, "all"), item("完结", 2, "done"), item("连载", 2, "loading"),
        )

        val result = ModernExploreClassificationEngine.classify(kinds, "")
        val section = result.nodes.single()

        assertEquals(ExploreMode.SECTION, result.mode)
        assertEquals(listOf("推荐", "评分", "热门"), section.children.map { it.title })
        section.children.forEach { rank ->
            assertEquals(1, rank.children.size)
            assertEquals(3, rank.children.single().children.size)
        }
    }

    @Test
    fun `full width url stays category when compact siblings share url family`() {
        val kinds = listOf(
            ExploreKind(title = "一级A", url = "/category?kind=1&page={{page}}", style = full),
            ExploreKind(title = "A1", url = "/category?kind=2&page={{page}}", style = compact),
            ExploreKind(title = "A2", url = "/category?kind=3&page={{page}}", style = compact),
            ExploreKind(title = "一级B", url = "/category?kind=4&page={{page}}", style = full),
            ExploreKind(title = "B1", url = "/category?kind=5&page={{page}}", style = compact),
        )

        ModernExploreControlExtractor.extractNativeControls(kinds)

        assertFalse(ModernExploreControlExtractor.isStandaloneUrlEntry(kinds[0]))
        assertFalse(ModernExploreControlExtractor.isStandaloneUrlEntry(kinds[3]))
        assertTrue(ModernExploreControlExtractor.standaloneUrlEntries().isEmpty())
    }

    @Test
    fun `isolated full width url remains standalone entry`() {
        val kinds = listOf(
            ExploreKind(title = "入口", url = "/bookshelf", style = full),
            ExploreKind(title = "分类A", url = "/category?id=1", style = compact),
            ExploreKind(title = "分类B", url = "/category?id=2", style = compact),
        )

        ModernExploreControlExtractor.extractNativeControls(kinds)

        assertTrue(ModernExploreControlExtractor.isStandaloneUrlEntry(kinds[0]))
        assertEquals(listOf("入口"), ModernExploreControlExtractor.standaloneUrlEntries().map { it.title })
    }
}
