from pathlib import Path

# AnalyzeUrl: expose only paragraph-review URL locals to JS.
p = Path('app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt')
s = p.read_text()
if 'private val extraParams: Map<String, Any?> = emptyMap()' not in s:
    s = s.replace(
        '    private val infoMap: MutableMap<String, String>? = null\n) : JsExtensions {',
        '    private val infoMap: MutableMap<String, String>? = null,\n    private val extraParams: Map<String, Any?> = emptyMap()\n) : JsExtensions {', 1)
if 'bindings["paraIndex"]' not in s:
    s = s.replace(
        '            bindings["page"] = page\n',
        '''            bindings["page"] = extraParams["page"]?.let { value ->
                if (value is String) value.toIntOrNull() ?: value else value
            } ?: page
            extraParams["paraIndex"]?.let { bindings["paraIndex"] = it }
            extraParams["paraData"]?.let { bindings["paraData"] = it }
''', 1)
p.write_text(s)

# TextLine: remember how many title paragraphs precede body review ids.
p = Path('app/src/main/java/io/legado/app/ui/book/read/page/entities/TextLine.kt')
s = p.read_text()
if 'var reviewTitleOffset: Int = 0' not in s:
    s = s.replace('    var paragraphNum: Int = 0,\n', '    var paragraphNum: Int = 0,\n    var reviewTitleOffset: Int = 0,\n', 1)
p.write_text(s)

# ReviewColumn: counts are populated after paragraph number is resolved.
p = Path('app/src/main/java/io/legado/app/ui/book/read/page/entities/column/ReviewColumn.kt')
s = p.read_text().replace('    val count: Int = 0\n', '    var count: Int = 0\n', 1)
p.write_text(s)

# ChapterProvider: minimal chapter-scoped providers; avoids touching newer MD3 layout snapshots.
p = Path('app/src/main/java/io/legado/app/ui/book/read/page/provider/ChapterProvider.kt')
s = p.read_text()
if 'private var reviewCountProvider: ((Int, Int) -> Int)? = null' not in s:
    marker = '    const val indentChar = "　"\n'
    insert = '''    const val indentChar = "　"

    @Volatile
    private var reviewCountProvider: ((Int, Int) -> Int)? = null
    @Volatile
    private var reviewKeyProvider: ((Int, Int) -> String?)? = null
    @Volatile
    private var reviewProviderChapterIndex: Int? = null

    fun setReviewProviders(
        countProvider: ((Int, Int) -> Int)?,
        keyProvider: ((Int, Int) -> String?)?,
        chapterIndex: Int? = ReadBook.durChapterIndex,
    ) {
        reviewCountProvider = countProvider
        reviewKeyProvider = keyProvider
        reviewProviderChapterIndex = chapterIndex.takeIf { countProvider != null }
    }

    fun clearReviewProviders() {
        reviewCountProvider = null
        reviewKeyProvider = null
        reviewProviderChapterIndex = null
    }

    fun getReviewKeyById(reviewId: Int, chapterIndex: Int = ReadBook.durChapterIndex): String? {
        if (reviewProviderChapterIndex != chapterIndex) return null
        return reviewKeyProvider?.invoke(chapterIndex, reviewId)?.takeIf { it.isNotBlank() }
    }

    fun getReviewCount(
        paragraphNum: Int,
        isTitle: Boolean = false,
        titleOffset: Int = 1,
        chapterIndex: Int = ReadBook.durChapterIndex,
    ): Int {
        val provider = reviewCountProvider ?: return 0
        if (reviewProviderChapterIndex != chapterIndex) return 0
        if (isTitle) return provider(chapterIndex, -1).coerceAtLeast(0)
        val reviewId = paragraphNum - titleOffset
        if (reviewId <= 0) return 0
        return provider(chapterIndex, reviewId).coerceAtLeast(0)
    }
'''
    if marker not in s: raise SystemExit('ChapterProvider insertion point missing')
    s = s.replace(marker, insert, 1)
