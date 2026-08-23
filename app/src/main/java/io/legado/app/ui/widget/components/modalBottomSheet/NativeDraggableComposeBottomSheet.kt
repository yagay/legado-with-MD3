package io.legado.app.ui.widget.components.modalBottomSheet

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import kotlin.math.abs

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
            var sheetAnimator: ValueAnimator? = null
            var parentHeight = 0
            var gestureStartY = 0f
            var gestureStartTop = 0
            var lastHandleY = 0f
            var lastDirection = 0
            val directionSlop = dp(2).toFloat()
            val dismissDistance = dp(72)

            fun refreshGeometry(sheet: View, behavior: BottomSheetBehavior<View>) {
                val parent = sheet.parent as? View ?: return
                if (parent.height <= 0) return
                parentHeight = parent.height
                parent.layoutParams = parent.layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                sheet.layoutParams = sheet.layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                root.layoutParams = root.layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, 0)
                behavior.isFitToContents = false
                behavior.expandedOffset = 0
                behavior.skipCollapsed = true
                behavior.isHideable = false
                behavior.isDraggable = false
                parent.requestLayout()
                sheet.requestLayout()
                root.requestLayout()
            }

            fun animateBackToExpanded() {
                val sheet = bottomSheetView ?: return
                sheetAnimator?.cancel()
                if (sheet.top == 0) {
                    root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, 0)
                    sheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
                    return
                }
                var cancelled = false
                sheetAnimator = ValueAnimator.ofInt(sheet.top, 0).apply {
                    duration = 180L
                    addUpdateListener { animator ->
                        val targetTop = animator.animatedValue as Int
                        sheet.offsetTopAndBottom(targetTop - sheet.top)
                        root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, targetTop)
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationCancel(animation: Animator) {
                            cancelled = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (cancelled) return
                            root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, 0)
                            sheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
                        }
                    })
                    start()
                }
            }

            val dragHandleHost = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(32),
                )
                isClickable = true
                setOnTouchListener { view, event ->
                    val sheet = bottomSheetView
                    val behavior = sheetBehavior
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            sheetAnimator?.cancel()
                            if (sheet != null && behavior != null) {
                                refreshGeometry(sheet, behavior)
                            }
                            gestureStartY = event.rawY
                            lastHandleY = event.rawY
                            gestureStartTop = sheet?.top ?: 0
                            lastDirection = 0
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val deltaStep = event.rawY - lastHandleY
                            lastHandleY = event.rawY
                            if (abs(deltaStep) >= directionSlop) {
                                lastDirection = if (deltaStep > 0f) 1 else -1
                            }
                            if (sheet != null) {
                                val actualParentHeight = (sheet.parent as? View)?.height
                                    ?.takeIf { it > 0 }
                                    ?: parentHeight.takeIf { it > 0 }
                                    ?: context.resources.displayMetrics.heightPixels
                                val targetTop = (
                                    gestureStartTop + (event.rawY - gestureStartY).toInt()
                                ).coerceIn(0, actualParentHeight)
                                sheet.offsetTopAndBottom(targetTop - sheet.top)
                                root.setPadding(
                                    root.paddingLeft,
                                    root.paddingTop,
                                    root.paddingRight,
                                    targetTop,
                                )
                            }
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            val releaseTop = sheet?.top ?: gestureStartTop
                            if (lastDirection > 0 && releaseTop >= dismissDistance) {
                                dialogRef?.dismiss()
                            } else {
                                animateBackToExpanded()
                            }
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            view.performClick()
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            animateBackToExpanded()
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
                window?.navigationBarColor = surfaceColor
                setCanceledOnTouchOutside(true)
                setOnDismissListener {
                    if (!disposing) currentDismiss.value.invoke()
                }
                setOnShowListener {
                    window?.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                        ?.let { bottomSheet ->
                            bottomSheetView = bottomSheet
                            bottomSheet.background = sheetBackground
                            val parent = bottomSheet.parent as? View
                            parent?.layoutParams = parent?.layoutParams?.apply {
                                height = ViewGroup.LayoutParams.MATCH_PARENT
                            }
                            val behavior = BottomSheetBehavior.from(bottomSheet).apply {
                                isFitToContents = false
                                expandedOffset = 0
                                skipCollapsed = true
                                isHideable = false
                                isDraggable = false
                            }
                            sheetBehavior = behavior

                            bottomSheet.post {
                                refreshGeometry(bottomSheet, behavior)
                                if (bottomSheet.top != 0) {
                                    bottomSheet.offsetTopAndBottom(-bottomSheet.top)
                                }
                                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                            }

                            parent?.addOnLayoutChangeListener {
                                    _, _, _, _, _, _, _, _, _ ->
                                refreshGeometry(bottomSheet, behavior)
                                if (bottomSheet.top != 0) {
                                    bottomSheet.offsetTopAndBottom(-bottomSheet.top)
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
