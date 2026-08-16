from pathlib import Path

p = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
s = p.read_text()

start = s.index('    fun searchSuiteBooks(query: String) {')
end = s.index('    private fun filterLoadedSuiteBooks(', start)
old = s[start:end]
new = r'''    fun searchSuiteBooks(query: String) {
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

'''
s = s[:start] + new + s[end:]

# Embedded-search execution is no longer part of top-bar search. Keep the extractor result only
# for hiding source-native search controls; remove obsolete execution state/helpers from this VM.
s = s.replace('    private var lastEmbeddedSearchQuery: String? = null\n', '')
s = s.replace('                lastEmbeddedSearchQuery = null\n', '')
s = s.replace('        lastEmbeddedSearchQuery = null\n', '')

helper_start = s.find('    private suspend fun executeEmbeddedSearch(')
if helper_start != -1:
    helper_end = s.index('    private fun loadMoreWidgetData(', helper_start)
    s = s[:helper_start] + s[helper_end:]

p.write_text(s)
