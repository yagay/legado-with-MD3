package io.legado.app.enhance.explore.vm

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.builder.ModernExploreControlExtractor
import io.legado.app.enhance.explore.builder.ModernExploreControlExtractor.SearchControl
import io.legado.app.enhance.explore.builder.ModernExploreControlExtractor.SelectControl
import io.legado.app.enhance.explore.builder.ModernExploreClassificationEngine
import io.legado.app.enhance.explore.model.ExploreMode
import io.legado.app.enhance.explore.model.ExploreNode
import io.legado.app.enhance.explore.model.DiscoverySuite
import io.legado.app.enhance.explore.model.DiscoverySuiteConfig
import io.legado.app.enhance.explore.model.DiscoverySuiteStore
import io.legado.app.enhance.explore.model.DiscoverySuiteWidgetTarget
import io.legado.app.enhance.explore.model.DiscoverySuiteWidgetType
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.exploreKindsJson
import io.legado.app.help.source.getExploreInfoMap
import io.legado.app.ui.main.explore.ExploreEffect
import io.legado.app.ui.main.explore.ExploreIntent
import io.legado.app.ui.main.explore.ExploreViewModel
import io.legado.app.ui.main.explore.ExploreViewModel.DynamicSelectorUi
import io.legado.app.ui.widget.components.explore.calculateExploreKindRows
import kotlinx.collections.immutable.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
data class EnhanceState(
    val suites: ImmutableList<DiscoverySuite> = persistentListOf(),
    val selectedSuite: DiscoverySuite? = null,
    val widgetBooks: ImmutableMap<String, ImmutableList<SearchBook>> = persistentMapOf(),
    val widgetLoading: ImmutableMap<String, Boolean> = persistentMapOf(),
    val widgetErrors: ImmutableMap<String, String> = persistentMapOf(),
    val exploreError: String? = null,
    val resolvedTags: ImmutableMap<String, String> = persistentMapOf(),
    val showDiscoveryConfig: Boolean = false,
    val selectedWidgetTargets: ImmutableMap<String, String> = persistentMapOf(),
    val selectedRankDimension: String? = null,
    val selectedRankStatus: String? = null,
    val showCategorySheet: Boolean = false,
    val dynamicSelectors: ImmutableList<DynamicSelectorUi> = persistentListOf(),
    val dynamicControls: ImmutableList<ExploreKind> = persistentListOf(),
    val sourceKindPreviewRows: ImmutableList<ImmutableList<Pair<ExploreKind, Int>>> = persistentListOf(),
    val sourceKindPreviewReady: Boolean = false,
    val selectedWidgetKeys: ImmutableMap<String, String> = persistentMapOf(),
    val dynamicCategoryTargets: ImmutableList<DiscoverySuiteWidgetTarget> = persistentListOf(),
    val dynamicRankTargets: ImmutableList<ImmutableList<DiscoverySuiteWidgetTarget>> = persistentListOf(),
    val dynamicChannelTargets: ImmutableList<DiscoverySuiteWidgetTarget> = persistentListOf(),
    val channelCategoryMap: ImmutableMap<String, ImmutableList<DiscoverySuiteWidgetTarget>> = persistentMapOf(),
    val selectedSourceName: String? = null,
    /** null = 未处于瀑布流搜索结果模式；非 null = 顶部搜索的书籍结果 */
    val suiteSearchBooks: ImmutableList<SearchBook>? = null,
    val suiteSearchLoading: Boolean = false,
    /** true 表示来自书源 searchUrl；false 表示本地过滤当前已加载书籍 */
    val suiteSearchRemote: Boolean = false,
    /** name(default) / author / kind */
    val suiteSearchField: String = "name",
    val suiteSearchPage: Int = 1,
    val suiteSearchIsEnd: Boolean = true,
    val widgetPages: ImmutableMap<String, Int> = persistentMapOf(),
    val widgetIsEnd: ImmutableMap<String, Boolean> = persistentMapOf(),
)

class ExploreViewModelEnhance(private val vm: ExploreViewModel) {

    private var allSourceKinds: List<ExploreNode> = emptyList()
    private var allSourceRawKinds: List<ExploreKind> = emptyList()
    private var allSourceMode: ExploreMode = ExploreMode.FLAT
    private var allSourceControls: List<SelectControl> = emptyList()
    private var suiteSearchControl: SearchControl? = null
    private var suiteSearchJob: Job? = null
    private var suiteRefreshJob: Job? = null
    private var suiteRefreshVersion: Long = 0L
    /** Invalidates stale waterfall loads whenever a dynamic control/source changes. */
    private var widgetRequestVersion: Long = 0L

