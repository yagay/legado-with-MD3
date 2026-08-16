from pathlib import Path
import re

core = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreViewModel.kt')
text = core.read_text(encoding='utf-8')
old = '    private val exploreKindUseCase: ExploreKindUiUseCase,'
if old not in text:
    raise SystemExit('ExploreViewModel exploreKindUseCase constructor marker not found')
text = text.replace(old, '    internal val exploreKindUseCase: ExploreKindUiUseCase,', 1)
dead = re.compile(r'''\n\n    private companion object \{\n        val STATUS_SELECTOR_TITLES = setOf\([\s\S]*?\n        \)\n        val RANK_SELECTOR_TITLES = setOf\([\s\S]*?\n        \)\n    \}\n''')
text, count = dead.subn('\n', text, count=1)
if count != 1:
    raise SystemExit(f'ExploreViewModel dead selector constants replacement count={count}')
core.write_text(text, encoding='utf-8')

path = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
text = path.read_text(encoding='utf-8')

for line in [
    'import com.script.rhino.runScriptWithContext\n',
    'import io.legado.app.ui.login.SourceLoginJsExtensions\n',
]:
    if line not in text:
        raise SystemExit(f'missing import marker: {line!r}')
    text = text.replace(line, '', 1)

import_marker = 'import io.legado.app.enhance.explore.builder.ModernExploreClassificationEngine\n'
helper_imports = (
    'import io.legado.app.enhance.explore.builder.hasModernChildren\n'
    'import io.legado.app.enhance.explore.builder.modernTargetUrl\n'
)
if import_marker not in text:
    raise SystemExit('classification import marker not found')
text = text.replace(import_marker, import_marker + helper_imports, 1)

select_pattern = re.compile(
    r'    private fun selectControlTarget\([\s\S]*?\n    private fun extractSelectVariableKey\('
)
select_replacement = '''    private fun selectControlTarget(
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
                allSourceKinds = classification.kinds
                allSourceMode = classification.mode
                allSourceControls = ModernExploreControlExtractor.fromFlatKinds(allSourceRawKinds)
                rebuildSelectors(suite, defaultSourceUrl)
            } catch (_: Exception) {
            }
        }
    }

    private fun extractSelectVariableKey('''
text, count = select_pattern.subn(select_replacement, text, count=1)
if count != 1:
    raise SystemExit(f'selectControlTarget replacement count={count}')

extract_pattern = re.compile(
    r'    private fun extractSelectVariableKey\([\s\S]*?\n    private fun rebuildSelectors\('
)
text, count = extract_pattern.subn('    private fun rebuildSelectors(', text, count=1)
if count != 1:
    raise SystemExit(f'extractSelectVariableKey removal count={count}')

text = text.replace('.targetUrl()', '.modernTargetUrl()')
text = text.replace('.hasChildren()', '.hasModernChildren()')

infer_pattern = re.compile(
    r'    private fun inferSelectorTitle\([\s\S]*?\n    private fun cleanExploreTitle\('
)
infer_replacement = '''    private fun inferSelectorTitle(
        level: Int,
        items: List<ExploreKind>,
        inheritedTitle: String?
    ): String {
        // 现代布局不再根据名称猜测频道/状态/榜单等业务语义。
        val inherited = cleanExploreTitle(inheritedTitle.orEmpty())
        return inherited.takeIf { it.isNotBlank() } ?: "分类"
    }

    private fun inferSelectorType(
        items: List<ExploreKind>
    ): DynamicSelectorUi.SelectorType {
        // RankButtons 仅由显式 DiscoverySuite widget 配置决定。
        return DynamicSelectorUi.SelectorType.TagBar
    }

    private fun cleanExploreTitle('''
text, count = infer_pattern.subn(infer_replacement, text, count=1)
if count != 1:
    raise SystemExit(f'infer selector replacement count={count}')

extensions_pattern = re.compile(
    r'''\n    private fun ExploreKind\.hasChildren\(\): Boolean =[\s\S]*?\n    private fun ExploreKind\.isGroupHeader\(\): Boolean =[^\n]*\n'''
)
text, count = extensions_pattern.subn('\n', text, count=1)
if count != 1:
    raise SystemExit(f'private ExploreKind extension removal count={count}')

companion_pattern = re.compile(
    r'''    private companion object \{\n        const val DYNAMIC_LEVEL_PREFIX = "dynamic_level_"\n        const val DYNAMIC_SELECT_PREFIX = "dynamic_select_"\n        val STATUS_SELECTOR_TITLES = setOf\([\s\S]*?\n        \)\n        val RANK_SELECTOR_TITLES = setOf\([\s\S]*?\n        \)\n    \}'''
)
companion_replacement = '''    private companion object {
        const val DYNAMIC_LEVEL_PREFIX = "dynamic_level_"
        const val DYNAMIC_SELECT_PREFIX = "dynamic_select_"
    }'''
text, count = companion_pattern.subn(companion_replacement, text, count=1)
if count != 1:
    raise SystemExit(f'companion cleanup count={count}')

for token in [
    'SourceLoginJsExtensions',
    'runScriptWithContext',
    'STATUS_SELECTOR_TITLES',
    'RANK_SELECTOR_TITLES',
    'extractSelectVariableKey',
    '.targetUrl()',
    '.hasChildren()',
]:
    if token in text:
        raise SystemExit(f'forbidden leftover: {token}')

path.write_text(text, encoding='utf-8')
