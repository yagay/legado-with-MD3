package io.legado.app.model.jsEngine

import android.app.Application
import com.google.gson.JsonParser
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

class JsEngineCompatibilityTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        injectAsAppCtx()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = JsEngineCompatibilityTestApplication::class)
class SourceJsEngineCompatibilityTest {

    private val sourceKeys = mutableListOf<String>()

    @After
    fun tearDown() {
        sourceKeys.forEach(SourceJsEngineModeStore::clearMode)
    }

    @Test
    fun `unmarked sources remain on legacy Rhino`() {
        val source = source("legacy")

        assertEquals(SourceJsEngineMode.LEGACY, SourceJsEngineModeStore.getMode(source.getKey()))
        assertEquals(3.0, source.evalJS("1 + 2"))
    }

    @Test
    fun `portable source metadata round trips both Rhino modes`() {
        val modern = modernSource("portable-modern")
        val legacy = source("portable-legacy")

        val json = BookSourceJsEngineMetadata.toJson(listOf(modern, legacy))
        val items = JsonParser.parseString(json).asJsonArray

        assertEquals(SourceJsEngineMode.MODERN, BookSourceJsEngineMetadata.readMode(items[0]))
        assertEquals(SourceJsEngineMode.LEGACY, BookSourceJsEngineMetadata.readMode(items[1]))
        assertEquals(modern.getKey(), BookSourceJsEngineMetadata.readSourceKey(items[0]))
        assertEquals(legacy.getKey(), BookSourceJsEngineMetadata.readSourceKey(items[1]))
    }

    @Test
    fun `portable metadata stays compatible with normal BookSource Gson parsing`() {
        val original = source("portable-gson")
        val json = BookSourceJsEngineMetadata.toJson(original, SourceJsEngineMode.MODERN)

        val restored = GSON.fromJson(json, BookSource::class.java)

        assertEquals(original.bookSourceUrl, restored.bookSourceUrl)
        assertEquals(original.bookSourceName, restored.bookSourceName)
        assertEquals(SourceJsEngineMode.MODERN, BookSourceJsEngineMetadata.readMode(json))
    }

    @Test
    fun `malformed portable metadata is ignored safely`() {
        assertNull(
            BookSourceJsEngineMetadata.readMode(
                """{"bookSourceUrl":"https://compat.test/bad","_legadoEnhance":"bad"}"""
            )
        )
        assertNull(
            BookSourceJsEngineMetadata.readMode(
                """{"bookSourceUrl":"https://compat.test/bad","_legadoEnhance":{"jsEngine":"UNKNOWN"}}"""
            )
        )
    }

    @Test
    fun `modern source exposes Legado bindings and normalizes arrays`() {
        val source = modernSource("bindings")

        val result = source.evalJS(
            "[java.getKey(), source.getKey(), sourceApi.getKey(), {ok:true, values:[1,2]}]"
        ) as List<*>

        assertEquals(source.getKey(), result[0])
        assertEquals(source.getKey(), result[1])
        assertEquals(source.getKey(), result[2])
        val data = result[3] as Map<*, *>
        assertEquals(true, data["ok"])
        val values = data["values"] as List<*>
        assertEquals(listOf(1, 2), values.map { (it as Number).toInt() })
    }

    @Test
    fun `modern source loads plain jsLib through shared scope`() {
        val source = modernSource("jslib").apply {
            jsLib = "function compatFromLib(v) { return 'lib:' + v; }"
        }

        assertEquals("lib:ok", source.evalJS("compatFromLib('ok')"))
    }

    /**
     * Real-source fixture reduced from supplied jsLib code that uses:
     * const { java, source } = this
     * inside a library function. This specifically verifies TeamLegado's dynamic top-level this
     * semantics after the function has been defined in SharedJsScope and invoked from a child scope.
     */
    @Test
    fun `real jsLib function receives runtime java and source through this`() {
        val source = modernSource("jslib-runtime-this").apply {
            jsLib = """
                function compatRuntimeThis() {
                    const { java, source } = this;
                    return java.getKey() + '|' + source.getKey();
                }
            """.trimIndent()
        }

        assertEquals(
            "${source.getKey()}|${source.getKey()}",
            source.evalJS("compatRuntimeThis()")
        )
    }

    @Test
    fun `modern source keeps source global state between evaluations`() {
        val source = modernSource("global").apply {
            jsLib = "function compatLibraryLoaded() { return true; }"
        }

        assertEquals(
            1.0,
            source.evalJS("globalThis.__compatCount = (globalThis.__compatCount || 0) + 1; globalThis.__compatCount")
        )
        assertEquals(
            2.0,
            source.evalJS("globalThis.__compatCount = (globalThis.__compatCount || 0) + 1; globalThis.__compatCount")
        )
    }

    @Test
    fun `modern source has TeamLegado CryptoJS scope without jsLib`() {
        val source = modernSource("crypto")

        assertEquals(
            "900150983cd24fb0d6963f7d28e17f72",
            source.evalJS("CryptoJS.MD5('abc').toString()")
        )
    }

    @Test
    fun `modern failure is propagated without changing engine mode`() {
        val source = modernSource("error")

        val error = assertThrows(Exception::class.java) {
            source.evalJS("throw new Error('compat-modern-failure')")
        }

        assertTrue(error.message.orEmpty().contains("compat-modern-failure"))
        assertEquals(SourceJsEngineMode.MODERN, SourceJsEngineModeStore.getMode(source.getKey()))
    }

    private fun source(name: String): BookSource {
        val key = "https://compat.test/$name"
        sourceKeys += key
        SourceJsEngineModeStore.clearMode(key)
        return BookSource(bookSourceUrl = key, bookSourceName = "compat-$name")
    }

    private fun modernSource(name: String): BookSource {
        return source(name).also {
            SourceJsEngineModeStore.setMode(it.getKey(), SourceJsEngineMode.MODERN)
        }
    }
}
