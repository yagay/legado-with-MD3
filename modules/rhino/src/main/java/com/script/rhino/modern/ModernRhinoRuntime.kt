package com.script.rhino.modern

import org.htmlunit.corejs.javascript.ConsString
import org.htmlunit.corejs.javascript.Context
import org.htmlunit.corejs.javascript.ScriptableObject
import org.htmlunit.corejs.javascript.Undefined
import org.htmlunit.corejs.javascript.Wrapper

/**
 * Isolated execution facade for Legado's current HtmlUnit Rhino fork.
 * It intentionally exposes only plain JVM values to the app layer so no
 * org.htmlunit.corejs types leak into parsers/UI.
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
            unwrap(result)
        } finally {
            Context.exit()
        }
    }

    private fun unwrap(value: Any?): Any? {
        var result = value
        if (result is Wrapper) {
            result = result.unwrap()
        }
        if (result is ConsString) {
            result = result.toString()
        }
        return if (result is Undefined) null else result
    }
}
