from pathlib import Path

path = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
text = path.read_text()


def replace_once(old, new):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'expected exactly one match, found {count}: {old[:120]!r}')
    text = text.replace(old, new, 1)

replace_once(
    'import io.legado.app.enhance.explore.builder.ModernExploreControlExtractor.SelectControl\n',
    'import io.legado.app.enhance.explore.builder.ModernExploreControlExtractor.SearchControl\n'
    'import io.legado.app.enhance.explore.builder.ModernExploreControlExtractor.SelectControl\n'
)
replace_once(
    'import kotlinx.coroutines.launch\n',
    'import kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\n'
)
replace_once(
    '    private var allSourceControls: List<SelectControl> = emptyList()\n'
    '    private var suiteSearchJob: Job? = null\n',
    '    private var allSourceControls: List<SelectControl> = emptyList()\n'
    '    private var suiteSearchControl: SearchControl? = null\n'
    '    private var lastEmbeddedSearchQuery: String? = null\n'
    '    private var suiteSearchJob: Job? = null\n'
)
replace_once(
    '                allSourceMode = ExploreMode.FLAT\n'
    '                allSourceControls = emptyList()\n'
    '                refreshSuite()\n',
    '                allSourceMode = ExploreMode.FLAT\n'
    '                allSourceControls = emptyList()\n'
    '                suiteSearchControl = null\n'
    '                lastEmbeddedSearchQuery = null\n'
    '                refreshSuite()\n'
)
replace_once(
    '        widgetRequestVersion++\n'
    '        vm.updateUiState {\n',
    '        widgetRequestVersion++\n'
    '        suiteSearchControl = null\n'
    '        lastEmbeddedSearchQuery = null\n'
    '        vm.updateUiState {\n'
)
old_native = '''            val nativeControls = allSourceRawKinds.filter { kind ->
                kind.type == ExploreKind.Type.text ||
                    kind.type == ExploreKind.Type.button ||
                    kind.type == ExploreKind.Type.toggle
            }
            vm.updateUiState { state ->
                state.copy(enhance = state.enhance.copy(dynamicControls = nativeControls.toImmutableList()))
            }
            rebuildSelectors(suite, defaultSourceUrl)
'''
replace_once(old_native, '''            refreshNativeControls()
            rebuildSelectors(suite, defaultSourceUrl)
''')

# The same source-kind reload path after a select action must also rediscover
# an embedded search pair, because source controls can be generated dynamically.
old_select_reload = '''                allSourceControls = if (classification.mode == ExploreMode.TREE) {
                ModernExploreControlExtractor.fromTreeRoot(classification.nodes)
            } else {
                ModernExploreControlExtractor.fromFlatKinds(allSourceRawKinds)
            }
                rebuildSelectors(suite, defaultSourceUrl)
'''
replace_once(old_select_reload, '''                allSourceControls = if (classification.mode == ExploreMode.TREE) {
                    ModernExploreControlExtractor.fromTreeRoot(classification.nodes)
                } else {
                    ModernExploreControlExtractor.fromFlatKinds(allSourceRawKinds)
                }
                refreshNativeControls()
                rebuildSelectors(suite, defaultSourceUrl)
''')

