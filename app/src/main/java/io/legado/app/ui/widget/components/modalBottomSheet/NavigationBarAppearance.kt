package io.legado.app.ui.widget.components.modalBottomSheet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun applyHostNavigationBarAppearance(
    context: Context,
    targetWindow: Window,
    fallbackColor: Int,
) {
    val hostWindow = context.findActivity()?.window

    targetWindow.navigationBarColor = hostWindow?.navigationBarColor ?: fallbackColor

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        targetWindow.navigationBarDividerColor =
            hostWindow?.navigationBarDividerColor ?: targetWindow.navigationBarColor
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        targetWindow.isNavigationBarContrastEnforced = false
    }

    val lightNavigationBar = when {
        hostWindow == null -> false
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            hostWindow.insetsController
                ?.systemBarsAppearance
                ?.and(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS) != 0
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            hostWindow.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR != 0
        }

        else -> false
    }

    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            targetWindow.insetsController?.setSystemBarsAppearance(
                if (lightNavigationBar) {
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                } else {
                    0
                },
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            targetWindow.decorView.systemUiVisibility = if (lightNavigationBar) {
                targetWindow.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                targetWindow.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        }
    }
}

@Composable
internal fun SyncDialogNavigationBarAppearance(fallbackColor: Int) {
    val context = LocalContext.current
    val view = LocalView.current

    SideEffect {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        applyHostNavigationBarAppearance(context, dialogWindow, fallbackColor)
    }
}
