from pathlib import Path

p = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
text = p.read_text()
old = '''                val key = control.kind.title
                val infoMap = getExploreInfoMap(defaultSourceUrl)
                if (key.isNotBlank()) {
                    infoMap[key] = value
                    infoMap.saveNow()
                }
                control.kind.action
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { action ->
                        val actionJs = when {
                            action.startsWith("<js>") && action.endsWith("</js>") ->
                                action.removePrefix("<js>").removeSuffix("</js>")
                            action.startsWith("{{") && action.endsWith("}}") ->
                                action.removePrefix("{{").removeSuffix("}}")
                            else -> action
                        }
                        // Match legado:leg applyDiscoverSelectValue(): execute in the
                        // BookSource JS scope so jsLib helpers (setVariable/BaseUrl/etc.)
                        // and injected infoMap/java are the same ones used by discovery.
                        runScriptWithContext {
                            source.evalJS(actionJs) {
                                put("java", SourceLoginJsExtensions(null, source))
                                put("infoMap", infoMap)
                            }
                        }
                    }
'''
new = '''                val key = control.kind.title
                val infoMap = getExploreInfoMap(defaultSourceUrl)
                val action = control.kind.action?.trim()?.takeIf { it.isNotBlank() }
                val actionJs = action?.let {
                    when {
                        it.startsWith("<js>") && it.endsWith("</js>") ->
                            it.removePrefix("<js>").removeSuffix("</js>")
                        it.startsWith("{{") && it.endsWith("}}") ->
                            it.removePrefix("{{").removeSuffix("}}")
                        else -> it
                    }
                }
                // A number of dynamic sources use a display label that differs from the
                // source-variable key, e.g. `平台` -> `发现页来源`.  The select action then
                // persists that hidden key through setVariable().  Keep both keys in the
                // discovery InfoMap so either style of action sees the newly selected value.
                val variableKey = actionJs?.let(::extractSelectVariableKey)
                sequenceOf(key, control.title, control.kind.viewName, variableKey)
                    .filterNotNull()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .forEach { infoMap[it] = value }
                infoMap.saveNow()

                if (actionJs != null) {
                    runScriptWithContext {
                        // When the action exposes a literal setVariable key, persist it first.
                        // This mirrors the source's own helper and prevents a stale default
                        // (commonly 番茄) from being read while the discovery page is rebuilt.
                        if (!variableKey.isNullOrBlank()) {
                            val escapedKey = variableKey
                                .replace("\\\\", "\\\\\\\\")
                                .replace("'", "\\\\'")
                            val escapedValue = value
                                .replace("\\\\", "\\\\\\\\")
                                .replace("'", "\\\\'")
                            source.evalJS("setVariable('$escapedKey','$escapedValue',false)") {
                                put("java", SourceLoginJsExtensions(null, source))
                                put("infoMap", infoMap)
                            }
                        }
                        // Match legado:leg applyDiscoverSelectValue(): execute in the
                        // BookSource JS scope so jsLib helpers (setVariable/BaseUrl/etc.)
                        // and injected infoMap/java are the same ones used by discovery.
                        source.evalJS(actionJs) {
                            put("java", SourceLoginJsExtensions(null, source))
                            put("infoMap", infoMap)
                        }
                    }
                }
'''
if old not in text:
    raise SystemExit('select action block not found')
text = text.replace(old, new, 1)
marker = '''    private fun rebuildSelectors(suite: DiscoverySuite, defaultSourceUrl: String) {'''
helper = '''    private fun extractSelectVariableKey(actionJs: String): String? {\n        val patterns = listOf(\n            Regex("""setVariable\\s*\\(\\s*['\\\"]([^'\\\"]+)['\\\"]"""),\n            Regex("""source\\.setVariable\\s*\\(\\s*['\\\"]([^'\\\"]+)['\\\"]"""),\n        )\n        return patterns.firstNotNullOfOrNull { pattern ->\n            pattern.find(actionJs)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty)\n        }\n    }\n\n'''
if marker not in text:
    raise SystemExit('rebuild marker not found')
text = text.replace(marker, helper + marker, 1)
p.write_text(text)
print('fixed dynamic select action persistence')
