package com.script.upstream.bridge

import com.script.upstream.ScriptBindings
import com.script.upstream.rhino.RhinoScriptEngine

/** Thin JVM-value facade over the vendored LegadoTeam Rhino runtime. */
object UpstreamLegadoRuntime {
    fun eval(js: String, bindings: Map<String, Any?>): Any? {
        val scriptBindings = ScriptBindings()
        bindings.forEach { (key, value) -> scriptBindings[key] = value }
        val scope = RhinoScriptEngine.getRuntimeScope(scriptBindings)
        return RhinoScriptEngine.eval(js, scope)
    }
}
