package io.legado.app.enhance.source

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule

/**
 * Detect review capabilities without changing the upstream BookSource model or
 * persisting synthetic source groups.
 */
fun BookSource.hasBookReviewCapability(): Boolean =
    ruleReview.activeReviewRule()?.run {
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
    ruleReview.activeReviewRule()?.run {
        hasAny(
            reviewUrl,
            avatarRule,
            contentRule,
            postTimeRule,
            reviewQuoteUrl,
        )
    } == true

fun BookSource.hasOtherCommentCapability(): Boolean =
    ruleReview.activeReviewRule()?.run {
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

private fun ReviewRule?.activeReviewRule(): ReviewRule? =
    this?.takeIf { it.enabled }

private fun hasAny(vararg values: String?): Boolean =
    values.any { !it.isNullOrBlank() }
