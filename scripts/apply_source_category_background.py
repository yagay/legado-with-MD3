from pathlib import Path

sheet = Path('app/src/main/java/io/legado/app/ui/widget/components/modalBottomSheet/AppModalBottomSheet.kt')
s = sheet.read_text(encoding='utf-8')
if 'import androidx.compose.ui.graphics.Color\n' not in s:
    s = s.replace('import androidx.compose.ui.Alignment\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.graphics.Color\n', 1)
s = s.replace(
'''    sheetGesturesEnabled: Boolean = true,\n    content: @Composable ColumnScope.() -> Unit\n) {\n    val colorScheme = LocalLegadoThemeColors.current.colorScheme\n    val sheetContainerColor = LegadoTheme.colorScheme.surfaceContainer\n''',
'''    sheetGesturesEnabled: Boolean = true,\n    containerColor: Color? = null,\n    content: @Composable ColumnScope.() -> Unit\n) {\n    val colorScheme = LocalLegadoThemeColors.current.colorScheme\n    val sheetContainerColor = containerColor ?: LegadoTheme.colorScheme.surfaceContainer\n''', 1)
sheet.write_text(s, encoding='utf-8')

screen = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt')
s = screen.read_text(encoding='utf-8')
old = '''    AppModalBottomSheet(\n        show = state.layoutMode == 1 && sourceKindPreviewUrl != null,\n        onDismissRequest = {\n            sourceKindPreviewUrl = null\n        },\n        title = sourceKindPreviewSource?.bookSourceName ?: state.enhance.selectedSourceName,\n    ) {'''
new = '''    AppModalBottomSheet(\n        show = state.layoutMode == 1 && sourceKindPreviewUrl != null,\n        onDismissRequest = {\n            sourceKindPreviewUrl = null\n        },\n        title = sourceKindPreviewSource?.bookSourceName ?: state.enhance.selectedSourceName,\n        containerColor = if (composeEngine) {\n            MiuixTheme.colorScheme.surface\n        } else {\n            MaterialTheme.colorScheme.background\n        },\n    ) {'''
if old not in s:
    raise SystemExit('source category sheet anchor not found')
s = s.replace(old, new, 1)
screen.write_text(s, encoding='utf-8')
