package io.legado.app.enhance.source

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule
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
