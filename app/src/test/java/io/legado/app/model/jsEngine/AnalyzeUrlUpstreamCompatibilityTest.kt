package io.legado.app.model.jsEngine

import io.legado.app.data.entities.BookSource
import io.legado.app.model.analyzeRule.AnalyzeUrl
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = JsEngineCompatibilityTestApplication::class)
class AnalyzeUrlUpstreamCompatibilityTest {

    private val sourceKeys = mutableListOf<String>()

    @After
    fun tearDown() {
        sourceKeys.forEach(SourceJsEngineModeStore::clearMode)
    }

    @Test
    fun `AnalyzeUrl JavaScript uses upstream bindings CryptoJS and normalized results`() {
        val source = modernSource("bindings")
        val analyzeUrl = AnalyzeUrl(
            mUrl = "https://compat.test/path",
            page = 3,
            source = source,
            headerMapF = emptyMap(),
            extraParams = mapOf("page" to "7"),
        )

        val result = analyzeUrl.evalJS(
            "[page, source.getKey(), sourceApi.getKey(), CryptoJS.MD5('abc').toString()]"
        ) as List<*>

        assertEquals(7, (result[0] as Number).toInt())
        assertEquals(source.getKey(), result[1])
        assertEquals(source.getKey(), result[2])
        assertEquals("900150983cd24fb0d6963f7d28e17f72", result[3])
    }

    @Test
    fun `AnalyzeUrl propagates cancelled coroutine context into upstream Rhino`() {
        val source = modernSource("cancelled-context")
        val job = Job()
        val analyzeUrl = AnalyzeUrl(
            mUrl = "https://compat.test/path",
            source = source,
            coroutineContext = job,
            headerMapF = emptyMap(),
        )
        job.cancel()

        assertThrows(CancellationException::class.java) {
            analyzeUrl.evalJS("1 + 2")
        }
    }

    private fun modernSource(name: String): BookSource {
        val key = "https://compat.test/analyze-url-$name"
        sourceKeys += key
        SourceJsEngineModeStore.setMode(key, SourceJsEngineMode.MODERN)
        return BookSource(bookSourceUrl = key, bookSourceName = "compat-analyze-url-$name")
    }
}
