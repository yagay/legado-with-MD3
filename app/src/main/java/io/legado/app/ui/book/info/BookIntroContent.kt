package io.legado.app.ui.book.info

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.utils.setHtml
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin

/**
 * 书籍详情页简介渲染器。
 *
 * 保留 Legado 书源的简介协议：
 * - <useweb>...</useweb> 使用 WebView + UTF-8 + 书籍 URL 作为 base URL；
 * - <usehtml>...</usehtml> 按 HTML 渲染；
 * - <md>...</md> 按 Markdown 渲染；
 * - 其他内容保持 MD3 原来的纯文本显示。
 *
 * 该兼容层只属于书籍详情页，避免把书源协议侵入通用 AnimatedTextLine。
 */
@Composable
internal fun BookIntroContent(
    text: String,
    baseUrl: String?,
    modifier: Modifier = Modifier,
) {
    val intro = text.trimStart()
    when {
        intro.startsWith("<useweb>") -> {
            unwrapIntro(intro, 8)?.let { html ->
                BookIntroWebView(html = html, baseUrl = baseUrl, modifier = modifier)
            } ?: AnimatedTextLine(
                text = text,
                style = LegadoTheme.typography.bodyMedium,
                modifier = modifier,
            )
        }

        intro.startsWith("<usehtml>") -> {
            unwrapIntro(intro, 9)?.let { html ->
                BookIntroHtml(html = html, modifier = modifier)
            } ?: AnimatedTextLine(
                text = text,
                style = LegadoTheme.typography.bodyMedium,
                modifier = modifier,
            )
        }

        intro.startsWith("<md>") -> {
            unwrapIntro(intro, 4)?.let { markdown ->
                BookIntroMarkdown(markdown = markdown, modifier = modifier)
            } ?: AnimatedTextLine(
                text = text,
                style = LegadoTheme.typography.bodyMedium,
                modifier = modifier,
            )
        }

        else -> AnimatedTextLine(
            text = text,
            style = LegadoTheme.typography.bodyMedium,
            modifier = modifier,
        )
    }
}

private fun unwrapIntro(intro: String, prefixLength: Int): String? {
    val lastIndex = intro.lastIndexOf('<')
    return if (lastIndex >= prefixLength) intro.substring(prefixLength, lastIndex) else null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BookIntroWebView(
    html: String,
    baseUrl: String?,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    var contentHeightPx by remember(html, baseUrl) { mutableIntStateOf(1) }
    val resolvedBaseUrl = remember(baseUrl) {
        baseUrl
            ?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?.substringBefore(',')
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { contentHeightPx.coerceAtLeast(1).toDp() }),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.defaultTextEncodingName = "utf-8"
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            "(function(){return Math.max(document.body.scrollHeight,document.documentElement.scrollHeight);})()"
                        ) { value ->
                            val cssHeight = value
                                ?.trim('"')
                                ?.toDoubleOrNull()
                                ?: return@evaluateJavascript
                            val px = (cssHeight * density.density).toInt()
                            if (px > 0) contentHeightPx = px
                        }
                    }
                }
            }
        },
        update = { webView ->
            val tag = "$resolvedBaseUrl\u0000$html"
            if (webView.tag != tag) {
                webView.tag = tag
                webView.loadDataWithBaseURL(
                    resolvedBaseUrl,
                    html,
                    "text/html",
                    "utf-8",
                    resolvedBaseUrl,
                )
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        },
    )
}

@Composable
private fun BookIntroHtml(
    html: String,
    modifier: Modifier,
) {
    val textColor = LegadoTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(textColor)
                includeFontPadding = false
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setHtml(html)
        },
    )
}

@Composable
private fun BookIntroMarkdown(
    markdown: String,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val textColor = LegadoTheme.colorScheme.onSurface.toArgb()
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(GlideImagesPlugin.create(Glide.with(context)))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(textColor)
                includeFontPadding = false
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            markwon.setMarkdown(textView, markdown)
        },
    )
}
