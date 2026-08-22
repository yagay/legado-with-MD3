package io.legado.app.ui.widget.components.book

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState

/**
 * Compatibility overload for modern discovery list sizing.
 * Rendering stays on the upstream SearchBookListItem implementation.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SearchBookListItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showPadding: Boolean = true,
    coverHeight: Dp,
    adaptContentToCoverHeight: Boolean,
) {
    SearchBookListItem(
        book = book,
        shelfState = shelfState,
        onClick = onClick,
        modifier = modifier,
        showPadding = showPadding,
    )
}
