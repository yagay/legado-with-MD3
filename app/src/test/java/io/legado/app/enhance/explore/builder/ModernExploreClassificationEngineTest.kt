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
        assertEquals(kinds, result.kinds)
        kinds.indices.forEach { index -> assertSame(kinds[index], result.kinds[index]) }
    }

    @Test
    fun `section grouping uses header boundaries and preserves child kinds`() {
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
        val section = result.kinds.single()
        assertEquals("频道", section.title)
        assertEquals(listOf(url, select), section.children)
        assertSame(url, section.children.orEmpty()[0])
        assertSame(select, section.children.orEmpty()[1])
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
        assertEquals(kinds, result.kinds)
    }

    @Test
    fun `explicit json children are the only source of tree hierarchy`() {
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
        assertEquals("频道", result.kinds.single().title)
        val children = result.kinds.single().children.orEmpty()
        assertEquals(listOf("输入", "分类"), children.map { it.title })
        assertEquals(ExploreKind.Type.text, children.first().type)
        assertEquals("runText()", children.first().action)
    }
}