    fun onIntent(intent: ExploreIntent): Boolean {
        when (intent) {
            is ExploreIntent.Search -> {}
            is ExploreIntent.SwitchSuite -> switchSuite(intent.suite)
            is ExploreIntent.RefreshSuite -> {
                allSourceKinds = emptyList()
                allSourceRawKinds = emptyList()
                allSourceMode = ExploreMode.FLAT
                allSourceControls = emptyList()
                suiteSearchControl = null
                refreshSuite(clearKindsCache = true)
            }
            is ExploreIntent.SetSuiteDefaultSource -> setSuiteDefaultSource(intent.sourceUrl)
            is ExploreIntent.ShowDiscoveryConfig -> vm.updateUiState {
                it.copy(enhance = it.enhance.copy(showDiscoveryConfig = intent.show))
            }
            is ExploreIntent.UpdateDiscoverySettings -> updateDiscoverySettings(intent.transform)
            is ExploreIntent.SelectWidgetTarget -> selectWidgetTarget(intent.widgetId, intent.target)
            is ExploreIntent.LoadMoreWidgetData -> loadMoreWidgetData(intent.widgetId)
            is ExploreIntent.LoadMoreSuiteSearch -> loadMoreSuiteSearch()
            is ExploreIntent.SetSuiteSearchField -> setSuiteSearchField(intent.field)
            is ExploreIntent.ToggleCategorySheet -> vm.updateUiState {
                it.copy(enhance = it.enhance.copy(showCategorySheet = intent.show))
            }
            is ExploreIntent.OpenBook -> vm.emitEffect(
                ExploreEffect.OpenBookInfo(
                    name = intent.book.name,
                    author = intent.book.author,
                    bookUrl = intent.book.bookUrl,
                    origin = intent.book.origin,
                    coverPath = intent.book.coverUrl,
                    sharedCoverKey = intent.sharedCoverKey
                )
            )
            is ExploreIntent.ToggleLayoutMode -> vm.toggleLayoutMode()
            else -> return false
        }
        return true
    }

    fun loadDiscoverySuite() {
        val config = DiscoverySuiteStore.load()
        val selectedId = DiscoverySuiteStore.getSelectedSuiteId()
        val selectedSuite = config.suites.find { it.id == selectedId } ?: config.suites.firstOrNull()
        vm.updateUiState {
            it.copy(
                enhance = it.enhance.copy(
                    suites = config.suites.toImmutableList(),
                    selectedSuite = selectedSuite,
                )
            )
        }
        resolveSelectedSourceName()
        if (selectedSuite != null) refreshSuite()
    }

    private fun effectiveSourceUrl(
        suite: DiscoverySuite? = vm.uiState.value.enhance.selectedSuite
    ): String? {
        val resolvedSuite = suite ?: return null
        val items = vm.uiState.value.items
        return resolvedSuite.defaultSourceUrl
            ?.takeIf { saved -> items.any { it.bookSourceUrl == saved } }
            ?: items.firstOrNull()?.bookSourceUrl
    }

    private fun repairSuiteDefaultSource(suite: DiscoverySuite, sourceUrl: String): DiscoverySuite {
        if (suite.defaultSourceUrl == sourceUrl) return suite
        val repaired = suite.copy(defaultSourceUrl = sourceUrl)
        val config = DiscoverySuiteStore.load()
        val updated = config.copy(
            suites = config.suites.map { if (it.id == suite.id) repaired else it }
        )
        DiscoverySuiteStore.save(updated)
        vm.updateUiState { state ->
            state.copy(
                enhance = state.enhance.copy(
                    selectedSuite = if (state.enhance.selectedSuite?.id == suite.id) repaired else state.enhance.selectedSuite,
                    suites = updated.suites.toImmutableList(),
                )
            )
        }
        return repaired
    }

    fun resolveSelectedSourceName() {
        val state = vm.uiState.value
        val suite = state.enhance.selectedSuite ?: return
        val sourceUrl = effectiveSourceUrl(suite)
        val sourceName = sourceUrl
            ?.let { url -> state.items.find { it.bookSourceUrl == url } }
            ?.bookSourceName
        if (sourceName != state.enhance.selectedSourceName) {
            vm.updateUiState {
                it.copy(enhance = it.enhance.copy(selectedSourceName = sourceName))
            }
        }
    }

    private fun switchSuite(suite: DiscoverySuite) {
        DiscoverySuiteStore.setSelectedSuiteId(suite.id)
        vm.updateUiState { it.copy(enhance = it.enhance.copy(selectedSuite = suite)) }
        resolveSelectedSourceName()
        refreshSuite()
    }

