package io.legado.app.ui.book.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.domain.usecase.SaveSearchBooksUseCase
import io.legado.app.domain.gateway.CoverSettingsGateway
import android.content.res.Configuration
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.utils.stackTraceStr
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.init.appCtx

private data class ExploreShowLoadState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isEnd: Boolean = false,
    val errorMsg: String? = null,
)

private data class ExploreShowKindState(
    val kinds: List<ExploreKind> = emptyList(),
    val selectedKindTitle: String? = null,
)

private data class ExploreShowDisplayState(
    val sourceUrl: String? = null,
    val layoutState: Int,
    val gridCount: Int,
    val sheet: ExploreShowSheet = ExploreShowSheet.None,
)

class ExploreShowViewModel(
    private val repository: ExploreRepository,
    private val resolveBookShelfStateUseCase: ResolveBookShelfStateUseCase,
    private val exploreBooksUseCase: ExploreBooksUseCase,
    private val saveSearchBooksUseCase: SaveSearchBooksUseCase,
    private val addToBookshelfUseCase: AddToBookshelfUseCase,
    private val localPreferencesRepository: SettingsRepository,
    private val coverSettingsGateway: CoverSettingsGateway,
) : ViewModel() {

    private val _rawBooks = MutableStateFlow<List<SearchBook>>(emptyList())
    private val _bookshelf = MutableStateFlow<Set<BookShelfKey>>(emptySet())
    private val _loadState = MutableStateFlow(ExploreShowLoadState())
    private val _kindState = MutableStateFlow(ExploreShowKindState())
    private val _displayState = MutableStateFlow(
        ExploreShowDisplayState(
            layoutState = 0,
            gridCount = if (appCtx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 7 else 3,
        )
    )

    private var sourceUrl: String? = null
    private var exploreUrl: String? = null
    private var initialExploreUrl: String? = null
    private var initialized = false
    private var page = 1
    private var autoPageCount = 0

    companion object {
        private const val MAX_AUTO_PAGES = 3
        private const val AUTO_PAGE_DELAY_MS = 500L
    }

    private val _uiState = MutableStateFlow(
        ExploreShowUiState(
            layoutState = _displayState.value.layoutState,
            gridCount = _displayState.value.gridCount,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ExploreShowEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        observeBookshelf()
        combineUiState()
        loadLayoutMode()
        loadGridCount()
    }

    fun onIntent(intent: ExploreShowIntent) {
        when (intent) {
            is ExploreShowIntent.InitData -> initData(intent.sourceUrl, intent.exploreUrl)
            ExploreShowIntent.LoadMore -> loadMore()
            ExploreShowIntent.ForceLoadNext -> loadMore(forceLoad = true)
            ExploreShowIntent.Refresh -> loadMore(isRefresh = true)
            is ExploreShowIntent.SwitchKind -> switchKind(intent.kind)
            ExploreShowIntent.ToggleLayout -> toggleLayout()
            is ExploreShowIntent.SaveGridCount -> saveGridCount(intent.count)
            is ExploreShowIntent.ShowSheet -> _displayState.update { it.copy(sheet = intent.sheet) }
            ExploreShowIntent.DismissSheet -> _displayState.update { it.copy(sheet = ExploreShowSheet.None) }
            is ExploreShowIntent.OpenBook -> emitEffect(
                ExploreShowEffect.OpenBookInfo(
                    name = intent.book.name,
                    author = intent.book.author,
                    bookUrl = intent.book.bookUrl,
                    origin = intent.book.origin,
                    coverPath = intent.book.coverUrl,
                    sharedCoverKey = intent.sharedCoverKey,
                )
            )

            is ExploreShowIntent.AddToShelf -> viewModelScope.launch {
                addToBookshelfUseCase.execute(intent.book)
            }
        }
    }

    private fun observeBookshelf() {
        viewModelScope.launch {
            repository.getBookshelfItems().collect { list ->
                _bookshelf.value = list.map {
                    BookShelfKey(it.name, it.author, it.bookUrl)
                }.toSet()
            }
        }
    }

    private fun combineUiState() {
        viewModelScope.launch {
            val displayAndCoverSettings = combine(
                _displayState,
                coverSettingsGateway.settings,
            ) { displayState, coverSettings -> displayState to coverSettings }
            combine(
                _rawBooks,
                _bookshelf,
                _loadState,
                _kindState,
                displayAndCoverSettings,
            ) { rawBooks, bookshelf, loadState, kindState, displayAndCover ->
                val (displayState, coverSettings) = displayAndCover
                val books = rawBooks.map { item ->
                    ExploreBookItemUi(
                        book = item,
                        shelfState = resolveBookShelfStateUseCase.execute(
                            name = item.name,
                            author = item.author,
                            url = item.bookUrl,
                            shelf = bookshelf,
                        )
                    )
                }

                ExploreShowUiState(
                    sourceUrl = displayState.sourceUrl,
                    books = books.toImmutableList(),
                    kinds = kindState.kinds.toImmutableList(),
                    selectedKindTitle = kindState.selectedKindTitle,
                    layoutState = displayState.layoutState,
                    gridCount = displayState.gridCount,
                    isLoading = loadState.isLoading,
                    isRefreshing = loadState.isRefreshing,
                    isEnd = loadState.isEnd,
                    errorMsg = loadState.errorMsg,
                    sheet = displayState.sheet,
                    filterStateId = coverSettings.exploreFilterState,
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun loadMore(isRefresh: Boolean = false, forceLoad: Boolean = false) {
        if (_loadState.value.isLoading) return
        if (_loadState.value.isEnd && !isRefresh && !forceLoad) return

        val currentSourceUrl = sourceUrl ?: return
        viewModelScope.launch {
            if (isRefresh) {
                page = 1
                autoPageCount = 0
                _rawBooks.value = emptyList()
                _loadState.update { it.copy(isEnd = false, errorMsg = null) }
            }

            _loadState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = isRefresh,
                    errorMsg = null,
                )
            }

            runCatching {
                exploreBooksUseCase.execute(
                    sourceUrl = currentSourceUrl,
                    moduleUrl = exploreUrl,
                    args = null,
                    page = page,
                )
            }.onSuccess { result ->
                if (result.books.isEmpty()) {
                    _loadState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isEnd = true,
                        )
                    }
                    return@onSuccess
                }

                saveSearchBooksUseCase.execute(result.books)
                val oldBooks = _rawBooks.value
                val merged = if (page == 1) {
                    result.books
                } else {
                    (oldBooks + result.books).distinctBy { it.bookUrl }
                }
                _rawBooks.value = merged
                page += 1
                _loadState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isEnd = result.books.isEmpty() || merged.size == oldBooks.size,
                    )
                }

                if (result.shouldAutoLoadNext && !_loadState.value.isEnd && autoPageCount < MAX_AUTO_PAGES) {
                    autoPageCount += 1
                    delay(AUTO_PAGE_DELAY_MS)
                    loadMore()
                }
            }.onFailure { error ->
                _loadState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMsg = error.stackTraceStr,
                    )
                }
            }
        }
    }

    private fun initData(incomingSourceUrl: String, incomingExploreUrl: String?) {
        if (initialized && sourceUrl == incomingSourceUrl && initialExploreUrl == incomingExploreUrl) {
            return
        }
        initialized = true
        sourceUrl = incomingSourceUrl
        initialExploreUrl = incomingExploreUrl
        exploreUrl = incomingExploreUrl
        page = 1
        autoPageCount = 0
        _rawBooks.value = emptyList()
        _loadState.value = ExploreShowLoadState()
        _kindState.value = ExploreShowKindState()
        _displayState.update {
            it.copy(
                sourceUrl = incomingSourceUrl,
                sheet = ExploreShowSheet.None,
            )
        }

        if (incomingExploreUrl == null) {
            viewModelScope.launch {
                loadKinds(incomingSourceUrl)
            }
        }

        loadMore(isRefresh = true)
    }

    private suspend fun loadKinds(sourceUrl: String) {
        _kindState.update { it.copy(kinds = repository.getSourceExploreKinds(sourceUrl)) }
    }

    private fun switchKind(kind: ExploreKind) {
        _kindState.update { it.copy(selectedKindTitle = kind.title) }
        exploreUrl = kind.url
        _loadState.update { it.copy(isEnd = false) }
        autoPageCount = 0
        loadMore(isRefresh = true)
    }

    private fun toggleLayout() {
        _displayState.update {
            val layoutState = if (it.layoutState == 0) 1 else 0
            viewModelScope.launch {
                localPreferencesRepository.updatePreference(LocalPreferencesKeys.EXPLORE_LAYOUT_MODE, layoutState)
            }
            it.copy(layoutState = layoutState)
        }
    }

    private fun loadLayoutMode() {
        viewModelScope.launch {
            val mode = localPreferencesRepository.getPreference(LocalPreferencesKeys.EXPLORE_LAYOUT_MODE, 0).first()
            _displayState.update { it.copy(layoutState = mode) }
        }
    }

    private fun loadGridCount() {
        viewModelScope.launch {
            val defaultCount = if (appCtx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 7 else 3
            val count = localPreferencesRepository.getPreference(LocalPreferencesKeys.EXPLORE_GRID_COUNT, defaultCount).first()
            _displayState.update { it.copy(gridCount = count) }
        }
    }

    private fun toggleLayoutState(current: Int): Int = if (current == 0) 1 else 0

    private fun emitEffect(effect: ExploreShowEffect) {
        _effects.tryEmit(effect)
    }
}
