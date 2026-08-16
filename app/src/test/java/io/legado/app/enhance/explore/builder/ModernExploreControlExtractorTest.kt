package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ModernExploreControlExtractorTest {

    @Test
    fun `detects text and button when button reads matching infoMap key`() {
        val text = ExploreKind(title = "keyword", type = ExploreKind.Type.text)
        val unrelated = ExploreKind(title = "分类", url = "https://example.com/list")
        val button = ExploreKind(
            title = "执行",
            type = ExploreKind.Type.button,
            action = "<js>var q = infoMap.get('keyword'); java.refreshExplore()</js>"
        )

        val control = ModernExploreControlExtractor.findSearchControl(listOf(text, unrelated, button))!!

        assertSame(text, control.textKind)
        assertSame(button, control.buttonKind)
        assertEquals(setOf(0, 2), control.hiddenSourceIndexes)
    }

    @Test
    fun `supports bracket infoMap reads without relying on display words`() {
        val text = ExploreKind(title = "参数A", type = ExploreKind.Type.text)
        val button = ExploreKind(
            title = "动作B",
            type = ExploreKind.Type.button,
            action = "{{ let value = infoMap[\"参数A\"]; java.refreshExplore(); }}"
        )

        val control = ModernExploreControlExtractor.findSearchControl(listOf(text, button))

        assertSame(text, control?.textKind)
        assertSame(button, control?.buttonKind)
    }

    @Test
    fun `single text plus refresh button is treated as embedded search`() {
        val text = ExploreKind(title = "任意参数", type = ExploreKind.Type.text)
        val button = ExploreKind(
            title = "任意动作",
            type = ExploreKind.Type.button,
            action = "java.refreshExplore()"
        )

        val control = ModernExploreControlExtractor.findSearchControl(listOf(text, button))

        assertSame(text, control?.textKind)
        assertSame(button, control?.buttonKind)
        assertEquals(setOf(0, 1), control?.hiddenSourceIndexes)
    }

    @Test
    fun `single text plus legacy refresh callback is supported`() {
        val text = ExploreKind(title = "值", type = ExploreKind.Type.text)
        val button = ExploreKind(
            title = "动作",
            type = ExploreKind.Type.button,
            action = "<js>java.reLoginView(false)</js>"
        )

        val control = ModernExploreControlExtractor.findSearchControl(listOf(text, button))

        assertSame(text, control?.textKind)
        assertSame(button, control?.buttonKind)
    }

    @Test
    fun `single text followed by wrapped action button is supported`() {
        val text = ExploreKind(title = "任意输入", type = ExploreKind.Type.text)
        val button = ExploreKind(
            title = "任意按钮",
            type = ExploreKind.Type.button,
            action = "doSearch()"
        )

        val control = ModernExploreControlExtractor.findSearchControl(listOf(text, button))

        assertSame(text, control?.textKind)
        assertSame(button, control?.buttonKind)
    }

    @Test
    fun `multiple text form is never inferred from refresh button alone`() {
        val account = ExploreKind(title = "账号", type = ExploreKind.Type.text)
        val password = ExploreKind(title = "密码", type = ExploreKind.Type.text)
        val button = ExploreKind(
            title = "提交",
            type = ExploreKind.Type.button,
            action = "java.refreshExplore()"
        )

        assertNull(ModernExploreControlExtractor.findSearchControl(listOf(account, password, button)))
    }

    @Test
    fun `explicit login action is excluded from single text fallback`() {
        val text = ExploreKind(title = "值", type = ExploreKind.Type.text)
        val button = ExploreKind(
            title = "动作",
            type = ExploreKind.Type.button,
            action = "java.login()"
        )

        assertNull(ModernExploreControlExtractor.findSearchControl(listOf(text, button)))
    }

    @Test
    fun `does not pair a button with a different infoMap key when not adjacent fallback`() {
        val text = ExploreKind(title = "keyword", type = ExploreKind.Type.text)
        val toggle = ExploreKind(title = "开关", type = ExploreKind.Type.toggle)
        val button = ExploreKind(
            title = "动作",
            type = ExploreKind.Type.button,
            action = "var x = infoMap.get('anotherKey')"
        )

        assertNull(ModernExploreControlExtractor.findSearchControl(listOf(text, toggle, button)))
    }
}
