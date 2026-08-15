from pathlib import Path
import re

# 1) ExploreScreen: opaque sticky search + trailing scope selector.
p = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt')
s = p.read_text()
if 'import androidx.compose.foundation.background\n' not in s:
    s = s.replace('import androidx.compose.foundation.combinedClickable\n', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.combinedClickable\n', 1)
if 'import androidx.compose.material.icons.filled.FilterAlt\n' not in s:
    s = s.replace('import androidx.compose.material.icons.filled.Group\n', 'import androidx.compose.material.icons.filled.Group\nimport androidx.compose.material.icons.filled.FilterAlt\n', 1)

old_sticky = '''                    stickyHeader(key = "source_menu_search") {
                        OutlinedTextField(
                            value = sourceMenuQuery,
                            onValueChange = { sourceMenuQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),'''
new_sticky = '''                    stickyHeader(key = "source_menu_search") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            OutlinedTextField(
                                value = sourceMenuQuery,
                                onValueChange = { sourceMenuQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),'''
if old_sticky in s:
    s = s.replace(old_sticky, new_sticky, 1)
    s = s.replace('''                            singleLine = true
                        )
                    }
                    items(''', '''                                singleLine = true
                            )
                        }
                    }
                    items(''', 1)
elif '.background(MaterialTheme.colorScheme.surface)' not in s:
    raise SystemExit('sticky search block not found')

old_search = '''        onSearchQueryChange = { onIntent(ExploreIntent.Search(it)) },
        onSearchToggle = { onIntent(ExploreIntent.ToggleSearch(it)) },
        searchPlaceholder = stringResource(R.string.search),'''
new_search = '''        onSearchQueryChange = { onIntent(ExploreIntent.Search(it)) },
        onSearchToggle = { onIntent(ExploreIntent.ToggleSearch(it)) },
        searchTrailingIcon = if (state.layoutMode == 1) {
            {
                var showSearchFieldMenu by remember { mutableStateOf(false) }
                Box {
                    TopBarActionButton(
                        onClick = { showSearchFieldMenu = true },
                        imageVector = Icons.Default.FilterAlt,
                        contentDescription = when (state.enhance.suiteSearchField) {
                            "author" -> "搜索作者"
                            "kind" -> "搜索分类"
                            else -> "搜索书籍名"
                        }
                    )
                    RoundDropdownMenu(
                        expanded = showSearchFieldMenu,
                        onDismissRequest = { showSearchFieldMenu = false }
                    ) {
                        listOf(
                            "name" to "书籍名",
                            "author" to "作者",
                            "kind" to "分类"
                        ).forEach { (field, label) ->
                            RoundDropdownMenuItem(
                                text = label,
                                isSelected = state.enhance.suiteSearchField == field,
                                onClick = {
                                    onIntent(ExploreIntent.SetSuiteSearchField(field))
                                    showSearchFieldMenu = false
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
if old_search in s:
    s = s.replace(old_search, new_search, 1)
elif 'SetSuiteSearchField' not in s:
    raise SystemExit('top search block not found')
p.write_text(s)

# 2) ExploreIntent.
p = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreViewModel.kt')
s = p.read_text()
if 'data class SetSuiteSearchField' not in s:
    needle = '    data object LoadMoreSuiteSearch : ExploreIntent\n'
    if needle not in s:
        raise SystemExit('ExploreIntent insertion point not found')
    s = s.replace(needle, needle + '    data class SetSuiteSearchField(val field: String) : ExploreIntent\n', 1)
p.write_text(s)

# 3) Enhance VM: state, intent, scoped search.
p = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
s = p.read_text()
if 'val suiteSearchField: String = "name"' not in s:
    s = s.replace(
        '    val suiteSearchRemote: Boolean = false,\n    val suiteSearchPage: Int = 1,',
        '    val suiteSearchRemote: Boolean = false,\n    /** name(default) / author / kind */\n    val suiteSearchField: String = "name",\n    val suiteSearchPage: Int = 1,',
        1
    )
if 'is ExploreIntent.SetSuiteSearchField' not in s:
    needle = '            is ExploreIntent.LoadMoreSuiteSearch -> loadMoreSuiteSearch()\n'
    if needle not in s:
        raise SystemExit('enhance intent insertion point not found')
    s = s.replace(needle, needle + '            is ExploreIntent.SetSuiteSearchField -> setSuiteSearchField(intent.field)\n', 1)

if 'private fun setSuiteSearchField(' not in s:
    marker = '    fun searchSuiteBooks(query: String) {\n'
    method = '''    private fun setSuiteSearchField(field: String) {
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

'''
    if marker not in s:
        raise SystemExit('searchSuiteBooks marker not found')
    s = s.replace(marker, method + marker, 1)

s = s.replace(
    '            val localMatches = filterLoadedSuiteBooks(state, suite, query)\n            if (!source?.searchUrl.isNullOrBlank()) {',
    '            val field = state.enhance.suiteSearchField\n            val localMatches = filterLoadedSuiteBooks(state, suite, query, field)\n            if (field == "name" && !source?.searchUrl.isNullOrBlank()) {',
    1
)

# Replace filter signature + body using regex, independent of indentation.
pattern = re.compile(
    r'''    private fun filterLoadedSuiteBooks\(\n        state: ExploreViewModel\.ExploreUiState,\n        suite: DiscoverySuite,\n        query: String\n    \): List<SearchBook> \{\n(?P<body>.*?)\n    \}\n\n    private fun loadMoreSuiteSearch''',
    re.S
)
m = pattern.search(s)
if not m:
    raise SystemExit('filterLoadedSuiteBooks function not found')
new_func = '''    private fun filterLoadedSuiteBooks(
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

    private fun loadMoreSuiteSearch'''
s = s[:m.start()] + new_func + s[m.end():]
p.write_text(s)
