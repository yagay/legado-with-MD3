package io.legado.app.ui.widget.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import io.legado.app.ui.widget.components.SelectionActions
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuLazy
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior
import io.legado.app.ui.widget.components.topbar.TopBarActionButton

/**
 * Discovery-only overload.
 *
 * The upstream ListScaffold stays untouched. This overload is selected only by
 * discovery because [subtitleDropdownMenuWidth] is required and is not part of
 * the upstream signature. Other screens therefore keep the exact upstream
 * composition/runtime path.
 */
@Suppress("UNUSED_PARAMETER")
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
    val hasSubtitleMenu = subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null
    var internalExpanded by remember(hasSubtitleMenu) { mutableStateOf(false) }
    val expanded = subtitleMenuExpanded ?: internalExpanded

    fun setExpanded(value: Boolean) {
        if (subtitleMenuExpanded == null) internalExpanded = value
        onSubtitleMenuExpandedChange?.invoke(value)
    }

    ListScaffold(
        title = title,
        state = state,
        subtitle = subtitle,
        onBackClick = onBackClick,
        backNavigationIcon = backNavigationIcon,
        showSearchAction = showSearchAction,
        onSearchToggle = onSearchToggle,
        onSearchQueryChange = onSearchQueryChange,
        onSearchSubmit = onSearchSubmit,
        searchTrailingIcon = searchTrailingIcon,
        searchPlaceholder = searchPlaceholder,
        topBarActions = {
            topBarActions()
            if (hasSubtitleMenu) {
                Box {
                    TopBarActionButton(
                        onClick = { setExpanded(!expanded) },
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起书源菜单" else "展开书源菜单",
                    )
                    if (subtitleDropdownMenuLazy != null) {
                        RoundDropdownMenuLazy(
                            expanded = expanded,
                            onDismissRequest = { setExpanded(false) },
                            modifier = Modifier,
                            maxHeight = subtitleDropdownMenuHeight,
                        ) {
                            if (subtitleDropdownMenuFixedHeader != null) {
                                item(key = "discovery_source_fixed_header") {
                                    subtitleDropdownMenuFixedHeader()
                                }
                            }
                            subtitleDropdownMenuLazy { setExpanded(false) }
                        }
                    } else if (subtitleDropdownMenu != null) {
                        RoundDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { setExpanded(false) },
                        ) {
                            subtitleDropdownMenu { setExpanded(false) }
                        }
                    }
                }
            }
        },
        bottomContent = bottomContent,
        dropDownMenuContent = dropDownMenuContent,
        onClearSelection = onClearSelection,
        selectionActions = selectionActions,
        onAddClick = onAddClick,
        floatingActionButton = floatingActionButton,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = contentWindowInsets,
        scrollBehavior = scrollBehavior,
        content = content,
    )
}
