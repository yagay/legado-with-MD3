#!/usr/bin/env python3
from pathlib import Path
from urllib.request import urlopen

ROOT = Path('.')
BASE = 'https://raw.githubusercontent.com/yagay/legado/leg/'
review_dir = ROOT / 'app/src/main/java/io/legado/app/enhance/review'
review_dir.mkdir(parents=True, exist_ok=True)

# Pure runtime adapters copied from legado:leg. They do not bring old UI/theme into MD3.
for name in [
    'ReviewContext.kt',
    'ReviewCapabilityResolver.kt',
    'LegacyBookReviewResolver.kt',
    'LegacyBookReviewLoader.kt',
    'LegacyParagraphReviewResolver.kt',
    'BookReviewCountLoader.kt',
]:
    data = urlopen(BASE + 'app/src/main/java/io/legado/app/enhance/review/' + name).read().decode('utf-8')
    (review_dir / name).write_text(data, encoding='utf-8')

# ReviewLoader adapted only at the JS-source boundary: current MD3 does not yet have legado:leg's
# JsSourceReview subsystem. Explicit ReviewRule + legacy protocol adapters retain leg semantics.
(review_dir / 'ReviewLoader.kt').write_text(r'''package io.legado.app.enhance.review

import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.ReviewRuleParser
import kotlin.coroutines.CoroutineContext

internal object ReviewLoader {
    data class SummaryRequest(
        val source: BookSource, val book: Book, val chapter: BookChapter,
        val ruleHash: Int, val ruleOverride: ReviewRule? = null,
    )
    data class SummaryResult(val summary: ReviewRuleParser.SummaryResult, val source: BaseSource)
    data class DetailRequest(
        val source: BookSource, val book: Book, val chapter: BookChapter,
        val paragraphIndex: Int, val paragraphData: String, val page: Int,
        val ruleHash: Int, val nextPageUrl: String? = null,
        val ruleOverride: ReviewRule? = null,
    )
    data class DetailResult(
        val items: List<ReviewRuleParser.DetailItem>, val nextPageUrl: String?,
        val hasNextPageRule: Boolean, val hasReplyUrl: Boolean, val source: BaseSource,
    )
    data class ReplyRequest(
        val source: BookSource, val book: Book, val chapter: BookChapter,
        val paragraphIndex: Int, val paragraphData: String, val reviewId: String,
        val page: Int, val ruleHash: Int, val ruleOverride: ReviewRule? = null,
    )
    data class ReplyResult(val replies: List<ReviewRuleParser.DetailItem>, val page: Int, val source: BaseSource)

    suspend fun loadSummary(request: SummaryRequest, coroutineContext: CoroutineContext): SummaryResult? {
        val rule = request.ruleOverride ?: request.source.ruleReview ?: return null
        if (!rule.enabled || rule.hashCode() != request.ruleHash || request.chapter.isVolume) return null
        val summaryUrl = rule.configuredSummaryUrl() ?: return null
        val analyzeUrl = AnalyzeUrl(
            summaryUrl, baseUrl = request.chapter.url, source = request.source,
            ruleData = request.book, chapter = request.chapter, coroutineContext = coroutineContext,
        )
        val body = analyzeUrl.getStrResponseAwait(useWebView = false).body ?: return null
        val result = ReviewRuleParser.parseSummary(
            body, rule, request.source, request.book, request.chapter,
            analyzeUrl.url, coroutineContext,
        ) ?: return null
        return SummaryResult(result, request.source)
    }

    suspend fun loadDetail(request: DetailRequest, coroutineContext: CoroutineContext): DetailResult? {
        val source = request.source
        val book = request.book
        val chapter = request.chapter
        val page = request.page
        val syntheticBook = request.paragraphIndex == -1 && request.paragraphData.isEmpty() &&
            chapter.bookUrl == book.bookUrl && chapter.url == book.bookUrl
        val legacyNext = request.nextPageUrl?.takeIf { it.startsWith(LEGACY_DOUBAN_NEXT_PREFIX) }
            ?.removePrefix(LEGACY_DOUBAN_NEXT_PREFIX)
        if (syntheticBook && (page == 1 || legacyNext != null)) {
            val legacy = LegacyBookReviewLoader.loadDoubanLongReviews(
                source, book, page, legacyNext, coroutineContext,
            )
            if (legacy != null && (legacy.items.isNotEmpty() || legacyNext != null)) {
                return DetailResult(
                    legacy.items,
                    legacy.nextPageUrl?.let { LEGACY_DOUBAN_NEXT_PREFIX + it },
                    legacy.hasNextPageRule, false, source,
                )
            }
        }
        val rule = request.ruleOverride ?: source.ruleReview ?: return null
        if (!rule.enabled || rule.hashCode() != request.ruleHash) return null
        val first = rule.reviewDetailUrl?.takeIf { it.isNotBlank() } ?: return null
        val nextRule = rule.reviewDetailNextPageUrl?.takeIf { it.isNotBlank() }
        val next = request.nextPageUrl?.takeIf { it.isNotBlank() }
        if (page > 1 && next == null && nextRule == null) return null
        if (rule.detailListRule.isNullOrBlank() || rule.detailContentRule.isNullOrBlank()) return null
        val urlRule = when {
            page > 1 && next != null -> next
            page > 1 -> nextRule ?: first
            else -> first
        }
        val paraIndex = request.paragraphIndex.toString()
        val analyzeUrl = AnalyzeUrl(
            urlRule, page = page,
            extraParams = mapOf("paraIndex" to paraIndex, "paraData" to request.paragraphData, "page" to page.toString()),
            baseUrl = chapter.url, source = source, ruleData = book, chapter = chapter,
            coroutineContext = coroutineContext,
        )
        val body = analyzeUrl.getStrResponseAwait(useWebView = false).body ?: ""
        val parsed = ReviewRuleParser.parseDetailPage(
            body, rule, nextRule, analyzeUrl.url, source, book, chapter, coroutineContext,
            paraIndex, request.paragraphData, page.toString(),
        )
        return DetailResult(
            parsed.items, parsed.nextPageUrl, nextRule != null,
            !rule.reviewQuoteUrl.isNullOrBlank() && !rule.replyListRule.isNullOrBlank() && !rule.replyContentRule.isNullOrBlank(),
            source,
        )
    }

    suspend fun loadReplies(request: ReplyRequest, coroutineContext: CoroutineContext): ReplyResult? {
        val rule = request.ruleOverride ?: request.source.ruleReview ?: return null
        if (!rule.enabled || rule.hashCode() != request.ruleHash) return null
        val urlRule = rule.reviewQuoteUrl?.takeIf { it.isNotBlank() } ?: return null
        if (rule.replyListRule.isNullOrBlank() || rule.replyContentRule.isNullOrBlank()) return null
        val paraIndex = request.paragraphIndex.toString()
        val analyzeUrl = AnalyzeUrl(
            urlRule, page = request.page,
            extraParams = mapOf(
                "paraIndex" to paraIndex, "paraData" to request.paragraphData,
                "reviewId" to request.reviewId, "page" to request.page.toString(),
            ),
            baseUrl = request.chapter.url, source = request.source, ruleData = request.book,
            chapter = request.chapter, coroutineContext = coroutineContext,
        )
        val body = analyzeUrl.getStrResponseAwait(useWebView = false).body?.takeIf { it.isNotBlank() } ?: return null
        return ReplyResult(
            ReviewRuleParser.parseReplyPage(
                body, rule, analyzeUrl.url, request.source, request.book, request.chapter,
                coroutineContext, paraIndex, request.paragraphData, request.page.toString(),
            ), request.page, request.source,
        )
    }

    private const val LEGACY_DOUBAN_NEXT_PREFIX = "legacy-douban:"
}
''', encoding='utf-8')