marker = '''    private fun loadMoreWidgetData(widgetId: String) {
'''
helper = '''    private fun refreshNativeControls() {
        suiteSearchControl = ModernExploreControlExtractor.findSearchControl(allSourceRawKinds)
        val hiddenIndexes = suiteSearchControl?.hiddenSourceIndexes.orEmpty()
        val nativeControls = allSourceRawKinds.mapIndexedNotNull { index, kind ->
            if (index in hiddenIndexes) return@mapIndexedNotNull null
            kind.takeIf {
                it.type == ExploreKind.Type.text ||
                    it.type == ExploreKind.Type.button ||
                    it.type == ExploreKind.Type.toggle
            }
        }
        vm.updateUiState { state ->
            state.copy(enhance = state.enhance.copy(dynamicControls = nativeControls.toImmutableList()))
        }
    }

    private suspend fun executeEmbeddedSearch(
        query: String,
        control: SearchControl,
        suite: DiscoverySuite,
        sourceUrl: String,
    ): Boolean = withContext(IO) {
        try {
            val source = vm.exploreRepository.getBookSource(sourceUrl) ?: return@withContext false
            val infoMap = getExploreInfoMap(sourceUrl)
            infoMap[control.textKind.title] = query
            infoMap.saveNow()

            // Match the original ExploreKind text behavior first: changing a text field can
            // have its own debounced action. Then invoke the paired button action.
            if (!control.textKind.action.isNullOrBlank()) {
                vm.exploreKindUseCase.executeAction(
                    action = control.textKind.action,
                    title = control.textKind.title,
                    sourceUrl = sourceUrl,
                    infoMap = infoMap,
                    activity = null,
                    onRefreshKinds = {}
                )
            }
            vm.exploreKindUseCase.executeAction(
                action = control.buttonKind.action,
                title = control.buttonKind.title,
                sourceUrl = sourceUrl,
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
            widgetRequestVersion++
            vm.updateUiState { state ->
                state.copy(
                    enhance = state.enhance.copy(
                        widgetBooks = persistentMapOf(),
                        widgetLoading = persistentMapOf(),
                        widgetPages = persistentMapOf(),
                        widgetIsEnd = persistentMapOf(),
                        suiteSearchBooks = null,
                        suiteSearchLoading = false,
                        suiteSearchRemote = false,
                        suiteSearchPage = 1,
                        suiteSearchIsEnd = true,
                    )
                )
            }
            rebuildSelectors(suite, sourceUrl)
            true
        } catch (_: Exception) {
            false
        }
    }

'''
replace_once(marker, helper + marker)

# Replace the full search function with a version that gives a structurally
# confirmed embedded text+button pair priority over standard searchUrl.
start = text.index('    fun searchSuiteBooks(query: String) {')
end = text.index('    private fun filterLoadedSuiteBooks(', start)
new_search = '''    fun searchSuiteBooks(query: String) {
        suiteSearchJob?.cancel()
        if (query.isBlank()) {
            val control = suiteSearchControl
            val shouldResetEmbedded = lastEmbeddedSearchQuery != null && control != null
            lastEmbeddedSearchQuery = null
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
            if (shouldResetEmbedded) {
                val state = vm.uiState.value
                val suite = state.enhance.selectedSuite
                val sourceUrl = suite?.defaultSourceUrl ?: state.items.firstOrNull()?.bookSourceUrl
                if (suite != null && sourceUrl != null) {
                    suiteSearchJob = vm.viewModelScope.launch {
                        executeEmbeddedSearch("", control!!, suite, sourceUrl)
                    }
                }
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

            // A structurally confirmed source-native text + button search owns the name-search
            // path. We feed the top-bar value into the original InfoMap key and execute the
            // original actions, so the source's own explore URL/category/paging logic remains
            // authoritative. No title/locale keyword guessing is used.
            val embeddedControl = suiteSearchControl
            if (field == "name" && embeddedControl != null) {
                vm.updateUiState {
                    it.copy(
                        enhance = it.enhance.copy(
                            suiteSearchBooks = null,
                            suiteSearchLoading = true,
                            suiteSearchRemote = false,
                            suiteSearchPage = 1,
                            suiteSearchIsEnd = true
                        )
                    )
                }
                if (executeEmbeddedSearch(query, embeddedControl, suite, sourceUrl)) {
                    lastEmbeddedSearchQuery = query
                    return@launch
                }
                // If a source action fails at runtime, keep the existing standard/local
                // search fallback instead of leaving the page in a broken search state.
                vm.updateUiState {
                    it.copy(enhance = it.enhance.copy(suiteSearchLoading = false))
                }
            }

            if (field == "name" && !source?.searchUrl.isNullOrBlank()) {
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
            } else if (vm.uiState.value.searchKey.trim() == query) {
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

'''
text = text[:start] + new_search + text[end:]

path.write_text(text)
