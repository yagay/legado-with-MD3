package io.legado.app.enhance.explore.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
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
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.enhance.explore.builder.ModernExploreControlExtractor
import io.legado.app.enhance.explore.model.DiscoverySuiteWidgetType
import io.legado.app.enhance.explore.ui.ModernDiscoveryFilterBar
import io.legado.app.ui.main.explore.ExploreIntent
import io.legado.app.ui.main.explore.ExploreViewModel
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.LoadMoreFooter
import io.legado.app.ui.widget.components.explore.DiscoverySuiteHeader
import io.legado.app.ui.widget.components.explore.DiscoverySuiteHorizontalBooksWidget
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverySuiteScreen(
    state: ExploreViewModel.ExploreUiState,
    onIntent: (ExploreIntent) -> Unit,
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onBookClick: (SearchBook, String?) -> Unit,
    paddingValues: PaddingValues,
) {
    val suite = state.enhance.selectedSuite
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val exploreKindUseCase: ExploreKindUiUseCase = koinInject()

    val mainBookWidget = remember(suite) {
        suite?.widgets?.find {
            it.type == DiscoverySuiteWidgetType.WaterfallBooks.type ||
                it.type == DiscoverySuiteWidgetType.BookList.type ||
                it.type == DiscoverySuiteWidgetType.HorizontalBooks.type
        }
    }
    val orderedExploreRows = remember(
        state.enhance.dynamicSelectors,
        state.enhance.dynamicControls,
    ) {
        buildOrderedExploreRows(
            selectors = state.enhance.dynamicSelectors,
            controls = state.enhance.dynamicControls,
        )
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

        if (mainBookWidget == null) {
            EmptyMessage(
                message = "该套件缺少书籍列表组件",
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
            orderedExploreRows.forEach { row ->
                when (row) {
                    is OrderedExploreRow.NativeControls -> {
                        item(key = row.key) {
                            AdaptiveExploreControlRows(
                                controls = row.controls,
                                sourceUrl = suite.defaultSourceUrl,
                                useCase = exploreKindUseCase,
                                onOpenUrl = { kind, url ->
                                    onOpenExploreShow(kind.title, suite.defaultSourceUrl.orEmpty(), url)
                                },
                                onRefreshKinds = { onIntent(ExploreIntent.RefreshSuite) },
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }

                    is OrderedExploreRow.StandaloneEntry -> {
                        item(key = row.key) {
                            AdaptiveExploreControlRows(
                                controls = listOf(row.entry),
                                sourceUrl = suite.defaultSourceUrl,
                                useCase = exploreKindUseCase,
                                onOpenUrl = { kind, url ->
                                    onOpenExploreShow(kind.title, suite.defaultSourceUrl.orEmpty(), url)
                                },
                                onRefreshKinds = { onIntent(ExploreIntent.RefreshSuite) },
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }

                    is OrderedExploreRow.Selector -> {
                        val selector = row.selector
                        item(key = selector.id) {
                            ModernDiscoveryFilterBar(
                                title = displayTitleForSelector(selector),
                                targets = selector.targets,
                                selectedTargetTitle = selector.selectedTitle,
                                onTargetClick = { target ->
                                    onIntent(
                                        ExploreIntent.SelectWidgetTarget(
                                            selector.id,
                                            target
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }

            suite.widgets.sortedBy { it.order }.forEach { widget ->
                val isMainBookWidget = widget.id == mainBookWidget?.id
                val suiteSearchBooks = state.enhance.suiteSearchBooks
                val books = if (isMainBookWidget && suiteSearchBooks != null) {
                    suiteSearchBooks
                } else {
                    state.enhance.widgetBooks[widget.id] ?: persistentListOf()
                }

                when (DiscoverySuiteWidgetType.from(widget.type)) {
                    DiscoverySuiteWidgetType.TagBar -> {
                        item(key = widget.id) {
                            ModernDiscoveryFilterBar(
                                title = widget.title,
                                targets = if (widget.isDynamic) {
                                    state.enhance.dynamicCategoryTargets.takeIf { it.isNotEmpty() }
                                        ?: widget.targets
                                } else {
                                    widget.targets
                                },
                                selectedTargetTitle = state.enhance.selectedWidgetTargets[widget.id],
                                onTargetClick = { target ->
                                    onIntent(ExploreIntent.SelectWidgetTarget(widget.id, target))
                                }
                            )
                        }
                    }

                    DiscoverySuiteWidgetType.RankButtons -> {
                        item(key = widget.id) {
                            val rankTargets = state.enhance.dynamicRankTargets
                            val groupIndex = if (widget.title == "榜单") 1 else 0
                            val group = rankTargets.getOrNull(groupIndex) ?: widget.targets

                            ModernDiscoveryFilterBar(
                                title = widget.title,
                                targets = group,
                                selectedTargetTitle = state.enhance.selectedWidgetTargets[widget.id],
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
                                        title = if (isMainBookWidget && state.enhance.suiteSearchBooks != null) {
                                            if (state.enhance.suiteSearchRemote) {
                                                "搜索：${state.searchKey.trim()}"
                                            } else {
                                                "当前页面：${state.searchKey.trim()}"
                                            }
                                        } else {
                                            buildExplorePathTitle(
                                                selectors = state.enhance.dynamicSelectors,
                                                fallback = widget.title
                                            )
                                        },
                                        onSettingsClick = { onIntent(ExploreIntent.ShowDiscoveryConfig(true)) },
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
                                                Spacer(modifier = Modifier.weight(1f))
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
                                if (state.enhance.suiteSearchBooks != null) {
                                    LoadMoreFooter(
                                        isLoading = state.enhance.suiteSearchLoading,
                                        errorMsg = null,
                                        onRetry = {
                                            if (state.enhance.suiteSearchRemote) {
                                                onIntent(ExploreIntent.LoadMoreSuiteSearch)
                                            }
                                        },
                                        isEnd = state.enhance.suiteSearchIsEnd,
                                        autoLoad = state.enhance.suiteSearchRemote
                                    )
                                } else {
                                    LoadMoreFooter(
                                        isLoading = state.enhance.widgetLoading[widget.id] ?: false,
                                        errorMsg = state.enhance.widgetErrors[widget.id] ?: state.enhance.exploreError,
                                        onRetry = { onIntent(ExploreIntent.LoadMoreWidgetData(widget.id)) },
                                        isEnd = state.enhance.widgetIsEnd[widget.id] ?: false,
                                        autoLoad = true
                                    )
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

private sealed interface OrderedExploreRow {
    data class Selector(
        val selector: ExploreViewModel.DynamicSelectorUi,
    ) : OrderedExploreRow

    data class NativeControls(
        val key: String,
        val controls: List<ExploreKind>,
    ) : OrderedExploreRow

    data class StandaloneEntry(
        val key: String,
        val entry: ExploreKind,
    ) : OrderedExploreRow
}

private sealed interface OrderedExploreAtom {
    val sourceIndex: Int
    val fallbackOrder: Int

    data class Selector(
        override val sourceIndex: Int,
        override val fallbackOrder: Int,
        val selector: ExploreViewModel.DynamicSelectorUi,
    ) : OrderedExploreAtom

    data class NativeControl(
        override val sourceIndex: Int,
        override val fallbackOrder: Int,
        val control: ExploreKind,
    ) : OrderedExploreAtom

    data class StandaloneEntry(
        override val sourceIndex: Int,
        override val fallbackOrder: Int,
        val entry: ExploreKind,
    ) : OrderedExploreAtom
}

/**
 * Keep source order within utility rows and within category rows, but place utility/native rows
 * before category selectors. This keeps normal category ordering/rules untouched while making
 * source-defined actions such as refresh, bookshelf and config entries immediately accessible.
 */
private fun buildOrderedExploreRows(
    selectors: List<ExploreViewModel.DynamicSelectorUi>,
    controls: List<ExploreKind>,
): List<OrderedExploreRow> {
    val standaloneEntries = ModernExploreControlExtractor.standaloneUrlEntries()
    if (selectors.isEmpty() && controls.isEmpty() && standaloneEntries.isEmpty()) return emptyList()

    val atoms = buildList<OrderedExploreAtom> {
        selectors.forEachIndexed { index, selector ->
            val filteredTargets = selector.targets.filterNot { target ->
                ModernExploreControlExtractor.isStandaloneUrlTarget(
                    title = target.title,
                    url = target.tagUrl,
                )
            }
            if (filteredTargets.isEmpty()) return@forEachIndexed
            val visibleSelector = if (filteredTargets.size == selector.targets.size) {
                selector
            } else {
                selector.copy(targets = filteredTargets.toImmutableList())
            }
            add(
                OrderedExploreAtom.Selector(
                    sourceIndex = sourceIndexOfSelector(visibleSelector),
                    fallbackOrder = index * 3,
                    selector = visibleSelector,
                )
            )
        }
        controls.forEachIndexed { index, control ->
            add(
                OrderedExploreAtom.NativeControl(
                    sourceIndex = ModernExploreControlExtractor.sourceIndexOf(control),
                    fallbackOrder = index * 3 + 1,
                    control = control,
                )
            )
        }
        standaloneEntries.forEachIndexed { index, entry ->
            add(
                OrderedExploreAtom.StandaloneEntry(
                    sourceIndex = ModernExploreControlExtractor.sourceIndexOf(entry),
                    fallbackOrder = index * 3 + 2,
                    entry = entry,
                )
            )
        }
    }.sortedWith(
        compareBy<OrderedExploreAtom>(
            { if (it is OrderedExploreAtom.Selector) 1 else 0 },
            { if (it.sourceIndex >= 0) it.sourceIndex else Int.MAX_VALUE },
            { it.fallbackOrder },
        )
    )

    val rows = mutableListOf<OrderedExploreRow>()
    var pendingControls = mutableListOf<ExploreKind>()
    var pendingIndexes = mutableListOf<Int>()

    fun flushControls() {
        if (pendingControls.isEmpty()) return
        rows += OrderedExploreRow.NativeControls(
            key = "dynamic_native_${pendingIndexes.joinToString("_")}",
            controls = pendingControls.toList(),
        )
        pendingControls = mutableListOf()
        pendingIndexes = mutableListOf()
    }

    atoms.forEach { atom ->
        when (atom) {
            is OrderedExploreAtom.Selector -> {
                flushControls()
                rows += OrderedExploreRow.Selector(atom.selector)
            }

            is OrderedExploreAtom.NativeControl -> {
                pendingControls += atom.control
                pendingIndexes += atom.sourceIndex.takeIf { it >= 0 } ?: atom.fallbackOrder
            }

            is OrderedExploreAtom.StandaloneEntry -> {
                flushControls()
                val entryIndex = atom.sourceIndex.takeIf { it >= 0 } ?: atom.fallbackOrder
                rows += OrderedExploreRow.StandaloneEntry(
                    key = "dynamic_entry_$entryIndex",
                    entry = atom.entry,
                )
            }
        }
    }
    flushControls()
    return rows
}

private fun sourceIndexOfSelector(selector: ExploreViewModel.DynamicSelectorUi): Int {
    if (selector.id.startsWith("dynamic_select_")) {
        val selectIndex = ModernExploreControlExtractor.sourceIndexOfSelect(selector.title)
        if (selectIndex >= 0) return selectIndex
    }

    return selector.targets.asSequence()
        .map { target ->
            ModernExploreControlExtractor.sourceIndexOfTarget(
                title = target.title,
                url = target.tagUrl,
            )
        }
        .filter { it >= 0 }
        .minOrNull()
        ?: -1
}

private fun displayTitleForSelector(selector: ExploreViewModel.DynamicSelectorUi): String {
    if (selector.id != "dynamic_level_0") return selector.title
    val sourceIndex = sourceIndexOfSelector(selector)
    return ModernExploreControlExtractor.structuralParentSelectionBefore(sourceIndex)
        ?: selector.title
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
        .replace(Regex("[\\[\\]【】?（）<>《》]"), "")
        .replace(Regex("[\\p{So}\\p{Sk}]+"), "")
        .replace(Regex("[༺༻ˇ»«`´ʚɞ]+"), "")
        .trim()
}