package io.legado.app.model.jsEngine

import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.entities.BaseSource
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

/** Existing MD3/Mozilla Rhino behaviour, kept byte-for-byte equivalent at the binding/scope level. */
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
 * Central switch. MODERN is intentionally unavailable until the isolated HtmlUnit-Rhino runtime
 * is installed; selecting it fails loudly instead of silently running the wrong engine.
 */
object SourceJsEngineRouter {
    @Volatile
    private var modernEngine: SourceJsEngine? = null

    fun installModernEngine(engine: SourceJsEngine) {
        modernEngine = engine
    }

    fun eval(
        source: BaseSource,
        jsStr: String,
        bindingsConfig: ScriptBindings.() -> Unit = {},
    ): Any? {
        return when (SourceJsEngineModeStore.getMode(source.getKey())) {
            SourceJsEngineMode.LEGACY -> LegacySourceJsEngine.eval(source, jsStr, bindingsConfig)
            SourceJsEngineMode.MODERN -> {
                val engine = modernEngine
                    ?: error("Modern source JavaScript engine is not installed")
                engine.eval(source, jsStr, bindingsConfig)
            }
        }
    }
}