# leg strings, kept separate so future upstream string merges stay clean.
res = ROOT / 'app/src/main/res/values/strings_enhance_review.xml'
res.write_text('''<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <string name="book_review">书评</string>\n    <string name="book_review_with_count">书评（共 %1$d 条）</string>\n    <string name="view_book_review">查看书评</string>\n    <string name="book_review_loading">正在加载书评…</string>\n    <string name="book_review_load_more">加载更多</string>\n    <string name="book_review_empty">暂无书评</string>\n</resources>\n''', encoding='utf-8')

# Compose counterpart of leg's BookReviewEntryView / ReviewDetailDialog for the current MD3 page.
ui = ROOT / 'app/src/main/java/io/legado/app/ui/book/info/BookInfoReviewUi.kt'
ui.write_text(r'''package io.legado.app.ui.book.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

@Composable
internal fun BookReviewEntry(
    state: BookReviewUiState,
    onClick: () -> Unit,
) {
    if (!state.available) return
    ListItem(
        headlineContent = {
            Text(if (state.totalCount != null) "书评（共 ${state.totalCount} 条）" else "书评")
        },
        leadingContent = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
        trailingContent = { TextButton(onClick = onClick) { Text("查看书评") } },
        colors = ListItemDefaults.colors(containerColor = LegadoTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookReviewSheet(
    state: BookReviewUiState,
    onDismiss: () -> Unit,
    onLoadMore: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (state.totalCount != null) "书评（共 ${state.totalCount} 条）" else "书评",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                state.loading && state.items.isEmpty() -> Text("正在加载书评…", modifier = Modifier.padding(vertical = 24.dp))
                state.items.isEmpty() -> Text("暂无书评", modifier = Modifier.padding(vertical = 24.dp))
                else -> LazyColumn {
                    items(state.items, key = { it.key }) { item ->
                        BookReviewItem(item)
                    }
                    if (state.hasMore) {
                        item {
                            Button(
                                onClick = onLoadMore,
                                enabled = !state.loadingMore,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            ) { Text(if (state.loadingMore) "正在加载…" else "加载更多") }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BookReviewItem(item: BookReviewItemUi) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.name.ifBlank { "匿名用户" }, fontWeight = FontWeight.SemiBold)
            item.time?.takeIf { it.isNotBlank() }?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        }
        if (item.badges.isNotEmpty()) {
            Text(item.badges.joinToString(" · "), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        item.content?.takeIf { it.isNotBlank() }?.let {
            Text(it, modifier = Modifier.padding(top = 4.dp))
        }
        val meta = buildList {
            item.likeCount?.takeIf { it > 0 }?.let { add("赞 $it") }
            item.replyCount?.takeIf { it > 0 }?.let { add("回复 $it") }
        }
        if (meta.isNotEmpty()) Text(meta.joinToString("  "), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        item.replies.forEach { reply ->
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp)) {
                Text("${reply.name.ifBlank { "匿名" }}：${reply.content.orEmpty()}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
    }
}
''', encoding='utf-8')

