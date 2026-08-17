from pathlib import Path

path = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt')
text = path.read_text(encoding='utf-8')

repls = [
('import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.rememberLazyListState',
 'import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.itemsIndexed\nimport androidx.compose.foundation.lazy.rememberLazyListState'),
('import kotlinx.coroutines.launch',
 'import kotlinx.coroutines.Dispatchers.IO\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext'),
('''    var sourceKindPreviewKinds by remember { mutableStateOf<List<ExploreKind>>(emptyList()) }\n    var sourceKindPreviewLoading by remember { mutableStateOf(false) }''',
 '''    var sourceKindPreviewRows by remember { mutableStateOf<List<List<Pair<ExploreKind, Int>>>>(emptyList()) }\n    var sourceKindPreviewLoading by remember { mutableStateOf(false) }'''),
('''        if (sourceKindPreviewUrl == null || source == null) {\n            sourceKindPreviewKinds = emptyList()\n            sourceKindPreviewLoading = false\n            return@LaunchedEffect\n        }\n        sourceKindPreviewLoading = true\n        sourceKindPreviewKinds = runCatching { source.exploreKinds() }.getOrDefault(emptyList())\n        sourceKindPreviewLoading = false\n    }\n    val sourceKindPreviewRows = remember(sourceKindPreviewKinds) {\n        calculateExploreKindRows(sourceKindPreviewKinds, maxSpan = 6)\n    }''',
 '''        if (sourceKindPreviewUrl == null || source == null) {\n            sourceKindPreviewRows = emptyList()\n            sourceKindPreviewLoading = false\n            return@LaunchedEffect\n        }\n        sourceKindPreviewLoading = true\n        sourceKindPreviewRows = withContext(IO) {\n            runCatching {\n                calculateExploreKindRows(source.exploreKinds(), maxSpan = 6)\n            }.getOrDefault(emptyList())\n        }\n        sourceKindPreviewLoading = false\n    }'''),
('''            sourceKindPreviewUrl = null\n            sourceKindPreviewKinds = emptyList()''',
 '''            sourceKindPreviewUrl = null\n            sourceKindPreviewRows = emptyList()'''),
('''                Column(\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .heightIn(max = 560.dp)\n                        .verticalScroll(rememberScrollState())\n                ) {\n                    sourceKindPreviewRows.forEach { rowItems ->''',
 '''                LazyColumn(\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .heightIn(max = 560.dp)\n                ) {\n                    itemsIndexed(\n                        items = sourceKindPreviewRows,\n                        key = { index, _ -> "source_kind_preview_$index" },\n                    ) { _, rowItems ->'''),
('''                                        sourceKindPreviewUrl = null\n                                        sourceKindPreviewKinds = emptyList()''',
 '''                                        sourceKindPreviewUrl = null\n                                        sourceKindPreviewRows = emptyList()'''),
('''                                        sourceKindPreviewUrl = null\n                                        sourceKindPreviewKinds = emptyList()''',
 '''                                        sourceKindPreviewUrl = null\n                                        sourceKindPreviewRows = emptyList()'''),
]

for old, new in repls:
    if old not in text:
        raise SystemExit('pattern not found: ' + old[:120].replace('\n', '\\n'))
    text = text.replace(old, new, 1)

path.write_text(text, encoding='utf-8')
print('optimized source category popup')