p.write_text(s)

# TextChapterLayout: reuse the existing reviewChar placeholder but turn it into a real ReviewColumn.
p = Path('app/src/main/java/io/legado/app/ui/book/read/page/provider/TextChapterLayout.kt')
s = p.read_text()
if 'entities.column.ReviewColumn' not in s:
    s = s.replace('import io.legado.app.ui.book.read.page.entities.column.ImageColumn\n', 'import io.legado.app.ui.book.read.page.entities.column.ImageColumn\nimport io.legado.app.ui.book.read.page.entities.column.ReviewColumn\n', 1)
if 'private var reviewTitleOffset = 0' not in s:
    s = s.replace('    private var pendingTextPage = TextPage()\n', '    private var pendingTextPage = TextPage()\n    private var reviewTitleOffset = 0\n', 1)
if 'reviewTitleOffset = sequenceOf(' not in s:
    needle = '        val sb = StringBuffer()\n'
    repl = '''        reviewTitleOffset = sequenceOf(
            pendingTextPage.lines.asReversed().firstOrNull { it.isTitle },
            textPages.asReversed().asSequence().flatMap { it.lines.asReversed().asSequence() }.firstOrNull { it.isTitle }
        ).filterNotNull().firstOrNull()?.paragraphNum ?: 0

        val sb = StringBuffer()
'''
    if needle not in s: raise SystemExit('TextChapterLayout body marker missing')
    s = s.replace(needle, repl, 1)
if 'textLine.reviewTitleOffset = reviewTitleOffset' not in s:
    needle = '            calcTextLinePosition(textPages, textLine, stringBuilder.length)\n            stringBuilder.append(lineText)\n'
    repl = '''            calcTextLinePosition(textPages, textLine, stringBuilder.length)
            textLine.reviewTitleOffset = reviewTitleOffset
            (textLine.columns.lastOrNull() as? ReviewColumn)?.count = ChapterProvider.getReviewCount(
                paragraphNum = textLine.paragraphNum,
                isTitle = textLine.isTitle,
                titleOffset = textLine.reviewTitleOffset,
                chapterIndex = bookChapter.index,
            )
            stringBuilder.append(lineText)
'''
    if needle not in s: raise SystemExit('TextChapterLayout paragraph resolution marker missing')
    s = s.replace(needle, repl, 1)
if 'char == reviewChar -> {' not in s:
    needle = '''        val column = when {
            !srcList.isNullOrEmpty() && (char == srcReplaceChar || char == reviewChar) -> {'''
    repl = '''        val column = when {
            char == reviewChar && srcList.isNullOrEmpty() -> {
                ReviewColumn(
                    start = absStartX + xStart,
                    end = absStartX + xEnd,
                    count = 0,
                )
            }

            !srcList.isNullOrEmpty() && (char == srcReplaceChar || char == reviewChar) -> {'''
    if needle not in s: raise SystemExit('TextChapterLayout addChar marker missing')
    s = s.replace(needle, repl, 1)
p.write_text(s)

# ContentTextView: route a real review click instead of the old placeholder toast.
p = Path('app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt')
s = p.read_text()
old = '''                is ReviewColumn -> {
                    context.toastOnUi("Button Pressed!")
                    handled = true
                }
'''
new = '''                is ReviewColumn -> {
                    if (column.count > 0) {
                        val reviewId = if (textLine.isTitle) {
                            -1
                        } else {
                            (textLine.paragraphNum - textLine.reviewTitleOffset)
                                .takeIf { it > 0 } ?: textLine.paragraphNum
                        }
                        requireCallBack.onReviewClick(reviewId, column.count, textPage.chapterIndex)
                        handled = true
                    }
                }
'''
if old in s:
    s = s.replace(old, new, 1)
elif 'onReviewClick(reviewId' not in s:
    raise SystemExit('ContentTextView review click marker missing')
