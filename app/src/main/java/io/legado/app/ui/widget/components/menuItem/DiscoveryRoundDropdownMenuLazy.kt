package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideAppContentColor
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.rememberOpaqueColorScheme
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import top.yukonga.miuix.kmp.window.WindowListPopup

/** Discovery-only popup used by the modern Explore source picker. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscoveryRoundDropdownMenuLazy(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    shadowElevation: Dp = 4.dp,
    verticalSpacing: Dp = 8.dp,
    width: Dp = 280.dp,
    height: Dp = 320.dp,
    state: LazyListState,
    showFastScroll: Boolean = false,
    fixedHeader: (@Composable () -> Unit)? = null,
    content: LazyListScope.(dismiss: () -> Unit) -> Unit,
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val popupContainerColor = LegadoTheme.colorScheme.surfaceContainer

    if (isMiuix) {
        if (expanded) {
            val popupContentColor = LegadoTheme.colorScheme.onSurface
            WindowListPopup(
                show = true,
                onDismissRequest = onDismissRequest,
                popupModifier = modifier,
            ) {
                ProvideAppDensity {
                    ProvideAppContentColor(popupContentColor) {
                        Column(
                            modifier = Modifier
                                .requiredSize(width = width, height = height)
                                .background(popupContainerColor),
                        ) {
                            fixedHeader?.invoke()
                            val listModifier = Modifier.fillMaxWidth().weight(1f)
                            if (showFastScroll) {
                                FastScrollLazyColumn(
                                    modifier = listModifier,
                                    state = state,
                                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                                ) {
                                    content(onDismissRequest)
                                }
                            } else {
                                LazyColumn(
                                    modifier = listModifier,
                                    state = state,
                                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                                ) {
                                    content(onDismissRequest)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val colorScheme = rememberOpaqueColorScheme()
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            shape = shape,
            shadowElevation = shadowElevation,
            containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        ) {
            ProvideAppDensity {
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    typography = Typography(),
                    motionScheme = MotionScheme.expressive(),
                    shapes = Shapes(),
                ) {
                    Column(modifier = Modifier.requiredSize(width = width, height = height)) {
                        fixedHeader?.invoke()
                        val listModifier = Modifier.fillMaxWidth().weight(1f)
                        if (showFastScroll) {
                            FastScrollLazyColumn(
                                modifier = listModifier,
                                state = state,
                                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                            ) {
                                content(onDismissRequest)
                            }
                        } else {
                            LazyColumn(
                                modifier = listModifier,
                                state = state,
                                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                            ) {
                                content(onDismissRequest)
                            }
                        }
                    }
                }
            }
        }
    }
}
