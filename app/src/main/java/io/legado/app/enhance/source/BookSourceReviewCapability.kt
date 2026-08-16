package io.legado.app.enhance.source

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule

/**
 * Review capability detection kept in the enhance layer so the upstream BookSource model
 * and persisted source groups remain untouched.
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
