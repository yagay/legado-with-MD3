package io.legado.app.model.jsEngine

import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.entities.BaseSource
import io.legado.app.constant.AppLog
import io.legado.app.help.CacheManager
import io.legado.app.help.http.CookieStore
import io.legado.app.help.source.getShareScope

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

/** Existing MD3/Mozilla Rhino behaviour, kept equivalent at the binding/scope level. */
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

/** Central per-source switch. Unconfigured sources always remain on LEGACY. */
object SourceJsEngineRouter {
    fun eval(
        source: BaseSource,
        jsStr: String,
        bindingsConfig: ScriptBindings.() -> Unit = {},
    ): Any? {
        val mode = SourceJsEngineModeStore.getMode(source.getKey())
        return try {
            when (mode) {
                SourceJsEngineMode.LEGACY -> LegacySourceJsEngine.eval(source, jsStr, bindingsConfig)
                SourceJsEngineMode.MODERN -> ModernSourceJsEngine.eval(source, jsStr, bindingsConfig)
            }
        } catch (error: Exception) {
            AppLog.put("JavaScript [${mode.name}] ${source.getTag()}\n$error", error)
            throw error
        }
    }
}
