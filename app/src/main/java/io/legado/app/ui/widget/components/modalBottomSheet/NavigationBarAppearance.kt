package io.legado.app.ui.widget.components.modalBottomSheet

import android.content.Context
import android.view.Window
import androidx.compose.runtime.Composable

/**
 * MD3 upstream does not override navigation-bar appearance from bottom sheets.
 *
 * Keep these compatibility hooks as no-ops so existing custom sheet callers do not
 * rewrite the host navigation bar when a dialog is shown.
 */
@Suppress("UNUSED_PARAMETER")
internal fun applyHostNavigationBarAppearance(
    context: Context,
    targetWindow: Window,
    fallbackColor: Int,
) = Unit

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun SyncDialogNavigationBarAppearance(fallbackColor: Int) = Unit
