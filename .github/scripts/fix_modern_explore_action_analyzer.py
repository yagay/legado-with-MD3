from pathlib import Path

p = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
text = p.read_text()

anchor = 'import io.legado.app.model.analyzeRule.AnalyzeRule\n'
extra = 'import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext\n'
if extra not in text:
    if anchor not in text:
        raise SystemExit('AnalyzeRule import not found')
    text = text.replace(anchor, anchor + extra, 1)

old = '''                        val actionRule = if (action.startsWith("<js>") || action.startsWith("{{")) {\n                            action\n                        } else {\n                            "<js>$action</js>"\n                        }\n                        AnalyzeRule(source = source, preUpdateJs = true)\n                            .setContent(actionRule, source.getKey())\n                            .put("infoMap", infoMap)\n                            .getString(actionRule)\n'''
new = '''                        val actionJs = when {\n                            action.startsWith("<js>") && action.endsWith("</js>") ->\n                                action.removePrefix("<js>").removeSuffix("</js>")\n                            action.startsWith("{{") && action.endsWith("}}") ->\n                                action.removePrefix("{{").removeSuffix("}}")\n                            else -> action\n                        }\n                        AnalyzeRule(source = source, preUpdateJs = true)\n                            .setContent(actionJs, source.getKey())\n                            .setCoroutineContext(coroutineContext)\n                            .evalJS("var infoMap = result;\\n$actionJs", infoMap)\n'''
if old not in text:
    raise SystemExit('old analyzer block not found')
text = text.replace(old, new, 1)
p.write_text(text)
print('patched modern explore action analyzer compatibility')
