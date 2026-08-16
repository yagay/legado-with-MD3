package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ModernExploreControlExtractorTest {

    @Test
    fun `detects text and button only when button reads matching infoMap key`() {
        val text = ExploreKind(title = "keyword", type = ExploreKind.Type.text)
        val unrelated = ExploreKind(title = "分类", url = "https://example.com/list")
        val button = ExploreKind(
            title = "执行",
            type = ExploreKind.Type.button,
            action = "<js>var q = infoMap.get('keyword'); java.reUiView(false)</js>"
        )

        val control = ModernExploreControlExtractor.findSearchControl(
            listOf(text, unrelated, button)
        )!!

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
            action = "{{ let value = infoMap[\"参数A\"]; java.reUiView(false); }}"
        )

        val control = ModernExploreControlExtractor.findSearchControl(listOf(text, button))

        assertSame(text, control?.textKind)
        assertSame(button, control?.buttonKind)
    }

    @Test
    fun `does not infer semantics from adjacent text and button`() {
        val account = ExploreKind(title = "账号", type = ExploreKind.Type.text)
        val password = ExploreKind(title = "密码", type = ExploreKind.Type.text)
        val button = ExploreKind(
            title = "登录",
            type = ExploreKind.Type.button,
            action = "java.login()"
        )

        assertNull(
            ModernExploreControlExtractor.findSearchControl(
                listOf(account, password, button)
            )
        )
    }

    @Test
    fun `does not pair a button with a different infoMap key`() {
        val text = ExploreKind(title = "keyword", type = ExploreKind.Type.text)
        val button = ExploreKind(
            title = "动作",
            type = ExploreKind.Type.button,
            action = "var x = infoMap.get('anotherKey')"
        )

        assertNull(ModernExploreControlExtractor.findSearchControl(listOf(text, button)))
    }
}
