package io.legado.app.enhance.explore.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.main.explore.ExploreIntent
import io.legado.app.ui.main.explore.ExploreViewModel.ExploreUiState

@Composable
fun ExploreScreenEnhance(
    state: ExploreUiState,
    onIntent: (ExploreIntent) -> Unit,
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onBookClick: (SearchBook, String?) -> Unit,
    paddingValues: PaddingValues
) {
    if (state.layoutMode == 1) {
        DiscoverySuiteScreen(
            state = state,
            onIntent = onIntent,
            onOpenExploreShow = onOpenExploreShow,
            onBookClick = onBookClick,
            paddingValues = paddingValues
        )
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
