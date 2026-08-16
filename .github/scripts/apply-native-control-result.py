from pathlib import Path

path = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/vm/ExploreViewModelEnhance.kt')
text = path.read_text()
old = '''    private fun refreshNativeControls() {
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
'''
new = '''    private fun refreshNativeControls() {
        val result = ModernExploreControlExtractor.extractNativeControls(allSourceRawKinds)
        suiteSearchControl = result.searchControl
        vm.updateUiState { state ->
            state.copy(
                enhance = state.enhance.copy(
                    dynamicControls = result.visibleControls.toImmutableList()
                )
            )
        }
    }
'''
if old not in text:
    raise SystemExit('target refreshNativeControls block not found')
path.write_text(text.replace(old, new, 1))
