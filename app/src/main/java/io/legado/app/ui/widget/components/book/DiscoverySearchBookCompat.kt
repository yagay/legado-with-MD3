package io.legado.app.ui.widget.components.book

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState

/** Compatibility overload used by the modern discovery layout. */
@Composable
fun SearchBookListItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: (() -> Unit)?,
    showPadding: Boolean = true,
    coverHeight: Dp,
    adaptContentToCoverHeight: Boolean,
) {
    SearchBookListItem(
        book = book,
        shelfState = shelfState,
        onClick = onClick,
        showPadding = showPadding,
    )
}
