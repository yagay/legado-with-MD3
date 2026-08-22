package io.legado.app.ui.widget.components.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.widget.components.book.SearchBookGridItem

/**
 * Modern-discovery layout adapter.
 *
 * Only the horizontal container is specific to the new layout. Book rendering
 * stays fully owned by the upstream SearchBookGridItem implementation.
 */
@Composable
fun DiscoverySuiteHorizontalBooksWidget(
    books: List<SearchBook>,
    onBookClick: (SearchBook) -> Unit,
) {
    LazyRow(
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
                modifier = Modifier.width(96.dp),
            )
        }
    }
}
