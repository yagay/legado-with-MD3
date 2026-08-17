package io.legado.app.ui.widget.components.topbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.responsiveHazeEffect
import io.legado.app.ui.widget.components.GlassDefaults
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuLazy
import io.legado.app.ui.widget.components.text.AdaptiveAnimatedText
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun GlassMediumFlexibleTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    useCharMode: Boolean = false,
    subtitle: String? = null,
    scrollBehavior: GlassTopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    subtitleDropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    subtitleDropdownMenuLazy: (LazyListScope.(onDismiss: () -> Unit) -> Unit)? = null,
    subtitleDropdownMenuWidth: Dp = 280.dp,
    subtitleDropdownMenuHeight: Dp = 320.dp,
    subtitleDropdownMenuState: LazyListState = rememberLazyListState(),
    subtitleDropdownMenuFastScroll: Boolean = false,
    subtitleDropdownMenuFixedHeader: (@Composable () -> Unit)? = null,
    subtitleMenuExpanded: Boolean? = null,
    onSubtitleMenuExpandedChange: ((Boolean) -> Unit)? = null,
    onSubtitleLongClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable (ColumnScope.() -> Unit)? = null
) {
    var internalShowSubtitleMenu by remember { mutableStateOf(false) }
    val showSubtitleMenu = subtitleMenuExpanded ?: internalShowSubtitleMenu
    fun setSubtitleMenuExpanded(expanded: Boolean) {
        if (subtitleMenuExpanded == null) {
            internalShowSubtitleMenu = expanded
        }
        onSubtitleMenuExpandedChange?.invoke(expanded)
    }

    val hazeState = LocalHazeState.current
    val themeSettings = LocalAppUiConfiguration.current.theme
    val composeEngine = LegadoTheme.composeEngine
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)

    val containerColor = if (!isMiuix) {
        GlassDefaults.secondaryColorOr { GlassTopAppBarDefaults.containerColor() }
    } else {
        GlassDefaults.secondaryColorOr { GlassTopAppBarDefaults.getMiuixAppBarColor() }
    }

    val scrolledColor = if (!isMiuix) {
        GlassDefaults.secondaryColorOr { GlassTopAppBarDefaults.scrolledContainerColor() }
    } else {
        GlassDefaults.secondaryColorOr { GlassTopAppBarDefaults.getMiuixAppBarColor() }
    }

    val animatedColor = if (!isMiuix) {
        val fraction = scrollBehavior?.collapsedFraction ?: 0f
        lerp(containerColor, scrolledColor, fraction)
    } else {
        containerColor
    }

    val finalModifier = if (hazeState != null) {
        modifier
            .background(color = animatedColor)
            .responsiveHazeEffect(state = hazeState)
    } else {
        modifier.background(color = animatedColor)
    }

    val transparentColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent
    )
    val subtitleText = subtitle?.takeIf { it.isNotBlank() }

    Column(
        modifier = finalModifier
    ) {
        when {
            isMiuix -> {
                // Reserve constant status-bar space (ignoring visibility) instead
                // of MiuixTopAppBar's default animating status-bar padding. This
                // matches Material3's TopAppBar (systemBarsForVisualComponents) so
                // the bar/content doesn't reflow down when the status bar is
                // re-shown — e.g. returning from a reader that hid it.
                MiuixTopAppBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
                    title = title,
                    subtitle = subtitleText.orEmpty(),
                    navigationIcon = navigationIcon,
                    actions = {
                        TopBarActionsRow(
                            modifier = Modifier.padding(end = miuixTopBarActionsEndPadding())
                        ) { actions() }
                    },
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    navigationIconPadding = miuixTopBarSlotPadding(),
                    actionIconPadding = miuixTopBarSlotPadding(),
                    scrollBehavior = (scrollBehavior as? MiuixGlassScrollBehavior)?.miuixBehavior
                )
            }

            else -> {
                // A subtitle menu must not change the top-app-bar layout. Explore uses
                // the subtitle as its source picker in waterfall mode; falling back to
                // the compact TopAppBar here moved both the "Discovery" title and the
                // popup anchor compared with list mode. The popup itself is composed
                // once below this bar, so MediumFlexibleTopAppBar can safely keep the
                // same positioning in both modes.
                if (themeSettings.useFlexibleTopAppBar) {
                    MediumFlexibleTopAppBar(
                        modifier = Modifier,
                        title = {
                            AdaptiveAnimatedText(
                                text = title,
                                useCharMode = useCharMode,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        subtitle = subtitleText?.let { text ->
                            {
                                val hasSubtitleMenu = subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null
                                val rowModifier = if (hasSubtitleMenu || onSubtitleLongClick != null) {
                                    Modifier.combinedClickable(
                                        onClick = { if (hasSubtitleMenu) setSubtitleMenuExpanded(true) },
                                        onLongClick = onSubtitleLongClick,
                                    )
                                } else Modifier
                                Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                                    AnimatedTextLine(text = text)
                                    if (subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null) {
                                        Icon(
                                            imageVector = if (showSubtitleMenu) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (showSubtitleMenu) "收起书源菜单" else "展开书源菜单",
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = navigationIcon,
                        actions = {
                            Box(modifier = Modifier.padding(end = 12.dp)) {
                                TopBarActionsRow { actions() }
                            }
                        },
                        scrollBehavior = (scrollBehavior as? M3GlassScrollBehavior)?.m3Behavior,
                        colors = transparentColors
                    )
                } else {
                    TopAppBar(
                        modifier = Modifier,
                        title = {
                            Column {
                                AdaptiveAnimatedText(
                                    text = title,
                                    useCharMode = useCharMode,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                subtitleText?.let { text ->
                                    val hasSubtitleMenu = subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null
                                    val rowModifier = if (hasSubtitleMenu || onSubtitleLongClick != null) {
                                        Modifier.combinedClickable(
                                            onClick = { if (hasSubtitleMenu) setSubtitleMenuExpanded(true) },
                                            onLongClick = onSubtitleLongClick,
                                        )
                                    } else Modifier
                                    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                                        AnimatedTextLine(
                                            text = text,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null) {
                                            Icon(
                                                imageVector = if (showSubtitleMenu) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (showSubtitleMenu) "收起书源菜单" else "展开书源菜单",
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = navigationIcon,
                        actions = {
                            Box(modifier = Modifier.padding(end = 12.dp)) {
                                TopBarActionsRow { actions() }
                            }
                        },
                        scrollBehavior = (scrollBehavior as? M3GlassScrollBehavior)?.m3Behavior,
                        colors = transparentColors
                    )
                }
            }
        }

        // TopAppBar may compose its title/subtitle slot more than once while
        // measuring or animating. A Popup inside that slot therefore creates
        // duplicate windows for the same expanded state. Keep the clickable
        // subtitle in the slot, but own exactly one popup at this outer level.
        if (subtitleDropdownMenuLazy != null) {
            RoundDropdownMenuLazy(
                expanded = showSubtitleMenu,
                onDismissRequest = { setSubtitleMenuExpanded(false) },
                width = subtitleDropdownMenuWidth,
                height = subtitleDropdownMenuHeight,
                state = subtitleDropdownMenuState,
                showFastScroll = subtitleDropdownMenuFastScroll,
                fixedHeader = subtitleDropdownMenuFixedHeader,
            ) {
                subtitleDropdownMenuLazy { setSubtitleMenuExpanded(false) }
            }
        } else if (subtitleDropdownMenu != null) {
            RoundDropdownMenu(
                expanded = showSubtitleMenu,
                onDismissRequest = { setSubtitleMenuExpanded(false) }
            ) {
                subtitleDropdownMenu { setSubtitleMenuExpanded(false) }
            }
        }

        bottomContent?.invoke(this)
    }
}

object GlassTopAppBarDefaults {

    @Composable
    fun getMiuixAppBarColor(): Color {
        val baseColor = GlassDefaults.secondaryColorOr { MiuixTheme.colorScheme.surface }
        return GlassDefaults.glassColor(
            noBlurColor = baseColor,
            blurAlpha = GlassDefaults.TransparentAlpha
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun defaultScrollBehavior(): GlassTopAppBarScrollBehavior {
        val configuration = LocalAppUiConfiguration.current
        val composeEngine = configuration.appShell.composeEngine

        return if (ThemeResolver.isMiuixEngine(composeEngine)) {
            val miuixBehavior = MiuixScrollBehavior()
            remember(miuixBehavior) { MiuixGlassScrollBehavior(miuixBehavior) }
        } else if (configuration.theme.useFlexibleTopAppBar) {
            val m3Behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            remember(m3Behavior) { M3GlassScrollBehavior(m3Behavior) }
        } else {
            val m3Behavior = TopAppBarDefaults.pinnedScrollBehavior()
            remember(m3Behavior) { M3GlassScrollBehavior(m3Behavior) }
        }
    }

    @Composable
    fun glassColors(): TopAppBarColors {

        val containerBaseColor = GlassDefaults.secondaryColorOr {
            MaterialTheme.colorScheme.surface
        }
        val containerColor = GlassDefaults.glassColor(
            noBlurColor = containerBaseColor,
            blurAlpha = GlassDefaults.TransparentAlpha
        )

        val scrolledBaseColor = GlassDefaults.secondaryColorOr {
            MaterialTheme.colorScheme.surfaceContainer
        }
        val scrolledContainerColor = if (LocalAppUiConfiguration.current.theme.enableBlur) {
            scrolledBaseColor.copy(alpha = GlassDefaults.TransparentAlpha)
        } else {
            scrolledBaseColor
        }

        return TopAppBarDefaults.topAppBarColors(
            containerColor = applyTopBarOpacity(containerColor),
            scrolledContainerColor = applyTopBarOpacity(scrolledContainerColor)
        )
    }

    @Composable
    fun containerColor(): Color {
        val baseColor = GlassDefaults.secondaryColorOr { MaterialTheme.colorScheme.surface }
        val glassColor = GlassDefaults.glassColor(
            noBlurColor = baseColor,
            blurAlpha = GlassDefaults.TransparentAlpha
        )
        return applyTopBarOpacity(glassColor)
    }

    @Composable
    fun scrolledContainerColor(): Color {
        val baseColor = GlassDefaults.secondaryColorOr {
            MaterialTheme.colorScheme.surfaceContainer
        }
        val glassColor = GlassDefaults.glassColor(
            noBlurColor = baseColor,
            blurAlpha = GlassDefaults.TransparentAlpha
        )
        return applyTopBarOpacity(glassColor)
    }

    @Composable
    fun controlContainerColor(): Color {
        val baseColor = GlassDefaults.glassColor(
            noBlurColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            blurAlpha = GlassDefaults.DefaultBlurAlpha
        )
        return applyTopBarOpacity(baseColor)
    }

    @Composable
    private fun applyTopBarOpacity(color: Color): Color {
        val opacity = (LocalAppUiConfiguration.current.theme.topBarOpacity.coerceIn(0, 100)) / 100f
        return color.copy(alpha = (color.alpha * opacity).coerceIn(0f, 1f))
    }
}
