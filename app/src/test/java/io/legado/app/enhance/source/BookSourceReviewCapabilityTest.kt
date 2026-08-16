package io.legado.app.enhance.source

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule
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
    fun `empty review rule has no review capabilities`() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/empty",
            ruleReview = ReviewRule(enabled = true)
        )

        assertFalse(source.hasBookReviewCapability())
        assertFalse(source.hasParagraphReviewCapability())
        assertFalse(source.hasOtherCommentCapability())
    }
}
