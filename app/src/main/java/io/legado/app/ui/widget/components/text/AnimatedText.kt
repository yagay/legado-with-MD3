package io.legado.app.ui.widget.components.text

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

private const val USE_WEB_PREFIX = "<useweb>"

@Composable
fun AnimatedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in text.indices) {
            val char = text[i]
            Box {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically(initialOffsetY = { it })).togetherWith(
                            slideOutVertically(targetOffsetY = { -it })
                        )
                    },
                    label = ""
                ) {
                    AppText(
                        style = style,
                        color = color,
                        softWrap = softWrap,
                        text = it.toString(),
                        fontSize = fontSize,
                        fontStyle = fontStyle,
                        fontWeight = fontWeight,
                        fontFamily = fontFamily,
                        letterSpacing = letterSpacing,
                        textDecoration = textDecoration,
                        textAlign = textAlign,
                        lineHeight = lineHeight,
                        overflow = overflow,
                        maxLines = maxLines,
                        minLines = minLines,
                        onTextLayout = onTextLayout
                    )
                }
            }
        }
    }
}

/**
 * 动画文本控件
 *
 * 区别于 AnimatedText, 该控件在文本变化时，提供整行的滑动动画效果。
 * Legado 的书籍详情简介允许以 <useweb> 包裹 HTML；这里保留普通文本行为，
 * 仅在收到该显式协议时切换到 WebView 渲染。
 */
@Composable
fun AnimatedTextLine(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { it }).togetherWith(slideOutVertically { -it })
        },
        label = "LineAnimation",
        modifier = modifier
    ) { targetText ->
        if (targetText.startsWith(USE_WEB_PREFIX, ignoreCase = true)) {
            UseWebText(
                text = targetText,
                modifier = modifier,
            )
        } else {
            AppText(
                text = targetText,
                modifier = modifier,
                style = style,
                color = color,
                softWrap = softWrap,
                fontSize = fontSize,
                fontStyle = fontStyle,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textDecoration = textDecoration,
                textAlign = textAlign,
                lineHeight = lineHeight,
                overflow = overflow,
                maxLines = maxLines,
                minLines = minLines,
                onTextLayout = onTextLayout
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun UseWebText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val html = remember(text) { extractUseWebBody(text) }
    var heightDp by remember(text) { mutableFloatStateOf(1f) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                isNestedScrollingEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.defaultTextEncodingName = "utf-8"
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(
                            "Math.max(document.body.scrollHeight, document.documentElement.scrollHeight).toString()"
                        ) { result ->
                            val cssHeight = result
                                ?.trim()
                                ?.trim('"')
                                ?.toDoubleOrNull()
                                ?: return@evaluateJavascript
                            val density = view.resources.displayMetrics.density
                            heightDp = ((cssHeight * density).roundToInt() / density)
                                .coerceAtLeast(1f)
                        }
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(
                    null,
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}

private fun extractUseWebBody(text: String): String {
    if (!text.startsWith(USE_WEB_PREFIX, ignoreCase = true)) return text
    val lastIndex = text.lastIndexOf("<")
    return if (lastIndex >= USE_WEB_PREFIX.length) {
        text.substring(USE_WEB_PREFIX.length, lastIndex)
    } else {
        text
    }
}

@Composable
fun AdaptiveAnimatedText(
    text: String,
    useCharMode: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    AnimatedContent(
        targetState = useCharMode,
        transitionSpec = {
            (slideInVertically { it } + fadeIn()).togetherWith(
                slideOutVertically { -it } + fadeOut()
            )
        },
        label = "ModeSwitchAnimation",
        modifier = modifier
    ) { currentMode ->
        if (currentMode) {
            AnimatedText(
                text = text,
                color = color,
                fontSize = fontSize,
                fontStyle = fontStyle,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textDecoration = textDecoration,
                textAlign = textAlign,
                lineHeight = lineHeight,
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                minLines = minLines,
                onTextLayout = onTextLayout,
                style = style
            )
        } else {
            AnimatedTextLine(
                text = text,
                color = color,
                fontSize = fontSize,
                fontStyle = fontStyle,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textDecoration = textDecoration,
                textAlign = textAlign,
                lineHeight = lineHeight,
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                minLines = minLines,
                onTextLayout = onTextLayout,
                style = style
            )
        }
    }
}
