package io.legado.app.ui.main.explore

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.CustomSettingsGateway
import io.legado.app.domain.usecase.ExploreBooksUseCase
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
    private val shellSettingsGateway: AppShellSettingsGateway,
    private val exploreBooksUseCase: ExploreBooksUseCase,
    private val customSettingsGateway: CustomSettingsGateway,
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
        _uiState.update {
            it.copy(
                layoutMode = initialMode,
                layoutSwitcherEnabled = if (customSettings.masterSwitch) {
                    customSettings.discoveryLayoutSwitcherEnabled
                } else {
                    true
                },
            )
        }

        observeGroups()
        observeExplore()

        viewModelScope.launch {
            customSettingsGateway.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        layoutSwitcherEnabled = if (settings.masterSwitch) {
                            settings.discoveryLayoutSwitcherEnabled
                        } else {
                            true
                        },
                        layoutMode = if (settings.masterSwitch) {
                            settings.discoveryLayoutMode
                        } else {
                            it.layoutMode
                        },
                    )
                }
            }
        }

        if (initialMode == 1) {
            viewModelScope.launch {
                _uiState.map { it.items }.filter { it.isNotEmpty() }.first()
                loadDiscoverySuite()
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
            is ExploreIntent.UpdateKindValue -> updateKindValue(intent.sourceUrl, intent.kind, intent.value)
            is ExploreIntent.RunKindAction -> requestKindAction(intent.sourceUrl, intent.kind)
            is ExploreIntent.OpenEdit -> _effects.tryEmit(ExploreEffect.OpenEdit(intent.source.bookSourceUrl))
            is ExploreIntent.OpenSearch -> _effects.tryEmit(ExploreEffect.OpenSearch(intent.source))
            is ExploreIntent.OpenLogin -> _effects.tryEmit(ExploreEffect.OpenLogin(intent.source.bookSourceUrl))
            ExploreIntent.ToggleLayoutMode -> toggleLayoutMode()
            is ExploreIntent.SwitchSuite -> switchSuite(intent.suite)
            ExploreIntent.RefreshSuite -> {
                allSourceKinds = emptyList()
                refreshSuite()
            }
            is ExploreIntent.SetSuiteDefaultSource -> setSuiteDefaultSource(intent.sourceUrl)
            is ExploreIntent.ShowDiscoveryConfig -> _uiState.update { it.copy(showDiscoveryConfig = intent.show) }
            is ExploreIntent.UpdateDiscoverySettings -> updateDiscoverySettings(intent.transform)
            is ExploreIntent.SelectWidgetTarget -> selectWidgetTarget(intent.widgetId, intent.target)
            is ExploreIntent.LoadMoreWidgetData -> loadMoreWidgetData(intent.widgetId)
            ExploreIntent.LoadMoreSuiteSearch -> loadMoreSuiteSearch()
            is ExploreIntent.ToggleCategorySheet -> _uiState.update { it.copy(showCategorySheet = intent.show) }
            is ExploreIntent.OpenBook -> _effects.tryEmit(
                ExploreEffect.OpenBookInfo(
                    name = intent.book.name,
                    author = intent.book.author,
                    bookUrl = intent.book.bookUrl,
                    origin = intent.book.origin,
                    coverPath = intent.book.coverUrl,
                    sharedCoverKey = intent.sharedCoverKey,
                )
            )
        }
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

    fun search(key: String) {
        val query = key.trim()
        _uiState.update { it.copy(searchKey = key, expandedId = null) }
        if (_uiState.value.layoutMode == 0) {
            observeExplore()
        } else {
            searchSuiteBooks(query)
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
            val query = if (state.layoutMode == 0) state.searchKey else ""
            exploreRepository.getExploreSources(query, state.selectedGroup)
                .flowOn(IO)
                .collectLatest { items ->
                    _uiState.update { it.copy(items = items.toImmutableList()) }
                }
        }
    }

    private fun toggleLayoutMode() {
        val newMode = if (_uiState.value.layoutMode == 0) 1 else 0
        suiteSearchJob?.cancel()
        _uiState.update {
            it.copy(
                layoutMode = newMode,
                searchKey = "",
                isSearch = false,
                suiteSearchBooks = null,
                suiteSearchLoading = false,
                suiteSearchRemote = false,
                suiteSearchPage = 1,
                suiteSearchIsEnd = true,
            )
        }
        observeExplore()
        viewModelScope.launch {
            customSettingsGateway.update { it.copy(discoveryLayoutMode = newMode) }
            shellSettingsGateway.update { it.copy(exploreLayoutMode = newMode) }
        }
        if (newMode == 1) loadDiscoverySuite()
    }

    private fun loadDiscoverySuite() {
        var config = DiscoverySuiteStore.load()
        var changed = false
        config = config.copy(
            suites = config.suites.map { suite ->
                val widgets = suite.widgets.filter { widget ->
                    val keep = widget.title !in setOf("热门分类", "分类", "精选榜单", "榜单", "筛选", "状态")
                    if (!keep) changed = true
                    keep
                }
                suite.copy(widgets = widgets)
            },
        )
        if (changed) DiscoverySuiteStore.save(config)

        val selectedId = DiscoverySuiteStore.getSelectedSuiteId()
        var selectedSuite = config.suites.firstOrNull { it.id == selectedId }
            ?: config.suites.firstOrNull()
        if (selectedSuite != null && selectedSuite.widgets.isEmpty()) {
            val reset = DiscoverySuiteStore.resetDefault()
            config = reset
            selectedSuite = reset.suites.firstOrNull()
        }

        val sourceName = selectedSuite?.defaultSourceUrl?.let { sourceUrl ->
            _uiState.value.items.firstOrNull { it.bookSourceUrl == sourceUrl }?.bookSourceName
        } ?: _uiState.value.items.firstOrNull()?.bookSourceName

        _uiState.update {
            it.copy(
                suites = config.suites.toImmutableList(),
                selectedSuite = selectedSuite,
                selectedSourceName = sourceName,
            )
        }
        if (selectedSuite != null) refreshSuite()
    }

    private fun switchSuite(suite: DiscoverySuite) {
        DiscoverySuiteStore.setSelectedSuiteId(suite.id)
        val sourceName = suite.defaultSourceUrl?.let { sourceUrl ->
            _uiState.value.items.firstOrNull { it.bookSourceUrl == sourceUrl }?.bookSourceName
        } ?: _uiState.value.items.firstOrNull()?.bookSourceName
        _uiState.update { it.copy(selectedSuite = suite, selectedSourceName = sourceName) }
        refreshSuite()
    }

    private fun setSuiteDefaultSource(sourceUrl: String) {
        val suite = _uiState.value.selectedSuite ?: return
        val updatedSuite = suite.copy(defaultSourceUrl = sourceUrl)
        val config = DiscoverySuiteStore.load()
        val updatedConfig = config.copy(
            suites = config.suites.map { if (it.id == suite.id) updatedSuite else it },
        )
        DiscoverySuiteStore.save(updatedConfig)
        suiteSearchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSuite = updatedSuite,
                suites = updatedConfig.suites.toImmutableList(),
                selectedSourceName = it.items.firstOrNull { item -> item.bookSourceUrl == sourceUrl }?.bookSourceName,
                searchKey = "",
                suiteSearchBooks = null,
                suiteSearchLoading = false,
                suiteSearchRemote = false,
            )
        }
        refreshSuite()
    }

    private fun updateDiscoverySettings(transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig) {
        val updated = transform(DiscoverySuiteStore.load())
        DiscoverySuiteStore.save(updated)
        _uiState.update { state ->
            val current = state.selectedSuite
            val updatedCurrent = current?.let { suite -> updated.suites.firstOrNull { it.id == suite.id } }
            state.copy(
                suites = updated.suites.toImmutableList(),
                selectedSuite = updatedCurrent ?: current,
            )
        }
        refreshSuite()
    }

    private fun refreshSuite() {
        val suite = _uiState.value.selectedSuite ?: return
        val sourceUrl = suite.defaultSourceUrl ?: _uiState.value.items.firstOrNull()?.bookSourceUrl ?: return
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
                suiteSearchRemote = false,
            )
        }
        viewModelScope.launch(IO) {
            allSourceKinds = runCatching { exploreRepository.getSourceExploreKinds(sourceUrl) }
                .getOrDefault(emptyList())
            rebuildSelectors(suite, sourceUrl)
        }
    }

    private fun selectWidgetTarget(widgetId: String, target: DiscoverySuiteWidgetTarget) {
        if (!widgetId.startsWith(DYNAMIC_LEVEL_PREFIX)) return
        val suite = _uiState.value.selectedSuite ?: return
        val sourceUrl = suite.defaultSourceUrl ?: _uiState.value.items.firstOrNull()?.bookSourceUrl ?: return
        val level = widgetId.removePrefix(DYNAMIC_LEVEL_PREFIX).toIntOrNull() ?: return
        if (_uiState.value.selectedWidgetTargets[widgetId] == target.title) return

        saveSelection(widgetId, target.title)
        _uiState.update { state ->
            val selections = state.selectedWidgetTargets.toMutableMap()
            selections[widgetId] = target.title
            selections.keys.filter { key ->
                key.startsWith(DYNAMIC_LEVEL_PREFIX) &&
                    (key.removePrefix(DYNAMIC_LEVEL_PREFIX).toIntOrNull() ?: -1) > level
            }.toList().forEach(selections::remove)
            selections.remove("current_url")
            state.copy(selectedWidgetTargets = selections.toImmutableMap())
        }
        viewModelScope.launch(IO) { rebuildSelectors(suite, sourceUrl) }
    }

    private fun saveSelection(widgetId: String, title: String) {
        if (title.isBlank()) return
        val sourceUrl = _uiState.value.selectedSuite?.defaultSourceUrl
            ?: _uiState.value.items.firstOrNull()?.bookSourceUrl
            ?: return
        val config = DiscoverySuiteStore.load()
        DiscoverySuiteStore.save(
            config.copy(lastSelectedTargets = config.lastSelectedTargets + ("${sourceUrl}_$widgetId" to title)),
        )
    }

    private fun rebuildSelectors(suite: DiscoverySuite, sourceUrl: String) {
        if (allSourceKinds.isEmpty()) return
        val config = DiscoverySuiteStore.load()
        val selectors = mutableListOf<DynamicSelectorUi>()
        var currentItems = allSourceKinds
        var inheritedTitle: String? = null
        var lastUrl: String? = null
        var level = 0
        val safetyLimit = (countExploreNodes(allSourceKinds) + 1).coerceAtLeast(16)
        var steps = 0

        while (currentItems.isNotEmpty() && steps++ < safetyLimit) {
            while (
                currentItems.size == 1 &&
                currentItems.first().targetUrl().isNullOrBlank() &&
                currentItems.first().hasChildren()
            ) {
                val container = currentItems.first()
                inheritedTitle = cleanExploreTitle(container.title).ifBlank { inheritedTitle }
                currentItems = container.children.orEmpty()
                if (currentItems.isEmpty()) break
            }
            if (currentItems.isEmpty()) break

            val id = "$DYNAMIC_LEVEL_PREFIX$level"
            val targets = currentItems.map { kind ->
                DiscoverySuiteWidgetTarget(
                    sourceUrl = sourceUrl,
                    tagUrl = kind.targetUrl().orEmpty(),
                    title = kind.title,
                )
            }
            val saved = config.lastSelectedTargets["${sourceUrl}_$id"]
            val selected = _uiState.value.selectedWidgetTargets[id]
                ?.takeIf { title -> targets.any { it.title == title } }
                ?: saved?.takeIf { title -> targets.any { it.title == title } }
                ?: targets.firstOrNull()?.title
                ?: break

            selectors += DynamicSelectorUi(
                id = id,
                title = inferSelectorTitle(level, currentItems, inheritedTitle),
                targets = targets.toImmutableList(),
                selectedTitle = selected,
                type = inferSelectorType(currentItems),
            )

            val selectedKind = currentItems.firstOrNull { it.title == selected } ?: break
            selectedKind.targetUrl()?.let { lastUrl = it }
            inheritedTitle = selectedKind.title
            currentItems = selectedKind.children.orEmpty()
            level++
        }

        val selectionMap = selectors.associate { it.id to it.selectedTitle.orEmpty() }.toMutableMap()
        lastUrl?.let { selectionMap["current_url"] = it }
        _uiState.update { state ->
            val preserved = state.selectedWidgetTargets.filterKeys {
                !it.startsWith(DYNAMIC_LEVEL_PREFIX) && it != "current_url"
            }
            state.copy(
                dynamicSelectors = selectors.toImmutableList(),
                selectedWidgetTargets = (preserved + selectionMap).toImmutableMap(),
            )
        }

        if (lastUrl != null) {
            _uiState.update {
                it.copy(
                    widgetBooks = persistentMapOf(),
                    widgetLoading = persistentMapOf(),
                    widgetPages = persistentMapOf(),
                    widgetIsEnd = persistentMapOf(),
                )
            }
            loadBookWidgetData(suite, lastUrl, sourceUrl)
        }
    }

    private fun loadBookWidgetData(suite: DiscoverySuite, tagUrl: String, sourceUrl: String) {
        val widgetId = suite.widgets.firstOrNull { widget ->
            widget.type == DiscoverySuiteWidgetType.WaterfallBooks.type ||
                widget.type == DiscoverySuiteWidgetType.BookList.type ||
                widget.type == DiscoverySuiteWidgetType.HorizontalBooks.type
        }?.id ?: return
        loadWidgetDataWithUrl(widgetId, sourceUrl, tagUrl)
    }

    private fun loadWidgetDataWithUrl(widgetId: String, sourceUrl: String, tagUrl: String) {
        if (tagUrl.isBlank()) return
        _uiState.update { it.copy(widgetLoading = (it.widgetLoading + (widgetId to true)).toImmutableMap()) }
        viewModelScope.launch(IO) {
            runCatching {
                exploreBooksUseCase.execute(sourceUrl = sourceUrl, moduleUrl = tagUrl, args = null)
            }.onSuccess { result ->
                val books = result.books.distinctBy { it.bookUrl }.toImmutableList()
                _uiState.update {
                    it.copy(
                        widgetBooks = (it.widgetBooks + (widgetId to books)).toImmutableMap(),
                        widgetLoading = (it.widgetLoading + (widgetId to false)).toImmutableMap(),
                        widgetPages = (it.widgetPages + (widgetId to 1)).toImmutableMap(),
                        widgetIsEnd = (it.widgetIsEnd + (widgetId to false)).toImmutableMap(),
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(widgetLoading = (state.widgetLoading + (widgetId to false)).toImmutableMap())
                }
            }
        }
    }

    private fun loadMoreWidgetData(widgetId: String) {
        val state = _uiState.value
        if (state.widgetLoading[widgetId] == true || state.widgetIsEnd[widgetId] == true) return
        val suite = state.selectedSuite ?: return
        val sourceUrl = suite.defaultSourceUrl ?: state.items.firstOrNull()?.bookSourceUrl ?: return
        val currentUrl = state.selectedWidgetTargets["current_url"] ?: return
        val nextPage = (state.widgetPages[widgetId] ?: 1) + 1

        _uiState.update { it.copy(widgetLoading = (it.widgetLoading + (widgetId to true)).toImmutableMap()) }
        viewModelScope.launch(IO) {
            runCatching {
                exploreBooksUseCase.execute(
                    sourceUrl = sourceUrl,
                    moduleUrl = currentUrl,
                    args = null,
                    page = nextPage,
                )
            }.onSuccess { result ->
                _uiState.update { current ->
                    val old = current.widgetBooks[widgetId].orEmpty()
                    val merged = (old + result.books).distinctBy { it.bookUrl }
                    current.copy(
                        widgetBooks = (current.widgetBooks + (widgetId to merged.toImmutableList())).toImmutableMap(),
                        widgetLoading = (current.widgetLoading + (widgetId to false)).toImmutableMap(),
                        widgetPages = (current.widgetPages + (widgetId to nextPage)).toImmutableMap(),
                        widgetIsEnd = (current.widgetIsEnd + (widgetId to (result.books.isEmpty() || merged.size == old.size))).toImmutableMap(),
                    )
                }
            }.onFailure {
                _uiState.update { current ->
                    current.copy(
                        widgetLoading = (current.widgetLoading + (widgetId to false)).toImmutableMap(),
                        widgetIsEnd = (current.widgetIsEnd + (widgetId to true)).toImmutableMap(),
                    )
                }
            }
        }
    }

    private fun searchSuiteBooks(query: String) {
        suiteSearchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    suiteSearchBooks = null,
                    suiteSearchLoading = false,
                    suiteSearchRemote = false,
                    suiteSearchPage = 1,
                    suiteSearchIsEnd = true,
                )
            }
            return
        }

        suiteSearchJob = viewModelScope.launch {
            delay(300)
            val state = _uiState.value
            if (state.layoutMode != 1 || state.searchKey.trim() != query) return@launch
            val suite = state.selectedSuite ?: return@launch
            val sourceUrl = suite.defaultSourceUrl ?: state.items.firstOrNull()?.bookSourceUrl ?: return@launch
            val source = runCatching { exploreRepository.getBookSource(sourceUrl) }.getOrNull()
            val local = filterLoadedSuiteBooks(state, suite, query)

            if (source?.searchUrl.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        suiteSearchBooks = local.toImmutableList(),
                        suiteSearchLoading = false,
                        suiteSearchRemote = false,
                        suiteSearchPage = 1,
                        suiteSearchIsEnd = true,
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    suiteSearchBooks = local.toImmutableList(),
                    suiteSearchLoading = true,
                    suiteSearchRemote = true,
                    suiteSearchPage = 1,
                    suiteSearchIsEnd = false,
                )
            }

            runCatching {
                exploreBooksUseCase.execute(
                    sourceUrl = sourceUrl,
                    moduleUrl = null,
                    args = null,
                    page = 1,
                    key = query,
                )
            }.onSuccess { result ->
                if (_uiState.value.searchKey.trim() == query) {
                    val merged = (local + result.books).distinctBy { it.bookUrl }
                    _uiState.update {
                        it.copy(
                            suiteSearchBooks = merged.toImmutableList(),
                            suiteSearchLoading = false,
                            suiteSearchRemote = true,
                            suiteSearchPage = 1,
                            suiteSearchIsEnd = result.books.isEmpty(),
                        )
                    }
                }
            }.onFailure {
                if (_uiState.value.searchKey.trim() == query) {
                    _uiState.update {
                        it.copy(
                            suiteSearchBooks = local.toImmutableList(),
                            suiteSearchLoading = false,
                            suiteSearchRemote = true,
                            suiteSearchPage = 1,
                            suiteSearchIsEnd = true,
                        )
                    }
                }
            }
        }
    }

    private fun filterLoadedSuiteBooks(
        state: ExploreUiState,
        suite: DiscoverySuite,
        query: String,
    ): List<SearchBook> {
        val widgetId = suite.widgets.firstOrNull { widget ->
            widget.type == DiscoverySuiteWidgetType.WaterfallBooks.type ||
                widget.type == DiscoverySuiteWidgetType.BookList.type ||
                widget.type == DiscoverySuiteWidgetType.HorizontalBooks.type
        }?.id
        return widgetId?.let { state.widgetBooks[it] }.orEmpty().filter { book ->
            book.name.contains(query, true) ||
                book.author.contains(query, true) ||
                book.kind.orEmpty().contains(query, true) ||
                book.intro.orEmpty().contains(query, true) ||
                book.latestChapterTitle.orEmpty().contains(query, true) ||
                book.wordCount.orEmpty().contains(query, true)
        }
    }

    private fun loadMoreSuiteSearch() {
        val state = _uiState.value
        if (state.layoutMode != 1 || !state.suiteSearchRemote || state.suiteSearchLoading || state.suiteSearchIsEnd) return
        val query = state.searchKey.trim()
        if (query.isBlank()) return
        val sourceUrl = state.selectedSuite?.defaultSourceUrl ?: state.items.firstOrNull()?.bookSourceUrl ?: return
        val nextPage = state.suiteSearchPage + 1
        _uiState.update { it.copy(suiteSearchLoading = true) }

        viewModelScope.launch(IO) {
            runCatching {
                exploreBooksUseCase.execute(
                    sourceUrl = sourceUrl,
                    moduleUrl = null,
                    args = null,
                    page = nextPage,
                    key = query,
                )
            }.onSuccess { result ->
                if (_uiState.value.searchKey.trim() != query) return@onSuccess
                _uiState.update { current ->
                    val old = current.suiteSearchBooks.orEmpty()
                    val merged = (old + result.books).distinctBy { it.bookUrl }
                    current.copy(
                        suiteSearchBooks = merged.toImmutableList(),
                        suiteSearchLoading = false,
                        suiteSearchPage = nextPage,
                        suiteSearchIsEnd = result.books.isEmpty() || merged.size == old.size,
                    )
                }
            }.onFailure {
                if (_uiState.value.searchKey.trim() == query) {
                    _uiState.update { it.copy(suiteSearchLoading = false, suiteSearchIsEnd = true) }
                }
            }
        }
    }

    fun toggleExpand(source: BookSourcePart) {
        val newExpandedId = if (_uiState.value.expandedId == source.bookSourceUrl) null else source.bookSourceUrl
        _uiState.update {
            it.copy(
                expandedId = newExpandedId,
                exploreKinds = persistentListOf(),
                kindDisplayNames = persistentMapOf(),
                kindValues = persistentMapOf(),
                loadingKinds = newExpandedId != null,
            )
        }
        if (newExpandedId != null) loadExploreKinds(source)
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
                        infoMap = infoMap,
                    )
                }
                val values = buildKindValues(kinds, source.bookSourceUrl)
                _uiState.update {
                    if (it.expandedId == source.bookSourceUrl) {
                        it.copy(
                            exploreKinds = kinds.toImmutableList(),
                            kindDisplayNames = displayNames.toImmutableMap(),
                            kindValues = values.toImmutableMap(),
                            loadingKinds = false,
                        )
                    } else it
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(loadingKinds = false) }
            }
        }
    }

    fun refreshExploreKinds(source: BookSourcePart) {
        viewModelScope.launch(IO) {
            source.clearExploreKindsCache()
            if (_uiState.value.expandedId == source.bookSourceUrl) loadExploreKinds(source)
        }
    }

    fun topSource(bookSource: BookSourcePart) {
        execute { exploreRepository.topSource(bookSource) }
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
        execute { exploreRepository.deleteSource(source.bookSourceUrl) }
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

    private fun cleanExploreTitle(title: String): String = title
        .replace(Regex("[\\[\\]【】()（）<>《》]"), "")
        .replace(Regex("[\\p{So}\\p{Sk}]+"), "")
        .replace(Regex("[༺༻ˇ»«`´ʚɞ]+"), "")
        .trim()

    private fun inferSelectorTitle(level: Int, items: List<ExploreKind>, inheritedTitle: String?): String {
        val titles = items.map { cleanExploreTitle(it.title) }
        if (titles.any { it.contains("男频") || it.contains("女频") || it.contains("男生频道") || it.contains("女生频道") }) return "频道"
        if (titles.count { it in STATUS_SELECTOR_TITLES } >= 2) return "状态"
        if (titles.count { it in RANK_SELECTOR_TITLES || it.endsWith("榜") || it.contains("排行") } >= 2) return "榜单"
        val inherited = cleanExploreTitle(inheritedTitle.orEmpty())
        if (inherited.contains("排行") || inherited.endsWith("榜")) return inherited
        if (inherited in setOf("分类", "频道", "状态", "榜单", "标签", "类型")) return inherited
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

    @androidx.compose.runtime.Immutable
    data class DynamicSelectorUi(
        val id: String,
        val title: String,
        val targets: ImmutableList<DiscoverySuiteWidgetTarget>,
        val selectedTitle: String?,
        val type: SelectorType = SelectorType.TagBar,
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
        val showDiscoveryConfig: Boolean = false,
        val selectedWidgetTargets: ImmutableMap<String, String> = persistentMapOf(),
        val showCategorySheet: Boolean = false,
        val dynamicSelectors: ImmutableList<DynamicSelectorUi> = persistentListOf(),
        val dynamicCategoryTargets: ImmutableList<DiscoverySuiteWidgetTarget> = persistentListOf(),
        val dynamicRankTargets: ImmutableList<ImmutableList<DiscoverySuiteWidgetTarget>> = persistentListOf(),
        val selectedSourceName: String? = null,
        val suiteSearchBooks: ImmutableList<SearchBook>? = null,
        val suiteSearchLoading: Boolean = false,
        val suiteSearchRemote: Boolean = false,
        val suiteSearchPage: Int = 1,
        val suiteSearchIsEnd: Boolean = true,
        val widgetPages: ImmutableMap<String, Int> = persistentMapOf(),
        val widgetIsEnd: ImmutableMap<String, Boolean> = persistentMapOf(),
    ) : ListUiState<BookSourcePart>

    private fun buildKindValues(kinds: List<ExploreKind>, sourceUrl: String): Map<String, String> {
        val infoMap = getExploreInfoMap(sourceUrl)
        var shouldSave = false
        val values = HashMap<String, String>()
        kinds.forEach { kind ->
            when (kind.type) {
                ExploreKind.Type.text -> values[kind.title] = infoMap[kind.title].orEmpty()
                ExploreKind.Type.toggle, ExploreKind.Type.select -> {
                    val chars = kind.chars?.filterNotNull()?.takeIf { it.isNotEmpty() }
                        ?: listOf("chars", "is null")
                    val value = infoMap[kind.title]?.takeUnless { it.isEmpty() }
                        ?: (kind.default ?: chars.first()).also {
                            infoMap[kind.title] = it
                            shouldSave = true
                        }
                    values[kind.title] = value
                }
            }
        }
        if (shouldSave) infoMap.saveNow()
        return values
    }

    private companion object {
        const val DYNAMIC_LEVEL_PREFIX = "dynamic_level_"
        val STATUS_SELECTOR_TITLES = setOf("全部", "完结", "连载", "完本", "在更", "已完结", "连载中", "Finished", "Loading")
        val RANK_SELECTOR_TITLES = setOf("推荐", "评分", "热门", "周榜", "月榜", "总榜", "日榜", "本周", "本月", "本日")
    }
}

sealed interface ExploreListItem {
    val key: String

    data class Header(val source: BookSourcePart) : ExploreListItem {
        override val key: String = source.bookSourceUrl
    }

    data class KindRow(
        val sourceUrl: String,
        val rowIndex: Int,
        val rowItems: ImmutableList<Pair<ExploreKind, Int>>,
    ) : ExploreListItem {
        override val key: String = "${sourceUrl}_$rowIndex"
    }
}

sealed interface ExploreEffect {
    data class ExecuteKindAction(val sourceUrl: String, val kind: ExploreKind) : ExploreEffect
    data class OpenEdit(val sourceUrl: String) : ExploreEffect
    data class OpenSearch(val source: BookSourcePart) : ExploreEffect
    data class OpenLogin(val sourceUrl: String) : ExploreEffect
    data class OpenBookInfo(
        val name: String,
        val author: String,
        val bookUrl: String,
        val origin: String?,
        val coverPath: String?,
        val sharedCoverKey: String?,
    ) : ExploreEffect
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

fun buildExploreListItems(state: ExploreViewModel.ExploreUiState): ImmutableList<ExploreListItem> {
    if (state.items.isEmpty()) return persistentListOf()
    val expandedId = state.expandedId
    val kindRows = if (expandedId != null) calculateExploreKindRows(state.exploreKinds, 6) else emptyList()
    return buildList {
        state.items.forEach { source ->
            add(ExploreListItem.Header(source))
            if (source.bookSourceUrl == expandedId) {
                kindRows.forEachIndexed { index, row ->
                    add(
                        ExploreListItem.KindRow(
                            sourceUrl = source.bookSourceUrl,
                            rowIndex = index,
                            rowItems = row.toImmutableList(),
                        ),
                    )
                }
            }
        }
    }.toImmutableList()
}
