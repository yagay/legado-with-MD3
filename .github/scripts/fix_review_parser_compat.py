from pathlib import Path

p = Path('app/src/main/java/io/legado/app/model/analyzeRule/ReviewRuleParser.kt')
s = p.read_text()
s = s.replace('import org.htmlunit.corejs.javascript.NativeArray\n', '')
s = s.replace('import org.htmlunit.corejs.javascript.Scriptable\n', '')
s = s.replace('analyzeRule.getElementsRaw(', 'analyzeRule.getElements(')
s = s.replace('AnalyzeByJSonPath(content) { throw it }.getString(value)', 'AnalyzeByJSonPath(content).getString(value)')
s = s.replace('AnalyzeByJSonPath(content) { throw it }.getStringList(value)', 'AnalyzeByJSonPath(content).getStringList(value)')
start = s.index('    private fun normalizeList(value: Any?): List<Any> = when (value) {')
end = s.index('\n\n    internal data class ContentProtocol(', start)
s = s[:start] + '''    private fun normalizeList(value: Any?): List<Any> = when (value) {
        is List<*> -> value.filterNotNull()
        is Array<*> -> value.filterNotNull()
        is String -> GSON.fromJsonArray<Any>(value).getOrNull().orEmpty()
        null -> emptyList()
        else -> listOf(value)
    }''' + s[end:]
p.write_text(s)
