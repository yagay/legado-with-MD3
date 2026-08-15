from pathlib import Path

# BookSource: persist and deserialize ReviewRule like Legado.
p = Path('app/src/main/java/io/legado/app/data/entities/BookSource.kt')
s = p.read_text()
s = s.replace(
'''                && getContentRule() == source.getContentRule()
    }
''',
'''                && getContentRule() == source.getContentRule()
                && ruleReview == source.ruleReview
    }
''', 1)
s = s.replace(
'''        fun stringToReviewRule(json: String?): ReviewRule? = null

        @TypeConverter
        fun reviewRuleToString(reviewRule: ReviewRule?): String = "null"
''',
'''        fun stringToReviewRule(json: String?) =
            GSON.fromJsonObject<ReviewRule>(json).getOrNull()

        @TypeConverter
        fun reviewRuleToString(reviewRule: ReviewRule?): String =
            GSON.toJson(reviewRule)
''', 1)
p.write_text(s)

# AnalyzeRule: paragraph-review locals must not leak into book/source variables.
p = Path('app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt')
s = p.read_text()
if 'private val localBindings = HashMap<String, String>()' not in s:
    s = s.replace(
        '    private val scriptCache = hashMapOf<String, CompiledScript>()\n',
        '    private val scriptCache = hashMapOf<String, CompiledScript>()\n    private val localBindings = HashMap<String, String>()\n', 1)
if 'fun setLocal(key: String, value: String): AnalyzeRule' not in s:
    needle = '''    /**
     * 获取保存的数据
     */
    fun get(key: String): String {
'''
    repl = '''    fun setLocal(key: String, value: String): AnalyzeRule {
        localBindings[key] = value
        return this
    }

    /**
     * 获取保存的数据
     */
    fun get(key: String): String {
        localBindings[key]?.let { return it }
'''
    if needle not in s: raise SystemExit('AnalyzeRule get block not found')
    s = s.replace(needle, repl, 1)
if 'localBindings["paraIndex"]' not in s:
    needle = '            bindings["fromBookInfo"] = isFromBookInfo\n'
    repl = '''            bindings["fromBookInfo"] = isFromBookInfo
            localBindings["paraIndex"]?.let { bindings["paraIndex"] = it }
            localBindings["paraData"]?.let { bindings["paraData"] = it }
            localBindings["page"]?.let { bindings["page"] = it.toIntOrNull() ?: it }
'''
    if needle not in s: raise SystemExit('AnalyzeRule bindings block not found')
    s = s.replace(needle, repl, 1)
p.write_text(s)
