package io.legado.app.ui.widget.components.topbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu

private enum class ActiveTopBarMenu { None, Subtitle, More }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DynamicTopAppBar(
    title: String,
    subtitle: String? = null,
    subtitleDropdownMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    subtitleDropdownMenuLazy: (LazyListScope.(dismiss: () -> Unit) -> Unit)? = null,
    subtitleDropdownMenuWidth: Dp = 280.dp,
    subtitleDropdownMenuHeight: Dp = 320.dp,
    state: ListUiState<T>,
    scrollBehavior: GlassTopAppBarScrollBehavior,
    onBackClick: (() -> Unit)? = null,
    backNavigationIcon: ImageVector = AppIcons.Back,
    showSearchAction: Boolean = true,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit = {},
    searchPlaceholder: String?,
    searchLeadingIcon: ImageVector = Icons.Default.Search,
    searchTrailingIcon: @Composable (() -> Unit)? = null,
    searchDropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onClearSelection: () -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    dropDownMenuContent: @Composable (ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    bottomContent: @Composable (ColumnScope.(GlassTopAppBarScrollBehavior) -> Unit)? = null
) {
    var activeMenu by remember { mutableStateOf(ActiveTopBarMenu.None) }
    val isSelecting = state.selectedIds.isNotEmpty()

    GlassMediumFlexibleTopAppBar(
        modifier = Modifier.fillMaxWidth(),
        title = if (isSelecting) "已选择 ${state.selectedIds.size}/${state.items.size}" else title,
        useCharMode = isSelecting || state.isLoading,
        subtitle = subtitle,
        subtitleDropdownMenu = subtitleDropdownMenu,
        subtitleDropdownMenuLazy = subtitleDropdownMenuLazy,
        subtitleDropdownMenuWidth = subtitleDropdownMenuWidth,
        subtitleDropdownMenuHeight = subtitleDropdownMenuHeight,
        subtitleMenuExpanded = if (subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null) activeMenu == ActiveTopBarMenu.Subtitle else null,
        onSubtitleMenuExpandedChange = if (subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null) { expanded ->
            activeMenu = if (expanded) ActiveTopBarMenu.Subtitle else ActiveTopBarMenu.None
        } else null,
        navigationIcon = {
            if (isSelecting || onBackClick != null) {
                TopBarNavigationButton(
                    onClick = { if (isSelecting) onClearSelection() else onBackClick?.invoke() },
                    imageVector = if (isSelecting) AppIcons.Close else backNavigationIcon,
                    contentDescription = stringResource(
                        if (isSelecting) R.string.cancel_select else R.string.back
                    )
                )
            }
        },
        actions = {
            if (!isSelecting) {
                if (showSearchAction) {
                    TopBarActionButton(
                        onClick = {
                            activeMenu = ActiveTopBarMenu.None
                            onSearchToggle(true)
                        },
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search)
                    )
                }
                topBarActions()
                if (dropDownMenuContent != null) {
                    TopBarActionButton(
                        onClick = { activeMenu = ActiveTopBarMenu.More },
                        imageVector = AppIcons.MoreVert,
                        contentDescription = stringResource(R.string.more_menu)
                    )
                    RoundDropdownMenu(
                        expanded = activeMenu == ActiveTopBarMenu.More,
                        onDismissRequest = { activeMenu = ActiveTopBarMenu.None }
                    ) {
                        dropDownMenuContent { activeMenu = ActiveTopBarMenu.None }
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
        bottomContent = {
            if (state.isSearch) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SearchBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        query = state.searchKey,
                        onQueryChange = onSearchQueryChange,
                        onSearch = onSearchSubmit,
                        placeholder = searchPlaceholder,
                        trailingIcon = {
                            Row {
                                searchTrailingIcon?.invoke()
                                IconButton(
                                    onClick = {
                                        onSearchQueryChange("")
                                        onSearchToggle(false)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.close)
                                    )
                                }
                            }
                        },
                        autoFocus = true
                    )
                }
            } else {
                bottomContent?.invoke(this, scrollBehavior)
            }
        }
    )
}
