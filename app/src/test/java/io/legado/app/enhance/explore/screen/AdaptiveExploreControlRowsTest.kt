package io.legado.app.enhance.explore.screen

import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveExploreControlRowsTest {

    @Test
    fun `short controls share a row`() {
        val controls = listOf(
            ExploreKind(title = "A", type = ExploreKind.Type.button),
            ExploreKind(title = "BB", type = ExploreKind.Type.button),
            ExploreKind(title = "CCC", type = ExploreKind.Type.toggle),
        )

        val rows = packAdaptiveControlRows(controls)

        assertEquals(1, rows.size)
        assertEquals(controls, rows.single())
    }

    @Test
    fun `longer labels receive larger width weights`() {
        val short = ExploreKind(title = "短", type = ExploreKind.Type.button)
        val long = ExploreKind(title = "这是一个更长的选项", type = ExploreKind.Type.button)

        assertTrue(controlWidthUnits(long) > controlWidthUnits(short))
    }

    @Test
    fun `text controls can share a row when width allows`() {
        val controls = listOf(
            ExploreKind(title = "输入", type = ExploreKind.Type.text),
            ExploreKind(title = "确定", type = ExploreKind.Type.button),
        )

        val rows = packAdaptiveControlRows(controls)

        assertEquals(1, rows.size)
        assertEquals(controls, rows.single())
    }

    @Test
    fun `text controls keep a larger minimum width`() {
        val text = ExploreKind(title = "A", type = ExploreKind.Type.text)
        val button = ExploreKind(title = "A", type = ExploreKind.Type.button)

        assertTrue(controlWidthUnits(text) > controlWidthUnits(button))
    }

    @Test
    fun `rows split when text width budget is exceeded`() {
        val controls = listOf(
            ExploreKind(title = "非常长的第一个控制项", type = ExploreKind.Type.button),
            ExploreKind(title = "非常长的第二个控制项", type = ExploreKind.Type.button),
        )

        val rows = packAdaptiveControlRows(controls, maxUnitsPerRow = 18f)

        assertEquals(2, rows.size)
    }
}
