package io.legado.app.ui.main.explore

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Protects the Explore upstream-reuse boundary.
 *
 * The modern/waterfall UI may change presentation, but source protocol behavior must stay in
 * the shared HapeLee/TeamLegado-compatible Explore components instead of being reimplemented
 * in the enhance layer.
 */
class ExploreUpstreamReuseTest {

    @Test
    fun `explore actions reuse source js navigation instead of login-screen guard`() {
        val extensions = readProjectFile(SOURCE_LOGIN_JS_EXTENSIONS)
        val useCase = readProjectFile(EXPLORE_KIND_UI_USE_CASE)

        assertContains(extensions, "private val allowOpenLogin: Boolean = false")
        assertContains(extensions, "name == \"login\" && !allowOpenLogin")
        assertContains(extensions, "super.open(name, url, title, origin)")
        assertContains(useCase, "allowOpenLogin = true")
    }

    @Test
    fun `explore js callbacks refresh shared kinds state`() {
        val useCase = readProjectFile(EXPLORE_KIND_UI_USE_CASE)

        assertContains(useCase, "override fun upUiData(data: Map<String, Any?>?)")
        assertContains(useCase, "override fun reUiView(deltaUp: Boolean)")
        assertTrue(
            "Explore callbacks should refresh the shared ExploreKinds state",
            useCase.windowAfter("override fun upUiData", 180).contains("onRefreshKinds()") &&
                useCase.windowAfter("override fun reUiView", 180).contains("onRefreshKinds()")
        )
    }

    @Test
    fun `shared explore item state remains the InfoMap source of truth`() {
        val itemState = readProjectFile(EXPLORE_KIND_ITEM_STATE)

        val updateValue = itemState.windowAfter("fun updateValue", 360)
        val infoMapWrite = updateValue.indexOf("it[kind.title] = value")
        val callback = updateValue.indexOf("onValueChange?.invoke(value)")
        assertTrue("InfoMap must be updated for source behavior", infoMapWrite >= 0)
        assertTrue("Presentation callback must run only after the shared InfoMap write", callback > infoMapWrite)
    }

    @Test
    fun `shared compose renderer keeps every upstream ExploreKind type`() {
        val renderer = readProjectFile(EXPLORE_KIND_MULTI_TYPE_ITEM)

        listOf("url", "button", "text", "toggle", "select").forEach { type ->
            assertContains(renderer, "ExploreKind.Type.$type")
        }
    }

    private fun String.windowAfter(marker: String, length: Int): String {
        val start = indexOf(marker)
        if (start < 0) return ""
        return substring(start, (start + length).coerceAtMost(this.length))
    }

    private fun assertContains(source: String, expected: String) {
        assertTrue("Expected source to contain: $expected", source.contains(expected))
    }

    private fun readProjectFile(pathInApp: String): String =
        sequenceOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull(File::isFile)
            ?.readText()
            .orEmpty()

    private companion object {
        const val SOURCE_LOGIN_JS_EXTENSIONS =
            "src/main/java/io/legado/app/ui/login/SourceLoginJsExtensions.kt"
        const val EXPLORE_KIND_UI_USE_CASE =
            "src/main/java/io/legado/app/domain/usecase/ExploreKindUiUseCase.kt"
        const val EXPLORE_KIND_ITEM_STATE =
            "src/main/java/io/legado/app/ui/widget/components/explore/ExploreKindItemState.kt"
        const val EXPLORE_KIND_MULTI_TYPE_ITEM =
            "src/main/java/io/legado/app/ui/widget/components/explore/ExploreKindMultiTypeItem.kt"
    }
}
