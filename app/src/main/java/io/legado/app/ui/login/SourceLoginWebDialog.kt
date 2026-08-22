package io.legado.app.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.constant.AppConst

/**
 * Native WebView login bottom sheet.
 *
 * This intentionally avoids Compose ModalBottomSheet + AndroidView(WebView). The WebView is
 * attached directly to an Android BottomSheetDialog so its fixed/transform layers and touch
 * handling are not affected by Compose layout animation, clipping or pointer interop.
 */
@SuppressLint("SetJavaScriptEnabled", "WebViewClientOnReceivedSslError")
@Composable
fun SourceLoginWebDialog(
    state: SourceLoginUiState,
    onIntent: (SourceLoginIntent) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val currentIntent by rememberUpdatedState(onIntent)
    val currentOpenExternalUrl by rememberUpdatedState(onOpenExternalUrl)
    val show = state.mode == SourceLoginMode.Web && !state.loading
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(show) {
        if (!show) {
            onDispose { }
        } else {
            var disposing = false
            val density = context.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }

            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(12), dp(8))
            }
            val titleView = TextView(context).apply {
                text = state.title
                textSize = 18f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val confirmButton = Button(context).apply {
                text = context.getString(android.R.string.ok)
                setOnClickListener { currentIntent(SourceLoginIntent.Confirm) }
            }
            header.addView(
                titleView,
                LinearLayout.LayoutParams(0, dp(48), 1f),
            )
            header.addView(
                confirmButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(48),
                ),
            )

            val progress = ProgressBar(
                context,
                null,
                android.R.attr.progressBarStyleHorizontal,
            ).apply {
                max = 100
                progress = state.webProgress.coerceIn(0, 100)
                visibility = if (state.webProgress in 0..99) android.view.View.VISIBLE
                else android.view.View.GONE
            }

            val nativeWebView = WebView(context).apply {
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
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        progress.progress = newProgress
                        progress.visibility = if (newProgress in 0..99) android.view.View.VISIBLE
                        else android.view.View.GONE
                        currentIntent(SourceLoginIntent.WebProgressChanged(newProgress))
                    }
                }

                state.webUrl?.let { loadUrl(it, state.headers) }
            }
            webView = nativeWebView

            root.addView(
                header,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            root.addView(
                progress,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(3),
                ),
            )
            root.addView(
                nativeWebView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(560),
                ),
            )

            val dialog = BottomSheetDialog(context).apply {
                setContentView(root)
                setCanceledOnTouchOutside(true)
                setOnDismissListener {
                    if (!disposing) currentIntent(SourceLoginIntent.Back)
                }
                setOnShowListener {
                    findViewById<android.view.View>(
                        com.google.android.material.R.id.design_bottom_sheet
                    )?.let { bottomSheet ->
                        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                            height = (context.resources.displayMetrics.heightPixels * 0.85f).toInt()
                        }
                        BottomSheetBehavior.from(bottomSheet).apply {
                            this.state = BottomSheetBehavior.STATE_EXPANDED
                            skipCollapsed = true
                            isDraggable = false
                        }
                    }
                }
                show()
            }

            onDispose {
                disposing = true
                webView = null
                nativeWebView.stopLoading()
                nativeWebView.webChromeClient = null
                nativeWebView.webViewClient = WebViewClient()
                nativeWebView.destroy()
                dialog.setOnDismissListener(null)
                dialog.dismiss()
            }
        }
    }

    LaunchedEffect(state.checkingCookie) {
        if (state.checkingCookie) {
            state.webUrl?.let { webView?.loadUrl(it, state.headers) }
        }
    }
}
