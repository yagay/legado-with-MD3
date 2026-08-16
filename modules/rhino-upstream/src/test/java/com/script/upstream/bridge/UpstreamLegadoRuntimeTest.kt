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
}
