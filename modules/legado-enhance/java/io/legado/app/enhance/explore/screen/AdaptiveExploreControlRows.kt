package io.legado.app.enhance.explore.screen

import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.enhance.explore.ui.stripWrapSymbols
import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem
import io.legado.app.ui.widget.components.explore.calculateExploreKindRows
import io.legado.app.ui.widget.dialog.BottomWebViewDialog
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Renders source-native controls with the same flex/span rules as the upstream list layout.
 * The modern layout only decides where these rows appear; sizing and item styling stay upstream.
 */
@Composable
fun AdaptiveExploreControlRows(
    controls: List<ExploreKind>,
    sourceUrl: String?,
    useCase: ExploreKindUiUseCase,
    onOpenUrl: (ExploreKind, String) -> Unit,
    onOpenLogin: (String) -> Unit,
    onRefreshKinds: () -> Unit,
    onRunAction: ((ExploreKind) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (controls.isEmpty()) return

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val visibleControls = remember(controls) {
        controls.filter { kind ->
            stripWrapSymbols(kind.title).any { it.isLetterOrDigit() }
        }
    }
    if (visibleControls.isEmpty()) return

    val rows = remember(visibleControls) {
        calculateExploreKindRows(visibleControls, maxSpan = 6)
    }
    val effectiveRunAction: (ExploreKind) -> Unit = onRunAction ?: { kind ->
        scope.launch {
            useCase.executeAction(
                action = kind.action,
                title = kind.title,
                sourceUrl = sourceUrl,
                activity = activity,
                onRefreshKinds = onRefreshKinds,
                onOpenLogin = login@{
                    val key = sourceUrl?.takeIf { it.isNotBlank() } ?: return@login false
                    activity?.runOnUiThread { onOpenLogin(key) } ?: return@login false
                    true
                },
                onShowBrowser = browser@{ url, html, preloadJs, config ->
                    val host = activity ?: return@browser false
                    val key = sourceUrl?.takeIf { it.isNotBlank() } ?: return@browser false
                    val forcedConfig = buildModernBrowserSheetConfig(
                        activity = host,
                        originalConfig = config,
                    )
                    host.runOnUiThread {
                        host.showDialogFragment(
                            BottomWebViewDialog(
                                key,
                                0,
                                url,
                                html,
                                preloadJs,
                                forcedConfig,
                            )
                        )
                    }
                    true
                },
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (kind, span) ->
                    ExploreKindMultiTypeItem(
                        kind = kind,
                        sourceUrl = sourceUrl,
                        onOpenUrl = { url -> onOpenUrl(kind, url) },
                        onRefreshKinds = onRefreshKinds,
                        modifier = Modifier.weight(span.toFloat()),
                        isMiuix = false,
                        displayNameOverride = stripWrapSymbols(kind.title),
                        onRunAction = { effectiveRunAction(kind) },
                        useCase = useCase,
                    )
                }
            }
        }
    }
}

private fun buildModernBrowserSheetConfig(
    activity: AppCompatActivity,
    originalConfig: String?,
): String {
    val config = runCatching {
        originalConfig?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
    }.getOrElse { JSONObject() }
    val collapsedHeight = (activity.resources.displayMetrics.heightPixels * 0.75f).toInt()
    return config.apply {
        put("state", BottomSheetBehavior.STATE_COLLAPSED)
        put("dialogHeight", ViewGroup.LayoutParams.MATCH_PARENT)
        put("peekHeight", collapsedHeight)
        put("setExpandedOffset", 0)
        put("setFitToContents", false)
        put("skipCollapsed", false)
        put("isHideable", true)
        put("isDraggable", true)
        put("isDraggableOnNestedScroll", true)
        put("isNestedScrollingEnabled", true)
        put("isGestureInsetBottomIgnored", true)
        put("scrollNoDraggable", true)
        put("dismissOnTouchOutside", true)
        put("expandedCornersRadius", 20)
        put("shouldDimBackground", true)
        put("backgroundDimAmount", 0.5)
        put("hardwareAccelerated", true)
        remove("heightPercentage")
        remove("maxHeight")
    }.toString()
}

private fun Context.findActivity(): AppCompatActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is AppCompatActivity) return current
        current = current.baseContext
    }
    return null
}
