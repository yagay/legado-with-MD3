package io.legado.app.model.jsEngine

import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.rhino.modern.ModernRhinoRuntime
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.CacheManager
import io.legado.app.help.http.CookieStore
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper

/**
 * Bridge from Legado's existing source JS API to the isolated HtmlUnit Rhino runtime.
 * The staging ScriptBindings are only used to preserve all existing call-site lambdas;
 * values are unwrapped back to plain JVM objects before entering the modern runtime.
 */
object ModernSourceJsEngine : SourceJsEngine {

    override fun eval(
        source: BaseSource,
        jsStr: String,
        bindingsConfig: ScriptBindings.() -> Unit,
    ): Any? {
        val staging = buildScriptBindings { bindings ->
            bindings["java"] = source
            bindings["source"] = source
            bindings["baseUrl"] = source.getKey()
            bindings["cookie"] = CookieStore
            bindings["cache"] = CacheManager
            bindings.apply(bindingsConfig)
        }
        val rawBindings = staging.ids
            .mapNotNull { id ->
                val key = id as? String ?: return@mapNotNull null
                val value = staging.get(key, staging)
                if (value === ScriptableObject.NOT_FOUND) return@mapNotNull null
                key to unwrapLegacyValue(value)
            }
            .toMap()

        val jsLib = source.jsLib?.takeIf { it.isNotBlank() }
        val script = if (jsLib != null && !jsLib.trimStart().startsWith("{")) {
            "$jsLib\n$jsStr"
        } else {
            jsStr
        }
        return ModernRhinoRuntime.eval(script, rawBindings)
    }

    private fun unwrapLegacyValue(value: Any?): Any? {
        var result = value
        if (result is Wrapper) {
            result = result.unwrap()
        }
        return if (result is Undefined) null else result
    }
}
