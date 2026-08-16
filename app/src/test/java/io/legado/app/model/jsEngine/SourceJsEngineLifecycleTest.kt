package io.legado.app.model.jsEngine

import io.legado.app.data.entities.BookSource
import io.legado.app.model.SharedJsScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = JsEngineCompatibilityTestApplication::class)
class SourceJsEngineLifecycleTest {

    private val sourceKeys = mutableListOf<String>()

    @After
    fun tearDown() {
        sourceKeys.forEach(SourceJsEngineModeStore::clearMode)
    }

    @Test
    fun `legacy refresh entry point clears upstream jsLib scope and globals`() {
        val source = modernSource("refresh").apply {
            jsLib = "function compatLibraryLoaded() { return true; }"
        }

        assertEquals(1.0, incrementGlobal(source))
        SharedJsScope.remove(source.jsLib)
        assertEquals(1.0, incrementGlobal(source))
    }

    @Test
    fun `source lifecycle cleanup clears mode and upstream shared global state`() {
        val source = modernSource("delete").apply {
            jsLib = "function compatLibraryLoaded() { return true; }"
        }

        assertEquals(1.0, incrementGlobal(source))
        SourceJsEngineLifecycle.clearSource(BookSource::class.java, source.getKey())
        assertEquals(SourceJsEngineMode.LEGACY, SourceJsEngineModeStore.getMode(source.getKey()))

        SourceJsEngineModeStore.setMode(source.getKey(), SourceJsEngineMode.MODERN)
        assertEquals(1.0, incrementGlobal(source))
    }

    @Test
    fun `switching Rhino mode resets upstream shared global state`() {
        val source = modernSource("mode-switch").apply {
            jsLib = "function compatLibraryLoaded() { return true; }"
        }

        assertEquals(1.0, incrementGlobal(source))
        SourceJsEngineModeStore.setMode(source.getKey(), SourceJsEngineMode.LEGACY)
        SourceJsEngineModeStore.setMode(source.getKey(), SourceJsEngineMode.MODERN)
        assertEquals(1.0, incrementGlobal(source))
    }

    private fun incrementGlobal(source: BookSource): Any? = source.evalJS(
        "globalThis.__lifecycleCount = (globalThis.__lifecycleCount || 0) + 1; globalThis.__lifecycleCount"
    )

    private fun modernSource(name: String): BookSource {
        val key = "https://compat.test/lifecycle-$name"
        sourceKeys += key
        SourceJsEngineModeStore.setMode(key, SourceJsEngineMode.MODERN)
        return BookSource(bookSourceUrl = key, bookSourceName = "compat-lifecycle-$name")
    }
}
