package com.script.upstream.bridge

import com.script.upstream.ScriptBindings
import com.script.upstream.rhino.RhinoScriptEngine
import org.htmlunit.corejs.javascript.ConsString
import org.htmlunit.corejs.javascript.NativeArray
import org.htmlunit.corejs.javascript.NativeObject
import org.htmlunit.corejs.javascript.Scriptable
import org.htmlunit.corejs.javascript.Undefined
import org.htmlunit.corejs.javascript.Wrapper

/**
 * Thin JVM-value facade over the vendored LegadoTeam Rhino runtime.
 * HtmlUnit Rhino values are normalized here so parser/business code never depends on them.
 */
object UpstreamLegadoRuntime {

    fun eval(js: String, bindings: Map<String, Any?>): Any? {
        val scriptBindings = ScriptBindings()
        bindings.forEach { (key, value) -> scriptBindings[key] = value }
        val scope = RhinoScriptEngine.getRuntimeScope(scriptBindings)
        return toJvmValue(RhinoScriptEngine.eval(js, scope))
    }

    fun toJvmValue(value: Any?): Any? {
        var result = value
        if (result is Wrapper) result = result.unwrap()
        return when (result) {
            null, is Undefined -> null
            Scriptable.NOT_FOUND -> null
            is ConsString -> result.toString()
            is NativeArray -> {
                val values = ArrayList<Any?>(result.length.toInt())
                for (index in 0 until result.length.toInt()) {
                    val item = result.get(index, result)
                    values.add(toJvmValue(item))
                }
                values
            }
            is NativeObject -> {
                result.ids.mapNotNull { id ->
                    val key = id?.toString() ?: return@mapNotNull null
                    val item = result.get(key, result)
                    if (item === Scriptable.NOT_FOUND) return@mapNotNull null
                    key to toJvmValue(item)
                }.toMap()
            }
            is Array<*> -> result.map(::toJvmValue)
            is Iterable<*> -> result.map(::toJvmValue)
            else -> result
        }
    }
}
