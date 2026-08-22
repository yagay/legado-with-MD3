package io.legado.app.enhance.explore.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.explore.ExploreShowEffect
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.main.explore.ExploreIntent
import io.legado.app.ui.main.explore.ExploreViewModel.ExploreUiState
import io.legado.app.ui.widget.components.modalBottomSheet.NativeDraggableComposeBottomSheet
import org.koin.androidx.compose.koinViewModel

private data class NewLayoutExploreSheetRequest(
    val title: String?,
    val sourceUrl: String,
    val exploreUrl: String?,
)

@Composable
fun ExploreScreenEnhance(
    state: ExploreUiState,
    onIntent: (ExploreIntent) -> Unit,
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onBookClick: (SearchBook, String?) -> Unit,
    paddingValues: PaddingValues
) {
    if (state.layoutMode != 1) return

    var sheetRequest by remember { mutableStateOf<NewLayoutExploreSheetRequest?>(null) }

    DiscoverySuiteScreen(
        state = state,
        onIntent = onIntent,
        onOpenExploreShow = { title, sourceUrl, exploreUrl ->
            sheetRequest = NewLayoutExploreSheetRequest(
                title = title,
                sourceUrl = sourceUrl,
                exploreUrl = exploreUrl,
            )
        },
        onBookClick = onBookClick,
        paddingValues = paddingValues
    )

    val request = sheetRequest
    if (request != null) {
        val sheetViewModel: ExploreShowViewModel = koinViewModel()
        val sheetState by sheetViewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(sheetViewModel, request.sourceUrl, request.exploreUrl) {
            sheetViewModel.onIntent(
                ExploreShowIntent.InitData(
                    sourceUrl = request.sourceUrl,
                    exploreUrl = request.exploreUrl,
                )
            )
        }

        LaunchedEffect(sheetViewModel) {
            sheetViewModel.effects.collect { effect ->
                when (effect) {
                    is ExploreShowEffect.OpenBookInfo -> onBookClick(
                        SearchBook(
                            name = effect.name,
                            author = effect.author,
                            bookUrl = effect.bookUrl,
                            origin = effect.origin ?: "",
                            coverUrl = effect.coverPath,
                        ),
                        effect.sharedCoverKey,
                    )

                    is ExploreShowEffect.ShowMessage -> Unit
                }
            }
        }

        NativeDraggableComposeBottomSheet(
            show = true,
            title = null,
            onDismissRequest = { sheetRequest = null },
        ) {
            ExploreShowScreen(
                state = sheetState,
                onIntent = sheetViewModel::onIntent,
                title = request.title.orEmpty(),
                onBack = { sheetRequest = null },
                onBookClick = onBookClick,
            )
        }
    }
}

@Composable
fun ExploreConfigEnhance(
    state: ExploreUiState,
    onIntent: (ExploreIntent) -> Unit
) {
    DiscoveryConfigSheet(
        show = state.enhance.showDiscoveryConfig,
        state = state,
        onIntent = onIntent,
        onDismissRequest = { onIntent(ExploreIntent.ShowDiscoveryConfig(false)) }
    )
}