contract = ROOT / 'app/src/main/java/io/legado/app/ui/book/info/BookInfoContract.kt'
s = contract.read_text(encoding='utf-8')
s = s.replace('    val showMangaUi: Boolean = true,\n)', '    val showMangaUi: Boolean = true,\n    val bookReview: BookReviewUiState = BookReviewUiState(),\n)')
marker = '@Stable\ndata class BookInfoBookUi('
insert = '''@Stable\ndata class BookReviewUiState(\n    val available: Boolean = false,\n    val totalCount: Int? = null,\n    val items: List<BookReviewItemUi> = emptyList(),\n    val loading: Boolean = false,\n    val loadingMore: Boolean = false,\n    val hasMore: Boolean = false,\n)\n\n@Stable\ndata class BookReviewItemUi(\n    val key: String,\n    val name: String,\n    val badges: List<String>,\n    val content: String?,\n    val imageUrl: String?,\n    val audioUrl: String?,\n    val time: String?,\n    val likeCount: Int?,\n    val replyCount: Int?,\n    val replies: List<BookReviewItemUi> = emptyList(),\n)\n\n'''
if 'data class BookReviewUiState' not in s:
    s = s.replace(marker, insert + marker)
s = s.replace('    data object ReadRecord : BookInfoSheet\n', '    data object ReadRecord : BookInfoSheet\n    data object BookReview : BookInfoSheet\n')
s = s.replace('    data object ReadRecordClick : BookInfoIntent\n', '    data object ReadRecordClick : BookInfoIntent\n    data object BookReviewClick : BookInfoIntent\n    data object LoadMoreBookReviews : BookInfoIntent\n')
contract.write_text(s, encoding='utf-8')

