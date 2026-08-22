package io.legado.app.ui.widget.components.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.book.SearchBookGridItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.TopBarActionButton

/**
 * Thin layout adapters for the modern discovery page.
 * Visuals stay delegated to existing app components; this file only arranges them.
 */
@Composable
fun DiscoverySuiteHeader(
    title: String,
    onSettingsClick: () -> Unit,
    onScrollToTop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .adaptiveHorizontalPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppText(
            text = title,
            style = LegadoTheme.typography.labelSmall,
            color = LegadoTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TopBarActionButton(
            onClick = onScrollToTop,
            imageVector = Icons.Default.VerticalAlignTop,
            contentDescription = "回到顶部",
        )
        TopBarActionButton(
            onClick = onSettingsClick,
            imageVector = Icons.Default.Settings,
            contentDescription = "瀑布流布局设置",
        )
    }
}

@Composable
fun DiscoverySuiteHorizontalBooksWidget(
    books: List<SearchBook>,
    onBookClick: (SearchBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = books,
            key = { it.bookUrl },
        ) { book ->
            SearchBookGridItem(
                book = book,
                shelfState = BookShelfState.NOT_IN_SHELF,
                onClick = { onBookClick(book) },
                modifier = Modifier.width(104.dp),
            )
        }
    }
}