    private fun refreshSuite(clearKindsCache: Boolean = false) {
        val initialSuite = vm.uiState.value.enhance.selectedSuite ?: return
        val defaultSourceUrl = effectiveSourceUrl(initialSuite) ?: return
        val suite = repairSuiteDefaultSource(initialSuite, defaultSourceUrl)
        suiteRefreshJob?.cancel()
        val refreshVersion = ++suiteRefreshVersion
        widgetRequestVersion++
        suiteSearchControl = null
        vm.updateUiState {
            it.copy(
                enhance = it.enhance.copy(
                    widgetBooks = persistentMapOf(),
                    widgetLoading = persistentMapOf(),
                    widgetErrors = persistentMapOf(),
                    exploreError = null,
                    selectedWidgetTargets = persistentMapOf(),
                    widgetPages = persistentMapOf(),
                    widgetIsEnd = persistentMapOf(),
                    dynamicSelectors = persistentListOf(),
                    dynamicControls = persistentListOf(),
                    sourceKindPreviewRows = persistentListOf(),
                    sourceKindPreviewReady = false,
                    selectedWidgetKeys = persistentMapOf(),
                    suiteSearchBooks = null,
                    suiteSearchLoading = false,
                    suiteSearchRemote = false
                )
            )
        }
        suiteRefreshJob = vm.viewModelScope.launch(IO) {
            val source = try {
                vm.exploreRepository.getBookSource(defaultSourceUrl)
            } catch (_: Exception) {
                null
            }
            if (clearKindsCache) {
                try {
                    source?.clearExploreKindsCache()
                } catch (_: Exception) {
                }
            }
            val rawKinds = try {
                source?.exploreKinds().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            val sourceKindPreviewRows = buildSourceKindPreviewRows(rawKinds)
            val classification = try {
                ModernExploreClassificationEngine.classify(
                    rawKinds,
                    source?.exploreKindsJson().orEmpty()
                )
            } catch (_: Exception) {
                ModernExploreClassificationEngine.classify(rawKinds, "")
            }
            if (
                refreshVersion != suiteRefreshVersion ||
                effectiveSourceUrl() != defaultSourceUrl
            ) return@launch

            allSourceRawKinds = rawKinds
            allSourceKinds = classification.nodes
            allSourceMode = classification.mode
            val classifiedKinds = if (classification.mode == ExploreMode.TREE) {
                ModernExploreControlExtractor.flattenOriginalKinds(classification.nodes)
            } else {
                rawKinds
            }
            val exploreError = classifiedKinds
                .firstOrNull { it.title.startsWith("ERROR:", ignoreCase = true) }
                ?.let { it.url?.takeIf(String::isNotBlank) ?: it.title }
            vm.updateUiState { state ->
                state.copy(
                    enhance = state.enhance.copy(
                        exploreError = exploreError,
                        sourceKindPreviewRows = sourceKindPreviewRows,
                        sourceKindPreviewReady = true,
                    )
                )
            }
            allSourceControls = if (classification.mode == ExploreMode.TREE) {
                ModernExploreControlExtractor.fromTreeRoot(classification.nodes)
            } else {
                ModernExploreControlExtractor.fromFlatKinds(rawKinds)
            }
            initializeExploreDefaults(defaultSourceUrl)
            refreshNativeControls()
            rebuildSelectors(suite, defaultSourceUrl)
        }
    }

    private suspend fun initializeExploreDefaults(sourceUrl: String) {
        val infoMap = getExploreInfoMap(sourceUrl)
        var shouldSave = false
        val kinds = if (allSourceMode == ExploreMode.TREE) {
            ModernExploreControlExtractor.flattenOriginalKinds(allSourceKinds)
        } else {
            allSourceRawKinds
        }
        kinds.forEach { kind ->
            val defaultValue = kind.default ?: return@forEach
            if (infoMap[kind.title] == null) {
                infoMap[kind.title] = defaultValue
                shouldSave = true
            }
        }
        if (shouldSave) infoMap.saveNow()
    }

    private fun refreshNativeControls() {
        val nativeKinds = if (allSourceMode == ExploreMode.TREE) {
            ModernExploreControlExtractor.flattenOriginalKinds(allSourceKinds)
        } else {
            allSourceRawKinds
        }
        val result = ModernExploreControlExtractor.extractNativeControls(nativeKinds)
        suiteSearchControl = result.searchControl
        vm.updateUiState { state ->
            state.copy(
                enhance = state.enhance.copy(
                    dynamicControls = result.visibleControls.toImmutableList()
                )
            )
        }
    }

    private fun loadMoreWidgetData(widgetId: String) {
        val state = vm.uiState.value
        val enhance = state.enhance
        if (enhance.widgetLoading[widgetId] == true || enhance.widgetIsEnd[widgetId] == true) return
        val suite = enhance.selectedSuite ?: return
        val defaultSourceUrl = effectiveSourceUrl(suite) ?: return
        val currentUrl = enhance.selectedWidgetTargets["current_url"] ?: return
        val requestVersion = widgetRequestVersion
        val nextPage = (enhance.widgetPages[widgetId] ?: 1) + 1
        vm.updateUiState {
            it.copy(
                enhance = it.enhance.copy(
                    widgetLoading = (it.enhance.widgetLoading + (widgetId to true)).toImmutableMap(),
                    widgetErrors = (it.enhance.widgetErrors - widgetId).toImmutableMap(),
                )
            )
        }
        vm.viewModelScope.launch(IO) {
            try {
                val result = vm.exploreBooksUseCase.execute(
                    sourceUrl = defaultSourceUrl,
                    moduleUrl = currentUrl,
                    args = null,
                    page = nextPage
                )
                if (requestVersion != widgetRequestVersion ||
                    vm.uiState.value.enhance.selectedWidgetTargets["current_url"] != currentUrl
                ) return@launch
                if (result.books.isEmpty()) {
                    vm.updateUiState {
                        it.copy(
                            enhance = it.enhance.copy(
                                widgetIsEnd = (it.enhance.widgetIsEnd + (widgetId to true)).toImmutableMap(),
                                widgetLoading = (it.enhance.widgetLoading + (widgetId to false)).toImmutableMap()
                            )
                        )
                    }
                    return@launch
                }
                vm.updateUiState { s ->
                    val currentBooks = s.enhance.widgetBooks[widgetId] ?: persistentListOf()
                    val newBooksList = (currentBooks + result.books).distinctBy { it.bookUrl }
                    s.copy(
                        enhance = s.enhance.copy(
                            widgetBooks = (s.enhance.widgetBooks + (widgetId to newBooksList.toImmutableList())).toImmutableMap(),
                            widgetLoading = (s.enhance.widgetLoading + (widgetId to false)).toImmutableMap(),
                            widgetPages = (s.enhance.widgetPages + (widgetId to nextPage)).toImmutableMap(),
                            widgetIsEnd = (s.enhance.widgetIsEnd + (widgetId to (newBooksList.size == currentBooks.size))).toImmutableMap()
                        )
                    )
                }
            } catch (e: Exception) {
                if (requestVersion == widgetRequestVersion &&
                    vm.uiState.value.enhance.selectedWidgetTargets["current_url"] == currentUrl
                ) {
                    vm.updateUiState {
                        it.copy(
                            enhance = it.enhance.copy(
                                widgetLoading = (it.enhance.widgetLoading + (widgetId to false)).toImmutableMap(),
                                widgetErrors = (it.enhance.widgetErrors + (widgetId to (e.localizedMessage ?: e.toString()))).toImmutableMap(),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun saveSelection(widgetId: String, title: String) {
        if (title.isEmpty()) return
        val suite = vm.uiState.value.enhance.selectedSuite ?: return
        val sourceUrl = effectiveSourceUrl(suite) ?: return
        val config = DiscoverySuiteStore.load()
        DiscoverySuiteStore.save(
            config.copy(lastSelectedTargets = config.lastSelectedTargets + ("${sourceUrl}_$widgetId" to title))
        )
    }

    private fun updateDiscoverySettings(transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig) {
        val config = DiscoverySuiteStore.load()
        val updated = transform(config)
        DiscoverySuiteStore.save(updated)
        vm.updateUiState { it.copy(enhance = it.enhance.copy(suites = updated.suites.toImmutableList())) }
        val current = vm.uiState.value.enhance.selectedSuite ?: return
        updated.suites.find { it.id == current.id }?.let { updatedCurrent ->
            vm.updateUiState { it.copy(enhance = it.enhance.copy(selectedSuite = updatedCurrent)) }
            refreshSuite()
        }
    }

    private fun selectWidgetTarget(widgetId: String, target: DiscoverySuiteWidgetTarget) {
        val suite = vm.uiState.value.enhance.selectedSuite ?: return
        val defaultSourceUrl = effectiveSourceUrl(suite) ?: return
        if (widgetId.startsWith(DYNAMIC_SELECT_PREFIX)) {
            selectControlTarget(widgetId, target, suite, defaultSourceUrl)
            return
        }
        if (!widgetId.startsWith(DYNAMIC_LEVEL_PREFIX)) return
        val level = widgetId.removePrefix(DYNAMIC_LEVEL_PREFIX).toIntOrNull() ?: return
        val targetKey = dynamicTargetKey(target)
        if (vm.uiState.value.enhance.selectedWidgetKeys[widgetId] == targetKey) return
        saveSelection(widgetId, targetKey)
        vm.updateUiState { state ->
            val newSelections = state.enhance.selectedWidgetTargets.toMutableMap()
            val newKeys = state.enhance.selectedWidgetKeys.toMutableMap()
            newSelections[widgetId] = target.title
            newKeys[widgetId] = targetKey
            newSelections.keys.filter { key ->
                key.startsWith(DYNAMIC_LEVEL_PREFIX) &&
                    (key.removePrefix(DYNAMIC_LEVEL_PREFIX).toIntOrNull() ?: -1) > level
            }.toList().forEach { key ->
                newSelections.remove(key)
                newKeys.remove(key)
            }
            newSelections.remove("current_url")
            state.copy(enhance = state.enhance.copy(
                selectedWidgetTargets = newSelections.toImmutableMap(),
                selectedWidgetKeys = newKeys.toImmutableMap()
            ))
        }
        vm.viewModelScope.launch(IO) { rebuildSelectors(suite, defaultSourceUrl) }
    }

    private fun selectControlTarget(
        widgetId: String,
        target: DiscoverySuiteWidgetTarget,
        suite: DiscoverySuite,
        defaultSourceUrl: String
    ) {
        val sourceKey = widgetId.removePrefix(DYNAMIC_SELECT_PREFIX).takeIf { it.isNotBlank() } ?: return
        val control = allSourceControls.firstOrNull { it.sourceKey == sourceKey } ?: return
        val value = target.title
        if (value !in control.options) return
        if (vm.uiState.value.enhance.selectedWidgetTargets[widgetId] == value) return
        saveSelection(widgetId, value)
        suiteRefreshJob?.cancel()
        val refreshVersion = ++suiteRefreshVersion
        widgetRequestVersion++
        vm.updateUiState { state ->
            val selections = state.enhance.selectedWidgetTargets.toMutableMap().apply {
                put(widgetId, value)
                remove("current_url")
            }
            state.copy(
                enhance = state.enhance.copy(
                    selectedWidgetTargets = selections.toImmutableMap(),
                    widgetBooks = persistentMapOf(),
                    widgetLoading = persistentMapOf(),
                    widgetPages = persistentMapOf(),
                    widgetIsEnd = persistentMapOf(),
                    suiteSearchBooks = null,
                    suiteSearchLoading = false,
                    suiteSearchRemote = false,
                    sourceKindPreviewRows = persistentListOf(),
                    sourceKindPreviewReady = false,
                )
            )
        }
        vm.viewModelScope.launch(IO) {
            try {
                val source = vm.exploreRepository.getBookSource(defaultSourceUrl) ?: return@launch
                val infoMap = getExploreInfoMap(defaultSourceUrl)
                infoMap[control.kind.title] = value
                infoMap.saveNow()

                vm.exploreKindUseCase.executeAction(
                    action = control.kind.action,
                    title = control.kind.title,
                    sourceUrl = defaultSourceUrl,
                    infoMap = infoMap,
                    activity = null,
                    onRefreshKinds = {}
                )

                source.clearExploreKindsCache()
                allSourceRawKinds = source.exploreKinds()
                val sourceKindPreviewRows = buildSourceKindPreviewRows(allSourceRawKinds)
                val classification = ModernExploreClassificationEngine.classify(
                    allSourceRawKinds,
                    source.exploreKindsJson()
                )
                if (
                    refreshVersion != suiteRefreshVersion ||
                    effectiveSourceUrl() != defaultSourceUrl
                ) return@launch
                allSourceKinds = classification.nodes
                allSourceMode = classification.mode
                val classifiedKinds = if (classification.mode == ExploreMode.TREE) {
                    ModernExploreControlExtractor.flattenOriginalKinds(classification.nodes)
                } else {
                    allSourceRawKinds
                }
                val exploreError = classifiedKinds
                    .firstOrNull { it.title.startsWith("ERROR:", ignoreCase = true) }
                    ?.let { it.url?.takeIf(String::isNotBlank) ?: it.title }
                vm.updateUiState { state ->
                    state.copy(
                        enhance = state.enhance.copy(
                            exploreError = exploreError,
                            sourceKindPreviewRows = sourceKindPreviewRows,
                            sourceKindPreviewReady = true,
                        )
                    )
                }
                allSourceControls = if (classification.mode == ExploreMode.TREE) {
                    ModernExploreControlExtractor.fromTreeRoot(classification.nodes)
                } else {
                    ModernExploreControlExtractor.fromFlatKinds(allSourceRawKinds)
                }
                initializeExploreDefaults(defaultSourceUrl)
                refreshNativeControls()
                rebuildSelectors(suite, defaultSourceUrl)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildSourceKindPreviewRows(
        kinds: List<ExploreKind>
    ): ImmutableList<ImmutableList<Pair<ExploreKind, Int>>> {
        return calculateExploreKindRows(kinds, maxSpan = 6)
            .map { it.toImmutableList() }
            .toImmutableList()
    }

    private fun rebuildSelectors(suite: DiscoverySuite, defaultSourceUrl: String) {
        val selectors = mutableListOf<DynamicSelectorUi>()
        val selectorKeys = mutableMapOf<String, String>()
        val config = DiscoverySuiteStore.load()
        if (allSourceKinds.isEmpty()) return
        var currentLevelItems = allSourceKinds
        var inheritedTitle: String? = null
        var lastValidUrl: String? = null
        var level = 0
        val safetyLimit = (countExploreNodes(allSourceKinds) + 1).coerceAtLeast(16)
        var steps = 0
        while (currentLevelItems.isNotEmpty() && steps++ < safetyLimit) {
            while (
                currentLevelItems.size == 1 &&
                currentLevelItems.first().url.isNullOrBlank() &&
                currentLevelItems.first().children.isNotEmpty()
            ) {
                val container = currentLevelItems.first()
                inheritedTitle = cleanExploreTitle(container.title).ifBlank { inheritedTitle }
                currentLevelItems = container.children.orEmpty()
                if (currentLevelItems.isEmpty()) break
            }
            if (currentLevelItems.isEmpty()) break
            val visibleItems = currentLevelItems.filter {
                !it.title.startsWith("ERROR:", ignoreCase = true) &&
                    (it.children.isNotEmpty() || !it.url.isNullOrBlank())
            }
            if (visibleItems.isEmpty()) break
            val widgetId = "$DYNAMIC_LEVEL_PREFIX$level"
            val targets = visibleItems.map { kind ->
                DiscoverySuiteWidgetTarget(
                    sourceUrl = defaultSourceUrl,
                    tagUrl = kind.url.orEmpty(),
                    title = kind.title
                )
            }
            val savedKey = config.lastSelectedTargets["${defaultSourceUrl}_$widgetId"]
            val stateKey = vm.uiState.value.enhance.selectedWidgetKeys[widgetId]
            val legacyTitle = vm.uiState.value.enhance.selectedWidgetTargets[widgetId]
            val selectedIndex = stateKey
                ?.let { key -> targets.indexOfFirst { dynamicTargetKey(it) == key }.takeIf { it >= 0 } }
                ?: savedKey?.let { key ->
                    targets.indexOfFirst { dynamicTargetKey(it) == key }.takeIf { it >= 0 }
                        ?: targets.indexOfFirst { it.title == key }.takeIf { it >= 0 }
                }
                ?: legacyTitle?.let { title -> targets.indexOfFirst { it.title == title }.takeIf { it >= 0 } }
                ?: 0
            val selectedTarget = targets[selectedIndex]
            val selectedTitle = selectedTarget.title
            selectorKeys[widgetId] = dynamicTargetKey(selectedTarget)
            selectors += DynamicSelectorUi(
                id = widgetId,
                title = inferSelectorTitle(level, visibleItems, inheritedTitle),
                targets = targets.toImmutableList(),
                selectedTitle = selectedTitle,
                type = inferSelectorType(visibleItems)
            )
            val selectedItem = visibleItems.getOrNull(selectedIndex) ?: break
            selectedItem.url?.let { lastValidUrl = it }
            inheritedTitle = selectedItem.title
            currentLevelItems = selectedItem.children.orEmpty()
            level++
        }

        allSourceControls.forEach { control ->
            val widgetId = "$DYNAMIC_SELECT_PREFIX${control.sourceKey}"
            val targets = control.options.map { value ->
                DiscoverySuiteWidgetTarget(defaultSourceUrl, value, value)
            }
            if (targets.isEmpty()) return@forEach
            val savedTitle = config.lastSelectedTargets["${defaultSourceUrl}_$widgetId"]
            val stateSelected = vm.uiState.value.enhance.selectedWidgetTargets[widgetId]
            val selectedTitle = stateSelected
                ?.takeIf { value -> targets.any { it.title == value } }
                ?: savedTitle?.takeIf { value -> targets.any { it.title == value } }
                ?: control.defaultValue?.takeIf { value -> targets.any { it.title == value } }
                ?: targets.first().title
            selectors += DynamicSelectorUi(
                id = widgetId,
                title = control.title,
                targets = targets.toImmutableList(),
                selectedTitle = selectedTitle,
                type = DynamicSelectorUi.SelectorType.TagBar
            )
        }

        // Flat sources have no structural parent/child ordering, so sourceIndex may be used
        // to restore independent controls to their original declaration order. Once SECTION
        // or TREE has recovered hierarchy, traversal order is authoritative and must not be
        // sorted again by raw source positions.
        val orderedSelectors = if (allSourceMode == ExploreMode.FLAT) {
            selectors.withIndex()
                .sortedWith(compareBy({ selectorSourceIndex(it.value) }, { it.index }))
                .map { it.value }
        } else {
            selectors
        }
        val finalSelections = orderedSelectors
            .associateBy({ it.id }, { it.selectedTitle.orEmpty() })
            .toMutableMap()
        lastValidUrl?.let { finalSelections["current_url"] = it }
        vm.updateUiState { state ->
            val preserved = state.enhance.selectedWidgetTargets
                .filterKeys { key -> !key.startsWith(DYNAMIC_LEVEL_PREFIX) && key != "current_url" }
            val preservedKeys = state.enhance.selectedWidgetKeys
                .filterKeys { key -> !key.startsWith(DYNAMIC_LEVEL_PREFIX) }
            state.copy(
                enhance = state.enhance.copy(
                    dynamicSelectors = orderedSelectors.toImmutableList(),
                    selectedWidgetTargets = (preserved + finalSelections).toImmutableMap(),
                    selectedWidgetKeys = (preservedKeys + selectorKeys).toImmutableMap()
                )
            )
        }
        if (lastValidUrl != null) {
            vm.updateUiState {
                it.copy(
                    enhance = it.enhance.copy(
                        widgetBooks = persistentMapOf(),
                        widgetLoading = persistentMapOf(),
                        widgetPages = persistentMapOf(),
                        widgetIsEnd = persistentMapOf()
                    )
                )
            }
            loadBookWidgetData(suite, lastValidUrl, defaultSourceUrl)
        }
    }

    private fun selectorSourceIndex(selector: DynamicSelectorUi): Int {
        if (selector.id.startsWith(DYNAMIC_SELECT_PREFIX)) {
            return selector.id.removePrefix(DYNAMIC_SELECT_PREFIX).toIntOrNull() ?: Int.MAX_VALUE
        }
        val optionTitles = selector.targets.asSequence()
            .map { cleanExploreTitle(it.title) }
            .filter { it.isNotBlank() }
            .toSet()
        if (optionTitles.isEmpty()) return Int.MAX_VALUE
        return allSourceRawKinds.indexOfFirst { kind ->
            cleanExploreTitle(kind.title) in optionTitles
        }.takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    private fun collectOriginalKinds(nodes: List<ExploreNode>): List<ExploreKind> =
        ModernExploreControlExtractor.flattenOriginalKinds(nodes)

    private fun countExploreNodes(nodes: List<ExploreNode>): Int {
        var count = 0
        val stack = ArrayDeque<ExploreNode>()
        nodes.forEach(stack::addLast)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            count++
            node.children.forEach(stack::addLast)
        }
        return count
    }

    private fun inferSelectorTitle(
        level: Int,
        items: List<ExploreNode>,
        inheritedTitle: String?
    ): String {
        // 现代布局不再根据名称猜测频道/状态/榜单等业务语义。
        val inherited = cleanExploreTitle(inheritedTitle.orEmpty())
        return inherited.takeIf { it.isNotBlank() } ?: "分类"
    }

    private fun inferSelectorType(
        items: List<ExploreNode>
    ): DynamicSelectorUi.SelectorType {
        // RankButtons 仅由显式 DiscoverySuite widget 配置决定。
        return DynamicSelectorUi.SelectorType.TagBar
    }

    private fun cleanExploreTitle(title: String): String = title
        .replace(Regex("[\\[\\]【】?（）<>《》]"), "")
        .replace(Regex("[\\p{So}\\p{Sk}]+"), "")
        .replace(Regex("[༺༻ˇ»«`´ʚɞ]+"), "")
        .trim()

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
        val suite = vm.uiState.value.enhance.selectedSuite ?: return
        val updatedSuite = suite.copy(defaultSourceUrl = sourceUrl)
        val config = DiscoverySuiteStore.load()
        val updatedConfig = config.copy(
            suites = config.suites.map { if (it.id == suite.id) updatedSuite else it }
        )
        DiscoverySuiteStore.save(updatedConfig)
        suiteSearchJob?.cancel()
        vm.updateUiState {
            it.copy(
                enhance = it.enhance.copy(
                    selectedSuite = updatedSuite,
                    suites = updatedConfig.suites.toImmutableList(),
                    suiteSearchBooks = null,
                    suiteSearchLoading = false,
                    suiteSearchRemote = false
                ),
                searchKey = ""
            )
        }
        resolveSelectedSourceName()
        refreshSuite()
    }

    private fun loadWidgetDataWithUrl(widgetId: String, sourceUrl: String, tagUrl: String) {
        if (tagUrl.isEmpty()) return
        val requestVersion = widgetRequestVersion
        vm.updateUiState {
            it.copy(
                enhance = it.enhance.copy(
                    widgetLoading = (it.enhance.widgetLoading + (widgetId to true)).toImmutableMap(),
                    widgetErrors = (it.enhance.widgetErrors - widgetId).toImmutableMap(),
                )
            )
        }
        vm.viewModelScope.launch(IO) {
            try {
                val result = vm.exploreBooksUseCase.execute(sourceUrl = sourceUrl, moduleUrl = tagUrl, args = null)
                if (requestVersion != widgetRequestVersion ||
                    vm.uiState.value.enhance.selectedWidgetTargets["current_url"] != tagUrl
                ) return@launch
                val finalBooks = result.books.distinctBy { it.bookUrl }
                vm.updateUiState {
                    it.copy(
                        enhance = it.enhance.copy(
                            widgetBooks = (it.enhance.widgetBooks + (widgetId to finalBooks.toImmutableList())).toImmutableMap(),
                            widgetLoading = (it.enhance.widgetLoading + (widgetId to false)).toImmutableMap(),
                            widgetPages = (it.enhance.widgetPages + (widgetId to 1)).toImmutableMap(),
                            widgetIsEnd = (it.enhance.widgetIsEnd + (widgetId to finalBooks.isEmpty())).toImmutableMap(),
                            widgetErrors = (it.enhance.widgetErrors - widgetId).toImmutableMap()
                        )
                    )
                }
            } catch (e: Exception) {
                if (requestVersion == widgetRequestVersion &&
                    vm.uiState.value.enhance.selectedWidgetTargets["current_url"] == tagUrl
                ) {
                    vm.updateUiState {
                        it.copy(
                            enhance = it.enhance.copy(
                                widgetLoading = (it.enhance.widgetLoading + (widgetId to false)).toImmutableMap(),
                                widgetErrors = (it.enhance.widgetErrors + (widgetId to (e.localizedMessage ?: e.toString()))).toImmutableMap(),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun setSuiteSearchField(field: String) {
        val normalized = field.takeIf { it == "name" || it == "author" || it == "kind" } ?: "name"
        if (vm.uiState.value.enhance.suiteSearchField == normalized) return
        vm.updateUiState { state ->
            state.copy(
                enhance = state.enhance.copy(
                    suiteSearchField = normalized,
                    suiteSearchBooks = null,
                    suiteSearchLoading = false,
                    suiteSearchRemote = false,
                    suiteSearchPage = 1,
                    suiteSearchIsEnd = true
                )
            )
        }
        val query = vm.uiState.value.searchKey.trim()
        if (query.isNotBlank()) searchSuiteBooks(query)
    }

    fun searchSuiteBooks(query: String) {
        suiteSearchJob?.cancel()
        if (query.isBlank()) {
            vm.updateUiState {
                it.copy(
                    enhance = it.enhance.copy(
                        suiteSearchBooks = null,
                        suiteSearchLoading = false,
                        suiteSearchRemote = false,
                        suiteSearchPage = 1,
                        suiteSearchIsEnd = true
                    )
                )
            }
            return
        }
        suiteSearchJob = vm.viewModelScope.launch {
            delay(300)
            val state = vm.uiState.value
            if (state.layoutMode != 1 || state.searchKey.trim() != query) return@launch
            val suite = state.enhance.selectedSuite ?: return@launch
            val sourceUrl = effectiveSourceUrl(suite) ?: return@launch
            val source = try { vm.exploreRepository.getBookSource(sourceUrl) } catch (_: Exception) { null }
            val field = state.enhance.suiteSearchField
            val localMatches = filterLoadedSuiteBooks(state, suite, query, field)

            // Top-bar search is always local-first. Source-native explore text/button controls
            // are presentation-only here: they are hidden by ModernExploreControlExtractor and
            // never executed by the top-bar search path.
            vm.updateUiState {
                it.copy(
                    enhance = it.enhance.copy(
                        suiteSearchBooks = localMatches.toImmutableList(),
                        suiteSearchLoading = field == "name" && !source?.searchUrl.isNullOrBlank(),
                        suiteSearchRemote = field == "name" && !source?.searchUrl.isNullOrBlank(),
                        suiteSearchPage = 1,
                        suiteSearchIsEnd = field != "name" || source?.searchUrl.isNullOrBlank()
                    )
                )
            }

            if (field == "name" && !source?.searchUrl.isNullOrBlank()) {
                try {
                    val result = vm.exploreBooksUseCase.execute(
                        sourceUrl = sourceUrl,
                        moduleUrl = null,
                        args = null,
                        page = 1,
                        key = query
                    )
                    if (vm.uiState.value.searchKey.trim() == query) {
                        vm.updateUiState {
                            val merged = (localMatches + result.books).distinctBy { book -> book.bookUrl }
                            it.copy(
                                enhance = it.enhance.copy(
                                    suiteSearchBooks = merged.toImmutableList(),
                                    suiteSearchLoading = false,
                                    suiteSearchRemote = true,
                                    suiteSearchPage = 1,
                                    suiteSearchIsEnd = result.books.isEmpty()
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    if (vm.uiState.value.searchKey.trim() == query) {
                        vm.updateUiState {
                            it.copy(
                                enhance = it.enhance.copy(
                                    suiteSearchBooks = localMatches.toImmutableList(),
                                    suiteSearchLoading = false,
                                    suiteSearchRemote = true,
                                    suiteSearchPage = 1,
                                    suiteSearchIsEnd = true
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun filterLoadedSuiteBooks(
        state: ExploreViewModel.ExploreUiState,
        suite: DiscoverySuite,
        query: String,
        field: String = state.enhance.suiteSearchField
    ): List<SearchBook> {
        val bookWidgetId = suite.widgets.firstOrNull { widget ->
            widget.type == DiscoverySuiteWidgetType.WaterfallBooks.type ||
                widget.type == DiscoverySuiteWidgetType.BookList.type ||
                widget.type == DiscoverySuiteWidgetType.HorizontalBooks.type
        }?.id
        val loadedBooks = bookWidgetId?.let { state.enhance.widgetBooks[it] }.orEmpty()
        return loadedBooks.filter { book ->
            when (field) {
                "author" -> book.author.contains(query, ignoreCase = true)
                "kind" -> book.kind.orEmpty().contains(query, ignoreCase = true)
                else -> book.name.contains(query, ignoreCase = true)
            }
        }
    }

    private fun loadMoreSuiteSearch() {
        val state = vm.uiState.value
        val enhance = state.enhance
        if (state.layoutMode != 1 || !enhance.suiteSearchRemote || enhance.suiteSearchLoading || enhance.suiteSearchIsEnd) return
        val query = state.searchKey.trim()
        if (query.isBlank()) return
        val suite = enhance.selectedSuite ?: return
        val sourceUrl = effectiveSourceUrl(suite) ?: return
        val nextPage = enhance.suiteSearchPage + 1
        vm.updateUiState { it.copy(enhance = it.enhance.copy(suiteSearchLoading = true)) }
        vm.viewModelScope.launch(IO) {
            try {
                val result = vm.exploreBooksUseCase.execute(
                    sourceUrl = sourceUrl,
                    moduleUrl = null,
                    args = null,
                    page = nextPage,
                    key = query
                )
                if (vm.uiState.value.searchKey.trim() != query) return@launch
                vm.updateUiState { current ->
                    val old = current.enhance.suiteSearchBooks.orEmpty()
                    val merged = (old + result.books).distinctBy { it.bookUrl }
                    current.copy(
                        enhance = current.enhance.copy(
                            suiteSearchBooks = merged.toImmutableList(),
                            suiteSearchLoading = false,
                            suiteSearchPage = nextPage,
                            suiteSearchIsEnd = result.books.isEmpty() || merged.size == old.size
                        )
                    )
                }
            } catch (_: Exception) {
                if (vm.uiState.value.searchKey.trim() == query) {
                    vm.updateUiState { it.copy(enhance = it.enhance.copy(suiteSearchLoading = false, suiteSearchIsEnd = true)) }
                }
            }
        }
    }

    fun clearSuiteSearchJob() {
        suiteSearchJob?.cancel()
    }

    private fun dynamicTargetKey(target: DiscoverySuiteWidgetTarget): String =
        "${target.title}\u001F${target.tagUrl}"


    private companion object {
        const val DYNAMIC_LEVEL_PREFIX = "dynamic_level_"
        const val DYNAMIC_SELECT_PREFIX = "dynamic_select_"
    }
}