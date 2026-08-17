from pathlib import Path

vm_path = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
vm = vm_path.read_text(encoding='utf-8')

repls = [
    (
        'import io.legado.app.ui.main.explore.ExploreViewModel.DynamicSelectorUi\n',
        'import io.legado.app.ui.main.explore.ExploreViewModel.DynamicSelectorUi\nimport io.legado.app.ui.widget.components.explore.calculateExploreKindRows\n'
    ),
    (
        '    val dynamicControls: ImmutableList<ExploreKind> = persistentListOf(),\n',
        '    val dynamicControls: ImmutableList<ExploreKind> = persistentListOf(),\n    val sourceKindPreviewRows: ImmutableList<ImmutableList<Pair<ExploreKind, Int>>> = persistentListOf(),\n    val sourceKindPreviewReady: Boolean = false,\n'
    ),
    (
        '                    dynamicSelectors = persistentListOf(),\n                    dynamicControls = persistentListOf(),\n',
        '                    dynamicSelectors = persistentListOf(),\n                    dynamicControls = persistentListOf(),\n                    sourceKindPreviewRows = persistentListOf(),\n                    sourceKindPreviewReady = false,\n'
    ),
    (
        '            val classification = try {\n                ModernExploreClassificationEngine.classify(\n                    rawKinds,\n                    source?.exploreKindsJson().orEmpty()\n                )\n            } catch (_: Exception) {\n                ModernExploreClassificationEngine.classify(rawKinds, "")\n            }\n',
        '            val sourceKindPreviewRows = buildSourceKindPreviewRows(rawKinds)\n            val classification = try {\n                ModernExploreClassificationEngine.classify(\n                    rawKinds,\n                    source?.exploreKindsJson().orEmpty()\n                )\n            } catch (_: Exception) {\n                ModernExploreClassificationEngine.classify(rawKinds, "")\n            }\n'
    ),
    (
        '            vm.updateUiState { state ->\n                state.copy(enhance = state.enhance.copy(exploreError = exploreError))\n            }\n',
        '            vm.updateUiState { state ->\n                state.copy(\n                    enhance = state.enhance.copy(\n                        exploreError = exploreError,\n                        sourceKindPreviewRows = sourceKindPreviewRows,\n                        sourceKindPreviewReady = true,\n                    )\n                )\n            }\n',
        1
    ),
    (
        '                    suiteSearchBooks = null,\n                    suiteSearchLoading = false,\n                    suiteSearchRemote = false,\n',
        '                    suiteSearchBooks = null,\n                    suiteSearchLoading = false,\n                    suiteSearchRemote = false,\n                    sourceKindPreviewRows = persistentListOf(),\n                    sourceKindPreviewReady = false,\n',
        1
    ),
    (
        '                allSourceRawKinds = source.exploreKinds()\n                val classification = ModernExploreClassificationEngine.classify(\n',
        '                allSourceRawKinds = source.exploreKinds()\n                val sourceKindPreviewRows = buildSourceKindPreviewRows(allSourceRawKinds)\n                val classification = ModernExploreClassificationEngine.classify(\n'
    ),
]

for entry in repls:
    old, new = entry[0], entry[1]
    count = entry[2] if len(entry) > 2 else 1
    if old not in vm:
        raise SystemExit('VM pattern not found: ' + old[:120].replace('\n', '\\n'))
    vm = vm.replace(old, new, count)

# Replace the second exploreError-only state update in selectControlTarget.
needle = '''                vm.updateUiState { state ->\n                    state.copy(enhance = state.enhance.copy(exploreError = exploreError))\n                }'''
replacement = '''                vm.updateUiState { state ->\n                    state.copy(\n                        enhance = state.enhance.copy(\n                            exploreError = exploreError,\n                            sourceKindPreviewRows = sourceKindPreviewRows,\n                            sourceKindPreviewReady = true,\n                        )\n                    )\n                }'''
if needle not in vm:
    raise SystemExit('select exploreError update not found')
