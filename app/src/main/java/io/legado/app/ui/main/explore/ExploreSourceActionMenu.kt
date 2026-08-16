package io.legado.app.ui.main.explore

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem

/**
 * Shared source-action menu used by both the upstream-style list header and the modern source picker.
 * The modern layout only chooses where this content is shown; source actions stay defined here once.
 */
fun exploreSourceActionRowCount(source: BookSourcePart, includeBack: Boolean = false): Int =
    6 + (if (source.hasLoginUrl) 1 else 0) + (if (includeBack) 1 else 0)

@Composable
fun ExploreSourceActionMenuContent(
    source: BookSourcePart,
    onTop: () -> Unit,
    onEdit: () -> Unit,
    onSearch: () -> Unit,
    onLogin: () -> Unit,
    onSetHomeSource: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    PillHeaderDivider(title = source.bookSourceName)

    if (onBack != null) {
        RoundDropdownMenuItem(
            leadingIcon = { MenuItemIcon(Icons.Default.ArrowBack) },
            text = "返回书源列表",
            onClick = onBack,
        )
    }

    RoundDropdownMenuItem(
        leadingIcon = { MenuItemIcon(Icons.Default.VerticalAlignTop) },
        text = stringResource(R.string.to_top),
        onClick = { onTop(); onDismiss() },
    )
    RoundDropdownMenuItem(
        leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
        text = stringResource(R.string.edit),
        onClick = { onEdit(); onDismiss() },
    )
    RoundDropdownMenuItem(
        leadingIcon = { MenuItemIcon(Icons.Default.Search) },
        text = stringResource(R.string.search),
        onClick = { onSearch(); onDismiss() },
    )
    if (source.hasLoginUrl) {
        RoundDropdownMenuItem(
            leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
            text = stringResource(R.string.login),
            onClick = { onLogin(); onDismiss() },
        )
    }
    RoundDropdownMenuItem(
        leadingIcon = { MenuItemIcon(Icons.Default.Dashboard) },
        text = "设为示例首页源",
        onClick = { onSetHomeSource(); onDismiss() },
    )
    RoundDropdownMenuItem(
        leadingIcon = { MenuItemIcon(Icons.Default.Refresh) },
        text = stringResource(R.string.refresh),
        onClick = { onRefresh(); onDismiss() },
    )
    RoundDropdownMenuItem(
        leadingIcon = {
            MenuItemIcon(
                Icons.Default.Delete,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        text = stringResource(R.string.delete),
        color = LegadoTheme.colorScheme.error,
        onClick = { onDelete(); onDismiss() },
    )
}
