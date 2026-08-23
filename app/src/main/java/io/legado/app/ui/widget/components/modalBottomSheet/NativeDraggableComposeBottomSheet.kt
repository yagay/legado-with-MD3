package io.legado.app.ui.widget.components.modalBottomSheet

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * Android 平台统一原生拖拽底栏。
 *
 * 登录、类别选择以及导航层二级页面都通过这一底层宿主展示，
 * 统一拖动、系统栏、背景和遮罩行为。
 */
@Composable
fun NativeDraggableComposeBottomSheet(
    show: Boolean,
    title: String?,
    onDismissRequest: () -> Unit,
    scrimColor: Color = Color.Black.copy(alpha = 0.4f),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    gesturesEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val surfaceColor = containerColor.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val scrimColorArgb = scrimColor.toArgb()
    val parentComposition = rememberCompositionContext()
    val currentContent = rememberUpdatedState(content)
    val currentDismiss = rememberUpdatedState(onDismissRequest)

    DisposableEffect(
        show,
        title,
        density,
        surfaceColor,
        onSurfaceColor,
        onSurfaceVariantColor,
        scrimColorArgb,
        gesturesEnabled,
    ) {
        if (!show) {
            onDispose { }
        } else {
            var disposing = false
            fun dp(value: Int) = (value * density).toInt()

            val sheetBackground = GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadii = floatArrayOf(
                    dp(28).toFloat(), dp(28).toFloat(),
                    dp(28).toFloat(), dp(28).toFloat(),
                    0f, 0f, 0f, 0f,
                )
            }

            lateinit var overlay: SimpleDraggableOverlay
            overlay = SimpleDraggableOverlay(
                context = context,
                scrimColor = scrimColorArgb,
                onDismiss = {
                    if (!disposing) currentDismiss.value.invoke()
                }
            )

            val sheetRoot = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = sheetBackground
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            overlay.panel.addView(sheetRoot)

            val directionSlop = dp(2).toFloat()
            var gestureStartY = 0f
            var gestureStartTranslation = 0f
            var lastY = 0f
            var lastDirection = 0

            val dragHandleHost = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(32),
                )
                isClickable = gesturesEnabled
                if (gesturesEnabled) {
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                overlay.cancelAnimation()
                                gestureStartY = event.rawY
                                gestureStartTranslation = overlay.panel.translationY
                                lastY = event.rawY
                                lastDirection = 0
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                true
                            }

                            MotionEvent.ACTION_MOVE -> {
                                val step = event.rawY - lastY
                                lastY = event.rawY
                                if (abs(step) >= directionSlop) {
                                    lastDirection = if (step > 0f) 1 else -1
                                }
                                overlay.moveTo(gestureStartTranslation + event.rawY - gestureStartY)
                                true
                            }

                            MotionEvent.ACTION_UP -> {
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                                overlay.settle(lastDirection)
                                view.performClick()
                                true
                            }

                            MotionEvent.ACTION_CANCEL -> {
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                                overlay.settle(-1)
                                true
                            }

                            else -> true
                        }
                    }
                }
            }

            val dragHandle = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(onSurfaceVariantColor)
                    cornerRadius = dp(2).toFloat()
                }
                alpha = if (gesturesEnabled) 0.45f else 0.25f
            }
            dragHandleHost.addView(
                dragHandle,
                FrameLayout.LayoutParams(dp(32), dp(4), Gravity.CENTER),
            )
            sheetRoot.addView(dragHandleHost)

            if (!title.isNullOrEmpty()) {
                val titleView = TextView(context).apply {
                    text = title
                    textSize = 18f
                    setTextColor(onSurfaceColor)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(16), 0, dp(16), dp(8))
                }
                sheetRoot.addView(
                    titleView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(48),
                    ),
                )
            }

            val contentNestedScrollConnection = object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (!gesturesEnabled) return Offset.Zero
                    if (overlay.panel.translationY > 0f && available.y < 0f) {
                        return Offset(0f, overlay.moveBy(available.y))
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!gesturesEnabled) return Offset.Zero
                    if (available.y > 0f) {
                        return Offset(0f, overlay.moveBy(available.y))
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!gesturesEnabled) return Velocity.Zero
                    if (overlay.panel.translationY > 0f) {
                        overlay.settle(if (available.y > 0f) 1 else -1)
                        return available
                    }
                    return Velocity.Zero
                }
            }

            val composeView = ComposeView(context).apply {
                setParentCompositionContext(parentComposition)
                setContent {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(contentNestedScrollConnection),
                    ) {
                        currentContent.value.invoke()
                    }
                }
            }
            sheetRoot.addView(
                composeView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )

            overlay.show()

            onDispose {
                disposing = true
                composeView.disposeComposition()
                overlay.dispose()
            }
        }
    }
}