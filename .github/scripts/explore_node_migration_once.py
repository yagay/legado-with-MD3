from pathlib import Path
import re

path = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
text = path.read_text(encoding='utf-8')

# Imports: hierarchy is now represented by enhance ExploreNode, not ExploreKind helpers.
for line in [
    'import io.legado.app.enhance.explore.builder.hasModernChildren\n',
    'import io.legado.app.enhance.explore.builder.modernTargetUrl\n',
]:
    if line not in text:
        raise SystemExit(f'missing old hierarchy import: {line!r}')
    text = text.replace(line, '', 1)

model_marker = 'import io.legado.app.enhance.explore.model.ExploreMode\n'
if model_marker not in text:
    raise SystemExit('ExploreMode import marker not found')
text = text.replace(
    model_marker,
    model_marker + 'import io.legado.app.enhance.explore.model.ExploreNode\n',
    1,
)

old = '    private var allSourceKinds: List<ExploreKind> = emptyList()'
if old not in text:
    raise SystemExit('allSourceKinds declaration not found')
text = text.replace(old, '    private var allSourceKinds: List<ExploreNode> = emptyList()', 1)

# Both refresh paths use the same classifier. Fallback remains structure-only.
text = text.replace(
    'ModernExploreClassificationEngine.Result(allSourceRawKinds, ExploreMode.FLAT)',
    'ModernExploreClassificationEngine.classify(allSourceRawKinds, "")'
)
text = text.replace('allSourceKinds = classification.kinds', 'allSourceKinds = classification.nodes')

old_controls = '            allSourceControls = ModernExploreControlExtractor.fromFlatKinds(allSourceRawKinds)'
new_controls = '''            allSourceControls = if (classification.mode == ExploreMode.TREE) {
                ModernExploreControlExtractor.fromTreeRoot(classification.nodes)
            } else {
                ModernExploreControlExtractor.fromFlatKinds(allSourceRawKinds)
            }'''
count = text.count(old_controls)
if count != 2:
    raise SystemExit(f'expected 2 control assignment sites, found {count}')
text = text.replace(old_controls, new_controls)

# Traversal now consumes ExploreNode directly.
text = text.replace('currentLevelItems.first().modernTargetUrl().isNullOrBlank()', 'currentLevelItems.first().url.isNullOrBlank()')
text = text.replace('currentLevelItems.first().hasModernChildren()', 'currentLevelItems.first().children.isNotEmpty()')
text = text.replace('it.hasModernChildren() || !it.modernTargetUrl().isNullOrBlank()', 'it.children.isNotEmpty() || !it.url.isNullOrBlank()')
text = text.replace('tagUrl = kind.modernTargetUrl().orEmpty()', 'tagUrl = kind.url.orEmpty()')
text = text.replace('selectedItem.modernTargetUrl()?.let { lastValidUrl = it }', 'selectedItem.url?.let { lastValidUrl = it }')

# Root select controls were already extracted using node sourceIndex; no second tree extraction is needed.
controls_pattern = re.compile(
    r'''        val controlsForMode = if \(allSourceMode == ExploreMode\.TREE\) \{\n            ModernExploreControlExtractor\.fromTreeRoot\(allSourceRawKinds\)\n        \} else \{\n            allSourceControls\n        \}\n        controlsForMode\.sortedBy \{ it\.sourceIndex \}\.forEach \{ control ->'''
)
text, count = controls_pattern.subn(
    '        allSourceControls.sortedBy { it.sourceIndex }.forEach { control ->',
    text,
    count=1,
)
if count != 1:
    raise SystemExit(f'controlsForMode replacement count={count}')

# Node counting no longer touches core ExploreKind hierarchy.
count_pattern = re.compile(
    r'''    private fun countExploreNodes\(kinds: List<ExploreKind>\): Int \{\n        var count = 0\n        val stack = ArrayDeque<ExploreKind>\(\)\n        kinds\.forEach\(stack::addLast\)\n        while \(stack\.isNotEmpty\(\)\) \{\n            val node = stack\.removeLast\(\)\n            count\+\+\n            node\.children\.orEmpty\(\)\.forEach\(stack::addLast\)\n        \}\n        return count\n    \}'''
)
count_replacement = '''    private fun countExploreNodes(nodes: List<ExploreNode>): Int {
        var count = 0
        val stack = ArrayDeque<ExploreNode>()
        nodes.forEach(stack::addLast)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            count++
            node.children.forEach(stack::addLast)
        }
        return count
    }'''
text, count = count_pattern.subn(count_replacement, text, count=1)
if count != 1:
    raise SystemExit(f'countExploreNodes replacement count={count}')

text = text.replace('items: List<ExploreKind>,\n        inheritedTitle', 'items: List<ExploreNode>,\n        inheritedTitle', 1)
text = text.replace('items: List<ExploreKind>\n    ): DynamicSelectorUi.SelectorType', 'items: List<ExploreNode>\n    ): DynamicSelectorUi.SelectorType', 1)

# Tree traversal must not retain direct core children/helper references.
for token in [
    '.modernTargetUrl()',
    '.hasModernChildren()',
    'classification.kinds',
    'fromTreeRoot(allSourceRawKinds)',
    'List<ExploreKind>): Int',
]:
    if token in text:
        raise SystemExit(f'forbidden hierarchy leftover: {token}')

path.write_text(text, encoding='utf-8')
