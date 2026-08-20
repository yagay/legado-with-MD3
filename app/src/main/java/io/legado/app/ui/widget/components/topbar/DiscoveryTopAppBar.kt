package io.legado.app.ui.widget.components.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.theme.responsiveHazeEffect
import io.legado.app.ui.widget.components.GlassDefaults
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.menuItem.DiscoveryRoundDropdownMenuLazy
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.text.AdaptiveAnimatedText
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DiscoveryDynamicTopAppBar(
    title: String,
    subtitle: String? = null,
    subtitleDropdownMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    subtitleDropdownMenuLazy: (LazyListScope.(dismiss: () -> Unit) -> Unit)? = null,
    subtitleDropdownMenuWidth: Dp,
    subtitleDropdownMenuHeight: Dp,
    subtitleDropdownMenuState: LazyListState,
    subtitleDropdownMenuFastScroll: Boolean = false,
    subtitleDropdownMenuFixedHeader: (@Composable () -> Unit)? = null,
    subtitleMenuExpanded: Boolean? = null,
    onSubtitleMenuExpandedChange: ((Boolean) -> Unit)? = null,
    onSubtitleLongClick: (() -> Unit)? = null,
    state: ListUiState<T>,
    scrollBehavior: GlassTopAppBarScrollBehavior,
    onBackClick: (() -> Unit)? = null,
    backNavigationIcon: ImageVector = AppIcons.Back,
    showSearchAction: Boolean = true,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit = {},
    searchPlaceholder: String?,
    searchTrailingIcon: @Composable (() -> Unit)? = null,
    onClearSelection: () -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    dropDownMenuContent: @Composable (ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    bottomContent: @Composable (ColumnScope.(GlassTopAppBarScrollBehavior) -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    val isSelecting = state.selectedIds.isNotEmpty()

    DiscoveryGlassMediumFlexibleTopAppBar(
        modifier = Modifier.fillMaxWidth(),
        title = when {
            state.isLoading -> stringResource(R.string.list_loading_title)
            isSelecting -> stringResource(R.string.list_selected_count, state.selectedIds.size, state.items.size)
            else -> title
        },
        useCharMode = isSelecting || state.isLoading,
        subtitle = subtitle,
        subtitleDropdownMenu = subtitleDropdownMenu,
        subtitleDropdownMenuLazy = subtitleDropdownMenuLazy,
        subtitleDropdownMenuWidth = subtitleDropdownMenuWidth,
        subtitleDropdownMenuHeight = subtitleDropdownMenuHeight,
        subtitleDropdownMenuState = subtitleDropdownMenuState,
        subtitleDropdownMenuFastScroll = subtitleDropdownMenuFastScroll,
        subtitleDropdownMenuFixedHeader = subtitleDropdownMenuFixedHeader,
        subtitleMenuExpanded = subtitleMenuExpanded,
        onSubtitleMenuExpandedChange = onSubtitleMenuExpandedChange,
        onSubtitleLongClick = onSubtitleLongClick,
        navigationIcon = {
            if (isSelecting || onBackClick != null) {
                TopBarNavigationButton(
                    onClick = { if (isSelecting) onClearSelection() else onBackClick?.invoke() },
                    imageVector = if (isSelecting) AppIcons.Close else backNavigationIcon,
                    contentDescription = stringResource(if (isSelecting) R.string.cancel_select else R.string.back),
                )
            }
        },
        actions = {
            if (!isSelecting) {
                if (showSearchAction) {
                    TopBarActionButton(
                        onClick = { onSearchToggle(!state.isSearch) },
                        imageVector = AppIcons.Search,
                        contentDescription = stringResource(R.string.search),
                    )
                }
                topBarActions()
                dropDownMenuContent?.let { content ->
                    Box {
                        TopBarActionButton(
                            onClick = { showMenu = true },
                            imageVector = AppIcons.MoreVert,
                            contentDescription = stringResource(R.string.more_menu),
                        )
                        RoundDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) { dismiss -> content(dismiss) }
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
        bottomContent = {
            AnimatedVisibility(
                modifier = Modifier.adaptiveHorizontalPadding(),
                visible = state.isSearch && !isSelecting,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                    SearchBar(
                        query = state.searchKey,
                        onQueryChange = onSearchQueryChange,
                        onSearch = onSearchSubmit,
                        placeholder = searchPlaceholder,
                        trailingIcon = searchTrailingIcon,
                    )
                }
            }
            bottomContent?.invoke(this, scrollBehavior)
        },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun DiscoveryGlassMediumFlexibleTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    useCharMode: Boolean = false,
    subtitle: String? = null,
    scrollBehavior: GlassTopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    subtitleDropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    subtitleDropdownMenuLazy: (LazyListScope.(onDismiss: () -> Unit) -> Unit)? = null,
    subtitleDropdownMenuWidth: Dp,
    subtitleDropdownMenuHeight: Dp,
    subtitleDropdownMenuState: LazyListState,
    subtitleDropdownMenuFastScroll: Boolean = false,
    subtitleDropdownMenuFixedHeader: (@Composable () -> Unit)? = null,
    subtitleMenuExpanded: Boolean? = null,
    onSubtitleMenuExpandedChange: ((Boolean) -> Unit)? = null,
    onSubtitleLongClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable (ColumnScope.() -> Unit)? = null,
) {
    var internalShowSubtitleMenu by remember { mutableStateOf(false) }
    val showSubtitleMenu = subtitleMenuExpanded ?: internalShowSubtitleMenu
    fun setSubtitleMenuExpanded(expanded: Boolean) {
        if (subtitleMenuExpanded == null) internalShowSubtitleMenu = expanded
        onSubtitleMenuExpandedChange?.invoke(expanded)
    }

    val hazeState = LocalHazeState.current
    val themeSettings = LocalAppUiConfiguration.current.theme
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val containerColor = if (!isMiuix) {
        GlassDefaults.secondaryColorOr { GlassTopAppBarDefaults.containerColor() }
    } else {
        GlassDefaults.secondaryColorOr { GlassTopAppBarDefaults.getMiuixAppBarColor() }
    }
    val scrolledColor = if (!isMiuix) {
        GlassDefaults.secondaryColorOr { GlassTopAppBarDefaults.scrolledContainerColor() }
    } else containerColor
    val animatedColor = if (!isMiuix) lerp(containerColor, scrolledColor, scrollBehavior?.collapsedFraction ?: 0f) else containerColor
    val finalModifier = if (hazeState != null) {
        modifier.background(animatedColor).responsiveHazeEffect(state = hazeState)
    } else modifier.background(animatedColor)
    val subtitleText = subtitle?.takeIf { it.isNotBlank() }
    val transparentColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    )

    Column(modifier = finalModifier) {
        if (isMiuix) {
            MiuixTopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
                title = title,
                subtitle = subtitleText.orEmpty(),
                navigationIcon = navigationIcon,
                actions = { TopBarActionsRow(modifier = Modifier.padding(end = miuixTopBarActionsEndPadding())) { actions() } },
                color = Color.Transparent,
                defaultWindowInsetsPadding = false,
                navigationIconPadding = miuixTopBarSlotPadding(),
                actionIconPadding = miuixTopBarSlotPadding(),
                scrollBehavior = (scrollBehavior as? MiuixGlassScrollBehavior)?.miuixBehavior,
            )
        } else if (themeSettings.useFlexibleTopAppBar) {
            MediumFlexibleTopAppBar(
                title = { AdaptiveAnimatedText(text = title, useCharMode = useCharMode, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                subtitle = subtitleText?.let { text ->
                    {
                        val hasMenu = subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null
                        Row(
                            modifier = if (hasMenu || onSubtitleLongClick != null) {
                                Modifier.combinedClickable(
                                    onClick = { if (hasMenu) setSubtitleMenuExpanded(true) },
                                    onLongClick = onSubtitleLongClick,
                                )
                            } else Modifier,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AnimatedTextLine(text = text)
                            if (hasMenu) {
                                Icon(
                                    imageVector = if (showSubtitleMenu) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                },
                navigationIcon = navigationIcon,
                actions = { Box(modifier = Modifier.padding(end = 12.dp)) { TopBarActionsRow { actions() } } },
                scrollBehavior = (scrollBehavior as? M3GlassScrollBehavior)?.m3Behavior,
                colors = transparentColors,
            )
        } else {
            TopAppBar(
                title = {
                    Column {
                        AdaptiveAnimatedText(text = title, useCharMode = useCharMode, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitleText?.let { text ->
                            val hasMenu = subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null
                            Row(
                                modifier = if (hasMenu || onSubtitleLongClick != null) {
                                    Modifier.combinedClickable(
                                        onClick = { if (hasMenu) setSubtitleMenuExpanded(true) },
                                        onLongClick = onSubtitleLongClick,
                                    )
                                } else Modifier,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AnimatedTextLine(
                                    text = text,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (hasMenu) {
                                    Icon(
                                        imageVector = if (showSubtitleMenu) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = navigationIcon,
                actions = { Box(modifier = Modifier.padding(end = 12.dp)) { TopBarActionsRow { actions() } } },
                scrollBehavior = (scrollBehavior as? M3GlassScrollBehavior)?.m3Behavior,
                colors = transparentColors,
            )
        }

        if (subtitleDropdownMenuLazy != null) {
            DiscoveryRoundDropdownMenuLazy(
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
                onDismissRequest = { setSubtitleMenuExpanded(false) },
            ) { subtitleDropdownMenu { setSubtitleMenuExpanded(false) } }
        }

        bottomContent?.invoke(this)
    }
}
