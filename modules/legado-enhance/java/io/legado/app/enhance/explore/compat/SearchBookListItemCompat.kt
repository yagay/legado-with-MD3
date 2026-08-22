package io.legado.app.ui.widget.components.book

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState

/**
 * Compatibility adapter for the modern discovery caller.
 *
 * The legacy modern layout used to request a custom cover height. To keep the new layout visually
 * identical to the upstream list layout, those sizing arguments are intentionally ignored and the
 * real upstream SearchBookListItem is used unchanged.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SearchBookListItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: (() -> Unit)?,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    showPadding: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
    sourceCount: Int? = null,
    @Suppress("UNUSED_PARAMETER") coverHeight: Dp,
    @Suppress("UNUSED_PARAMETER") adaptContentToCoverHeight: Boolean,
) {
    SearchBookListItem(
        book = book,
        shelfState = shelfState,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        showPadding = showPadding,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedCoverKey = sharedCoverKey,
        sourceCount = sourceCount,
    )
}