vm = vm.replace(needle, replacement, 1)

insert_before = '    private fun rebuildSelectors(suite: DiscoverySuite, defaultSourceUrl: String) {'
helper = '''    private fun buildSourceKindPreviewRows(\n        kinds: List<ExploreKind>\n    ): ImmutableList<ImmutableList<Pair<ExploreKind, Int>>> {\n        return calculateExploreKindRows(kinds, maxSpan = 6)\n            .map { it.toImmutableList() }\n            .toImmutableList()\n    }\n\n'''
if insert_before not in vm:
    raise SystemExit('rebuildSelectors anchor not found')
vm = vm.replace(insert_before, helper + insert_before, 1)
vm_path.write_text(vm, encoding='utf-8')

screen_path = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt')
screen = screen_path.read_text(encoding='utf-8')

screen_repls = [
    (
        'import io.legado.app.ui.widget.components.explore.calculateExploreKindRows\n',
        ''
    ),
    (
        'import kotlinx.coroutines.Dispatchers.IO\n',
        ''
    ),
    (
        'import kotlinx.coroutines.withContext\n',
        ''
    ),
    (
        '''    var sourceKindPreviewUrl by rememberSaveable { mutableStateOf<String?>(null) }\n    var sourceKindPreviewRows by remember { mutableStateOf<List<List<Pair<ExploreKind, Int>>>>(emptyList()) }\n    var sourceKindPreviewLoading by remember { mutableStateOf(false) }\n    val sourceKindPreviewSource = remember(sourceKindPreviewUrl, state.items) {\n        state.items.firstOrNull { it.bookSourceUrl == sourceKindPreviewUrl }\n    }\n    LaunchedEffect(sourceKindPreviewUrl, sourceKindPreviewSource) {\n        val source = sourceKindPreviewSource\n        if (sourceKindPreviewUrl == null || source == null) {\n            sourceKindPreviewRows = emptyList()\n            sourceKindPreviewLoading = false\n            return@LaunchedEffect\n        }\n        sourceKindPreviewLoading = true\n        sourceKindPreviewRows = withContext(IO) {\n            runCatching {\n                calculateExploreKindRows(source.exploreKinds(), maxSpan = 6)\n            }.getOrDefault(emptyList())\n        }\n        sourceKindPreviewLoading = false\n    }\n''',
        '''    var sourceKindPreviewUrl by rememberSaveable { mutableStateOf<String?>(null) }\n    val sourceKindPreviewRows = state.enhance.sourceKindPreviewRows\n    val sourceKindPreviewLoading = !state.enhance.sourceKindPreviewReady\n    val sourceKindPreviewSource = remember(sourceKindPreviewUrl, state.items) {\n        state.items.firstOrNull { it.bookSourceUrl == sourceKindPreviewUrl }\n    }\n'''
    ),
    (
        '''        onDismissRequest = {\n            sourceKindPreviewUrl = null\n            sourceKindPreviewRows = emptyList()\n        },''',
        '''        onDismissRequest = {\n            sourceKindPreviewUrl = null\n        },'''
    ),
    (
        '''                                        sourceKindPreviewUrl = null\n                                        sourceKindPreviewRows = emptyList()\n                                        onOpenExploreShow(kind.title, sourceUrl, url)''',
        '''                                        sourceKindPreviewUrl = null\n                                        onOpenExploreShow(kind.title, sourceUrl, url)'''
    ),
    (
        '''                                        sourceKindPreviewUrl = null\n                                        sourceKindPreviewRows = emptyList()\n                                        onIntent(ExploreIntent.RefreshSuite)''',
        '''                                        sourceKindPreviewUrl = null\n                                        onIntent(ExploreIntent.RefreshSuite)'''
    ),
]

for old, new in screen_repls:
    if old not in screen:
        raise SystemExit('Screen pattern not found: ' + old[:120].replace('\n', '\\n'))
    screen = screen.replace(old, new, 1)

screen_path.write_text(screen, encoding='utf-8')
print('reused prepared source kind rows')
