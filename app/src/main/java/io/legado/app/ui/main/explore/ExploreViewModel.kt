package io.legado.app.ui.main.explore

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.getExploreInfoMap
import io.legado.app.ui.widget.components.explore.calculateExploreKindRows
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExploreViewModel(
    application: Application,
    private val exploreRepository: ExploreRepository,
    private val exploreKindUseCase: ExploreKindUiUseCase,
    private val shellSettingsGateway: io.legado.app.domain.gateway.AppShellSettingsGateway,
    private val exploreBooksUseCase: io.legado.app.domain.usecase.ExploreBooksUseCase,
    private val customSettingsGateway: io.legado.app.domain.gateway.CustomSettingsGateway
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExploreUiState())
    private val _effects = MutableSharedFlow<ExploreEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var exploreJob: Job? = null
    private var kindsJob: Job? = null
    private var suiteSearchJob: Job? = null
    private var allSourceKinds: List<ExploreKind> = emptyList()

    init {
        val customSettings = customSettingsGateway.currentSettings
        val initialMode = if (customSettings.masterSwitch) {
            customSettings.discoveryLayoutMode
        } else {
            shellSettingsGateway.currentSettings.exploreLayoutMode
        }
        _uiState.update { it.copy(
            layoutMode = initialMode,
            layoutSwitcherEnabled = if (customSettings.masterSwitch) customSettings.discoveryLayoutSwitcherEnabled else true
        ) }
        observeGroups()
        observeExplore()

        viewModelScope.launch {
            customSettingsGateway.settings.collectLatest { settings ->
                _uiState.update { it.copy(
                    layoutSwitcherEnabled = if (settings.masterSwitch) settings.discoveryLayoutSwitcherEnabled else true,
                    layoutMode = if (settings.masterSwitch) settings.discoveryLayoutMode else it.layoutMode
                ) }
            }
        }

        if (initialMode == 1) {
            viewModelScope.launch {
                _uiState.map { it.items }.filter { it.isNotEmpty() }.first().let {
                    loadDiscoverySuite()
                }
            }
        }
    }

    fun onIntent(intent: ExploreIntent) {
        when (intent) {
            is ExploreIntent.Search -> search(intent.query)
            is ExploreIntent.ToggleSearch -> toggleSearchVisible(intent.visible)
            is ExploreIntent.SetGroup -> setGroup(intent.group)
            is ExploreIntent.ToggleExpand -> toggleExpand(intent.source)
            is ExploreIntent.TopSource -> topSource(intent.source)
            is ExploreIntent.RefreshKinds -> refreshExploreKinds(intent.source)
            is ExploreIntent.DeleteSource -> deleteSource(intent.source)
            is ExploreIntent.UpdateKindValue ->
                updateKindValue(intent.sourceUrl, intent.kind, intent.value)
            is ExploreIntent.RunKindAction -> requestKindAction(intent.sourceUrl, intent.kind)
            is ExploreIntent.OpenEdit -> _effects.tryEmit(
                ExploreEffect.OpenEdit(intent.source.bookSourceUrl)
            )
            is ExploreIntent.OpenSearch -> _effects.tryEmit(ExploreEffect.OpenSearch(intent.source))
            is ExploreIntent.OpenLogin -> _effects.tryEmit(
                ExploreEffect.OpenLogin(intent.source.bookSourceUrl)
            )
            is ExploreIntent.ToggleLayoutMode -> toggleLayoutMode()
            is ExploreIntent.SwitchSuite -> switchSuite(intent.suite)
            is ExploreIntent.RefreshSuite -> {
                allSourceKinds = emptyList()
                refreshSuite()
            }
            is ExploreIntent.SetSuiteDefaultSource -> setSuiteDefaultSource(intent.sourceUrl)
            is ExploreIntent.ShowDiscoveryConfig -> _uiState.update { it.copy(showDiscoveryConfig = intent.show) }
            is ExploreIntent.UpdateDiscoverySettings -> updateDiscoverySettings(intent.transform)
            is ExploreIntent.SelectWidgetTarget -> selectWidgetTarget(intent.widgetId, intent.target)
            is ExploreIntent.LoadMoreWidgetData -> loadMoreWidgetData(intent.widgetId)
            is ExploreIntent.LoadMoreSuiteSearch -> loadMoreSuiteSearch()
            is ExploreIntent.ToggleCategorySheet -> _uiState.update { it.copy(showCategorySheet = intent.show) }
            is ExploreIntent.OpenBook -> _effects.tryEmit(
                ExploreEffect.OpenBookInfo(
                    name = intent.book.name,
                    author = intent.book.author,
                    bookUrl = intent.book.bookUrl,
                    origin = intent.book.origin,
                    coverPath = intent.book.coverUrl,
                    sharedCoverKey = intent.sharedCoverKey
                )
            )
        }
    }

    private fun loadMoreWidgetData(widgetId: String) {
        val state = _uiState.value
        if (state.widgetLoading[widgetId] == true || state.widgetIsEnd[widgetId] == true) return

        val suite = state.selectedSuite ?: return
        val defaultSourceUrl = suite.defaultSourceUrl ?: state.items.firstOrNull()?.bookSourceUrl ?: return

        val currentUrl = state.selectedWidgetTargets["current_url"] ?: return
        val nextPage = (state.widgetPages[widgetId] ?: 1) + 1

        _uiState.update { it.copy(widgetLoading = (it.widgetLoading + (widgetId to true)).toImmutableMap()) }

        viewModelScope.launch(IO) {
            try {
                val result = exploreBooksUseCase.execute(
                    sourceUrl = defaultSourceUrl,
                    moduleUrl = currentUrl,
                    args = null,
                    page = nextPage
                )

                if (result.books.isEmpty()) {
                    _uiState.update { it.copy(
                        widgetIsEnd = (it.widgetIsEnd + (widgetId to true)).toImmutableMap(),
                        widgetLoading = (it.widgetLoading + (widgetId to false)).toImmutableMap()
                    ) }
                    return@launch
                }

                _uiState.update { s ->
                    val currentBooks = s.widgetBooks[widgetId] ?: persistentListOf()
                    val newBooksList = (currentBooks + result.books).distinctBy { it.bookUrl }

                    s.copy(
                        widgetBooks = (s.widgetBooks + (widgetId to newBooksList.toImmutableList())).toImmutableMap(),
                        widgetLoading = (s.widgetLoading + (widgetId to false)).toImmutableMap(),
                        widgetPages = (s.widgetPages + (widgetId to nextPage)).toImmutableMap(),
                        widgetIsEnd = (s.widgetIsEnd + (widgetId to (newBooksList.size == currentBooks.size))).toImmutableMap()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    widgetLoading = (it.widgetLoading + (widgetId to false)).toImmutableMap(),
                    widgetIsEnd = (it.widgetIsEnd + (widgetId to true)).toImmutableMap()
                ) }
            }
        }
    }

    private fun saveSelection(widgetId: String, title: String) {
        if (title.isEmpty()) return
        val suite = _uiState.value.selectedSuite ?: return
        val sourceUrl = suite.defaultSourceUrl ?: _uiState.value.items.firstOrNull()?.bookSourceUrl ?: return
        val config = DiscoverySuiteStore.load()
        val key = "${sourceUrl}_$widgetId"
        val updatedMap = config.lastSelectedTargets + (key to title)

        val newConfig = config.copy(lastSelectedTargets = updatedMap)
        DiscoverySuiteStore.save(newConfig)
    }

    private fun updateDiscoverySettings(transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig) {
        val config = DiscoverySuiteStore.load()
        val updated = transform(config)
        DiscoverySuiteStore.save(updated)
        _uiState.update { it.copy(suites = updated.suites.toImmutableList()) }
        val current = _uiState.value.selectedSuite
        if (current != null) {
            val updatedCurrent = updated.suites.find { it.id == current.id }
            if (updatedCurrent != null) {
                _uiState.update { it.copy(selectedSuite = updatedCurrent) }
                refreshSuite()
            }
        }
    }

    private fun selectWidgetTarget(widgetId: String, target: DiscoverySuiteWidgetTarget) {
        val suite = _uiState.value.selectedSuite ?: return
        val defaultSourceUrl = suite.defaultSourceUrl
            ?: _uiState.value.items.firstOrNull()?.bookSourceUrl
            ?: return

        if (!widgetId.startsWith(DYNAMIC_LEVEL_PREFIX)) return

        val level = widgetId.removePrefix(DYNAMIC_LEVEL_PREFIX).toIntOrNull() ?: return
        if (_uiState.value.selectedWidgetTargets[widgetId] == target.title) return

        saveSelection(widgetId, target.title)

        _uiState.update { state ->
            val newSelections = state.selectedWidgetTargets.toMutableMap()
            newSelections[widgetId] = target.title

            // 不再写死 0..10。选择上一级后，清除所有更深的动态层级。
            newSelections.keys
                .filter { key ->
                    key.startsWith(DYNAMIC_LEVEL_PREFIX) &&
                        (key.removePrefix(DYNAMIC_LEVEL_PREFIX).toIntOrNull() ?: -1) > level
                }
                .toList()
                .forEach(newSelections::remove)

            newSelections.remove("current_url")
            state.copy(selectedWidgetTargets = newSelections.toImmutableMap())
        }

        viewModelScope.launch(IO) {
            rebuildSelectors(suite, defaultSourceUrl)
        }
    }

    /**
     * 根据 ExploreKindTreeBuilder 生成的通用树动态构建筛选行。
     *
     * 这里没有最大层级限制：只要当前选中节点仍有 children，就继续生成下一行。
     * 单一、不可点击的容器节点会自动折叠成当前行标题，避免出现只有一个选项的无意义行。
     */
    private fun rebuildSelectors(suite: DiscoverySuite, defaultSourceUrl: String) {
        val selectors = mutableListOf<DynamicSelectorUi>()
        val config = DiscoverySuiteStore.load()
        if (allSourceKinds.isEmpty()) return

        var currentLevelItems = allSourceKinds
        var inheritedTitle: String? = null
        var lastValidUrl: String? = null
        var level = 0

        // TreeBuilder 生成的是无环树；仍保留节点数量级的安全阈值，防止第三方 children 数据异常自引用。
        val safetyLimit = (countExploreNodes(allSourceKinds) + 1).coerceAtLeast(16)
        var steps = 0

        while (currentLevelItems.isNotEmpty() && steps++ < safetyLimit) {
            // 单个纯容器自动折叠：
            // 排行榜(Header) -> 周榜/月榜/总榜
            // UI 直接显示“排行榜 [周榜][月榜][总榜]”，而不是“分类 [排行榜]”。
            while (
                currentLevelItems.size == 1 &&
                currentLevelItems.first().targetUrl().isNullOrBlank() &&
                currentLevelItems.first().hasChildren()
            ) {
                val container = currentLevelItems.first()
                inheritedTitle = cleanExploreTitle(container.title).ifBlank { inheritedTitle }
                currentLevelItems = container.children.orEmpty()
                if (currentLevelItems.isEmpty()) break
            }
            if (currentLevelItems.isEmpty()) break

            val widgetId = "$DYNAMIC_LEVEL_PREFIX$level"
            val targets = currentLevelItems.map { kind ->
                DiscoverySuiteWidgetTarget(
                    sourceUrl = defaultSourceUrl,
                    tagUrl = kind.targetUrl().orEmpty(),
                    title = kind.title
                )
            }
            if (targets.isEmpty()) break

            val savedTitle = config.lastSelectedTargets["${defaultSourceUrl}_$widgetId"]
            val stateSelected = _uiState.value.selectedWidgetTargets[widgetId]
            val selectedTitle = stateSelected
                ?.takeIf { title -> targets.any { it.title == title } }
                ?: savedTitle?.takeIf { title -> targets.any { it.title == title } }
                ?: targets.first().title

            val selectorTitle = inferSelectorTitle(
                level = level,
                items = currentLevelItems,
                inheritedTitle = inheritedTitle
            )

            selectors += DynamicSelectorUi(
                id = widgetId,
                title = selectorTitle,
                targets = targets.toImmutableList(),
                selectedTitle = selectedTitle,
                type = inferSelectorType(currentLevelItems)
            )

            val selectedItem = currentLevelItems.firstOrNull { it.title == selectedTitle } ?: break
            selectedItem.targetUrl()?.let { lastValidUrl = it }

            inheritedTitle = selectedItem.title
            currentLevelItems = selectedItem.children.orEmpty()
            level++
        }

        val finalSelections = selectors
            .associate { selector -> selector.id to selector.selectedTitle.orEmpty() }
            .toMutableMap()
        lastValidUrl?.let { finalSelections["current_url"] = it }

        _uiState.update { state ->
            val preserved = state.selectedWidgetTargets
                .filterKeys { key -> !key.startsWith(DYNAMIC_LEVEL_PREFIX) && key != "current_url" }
            state.copy(
                dynamicSelectors = selectors.toImmutableList(),
                selectedWidgetTargets = (preserved + finalSelections).toImmutableMap()
            )
        }

        if (lastValidUrl != null) {
            _uiState.update {
                it.copy(
                    widgetBooks = persistentMapOf(),
                    widgetLoading = persistentMapOf(),
                    widgetPages = persistentMapOf(),
                    widgetIsEnd = persistentMapOf()
                )
            }
            loadBookWidgetData(suite, lastValidUrl, defaultSourceUrl)
        }
    }

    private fun countExploreNodes(kinds: List<ExploreKind>): Int {
        var count = 0
        val stack = ArrayDeque<ExploreKind>()
        kinds.forEach(stack::addLast)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            count++
            node.children.orEmpty().forEach(stack::addLast)
        }
        return count
    }

    private fun inferSelectorTitle(
        level: Int,
        items: List<ExploreKind>,
        inheritedTitle: String?
    ): String {
        val cleanTitles = items.map { cleanExploreTitle(it.title) }

        if (cleanTitles.any { it.contains("男频") || it.contains("女频") || it.contains("男生频道") || it.contains("女生频道") }) {
            return "频道"
        }
        if (cleanTitles.count { it in STATUS_SELECTOR_TITLES } >= 2) {
            return "状态"
        }
        if (cleanTitles.count { it in RANK_SELECTOR_TITLES || it.endsWith("榜") || it.contains("排行") } >= 2) {
            return "榜单"
        }

        val inherited = cleanExploreTitle(inheritedTitle.orEmpty())
        if (inherited.isNotBlank()) {
            when {
                inherited.contains("排行") || inherited.endsWith("榜") -> return inherited
                inherited in setOf("分类", "频道", "状态", "榜单", "标签", "类型") -> return inherited
            }
        }

        return if (level == 0 && items.all { it.isGroupHeader() }) "分组" else "分类"
    }

    private fun inferSelectorType(items: List<ExploreKind>): DynamicSelectorUi.SelectorType {
        val titles = items.map { cleanExploreTitle(it.title) }
        return if (titles.count { it in RANK_SELECTOR_TITLES || it.endsWith("榜") || it.contains("排行") } >= 2) {
            DynamicSelectorUi.SelectorType.RankButtons
        } else {
            DynamicSelectorUi.SelectorType.TagBar
        }
    }

    private fun cleanExploreTitle(title: String): String {
        return title
            .replace(Regex("[\\[\\]【】()（）<>《》]"), "")
            .replace(Regex("[\\p{So}\\p{Sk}]+"), "")
            .replace(Regex("[༺༻ˇ»«`´ʚɞ]+"), "")
            .trim()
    }

    private companion object {
        const val DYNAMIC_LEVEL_PREFIX = "dynamic_level_"
        val STATUS_SELECTOR_TITLES = setOf(
            "全部", "完结", "连载", "完本", "在更", "已完结", "连载中", "Finished", "Loading"
        )
        val RANK_SELECTOR_TITLES = setOf(
            "推荐", "评分", "热门", "周榜", "月榜", "总榜", "日榜", "本周", "本月", "本日"
        )
    }

    private fun loadBookWidgetData(suite: DiscoverySuite, tagUrl: String, defaultSourceUrl: String) {
        if (tagUrl.isEmpty()) return
        val bookWidgetId = suite.widgets.find {
            it.type == DiscoverySuiteWidgetType.WaterfallBooks.type ||
            it.type == DiscoverySuiteWidgetType.BookList.type ||
            it.type == DiscoverySuiteWidgetType.HorizontalBooks.type
        }?.id ?: return

        loadWidgetDataWithUrl(bookWidgetId, defaultSourceUrl, tagUrl)
    }

    private fun setSuiteDefaultSource(sourceUrl: String) {
        val suite = _uiState.value.selectedSuite ?: return
        val updatedSuite = suite.copy(defaultSourceUrl = sourceUrl)
        val config = DiscoverySuiteStore.load()
        val updatedConfig = config.copy(
            suites = config.suites.map { if (it.id == suite.id) updatedSuite else it }
        )
        DiscoverySuiteStore.save(updatedConfig)
        suiteSearchJob?.cancel()
        _uiState.update { it.copy(
            selectedSuite = updatedSuite,
            suites = updatedConfig.suites.toImmutableList(),
            selectedSourceName = _uiState.value.items.find { item -> item.bookSourceUrl == sourceUrl }?.bookSourceName,
            searchKey = "",
            suiteSearchBooks = null,
            suiteSearchLoading = false,
            suiteSearchRemote = false
        ) }
        refreshSuite()
    }

    private fun observeGroups() {
        viewModelScope.launch {
            exploreRepository.getExploreGroups()
                .flowOn(IO)
                .collectLatest { groups ->
                    _uiState.update { it.copy(groups = groups.toImmutableList()) }
                }
        }
    }

    private fun toggleLayoutMode() {
        val newMode = if (_uiState.value.layoutMode == 0) 1 else 0
        suiteSearchJob?.cancel()
        // 两种布局的搜索语义不同：列表搜“源”，瀑布流搜“书”。切换布局时清空搜索，
        // 避免同一个关键字被另一种布局误解释。
        _uiState.update {
            it.copy(
                layoutMode = newMode,
                searchKey = "",
                isSearch = false,
                suiteSearchBooks = null,
                suiteSearchLoading = false,
                suiteSearchRemote = false,
                suiteSearchPage = 1,
                suiteSearchIsEnd = true
            )
        }
        observeExplore()
        // 将用户最后一次选择的布局同时写入两套持久化设置。
        // 这样无论“自定义设置总开关”当前是否开启，或者之后是否切换，
        // 下次进入 App / 发现页都会继续使用用户最后选择的布局。
        viewModelScope.launch {
            customSettingsGateway.update { it.copy(discoveryLayoutMode = newMode) }
            shellSettingsGateway.update { it.copy(exploreLayoutMode = newMode) }
        }
        if (newMode == 1) {
            loadDiscoverySuite()
        }
    }

    private fun loadDiscoverySuite() {
        var config = DiscoverySuiteStore.load()

        var changed = false
        config = config.copy(suites = config.suites.map { suite ->
            val filteredWidgets = suite.widgets.filter { widget ->
                val shouldKeep = when (widget.title) {
                    "热门分类", "分类", "精选榜单", "榜单", "筛选", "状态" -> false
                    else -> true
                }
                if (!shouldKeep) changed = true
                shouldKeep
            }
            suite.copy(widgets = filteredWidgets)
        })

        if (changed) {
            DiscoverySuiteStore.save(config)
        }

        val selectedId = DiscoverySuiteStore.getSelectedSuiteId()
        var selectedSuite = config.suites.find { it.id == selectedId } ?: config.suites.firstOrNull()

        if (selectedSuite != null && selectedSuite.widgets.isEmpty()) {
            val newConfig = DiscoverySuiteStore.resetDefault()
            selectedSuite = newConfig.suites.firstOrNull()
        }

        val sourceName = selectedSuite?.defaultSourceUrl?.let { url ->
            _uiState.value.items.find { it.bookSourceUrl == url }?.bookSourceName
        } ?: _uiState.value.items.firstOrNull()?.bookSourceName

        _uiState.update {
            it.copy(
                suites = config.suites.toImmutableList(),
                selectedSuite = selectedSuite,
                selectedSourceName = sourceName
            )
        }
        if (selectedSuite != null) {
            refreshSuite()
        }
    }

    private fun switchSuite(suite: DiscoverySuite) {
        DiscoverySuiteStore.setSelectedSuiteId(suite.id)
        val sourceName = suite.defaultSourceUrl?.let { url ->
            _uiState.value.items.find { it.bookSourceUrl == url }?.bookSourceName
        } ?: _uiState.value.items.firstOrNull()?.bookSourceName

        _uiState.update { it.copy(selectedSuite = suite, selectedSourceName = sourceName) }
        refreshSuite()
    }

    private fun refreshSuite() {
        val suite = _uiState.value.selectedSuite ?: return
        val defaultSourceUrl = suite.defaultSourceUrl ?: _uiState.value.items.firstOrNull()?.bookSourceUrl ?: return

        _uiState.update {
            it.copy(
                widgetBooks = persistentMapOf(),
                widgetLoading = persistentMapOf(),
                selectedWidgetTargets = persistentMapOf(),
                widgetPages = persistentMapOf(),
                widgetIsEnd = persistentMapOf(),
                dynamicSelectors = persistentListOf(),
                suiteSearchBooks = null,
                suiteSearchLoading = false,
                suiteSearchRemote = false
            )
        }

        viewModelScope.launch(IO) {
            allSourceKinds = try {
                exploreRepository.getSourceExploreKinds(defaultSourceUrl)
            } catch (e: Exception) { emptyList() }

            rebuildSelectors(suite, defaultSourceUrl)
        }
    }

    private fun loadWidgetDataWithUrl(widgetId: String, sourceUrl: String, tagUrl: String) {
        if (tagUrl.isEmpty()) return
        _uiState.update { it.copy(widgetLoading = (it.widgetLoading + (widgetId to true)).toImmutableMap()) }
        viewModelScope.launch(IO) {
            try {
                val result = exploreBooksUseCase.execute(
                    sourceUrl = sourceUrl,
                    moduleUrl = tagUrl,
                    args = null
                )
                val finalBooks = result.books.distinctBy { it.bookUrl }
                _uiState.update {
                    it.copy(
                        widgetBooks = (it.widgetBooks + (widgetId to finalBooks.toImmutableList())).toImmutableMap(),
                        widgetLoading = (it.widgetLoading + (widgetId to false)).toImmutableMap(),
                        widgetPages = (it.widgetPages + (widgetId to 1)).toImmutableMap(),
                        widgetIsEnd = (it.widgetIsEnd + (widgetId to false)).toImmutableMap()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(widgetLoading = (it.widgetLoading + (widgetId to false)).toImmutableMap()) }
            }
        }
    }

    fun search(key: String) {
        val query = key.trim()
        _uiState.update { it.copy(searchKey = key, expandedId = null) }

        // 普通列表布局：保持原来的行为，只过滤发现页中的书源列表。
        if (_uiState.value.layoutMode == 0) {
            observeExplore()
            return
        }

        // 瀑布流/DiscoverySuite：搜索的是“书”，不再过滤左侧书源列表。
        searchSuiteBooks(query)
    }

    /**
     * DiscoverySuite 顶部搜索：
     * 1. 当前书源定义了 searchUrl -> 调用该书源真正的搜索规则。
     * 2. 当前书源没有 searchUrl -> 仅在当前页面已经加载出来的书籍中做本地过滤。
     *
     * 不对具体书源做任何特判。
     */
    private fun searchSuiteBooks(query: String) {
        suiteSearchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    suiteSearchBooks = null,
                    suiteSearchLoading = false,
                    suiteSearchRemote = false,
                    suiteSearchPage = 1,
                    suiteSearchIsEnd = true
                )
            }
            return
        }

        suiteSearchJob = viewModelScope.launch {
            // ListScaffold 会在输入过程中持续回调，轻微防抖避免每输入一个字就请求网站。
            delay(300)

            val state = _uiState.value
            if (state.layoutMode != 1 || state.searchKey.trim() != query) return@launch

            val suite = state.selectedSuite ?: return@launch
            val sourceUrl = suite.defaultSourceUrl
                ?: state.items.firstOrNull()?.bookSourceUrl
                ?: return@launch

            val source = try {
                exploreRepository.getBookSource(sourceUrl)
            } catch (_: Exception) {
                null
            }

            // 无论书源是否支持远程搜索，都先从当前页面已经加载的书籍中匹配。
            // 这些结果始终排在最前面；远程搜索结果只追加在它们后面。
            val localMatches = filterLoadedSuiteBooks(state, suite, query)

            if (!source?.searchUrl.isNullOrBlank()) {
                // 书源存在搜索规则：先立即展示本地已加载匹配项，再请求网站搜索。
                _uiState.update {
                    it.copy(
                        suiteSearchBooks = localMatches.toImmutableList(),
                        suiteSearchLoading = true,
                        suiteSearchRemote = true,
                        suiteSearchPage = 1,
                        suiteSearchIsEnd = false
                    )
                }

                try {
                    val result = exploreBooksUseCase.execute(
                        sourceUrl = sourceUrl,
                        moduleUrl = null,
                        args = null,
                        page = 1,
                        key = query
                    )
                    if (_uiState.value.searchKey.trim() == query) {
                        _uiState.update {
                            val merged = (localMatches + result.books)
                                .distinctBy { book -> book.bookUrl }
                            it.copy(
                                suiteSearchBooks = merged.toImmutableList(),
                                suiteSearchLoading = false,
                                suiteSearchRemote = true,
                                suiteSearchPage = 1,
                                suiteSearchIsEnd = result.books.isEmpty()
                            )
                        }
                    }
                } catch (_: Exception) {
                    if (_uiState.value.searchKey.trim() == query) {
                        _uiState.update {
                            // 网站搜索失败时仍保留本地已加载的匹配结果。
                            it.copy(
                                suiteSearchBooks = localMatches.toImmutableList(),
                                suiteSearchLoading = false,
                                suiteSearchRemote = true,
                                suiteSearchPage = 1,
                                suiteSearchIsEnd = true
                            )
                        }
                    }
                }
            } else {
                // 没有搜索规则：只显示当前页面已经加载出来的匹配书籍。
                if (_uiState.value.searchKey.trim() == query) {
                    _uiState.update {
                        it.copy(
                            suiteSearchBooks = localMatches.toImmutableList(),
                            suiteSearchLoading = false,
                            suiteSearchRemote = false,
                            suiteSearchPage = 1,
                            suiteSearchIsEnd = true
                        )
                    }
                }
            }
        }
    }


    /**
     * 从 DiscoverySuite 当前已经加载到页面的主书籍组件中进行本地搜索。
     *
     * 远程搜索存在时，这批结果也会始终排在远程结果之前。
     */
    private fun filterLoadedSuiteBooks(
        state: ExploreUiState,
        suite: DiscoverySuite,
        query: String
    ): List<SearchBook> {
        val bookWidgetId = suite.widgets.firstOrNull { widget ->
            widget.type == DiscoverySuiteWidgetType.WaterfallBooks.type ||
                widget.type == DiscoverySuiteWidgetType.BookList.type ||
                widget.type == DiscoverySuiteWidgetType.HorizontalBooks.type
        }?.id

        val loadedBooks = bookWidgetId
            ?.let { state.widgetBooks[it] }
            .orEmpty()

        val q = query.lowercase()
        return loadedBooks.filter { book ->
            book.name.contains(query, ignoreCase = true) ||
                book.author.contains(query, ignoreCase = true) ||
                book.kind.orEmpty().contains(query, ignoreCase = true) ||
                book.intro.orEmpty().contains(query, ignoreCase = true) ||
                book.latestChapterTitle.orEmpty().contains(query, ignoreCase = true) ||
                book.wordCount.orEmpty().lowercase().contains(q)
        }
    }

    private fun loadMoreSuiteSearch() {
        val state = _uiState.value
        if (state.layoutMode != 1 || !state.suiteSearchRemote || state.suiteSearchLoading || state.suiteSearchIsEnd) return
        val query = state.searchKey.trim()
        if (query.isBlank()) return
        val suite = state.selectedSuite ?: return
        val sourceUrl = suite.defaultSourceUrl ?: state.items.firstOrNull()?.bookSourceUrl ?: return
        val nextPage = state.suiteSearchPage + 1

        _uiState.update { it.copy(suiteSearchLoading = true) }
        viewModelScope.launch(IO) {
            try {
                val result = exploreBooksUseCase.execute(
                    sourceUrl = sourceUrl,
                    moduleUrl = null,
                    args = null,
                    page = nextPage,
                    key = query
                )
                if (_uiState.value.searchKey.trim() != query) return@launch
                _uiState.update { current ->
                    val old = current.suiteSearchBooks.orEmpty()
                    val merged = (old + result.books).distinctBy { it.bookUrl }
                    current.copy(
                        suiteSearchBooks = merged.toImmutableList(),
                        suiteSearchLoading = false,
                        suiteSearchPage = nextPage,
                        suiteSearchIsEnd = result.books.isEmpty() || merged.size == old.size
                    )
                }
            } catch (_: Exception) {
                if (_uiState.value.searchKey.trim() == query) {
                    _uiState.update { it.copy(suiteSearchLoading = false, suiteSearchIsEnd = true) }
                }
            }
        }
    }

    fun setGroup(group: String) {
        _uiState.update { it.copy(selectedGroup = group, expandedId = null) }
        observeExplore()
    }

    fun toggleSearchVisible(visible: Boolean) {
        _uiState.update { it.copy(isSearch = visible) }
        if (!visible) {
            suiteSearchJob?.cancel()
            search("")
        }
    }

    private fun observeExplore() {
        exploreJob?.cancel()
        exploreJob = viewModelScope.launch {
            val state = _uiState.value
            // 列表布局的 searchKey 用于过滤书源；瀑布流的 searchKey 用于搜索书籍，
            // 两者不能混用，否则瀑布流搜索关键字会错误地把左上角书源菜单也过滤掉。
            val query = if (state.layoutMode == 0) state.searchKey else ""
            val selectedGroup = state.selectedGroup

            exploreRepository.getExploreSources(query, selectedGroup)
                .flowOn(IO)
                .collectLatest { items ->
                    _uiState.update { it.copy(items = items.toImmutableList()) }
                }
        }
    }

    fun toggleExpand(source: BookSourcePart) {
        val newExpandedId =
            if (_uiState.value.expandedId == source.bookSourceUrl) null else source.bookSourceUrl
        _uiState.update {
            it.copy(
                expandedId = newExpandedId,
                exploreKinds = persistentListOf(),
                kindDisplayNames = persistentMapOf(),
                kindValues = persistentMapOf(),
                loadingKinds = newExpandedId != null
            )
        }

        if (newExpandedId != null) {
            loadExploreKinds(source)
        }
    }

    private fun loadExploreKinds(source: BookSourcePart) {
        kindsJob?.cancel()
        kindsJob = viewModelScope.launch(IO) {
            try {
                val kinds = source.exploreKinds()
                exploreKindUseCase.warmUp(source.bookSourceUrl)
                val infoMap = getExploreInfoMap(source.bookSourceUrl)
                val displayNames = kinds.associate { kind ->
                    kind.title to exploreKindUseCase.resolveDisplayName(
                        kind = kind,
                        sourceUrl = source.bookSourceUrl,
                        infoMap = infoMap
                    )
                }
                val values = buildKindValues(kinds, source.bookSourceUrl)
                _uiState.update {
                    if (it.expandedId == source.bookSourceUrl) {
                        it.copy(
                            exploreKinds = kinds.toImmutableList(),
                            kindDisplayNames = displayNames.toImmutableMap(),
                            kindValues = values.toImmutableMap(),
                            loadingKinds = false
                        )
                    } else it
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loadingKinds = false) }
            }
        }
    }

    fun refreshExploreKinds(source: BookSourcePart) {
        viewModelScope.launch(IO) {
            source.clearExploreKindsCache()
            if (_uiState.value.expandedId == source.bookSourceUrl) {
                loadExploreKinds(source)
            }
        }
    }

    fun topSource(bookSource: BookSourcePart) {
        execute {
            exploreRepository.topSource(bookSource)
        }
    }

    fun refreshExploreKinds(sourceUrl: String) {
        val source = _uiState.value.items.firstOrNull { it.bookSourceUrl == sourceUrl } ?: return
        refreshExploreKinds(source)
    }

    fun updateKindValue(sourceUrl: String, kind: ExploreKind, value: String) {
        _uiState.update { state ->
            state.copy(kindValues = (state.kindValues + (kind.title to value)).toImmutableMap())
        }
        viewModelScope.launch(IO) {
            getExploreInfoMap(sourceUrl).apply {
                this[kind.title] = value
                saveNow()
            }
        }
    }

    fun requestKindAction(sourceUrl: String, kind: ExploreKind) {
        _effects.tryEmit(ExploreEffect.ExecuteKindAction(sourceUrl, kind))
    }

    fun deleteSource(source: BookSourcePart) {
        execute {
            exploreRepository.deleteSource(source.bookSourceUrl)
        }
    }

    @androidx.compose.runtime.Immutable
    data class DynamicSelectorUi(
        val id: String,
        val title: String,
        val targets: ImmutableList<DiscoverySuiteWidgetTarget>,
        val selectedTitle: String?,
        val type: SelectorType = SelectorType.TagBar
    ) {
        enum class SelectorType { TagBar, RankButtons }
    }

    data class ExploreUiState(
        override val items: ImmutableList<BookSourcePart> = persistentListOf(),
        override val selectedIds: ImmutableSet<String> = persistentSetOf(),
        override val searchKey: String = "",
        override val isSearch: Boolean = false,
        override val isLoading: Boolean = false,
        val groups: ImmutableList<String> = persistentListOf(),
        val selectedGroup: String = "",
        val expandedId: String? = null,
        val exploreKinds: ImmutableList<ExploreKind> = persistentListOf(),
        val kindDisplayNames: ImmutableMap<String, String> = persistentMapOf(),
        val kindValues: ImmutableMap<String, String> = persistentMapOf(),
        val loadingKinds: Boolean = false,
        val layoutMode: Int = 0,
        val layoutSwitcherEnabled: Boolean = true,
        val suites: ImmutableList<DiscoverySuite> = persistentListOf(),
        val selectedSuite: DiscoverySuite? = null,
        val widgetBooks: ImmutableMap<String, ImmutableList<SearchBook>> = persistentMapOf(),
        val widgetLoading: ImmutableMap<String, Boolean> = persistentMapOf(),
        val resolvedTags: ImmutableMap<String, String> = persistentMapOf(),
        val showDiscoveryConfig: Boolean = false,
        val selectedWidgetTargets: ImmutableMap<String, String> = persistentMapOf(),
        val selectedRankDimension: String? = null,
        val selectedRankStatus: String? = null,
        val showCategorySheet: Boolean = false,
        val dynamicSelectors: ImmutableList<DynamicSelectorUi> = persistentListOf(),
        val dynamicCategoryTargets: ImmutableList<DiscoverySuiteWidgetTarget> = persistentListOf(),
        val dynamicRankTargets: ImmutableList<ImmutableList<DiscoverySuiteWidgetTarget>> = persistentListOf(),
        val dynamicChannelTargets: ImmutableList<DiscoverySuiteWidgetTarget> = persistentListOf(),
        val channelCategoryMap: ImmutableMap<String, ImmutableList<DiscoverySuiteWidgetTarget>> = persistentMapOf(),
        val selectedSourceName: String? = null,
        /** null = 未处于瀑布流搜索结果模式；非 null = 顶部搜索的书籍结果。 */
        val suiteSearchBooks: ImmutableList<SearchBook>? = null,
        val suiteSearchLoading: Boolean = false,
        /** true 表示来自书源 searchUrl；false 表示本地过滤当前已加载书籍。 */
        val suiteSearchRemote: Boolean = false,
        val suiteSearchPage: Int = 1,
        val suiteSearchIsEnd: Boolean = true,
        val widgetPages: ImmutableMap<String, Int> = persistentMapOf(),
        val widgetIsEnd: ImmutableMap<String, Boolean> = persistentMapOf()
    ) : ListUiState<BookSourcePart>

    private fun buildKindValues(
        kinds: List<ExploreKind>,
        sourceUrl: String
    ): Map<String, String> {
        val infoMap = getExploreInfoMap(sourceUrl)
        var shouldSave = false
        val values = HashMap<String, String>()
        kinds.forEach { kind ->
            val value = infoMap[kind.title]
            if (value != null) {
                values[kind.title] = value
            } else {
                val defaultValue = kind.default
                if (defaultValue != null) {
                    values[kind.title] = defaultValue
                    infoMap[kind.title] = defaultValue
                    shouldSave = true
                }
            }
        }
        if (shouldSave) {
            viewModelScope.launch(IO) {
                infoMap.saveNow()
            }
        }
        return values
    }

}

