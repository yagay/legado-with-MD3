package io.legado.app.ui.main.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.LoadMoreFooter
import io.legado.app.ui.widget.components.explore.DiscoverySuiteHeader
import io.legado.app.ui.widget.components.explore.DiscoverySuiteHorizontalBooksWidget
import io.legado.app.ui.widget.components.explore.DiscoverySuiteRankButtonsWidget
import io.legado.app.ui.widget.components.explore.DiscoverySuiteTagBarWidget
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverySuiteScreen(
    state: ExploreViewModel.ExploreUiState,
    onIntent: (ExploreIntent) -> Unit,
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onBookClick: (SearchBook, String?) -> Unit,
    paddingValues: PaddingValues
) {
    val suite = state.selectedSuite
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val mainBookWidget = remember(suite) {
        suite?.widgets?.find {
            it.type == DiscoverySuiteWidgetType.WaterfallBooks.type ||
                it.type == DiscoverySuiteWidgetType.BookList.type ||
                it.type == DiscoverySuiteWidgetType.HorizontalBooks.type
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (suite == null) {
            AppContainedLoadingIndicator()
            return@Box
        }

        if (suite.widgets.isEmpty()) {
            EmptyMessage(
                message = "该套件没有配置组件",
                modifier = Modifier.fillMaxSize()
            )
            return@Box
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 80.dp
            )
        ) {
            state.dynamicSelectors.forEach { selector ->
                item(key = selector.id) {
                    DiscoverySuiteTagBarWidget(
                        title = selector.title,
                        targets = selector.targets,
                        selectedTargetTitle = selector.selectedTitle,
                        onTargetClick = { target ->
                            onIntent(ExploreIntent.SelectWidgetTarget(selector.id, target))
                        }
                    )
                }
            }

            suite.widgets.sortedBy { it.order }.forEach { widget ->
                val isMainBookWidget = widget.id == mainBookWidget?.id
                val books = if (isMainBookWidget && state.suiteSearchBooks != null) {
                    state.suiteSearchBooks
                } else {
                    state.widgetBooks[widget.id] ?: persistentListOf()
                }

                when (DiscoverySuiteWidgetType.from(widget.type)) {
                    DiscoverySuiteWidgetType.TagBar -> {
                        item(key = widget.id) {
                            DiscoverySuiteTagBarWidget(
                                title = widget.title,
                                targets = if (widget.isDynamic) {
                                    state.dynamicCategoryTargets.takeIf { it.isNotEmpty() } ?: widget.targets
                                } else widget.targets,
                                selectedTargetTitle = state.selectedWidgetTargets[widget.id],
                                onTargetClick = { target ->
                                    onIntent(ExploreIntent.SelectWidgetTarget(widget.id, target))
                                }
                            )
                        }
                    }

                    DiscoverySuiteWidgetType.RankButtons -> {
                        item(key = widget.id) {
                            val groupIndex = if (widget.title == "榜单") 1 else 0
                            val group = state.dynamicRankTargets.getOrNull(groupIndex) ?: widget.targets
                            DiscoverySuiteRankButtonsWidget(
                                title = widget.title,
                                targets = group,
                                selectedTargetTitle = state.selectedWidgetTargets[widget.id],
                                onTargetClick = { target ->
                                    onIntent(ExploreIntent.SelectWidgetTarget(widget.id, target))
                                }
                            )
                        }
                    }

                    DiscoverySuiteWidgetType.HorizontalBooks,
                    DiscoverySuiteWidgetType.WaterfallBooks,
                    DiscoverySuiteWidgetType.BookList -> {
                        if (widget.title.isNotEmpty()) {
                            stickyHeader(key = "${widget.id}_header") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    DiscoverySuiteHeader(
                                        title = if (isMainBookWidget && state.suiteSearchBooks != null) {
                                            if (state.suiteSearchRemote) {
                                                "搜索：${state.searchKey.trim()}"
                                            } else {
                                                "当前页面：${state.searchKey.trim()}"
                                            }
                                        } else {
                                            buildExplorePathTitle(state.dynamicSelectors, widget.title)
                                        },
                                        onSettingsClick = {
                                            onIntent(ExploreIntent.ShowDiscoveryConfig(true))
                                        },
                                        onScrollToTop = {
                                            scope.launch {
                                                if (listState.firstVisibleItemIndex > 15) {
                                                    listState.scrollToItem(15)
                                                }
                                                listState.animateScrollToItem(0)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        when (widget.displayStyle) {
                            1 -> {
                                itemsIndexed(
                                    items = books,
                                    key = { index, book -> "${widget.id}_li_${book.bookUrl}_$index" }
                                ) { index, book ->
                                    val sharedCoverKey = io.legado.app.ui.main.bookCoverSharedElementKey(
                                        book.bookUrl,
                                        "discovery:list:${widget.id}:$index"
                                    )
                                    io.legado.app.ui.widget.components.book.SearchBookListItem(
                                        book = book,
                                        shelfState = BookShelfState.NOT_IN_SHELF,
                                        onClick = { onBookClick(book, sharedCoverKey) },
                                        showPadding = true,
                                        coverHeight = widget.coverHeight.dp,
                                        adaptContentToCoverHeight = widget.type == DiscoverySuiteWidgetType.WaterfallBooks.type
                                    )
                                }
                            }

                            2 -> {
                                val chunks = books.chunked(widget.gridCount)
                                itemsIndexed(
                                    items = chunks,
                                    key = { index, chunk -> "${widget.id}_gr_${index}_${chunk.firstOrNull()?.bookUrl}" }
                                ) { _, chunk ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        chunk.forEach { book ->
                                            io.legado.app.ui.widget.components.book.SearchBookGridItem(
                                                book = book,
                                                shelfState = BookShelfState.NOT_IN_SHELF,
                                                onClick = { onBookClick(book, null) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (chunk.size < widget.gridCount) {
                                            repeat(widget.gridCount - chunk.size) {
                                                androidx.compose.foundation.layout.Spacer(
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                item(key = widget.id) {
                                    DiscoverySuiteHorizontalBooksWidget(
                                        books = books,
                                        onBookClick = { book -> onBookClick(book, null) }
                                    )
                                }
                            }
                        }

                        if (widget.id == mainBookWidget?.id) {
                            item(key = "${widget.id}_footer") {
                                if (state.suiteSearchBooks != null) {
                                    LoadMoreFooter(
                                        isLoading = state.suiteSearchLoading,
                                        errorMsg = null,
                                        onRetry = {
                                            if (state.suiteSearchRemote) {
                                                onIntent(ExploreIntent.LoadMoreSuiteSearch)
                                            }
                                        },
                                        isEnd = state.suiteSearchIsEnd,
                                        autoLoad = state.suiteSearchRemote
                                    )
                                } else {
                                    LoadMoreFooter(
                                        isLoading = state.widgetLoading[widget.id] ?: false,
                                        errorMsg = null,
                                        onRetry = {
                                            onIntent(ExploreIntent.LoadMoreWidgetData(widget.id))
                                        },
                                        isEnd = state.widgetIsEnd[widget.id] ?: false,
                                        autoLoad = true
                                    )
                                }
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}

private fun buildExplorePathTitle(
    selectors: List<ExploreViewModel.DynamicSelectorUi>,
    fallback: String
): String {
    val path = selectors
        .mapNotNull { it.selectedTitle }
        .map(::cleanExplorePathPart)
        .filter { it.isNotBlank() }
        .distinct()

    return path.takeIf { it.isNotEmpty() }
        ?.joinToString(" > ")
        ?: fallback
}

private fun cleanExplorePathPart(title: String): String {
    return title
        .replace(Regex("[\\[\\]【】()（）<>《》]"), "")
        .replace(Regex("[\\p{So}\\p{Sk}]+"), "")
        .replace(Regex("[༺༻ˇ»«`´ʚɞ]+"), "")
        .trim()
}