vm = ROOT / 'app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt'
s = vm.read_text(encoding='utf-8')
# imports
s = s.replace('import io.legado.app.data.entities.SearchBook\n', 'import io.legado.app.data.entities.SearchBook\nimport io.legado.app.data.entities.rule.ReviewRule\n')
s = s.replace('import io.legado.app.exception.NoBooksDirException\n', 'import io.legado.app.exception.NoBooksDirException\nimport io.legado.app.enhance.review.BookReviewCountLoader\nimport io.legado.app.enhance.review.ReviewCapabilityResolver\nimport io.legado.app.enhance.review.ReviewContext\nimport io.legado.app.enhance.review.ReviewLoader\nimport io.legado.app.enhance.review.chapterForAnalyze\n')
s = s.replace('import kotlinx.coroutines.Dispatchers.IO\n', 'import kotlinx.coroutines.Dispatchers.IO\nimport kotlinx.coroutines.Dispatchers.Main\n')
# fields
anchor = '    private var characterLoadJob: Job? = null\n'
extra = '''    private var bookReviewCountJob: Job? = null\n    private var bookReviewLoadJob: Job? = null\n    private var currentBookReviewRule: ReviewRule? = null\n    private var bookReviewPage = 0\n    private var bookReviewNextPageUrl: String? = null\n\n'''
if 'bookReviewCountJob' not in s:
    s = s.replace(anchor, anchor + extra)
# intents
s = s.replace('            BookInfoIntent.ReadRecordClick -> setSheet(BookInfoSheet.ReadRecord)\n', '''            BookInfoIntent.ReadRecordClick -> setSheet(BookInfoSheet.ReadRecord)\n            BookInfoIntent.BookReviewClick -> openBookReview()\n            BookInfoIntent.LoadMoreBookReviews -> loadBookReviews(loadMore = true)\n''')
# Trigger capability on ordinary upBook and source replacement.
s = s.replace('        bookSource = source\n        syncUiState(isTocLoading = false)\n', '        bookSource = source\n        refreshBookReviewCapability(book, source)\n        syncUiState(isTocLoading = false)\n')
s = s.replace('            bookSource = source\n            currentBook = it\n', '            bookSource = source\n            currentBook = it\n            refreshBookReviewCapability(it, source)\n')
# Add functions before dismissSheet.
fn_anchor = '    private fun dismissSheet() {'
functions = r'''    private fun refreshBookReviewCapability(book: Book, source: BookSource?) {
        bookReviewCountJob?.cancel()
        bookReviewLoadJob?.cancel()
        bookReviewPage = 0
        bookReviewNextPageUrl = null
        val rule = ReviewCapabilityResolver.resolveBookReview(source, book)
        currentBookReviewRule = rule
        _screenState.update {
            it.copy(bookReview = BookReviewUiState(available = source != null && rule != null))
        }
        if (source == null || rule == null) return
        val targetSource = source.getKey()
        val targetBook = book.bookUrl
        val targetRule = rule.hashCode()
        bookReviewCountJob = viewModelScope.launch(IO) {
            val count = runCatching {
                BookReviewCountLoader.loadExactCount(source, book, rule, coroutineContext)
            }.getOrNull()
            withContext(Main) {
                if (bookSource?.getKey() != targetSource || currentBook?.bookUrl != targetBook || currentBookReviewRule?.hashCode() != targetRule) return@withContext
                _screenState.update { state -> state.copy(bookReview = state.bookReview.copy(totalCount = count)) }
            }
        }
    }

    private fun openBookReview() {
        if (currentBookReviewRule == null) return
        setSheet(BookInfoSheet.BookReview)
        if (_screenState.value.bookReview.items.isEmpty()) loadBookReviews(loadMore = false)
    }

    private fun loadBookReviews(loadMore: Boolean) {
        if (bookReviewLoadJob?.isActive == true) return
        val source = bookSource ?: return
        val book = currentBook ?: return
        val rule = currentBookReviewRule ?: return
        val nextPage = if (loadMore) bookReviewNextPageUrl else null
        if (loadMore && bookReviewPage > 0 && nextPage == null) return
        val page = if (loadMore) bookReviewPage + 1 else 1
        _screenState.update { state ->
            state.copy(bookReview = state.bookReview.copy(
                loading = !loadMore,
                loadingMore = loadMore,
                items = if (loadMore) state.bookReview.items else emptyList(),
            ))
        }
        val context = ReviewContext.BookReview(source, book)
        val chapter = context.chapterForAnalyze()
        val targetSource = source.getKey()
        val targetBook = book.bookUrl
        val targetRule = rule.hashCode()
        bookReviewLoadJob = viewModelScope.launch(IO) {
            val result = runCatching {
                ReviewLoader.loadDetail(
                    ReviewLoader.DetailRequest(
                        source = source,
                        book = book,
                        chapter = chapter,
                        paragraphIndex = -1,
                        paragraphData = "",
                        page = page,
                        ruleHash = targetRule,
                        nextPageUrl = nextPage,
                        ruleOverride = rule,
                    ),
                    coroutineContext,
                )
            }.getOrNull()
            withContext(Main) {
                if (bookSource?.getKey() != targetSource || currentBook?.bookUrl != targetBook || currentBookReviewRule?.hashCode() != targetRule) return@withContext
                if (result == null) {
                    _screenState.update { state -> state.copy(bookReview = state.bookReview.copy(loading = false, loadingMore = false, hasMore = false)) }
                    return@withContext
                }
                bookReviewPage = page
                bookReviewNextPageUrl = result.nextPageUrl?.takeIf { it.isNotBlank() }
                val mapped = result.items.mapIndexed { index, item -> item.toBookReviewItemUi(page, index) }
                _screenState.update { state ->
                    val merged = if (loadMore) state.bookReview.items + mapped else mapped
                    state.copy(bookReview = state.bookReview.copy(
                        items = merged.distinctBy { it.key },
                        loading = false,
                        loadingMore = false,
                        hasMore = bookReviewNextPageUrl != null,
                    ))
                }
            }
        }
    }

    private fun io.legado.app.model.analyzeRule.ReviewRuleParser.DetailItem.toBookReviewItemUi(page: Int, index: Int): BookReviewItemUi {
        val fallback = "$page:$index:${name.orEmpty()}:${content.orEmpty().hashCode()}"
        return BookReviewItemUi(
            key = id?.takeIf { it.isNotBlank() } ?: fallback,
            name = name.orEmpty(),
            badges = badges,
            content = content,
            imageUrl = imageUrl,
            audioUrl = audioUrl,
            time = time,
            likeCount = likeCount,
            replyCount = replyCount,
            replies = replies.mapIndexed { replyIndex, reply -> reply.toBookReviewItemUi(page, index * 1000 + replyIndex + 1) },
        )
    }

'''
if 'private fun refreshBookReviewCapability' not in s:
    s = s.replace(fn_anchor, functions + fn_anchor)
