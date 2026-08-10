package io.legado.app.ui.main.explore

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.usecase.ExploreKindUiUseCase
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
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreRouteScreen(
    viewModel: ExploreViewModel = koinViewModel(),
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onOpenLogin: (sourceUrl: String) -> Unit,
    onOpenEdit: (sourceUrl: String) -> Unit,
    onOpenSearch: (scopeRaw: String) -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String, origin: String?, coverPath: String?, sharedCoverKey: String?) -> Unit = { _, _, _, _, _, _ -> },
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
                        },
                    )
                }
                is ExploreEffect.OpenEdit -> onOpenEdit(effect.sourceUrl)
                is ExploreEffect.OpenSearch -> onOpenSearch(SearchScope(effect.source).toString())
                is ExploreEffect.OpenLogin -> onOpenLogin(effect.sourceUrl)
                is ExploreEffect.OpenBookInfo -> onOpenBookInfo(
                    effect.name,
                    effect.author,
                    effect.bookUrl,
                    effect.origin,
                    effect.coverPath,
                    effect.sharedCoverKey,
                )
            }
        }
    }

    ExploreScreen(
        state = uiState,
        onIntent = viewModel::onIntent,
        onOpenExploreShow = onOpenExploreShow,
        onBookClick = { book, sharedCoverKey ->
            viewModel.onIntent(ExploreIntent.OpenBook(book, sharedCoverKey))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    state: ExploreViewModel.ExploreUiState,
    onIntent: (ExploreIntent) -> Unit,
    onOpenExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onBookClick: (SearchBook, String?) -> Unit = { _, _ -> },
) {
    val listItems by remember(state.items, state.expandedId, state.exploreKinds) {
        derivedStateOf { buildExploreListItems(state) }
    }
    var sourceToDeleteUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val sourceToDelete = remember(sourceToDeleteUrl, state.items) {
        state.items.firstOrNull { it.bookSourceUrl == sourceToDeleteUrl }
    }
    var sourceActionMenuUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val sourceActionMenuSource = remember(sourceActionMenuUrl, state.items) {
        state.items.firstOrNull { it.bookSourceUrl == sourceActionMenuUrl }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val stickyHeaderSource by remember(listItems, state.items) {
        derivedStateOf {
            val item = listItems.getOrNull(listState.firstVisibleItemIndex)
            if (item is ExploreListItem.KindRow) {
                state.items.find { it.bookSourceUrl == item.sourceUrl }
            } else null
        }
    }

    val composeEngine = ThemeResolver.isMiuixEngine(composeEngine)
    val sourceMenuItems = remember(state.items) { state.items.distinctBy { it.bookSourceUrl } }
    val sourceMenuTextStyle = MaterialTheme.typography.labelLarge
    val sourceMenuTextMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val sourceMenuWidth = remember(sourceMenuItems, sourceMenuTextStyle, density) {
        val longestPx = sourceMenuItems.maxOfOrNull { source ->
            sourceMenuTextMeasurer.measure(
                text = AnnotatedString(source.bookSourceName),
                style = sourceMenuTextStyle,
                maxLines = 1,
            ).size.width
        } ?: 0
        with(density) { longestPx.toDp() + 88.dp }.coerceIn(190.dp, 360.dp)
    }
    val configuration = LocalConfiguration.current
    val sourceMenuMaxHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp - 96.dp).coerceAtLeast(124.dp)
    }
    val sourceActionCount = remember(sourceActionMenuSource) {
        sourceActionMenuSource?.let { 7 + if (it.hasLoginUrl) 1 else 0 } ?: 0
    }
    val sourceMenuHeight = remember(sourceMenuItems.size, sourceActionCount, sourceMenuMaxHeight) {
        val rowCount = if (sourceActionCount > 0) sourceActionCount else sourceMenuItems.size
        (68 + rowCount * 56).dp.coerceIn(124.dp, sourceMenuMaxHeight)
    }
    val sourcePopupWidth = remember(sourceMenuWidth, sourceActionMenuSource) {
        if (sourceActionMenuSource != null) maxOf(sourceMenuWidth, 240.dp) else sourceMenuWidth
    }

    ListScaffold(
        title = stringResource(R.string.discovery),
        state = state,
        subtitle = if (state.layoutMode == 0) {
            state.selectedGroup.ifEmpty { stringResource(R.string.all) }
        } else {
            state.selectedSourceName ?: state.selectedSuite?.displayName
        },
        subtitleDropdownMenuWidth = sourcePopupWidth,
        subtitleDropdownMenuHeight = sourceMenuHeight,
        subtitleDropdownMenuLazy = if (state.layoutMode == 1) {
            { dismiss ->
                val actionSource = sourceActionMenuSource
                if (actionSource == null) {
                    item(key = "source_menu_header") { PillHeaderDivider(title = "选择首页源") }
                    items(sourceMenuItems, key = { it.bookSourceUrl }) { source ->
                        RoundDropdownMenuItem(
                            text = source.bookSourceName,
                            isSelected = source.bookSourceUrl == state.selectedSuite?.defaultSourceUrl,
                            onClick = {
                                sourceActionMenuUrl = null
                                onIntent(ExploreIntent.SetSuiteDefaultSource(source.bookSourceUrl))
                                dismiss()
                            },
                            onLongClick = { sourceActionMenuUrl = source.bookSourceUrl },
                        )
                    }
                } else {
                    item(key = "source_action_header_${actionSource.bookSourceUrl}") {
                        PillHeaderDivider(title = actionSource.bookSourceName)
                    }
                    item(key = "source_action_back") {
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.Default.ArrowBack) },
                            text = "返回书源列表",
                            onClick = { sourceActionMenuUrl = null },
                        )
                    }
                    item(key = "source_action_top") {
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.Default.VerticalAlignTop) },
                            text = stringResource(R.string.to_top),
                            onClick = {
                                onIntent(ExploreIntent.TopSource(actionSource))
                                sourceActionMenuUrl = null
                                dismiss()
                            },
                        )
                    }
                    item(key = "source_action_edit") {
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
                            text = stringResource(R.string.edit),
                            onClick = {
                                onIntent(ExploreIntent.OpenEdit(actionSource))
                                sourceActionMenuUrl = null
                                dismiss()
                            },
                        )
                    }
                    item(key = "source_action_search") {
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.Default.Search) },
                            text = stringResource(R.string.search),
                            onClick = {
                                onIntent(ExploreIntent.OpenSearch(actionSource))
                                sourceActionMenuUrl = null
                                dismiss()
                            },
                        )
                    }
                    if (actionSource.hasLoginUrl) {
                        item(key = "source_action_login") {
                            RoundDropdownMenuItem(
                                leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
                                text = stringResource(R.string.login),
                                onClick = {
                                    onIntent(ExploreIntent.OpenLogin(actionSource))
                                    sourceActionMenuUrl = null
                                    dismiss()
                                },
                            )
                        }
                    }
                    item(key = "source_action_home") {
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.Default.Dashboard) },
                            text = "设为示例首页源",
                            onClick = {
                                onIntent(ExploreIntent.SetSuiteDefaultSource(actionSource.bookSourceUrl))
                                sourceActionMenuUrl = null
                                dismiss()
                            },
                        )
                    }
                    item(key = "source_action_refresh") {
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.Default.Refresh) },
                            text = stringResource(R.string.refresh),
                            onClick = {
                                onIntent(ExploreIntent.RefreshKinds(actionSource))
                                sourceActionMenuUrl = null
                                dismiss()
                            },
                        )
                    }
                    item(key = "source_action_delete") {
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.Default.Delete, tint = MaterialTheme.colorScheme.error) },
                            text = stringResource(R.string.delete),
                            color = LegadoTheme.colorScheme.error,
                            onClick = {
                                sourceToDeleteUrl = actionSource.bookSourceUrl
                                sourceActionMenuUrl = null
                                dismiss()
                            },
                        )
                    }
                }
            }
        } else null,
        onSearchQueryChange = { onIntent(ExploreIntent.Search(it)) },
        onSearchToggle = { onIntent(ExploreIntent.ToggleSearch(it)) },
        searchPlaceholder = stringResource(R.string.search),
        topBarActions = {
            if (state.layoutMode == 1) {
                TopBarActionButton(
                    onClick = { onIntent(ExploreIntent.RefreshSuite) },
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                )
            }
            if (state.layoutSwitcherEnabled) {
                TopBarActionButton(
                    onClick = { onIntent(ExploreIntent.ToggleLayoutMode) },
                    imageVector = if (state.layoutMode == 0) Icons.Default.Dashboard else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = stringResource(R.string.a11y_switch_layout),
                )
            }
        },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                leadingIcon = { MenuItemIcon(Icons.Default.Group) },
                text = stringResource(R.string.all),
                onClick = { onIntent(ExploreIntent.SetGroup("")); dismiss() },
            )
            state.groups.forEach { group ->
                RoundDropdownMenuItem(
                    leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Outlined.Label) },
                    text = group,
                    onClick = { onIntent(ExploreIntent.SetGroup(group)); dismiss() },
                )
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { paddingValues ->
        if (state.layoutMode == 1) {
            DiscoverySuiteScreen(
                state = state,
                onIntent = onIntent,
                onOpenExploreShow = onOpenExploreShow,
                onBookClick = onBookClick,
                paddingValues = paddingValues,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.items.isEmpty()) {
                    EmptyMessage(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = paddingValues.calculateTopPadding(),
                                bottom = paddingValues.calculateBottomPadding(),
                            ),
                        messageResId = R.string.explore_empty,
                    )
                    return@Box
                }

                FastScrollLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = adaptiveContentPadding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = 120.dp,
                    ),
                ) {
                    items(items = listItems, key = { it.key }) { listItem ->
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
                                    onDelete = { sourceToDeleteUrl = item.bookSourceUrl },
                                    isMiuix = composeEngine,
                                )
                            }
                            is ExploreListItem.KindRow -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listItem.rowItems.forEach { (kind, span) ->
                                        ExploreKindMultiTypeItem(
                                            kind = kind,
                                            sourceUrl = listItem.sourceUrl,
                                            onOpenUrl = { url -> onOpenExploreShow(kind.title, listItem.sourceUrl, url) },
                                            modifier = Modifier.weight(span.toFloat()),
                                            isMiuix = composeEngine,
                                            displayNameOverride = state.kindDisplayNames[kind.title],
                                            valueOverride = state.kindValues[kind.title],
                                            onValueChange = { value ->
                                                onIntent(ExploreIntent.UpdateKindValue(listItem.sourceUrl, kind, value))
                                            },
                                            onRunAction = {
                                                onIntent(ExploreIntent.RunKindAction(listItem.sourceUrl, kind))
                                            },
                                        )
                                    }
                                    val totalSpan = listItem.rowItems.sumOf { it.second }
                                    if (totalSpan < 6) Spacer(Modifier.weight((6 - totalSpan).toFloat()))
                                }
                            }
                        }
                    }
                }

                TopFloatingStickyItem(
                    item = stickyHeaderSource,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = paddingValues.calculateTopPadding() + 4.dp, start = 8.dp),
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
                        },
                    )
                }
            }
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

    DiscoveryConfigSheet(
        show = state.showDiscoveryConfig,
        state = state,
        onIntent = onIntent,
        onDismissRequest = { onIntent(ExploreIntent.ShowDiscoveryConfig(false)) },
    )
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
    onSetHomeSource: () -> Unit = {},
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
        targetValue = if (isExpanded) {
            if (isMiuix) MiuixTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer
        } else {
            if (isMiuix) MiuixTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor",
    )

    val contentColor by animateColorAsState(
        targetValue = if (isExpanded) {
            if (isMiuix) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
        } else {
            if (isMiuix) MiuixTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor",
    )

    GlassCard(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                    onLongClick = { showMenu = true },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = item.bookSourceName
                    role = Role.Button
                    if (loadingKinds) stateDescription = loadingLabel
                }
                .fillMaxWidth(),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                AppText(
                    text = item.bookSourceName,
                    style = LegadoTheme.typography.titleMedium,
                    color = contentColor,
                )
            },
            trailingContent = {
                AnimatedContent(targetState = loadingKinds, label = "LoadingSwitch") { loading ->
                    if (loading) {
                        AppContainedLoadingIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.rotate(rotation).size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    PillHeaderDivider(title = item.bookSourceName)
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.VerticalAlignTop) },
                        text = stringResource(R.string.to_top),
                        onClick = { onTop(); showMenu = false },
                    )
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
                        text = stringResource(R.string.edit),
                        onClick = { onEdit(); showMenu = false },
                    )
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Search) },
                        text = stringResource(R.string.search),
                        onClick = { onSearch(); showMenu = false },
                    )
                    if (item.hasLoginUrl) {
                        RoundDropdownMenuItem(
                            leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
                            text = stringResource(R.string.login),
                            onClick = { onLogin(); showMenu = false },
                        )
                    }
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Dashboard) },
                        text = "设为示例首页源",
                        onClick = { onSetHomeSource(); showMenu = false },
                    )
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Refresh) },
                        text = stringResource(R.string.refresh),
                        onClick = { onRefresh(); showMenu = false },
                    )
                    RoundDropdownMenuItem(
                        leadingIcon = { MenuItemIcon(Icons.Default.Delete, tint = MaterialTheme.colorScheme.error) },
                        text = stringResource(R.string.delete),
                        color = LegadoTheme.colorScheme.error,
                        onClick = { onDelete(); showMenu = false },
                    )
                }
            },
        )
    }
}
