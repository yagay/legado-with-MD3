from pathlib import Path


def replace_once(path: str, old: str, new: str):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'missing pattern in {path}: {old[:100]!r}')
    p.write_text(text.replace(old, new, 1))

# PR #6: wire enhance-only review capability filters into source management.
vm = 'app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceViewModel.kt'
replace_once(vm,
'''import io.legado.app.domain.gateway.OtherSettingsGateway\nimport io.legado.app.help.book.ContentProcessor''',
'''import io.legado.app.domain.gateway.OtherSettingsGateway\nimport io.legado.app.enhance.source.hasBookReviewCapability\nimport io.legado.app.enhance.source.hasOtherCommentCapability\nimport io.legado.app.enhance.source.hasParagraphReviewCapability\nimport io.legado.app.help.book.ContentProcessor''')
replace_once(vm,
'''        const val FILTER_ENABLED_EXPLORE = "@enabledExplore"\n        const val FILTER_DISABLED_EXPLORE = "@disabledExplore"\n        const val PREFIX_GROUP = "group:"''',
'''        const val FILTER_ENABLED_EXPLORE = "@enabledExplore"\n        const val FILTER_DISABLED_EXPLORE = "@disabledExplore"\n        const val FILTER_BOOK_REVIEW = "@review:book"\n        const val FILTER_PARAGRAPH_REVIEW = "@review:paragraph"\n        const val FILTER_OTHER_COMMENT = "@review:other"\n        const val PREFIX_GROUP = "group:"''')
replace_once(vm,
'''            BookSourceViewModel.FILTER_ENABLED_EXPLORE -> source.enabledExplore; BookSourceViewModel.FILTER_DISABLED_EXPLORE -> !source.enabledExplore\n            else -> filter.startsWith(BookSourceViewModel.PREFIX_GROUP)''',
'''            BookSourceViewModel.FILTER_ENABLED_EXPLORE -> source.enabledExplore; BookSourceViewModel.FILTER_DISABLED_EXPLORE -> !source.enabledExplore\n            BookSourceViewModel.FILTER_BOOK_REVIEW -> source.getBookSource()?.hasBookReviewCapability() == true\n            BookSourceViewModel.FILTER_PARAGRAPH_REVIEW -> source.getBookSource()?.hasParagraphReviewCapability() == true\n            BookSourceViewModel.FILTER_OTHER_COMMENT -> source.getBookSource()?.hasOtherCommentCapability() == true\n            else -> filter.startsWith(BookSourceViewModel.PREFIX_GROUP)''')
replace_once(vm,
'''    BookSourceViewModel.FILTER_DISABLED_EXPLORE -> application.getString(io.legado.app.R.string.disabled_explore)\n    else -> removePrefix(BookSourceViewModel.PREFIX_GROUP)''',
'''    BookSourceViewModel.FILTER_DISABLED_EXPLORE -> application.getString(io.legado.app.R.string.disabled_explore)\n    BookSourceViewModel.FILTER_BOOK_REVIEW -> application.getString(io.legado.app.R.string.source_filter_book_review)\n    BookSourceViewModel.FILTER_PARAGRAPH_REVIEW -> application.getString(io.legado.app.R.string.review)\n    BookSourceViewModel.FILTER_OTHER_COMMENT -> application.getString(io.legado.app.R.string.source_filter_other_comment)\n    else -> removePrefix(BookSourceViewModel.PREFIX_GROUP)''')

screen = 'app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceScreen.kt'
replace_once(screen,
'''        stringResource(R.string.enabled_explore) to BookSourceViewModel.FILTER_ENABLED_EXPLORE,\n        stringResource(R.string.disabled_explore) to BookSourceViewModel.FILTER_DISABLED_EXPLORE,\n    )''',
'''        stringResource(R.string.enabled_explore) to BookSourceViewModel.FILTER_ENABLED_EXPLORE,\n        stringResource(R.string.disabled_explore) to BookSourceViewModel.FILTER_DISABLED_EXPLORE,\n        stringResource(R.string.source_filter_book_review) to BookSourceViewModel.FILTER_BOOK_REVIEW,\n        stringResource(R.string.review) to BookSourceViewModel.FILTER_PARAGRAPH_REVIEW,\n        stringResource(R.string.source_filter_other_comment) to BookSourceViewModel.FILTER_OTHER_COMMENT,\n    )''')

# PR #8: preserve explicit <useweb> on detail surfaces and render it with WebView.
html = 'app/src/main/java/io/legado/app/utils/HtmlFormatter.kt'
replace_once(html,
'''    private const val PARAGRAPH_INDENT = "　　"\n\n    private val blankChars''',
'''    private const val PARAGRAPH_INDENT = "　　"\n    private const val USE_WEB_PREFIX = "<useweb>"\n\n    private val blankChars''')
replace_once(html,
'''    fun formatDisplayText(html: String?): String {\n        if (html.isNullOrBlank()) return ""\n        val document = Jsoup.parseBodyFragment(html)''',
'''    fun formatDisplayText(html: String?): String {\n        if (html.isNullOrBlank()) return ""\n        if (html.startsWith(USE_WEB_PREFIX, ignoreCase = true)) return html\n        return formatPlainDisplayText(html)\n    }\n\n    private fun formatPlainDisplayText(html: String): String {\n        val document = Jsoup.parseBodyFragment(html)''')
replace_once(html,
'''    fun formatIntroText(html: String?): String {\n        return formatDisplayText(html)\n            .lineSequence()''',
'''    private fun extractUseWebBody(html: String): String {\n        if (!html.startsWith(USE_WEB_PREFIX, ignoreCase = true)) return html\n        val closing = html.lastIndexOf("</useweb>", ignoreCase = true)\n        return if (closing >= USE_WEB_PREFIX.length) {\n            html.substring(USE_WEB_PREFIX.length, closing)\n        } else {\n            html.removePrefix(USE_WEB_PREFIX)\n        }\n    }\n\n    fun formatIntroText(html: String?): String {\n        val displayText = when {\n            html.isNullOrBlank() -> ""\n            html.startsWith(USE_WEB_PREFIX, ignoreCase = true) ->\n                formatPlainDisplayText(extractUseWebBody(html))\n            else -> formatDisplayText(html)\n        }\n        return displayText\n            .lineSequence()''')

