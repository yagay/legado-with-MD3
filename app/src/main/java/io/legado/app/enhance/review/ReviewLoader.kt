package io.legado.app.enhance.review

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