if 'fun onReviewClick(paragraphNum: Int, count: Int, chapterIndex: Int)' not in s:
    s = s.replace('        fun onMarkingClick(markingId: String)\n', '        fun onMarkingClick(markingId: String)\n        fun onReviewClick(paragraphNum: Int, count: Int, chapterIndex: Int)\n', 1)
p.write_text(s)

# ReadBookController: summary loading + themed first-page detail dialog.
p = Path('app/src/main/java/io/legado/app/ui/book/read/ReadBookController.kt')
s = p.read_text()
if 'import androidx.appcompat.app.AlertDialog' not in s:
    s = s.replace('import androidx.appcompat.app.AppCompatActivity\n', 'import androidx.appcompat.app.AlertDialog\nimport androidx.appcompat.app.AppCompatActivity\n', 1)
if 'import io.legado.app.model.analyzeRule.AnalyzeUrl\n' not in s:
    s = s.replace('import io.legado.app.model.analyzeRule.AnalyzeRule\n', 'import io.legado.app.model.analyzeRule.AnalyzeRule\nimport io.legado.app.model.analyzeRule.AnalyzeUrl\nimport io.legado.app.model.analyzeRule.ReviewRuleParser\n', 1)
if 'private var reviewSummaryAppliedKey: String? = null' not in s:
    marker = '    private var cachedActionMenuItems: List<ActionMenuItem>? = null\n'
    insert = '''    private var cachedActionMenuItems: List<ActionMenuItem>? = null
    private var reviewSummaryAppliedKey: String? = null
    private var reviewSummaryLoadingKey: String? = null
    private var reviewSummaryRequestToken = 0L
'''
    if marker not in s: raise SystemExit('ReadBookController field marker missing')
    s = s.replace(marker, insert, 1)

