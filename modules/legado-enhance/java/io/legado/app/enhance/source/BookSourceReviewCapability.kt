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

/** Mirrors LegacyParagraphReviewResolver's aggregate paragraph-review protocol gate. */
private fun BookSource.hasLegacyParagraphReviewProtocol(): Boolean {
    if (ruleReview != null) return false
    val js = jsLib.orEmpty()
    return js.contains("/get_para_review", ignoreCase = true) &&
        js.contains("/para_review?book_id=", ignoreCase = true)
}

/**
 * The two aggregate legacy adapters embed replies in the generated ReviewRule,
 * so they also belong in the "other comments" capability group.
 */
private fun BookSource.hasLegacyEmbeddedReplyProtocol(): Boolean {
    val content = ruleContent?.content.orEmpty()
    val searchBookUrl = ruleSearch?.bookUrl.orEmpty()
    val exploreBookUrl = ruleExplore?.bookUrl.orEmpty()
    val fanqie = content.contains("/api/comment?book_id=") &&
        content.contains("data.data.comment") &&
        content.contains("user_info") &&
        content.contains("reply_count") &&
        (searchBookUrl.contains("/api/detail?book_id=") ||
            exploreBookUrl.contains("/api/detail?book_id="))
    return fanqie || hasLegacyParagraphReviewProtocol()
}

private fun hasAny(vararg values: String?): Boolean =
    values.any { !it.isNullOrBlank() }
