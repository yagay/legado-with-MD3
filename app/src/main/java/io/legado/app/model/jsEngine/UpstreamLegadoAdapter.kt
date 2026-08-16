package io.legado.app.model.jsEngine

import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.upstream.ScriptBindings as UpstreamScriptBindings
import com.script.upstream.bridge.UpstreamLegadoRuntime
import com.script.upstream.rhino.RhinoScriptEngine as UpstreamRhinoScriptEngine
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.CacheManager
import io.legado.app.help.http.CookieStore
import io.legado.app.utils.MD5Utils
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper

/**
 * Stable boundary for the TeamLegado-compatible JavaScript runtime.
 *
 * Parser/business code keeps the legacy binding lambda contract while MODERN sources are executed
 * with the isolated TeamLegado runtime, SharedJsScope and source-level shared global semantics.
 * HtmlUnit Rhino-specific return values are normalized before they leave this adapter.
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
            bindings["sourceApi"] = source
            bindings["baseUrl"] = source.getKey()
            bindings["cookie"] = CookieStore
            bindings["cache"] = CacheManager
            bindings.apply(bindingsConfig)
        }

        val bindings = UpstreamScriptBindings()
        staging.ids.forEach { id ->
            val key = id as? String ?: return@forEach
            val value = staging.get(key, staging)
            if (value !== ScriptableObject.NOT_FOUND) {
                bindings[key] = unwrapLegacyValue(value)
            }
        }

        val jsLib = source.jsLib?.takeIf { it.isNotBlank() }
        val sharedScope = if (jsLib != null) {
            UpstreamSharedJsScope.getScope(jsLib, null)
        } else {
            UpstreamSharedJsScope.getCryptoScope(source, null)
        }

        val scope = if (sharedScope == null) {
            UpstreamRhinoScriptEngine.getRuntimeScope(bindings)
        } else {
            val sharedGlobalState = jsLib?.let { library ->
                val key = "${MD5Utils.md5Encode(library)}:${source.javaClass.name}:${MD5Utils.md5Encode(source.getKey())}"
                UpstreamScriptBindings.getSharedGlobalStateHandle(key)
            }
            bindings.apply {
                chainTo(sharedScope, sharedGlobalState)
            }
        }

        return UpstreamLegadoRuntime.toJvmValue(
            UpstreamRhinoScriptEngine.eval(jsStr, scope)
        )
    }

    private fun unwrapLegacyValue(value: Any?): Any? {
        var result = value
        if (result is Wrapper) {
            result = result.unwrap()
        }
        return if (result is Undefined) null else result
    }
}
