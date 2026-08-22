package io.legado.app.ui.widget.components.book

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState

/**
 * Compatibility overload for the modern discovery screen.
 *
 * The upstream SearchBookListItem owns the actual rendering. Modern discovery
 * historically supplied cover sizing flags that are not part of the upstream
 * component API; keep those call sites source-compatible without forking the
 * shared book-list implementation.
 */
@Composable
fun SearchBookListItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: (() -> Unit)?,
    showPadding: Boolean,
    coverHeight: Dp,
    adaptContentToCoverHeight: Boolean,
) {
    // The two modern-only sizing arguments are intentionally ignored so the
    // shared upstream item remains the single rendering implementation.
    @Suppress("UNUSED_VARIABLE")
    val ignoredModernSizing = coverHeight to adaptContentToCoverHeight

    SearchBookListItem(
        book = book,
        shelfState = shelfState,
        onClick = onClick,
        showPadding = showPadding,
    )
}
