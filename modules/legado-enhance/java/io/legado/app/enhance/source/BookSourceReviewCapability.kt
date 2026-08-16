package io.legado.app.enhance.source

import io.legado.app.data.entities.BookSource

/**
 * Detect review capabilities without changing the upstream BookSource model or
 * persisting synthetic source groups.
 *
 * Keep this classification aligned with the runtime review adapters. Sources can
 * expose review support either through an explicit ReviewRule or through one of
 * the legacy protocols that the review runtime upgrades on demand.
 */
fun BookSource.hasBookReviewCapability(): Boolean {
    val explicit = ruleReview?.run {
        val paragraphRule = hasExplicitParagraphReviewFields()
        !paragraphRule && (enabled || hasAny(
            reviewSummaryUrl,
            summaryListRule,
            summaryCountRule,
            reviewDetailUrl,
            reviewDetailNextPageUrl,
            detailListRule,
            detailIdRule,
            detailAvatarRule,
            detailNameRule,
            detailBadgeRule,
            detailContentRule,
        ))
    } == true
    return explicit || hasLegacyBookReviewProtocol()
}

fun BookSource.hasParagraphReviewCapability(): Boolean {
    val explicit = ruleReview?.hasExplicitParagraphReviewFields() == true
    return explicit || hasLegacyParagraphReviewProtocol()
}

fun BookSource.hasOtherCommentCapability(): Boolean {
    val explicit = ruleReview?.run {
        // reviewQuoteUrl is the legacy "fetch paragraph-review replies" endpoint.
        // It is reply capability even when a source does not provide replyListRule.
        hasAny(
            reviewQuoteUrl,
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
    return explicit || hasLegacyEmbeddedReplyProtocol()
}

private fun io.legado.app.data.entities.rule.ReviewRule.hasExplicitParagraphReviewFields(): Boolean =
    hasAny(
        reviewUrl,
        avatarRule,
        contentRule,
        postTimeRule,
        reviewQuoteUrl,
        summaryParagraphIndexRule,
        summaryParagraphDataRule,
    )

/**
 * Mirrors the protocol gates in LegacyBookReviewResolver. These checks only answer
 * whether a source is capable of producing a whole-book review rule; book-specific
 * ids/URLs are still resolved later by the runtime adapter.
 */
private fun BookSource.hasLegacyBookReviewProtocol(): Boolean {
    val sourceUrl = bookSourceUrl.substringBefore('#')
    val infoIntro = ruleBookInfo?.intro.orEmpty()
    val tocUrl = ruleBookInfo?.tocUrl.orEmpty()
    val searchBookUrl = ruleSearch?.bookUrl.orEmpty()
    val exploreBookUrl = ruleExplore?.bookUrl.orEmpty()
    val chapterList = ruleToc?.chapterList.orEmpty()
    val chapterUrl = ruleToc?.chapterUrl.orEmpty()
    val content = ruleContent?.content.orEmpty()

    val fanqie = content.contains("/api/comment?book_id=") &&
        content.contains("data.data.comment") &&
        content.contains("user_info") &&
        content.contains("user_name") &&
        content.contains("digg_count") &&
        content.contains("reply_count") &&
        (searchBookUrl.contains("/api/detail?book_id=") ||
            exploreBookUrl.contains("/api/detail?book_id="))

    val yousuu = (tocUrl.contains("/comment") || chapterUrl.contains("/comment") ||
        chapterList.contains("/comment")) &&
        (chapterList.contains("data.comments") || chapterList.contains("书评")) &&
        content.contains("createrId.userName") && content.contains("score") &&
        content.contains("content") &&
        (content.contains("createdAt") || content.contains("praiseCount"))

    val douban = (tocUrl.contains("reviews") || sourceUrl.contains("douban.com")) &&
        (chapterList.contains("review-list") || chapterList.contains("review-item")) &&
        chapterUrl.contains("href") && content.contains("review-content")

    val qq = infoIntro.contains("commentlist..content") &&
        tocUrl.contains("ubook.reader.qq.com/api/book/chapter-list") &&
        (searchBookUrl.contains("detailadr.reader.qq.com/") ||
            exploreBookUrl.contains("detailadr.reader.qq.com/"))

    val jjwxc = infoIntro.contains("comment/getCommentList?versionCode=268&novelId=") &&
        infoIntro.contains("A.data.commentList") &&
        infoIntro.contains("commentAuthor") &&
        infoIntro.contains("commentBody") &&
        infoIntro.contains("commentDate") &&
        !infoIntro.contains("chapterId=")

    return fanqie || yousuu || douban || qq || jjwxc
}

/**
 * Detect old paragraph-review adapters from source-level JavaScript.
 *
 * Older sources are not fully uniform: some keep only the summary endpoint in
 * jsLib and construct the detail endpoint dynamically, while newer scripts use
 * getReviewSummary/getReviewDetail naming. Requiring two literal endpoint strings
 * made valid paragraph-review sources disappear from the manager filter.
 */
private fun BookSource.hasLegacyParagraphReviewProtocol(): Boolean {
    if (ruleReview != null) return false
    val js = jsLib.orEmpty()
    if (js.isBlank()) return false

    val hasLegacyApi = js.contains("get_para_review", ignoreCase = true) ||
        js.contains("para_review", ignoreCase = true)
    val hasNativeJsPair = js.contains("getReviewSummary", ignoreCase = true) &&
        js.contains("getReviewDetail", ignoreCase = true)
    val hasParagraphPayload = js.contains("paraIndex", ignoreCase = true) &&
        (js.contains("paraData", ignoreCase = true) ||
            js.contains("paragraphIndex", ignoreCase = true))

    return hasLegacyApi || hasNativeJsPair || hasParagraphPayload
}

/**
 * Legacy adapters can expose replies either through explicit reply endpoints or
 * as embedded reply arrays. Detect the common source-level forms so databases
 * created before ReviewRule persistence was fixed can still populate this group.
 */
private fun BookSource.hasLegacyEmbeddedReplyProtocol(): Boolean {
    val content = ruleContent?.content.orEmpty()
    val searchBookUrl = ruleSearch?.bookUrl.orEmpty()
    val exploreBookUrl = ruleExplore?.bookUrl.orEmpty()
    val js = jsLib.orEmpty()

    val fanqie = content.contains("/api/comment?book_id=") &&
        content.contains("data.data.comment") &&
        content.contains("user_info") &&
        content.contains("reply_count") &&
        (searchBookUrl.contains("/api/detail?book_id=") ||
            exploreBookUrl.contains("/api/detail?book_id="))

    val paragraphReplies = hasLegacyParagraphReviewProtocol() && (
        js.contains("reply", ignoreCase = true) ||
            js.contains("replies", ignoreCase = true) ||
            js.contains("reply_count", ignoreCase = true)
        )

    return fanqie || paragraphReplies
}

private fun hasAny(vararg values: String?): Boolean =
    values.any { !it.isNullOrBlank() }
