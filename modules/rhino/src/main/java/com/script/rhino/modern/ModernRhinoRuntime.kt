package com.script.rhino.modern

import org.htmlunit.corejs.javascript.ConsString
import org.htmlunit.corejs.javascript.Context
import org.htmlunit.corejs.javascript.NativeArray
import org.htmlunit.corejs.javascript.NativeObject
import org.htmlunit.corejs.javascript.Scriptable
import org.htmlunit.corejs.javascript.ScriptableObject
import org.htmlunit.corejs.javascript.Undefined
import org.htmlunit.corejs.javascript.Wrapper

/**
 * Isolated execution facade for Legado's current HtmlUnit Rhino fork.
 *
 * Keep HtmlUnit Rhino types inside this module. Values returned to the app/parser
 * layer are normalized to ordinary JVM types so AnalyzeRule does not need to know
 * which Rhino implementation executed the source.
 */
object ModernRhinoRuntime {

    fun eval(
        js: String,
        bindings: Map<String, Any?>,
    ): Any? {
        val cx = Context.enter()
        return try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            bindings.forEach { (key, value) ->
                ScriptableObject.putProperty(
                    scope,
                    key,
                    Context.javaToJS(value, scope),
                )
            }
            val result = cx.evaluateString(scope, js, "<SourceJs>", 1, null)
            normalize(result)
        } finally {
            Context.exit()
        }
    }

    /**
     * Convert modern Rhino values into the same plain JVM shapes expected by the
     * existing Legado parser. In particular, JS arrays must become List values;
     * otherwise AnalyzeRule.getElements() can fail with a ClassCastException.
     */
    private fun normalize(value: Any?): Any? {
        var result = value
        if (result is Wrapper) {
            result = result.unwrap()
        }
        if (result === Undefined.instance || result === Scriptable.NOT_FOUND) {
            return null
        }
        return when (result) {
            is ConsString -> result.toString()
            is NativeArray -> {
                val size = result.length.toInt()
                buildList(size) {
                    for (index in 0 until size) {
                        val item = result.get(index, result)
                        if (item !== Scriptable.NOT_FOUND) {
                            add(normalize(item))
                        }
                    }
                }
            }
            is NativeObject -> buildMap<String, Any?> {
                result.ids.forEach { id ->
                    val key = id.toString()
                    val item = result.get(key, result)
                    if (item !== Scriptable.NOT_FOUND) {
                        put(key, normalize(item))
                    }
                }
            }
            is Array<*> -> result.map(::normalize)
            is Iterable<*> -> result.map(::normalize)
            is Map<*, *> -> result.entries.associate { (key, item) ->
                key.toString() to normalize(item)
            }
            else -> result
        }
    }
}
