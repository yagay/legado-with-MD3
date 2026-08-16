from pathlib import Path

p = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
s = p.read_text()

# Match legado:leg action runtime instead of evaluating select actions through AnalyzeRule/result.
s = s.replace(
    'import androidx.lifecycle.viewModelScope\n',
    'import androidx.lifecycle.viewModelScope\nimport com.script.rhino.runScriptWithContext\n',
    1,
)
s = s.replace('import io.legado.app.model.analyzeRule.AnalyzeRule\n', '', 1)
s = s.replace('import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext\n', '', 1)
s = s.replace(
    'import io.legado.app.ui.main.explore.ExploreViewModel.DynamicSelectorUi\n',
    'import io.legado.app.ui.main.explore.ExploreViewModel.DynamicSelectorUi\nimport io.legado.app.ui.login.SourceLoginJsExtensions\n',
    1,
)

old = '    private var suiteSearchJob: Job? = null\n'
new = '''    private var suiteSearchJob: Job? = null
    /** Invalidates stale waterfall loads whenever a dynamic control/source changes. */
    private var widgetRequestVersion: Long = 0L
'''
if old not in s:
    raise SystemExit('suiteSearchJob anchor not found')
s = s.replace(old, new, 1)

old = '''    private fun refreshSuite() {
        val suite = vm.uiState.value.enhance.selectedSuite ?: return
        val defaultSourceUrl = suite.defaultSourceUrl ?: vm.uiState.value.items.firstOrNull()?.bookSourceUrl ?: return
        vm.updateUiState {
'''
new = '''    private fun refreshSuite() {
        val suite = vm.uiState.value.enhance.selectedSuite ?: return
        val defaultSourceUrl = suite.defaultSourceUrl ?: vm.uiState.value.items.firstOrNull()?.bookSourceUrl ?: return
        widgetRequestVersion++
        vm.updateUiState {
'''
if old not in s:
    raise SystemExit('refreshSuite anchor not found')
s = s.replace(old, new, 1)

old = '''        saveSelection(widgetId, value)
        vm.updateUiState { state ->
            state.copy(
                enhance = state.enhance.copy(
                    selectedWidgetTargets = (state.enhance.selectedWidgetTargets + (widgetId to value)).toImmutableMap()
                )
            )
        }
'''
new = '''        saveSelection(widgetId, value)
        // Dynamic selects may rebuild the whole discovery page. Invalidate the previous
        // platform/category request immediately so a slow old response cannot overwrite
        // the newly selected platform.
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
'''
if old not in s:
    raise SystemExit('select state block not found')
s = s.replace(old, new, 1)

old = '''                control.kind.action
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { action ->
                        val actionJs = when {
                            action.startsWith("<js>") && action.endsWith("</js>") ->
                                action.removePrefix("<js>").removeSuffix("</js>")
                            action.startsWith("{{") && action.endsWith("}}") ->
                                action.removePrefix("{{").removeSuffix("}}")
                            else -> action
                        }
                        AnalyzeRule(source = source, preUpdateJs = true)
                            .setContent(actionJs, source.getKey())
                            .setCoroutineContext(coroutineContext)
                            .evalJS("var infoMap = result;\\n$actionJs", infoMap)
                    }
'''
new = '''                control.kind.action
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { action ->
                        val actionJs = when {
                            action.startsWith("<js>") && action.endsWith("</js>") ->
                                action.removePrefix("<js>").removeSuffix("</js>")
                            action.startsWith("{{") && action.endsWith("}}") ->
                                action.removePrefix("{{").removeSuffix("}}")
                            else -> action
                        }
                        // Match legado:leg applyDiscoverSelectValue(): execute in the
                        // BookSource JS scope so jsLib helpers (setVariable/BaseUrl/etc.)
                        // and injected infoMap/java are the same ones used by discovery.
                        runScriptWithContext {
                            source.evalJS(actionJs) {
                                put("java", SourceLoginJsExtensions(null, source))
                                put("infoMap", infoMap)
                            }
                        }
                    }
'''
if old not in s:
    raise SystemExit('action analyzer block not found')
s = s.replace(old, new, 1)

old = '''    private fun loadWidgetDataWithUrl(widgetId: String, sourceUrl: String, tagUrl: String) {
        if (tagUrl.isEmpty()) return
        vm.updateUiState { it.copy(enhance = it.enhance.copy(widgetLoading = (it.enhance.widgetLoading + (widgetId to true)).toImmutableMap())) }
        vm.viewModelScope.launch(IO) {
            try {
                val result = vm.exploreBooksUseCase.execute(sourceUrl = sourceUrl, moduleUrl = tagUrl, args = null)
                val finalBooks = result.books.distinctBy { it.bookUrl }
                vm.updateUiState {
'''
new = '''    private fun loadWidgetDataWithUrl(widgetId: String, sourceUrl: String, tagUrl: String) {
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
'''
if old not in s:
    raise SystemExit('loadWidgetData block not found')
s = s.replace(old, new, 1)

old = '''            } catch (_: Exception) {
                vm.updateUiState { it.copy(enhance = it.enhance.copy(widgetLoading = (it.enhance.widgetLoading + (widgetId to false)).toImmutableMap())) }
            }
        }
    }

    private fun setSuiteSearchField'''
new = '''            } catch (_: Exception) {
                if (requestVersion == widgetRequestVersion &&
                    vm.uiState.value.enhance.selectedWidgetTargets["current_url"] == tagUrl
                ) {
                    vm.updateUiState { it.copy(enhance = it.enhance.copy(widgetLoading = (it.enhance.widgetLoading + (widgetId to false)).toImmutableMap())) }
                }
            }
        }
    }

    private fun setSuiteSearchField'''
if old not in s:
    raise SystemExit('loadWidget catch block not found')
s = s.replace(old, new, 1)

# Guard pagination results as well.
old = '''        val currentUrl = enhance.selectedWidgetTargets["current_url"] ?: return
        val nextPage = (enhance.widgetPages[widgetId] ?: 1) + 1
'''
new = '''        val currentUrl = enhance.selectedWidgetTargets["current_url"] ?: return
        val requestVersion = widgetRequestVersion
        val nextPage = (enhance.widgetPages[widgetId] ?: 1) + 1
'''
if old not in s:
    raise SystemExit('loadMore request anchor not found')
s = s.replace(old, new, 1)

old = '''                val result = vm.exploreBooksUseCase.execute(
                    sourceUrl = defaultSourceUrl,
                    moduleUrl = currentUrl,
                    args = null,
                    page = nextPage
                )
                if (result.books.isEmpty()) {
'''
new = '''                val result = vm.exploreBooksUseCase.execute(
                    sourceUrl = defaultSourceUrl,
                    moduleUrl = currentUrl,
                    args = null,
                    page = nextPage
                )
                if (requestVersion != widgetRequestVersion ||
                    vm.uiState.value.enhance.selectedWidgetTargets["current_url"] != currentUrl
                ) return@launch
                if (result.books.isEmpty()) {
'''
if old not in s:
    raise SystemExit('loadMore result anchor not found')
s = s.replace(old, new, 1)

p.write_text(s)
print('patched dynamic explore runtime and stale request guards')
