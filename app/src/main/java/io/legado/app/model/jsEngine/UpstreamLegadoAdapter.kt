package io.legado.app.model.jsEngine

import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.upstream.bridge.UpstreamLegadoRuntime
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.CacheManager
import io.legado.app.help.http.CookieStore
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper

/**
 * Stable boundary for the TeamLegado-compatible JavaScript runtime.
 *
 * Parser/business code depends only on [SourceJsEngineRouter]. HtmlUnit Rhino types are confined
 * to the isolated rhino-upstream module, while existing call sites can keep using the legacy
 * ScriptBindings lambda until that public boundary is neutralized in a later cleanup.
 */
object UpstreamLegadoAdapter : SourceJsEngine {

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

        // Keep current jsLib behavior for this cut-over. SharedJsScope is migrated separately so
        // the core runtime switch can be compiled and verified independently.
        val jsLib = source.jsLib?.takeIf { it.isNotBlank() }
        val script = if (jsLib != null && !jsLib.trimStart().startsWith("{")) {
            "$jsLib\n$jsStr"
        } else {
            jsStr
        }
        return UpstreamLegadoRuntime.eval(script, rawBindings)
    }

    private fun unwrapLegacyValue(value: Any?): Any? {
        var result = value
        if (result is Wrapper) {
            result = result.unwrap()
        }
        return if (result is Undefined) null else result
    }
}
