package io.legado.app.ui.widget.components.modalBottomSheet

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

@Composable
fun NativeDraggableComposeBottomSheet(
    show: Boolean,
    title: String?,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val parentComposition = rememberCompositionContext()
    val currentContent = rememberUpdatedState(content)
    val currentDismiss = rememberUpdatedState(onDismissRequest)

    DisposableEffect(show, title) {
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
                    } else {
                        value.data
                    }
                } else {
                    fallback
                }
            }

            val surfaceColor = resolveColor(android.R.attr.colorBackground, Color.WHITE)
            val onSurfaceColor = resolveColor(android.R.attr.textColorPrimary, Color.BLACK)
            val onSurfaceVariantColor = resolveColor(android.R.attr.textColorSecondary, onSurfaceColor)

            val sheetBackground = GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadii = floatArrayOf(
                    dp(28).toFloat(), dp(28).toFloat(),
                    dp(28).toFloat(), dp(28).toFloat(),
                    0f, 0f,
                    0f, 0f,
                )
            }

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = sheetBackground
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }

            var sheetBehavior: BottomSheetBehavior<View>? = null
            var bottomSheetView: View? = null
            var dialogRef: BottomSheetDialog? = null
            var gestureStartY = 0f
            var gestureStartTop = 0
            var lastHandleY = 0f
            var lastDirection = 0
            var gestureStartState = BottomSheetBehavior.STATE_COLLAPSED
            val directionSlop = dp(2).toFloat()
            val releaseSlop = dp(8).toFloat()

            val dragHandleHost = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(32),
                )
                isClickable = true
                setOnTouchListener { view, event ->
                    val behavior = sheetBehavior
                    val sheet = bottomSheetView
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            behavior?.state?.let { state ->
                                if (state == BottomSheetBehavior.STATE_SETTLING ||
                                    state == BottomSheetBehavior.STATE_DRAGGING
                                ) {
                                    behavior.state = if (sheet?.top ?: 0 <= dp(8)) {
                                        BottomSheetBehavior.STATE_EXPANDED
                                    } else {
                                        BottomSheetBehavior.STATE_COLLAPSED
                                    }
                                }
                            }
                            gestureStartY = event.rawY
                            lastHandleY = event.rawY
                            gestureStartTop = sheet?.top ?: 0
                            lastDirection = 0
                            gestureStartState = when (behavior?.state) {
                                BottomSheetBehavior.STATE_EXPANDED -> BottomSheetBehavior.STATE_EXPANDED
                                else -> BottomSheetBehavior.STATE_COLLAPSED
                            }
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val deltaStep = event.rawY - lastHandleY
                            lastHandleY = event.rawY
                            if (kotlin.math.abs(deltaStep) >= directionSlop) {
                                lastDirection = if (deltaStep > 0f) 1 else -1
                            }
                            if (sheet != null) {
                                val parentHeight = (sheet.parent as? View)?.height
                                    ?: context.resources.displayMetrics.heightPixels
                                val targetTop = (
                                    gestureStartTop + (event.rawY - gestureStartY).toInt()
                                ).coerceIn(0, parentHeight)
                                sheet.offsetTopAndBottom(targetTop - sheet.top)
                            }
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            val releaseTop = sheet?.top ?: gestureStartTop
                            val collapsedTop = sheet?.let {
                                ((it.parent as? View)?.height
                                    ?: context.resources.displayMetrics.heightPixels) -
                                    (behavior?.peekHeight ?: 0)
                            } ?: 0
                            val relativeTo75 = releaseTop - collapsedTop

                            when (lastDirection) {
                                1 -> {
                                    if (relativeTo75 > releaseSlop) {
                                        dialogRef?.dismiss()
                                    } else {
                                        behavior?.state = BottomSheetBehavior.STATE_COLLAPSED
                                    }
                                }

                                -1 -> {
                                    if (relativeTo75 < -releaseSlop) {
                                        behavior?.state = BottomSheetBehavior.STATE_EXPANDED
                                    } else {
                                        behavior?.state = BottomSheetBehavior.STATE_COLLAPSED
                                    }
                                }

                                else -> behavior?.state = gestureStartState
                            }

                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            view.performClick()
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            behavior?.state = gestureStartState
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            true
                        }

                        else -> true
                    }
                }
            }

            val dragHandle = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(onSurfaceVariantColor)
                    cornerRadius = dp(2).toFloat()
                }
                alpha = 0.45f
            }
            dragHandleHost.addView(
                dragHandle,
                FrameLayout.LayoutParams(dp(32), dp(4), Gravity.CENTER),
            )
            root.addView(dragHandleHost)

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
                root.addView(
                    titleView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(48),
                    ),
                )
            }

            val composeView = ComposeView(context).apply {
                setParentCompositionContext(parentComposition)
                setContent { currentContent.value.invoke() }
            }
            root.addView(
                composeView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )

            val dialog = BottomSheetDialog(context).apply {
                setContentView(root)
                setCanceledOnTouchOutside(true)
                setOnDismissListener {
                    if (!disposing) currentDismiss.value.invoke()
                }
                setOnShowListener {
                    findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                        ?.let { bottomSheet ->
                            bottomSheetView = bottomSheet
                            bottomSheet.background = sheetBackground
                            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                                height = ViewGroup.LayoutParams.MATCH_PARENT
                            }
                            sheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
                                isFitToContents = false
                                expandedOffset = 0
                                peekHeight = (context.resources.displayMetrics.heightPixels * 0.75f).toInt()
                                skipCollapsed = false
                                isHideable = false
                                isDraggable = false
                                state = BottomSheetBehavior.STATE_COLLAPSED
                            }
                        }
                }
                show()
            }
            dialogRef = dialog

            onDispose {
                disposing = true
                sheetBehavior = null
                bottomSheetView = null
                dialogRef = null
                composeView.disposeComposition()
                dialog.setOnDismissListener(null)
                dialog.dismiss()
            }
        }
    }
}
