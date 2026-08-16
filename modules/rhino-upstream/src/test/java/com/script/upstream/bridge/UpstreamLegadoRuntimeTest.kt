package com.script.upstream.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpstreamLegadoRuntimeTest {

    @Test
    fun `modern syntax and bindings execute through upstream runtime`() {
        val result = UpstreamLegadoRuntime.eval(
            "const value = injected?.nested?.value ?? 0; value + 1;",
            mapOf("injected" to mapOf("nested" to mapOf("value" to 6)))
        )

        assertEquals(7.0, result)
    }

    @Test
    fun `nested native arrays and objects are normalized to JVM values`() {
        val result = UpstreamLegadoRuntime.eval(
            "const rows = [{title:'A', tags:['x','y'], meta:{count:2}}, 'tail']; rows;",
            emptyMap()
        )

        assertTrue(result is List<*>)
        val rows = result as List<*>
        val first = rows[0] as Map<*, *>
        assertEquals("A", first["title"])
        assertEquals(listOf("x", "y"), first["tags"])
        assertEquals(2.0, (first["meta"] as Map<*, *>)["count"])
        assertEquals("tail", rows[1])
    }

    @Test
    fun `undefined and missing values do not leak Rhino types`() {
        assertNull(UpstreamLegadoRuntime.eval("undefined", emptyMap()))
        assertNull(UpstreamLegadoRuntime.eval("({}).missing", emptyMap()))
    }

    @Test
    fun `this can read injected Legado style bindings`() {
        val result = UpstreamLegadoRuntime.eval(
            "this.java.name + ':' + source.name",
            mapOf(
                "java" to mapOf("name" to "java-binding"),
                "source" to mapOf("name" to "source-binding")
            )
        )

        assertEquals("java-binding:source-binding", result)
    }

    /**
     * Real-source fixture derived from nextTocUrl rules in the supplied corpus:
     * var url=[]; for (...) url.push(next); url;
     */
    @Test
    fun `real nextTocUrl array pattern returns JVM list`() {
        val result = UpstreamLegadoRuntime.eval(
            """
            var page = 4;
            var baseUrl = 'https://compat.test/topic/1/';
            var url = [];
            if (page) {
                for (i = 2; i <= page; i++) {
                    var next = baseUrl.replace(/\d+\/$/, i + '/');
                    url.push(next);
                }
            }
            url;
            """.trimIndent(),
            emptyMap()
        )

        assertEquals(
            listOf(
                "https://compat.test/topic/2/",
                "https://compat.test/topic/3/",
                "https://compat.test/topic/4/",
            ),
            result
        )
    }

    /** Real-source fixture derived from a wordCount rule using optional chaining. */
    @Test
    fun `real optional chaining match pattern works with present and null result`() {
        val script = """
            let size = result?.match(/towan\('\\d+'\)/)?.[0];
            size || '';
        """.trimIndent()

        assertEquals(
            "towan('123')",
            UpstreamLegadoRuntime.eval(script, mapOf("result" to "xx towan('123') yy"))
        )
        assertEquals("", UpstreamLegadoRuntime.eval(script, mapOf("result" to null)))
    }

    /** Real-source fixture reduced from the BigInt Long helper in the supplied Kuwo jsLib. */
    @Test
    fun `real BigInt helper pattern supports bit operations`() {
        val result = UpstreamLegadoRuntime.eval(
            """
            Long = t => {
                const r = BigInt(t);
                return {
                    toString: () => r.toString(),
                    or: t => Long(r | BigInt(t)),
                    and: t => Long(r & BigInt(t)),
                    shiftLeft: t => Long(r << BigInt(t)),
                    shiftRight: t => Long(r >> BigInt(t))
                };
            };
            Long(3).shiftLeft(4).or(2).and(63).toString();
            """.trimIndent(),
            emptyMap()
        )

        assertEquals("50", result)
    }

    /**
     * Real-source fixture reduced from the supplied Qimao dynamic Explore script. Actions are
     * retained as strings but deliberately not executed, matching how Explore descriptors are built.
     */
    @Test
    fun `real dynamic Explore descriptor pattern returns structured JVM objects`() {
        val result = UpstreamLegadoRuntime.eval(
            """
            var s = [];
            s.push({
                title: '☃关键词💭：书名/作者',
                type: 'text',
                style: {layout_flexGrow: 1, layout_flexBasisPercent: 0.6}
            });
            s.push({
                title: '🔍搜索',
                type: 'button',
                action: "java.searchBook(infoMap['☃关键词💭：书名/作者'] || '', source.getKey())",
                style: {layout_flexGrow: 1, layout_flexBasisPercent: -1}
            });
            s.push({
                title: '📁 选择分组',
                type: 'select',
                chars: ['📊 排行榜', '📚 分类', '🏷️ 标签'],
                default: '📊 排行榜'
            });
            s;
            """.trimIndent(),
            emptyMap()
        ) as List<*>

        assertEquals(3, result.size)
        assertEquals("text", (result[0] as Map<*, *>)["type"])
        assertEquals("button", (result[1] as Map<*, *>)["type"])
        val select = result[2] as Map<*, *>
        assertEquals("select", select["type"])
        assertEquals(listOf("📊 排行榜", "📚 分类", "🏷️ 标签"), select["chars"])
    }
}
