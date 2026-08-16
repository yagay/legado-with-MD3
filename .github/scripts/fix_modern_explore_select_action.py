from pathlib import Path

p = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
text = p.read_text()

old_import = 'import io.legado.app.help.source.getExploreInfoMap\n'
new_import = old_import + 'import io.legado.app.model.analyzeRule.AnalyzeRule\n'
if 'import io.legado.app.model.analyzeRule.AnalyzeRule\n' not in text:
    if old_import not in text:
        raise SystemExit('import anchor not found')
    text = text.replace(old_import, new_import, 1)

old = '''                val key = control.kind.title\n                if (key.isNotBlank()) {\n                    getExploreInfoMap(defaultSourceUrl).apply {\n                        this[key] = value\n                        saveNow()\n                    }\n                }\n                source.clearExploreKindsCache()\n                allSourceRawKinds = source.exploreKinds()\n'''
new = '''                val key = control.kind.title\n                val infoMap = getExploreInfoMap(defaultSourceUrl)\n                if (key.isNotBlank()) {\n                    infoMap[key] = value\n                    infoMap.saveNow()\n                }\n                control.kind.action\n                    ?.trim()\n                    ?.takeIf { it.isNotBlank() }\n                    ?.let { action ->\n                        val actionRule = if (action.startsWith("<js>") || action.startsWith("{{")) {\n                            action\n                        } else {\n                            "<js>$action</js>"\n                        }\n                        AnalyzeRule(source = source, preUpdateJs = true)\n                            .setContent(actionRule, source.getKey())\n                            .put("infoMap", infoMap)\n                            .getString(actionRule)\n                    }\n                source.clearExploreKindsCache()\n                allSourceRawKinds = source.exploreKinds()\n'''
if old not in text:
    raise SystemExit('select action block not found')
text = text.replace(old, new, 1)
p.write_text(text)
print('patched modern explore select action execution')
