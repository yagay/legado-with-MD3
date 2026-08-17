package io.legado.app.ui.main.explore

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.getExploreInfoMap
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem
import io.legado.app.ui.widget.components.explore.calculateExploreKindRows
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.enhance.explore.screen.ExploreConfigEnhance
import io.legado.app.enhance.explore.screen.ExploreScreenEnhance
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun ExploreRouteScreen(
    viewModel: ExploreViewModel = koinViewModel(),
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onOpenLogin: (sourceUrl: String) -> Unit,
    onOpenEdit: (sourceUrl: String) -> Unit,
    onOpenSearch: (scopeRaw: String) -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String, origin: String?, coverPath: String?, sharedCoverKey: String?) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exploreKindUseCase: ExploreKindUiUseCase = koinInject()

    LaunchedEffect(viewModel, activity, exploreKindUseCase) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExploreEffect.ExecuteKindAction -> {
                    val infoMap = getExploreInfoMap(effect.sourceUrl)
                    exploreKindUseCase.executeAction(
                        action = effect.kind.action,
                        title = effect.kind.title,
                        sourceUrl = effect.sourceUrl,
                        infoMap = infoMap,
                        activity = activity,
                        onRefreshKinds = {
                            uiState.items.firstOrNull { it.bookSourceUrl == effect.sourceUrl }
                                ?.let { viewModel.onIntent(ExploreIntent.RefreshKinds(it)) }
                        }
                    )
                }
                is ExploreEffect.OpenEdit -> {
                    onOpenEdit(effect.sourceUrl)
                }
                is ExploreEffect.OpenSearch -> {
                    onOpenSearch(SearchScope(effect.source).toString())
                }
                is ExploreEffect.OpenLogin -> {
                    onOpenLogin(effect.sourceUrl)
                }
                is ExploreEffect.OpenBookInfo -> {
                    onOpenBookInfo(
                        effect.name,
                        effect.author,
                        effect.bookUrl,
                        effect.origin,
                        effect.coverPath,
                        effect.sharedCoverKey
                    )
                }
            }
        }
    }

    var showSourceMenu by remember { mutableStateOf(false) }

    ExploreScreen(
        state = uiState,
        onIntent = viewModel::onIntent,
        onOpenExploreShow = onOpenExploreShow,
        onOpenBookInfo = onOpenBookInfo,
        onShowSourceMenu = { showSourceMenu = true },
        onBookClick = { book, sharedCoverKey ->
            viewModel.onIntent(ExploreIntent.OpenBook(book, sharedCoverKey))
        }
    )

}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    state: ExploreViewModel.ExploreUiState,
    onIntent: (ExploreIntent) -> Unit,
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String, origin: String?, coverPath: String?, sharedCoverKey: String?) -> Unit,
    onShowSourceMenu: () -> Unit = {},
    onBookClick: (SearchBook, String?) -> Unit = { _, _ -> }
) {
    var sourceToDeleteUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val sourceToDelete = remember(sourceToDeleteUrl, state.items) {
        state.items.firstOrNull { it.bookSourceUrl == sourceToDeleteUrl }
    }
    // Long-pressing a source in the waterfall source picker switches the SAME
    // popup into the standard source action menu instead of opening a nested popup.
    // This avoids the double-popup problem while keeping the exact same actions
    // available as the list layout's source long-press menu.
    var sourceActionMenuUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val sourceActionMenuSource = remember(sourceActionMenuUrl, state.items) {
        state.items.firstOrNull { it.bookSourceUrl == sourceActionMenuUrl }
    }
    val scope = rememberCoroutineScope()
    val previewExploreKindUseCase: ExploreKindUiUseCase = koinInject()
    var sourceKindPreviewUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceKindPreviewKinds by remember { mutableStateOf<List<ExploreKind>>(emptyList()) }
    var sourceKindPreviewLoading by remember { mutableStateOf(false) }
    val sourceKindPreviewSource = remember(sourceKindPreviewUrl, state.items) {
        state.items.firstOrNull { it.bookSourceUrl == sourceKindPreviewUrl }
    }
    LaunchedEffect(sourceKindPreviewUrl, sourceKindPreviewSource) {
        val source = sourceKindPreviewSource
        if (sourceKindPreviewUrl == null || source == null) {
            sourceKindPreviewKinds = emptyList()
            sourceKindPreviewLoading = false
            return@LaunchedEffect
        }
        sourceKindPreviewLoading = true
        sourceKindPreviewKinds = runCatching { source.exploreKinds() }.getOrDefault(emptyList())
        sourceKindPreviewLoading = false
    }
    val sourceKindPreviewRows = remember(sourceKindPreviewKinds) {
        calculateExploreKindRows(sourceKindPreviewKinds, maxSpan = 6)
    }

    val composeEngine = ThemeResolver.isMiuixEngine(composeEngine)

    // Source picker popup is lazy-rendered for speed, but must have a concrete
    // size so Popup/DropdownMenu never asks LazyColumn for intrinsic sizes.
    // Width follows the longest visible source name; height grows with item
    // count and stops at a comfortable maximum, so one/few sources do not
    // leave a giant empty panel.
    val sourceMenuItems = remember(state.items) {
        state.items.distinctBy { it.bookSourceUrl }
    }
    var sourceMenuQuery by rememberSaveable { mutableStateOf("") }
    val filteredSourceMenuItems = remember(sourceMenuItems, sourceMenuQuery) {
        val query = sourceMenuQuery.trim()
        if (query.isEmpty()) {
            sourceMenuItems
        } else {
            sourceMenuItems.filter { source ->
                source.bookSourceName.contains(query, ignoreCase = true)
            }
        }
    }
    val sourceMenuListState = rememberLazyListState()
    var sourceMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val defaultSourceIndex = remember(filteredSourceMenuItems, state.enhance.selectedSuite?.defaultSourceUrl) {
        filteredSourceMenuItems.indexOfFirst {
            it.bookSourceUrl == state.enhance.selectedSuite?.defaultSourceUrl
        }
    }
    LaunchedEffect(sourceMenuExpanded, defaultSourceIndex, sourceActionMenuSource) {
        if (sourceMenuExpanded && sourceActionMenuSource == null && defaultSourceIndex >= 0) {
            // Miuix adds one outer spacer before the shared header item.
            val menuPrefixCount = if (composeEngine) 2 else 1
            sourceMenuListState.scrollToItem(defaultSourceIndex + menuPrefixCount)
        }
    }
    val configuration = LocalConfiguration.current
    val sourceMenuMaxHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp - 96.dp).coerceAtLeast(124.dp)
    }
    val sourceActionRowCount = remember(sourceActionMenuSource) {
        sourceActionMenuSource?.let { exploreSourceActionRowCount(it, includeBack = true) } ?: 0
    }
    val sourceMenuHeight = remember(filteredSourceMenuItems.size, sourceActionRowCount, sourceMenuMaxHeight) {
        val rowCount = if (sourceActionRowCount > 0) sourceActionRowCount else filteredSourceMenuItems.size
        val baseHeight = if (sourceActionRowCount > 0) 68 else 132
        (baseHeight + rowCount * 56).dp.coerceIn(124.dp, sourceMenuMaxHeight)
    }
    // Fixed width avoids the visible resize flash while long names remain single-line in menu items.
    val sourcePopupWidth = 280.dp

    ListScaffold(
        title = stringResource(R.string.discovery),
        state = state,
        subtitle = if (state.layoutMode == 0) state.selectedGroup.ifEmpty { stringResource(R.string.all) } else state.enhance.selectedSourceName ?: state.enhance.selectedSuite?.displayName,
        subtitleDropdownMenuWidth = sourcePopupWidth,
        subtitleDropdownMenuHeight = sourceMenuHeight,
        subtitleDropdownMenuState = sourceMenuListState,
        subtitleDropdownMenuFastScroll = state.layoutMode == 1,
        subtitleDropdownMenuFixedHeader = if (state.layoutMode == 1 && sourceActionMenuSource == null) {
            {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    OutlinedTextField(
                        value = sourceMenuQuery,
                        onValueChange = { sourceMenuQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        placeholder = { Text(stringResource(R.string.search)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        singleLine = true
                    )
                }
            }
        } else null,
        subtitleMenuExpanded = if (state.layoutMode == 1) sourceMenuExpanded else null,
        onSubtitleMenuExpandedChange = if (state.layoutMode == 1) {
            { expanded ->
                sourceMenuExpanded = expanded
                if (!expanded) {
                    sourceActionMenuUrl = null
                    sourceMenuQuery = ""
                }
            }
        } else null,
        onSubtitleLongClick = if (state.layoutMode == 1) {
            {
                val sourceUrl = state.enhance.selectedSuite?.defaultSourceUrl
                    ?: state.items.firstOrNull()?.bookSourceUrl
                if (sourceUrl != null) {
                    sourceMenuExpanded = false
                    sourceActionMenuUrl = null
                    sourceMenuQuery = ""
                    sourceKindPreviewUrl = sourceUrl
                }
            }
        } else null,
        subtitleDropdownMenuLazy = if (state.layoutMode == 1) {
            { dismiss ->
                val actionSource = sourceActionMenuSource
                if (actionSource == null) {
                    item(key = "source_menu_header") {
                        PillHeaderDivider(title = "选择首页源")
                    }
                    items(
                        items = filteredSourceMenuItems,
                        key = { it.bookSourceUrl }
                    ) { source ->
                        RoundDropdownMenuItem(
                            text = source.bookSourceName,
                            marquee = true,
                            isSelected = source.bookSourceUrl == state.enhance.selectedSuite?.defaultSourceUrl,
                            onClick = {
                                sourceActionMenuUrl = null
                                onIntent(ExploreIntent.SetSuiteDefaultSource(source.bookSourceUrl))
                                dismiss()
                            },
                            onLongClick = {
                                // Do not create a second Popup. Reuse this popup and swap
                                // its content to the same action set used by list headers.
                                sourceActionMenuUrl = source.bookSourceUrl
                                scope.launch { sourceMenuListState.scrollToItem(0) }
                            }
                        )
                    }
                } else {
                    item(key = "source_action_menu_${actionSource.bookSourceUrl}") {
                        ExploreSourceActionMenuContent(
                            source = actionSource,
                            onTop = { onIntent(ExploreIntent.TopSource(actionSource)) },
                            onEdit = { onIntent(ExploreIntent.OpenEdit(actionSource)) },
                            onSearch = { onIntent(ExploreIntent.OpenSearch(actionSource)) },
                            onLogin = { onIntent(ExploreIntent.OpenLogin(actionSource)) },
                            onSetHomeSource = { onIntent(ExploreIntent.SetSuiteDefaultSource(actionSource.bookSourceUrl)) },
                            onRefresh = { onIntent(ExploreIntent.RefreshKinds(actionSource)) },
                            onDelete = { sourceToDeleteUrl = actionSource.bookSourceUrl },
                            onDismiss = {
                                sourceActionMenuUrl = null
                                dismiss()
                            },
                            onBack = { sourceActionMenuUrl = null },
                        )
                    }
                }
            }
        } else null,
        onSearchQueryChange = { onIntent(ExploreIntent.Search(it)) },
        onSearchToggle = { onIntent(ExploreIntent.ToggleSearch(it)) },
        searchTrailingIcon = if (state.layoutMode == 1) {
            {
                var showSearchFieldMenu by remember { mutableStateOf(false) }
                Box {
                    TopBarActionButton(
                        onClick = { showSearchFieldMenu = true },
                        imageVector = Icons.Default.FilterAlt,
                        contentDescription = when (state.enhance.suiteSearchField) {
                            "author" -> "搜索作者"
                            "kind" -> "搜索分类"
                            else -> "搜索书籍名"
                        }
                    )
                    RoundDropdownMenu(
                        expanded = showSearchFieldMenu,
                        onDismissRequest = { showSearchFieldMenu = false }
                    ) {
                        listOf(
                            "name" to "书籍名",
                            "author" to "作者",
                            "kind" to "分类"
                        ).forEach { (field, label) ->
                            RoundDropdownMenuItem(
                                text = label,
                                isSelected = state.enhance.suiteSearchField == field,
                                onClick = {
                                    onIntent(ExploreIntent.SetSuiteSearchField(field))
                                    showSearchFieldMenu = false
                                }
                            )
                        }
                    }
                }
            }
        } else null,
        searchPlaceholder = if (state.layoutMode == 1) {
            when (state.enhance.suiteSearchField) {
                "author" -> "搜索作者"
                "kind" -> "搜索分类"
                else -> "搜索书籍名"
            }
        } else stringResource(R.string.search),
        topBarActions = {
            if (state.layoutMode == 1) {
                TopBarActionButton(
                    onClick = { onIntent(ExploreIntent.RefreshSuite) },
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.refresh)
                )
            }
            if (state.layoutSwitcherEnabled) {
                TopBarActionButton(
                    onClick = { onIntent(ExploreIntent.ToggleLayoutMode) },
                    imageVector = if (state.layoutMode == 0) Icons.Default.Dashboard else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = stringResource(R.string.a11y_switch_layout)
                )
            }
        },
        dropDownMenuContent = { dismiss ->
            // 普通列表和瀑布流统一使用同一套“书源分组”菜单。
            // 在瀑布流模式下，选择分组只负责筛选左上角“发现”标题下拉菜单
            // 中可见的书源名称；真正切换书源仍由左上角下拉菜单完成。
            // 这样两个布局的右上角三点菜单内容和功能完全一致，也避免维护两份逻辑。
            RoundDropdownMenuItem(
                leadingIcon = { MenuItemIcon(Icons.Default.Group) },
                text = stringResource(R.string.all),
                onClick = { onIntent(ExploreIntent.SetGroup("")); dismiss() }
            )
            state.groups.forEach { group ->
                RoundDropdownMenuItem(
                    leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Outlined.Label) },
                    text = group,
                    onClick = { onIntent(ExploreIntent.SetGroup(group)); dismiss() }
                )
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        if (state.layoutMode == 1) {
            ExploreScreenEnhance(
                state = state,
                onIntent = onIntent,
                onOpenExploreShow = onOpenExploreShow,
                onBookClick = onBookClick,
                paddingValues = paddingValues
            )
        } else {
            ExploreListContent(
                state = state,
                onIntent = onIntent,
                onOpenExploreShow = onOpenExploreShow,
                onDeleteSource = { sourceToDeleteUrl = it.bookSourceUrl },
                paddingValues = paddingValues,
                isMiuix = composeEngine
            )
        }
    }


    AppAlertDialog(
        data = sourceToDelete,
        onDismissRequest = { sourceToDeleteUrl = null },
        title = stringResource(R.string.sure_del),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { source ->
            onIntent(ExploreIntent.DeleteSource(source))
            sourceToDeleteUrl = null
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { sourceToDeleteUrl = null },
    )

    AppModalBottomSheet(
        show = state.layoutMode == 1 && sourceKindPreviewUrl != null,
        onDismissRequest = {
            sourceKindPreviewUrl = null
            sourceKindPreviewKinds = emptyList()
        },
        title = sourceKindPreviewSource?.bookSourceName ?: state.enhance.selectedSourceName,
    ) {
        when {
            sourceKindPreviewLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppContainedLoadingIndicator()
                }
            }

            sourceKindPreviewRows.isEmpty() -> {
                Text(
                    text = "该书源没有发现分类",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    sourceKindPreviewRows.forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowItems.forEach { (kind, span) ->
                                ExploreKindMultiTypeItem(
                                    kind = kind,
                                    sourceUrl = sourceKindPreviewUrl,
                                    onOpenUrl = { url ->
                                        val sourceUrl = sourceKindPreviewUrl.orEmpty()
                                        sourceKindPreviewUrl = null
                                        sourceKindPreviewKinds = emptyList()
                                        onOpenExploreShow(kind.title, sourceUrl, url)
                                    },
                                    onRefreshKinds = {
                                        sourceKindPreviewUrl = null
                                        sourceKindPreviewKinds = emptyList()
                                        onIntent(ExploreIntent.RefreshSuite)
                                    },
                                    modifier = Modifier.weight(span.toFloat()),
                                    isMiuix = composeEngine,
                                    useCase = previewExploreKindUseCase,
                                )
                            }
                            val totalSpan = rowItems.sumOf { it.second }
                            if (totalSpan < 6) {
                                Spacer(modifier = Modifier.weight((6 - totalSpan).toFloat()))
                            }
                        }
                    }
                }
            }
        }
    }

    ExploreConfigEnhance(state, onIntent)
}