vm.write_text(s, encoding='utf-8')

screen = ROOT / 'app/src/main/java/io/legado/app/ui/book/info/BookInfoScreen.kt'
s = screen.read_text(encoding='utf-8')
entry_anchor = '''                                BookInfoActions(\n                                    inBookshelf = state.inBookshelf,\n                                    onShelfClick = { onIntent(BookInfoIntent.ShelfClick) },\n                                    onTocClick = { onIntent(BookInfoIntent.TocClick) },\n                                    onGroupClick = { onIntent(BookInfoIntent.GroupClick) },\n                                    onSourceClick = { onIntent(BookInfoIntent.ChangeSourceClick) },\n                                    onReadRecordClick = { onIntent(BookInfoIntent.ReadRecordClick) },\n                                )\n'''
entry = entry_anchor + '''                                BookReviewEntry(\n                                    state = state.bookReview,\n                                    onClick = { onIntent(BookInfoIntent.BookReviewClick) },\n                                )\n'''
if 'onIntent(BookInfoIntent.BookReviewClick)' not in s:
    if entry_anchor not in s:
        raise SystemExit('BookInfoActions anchor not found')
    s = s.replace(entry_anchor, entry, 1)
sheet_anchor = '''        BookInfoSheet.ReadRecord -> BookReadRecordSheet(\n            show = currentSheet == BookInfoSheet.ReadRecord,\n            totalReadTime = state.readRecordTotalTime,\n            timelineDays = state.readRecordTimelineDays,\n            onDismissRequest = { onIntent(BookInfoIntent.DismissSheet) },\n        )\n'''
sheet = sheet_anchor + '''        BookInfoSheet.BookReview -> BookReviewSheet(\n            state = state.bookReview,\n            onDismiss = { onIntent(BookInfoIntent.DismissSheet) },\n            onLoadMore = { onIntent(BookInfoIntent.LoadMoreBookReviews) },\n        )\n'''
if 'BookInfoSheet.BookReview ->' not in s:
    if sheet_anchor not in s:
        raise SystemExit('ReadRecord sheet anchor not found')
    s = s.replace(sheet_anchor, sheet, 1)
screen.write_text(s, encoding='utf-8')

print('ported legado:leg book-review capability + MD3 UI adapter')
