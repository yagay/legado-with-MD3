package io.legado.app.utils

import io.legado.app.model.analyzeRule.AnalyzeUrl
import org.jsoup.Jsoup
import java.net.URL
import java.util.regex.Pattern

@Suppress("RegExpRedundantEscape")
object HtmlFormatter {
    private val nbspRegex = "(&nbsp;)+".toRegex()
    private val espRegex = "(&ensp;|&emsp;)".toRegex()
    private val noPrintRegex = "(&thinsp;|&zwnj;|&zwj;|\u2009|\u200C|\u200D)".toRegex()
    private val wrapHtmlRegex = "</?(?:div|p|br|hr|h\\d|article|dd|dl)[^>]*>".toRegex()
    private val commentRegex = "<!--[^>]*-->".toRegex() //注释
    private val notImgHtmlRegex = "</?(?!img)[a-zA-Z]+(?=[ >])[^<>]*>".toRegex()
    private val otherHtmlRegex = "</?[a-zA-Z]+(?=[ >])[^<>]*>".toRegex()
    private val formatImagePattern = Pattern.compile(
        "<img[^>]*\\ssrc\\s*=\\s*['\"]([^'\"{>]*\\{(?:[^{}]|\\{[^}>]+\\})+\\})['\"][^>]*>|<img[^>]*\\s(?:data-src|src)\\s*=\\s*['\"]([^'\">]+)['\"][^>]*>|<img[^>]*\\sdata-[^=>]*=\\s*['\"]([^'\">]*)['\"][^>]*>",
        Pattern.CASE_INSENSITIVE
    )
    private val indent1Regex = "\\s*\\n+\\s*".toRegex()
    private val indent2Regex = "^[\\n\\s]+".toRegex()
    private val lastRegex = "[\\n\\s]+$".toRegex()
    private const val PARAGRAPH_INDENT = "　　"
    private const val USE_WEB_PREFIX = "<useweb>"

    private val blankChars = charArrayOf(
        ' ', '\t', '\u00a0', '\u2002', '\u2003', '\u2009', '\u3000'
    )

    private const val metaLabels =
        "书名|名称|书籍名称|作者|译者|分类|类别|类型|题材|状态|连载状态|字数|标签|关键字|来源|首发|" +
            "更新时间|最后更新|最近更新|最新章节|最新更新"

    private const val introLabels = "内容简介|作品简介|小说简介|内容介绍|简介|文案|摘要"
    private val metaLineRegex =
        "^(?:[【\\[(（](?:$metaLabels)[】\\])）]|(?:$metaLabels)\\s*[：:])\\s*.{0,40}$".toRegex()
    private val introLabelRegex =
        "^(?:[【\\[(（](?:$introLabels)[】\\])）]|(?:$introLabels)\\s*[：:])\\s*".toRegex()

    private const val lineIcons = "(?:\\p{So}[\\uFE0F\\u200D\\s\\u200E]*)+"
    private val iconMetaLineRegex = "^$lineIcons[^：:\\s]{1,8}\\s*[：:]\\s*.{0,40}$".toRegex()
    private val decorationLineRegex = "^[^\\p{L}\\p{N}]+$".toRegex()

    fun format(html: String?, otherRegex: Regex = otherHtmlRegex): String =
        format(html, otherRegex, "　　")

    fun formatIntro(html: String?): String =
        format(html, otherHtmlRegex, "")

    private fun format(html: String?, otherRegex: Regex, paragraphIndent: String): String {
        html ?: return ""
        return html.replace(nbspRegex, " ")
            .replace(espRegex, " ")
            .replace(noPrintRegex, "")
            .replace(wrapHtmlRegex, "\n")
            .replace(commentRegex, "")
            .replace(otherRegex, "")
            .replace(indent1Regex, "\n$paragraphIndent")
            .replace(indent2Regex, paragraphIndent)
            .replace(lastRegex, "")
    }

    fun formatDisplayText(html: String?): String {
        if (html.isNullOrBlank()) return ""
        if (html.startsWith(USE_WEB_PREFIX, ignoreCase = true)) return html
        return formatPlainDisplayText(html)
    }

    private fun formatPlainDisplayText(html: String): String {
        val document = Jsoup.parseBodyFragment(html)
        document.outputSettings().prettyPrint(false)
        val body = document.body()
        body.select("script, style, noscript").remove()
        return format(body.html(), otherHtmlRegex, "")
            .lineSequence()
            .map { it.trim(*blankChars) }
            .filterNot { it.isEmpty() || metaLineRegex.matches(it) }
            .map { it.replaceFirst(introLabelRegex, "").trim(*blankChars) }
            .filterNot { it.isEmpty() }
            .joinToString("\n") { PARAGRAPH_INDENT + it }
    }

    private fun extractUseWebBody(html: String): String {
        if (!html.startsWith(USE_WEB_PREFIX, ignoreCase = true)) return html
        val closing = html.lastIndexOf("</useweb>", ignoreCase = true)
        return if (closing >= USE_WEB_PREFIX.length) {
            html.substring(USE_WEB_PREFIX.length, closing)
        } else {
            html.removePrefix(USE_WEB_PREFIX)
        }
    }

    fun formatIntroText(html: String?): String {
        val displayText = when {
            html.isNullOrBlank() -> ""
            html.startsWith(USE_WEB_PREFIX, ignoreCase = true) ->
                formatPlainDisplayText(extractUseWebBody(html))
            else -> formatDisplayText(html)
        }
        return displayText
            .lineSequence()
            .map { it.trim(*blankChars) }
            .filterNot {
                it.isEmpty() || decorationLineRegex.matches(it) || iconMetaLineRegex.matches(it)
            }
            .joinToString("\n") { PARAGRAPH_INDENT + it }
    }

    fun formatSummaryText(html: String?): String {
        return formatIntroText(html)
            .lineSequence()
            .map { it.trim(*blankChars) }
            .filterNot { it.isEmpty() }
            .joinToString(" ")
    }

    fun formatKeepImg(html: String?, redirectUrl: URL? = null): String {
        html ?: return ""
        val keepImgHtml = format(html, notImgHtmlRegex)

        val matcher = formatImagePattern.matcher(keepImgHtml)
        var appendPos = 0
        val sb = StringBuilder()
        while (matcher.find()) {
            var param = ""
            val rawUrl = matcher.group(1)?.let {
                val urlMatcher = AnalyzeUrl.paramPattern.matcher(it)
                if (urlMatcher.find()) {
                    param = ',' + it.substring(urlMatcher.end())
                    it.substring(0, urlMatcher.start())
                } else {
                    it
                }
            } ?: matcher.group(2) ?: matcher.group(3)!!
            val absoluteUrl = NetworkUtils.getAbsoluteURL(redirectUrl, rawUrl) + param
            sb.append(keepImgHtml.substring(appendPos, matcher.start()))
            sb.append("<img src=\"").append(absoluteUrl).append("\">")
            appendPos = matcher.end()
        }
        if (appendPos < keepImgHtml.length) {
            sb.append(keepImgHtml.substring(appendPos))
        }
        return sb.toString()
    }
}
