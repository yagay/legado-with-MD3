package io.legado.app.ui.main.explore

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.enhance.explore.model.DiscoverySuite
import io.legado.app.enhance.explore.model.DiscoverySuiteConfig
import io.legado.app.enhance.explore.model.DiscoverySuiteStore
import io.legado.app.enhance.explore.model.DiscoverySuiteWidgetTarget
import io.legado.app.enhance.explore.vm.EnhanceState
import io.legado.app.enhance.explore.vm.ExploreViewModelEnhance
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.getExploreInfoMap
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExploreViewModel(
    application: Application,
    internal val exploreRepository: ExploreRepository,
    internal val exploreKindUseCase: ExploreKindUiUseCase,
    internal val exploreBooksUseCase: ExploreBooksUseCase,
) : BaseViewModel(application) {

    private val initialLayoutMode = DiscoverySuiteStore.getLayoutMode()
    private val initialUiState = ExploreUiState(layoutMode = initialLayoutMode)
    private val _uiState = MutableStateFlow(initialUiState)
    val uiState: StateFlow<ExploreUiState> = _uiState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialUiState)
    private val _effects = MutableSharedFlow<ExploreEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var exploreJob: Job? = null
    private var kindsJob: Job? = null
    private var pendingDiscoverySuiteLoadAfterSources = initialLayoutMode == 1
    val enhance = ExploreViewModelEnhance(this)

    init {
        observeGroups()
        observeExplore()
    }

    internal fun updateUiState(transform: (ExploreUiState) -> ExploreUiState) {
        _uiState.update(transform)
    }

    internal fun emitEffect(effect: ExploreEffect) {
        _effects.tryEmit(effect)
    }

    fun onIntent(intent: ExploreIntent) {
        if (intent !is ExploreIntent.Search && enhance.onIntent(intent)) return
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
            else -> Unit
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

    internal fun toggleLayoutMode() {
        val newMode = if (_uiState.value.layoutMode == 0) 1 else 0
        DiscoverySuiteStore.setLayoutMode(newMode)
        enhance.clearSuiteSearchJob()
        _uiState.update {
            it.copy(
                layoutMode = newMode,
                searchKey = "",
                isSearch = false,
                enhance = it.enhance.copy(
                    suiteSearchBooks = null,
                    suiteSearchLoading = false,
                    suiteSearchRemote = false,
                    suiteSearchPage = 1,
                    suiteSearchIsEnd = true
                )
            )
        }
        observeExplore()
        if (newMode == 1) {
            if (_uiState.value.items.isEmpty()) {
                pendingDiscoverySuiteLoadAfterSources = true
            } else {
                pendingDiscoverySuiteLoadAfterSources = false
                enhance.loadDiscoverySuite()
            }
        } else {
            pendingDiscoverySuiteLoadAfterSources = false
        }
    }

    fun search(key: String) {
        val query = key.trim()
        _uiState.update { it.copy(searchKey = key, expandedId = null) }
        if (_uiState.value.layoutMode == 0) {
            observeExplore()
            return
        }
        enhance.searchSuiteBooks(query)
    }

    fun setGroup(group: String) {
        _uiState.update { it.copy(selectedGroup = group, expandedId = null) }
        observeExplore()
    }

    fun toggleSearchVisible(visible: Boolean) {
        _uiState.update { it.copy(isSearch = visible) }
        if (!visible) {
            enhance.clearSuiteSearchJob()
            search("")
        }
    }

    private fun observeExplore() {
        exploreJob?.cancel()
        exploreJob = viewModelScope.launch {
            val state = _uiState.value
            val query = if (state.layoutMode == 0) state.searchKey else ""
            val selectedGroup = state.selectedGroup

            exploreRepository.getExploreSources(query, selectedGroup)
                .flowOn(IO)
                .collectLatest { items ->
                    _uiState.update { it.copy(items = items.toImmutableList()) }
                    if (_uiState.value.layoutMode == 1) {
                        if (pendingDiscoverySuiteLoadAfterSources && items.isNotEmpty()) {
                            pendingDiscoverySuiteLoadAfterSources = false
                            enhance.loadDiscoverySuite()
                        } else {
                            enhance.resolveSelectedSourceName()
                        }
                    }
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
            } catch (_: Exception) {
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
        val enhance: EnhanceState = EnhanceState()
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
    data class PreviewDiscoverySettings(val transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig) : ExploreIntent
    data class SelectWidgetTarget(val widgetId: String, val target: DiscoverySuiteWidgetTarget) : ExploreIntent
    data class LoadMoreWidgetData(val widgetId: String) : ExploreIntent
    data object LoadMoreSuiteSearch : ExploreIntent
    data class SetSuiteSearchField(val field: String) : ExploreIntent
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
