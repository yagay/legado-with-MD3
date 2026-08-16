package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ModernExploreClassificationEngineTest {

    @Test
    fun `flat source kinds keep original order and protocol types`() {
        val kinds = listOf(
            ExploreKind(title = "输入", type = ExploreKind.Type.text, action = "textAction()"),
            ExploreKind(title = "开关", type = ExploreKind.Type.toggle, chars = arrayOf("A", "B")),
            ExploreKind(title = "选择", type = ExploreKind.Type.select, chars = arrayOf("1", "2")),
            ExploreKind(title = "动作", type = ExploreKind.Type.button, action = "buttonAction()"),
            ExploreKind(title = "分类", url = "https://example.com/list")
        )

        val result = ModernExploreClassificationEngine.classify(kinds, "")

        assertEquals(ExploreMode.FLAT, result.mode)
        assertEquals(kinds.map { it.title }, result.nodes.map { it.title })
        kinds.indices.forEach { index ->
            assertSame(kinds[index], result.nodes[index].originalKind)
            assertEquals(index, result.nodes[index].sourceIndex)
        }
    }

    @Test
    fun `url kind keeps url when action is also present`() {
        val kind = ExploreKind(
            title = "分类",
            url = "https://example.com/list",
            type = ExploreKind.Type.url,
            action = "someAction()"
        )

        val result = ModernExploreClassificationEngine.classify(listOf(kind), "")

        assertEquals("https://example.com/list", result.nodes.single().url)
    }

    @Test
    fun `section grouping uses header boundaries and preserves original kinds`() {
        val header = ExploreKind(title = "频道")
        val url = ExploreKind(title = "分类一", url = "https://example.com/1")
        val select = ExploreKind(
            title = "状态",
            type = ExploreKind.Type.select,
            chars = arrayOf("全部", "完结")
        )
        val kinds = listOf(header, url, select)

        val result = ModernExploreClassificationEngine.classify(kinds, "")

        assertEquals(ExploreMode.SECTION, result.mode)
        val section = result.nodes.single()
        assertEquals("频道", section.title)
        assertSame(header, section.originalKind)
        assertEquals(listOf("分类一", "状态"), section.children.map { it.title })
        assertSame(url, section.children[0].originalKind)
        assertSame(select, section.children[1].originalKind)
    }

    @Test
    fun `titles never infer a tree when source did not declare one`() {
        val kinds = listOf(
            ExploreKind(title = "男频", url = "https://example.com/male"),
            ExploreKind(title = "女频", url = "https://example.com/female"),
            ExploreKind(title = "热门", url = "https://example.com/hot"),
            ExploreKind(title = "完结", url = "https://example.com/finished")
        )

        val result = ModernExploreClassificationEngine.classify(kinds, "")

        assertEquals(ExploreMode.FLAT, result.mode)
        assertEquals(kinds.map { it.title }, result.nodes.map { it.title })
    }

    @Test
    fun `explicit json children live only in enhance nodes`() {
        val rawJson = """
            [
              {
                "title":"频道",
                "children":[
                  {"title":"输入","type":"text","action":"runText()"},
                  {"title":"分类","url":"https://example.com/list"}
                ]
              }
            ]
        """.trimIndent()
        val flatKinds = listOf(ExploreKind(title = "fallback", url = "https://example.com/fallback"))

        val result = ModernExploreClassificationEngine.classify(flatKinds, rawJson)

        assertEquals(ExploreMode.TREE, result.mode)
        val root = result.nodes.single()
        assertEquals("频道", root.title)
        assertEquals(listOf("输入", "分类"), root.children.map { it.title })
        val input = root.children.first().originalKind!!
        assertEquals(ExploreKind.Type.text, input.type)
        assertEquals("runText()", input.action)
    }
}
