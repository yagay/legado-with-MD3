from pathlib import Path

# 1) SliderSettingItem: optional live preview + live option label
p = Path('app/src/main/java/io/legado/app/ui/widget/components/settingItem/SliderSettingItem.kt')
s = p.read_text(encoding='utf-8')
s = s.replace(
'''    highlightKey: String? = null,\n    onValueChange: (Float) -> Unit\n) {''',
'''    highlightKey: String? = null,\n    onValuePreviewChange: ((Float) -> Unit)? = null,\n    onValueChange: (Float) -> Unit\n) {''')
s = s.replace(
'''        option = valueLabel?.invoke(value) ?: if (decimal) value.toString() else value.roundToInt().toString(),''',
'''        option = valueLabel?.invoke(if (expanded) sliderValue else value)\n            ?: if (decimal) (if (expanded) sliderValue else value).toString()\n            else (if (expanded) sliderValue else value).roundToInt().toString(),''')
s = s.replace(
'''                                sliderValue = v.coerceIn(valueRange)''',
'''                                sliderValue = v.coerceIn(valueRange)\n                                onValuePreviewChange?.invoke(sliderValue)''')
s = s.replace(
'''                        onValueChange = { sliderValue = it },''',
'''                        onValueChange = {\n                            sliderValue = it\n                            onValuePreviewChange?.invoke(it)\n                        },''')
s = s.replace(
'''                    IconButton(onClick = { sliderValue = defaultValue }) {''',
'''                    IconButton(onClick = {\n                        sliderValue = defaultValue\n                        onValuePreviewChange?.invoke(defaultValue)\n                    }) {''')
s = s.replace(
'''                        onDismiss = {\n                            sliderValue = value\n                            expanded = false\n                        },''',
'''                        onDismiss = {\n                            sliderValue = value\n                            onValuePreviewChange?.invoke(value)\n                            expanded = false\n                        },''')
p.write_text(s, encoding='utf-8')

# 2) ExploreIntent: memory-only preview intent
p = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreViewModel.kt')
s = p.read_text(encoding='utf-8')
s = s.replace(
'''    data class UpdateDiscoverySettings(val transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig) : ExploreIntent\n''',
'''    data class UpdateDiscoverySettings(val transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig) : ExploreIntent\n    data class PreviewDiscoverySettings(val transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig) : ExploreIntent\n''')
p.write_text(s, encoding='utf-8')

# 3) Enhance VM handler + preview method
p = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
s = p.read_text(encoding='utf-8')
s = s.replace(
'''            is ExploreIntent.UpdateDiscoverySettings -> updateDiscoverySettings(intent.transform)\n''',
'''            is ExploreIntent.UpdateDiscoverySettings -> updateDiscoverySettings(intent.transform)\n            is ExploreIntent.PreviewDiscoverySettings -> previewDiscoverySettings(intent.transform)\n''')
anchor = '''    private fun updateDiscoverySettings(transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig) {\n'''
method = '''    private fun previewDiscoverySettings(transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig) {\n        val state = vm.uiState.value\n        val current = state.enhance.selectedSuite ?: return\n        val base = DiscoverySuiteConfig(suites = state.enhance.suites)\n        val preview = transform(base)\n        val previewCurrent = preview.suites.find { it.id == current.id } ?: return\n        vm.updateUiState { ui ->\n            ui.copy(\n                enhance = ui.enhance.copy(\n                    suites = preview.suites.toImmutableList(),\n                    selectedSuite = previewCurrent,\n                )\n            )\n        }\n    }\n\n'''
if anchor not in s:
    raise SystemExit('updateDiscoverySettings anchor missing')
s = s.replace(anchor, method + anchor, 1)
p.write_text(s, encoding='utf-8')

# 4) DiscoveryConfigSheet: wire live preview for both sliders
p = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/screen/DiscoveryConfigSheet.kt')
s = p.read_text(encoding='utf-8')

def transform_block(field, conversion):
    return f'''{{ value ->\n                                onIntent(ExploreIntent.PreviewDiscoverySettings {{ config ->\n                                    config.copy(\n                                        suites = config.suites.map {{ s ->\n                                            if (s.id == suite.id) {{\n                                                s.copy(widgets = s.widgets.map {{ w ->\n                                                    if (w.id == bookWidget.id) w.copy({field} = value.{conversion}()) else w\n                                                }})\n                                            }} else s\n                                        }}\n                                    )\n                                }})\n                            }}'''

# Insert immediately before onValueChange in grid slider
needle = '''                            steps = 3,\n                            onValueChange = { value ->'''
repl = '''                            steps = 3,\n                            onValuePreviewChange = ''' + transform_block('gridCount','toInt') + ''',\n                            onValueChange = { value ->'''
if needle not in s:
    raise SystemExit('grid slider anchor missing')
s = s.replace(needle, repl, 1)

needle = '''                            valueRange = 80f..200f,\n                            onValueChange = { value ->'''
repl = '''                            valueRange = 80f..200f,\n                            onValuePreviewChange = ''' + transform_block('coverHeight','toInt') + ''',\n                            onValueChange = { value ->'''
if needle not in s:
    raise SystemExit('cover slider anchor missing')
s = s.replace(needle, repl, 1)
p.write_text(s, encoding='utf-8')
