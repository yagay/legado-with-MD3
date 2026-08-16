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
    val resolvedTags: ImmutableMap<String, String> = persistentMapOf(),
    val showDiscoveryConfig: Boolean = false,
    val selectedWidgetTargets: ImmutableMap<String, String> = persistentMapOf(),
    val selectedRankDimension: String? = null,
    val selectedRankStatus: String? = null,
    val showCategorySheet: Boolean = false,
    val dynamicSelectors: ImmutableList<DynamicSelectorUi> = persistentListOf(),
    val dynamicControls: ImmutableList<ExploreKind> = persistentListOf(),
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
                refreshSuite()
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

    fun resolveSelectedSourceName() {
        val state = vm.uiState.value
        val suite = state.enhance.selectedSuite ?: return
        val source = suite.defaultSourceUrl?.let { url ->
            state.items.find { it.bookSourceUrl == url }
        } ?: state.items.firstOrNull()
        val sourceName = source?.bookSourceName
        if (sourceName != state.enhance.selectedSourceName) {
            vm.updateUiState {
                it.copy(enhance = it.enhance.copy(selectedSourceName = sourceName))
            }
        }
        if (source != null && state.enhance.dynamicSelectors.isEmpty()) refreshSuite()
    }

    private fun switchSuite(suite: DiscoverySuite) {
        DiscoverySuiteStore.setSelectedSuiteId(suite.id)
        vm.updateUiState { it.copy(enhance = it.enhance.copy(selectedSuite = suite)) }
        resolveSelectedSourceName()
        refreshSuite()
    }

    private fun refreshSuite() {
        val suite = vm.uiState.value.enhance.selectedSuite ?: return
        val defaultSourceUrl = suite.defaultSourceUrl ?: vm.uiState.value.items.firstOrNull()?.bookSourceUrl ?: return
        widgetRequestVersion++
        suiteSearchControl = null
        vm.updateUiState {
            it.copy(
                enhance = it.enhance.copy(
                    widgetBooks = persistentMapOf(),
                    widgetLoading = persistentMapOf(),
                    selectedWidgetTargets = persistentMapOf(),
                    widgetPages = persistentMapOf(),
                    widgetIsEnd = persistentMapOf(),
                    dynamicSelectors = persistentListOf(),
                    dynamicControls = persistentListOf(),
                    selectedWidgetKeys = persistentMapOf(),
                    suiteSearchBooks = null,
                    suiteSearchLoading = false,
                    suiteSearchRemote = false
                )
            )
        }
        vm.viewModelScope.launch(IO) {
            val source = try {
                vm.exploreRepository.getBookSource(defaultSourceUrl)
            } catch (_: Exception) {
                null
            }
            allSourceRawKinds = try {
                source?.exploreKinds().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            val classification = try {
                ModernExploreClassificationEngine.classify(
                    allSourceRawKinds,
                    source?.exploreKindsJson().orEmpty()
                )
            } catch (_: Exception) {
                ModernExploreClassificationEngine.classify(allSourceRawKinds, "")
            }
            allSourceKinds = classification.nodes
            allSourceMode = classification.mode
            allSourceControls = if (classification.mode == ExploreMode.TREE) {
                ModernExploreControlExtractor.fromTreeRoot(classification.nodes)
            } else {
                ModernExploreControlExtractor.fromFlatKinds(allSourceRawKinds)
            }
            refreshNativeControls()
            rebuildSelectors(suite, defaultSourceUrl)
        }
    }

    private fun refreshNativeControls() {
        val result = ModernExploreControlExtractor.extractNativeControls(allSourceRawKinds)
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
        val defaultSourceUrl = suite.defaultSourceUrl ?: state.items.firstOrNull()?.bookSourceUrl ?: return
        val currentUrl = enhance.selectedWidgetTargets["current_url"] ?: return
        val requestVersion = widgetRequestVersion
        val nextPage = (enhance.widgetPages[widgetId] ?: 1) + 1
        vm.updateUiState {
            it.copy(enhance = it.enhance.copy(widgetLoading = (it.enhance.widgetLoading + (widgetId to true)).toImmutableMap()))
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
            } catch (_: Exception) {
                vm.updateUiState {
                    it.copy(
                        enhance = it.enhance.copy(
                            widgetLoading = (it.enhance.widgetLoading + (widgetId to false)).toImmutableMap(),
                            widgetIsEnd = (it.enhance.widgetIsEnd + (widgetId to true)).toImmutableMap()
                        )
                    )
                }
            }
        }
    }

    private fun saveSelection(widgetId: String, title: String) {
        if (title.isEmpty()) return
        val suite = vm.uiState.value.enhance.selectedSuite ?: return
        val sourceUrl = suite.defaultSourceUrl ?: vm.uiState.value.items.firstOrNull()?.bookSourceUrl ?: return
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
        val defaultSourceUrl = suite.defaultSourceUrl ?: vm.uiState.value.items.firstOrNull()?.bookSourceUrl ?: return
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
        val sourceIndex = widgetId.removePrefix(DYNAMIC_SELECT_PREFIX).toIntOrNull() ?: return
        val control = allSourceControls.firstOrNull { it.sourceIndex == sourceIndex } ?: return
        val value = target.title
        if (value !in control.options) return
        if (vm.uiState.value.enhance.selectedWidgetTargets[widgetId] == value) return
        saveSelection(widgetId, value)
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
                val classification = ModernExploreClassificationEngine.classify(
                    allSourceRawKinds,
                    source.exploreKindsJson()
                )
                allSourceKinds = classification.nodes
                allSourceMode = classification.mode
                allSourceControls = if (classification.mode == ExploreMode.TREE) {
                    ModernExploreControlExtractor.fromTreeRoot(classification.nodes)
                } else {
                    ModernExploreControlExtractor.fromFlatKinds(allSourceRawKinds)
                }
                refreshNativeControls()
                rebuildSelectors(suite, defaultSourceUrl)
            } catch (_: Exception) {
            }
        }
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
                it.children.isNotEmpty() || !it.url.isNullOrBlank()
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

        allSourceControls.sortedBy { it.sourceIndex }.forEach { control ->
            val widgetId = "$DYNAMIC_SELECT_PREFIX${control.sourceIndex}"
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

        val orderedSelectors = if (allSourceMode == ExploreMode.TREE) {
            selectors
        } else {
            selectors.withIndex()
                .sortedWith(compareBy({ selectorSourceIndex(it.value) }, { it.index }))
                .map { it.value }
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
        vm.updateUiState { it.copy(enhance = it.enhance.copy(widgetLoading = (it.enhance.widgetLoading + (widgetId to true)).toImmutableMap())) }
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
                            widgetIsEnd = (it.enhance.widgetIsEnd + (widgetId to false)).toImmutableMap()
                        )
                    )
                }
            } catch (_: Exception) {
                if (requestVersion == widgetRequestVersion &&
                    vm.uiState.value.enhance.selectedWidgetTargets["current_url"] == tagUrl
                ) {
                    vm.updateUiState { it.copy(enhance = it.enhance.copy(widgetLoading = (it.enhance.widgetLoading + (widgetId to false)).toImmutableMap())) }
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
            val sourceUrl = suite.defaultSourceUrl ?: state.items.firstOrNull()?.bookSourceUrl ?: return@launch
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
        val sourceUrl = suite.defaultSourceUrl ?: state.items.firstOrNull()?.bookSourceUrl ?: return
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
