package io.legado.app.ui.widget.components.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.widget.components.book.SearchBookGridItem
import kotlinx.collections.immutable.ImmutableList

@Composable
fun DiscoverySuiteHorizontalBooksWidget(
    books: ImmutableList<SearchBook>,
    onBookClick: (SearchBook) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(books) { book ->
            SearchBookGridItem(
                book = book,
                shelfState = BookShelfState.NOT_IN_SHELF,
                onClick = { onBookClick(book) },
                modifier = Modifier.width(100.dp),
            )
        }
    }
}

@Composable
fun DiscoverySuiteHeader(
    title: String,
    onSettingsClick: (() -> Unit)? = null,
    onScrollToTop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onScrollToTop != null) {
                IconButton(onClick = onScrollToTop, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.VerticalAlignTop,
                        contentDescription = "Scroll to top",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            if (onSettingsClick != null) {
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Layout settings",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