/**
 * 抽取上游原始列表内容，方便后续同步上游 UI 修改
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreListContent(
    state: ExploreViewModel.ExploreUiState,
    onIntent: (ExploreIntent) -> Unit,
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onDeleteSource: (BookSourcePart) -> Unit,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    isMiuix: Boolean
) {
    val listItems by remember(state.items, state.expandedId, state.exploreKinds) {
        derivedStateOf { buildExploreListItems(state) }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val stickyHeaderSource by remember(listItems, state.items) {
        derivedStateOf {
            val firstIndex = listState.firstVisibleItemIndex
            val item = listItems.getOrNull(firstIndex)
            if (item is ExploreListItem.KindRow) {
                state.items.find { it.bookSourceUrl == item.sourceUrl }
            } else {
                null
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.items.isEmpty()) {
            EmptyMessage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                messageResId = R.string.explore_empty
            )
            return@Box
        }

        FastScrollLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            items(
                items = listItems,
                key = { it.key }
            ) { listItem ->
                when (listItem) {
                    is ExploreListItem.Header -> {
                        val item = listItem.source
                        val isExpanded = state.expandedId == item.bookSourceUrl
                        ExploreSourceHeader(
                            modifier = Modifier.animateItem(),
                            item = item,
                            isExpanded = isExpanded,
                            loadingKinds = if (isExpanded) state.loadingKinds else false,
                            onClick = { onIntent(ExploreIntent.ToggleExpand(item)) },
                            onTop = { onIntent(ExploreIntent.TopSource(item)) },
                            onEdit = { onIntent(ExploreIntent.OpenEdit(item)) },
                            onSearch = { onIntent(ExploreIntent.OpenSearch(item)) },
                            onLogin = { onIntent(ExploreIntent.OpenLogin(item)) },
                            onSetHomeSource = { onIntent(ExploreIntent.SetSuiteDefaultSource(item.bookSourceUrl)) },
                            onRefresh = { onIntent(ExploreIntent.RefreshKinds(item)) },
                            onDelete = { onDeleteSource(item) },
                            isMiuix = isMiuix
                        )
                    }

                    is ExploreListItem.KindRow -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listItem.rowItems.forEach { (kind, span) ->
                                ExploreKindMultiTypeItem(
                                    kind = kind,
                                    sourceUrl = listItem.sourceUrl,
                                    onOpenUrl = { url ->
                                        onOpenExploreShow(kind.title, listItem.sourceUrl, url)
                                    },
                                    modifier = Modifier.weight(span.toFloat()),
                                    isMiuix = isMiuix,
                                    displayNameOverride = state.kindDisplayNames[kind.title],
                                    valueOverride = state.kindValues[kind.title],
                                    onValueChange = { value ->
                                        onIntent(
                                            ExploreIntent.UpdateKindValue(
                                                listItem.sourceUrl,
                                                kind,
                                                value,
                                            )
                                        )
                                    },
                                    onRunAction = {
                                        onIntent(
                                            ExploreIntent.RunKindAction(listItem.sourceUrl, kind)
                                        )
                                    }
                                )
                            }

                            val totalSpan = listItem.rowItems.sumOf { it.second }
                            if (totalSpan < 6) {
                                Spacer(
                                    modifier = Modifier.weight((6 - totalSpan).toFloat())
                                )
                            }
                        }
                    }
                }
            }
        }

        TopFloatingStickyItem(
            item = stickyHeaderSource,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = paddingValues.calculateTopPadding() + 4.dp, start = 8.dp)
        ) { item ->
            TextCard(
                text = item.bookSourceName,
                textStyle = LegadoTheme.typography.labelMediumEmphasized,
                cornerRadius = 12.dp,
                horizontalPadding = 12.dp,
                verticalPadding = 8.dp,
                modifier = Modifier.semantics {
                    contentDescription = item.bookSourceName
                    role = Role.Button
                },
                onClick = {
                    scope.launch {
                        val index = listItems.indexOfFirst {
                            it is ExploreListItem.Header && it.source.bookSourceUrl == item.bookSourceUrl
                        }
                        if (index >= 0) listState.animateScrollToItem(index)
                    }
                }
            )
        }
    }
}

sealed interface ExploreListItem {
    val key: String

    data class Header(val source: BookSourcePart) : ExploreListItem {
        override val key: String = "header_${source.bookSourceUrl}"
    }

    data class KindRow(
        val sourceUrl: String,
        val rowIndex: Int,
        val rowItems: List<Pair<ExploreKind, Int>>
    ) : ExploreListItem {
        override val key: String = "kind_${sourceUrl}_$rowIndex"
    }
}

private fun buildExploreListItems(state: ExploreViewModel.ExploreUiState): List<ExploreListItem> {
    val list = mutableListOf<ExploreListItem>()
    state.items.forEach { source ->
        list.add(ExploreListItem.Header(source))
        if (state.expandedId == source.bookSourceUrl) {
            val rows = calculateExploreKindRows(state.exploreKinds, maxSpan = 6)
            rows.forEachIndexed { index, rowItems ->
                list.add(ExploreListItem.KindRow(source.bookSourceUrl, index, rowItems))
            }
        }
    }
    return list
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreSourceHeader(
    modifier: Modifier = Modifier,
    item: BookSourcePart,
    isExpanded: Boolean,
    loadingKinds: Boolean,
    onClick: () -> Unit,
    onTop: () -> Unit,
    onEdit: () -> Unit,
    onSearch: () -> Unit,
    onLogin: () -> Unit,
    onSetHomeSource: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    isMiuix: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (isExpanded) 90f else 0f, label = "rotation")
    val expandActionLabel = stringResource(if (isExpanded) R.string.collapse else R.string.expand)
    val loadingLabel = stringResource(R.string.loading)
    val moreMenuLabel = stringResource(R.string.more_menu)

    val containerColor by animateColorAsState(
        targetValue = if (isExpanded)
            if (isMiuix) MiuixTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer
        else
            if (isMiuix) MiuixTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isExpanded)
            if (isMiuix) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
        else
            if (isMiuix) MiuixTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor"
    )

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        cornerRadius = 12.dp,
        containerColor = containerColor,
    ) {
        ListItem(
            modifier = Modifier
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = expandActionLabel,
                    onLongClickLabel = moreMenuLabel,
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = item.bookSourceName
                    role = Role.Button
                    if (loadingKinds) {
                        stateDescription = loadingLabel
                    }
                }
                .fillMaxWidth(),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            headlineContent = {
                AppText(
                    text = item.bookSourceName,
                    style = LegadoTheme.typography.titleMedium,
                    color = contentColor
                )
            },
            trailingContent = {
                AnimatedContent(
                    targetState = loadingKinds,
                    label = "LoadingSwitch"
                ) { loading ->
                    if (loading) {
                        AppContainedLoadingIndicator(
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier
                                .rotate(rotation)
                                .size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    ExploreSourceActionMenuContent(
                        source = item,
                        onTop = onTop,
                        onEdit = onEdit,
                        onSearch = onSearch,
                        onLogin = onLogin,
                        onSetHomeSource = onSetHomeSource,
                        onRefresh = onRefresh,
                        onDelete = onDelete,
                        onDismiss = { showMenu = false },
                    )
                }
            }
        )
    }
}
