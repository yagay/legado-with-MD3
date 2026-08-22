package io.legado.app.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

/**
 * Temporary login implementation that reuses the same AppModalBottomSheet container
 * used by the modern Explore source-name long-press preview, while keeping a fresh,
 * minimal WebView implementation instead of the legacy SourceLoginWebSheet content.
 */
@SuppressLint("SetJavaScriptEnabled", "WebViewClientOnReceivedSslError")
@Composable
fun SourceLoginWebDialog(
    state: SourceLoginUiState,
    onIntent: (SourceLoginIntent) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    AppModalBottomSheet(
        show = state.mode == SourceLoginMode.Web && !state.loading,
        onDismissRequest = { onIntent(SourceLoginIntent.Back) },
        title = state.title,
        containerColor = LegadoTheme.colorScheme.background,
        endAction = {
            MediumTonalButton(
                icon = AppIcons.Check,
                contentDescription = stringResource(R.string.ok),
                onClick = { onIntent(SourceLoginIntent.Confirm) },
            )
        },
        contentPaddingEnabled = false,
        sheetGesturesEnabled = false,
    ) {
        StandaloneLoginWebView(
            state = state,
            onIntent = onIntent,
            onOpenExternalUrl = onOpenExternalUrl,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 520.dp),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled", "WebViewClientOnReceivedSslError")
@Composable
private fun StandaloneLoginWebView(
    state: SourceLoginUiState,
    onIntent: (SourceLoginIntent) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIntent by rememberUpdatedState(onIntent)
    val currentOpenExternalUrl by rememberUpdatedState(onOpenExternalUrl)
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    state.headers[AppConst.UA_NAME]?.let { settings.userAgentString = it }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            url?.let { currentIntent(SourceLoginIntent.WebPageStarted(it)) }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            url?.let { currentIntent(SourceLoginIntent.WebPageFinished(it)) }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = handleUrl(request.url)

                        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                            handleUrl(Uri.parse(url))

                        private fun handleUrl(uri: Uri): Boolean {
                            if (uri.scheme == "http" || uri.scheme == "https") return false
                            currentOpenExternalUrl(uri.toString())
                            return true
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?,
                        ) {
                            handler?.proceed()
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, progress: Int) {
                            currentIntent(SourceLoginIntent.WebProgressChanged(progress))
                        }
                    }

                    state.webUrl?.let { loadUrl(it, state.headers) }
                    webView = this
                }
            },
            update = {},
        )

        if (state.webProgress in 0..99) {
            LinearProgressIndicator(
                progress = { state.webProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    LaunchedEffect(state.checkingCookie) {
        if (state.checkingCookie) {
            state.webUrl?.let { webView?.loadUrl(it, state.headers) }
        }
    }
}
