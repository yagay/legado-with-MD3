package io.legado.app.model.jsEngine

import com.script.ScriptBindings
import io.legado.app.data.entities.BaseSource

/**
 * Stable boundary for the TeamLegado-compatible JavaScript runtime.
 *
 * Parser/business code must depend only on [SourceJsEngineRouter]. The implementation behind
 * this adapter can track LegadoTeam/legado without leaking HtmlUnit Rhino types into callers.
 *
 * During the migration this delegates to the existing modern bridge. The next migration steps
 * replace that delegate with the upstream RhinoContext/RhinoScriptEngine/SharedJsScope stack.
 */
object UpstreamLegadoAdapter : SourceJsEngine {

    override fun eval(
        source: BaseSource,
        jsStr: String,
        bindingsConfig: ScriptBindings.() -> Unit,
    ): Any? {
        return ModernSourceJsEngine.eval(source, jsStr, bindingsConfig)
    }
}
