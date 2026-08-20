package io.legado.app.ui.widget.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SelectionActions
import io.legado.app.ui.widget.components.SelectionBottomBar
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.topbar.DiscoveryDynamicTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior
import io.legado.app.ui.widget.components.topbar.TopBarActionButton

/**
 * Discovery-only ListScaffold overload. The normal upstream ListScaffold remains untouched.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ListScaffold(
    title: String,
    state: ListUiState<T>,
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
    onBackClick: (() -> Unit)? = null,
    backNavigationIcon: ImageVector = AppIcons.Back,
    showSearchAction: Boolean = true,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit = {},
    searchTrailingIcon: @Composable (() -> Unit)? = null,
    searchPlaceholder: String? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable (ColumnScope.(GlassTopAppBarScrollBehavior) -> Unit)? = null,
    dropDownMenuContent: @Composable (ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    selectionActions: SelectionActions? = null,
    onAddClick: (() -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    scrollBehavior: GlassTopAppBarScrollBehavior? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val resolvedScrollBehavior = scrollBehavior ?: GlassTopAppBarDefaults.defaultScrollBehavior()

    val relocateOverflowToSubtitleHeader =
        subtitleDropdownMenuFastScroll &&
            subtitleDropdownMenuFixedHeader != null &&
            dropDownMenuContent != null

    val effectiveSubtitleFixedHeader: (@Composable () -> Unit)? =
        if (relocateOverflowToSubtitleHeader) {
            {
                var showRelocatedOverflow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        subtitleDropdownMenuFixedHeader?.invoke()
                    }
                    Box {
                        TopBarActionButton(
                            onClick = { showRelocatedOverflow = true },
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_menu),
                        )
                        RoundDropdownMenu(
                            expanded = showRelocatedOverflow,
                            onDismissRequest = { showRelocatedOverflow = false },
                        ) {
                            dropDownMenuContent?.invoke(this) { showRelocatedOverflow = false }
                        }
                    }
                }
            }
        } else subtitleDropdownMenuFixedHeader

    AppScaffold(
        modifier = Modifier.nestedScroll(resolvedScrollBehavior.nestedScrollConnection),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 72.dp),
            )
        },
        topBar = {
            DiscoveryDynamicTopAppBar(
                title = title,
                subtitle = subtitle,
                subtitleDropdownMenu = subtitleDropdownMenu,
                subtitleDropdownMenuLazy = subtitleDropdownMenuLazy,
                subtitleDropdownMenuWidth = subtitleDropdownMenuWidth,
                subtitleDropdownMenuHeight = subtitleDropdownMenuHeight,
                subtitleDropdownMenuState = subtitleDropdownMenuState,
                subtitleDropdownMenuFastScroll = subtitleDropdownMenuFastScroll,
                subtitleDropdownMenuFixedHeader = effectiveSubtitleFixedHeader,
                subtitleMenuExpanded = subtitleMenuExpanded,
                onSubtitleMenuExpandedChange = onSubtitleMenuExpandedChange,
                onSubtitleLongClick = onSubtitleLongClick,
                state = state,
                scrollBehavior = resolvedScrollBehavior,
                onBackClick = onBackClick,
                backNavigationIcon = backNavigationIcon,
                showSearchAction = showSearchAction,
                onSearchToggle = onSearchToggle,
                onSearchQueryChange = onSearchQueryChange,
                onSearchSubmit = onSearchSubmit,
                searchTrailingIcon = searchTrailingIcon,
                searchPlaceholder = searchPlaceholder,
                onClearSelection = { onClearSelection?.invoke() ?: selectionActions?.onClearSelection?.invoke() },
                topBarActions = topBarActions,
                dropDownMenuContent = if (relocateOverflowToSubtitleHeader) null else dropDownMenuContent,
                bottomContent = bottomContent,
            )
        },
        floatingActionButton = floatingActionButton,
        contentWindowInsets = contentWindowInsets,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(paddingValues)
            AnimatedVisibility(
                visible = state.selectedIds.isNotEmpty() && selectionActions != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp + ScreenOffset)
                    .zIndex(1f),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                selectionActions?.let { actions ->
                    SelectionBottomBar(
                        onSelectAll = actions.onSelectAll,
                        onSelectInvert = actions.onSelectInvert,
                        primaryAction = actions.primaryAction,
                        secondaryActions = actions.secondaryActions,
                    )
                }
            }
        }
    }
}
