package io.legado.app.enhance.source

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceReviewCapabilityTest {

    @Test
    fun `book review capability follows configured summary rules even when disabled`() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/book-review",
            ruleReview = ReviewRule(
                enabled = false,
                reviewSummaryUrl = "/reviews",
                summaryListRule = "$.data[*]",
            )
        )

        assertTrue(source.hasBookReviewCapability())
        assertFalse(source.hasParagraphReviewCapability())
    }

    @Test
    fun `legacy enabled review flag is migrated to review rule`() {
        val source = GSON.fromJsonObject<BookSource>(
            """
            {
              "bookSourceUrl": "https://example.com/legacy-review",
              "bookSourceName": "Legacy review source",
              "enabledReview": true,
              "ruleReview": {}
            }
            """.trimIndent()
        ).getOrThrow()

        assertTrue(source.ruleReview?.enabled == true)
        assertTrue(source.hasBookReviewCapability())
        assertFalse(source.hasParagraphReviewCapability())
    }

    @Test
    fun `legacy disabled review flag does not create review capability`() {
        val source = GSON.fromJsonObject<BookSource>(
            """
            {
              "bookSourceUrl": "https://example.com/no-review",
              "bookSourceName": "No review source",
              "enabledReview": false,
              "ruleReview": {}
            }
            """.trimIndent()
        ).getOrThrow()

        assertFalse(source.hasBookReviewCapability())
        assertFalse(source.hasParagraphReviewCapability())
        assertFalse(source.hasOtherCommentCapability())
    }

    @Test
    fun `paragraph review capability follows paragraph rules`() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/paragraph-review",
            ruleReview = ReviewRule(
                reviewUrl = "/paragraph/{{chapterId}}",
                contentRule = "$.content",
            )
        )

        assertTrue(source.hasParagraphReviewCapability())
        assertFalse(source.hasBookReviewCapability())
    }

    @Test
    fun `modern paragraph summary rules are not misclassified as book review`() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/modern-paragraph-review",
            ruleReview = ReviewRule(
                enabled = true,
                reviewSummaryUrl = "/summary",
                summaryListRule = "$.data[*]",
                summaryParagraphIndexRule = "$.paragraphIndex",
                summaryParagraphDataRule = "$.paragraphData",
                summaryCountRule = "$.count",
                reviewDetailUrl = "/detail",
                detailListRule = "$.comments[*]",
                detailContentRule = "$.content",
            )
        )

        assertTrue(source.hasParagraphReviewCapability())
        assertFalse(source.hasBookReviewCapability())
    }

    @Test
    fun `other comment capability follows reply and interaction rules`() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/other-comments",
            ruleReview = ReviewRule(
                replyListRule = "$.replies[*]",
                replyContentRule = "$.text",
            )
        )

        assertTrue(source.hasOtherCommentCapability())
        assertFalse(source.hasParagraphReviewCapability())
    }

    @Test
    fun `legacy review quote endpoint belongs to paragraph and other comment groups`() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/quote-comments",
            ruleReview = ReviewRule(
                reviewUrl = "/paragraph/comments",
                reviewQuoteUrl = "/paragraph/replies",
                contentRule = "$.content",
            )
        )

        assertTrue(source.hasParagraphReviewCapability())
        assertTrue(source.hasOtherCommentCapability())
        assertFalse(source.hasBookReviewCapability())
    }

    @Test
    fun `legacy fanqie aggregate protocol is visible in review groups`() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/fanqie",
            ruleSearch = SearchRule(bookUrl = "/api/detail?book_id={{$.book_id}}"),
            ruleContent = ContentRule(
                content = "@js:'/api/comment?book_id='+book.bookUrl+' data.data.comment user_info user_name digg_count reply_count'"
            )
        )

        assertTrue(source.hasBookReviewCapability())
        assertTrue(source.hasOtherCommentCapability())
        assertFalse(source.hasParagraphReviewCapability())
    }

    @Test
    fun `legacy paragraph aggregate protocol is visible in paragraph and other groups`() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/paragraph-aggregate",
            jsLib = "fetch('/get_para_review?book_id=1'); fetch('/para_review?book_id=1'); var replies=[];",
        )

        assertTrue(source.hasParagraphReviewCapability())
        assertTrue(source.hasOtherCommentCapability())
        assertFalse(source.hasBookReviewCapability())
    }

    @Test
    fun `single legacy paragraph endpoint is enough for source manager classification`() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/legacy-paragraph-summary",
            jsLib = "function loadReview(){ return fetch('/get_para_review?book_id=' + bookId); }",
        )

        assertTrue(source.hasParagraphReviewCapability())
        assertFalse(source.hasBookReviewCapability())
    }

    @Test
    fun `legacy qq protocol is visible in book review group`() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/qq",
            ruleBookInfo = BookInfoRule(
                intro = "@js:result.commentlist..content",
                tocUrl = "https://ubook.reader.qq.com/api/book/chapter-list",
            ),
            ruleExplore = ExploreRule(
                bookUrl = "https://detailadr.reader.qq.com/?bid={{$.bid}}",
            ),
        )

        assertTrue(source.hasBookReviewCapability())
        assertFalse(source.hasParagraphReviewCapability())
    }

    @Test
    fun `enabled review rule marks book review capability`() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/enabled-review",
            ruleReview = ReviewRule(enabled = true)
        )

        assertTrue(source.hasBookReviewCapability())
        assertFalse(source.hasParagraphReviewCapability())
        assertFalse(source.hasOtherCommentCapability())
    }
}