animated = 'app/src/main/java/io/legado/app/ui/widget/components/text/AnimatedText.kt'
replace_once(animated,
'''package io.legado.app.ui.widget.components.text\n\nimport androidx.compose.animation.AnimatedContent''',
'''package io.legado.app.ui.widget.components.text\n\nimport android.annotation.SuppressLint\nimport android.graphics.Color as AndroidColor\nimport android.webkit.WebView\nimport android.webkit.WebViewClient\nimport androidx.compose.animation.AnimatedContent''')
replace_once(animated,
'''import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.Row''',
'''import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.Row\nimport androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.height''')
replace_once(animated,
'''import androidx.compose.runtime.Composable\nimport androidx.compose.ui.Alignment''',
'''import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableFloatStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue\nimport androidx.compose.ui.Alignment''')
replace_once(animated,
'''import androidx.compose.ui.unit.TextUnit\n\n@Composable''',
'''import androidx.compose.ui.unit.TextUnit\nimport androidx.compose.ui.unit.dp\nimport androidx.compose.ui.viewinterop.AndroidView\nimport kotlin.math.roundToInt\n\nprivate const val USE_WEB_PREFIX = "<useweb>"\n\n@Composable''')
old_block = '''        AppText(\n            text = targetText,\n            modifier = modifier,\n            style = style,\n            color = color,\n            softWrap = softWrap,\n            fontSize = fontSize,\n            fontStyle = fontStyle,\n            fontWeight = fontWeight,\n            fontFamily = fontFamily,\n            letterSpacing = letterSpacing,\n            textDecoration = textDecoration,\n            textAlign = textAlign,\n            lineHeight = lineHeight,\n            overflow = overflow,\n            maxLines = maxLines,\n            minLines = minLines,\n            onTextLayout = onTextLayout\n        )\n    }\n}\n\n\n@Composable\nfun AdaptiveAnimatedText'''
new_block = '''        if (targetText.startsWith(USE_WEB_PREFIX, ignoreCase = true)) {\n            UseWebText(targetText, modifier)\n        } else {\n            AppText(\n                text = targetText,\n                modifier = modifier,\n                style = style,\n                color = color,\n                softWrap = softWrap,\n                fontSize = fontSize,\n                fontStyle = fontStyle,\n                fontWeight = fontWeight,\n                fontFamily = fontFamily,\n                letterSpacing = letterSpacing,\n                textDecoration = textDecoration,\n                textAlign = textAlign,\n                lineHeight = lineHeight,\n                overflow = overflow,\n                maxLines = maxLines,\n                minLines = minLines,\n                onTextLayout = onTextLayout\n            )\n        }\n    }\n}\n\n@SuppressLint("SetJavaScriptEnabled")\n@Composable\nprivate fun UseWebText(text: String, modifier: Modifier = Modifier) {\n    val html = remember(text) {\n        val closing = text.lastIndexOf("</useweb>", ignoreCase = true)\n        if (closing >= USE_WEB_PREFIX.length) {\n            text.substring(USE_WEB_PREFIX.length, closing)\n        } else {\n            text.removePrefix(USE_WEB_PREFIX)\n        }\n    }\n    var heightDp by remember(text) { mutableFloatStateOf(1f) }\n    AndroidView(\n        modifier = modifier.fillMaxWidth().height(heightDp.dp),\n        factory = { context ->\n            WebView(context).apply {\n                setBackgroundColor(AndroidColor.TRANSPARENT)\n                isVerticalScrollBarEnabled = false\n                isHorizontalScrollBarEnabled = false\n                isNestedScrollingEnabled = false\n                settings.javaScriptEnabled = true\n                settings.domStorageEnabled = true\n                settings.defaultTextEncodingName = "utf-8"\n                settings.useWideViewPort = false\n                settings.loadWithOverviewMode = false\n                webViewClient = object : WebViewClient() {\n                    override fun onPageFinished(view: WebView?, url: String?) {\n                        val currentView = view ?: return\n                        currentView.evaluateJavascript(\n                            "Math.max(document.body.scrollHeight, document.documentElement.scrollHeight).toString()"\n                        ) { result ->\n                            val cssHeight = result?.trim()?.trim('\\"')?.toDoubleOrNull()\n                                ?: return@evaluateJavascript\n                            val density = currentView.resources.displayMetrics.density\n                            heightDp = ((cssHeight * density).roundToInt() / density).coerceAtLeast(1f)\n                        }\n                    }\n                }\n            }\n        },\n        update = { webView ->\n            if (webView.tag != html) {\n                webView.tag = html\n                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)\n            }\n        },\n    )\n}\n\n@Composable\nfun AdaptiveAnimatedText'''
replace_once(animated, old_block, new_block)

print('review filters and useweb rendering patched')
