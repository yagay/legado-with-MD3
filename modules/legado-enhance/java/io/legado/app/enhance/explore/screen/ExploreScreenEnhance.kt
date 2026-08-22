package io.legado.app.enhance.explore.screen

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.http.CookieStore
import io.legado.app.ui.book.explore.ExploreShowEffect
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.login.SourceLoginIntent
import io.legado.app.ui.login.SourceLoginMode
import io.legado.app.ui.login.SourceLoginUiState
import io.legado.app.ui.login.SourceLoginWebDialog
import io.legado.app.ui.main.explore.ExploreIntent
import io.legado.app.ui.main.explore.ExploreViewModel.ExploreUiState
import io.legado.app.ui.widget.components.modalBottomSheet.NativeDraggableComposeBottomSheet
import kotlinx.collections.immutable.persistentMapOf
import org.koin.androidx.compose.koinViewModel

private data class NewLayoutExploreSheetRequest(
    val title: String?,
    val sourceUrl: String,
    val exploreUrl: String?,
)

private data class NewLayoutBrowserSheetRequest(
    val title: String,
    val sourceUrl: String,
    val url: String,
)

private val startBrowserPattern = Regex(
    """java\.startBrowser(?:Dp)?\(\s*[\"']([^\"']+)[\"'](?:\s*,\s*[\"']([^\"']*)[\"'])?\s*\)"""
)

private fun parseStartBrowserAction(
    rawUrl: String?,
    fallbackTitle: String?,
    sourceUrl: String,
): NewLayoutBrowserSheetRequest? {
    val raw = rawUrl?.trim().orEmpty()
    val match = startBrowserPattern.find(raw) ?: return null
    val url = match.groupValues.getOrNull(1).orEmpty()
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    val title = match.groupValues.getOrNull(2)
        ?.takeIf { it.isNotBlank() }
        ?: fallbackTitle.orEmpty()
    return NewLayoutBrowserSheetRequest(
        title = title,
        sourceUrl = sourceUrl,
        url = url,
    )
}

@Composable
fun ExploreScreenEnhance(
    state: ExploreUiState,
    onIntent: (ExploreIntent) -> Unit,
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onBookClick: (SearchBook, String?) -> Unit,
    paddingValues: PaddingValues
) {
    if (state.layoutMode != 1) return

    val context = LocalContext.current
    var sheetRequest by remember { mutableStateOf<NewLayoutExploreSheetRequest?>(null) }
    var browserRequest by remember { mutableStateOf<NewLayoutBrowserSheetRequest?>(null) }
    var browserCurrentUrl by remember { mutableStateOf<String?>(null) }

    DiscoverySuiteScreen(
        state = state,
        onIntent = onIntent,
        onOpenExploreShow = { title, sourceUrl, exploreUrl ->
            val browser = parseStartBrowserAction(exploreUrl, title, sourceUrl)
            if (browser != null) {
                sheetRequest = null
                browserCurrentUrl = browser.url
                browserRequest = browser
            } else {
                browserRequest = null
                sheetRequest = NewLayoutExploreSheetRequest(
                    title = title,
                    sourceUrl = sourceUrl,
                    exploreUrl = exploreUrl,
                )
            }
        },
        onBookClick = onBookClick,
        paddingValues = paddingValues
    )

    val browser = browserRequest
    if (browser != null) {
        fun saveBrowserCookie(url: String?) {
            if (url.isNullOrBlank()) return
            CookieManager.getInstance().getCookie(url)?.let { cookie ->
                CookieStore.setCookie(url, cookie)
            }
        }

        SourceLoginWebDialog(
            state = SourceLoginUiState(
                loading = false,
                title = browser.title,
                mode = SourceLoginMode.Web,
                webUrl = browser.url,
                headers = persistentMapOf(),
                webProgress = 0,
            ),
            onIntent = { intent ->
                when (intent) {
                    is SourceLoginIntent.WebPageStarted -> {
                        browserCurrentUrl = intent.url
                        saveBrowserCookie(intent.url)
                    }

                    is SourceLoginIntent.WebPageFinished -> {
                        browserCurrentUrl = intent.url
                        saveBrowserCookie(intent.url)
                    }

                    SourceLoginIntent.Confirm -> {
                        saveBrowserCookie(browserCurrentUrl)
                        browserRequest = null
                        browserCurrentUrl = null
                        onIntent(ExploreIntent.RefreshSuite)
                    }

                    SourceLoginIntent.Back -> {
                        saveBrowserCookie(browserCurrentUrl)
                        browserRequest = null
                        browserCurrentUrl = null
                        onIntent(ExploreIntent.RefreshSuite)
                    }

                    else -> Unit
                }
            },
            onOpenExternalUrl = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
        )
    }

    val request = sheetRequest
    if (request != null && browser == null) {
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