sealed interface ExploreIntent {
    data class Search(val query: String) : ExploreIntent
    data class ToggleSearch(val visible: Boolean) : ExploreIntent
    data class SetGroup(val group: String) : ExploreIntent
    data class ToggleExpand(val source: BookSourcePart) : ExploreIntent
    data class TopSource(val source: BookSourcePart) : ExploreIntent
    data class RefreshKinds(val source: BookSourcePart) : ExploreIntent
    data class DeleteSource(val source: BookSourcePart) : ExploreIntent
    data class UpdateKindValue(val sourceUrl: String, val kind: ExploreKind, val value: String) : ExploreIntent
    data class RunKindAction(val sourceUrl: String, val kind: ExploreKind) : ExploreIntent
    data class OpenEdit(val source: BookSourcePart) : ExploreIntent
    data class OpenSearch(val source: BookSourcePart) : ExploreIntent
    data class OpenLogin(val source: BookSourcePart) : ExploreIntent
    data object ToggleLayoutMode : ExploreIntent
    data class SwitchSuite(val suite: DiscoverySuite) : ExploreIntent
    data object RefreshSuite : ExploreIntent
    data class SetSuiteDefaultSource(val sourceUrl: String) : ExploreIntent
    data class ShowDiscoveryConfig(val show: Boolean) : ExploreIntent
    data class UpdateDiscoverySettings(val transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig) : ExploreIntent
    data class SelectWidgetTarget(val widgetId: String, val target: DiscoverySuiteWidgetTarget) : ExploreIntent
    data class LoadMoreWidgetData(val widgetId: String) : ExploreIntent
    data object LoadMoreSuiteSearch : ExploreIntent
    data class ToggleCategorySheet(val show: Boolean) : ExploreIntent
    data class OpenBook(val book: SearchBook, val sharedCoverKey: String?) : ExploreIntent
}

sealed interface ExploreEffect {
    data class OpenEdit(val sourceUrl: String) : ExploreEffect
    data class OpenSearch(val source: BookSourcePart) : ExploreEffect
    data class OpenLogin(val sourceUrl: String) : ExploreEffect
    data class ExecuteKindAction(val sourceUrl: String, val kind: ExploreKind) : ExploreEffect
    data class OpenBookInfo(
        val name: String,
        val author: String,
        val bookUrl: String,
        val origin: String?,
        val coverPath: String?,
        val sharedCoverKey: String?
    ) : ExploreEffect
}
