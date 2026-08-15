from pathlib import Path

p = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt')
s = p.read_text()
if 'import androidx.compose.foundation.background' not in s:
    s = s.replace('import androidx.compose.foundation.layout.Box\n', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.layout.Box\n')
if 'import androidx.compose.material.icons.filled.FilterAlt' not in s:
    s = s.replace('import androidx.compose.material.icons.filled.Group\n', 'import androidx.compose.material.icons.filled.Group\nimport androidx.compose.material.icons.filled.FilterAlt\n')
s = s.replace('.fillMaxWidth()\n                                .padding(horizontal = 12.dp, vertical = 6.dp),', '.fillMaxWidth()\n                                .background(MaterialTheme.colorScheme.surface)\n                                .padding(horizontal = 12.dp, vertical = 6.dp),', 1)
old = '''        onSearchQueryChange = { onIntent(ExploreIntent.Search(it)) },
        onSearchToggle = { onIntent(ExploreIntent.ToggleSearch(it)) },
        searchPlaceholder = stringResource(R.string.search),'''
new = '''        onSearchQueryChange = { onIntent(ExploreIntent.Search(it)) },
        onSearchToggle = { onIntent(ExploreIntent.ToggleSearch(it)) },
        searchTrailingIcon = if (state.layoutMode == 1) {
            {
                var showSearchScopeMenu by remember { mutableStateOf(false) }
                Box {
                    TopBarActionButton(
                        onClick = { showSearchScopeMenu = true },
                        imageVector = Icons.Default.FilterAlt,
                        contentDescription = when (state.enhance.suiteSearchField) {
                            "author" -> "搜索作者"
                            "kind" -> "搜索分类"
                            else -> "搜索书籍名"
                        }
                    )
                    RoundDropdownMenu(
                        expanded = showSearchScopeMenu,
                        onDismissRequest = { showSearchScopeMenu = false }
                    ) {
                        listOf("name" to "书籍名", "author" to "作者", "kind" to "分类").forEach { (field, label) ->
                            RoundDropdownMenuItem(
                                text = label,
                                isSelected = state.enhance.suiteSearchField == field,
                                onClick = {
                                    onIntent(ExploreIntent.SetSuiteSearchField(field))
                                    showSearchScopeMenu = false
                                }
                            )
                        }
                    }
                }
            }
        } else null,
        searchPlaceholder = if (state.layoutMode == 1) {
            when (state.enhance.suiteSearchField) {
                "author" -> "搜索作者"
                "kind" -> "搜索分类"
                else -> "搜索书籍名"
            }
        } else stringResource(R.string.search),'''
if old not in s:
    raise SystemExit('ExploreScreen search block missing')
s = s.replace(old, new, 1)
p.write_text(s)

p = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreViewModel.kt')
s = p.read_text()
if 'SetSuiteSearchField' not in s:
    needle = '    data object LoadMoreSuiteSearch : ExploreIntent\n'
    if needle not in s:
        raise SystemExit('ExploreIntent insertion point missing')
    s = s.replace(needle, needle + '    data class SetSuiteSearchField(val field: String) : ExploreIntent\n', 1)
p.write_text(s)

p = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
s = p.read_text()
if 'val suiteSearchField: String = "name"' not in s:
    s = s.replace('    val suiteSearchRemote: Boolean = false,\n    val suiteSearchPage: Int = 1,', '    val suiteSearchRemote: Boolean = false,\n    val suiteSearchField: String = "name",\n    val suiteSearchPage: Int = 1,', 1)
if 'is ExploreIntent.SetSuiteSearchField' not in s:
    s = s.replace('            is ExploreIntent.LoadMoreSuiteSearch -> loadMoreSuiteSearch()\n', '            is ExploreIntent.LoadMoreSuiteSearch -> loadMoreSuiteSearch()\n            is ExploreIntent.SetSuiteSearchField -> setSuiteSearchField(intent.field)\n', 1)
if 'private fun setSuiteSearchField(' not in s:
    marker = '    fun searchSuiteBooks(query: String) {\n'
    method = '''    private fun setSuiteSearchField(field: String) {
        val normalized = field.takeIf { it == "name" || it == "author" || it == "kind" } ?: "name"
        if (vm.uiState.value.enhance.suiteSearchField == normalized) return
        vm.updateUiState { state ->
            state.copy(enhance = state.enhance.copy(
                suiteSearchField = normalized,
                suiteSearchBooks = null,
                suiteSearchLoading = false,
                suiteSearchRemote = false,
                suiteSearchPage = 1,
                suiteSearchIsEnd = true
            ))
        }
        val query = vm.uiState.value.searchKey.trim()
        if (query.isNotBlank()) searchSuiteBooks(query)
    }

'''
    if marker not in s:
        raise SystemExit('searchSuiteBooks marker missing')
    s = s.replace(marker, method + marker, 1)
s = s.replace('            val localMatches = filterLoadedSuiteBooks(state, suite, query)\n\n            if (!source?.searchUrl.isNullOrBlank()) {', '            val field = state.enhance.suiteSearchField\n            val localMatches = filterLoadedSuiteBooks(state, suite, query, field)\n\n            if (field == "name" && !source?.searchUrl.isNullOrBlank()) {', 1)
s = s.replace('        query: String\n    ): List<SearchBook> {', '        query: String,\n        field: String = state.enhance.suiteSearchField\n    ): List<SearchBook> {', 1)
oldbody = '''        val q = query.lowercase()
        return loadedBooks.filter { book ->
            book.name.contains(query, ignoreCase = true) ||
                    book.author.contains(query, ignoreCase = true) ||
                    book.kind.orEmpty().contains(query, ignoreCase = true) ||
                    book.intro.orEmpty().contains(query, ignoreCase = true) ||
                    book.latestChapterTitle.orEmpty().contains(query, ignoreCase = true) ||
                    book.wordCount.orEmpty().lowercase().contains(q)
        }
'''
newbody = '''        return loadedBooks.filter { book ->
            when (field) {
                "author" -> book.author.contains(query, ignoreCase = true)
                "kind" -> book.kind.orEmpty().contains(query, ignoreCase = true)
                else -> book.name.contains(query, ignoreCase = true)
            }
        }
'''
if oldbody not in s:
    raise SystemExit('old filter body missing')
s = s.replace(oldbody, newbody, 1)
p.write_text(s)

out = Path('.tmp/ExploreViewModelEnhance.kt')
out.parent.mkdir(exist_ok=True)
out.write_text(p.read_text())
