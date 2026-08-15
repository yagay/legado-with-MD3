package io.legado.app.enhance.explore.vm

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.builder.ModernExploreControlExtractor
import io.legado.app.enhance.explore.builder.ModernExploreControlExtractor.SelectControl
import io.legado.app.enhance.explore.builder.ModernExploreClassificationEngine
import io.legado.app.enhance.explore.model.ExploreMode
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
    val suiteSearchPage: Int = 1,
    val suiteSearchIsEnd: Boolean = true,
    val widgetPages: ImmutableMap<String, Int> = persistentMapOf(),
    val widgetIsEnd: ImmutableMap<String, Boolean> = persistentMapOf(),
)

class ExploreViewModelEnhance(private val vm: ExploreViewModel) {

    private var allSourceKinds: List<ExploreKind> = emptyList()
    private var allSourceRawKinds: List<ExploreKind> = emptyList()
    private var allSourceMode: ExploreMode = ExploreMode.FLAT
    private var allSourceControls: List<SelectControl> = emptyList()
    private var suiteSearchJob: Job? = null

    fun onIntent(intent: ExploreIntent): Boolean {
        when (intent) {
            is ExploreIntent.Search -> {} // Let base handle key update, but enhance handles the action via searchSuiteBooks call in base
            is ExploreIntent.SwitchSuite -> switchSuite(intent.suite)
            is ExploreIntent.RefreshSuite -> {
                allSourceKinds = emptyList()
                allSourceRawKinds = emptyList()
                allSourceMode = ExploreMode.FLAT
                allSourceControls = emptyList()
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
            is ExploreIntent.ToggleLayoutMode -> {
                vm.toggleLayoutMode()
            }
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
        
        if (selectedSuite != null) {
            refreshSuite()
        }
    }

    /**
     * 根据当前选中的套件和已加载的书源列表，解析出书源名称。
     * 由于书源列表是异步加载的，该方法会在 init 和 observeExplore 成功后各调用一次。
     */
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

        if (source != null && state.enhance.dynamicSelectors.isEmpty()) {
            refreshSuite()
        }
    }

    private fun switchSuite(suite: DiscoverySuite) {
        DiscoverySuiteStore.setSelectedSuiteId(suite.id)
        vm.updateUiState {
            it.copy(enhance = it.enhance.copy(selectedSuite = suite))
        }
        resolveSelectedSourceName()
        refreshSuite()
    }

    private fun refreshSuite() {
        val suite = vm.uiState.value.enhance.selectedSuite ?: return
        val defaultSourceUrl = suite.defaultSourceUrl ?: vm.uiState.value.items.firstOrNull()?.bookSourceUrl ?: return

        vm.updateUiState {
            it.copy(
                enhance = it.enhance.copy(
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
                ModernExploreClassificationEngine.Result(allSourceRawKinds, ExploreMode.FLAT)
            }
            allSourceKinds = classification.kinds
            allSourceMode = classification.mode
            allSourceControls = ModernExploreControlExtractor.fromFlatKinds(allSourceRawKinds)

            rebuildSelectors(suite, defaultSourceUrl)
        }
    }

    private fun loadMoreWidgetData(widgetId: String) {
        val state = vm.uiState.value
        val enhance = state.enhance
        val isLoadingNow = enhance.widgetLoading[widgetId] == true
        val isEndNow = enhance.widgetIsEnd[widgetId] == true
        if (isLoadingNow || isEndNow) return

        val suite = enhance.selectedSuite ?: return
        val defaultSourceUrl = suite.defaultSourceUrl ?: state.items.firstOrNull()?.bookSourceUrl ?: return

        val currentUrl = enhance.selectedWidgetTargets["current_url"] ?: return
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
        val key = "${sourceUrl}_$widgetId"
        val updatedMap = config.lastSelectedTargets + (key to title)

        val newConfig = config.copy(lastSelectedTargets = updatedMap)
        DiscoverySuiteStore.save(newConfig)
    }

    private fun updateDiscoverySettings(transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig) {
        val config = DiscoverySuiteStore.load()
        val updated = transform(config)
        DiscoverySuiteStore.save(updated)
        vm.updateUiState { it.copy(enhance = it.enhance.copy(suites = updated.suites.toImmutableList())) }
        val current = vm.uiState.value.enhance.selectedSuite
        if (current != null) {
            val updatedCurrent = updated.suites.find { it.id == current.id }
            if (updatedCurrent != null) {
                vm.updateUiState { it.copy(enhance = it.enhance.copy(selectedSuite = updatedCurrent)) }
                refreshSuite()
            }
        }
    }

    private fun selectWidgetTarget(widgetId: String, target: DiscoverySuiteWidgetTarget) {
        val suite = vm.uiState.value.enhance.selectedSuite ?: return
        val defaultSourceUrl = suite.defaultSourceUrl
            ?: vm.uiState.value.items.firstOrNull()?.bookSourceUrl
            ?: return

        if (widgetId.startsWith(DYNAMIC_SELECT_PREFIX)) {
            selectControlTarget(widgetId, target, suite, defaultSourceUrl)
            return
        }
        if (!widgetId.startsWith(DYNAMIC_LEVEL_PREFIX)) return

        val level = widgetId.removePrefix(DYNAMIC_LEVEL_PREFIX).toIntOrNull() ?: return
        if (vm.uiState.value.enhance.selectedWidgetTargets[widgetId] == target.title) return

        saveSelection(widgetId, target.title)

        vm.updateUiState { state ->
            val newSelections = state.enhance.selectedWidgetTargets.toMutableMap()
            newSelections[widgetId] = target.title

            newSelections.keys
                .filter { key ->
                    key.startsWith(DYNAMIC_LEVEL_PREFIX) &&
                            (key.removePrefix(DYNAMIC_LEVEL_PREFIX).toIntOrNull() ?: -1) > level
                }
                .toList()
                .forEach(newSelections::remove)

            newSelections.remove("current_url")
            state.copy(enhance = state.enhance.copy(selectedWidgetTargets = newSelections.toImmutableMap()))
        }

        vm.viewModelScope.launch(IO) {
            rebuildSelectors(suite, defaultSourceUrl)
        }
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
        vm.updateUiState { state ->
            state.copy(
                enhance = state.enhance.copy(
                    selectedWidgetTargets = (state.enhance.selectedWidgetTargets + (widgetId to value)).toImmutableMap()
                )
            )
        }

        vm.viewModelScope.launch(IO) {
            try {
                val source = vm.exploreRepository.getBookSource(defaultSourceUrl) ?: return@launch
                val key = control.kind.title
                if (key.isNotBlank()) {
                    getExploreInfoMap(defaultSourceUrl).apply {
                        this[key] = value
                        saveNow()
                    }
                }
                source.clearExploreKindsCache()
                allSourceRawKinds = source.exploreKinds()
                val classification = ModernExploreClassificationEngine.classify(
                    allSourceRawKinds,
                    source.exploreKindsJson()
                )
                allSourceKinds = classification.kinds
                allSourceMode = classification.mode
                allSourceControls = ModernExploreControlExtractor.fromFlatKinds(allSourceRawKinds)
                rebuildSelectors(suite, defaultSourceUrl)
            } catch (_: Exception) {
            }
        }
    }

    private fun rebuildSelectors(suite: DiscoverySuite, defaultSourceUrl: String) {
        val selectors = mutableListOf<DynamicSelectorUi>()
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
            val stateSelected = vm.uiState.value.enhance.selectedWidgetTargets[widgetId]
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

        allSourceControls.sortedBy { it.sourceIndex }.forEach { control ->
            val widgetId = "$DYNAMIC_SELECT_PREFIX${control.sourceIndex}"
            val targets = control.options.map { value ->
                DiscoverySuiteWidgetTarget(
                    sourceUrl = defaultSourceUrl,
                    tagUrl = value,
                    title = value
                )
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
            state.copy(
                enhance = state.enhance.copy(
                    dynamicSelectors = orderedSelectors.toImmutableList(),
                    selectedWidgetTargets = (preserved + finalSelections).toImmutableMap()
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
        val optionTitles = selector.targets
            .asSequence()
            .map { cleanExploreTitle(it.title) }
            .filter { it.isNotBlank() }
            .toSet()
        if (optionTitles.isEmpty()) return Int.MAX_VALUE
        return allSourceRawKinds.indexOfFirst { kind ->
            cleanExploreTitle(kind.title) in optionTitles
        }.takeIf { it >= 0 } ?: Int.MAX_VALUE
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
            .replace(Regex("[\\[\\]【】?（）<>《》]"), "")
            .replace(Regex("[\\p{So}\\p{Sk}]+"), "")
            .replace(Regex("[༺༻ˇ»«`´ʚɞ]+"), "")
            .trim()
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
        vm.updateUiState { it.copy(enhance = it.enhance.copy(widgetLoading = (it.enhance.widgetLoading + (widgetId to true)).toImmutableMap())) }
        vm.viewModelScope.launch(IO) {
            try {
                val result = vm.exploreBooksUseCase.execute(
                    sourceUrl = sourceUrl,
                    moduleUrl = tagUrl,
                    args = null
                )
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
            } catch (e: Exception) {
                vm.updateUiState { it.copy(enhance = it.enhance.copy(widgetLoading = (it.enhance.widgetLoading + (widgetId to false)).toImmutableMap())) }
            }
        }
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
            val sourceUrl = suite.defaultSourceUrl
                ?: state.items.firstOrNull()?.bookSourceUrl
                ?: return@launch

            val source = try {
                vm.exploreRepository.getBookSource(sourceUrl)
            } catch (_: Exception) {
                null
            }

            val localMatches = filterLoadedSuiteBooks(state, suite, query)

            if (!source?.searchUrl.isNullOrBlank()) {
                vm.updateUiState {
                    it.copy(
                        enhance = it.enhance.copy(
                            suiteSearchBooks = localMatches.toImmutableList(),
                            suiteSearchLoading = true,
                            suiteSearchRemote = true,
                            suiteSearchPage = 1,
                            suiteSearchIsEnd = false
                        )
                    )
                }

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
                            val merged = (localMatches + result.books)
                                .distinctBy { book -> book.bookUrl }
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
            } else {
                if (vm.uiState.value.searchKey.trim() == query) {
                    vm.updateUiState {
                        it.copy(
                            enhance = it.enhance.copy(
                                suiteSearchBooks = localMatches.toImmutableList(),
                                suiteSearchLoading = false,
                                suiteSearchRemote = false,
                                suiteSearchPage = 1,
                                suiteSearchIsEnd = true
                            )
                        )
                    }
                }
            }
        }
    }

    private fun filterLoadedSuiteBooks(
        state: ExploreViewModel.ExploreUiState,
        suite: DiscoverySuite,
        query: String
    ): List<SearchBook> {
        val bookWidgetId = suite.widgets.firstOrNull { widget ->
            widget.type == DiscoverySuiteWidgetType.WaterfallBooks.type ||
                    widget.type == DiscoverySuiteWidgetType.BookList.type ||
                    widget.type == DiscoverySuiteWidgetType.HorizontalBooks.type
        }?.id

        val loadedBooks = bookWidgetId
            ?.let { state.enhance.widgetBooks[it] }
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

    private companion object {
        const val DYNAMIC_LEVEL_PREFIX = "dynamic_level_"
        const val DYNAMIC_SELECT_PREFIX = "dynamic_select_"
        val STATUS_SELECTOR_TITLES = setOf(
            "全部", "完结", "连载", "完本", "在更", "已完成", "连载中", "Finished", "Loading"
        )
        val RANK_SELECTOR_TITLES = setOf(
            "推荐", "评分", "热门", "周榜", "月榜", "总榜", "日榜", "本周", "本月", "本日"
        )
    }
}
