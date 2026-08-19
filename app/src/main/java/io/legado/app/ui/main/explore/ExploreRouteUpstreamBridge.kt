package io.legado.app.ui.main.explore

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.utils.startActivity
import org.koin.androidx.compose.koinViewModel

/**
 * Keeps MainScreen on the upstream ExploreRouteScreen call shape.
 * Modern discovery book clicks are bridged to the existing BookInfoActivity.
 */
@Composable
fun ExploreRouteScreen(
    viewModel: ExploreViewModel = koinViewModel(),
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onOpenLogin: (sourceUrl: String) -> Unit,
    onOpenEdit: (sourceUrl: String) -> Unit,
    onOpenSearch: (scopeRaw: String) -> Unit,
) {
    val context = LocalContext.current
    ExploreRouteScreen(
        viewModel = viewModel,
        onOpenExploreShow = onOpenExploreShow,
        onOpenLogin = onOpenLogin,
        onOpenEdit = onOpenEdit,
        onOpenSearch = onOpenSearch,
        onOpenBookInfo = { name, author, bookUrl, origin, coverPath, _ ->
            context.startActivity<BookInfoActivity> {
                putExtra("bookUrl", bookUrl)
                putExtra("name", name)
                putExtra("author", author)
                putExtra("origin", origin)
                putExtra("coverPath", coverPath)
            }
        },
    )
}
