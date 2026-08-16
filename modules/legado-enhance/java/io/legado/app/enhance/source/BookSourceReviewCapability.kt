package io.legado.app.enhance.source

import io.legado.app.data.entities.BookSource

/**
 * Detect review capabilities without changing the upstream BookSource model or
 * persisting synthetic source groups.
 *
 * ReviewRule.enabled also carries the legacy top-level `enabledReview` flag.
 * The Gson compatibility adapter migrates that flag while importing old
 * Legado book sources, so it remains available after Room persistence.
 */
fun BookSource.hasBookReviewCapability(): Boolean =
    ruleReview?.run {
        enabled || hasAny(
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
