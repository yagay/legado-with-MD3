package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
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
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.window.WindowListPopup

val LocalUseMiuixWindowPopup = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    shadowElevation: Dp = 4.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val popupContainerColor = LegadoTheme.colorScheme.surfaceContainer

    if (isMiuix) {
        if (expanded) {
            val popupContentColor = LegadoTheme.colorScheme.onSurface
            WindowListPopup(
                show = expanded,
                onDismissRequest = onDismissRequest,
                popupModifier = modifier
            ) {
                ProvideAppDensity {
                    ProvideAppContentColor(popupContentColor) {
                        // WindowListPopup already owns the popup surface. Adding a
                        // ListPopupColumn plus another painted container here creates a
                        // visible "menu inside menu" effect on some Miuix versions.
                        // Keep a single content container instead.
                        Column(modifier = Modifier.background(popupContainerColor)) {
                            Spacer(Modifier.height(12.dp))
                            content(onDismissRequest)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    } else {
        val colorScheme = rememberOpaqueColorScheme()
        val popupContainerColor = LegadoTheme.colorScheme.surfaceContainerLow

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            shape = shape,
            shadowElevation = shadowElevation,
            containerColor = popupContainerColor
        ) {
            ProvideAppDensity {
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    typography = Typography(),
                    motionScheme = MotionScheme.expressive(),
                    shapes = Shapes()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                    ) {
                        content(onDismissRequest)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundDropdownMenuLazy(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    shadowElevation: Dp = 4.dp,
    verticalSpacing: Dp = 8.dp,
    width: Dp = 280.dp,
    height: Dp = 320.dp,
    state: LazyListState = rememberLazyListState(),
    showFastScroll: Boolean = false,
    content: LazyListScope.(dismiss: () -> Unit) -> Unit
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val popupContainerColor = LegadoTheme.colorScheme.surfaceContainer

    if (isMiuix) {
        if (expanded) {
            val popupContentColor = LegadoTheme.colorScheme.onSurface
            WindowListPopup(
                show = expanded,
                onDismissRequest = onDismissRequest,
                popupModifier = modifier
            ) {
                ProvideAppDensity {
                    ProvideAppContentColor(popupContentColor) {
                        val listModifier = Modifier
                            .requiredSize(width = width, height = height)
                            .background(popupContainerColor)
                        if (showFastScroll) {
                            FastScrollLazyColumn(
                                modifier = listModifier,
                                state = state,
                                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                            ) {
                                item { Spacer(Modifier.height(12.dp)) }
                                content(onDismissRequest)
                                item { Spacer(Modifier.height(12.dp)) }
                            }
                        } else {
                            LazyColumn(
                                modifier = listModifier,
                                state = state,
                                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                            ) {
                                item { Spacer(Modifier.height(12.dp)) }
                                content(onDismissRequest)
                                item { Spacer(Modifier.height(12.dp)) }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val colorScheme = rememberOpaqueColorScheme()
        val popupContainerColor = LegadoTheme.colorScheme.surfaceContainerLow

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            shape = shape,
            shadowElevation = shadowElevation,
            containerColor = popupContainerColor
        ) {
            ProvideAppDensity {
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    typography = Typography(),
                    motionScheme = MotionScheme.expressive(),
                    shapes = Shapes()
                ) {
                    val listModifier = Modifier.requiredSize(width = width, height = height)
                    if (showFastScroll) {
                        FastScrollLazyColumn(
                            modifier = listModifier,
                            state = state,
                            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                        ) {
                            content(onDismissRequest)
                        }
                    } else {
                        LazyColumn(
                            modifier = listModifier,
                            state = state,
                            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                        ) {
                            content(onDismissRequest)
                        }
                    }
                }
            }
        }
    }
}
