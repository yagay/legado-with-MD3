package io.legado.app.model.jsEngine

import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.CacheManager
import io.legado.app.help.http.CookieStore
import io.legado.app.help.source.getShareScope
import kotlin.coroutines.CoroutineContext

/**
 * Common source-JavaScript entry point. Parser/UI code should keep calling BaseSource.evalJS();
 * BaseSource delegates here so the selected engine can change without leaking Rhino types upward.
 */
interface SourceJsEngine {
    fun eval(
        source: BaseSource,
        jsStr: String,
        bindingsConfig: ScriptBindings.() -> Unit = {},
    ): Any?
}

/** Existing MD3/Mozilla Rhino behaviour. Keep this path frozen for legacy sources. */
object LegacySourceJsEngine : SourceJsEngine {
    override fun eval(
        source: BaseSource,
        jsStr: String,
        bindingsConfig: ScriptBindings.() -> Unit,
    ): Any? {
        val bindings = buildScriptBindings { bindings ->
            bindings["java"] = source
            bindings["source"] = source
            bindings["baseUrl"] = source.getKey()
            bindings["cookie"] = CookieStore
            bindings["cache"] = CacheManager
            bindings.apply(bindingsConfig)
        }
        val sharedScope = source.getShareScope()
        val scope = if (sharedScope == null) {
            RhinoScriptEngine.getRuntimeScope(bindings)
        } else {
            bindings.apply {
                prototype = sharedScope
            }
        }
        return RhinoScriptEngine.eval(jsStr, scope)
    }
}

/**
 * Central per-source switch. Unconfigured sources always remain on LEGACY.
 *
 * The persisted MODERN name is retained for backwards compatibility, but it now means the
 * TeamLegado/upstream compatibility channel. All future upstream runtime work stays behind
 * [UpstreamLegadoAdapter].
 */
object SourceJsEngineRouter {
    fun eval(
        source: BaseSource,
        jsStr: String,
        bindingsConfig: ScriptBindings.() -> Unit = {},
        coroutineContext: CoroutineContext? = null,
    ): Any? {
        val mode = SourceJsEngineModeStore.getMode(source.getKey())
        return try {
            when (mode) {
                SourceJsEngineMode.LEGACY -> LegacySourceJsEngine.eval(source, jsStr, bindingsConfig)
                SourceJsEngineMode.MODERN -> UpstreamLegadoAdapter.eval(
                    source,
                    jsStr,
                    bindingsConfig,
                    coroutineContext,
                )
            }
        } catch (error: Exception) {
            AppLog.put("JavaScript [${mode.name}] ${source.getTag()}\n$error", error)
            throw error
        }
    }
}
