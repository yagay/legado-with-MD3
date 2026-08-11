package io.legado.app.ui.config.downloadCacheConfig

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.enhance.settingssearch.SettingDestination
import io.legado.app.enhance.settingssearch.getSettingScrollInfo
import io.legado.app.enhance.ui.LaunchSettingScrollEffect
import io.legado.app.model.CacheBook
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.androidx.compose.koinViewModel

@Composable
fun DownloadCacheConfigRouteScreen(
    onBackClick: () -> Unit,
    searchKey: String? = null,
    viewModel: DownloadCacheConfigViewModel = koinViewModel(),
) {
    DownloadCacheConfigScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        searchKey = searchKey,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadCacheConfigScreen(
    state: DownloadCacheConfigUiState,
    onIntent: (DownloadCacheConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    searchKey: String? = null,
) {
    val settings = state.settings
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val scrollInfo = remember(searchKey) {
        getSettingScrollInfo(context, SettingDestination.DownloadCache, searchKey)
    }
    LaunchSettingScrollEffect(scrollInfo, listState)

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.download_cache_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.http_cache)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.cover_cache),
                        description = stringResource(
                            R.string.cache_size_mb,
                            state.coverCacheSizeMb
                        ),
                        highlightKey = searchKey,
                        onClick = {
                            onIntent(
                                DownloadCacheConfigIntent.ShowDialog(
                                    DownloadCacheConfigDialog.ClearCoverCache
                                )
                            )
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.manga_cache),
                        description = stringResource(
                            R.string.cache_size_mb,
                            state.mangaCacheSizeMb
                        ),
                        highlightKey = searchKey,
                        onClick = {
                            onIntent(
                                DownloadCacheConfigIntent.ShowDialog(
                                    DownloadCacheConfigDialog.ClearMangaCache
                                )
                            )
                        }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.download_setting)) {
                    SliderSettingItem(
                        title = stringResource(R.string.threads_num_title),
                        description = stringResource(R.string.threads_num_summary),
                        value = settings.threadCount.toFloat(),
                        defaultValue = 8f,
                        valueRange = 1f..256f,
                        highlightKey = searchKey,
                        onValueChange = {
                            onIntent(DownloadCacheConfigIntent.SetThreadCount(it.toInt()))
                        }
                    )

                    SliderSettingItem(
                        title = stringResource(R.string.cache_book_threads_num_title),
                        description = stringResource(R.string.cache_book_threads_num_summary),
                        value = settings.cacheBookThreadCount
                            .coerceIn(1, CacheBook.maxDownloadConcurrency)
                            .toFloat(),
                        defaultValue = 4f,
                        valueRange = 1f..CacheBook.maxDownloadConcurrency.toFloat(),
                        highlightKey = searchKey,
                        onValueChange = {
                            onIntent(DownloadCacheConfigIntent.SetCacheBookThreadCount(it.toInt()))
                        }
                    )
                }
            }
        }
    }
}
