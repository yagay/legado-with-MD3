package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideAppContentColor
import io.legado.app.ui.theme.ProvideAppDensity

/**
 * 应用统一拖拽弹窗宿主。
 *
 * 登录、类别选择、日志/说明类弹窗以及导航层普通二级页面都应复用这一组件，
 * 统一拖动、圆角、遮罩和系统栏表现；各页面内容及自身 TopAppBar 保持不变。
 *
 * 原生 overlay 已经把整个 panel 放在状态栏下方，因此这里统一消费顶部状态栏 inset，
 * 防止内部 AppScaffold/TopAppBar 再次预留一层状态栏高度。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
@Suppress("UNUSED_PARAMETER")
fun AppModalBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    animateContentSize: Boolean = true,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    contentPaddingEnabled: Boolean = true,
    sheetGesturesEnabled: Boolean = true,
    containerColor: Color? = null,
    maxHeightFraction: Float = 1f,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!show) return

    val sheetContainerColor = containerColor ?: LegadoTheme.colorScheme.surfaceContainer
    val sheetContentColor = LegadoTheme.colorScheme.onSurface
    val sheetScrimColor = LegadoTheme.colorScheme.background

    NativeDraggableComposeBottomSheet(
        show = true,
        title = null,
        onDismissRequest = onDismissRequest,
        scrimColor = sheetScrimColor,
        containerColor = sheetContainerColor,
        gesturesEnabled = sheetGesturesEnabled,
    ) {
        ProvideAppDensity {
            ProvideAppContentColor(sheetContentColor) {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .consumeWindowInsets(WindowInsets.statusBars)
                        .consumeWindowInsets(WindowInsets.statusBarsIgnoringVisibility)
                        .let { contentModifier ->
                            if (contentPaddingEnabled) {
                                contentModifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 16.dp,
                                )
                            } else {
                                contentModifier
                            }
                        }
                        .let { contentModifier ->
                            if (animateContentSize) contentModifier.animateContentSize()
                            else contentModifier
                        }
                ) {
                    val hasHeader =
                        !title.isNullOrEmpty() || startAction != null || endAction != null

                    if (hasHeader) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (startAction != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .let {
                                            if (contentPaddingEnabled) it
                                            else it.padding(start = 16.dp)
                                        }
                                ) {
                                    startAction()
                                }
                            }

                            if (!title.isNullOrEmpty()) {
                                Text(
                                    text = title,
                                    style = LegadoTheme.typography.titleMediumEmphasized,
                                    color = sheetContentColor,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 56.dp),
                                )
                            }

                            if (endAction != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .let {
                                            if (contentPaddingEnabled) it
                                            else it.padding(end = 16.dp)
                                        }
                                ) {
                                    endAction()
                                }
                            }
                        }
                    }

                    content()
                }
            }
        }
    }
}

/**
 * 专为 nullable 数据设计的 AppModalBottomSheet 重载。
 * 当 [data] 不为 null 时显示弹窗；当 [data] 变为 null 时缓存最后一次内容。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> AppModalBottomSheet(
    data: T?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    animateContentSize: Boolean = true,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    sheetGesturesEnabled: Boolean = true,
    maxHeightFraction: Float = 1f,
    content: @Composable ColumnScope.(T) -> Unit
) {
    var cachedData by remember { mutableStateOf(data) }

    if (data != null) {
        cachedData = data
    }

    val currentData = cachedData
    AppModalBottomSheet(
        show = data != null,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title,
        startAction = startAction,
        endAction = endAction,
        animateContentSize = animateContentSize,
        contentWindowInsets = contentWindowInsets,
        sheetGesturesEnabled = sheetGesturesEnabled,
        maxHeightFraction = maxHeightFraction,
        content = {
            if (currentData != null) {
                content(currentData)
            }
        }
    )
}