if 'private fun loadReviewSummaryIfNeeded()' not in s:
    marker = '    // ── Key handling ──\n'
    methods = '''    private fun buildReviewSummaryKey(): String? {
        val book = ReadBook.book ?: return null
        val source = ReadBook.bookSource ?: return null
        val rule = source.ruleReview ?: return null
        return "${source.getKey()}|${book.bookUrl}|${rule.hashCode()}#${ReadBook.durChapterIndex}"
    }

    private fun loadReviewSummaryIfNeeded() {
        val book = ReadBook.book ?: run { ChapterProvider.clearReviewProviders(); return }
        val source = ReadBook.bookSource ?: run { ChapterProvider.clearReviewProviders(); return }
        val rule = source.ruleReview ?: run { ChapterProvider.clearReviewProviders(); return }
        val summaryUrl = rule.configuredSummaryUrl() ?: run { ChapterProvider.clearReviewProviders(); return }
        val chapterIndex = ReadBook.durChapterIndex
        val key = "${source.getKey()}|${book.bookUrl}|${rule.hashCode()}#$chapterIndex"
        if (reviewSummaryAppliedKey == key || reviewSummaryLoadingKey == key) return
        reviewSummaryLoadingKey = key
        val token = ++reviewSummaryRequestToken
        ChapterProvider.clearReviewProviders()
        activity.lifecycleScope.launch(IO) {
            val result = runCatching {
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: return@runCatching null
                if (chapter.isVolume) return@runCatching null
                val analyzeUrl = AnalyzeUrl(
                    summaryUrl,
                    baseUrl = chapter.url,
                    source = source,
                    ruleData = book,
                    chapter = chapter,
                    coroutineContext = coroutineContext,
                )
                val body = analyzeUrl.getStrResponseAwait(useWebView = false).body ?: return@runCatching null
                ReviewRuleParser.parseSummary(
                    body, rule, source, book, chapter, analyzeUrl.url, coroutineContext
                )
            }.getOrNull()
            withContext(Main) {
                if (token != reviewSummaryRequestToken || buildReviewSummaryKey() != key) return@withContext
                reviewSummaryLoadingKey = null
                if (result == null) {
                    ChapterProvider.clearReviewProviders()
                    return@withContext
                }
                ChapterProvider.setReviewProviders(
                    countProvider = { targetChapter, reviewId ->
                        if (targetChapter == chapterIndex) result.counts[reviewId] ?: 0 else 0
                    },
                    keyProvider = { targetChapter, reviewId ->
                        if (targetChapter == chapterIndex) result.keys[reviewId] else null
                    },
                    chapterIndex = chapterIndex,
                )
                reviewSummaryAppliedKey = key
                ReadBook.loadContent(resetPageOffset = false)
            }
        }
    }

    override fun onReviewClick(paragraphNum: Int, count: Int, chapterIndex: Int) {
        if (count <= 0 || paragraphNum == 0) return
        val book = ReadBook.book ?: return
        val source = ReadBook.bookSource ?: return
        val rule = source.ruleReview ?: return
        val detailUrl = rule.reviewDetailUrl?.takeIf { rule.enabled && it.isNotBlank() } ?: return
        val paraData = ChapterProvider.getReviewKeyById(paragraphNum, chapterIndex).orEmpty()
        activity.lifecycleScope.launch(IO) {
            val result = runCatching {
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: return@runCatching null
                val paraIndex = paragraphNum.toString()
                val analyzeUrl = AnalyzeUrl(
                    detailUrl,
                    page = 1,
                    baseUrl = chapter.url,
                    source = source,
                    ruleData = book,
                    chapter = chapter,
                    coroutineContext = coroutineContext,
                    extraParams = mapOf(
                        "paraIndex" to paraIndex,
                        "paraData" to paraData,
                        "page" to 1,
                    ),
                )
                val body = analyzeUrl.getStrResponseAwait(useWebView = false).body ?: return@runCatching null
                ReviewRuleParser.parseDetailPage(
                    body = body,
                    rule = rule,
                    nextPageRule = rule.reviewDetailNextPageUrl,
                    baseUrl = analyzeUrl.url,
                    source = source,
                    book = book,
                    chapter = chapter,
                    context = coroutineContext,
                    paraIndex = paraIndex,
                    paraData = paraData,
                    page = "1",
                )
            }.getOrNull()
            withContext(Main) {
                if (ReadBook.book?.bookUrl != book.bookUrl || result == null) return@withContext
                val message = result.items.joinToString("\n\n") { item ->
                    buildString {
                        val name = item.name.orEmpty().ifBlank { "匿名" }
                        append(name)
                        if (item.badges.isNotEmpty()) append("  ${item.badges.joinToString(" ")}")
                        item.time?.takeIf { it.isNotBlank() }?.let { append("\n$it") }
                        item.content?.takeIf { it.isNotBlank() }?.let { append("\n$it") }
                        if (item.replies.isNotEmpty()) {
                            item.replies.forEach { reply ->
                                append("\n  ↳ ${reply.name.orEmpty().ifBlank { "匿名" }}: ${reply.content.orEmpty()}")
                            }
                        }
                    }
                }.ifBlank { "暂无段评" }
                AlertDialog.Builder(activity)
                    .setTitle("段评（$count）")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

'''
    if marker not in s: raise SystemExit('ReadBookController method marker missing')
    s = s.replace(marker, methods + marker, 1)

# Every content refresh/chapter switch re-checks the chapter-specific summary key.
needle = '''    override fun upContent(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        postRender(ReadBookEffect.UpContent(relativePosition, resetPageOffset, success))
    }
'''
repl = '''    override fun upContent(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        postRender(ReadBookEffect.UpContent(relativePosition, resetPageOffset, success))
        handler.post { loadReviewSummaryIfNeeded() }
    }
'''
if needle in s:
    s = s.replace(needle, repl, 1)
elif 'handler.post { loadReviewSummaryIfNeeded() }' not in s:
    raise SystemExit('ReadBookController upContent marker missing')
p.write_text(s)
