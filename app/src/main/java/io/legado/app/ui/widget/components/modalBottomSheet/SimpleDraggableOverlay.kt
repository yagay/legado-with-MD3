package io.legado.app.ui.widget.components.modalBottomSheet

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * A small reusable sheet host that stays inside the current Activity window.
 *
 * Unlike BottomSheetDialog it never creates a second Window, so opening the sheet cannot replace
 * or dim the system navigation-bar window. The sheet itself is just a full-height panel translated
 * on Y while dragging.
 */
internal class SimpleDraggableOverlay(
    context: Context,
    private val onDismiss: () -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val dismissDistance = 72f * density
    private var animator: ValueAnimator? = null
    private var dismissed = false

    private val activity = context.findActivity()
    private val componentActivity = activity as? ComponentActivity
    private val activityContent = activity?.findViewById<ViewGroup>(android.R.id.content)

    val root = FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        isClickable = true
    }

    private val scrim = View(context).apply {
        setBackgroundColor(Color.argb(96, 0, 0, 0))
        isClickable = true
        setOnClickListener { dismiss() }
    }

    val panel = FrameLayout(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.BOTTOM,
        )
        isClickable = true
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            dismiss()
        }
    }

    init {
        root.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(panel)
    }

    fun show() {
        val parent = activityContent ?: return
        if (root.parent == null) {
            parent.addView(root)
        }
        componentActivity?.onBackPressedDispatcher?.addCallback(componentActivity, backCallback)
        root.post {
            val distance = root.height.takeIf { it > 0 }
                ?: context.resources.displayMetrics.heightPixels
            panel.translationY = distance.toFloat()
            scrim.alpha = 0f
            animateTo(0f, dismissAtEnd = false, duration = 220L)
        }
    }

    fun cancelAnimation() {
        animator?.cancel()
        animator = null
    }

    fun moveBy(deltaY: Float): Float {
        if (deltaY == 0f || dismissed) return 0f
        cancelAnimation()
        val max = root.height.takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels
        val old = panel.translationY
        val next = (old + deltaY).coerceIn(0f, max.toFloat())
        panel.translationY = next
        updateScrim(next, max)
        return next - old
    }

    fun moveTo(translationY: Float) {
        if (dismissed) return
        cancelAnimation()
        val max = root.height.takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels
        val next = translationY.coerceIn(0f, max.toFloat())
        panel.translationY = next
        updateScrim(next, max)
    }

    fun settle(lastDirection: Int = 0) {
        if (dismissed) return
        val shouldDismiss = panel.translationY >= dismissDistance && lastDirection >= 0
        if (shouldDismiss) {
            animateDismiss()
        } else {
            animateTo(0f, dismissAtEnd = false, duration = 180L)
        }
    }

    fun dismiss() {
        if (dismissed) return
        animateDismiss()
    }

    fun dispose() {
        dismissed = true
        cancelAnimation()
        backCallback.remove()
        (root.parent as? ViewGroup)?.removeView(root)
    }

    private fun animateDismiss() {
        val target = (root.height.takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels).toFloat()
        animateTo(target, dismissAtEnd = true, duration = 200L)
    }

    private fun animateTo(target: Float, dismissAtEnd: Boolean, duration: Long) {
        if (dismissed) return
        cancelAnimation()
        val start = panel.translationY
        if (start == target) {
            if (dismissAtEnd) finishDismiss()
            return
        }
        val max = root.height.takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels
        var cancelled = false
        animator = ValueAnimator.ofFloat(start, target).apply {
            this.duration = duration
            addUpdateListener { valueAnimator ->
                val y = valueAnimator.animatedValue as Float
                panel.translationY = y
                updateScrim(y, max)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled && dismissAtEnd) finishDismiss()
                }
            })
            start()
        }
    }

    private fun updateScrim(translationY: Float, max: Int) {
        if (max <= 0) return
        scrim.alpha = (1f - translationY / max.toFloat()).coerceIn(0f, 1f)
    }

    private fun finishDismiss() {
        if (dismissed) return
        dismissed = true
        cancelAnimation()
        backCallback.remove()
        (root.parent as? ViewGroup)?.removeView(root)
        onDismiss()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
