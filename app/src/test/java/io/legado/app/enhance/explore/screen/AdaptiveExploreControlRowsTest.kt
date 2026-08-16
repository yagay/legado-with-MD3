package io.legado.app.enhance.explore.screen

import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveExploreControlRowsTest {

    @Test
    fun `longer labels receive larger width weights`() {
        val short = ExploreKind(title = "短", type = ExploreKind.Type.button)
        val long = ExploreKind(title = "这是一个更长的选项", type = ExploreKind.Type.button)

        assertTrue(controlWidthUnits(long) > controlWidthUnits(short))
    }

    @Test
    fun `text controls keep a larger minimum width`() {
        val text = ExploreKind(title = "A", type = ExploreKind.Type.text)
        val button = ExploreKind(title = "A", type = ExploreKind.Type.button)

        assertTrue(controlWidthUnits(text) > controlWidthUnits(button))
    }

    @Test
    fun `source action labels scale proportionally in the shared row`() {
        val login = ExploreKind(title = "登录", type = ExploreKind.Type.button)
        val updateSource = ExploreKind(title = "更新书源", type = ExploreKind.Type.button)
        val refreshExplore = ExploreKind(title = "刷新发现页", type = ExploreKind.Type.button)

        val loginWeight = controlWidthUnits(login)
        val updateWeight = controlWidthUnits(updateSource)
        val refreshWeight = controlWidthUnits(refreshExplore)

        assertTrue(updateWeight > loginWeight)
        assertTrue(refreshWeight > updateWeight)
    }
}
