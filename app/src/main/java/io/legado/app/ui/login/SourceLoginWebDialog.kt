package io.legado.app.ui.login

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.http.SslError
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
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
import io.legado.app.ui.widget.components.modalBottomSheet.applyHostNavigationBarAppearance
import kotlin.math.abs

/**
 * Native WebView login/browser bottom sheet.
 *
 * The sheet opens fully expanded. There is no intermediate collapsed anchor. When the WebView is
 * already at the top, continuing to pull down transfers the gesture to the sheet so it follows the
 * finger and then either dismisses or returns to the expanded position.
 */
@SuppressLint("SetJavaScriptEnabled", "WebViewClientOnReceivedSslError")
@Composable
fun SourceLoginWebDialog(
    state: SourceLoginUiState,
    onIntent: (SourceLoginIntent) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    enableContentSheetDrag: Boolean = true,
) {
    val context = LocalContext.current
    val currentIntent by rememberUpdatedState(onIntent)
    val currentOpenExternalUrl by rememberUpdatedState(onOpenExternalUrl)
    val show = state.mode == SourceLoginMode.Web && !state.loading
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(show, enableContentSheetDrag) {
        if (!show) {
            onDispose { }
        } else {
            var disposing = false
            val density = context.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()

            fun resolveColor(attr: Int, fallback: Int): Int {
                val value = TypedValue()
                return if (context.theme.resolveAttribute(attr, value, true)) {
                    if (value.resourceId != 0) {
                        runCatching { context.getColor(value.resourceId) }.getOrDefault(value.data)
                    } else value.data
                } else fallback
            }

            val surfaceColor = resolveColor(android.R.attr.colorBackground, Color.WHITE)
            val onSurfaceColor = resolveColor(android.R.attr.textColorPrimary, Color.BLACK)
            val onSurfaceVariantColor = resolveColor(android.R.attr.textColorSecondary, onSurfaceColor)
            val accentColor = resolveColor(android.R.attr.colorAccent, onSurfaceColor)
            val sheetBackground = GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadii = floatArrayOf(
                    dp(28).toFloat(), dp(28).toFloat(),
                    dp(28).toFloat(), dp(28).toFloat(),
                    0f, 0f, 0f, 0f,
                )
            }

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = sheetBackground
                clipToPadding = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }

            var sheetBehavior: BottomSheetBehavior<View>? = null
            var bottomSheetView: View? = null
            var dialogRef: BottomSheetDialog? = null
            var sheetAnimator: ValueAnimator? = null
            var parentHeight = 0
            var manualSheetMotion = false
            val directionSlop = dp(2).toFloat()
            val dismissDistance = dp(72)

            fun refreshGeometry() {
                val sheet = bottomSheetView ?: return
                val parent = sheet.parent as? View ?: return
                if (parent.height <= 0) return
                parentHeight = parent.height

                sheet.layoutParams = sheet.layoutParams.apply {
                    height = parentHeight
                }
                root.layoutParams = root.layoutParams.apply {
                    height = parentHeight
                }
                sheetBehavior?.apply {
                    isFitToContents = false
                    expandedOffset = 0
                    skipCollapsed = true
                    isHideable = false
                    isDraggable = false
                }
                sheet.requestLayout()
                root.requestLayout()
            }

            fun animateSheetTo(targetTop: Int, dismissAtEnd: Boolean = false) {
                val sheet = bottomSheetView ?: return
                sheetAnimator?.cancel()
                manualSheetMotion = true
                val safeTarget = targetTop.coerceIn(0, parentHeight.coerceAtLeast(targetTop))
                if (sheet.top == safeTarget) {
                    if (dismissAtEnd) {
                        manualSheetMotion = false
                        dialogRef?.dismiss()
                    } else {
                        sheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
                        manualSheetMotion = false
                    }
                    return
                }
                var cancelled = false
                sheetAnimator = ValueAnimator.ofInt(sheet.top, safeTarget).apply {
                    duration = 220L
                    addUpdateListener { animator ->
                        val nextTop = animator.animatedValue as Int
                        sheet.offsetTopAndBottom(nextTop - sheet.top)
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationCancel(animation: Animator) {
                            cancelled = true
                            manualSheetMotion = false
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (cancelled) return
                            if (dismissAtEnd) {
                                manualSheetMotion = false
                                dialogRef?.dismiss()
                            } else {
                                sheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
                                manualSheetMotion = false
                            }
                        }
                    })
                    start()
                }
            }

            fun settleDrag(direction: Int) {
                val sheet = bottomSheetView ?: return
                manualSheetMotion = true
                refreshGeometry()
                if (direction > 0 && sheet.top >= dismissDistance) {
                    animateSheetTo(parentHeight, dismissAtEnd = true)
                } else {
                    animateSheetTo(0)
                }
            }

            var topGestureStartY = 0f
            var topGestureLastY = 0f
            var topGestureStartTop = 0
            var topGestureDirection = 0

            val topDragListener = View.OnTouchListener { view, event ->
                val sheet = bottomSheetView
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sheetAnimator?.cancel()
                        manualSheetMotion = true
                        refreshGeometry()
                        topGestureStartY = event.rawY
                        topGestureLastY = event.rawY
                        topGestureStartTop = sheet?.top ?: 0
                        topGestureDirection = 0
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val delta = event.rawY - topGestureLastY
                        topGestureLastY = event.rawY
                        if (abs(delta) >= directionSlop) {
                            topGestureDirection = if (delta > 0f) 1 else -1
                        }
                        sheet?.let { movingSheet ->
                            val requestedTop = (topGestureStartTop + event.rawY - topGestureStartY).toInt()
                            val targetTop = requestedTop.coerceIn(0, parentHeight)
                            movingSheet.offsetTopAndBottom(targetTop - movingSheet.top)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        settleDrag(topGestureDirection)
                        view.performClick()
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        animateSheetTo(0)
                        true
                    }

                    else -> true
                }
            }

            val dragHandleHost = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28))
                isClickable = true
                setOnTouchListener(topDragListener)
            }
            val dragHandle = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(onSurfaceVariantColor)
                    cornerRadius = dp(2).toFloat()
                }
                alpha = 0.45f
            }
            dragHandleHost.addView(dragHandle, FrameLayout.LayoutParams(dp(32), dp(4), Gravity.CENTER))

            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(12), dp(8))
                isClickable = true
                setOnTouchListener(topDragListener)
            }
            val titleView = TextView(context).apply {
                text = state.title
                textSize = 18f
                setTextColor(onSurfaceColor)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(56), 0, 0, 0)
                isClickable = false
            }
            val confirmButton = Button(context).apply {
                text = context.getString(android.R.string.ok)
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(16), 0, dp(16), 0)
                background = GradientDrawable().apply {
                    setColor(accentColor)
                    cornerRadius = dp(20).toFloat()
                }
                setTextColor(surfaceColor)
                setOnClickListener { currentIntent(SourceLoginIntent.Confirm) }
            }
            header.addView(titleView, LinearLayout.LayoutParams(0, dp(48), 1f))
            header.addView(confirmButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))

            val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = state.webProgress.coerceIn(0, 100)
                progressTintList = ColorStateList.valueOf(accentColor)
                visibility = if (state.webProgress in 0..99) View.VISIBLE else View.GONE
            }

            var contentGestureStartY = 0f
            var contentGestureLastY = 0f
            var contentGestureStartTop = 0
            var contentLastDirection = 0
            var contentDraggingSheet = false

            val nativeWebView = object : WebView(context) {
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (!enableContentSheetDrag) return super.onTouchEvent(event)
                    val sheet = bottomSheetView
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            sheetAnimator?.cancel()
                            contentGestureStartY = event.rawY
                            contentGestureLastY = event.rawY
                            contentGestureStartTop = sheet?.top ?: 0
                            contentLastDirection = 0
                            contentDraggingSheet = false
                            return super.onTouchEvent(event)
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val step = event.rawY - contentGestureLastY
                            contentGestureLastY = event.rawY
                            if (abs(step) >= directionSlop) {
                                contentLastDirection = if (step > 0f) 1 else -1
                            }

                            if (!contentDraggingSheet && sheet != null) {
                                val total = event.rawY - contentGestureStartY
                                val contentAtTop = scrollY <= 0 || !canScrollVertically(-1)
                                val draggingDownFromTop = total > directionSlop && contentAtTop
                                if (draggingDownFromTop) {
                                    val cancel = MotionEvent.obtain(event).apply {
                                        action = MotionEvent.ACTION_CANCEL
                                    }
                                    super.onTouchEvent(cancel)
                                    cancel.recycle()
                                    manualSheetMotion = true
                                    refreshGeometry()
                                    contentDraggingSheet = true
                                    contentGestureStartY = event.rawY
                                    contentGestureStartTop = sheet.top
                                    contentGestureLastY = event.rawY
                                    parent?.requestDisallowInterceptTouchEvent(true)
                                    return true
                                }
                            }

                            if (contentDraggingSheet && sheet != null) {
                                val requestedTop = (
                                    contentGestureStartTop + event.rawY - contentGestureStartY
                                ).toInt()
                                val targetTop = requestedTop.coerceIn(0, parentHeight)
                                sheet.offsetTopAndBottom(targetTop - sheet.top)
                                return true
                            }
                        }

                        MotionEvent.ACTION_UP -> {
                            if (contentDraggingSheet) {
                                contentDraggingSheet = false
                                parent?.requestDisallowInterceptTouchEvent(false)
                                settleDrag(contentLastDirection)
                                performClick()
                                return true
                            }
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            if (contentDraggingSheet) {
                                contentDraggingSheet = false
                                parent?.requestDisallowInterceptTouchEvent(false)
                                animateSheetTo(0)
                                return true
                            }
                        }
                    }
                    return super.onTouchEvent(event)
                }
            }.apply {
                setBackgroundColor(surfaceColor)
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
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
                        progress.visibility = if (newProgress in 0..99) View.VISIBLE else View.GONE
                        currentIntent(SourceLoginIntent.WebProgressChanged(newProgress))
                    }
                }
                state.webUrl?.let { loadUrl(it, state.headers) }
            }
            webView = nativeWebView

            root.addView(dragHandleHost)
            root.addView(
                header,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            root.addView(
                progress,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)),
            )
            root.addView(
                nativeWebView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )

            val dialog = BottomSheetDialog(context).apply {
                setContentView(root)
                window?.let { dialogWindow ->
                    dialogWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                    applyHostNavigationBarAppearance(context, dialogWindow, surfaceColor)
                }
                setCanceledOnTouchOutside(true)
                setOnDismissListener {
                    if (!disposing) currentIntent(SourceLoginIntent.Back)
                }
                setOnShowListener {
                    window?.let { dialogWindow ->
                        applyHostNavigationBarAppearance(context, dialogWindow, surfaceColor)
                    }
                    findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                        ?.let { bottomSheet ->
                            bottomSheetView = bottomSheet
                            bottomSheet.background = sheetBackground
                            val behavior = BottomSheetBehavior.from(bottomSheet).apply {
                                isFitToContents = false
                                expandedOffset = 0
                                skipCollapsed = true
                                isHideable = false
                                isDraggable = false
                            }
                            sheetBehavior = behavior
                            bottomSheet.post {
                                refreshGeometry()
                                if (bottomSheet.top != 0) {
                                    bottomSheet.offsetTopAndBottom(-bottomSheet.top)
                                }
                                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                            }
                            (bottomSheet.parent as? View)?.addOnLayoutChangeListener {
                                    _, _, _, _, _, _, _, _, _ ->
                                if (!manualSheetMotion) {
                                    refreshGeometry()
                                    if (bottomSheet.top != 0) {
                                        bottomSheet.offsetTopAndBottom(-bottomSheet.top)
                                    }
                                }
                            }
                        }
                }
                show()
            }
            dialogRef = dialog

            onDispose {
                disposing = true
                sheetAnimator?.cancel()
                sheetAnimator = null
                manualSheetMotion = false
                sheetBehavior = null
                bottomSheetView = null
                dialogRef = null
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
