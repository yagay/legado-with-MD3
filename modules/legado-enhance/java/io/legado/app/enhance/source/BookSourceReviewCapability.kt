package io.legado.app.enhance.source

import io.legado.app.data.entities.BookSource

/**
 * Detect review capabilities without changing the upstream BookSource model or
 * persisting synthetic source groups.
 *
 * Capability detection intentionally ignores ReviewRule.enabled. That flag
 * controls whether review behavior is currently enabled at runtime, while this
 * filter answers whether the source has the corresponding rules configured at
 * all. Imported sources may omit enabled and therefore deserialize it as false.
 */
fun BookSource.hasBookReviewCapability(): Boolean =
    ruleReview?.run {
        hasAny(
            reviewSummaryUrl,
            summaryListRule,
            summaryParagraphIndexRule,
            summaryParagraphDataRule,
            summaryCountRule,
            reviewDetailUrl,
            reviewDetailNextPageUrl,
            detailListRule,
            detailIdRule,
            detailAvatarRule,
            detailNameRule,
            detailBadgeRule,
            detailContentRule,
        )
    } == true

fun BookSource.hasParagraphReviewCapability(): Boolean =
    ruleReview?.run {
        hasAny(
            reviewUrl,
            avatarRule,
            contentRule,
            postTimeRule,
            reviewQuoteUrl,
        )
    } == true

fun BookSource.hasOtherCommentCapability(): Boolean =
    ruleReview?.run {
        hasAny(
            replyListRule,
            replyIdRule,
            replyAvatarRule,
            replyNameRule,
            replyBadgeRule,
            replyContentRule,
            voteUpUrl,
            voteDownUrl,
            postReviewUrl,
            postQuoteUrl,
            deleteUrl,
        )
    } == true

private fun hasAny(vararg values: String?): Boolean =
    values.any { !it.isNullOrBlank() }
