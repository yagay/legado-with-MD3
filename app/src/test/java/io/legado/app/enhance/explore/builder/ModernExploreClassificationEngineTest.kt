package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.FlexChildStyle
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
    fun `single direct explore url parsed as title is recovered`() {
        val kind = ExploreKind(
            title = "https://example.com/discover?page={{page}}",
            type = ExploreKind.Type.url
        )

        val result = ModernExploreClassificationEngine.classify(listOf(kind), "")

        assertEquals("https://example.com/discover?page={{page}}", result.nodes.single().url)
    }

    @Test
    fun `ordinary header title is never treated as direct url`() {
        val kind = ExploreKind(title = "频道", type = ExploreKind.Type.url)

        val result = ModernExploreClassificationEngine.classify(listOf(kind), "")

        assertEquals(null, result.nodes.single().url)
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
    fun `legacy flat visual hierarchy keeps channel groups above categories`() {
        val fullRow = FlexChildStyle(layout_flexGrow = 1f, layout_flexBasisPercent = 1f)
        val quarterRow = FlexChildStyle(layout_flexGrow = 1f, layout_flexBasisPercent = 0.25f)
        fun header(title: String) = ExploreKind(title = title, style = fullRow)
        fun item(title: String, id: String) = ExploreKind(
            title = title,
            url = "https://example.com/$id",
            style = quarterRow,
        )

        val kinds = listOf(
            ExploreKind(title = "我的书架", url = "https://example.com/bookshelf", style = fullRow),
            header("༺ˇ»`ʚ男生频道ɞ´«ˇ༻"),
            header("༺ 玄幻 ༻"),
            item("[推荐]", "male-fantasy-recommend"),
            item("完结", "male-fantasy-finished"),
            item("连载", "male-fantasy-loading"),
            header("༺ 神豪 ༻"),
            item("[推荐]", "male-rich-recommend"),
            item("完结", "male-rich-finished"),
            item("连载", "male-rich-loading"),
            header("༺ˇ»`ʚ女生频道ɞ´«ˇ༻"),
            header("༺ 无敌 ༻"),
            item("[推荐]", "female-invincible-recommend"),
            item("完结", "female-invincible-finished"),
            item("连载", "female-invincible-loading"),
            header("༺ 种田 ༻"),
            item("[推荐]", "female-farm-recommend"),
            item("完结", "female-farm-finished"),
            item("连载", "female-farm-loading"),
        )

        val result = ModernExploreClassificationEngine.classify(kinds, "")

        assertEquals(ExploreMode.SECTION, result.mode)
        assertEquals(
            listOf("我的书架", "༺ˇ»`ʚ男生频道ɞ´«ˇ༻", "༺ˇ»`ʚ女生频道ɞ´«ˇ༻"),
            result.nodes.map { it.title }
        )

        val male = result.nodes[1]
        assertEquals(listOf("༺ 玄幻 ༻", "༺ 神豪 ༻"), male.children.map { it.title })
        assertEquals(listOf("[推荐]", "完结", "连载"), male.children[0].children.map { it.title })
        assertEquals(listOf("[推荐]", "完结", "连载"), male.children[1].children.map { it.title })

        val female = result.nodes[2]
        assertEquals(listOf("༺ 无敌 ༻", "༺ 种田 ༻"), female.children.map { it.title })
        assertEquals(listOf("[推荐]", "完结", "连载"), female.children[0].children.map { it.title })
        assertEquals(listOf("[推荐]", "完结", "连载"), female.children[1].children.map { it.title })
    }

    @Test
    fun `legacy url matrix becomes independent rank and status dimensions`() {
        val fullRow = FlexChildStyle(layout_flexGrow = 1f, layout_flexBasisPercent = 1f)
        val quarterRow = FlexChildStyle(layout_flexGrow = 1f, layout_flexBasisPercent = 0.25f)
        fun header(title: String) = ExploreKind(title = title, style = fullRow)
        fun matrixItem(title: String, sort: Int, status: String) = ExploreKind(
            title = title,
            url = "https://example.com/list?gender=0&tags=7&creation_status=$status&sort=$sort&page={{page}}",
            style = quarterRow,
        )
        fun simpleItem(title: String, id: String) = ExploreKind(
            title = title,
            url = "https://example.com/$id",
            style = quarterRow,
        )

        val kinds = listOf(
            ExploreKind(title = "我的书架", url = "https://example.com/bookshelf", style = fullRow),
            header("༺ˇ»`ʚ男生频道ɞ´«ˇ༻"),
            header("༺ 玄幻 ༻"),
            matrixItem("[推荐]", 0, "ALL"),
            matrixItem("完结", 0, "Finished"),
            matrixItem("连载", 0, "Loading"),
            matrixItem("[评分]", 1, "ALL"),
            matrixItem("完结", 1, "Finished"),
            matrixItem("连载", 1, "Loading"),
            matrixItem("[热门]", 2, "ALL"),
            matrixItem("完结", 2, "Finished"),
            matrixItem("连载", 2, "Loading"),
            header("༺ 神豪 ༻"),
            simpleItem("[推荐]", "male-rich-recommend"),
            simpleItem("完结", "male-rich-finished"),
            simpleItem("连载", "male-rich-loading"),
            header("༺ˇ»`ʚ女生频道ɞ´«ˇ༻"),
            header("༺ 无敌 ༻"),
            simpleItem("[推荐]", "female-invincible-recommend"),
            simpleItem("完结", "female-invincible-finished"),
            simpleItem("连载", "female-invincible-loading"),
            header("༺ 种田 ༻"),
            simpleItem("[推荐]", "female-farm-recommend"),
            simpleItem("完结", "female-farm-finished"),
            simpleItem("连载", "female-farm-loading"),
        )

        val result = ModernExploreClassificationEngine.classify(kinds, "")
        val fantasy = result.nodes[1].children[0]

        assertEquals(listOf("推荐", "评分", "热门"), fantasy.children.map { it.title })
        fantasy.children.forEach { rank ->
            assertEquals(1, rank.children.size)
            assertEquals("分类", rank.children.single().title)
            assertEquals(
                listOf("全部", "完结", "连载"),
                rank.children.single().children.map { it.title }
            )
        }
        assertEquals(
            "https://example.com/list?gender=0&tags=7&creation_status=Finished&sort=1&page={{page}}",
            fantasy.children[1].children.single().children[1].url
        )
        assertSame(kinds[7], fantasy.children[1].children.single().children[1].originalKind)
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
