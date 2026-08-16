from pathlib import Path

path = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt')
text = path.read_text(encoding='utf-8')

text = text.replace(
    'import androidx.compose.ui.platform.LocalConfiguration\n',
    'import androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.LocalDensity\n',
    1,
)
text = text.replace(
    '    val sourceMenuListState = rememberLazyListState()\n    var sourceMenuExpanded by rememberSaveable { mutableStateOf(false) }',
    '    val sourceMenuListState = rememberLazyListState()\n    val sourceMenuDensity = LocalDensity.current\n    val sourceSearchHeaderOffsetPx = with(sourceMenuDensity) { 68.dp.roundToPx() }\n    var sourceMenuExpanded by rememberSaveable { mutableStateOf(false) }',
    1,
)
text = text.replace(
    '            sourceMenuListState.scrollToItem(defaultSourceIndex + menuPrefixCount)\n',
    '            sourceMenuListState.scrollToItem(\n                index = defaultSourceIndex + menuPrefixCount,\n                scrollOffset = -sourceSearchHeaderOffsetPx,\n            )\n',
    1,
)
text = text.replace(
    '                    item(key = "source_menu_search") {',
    '                    stickyHeader(key = "source_menu_search") {',
    1,
)

required = [
    'LocalDensity',
    'sourceSearchHeaderOffsetPx',
    'scrollOffset = -sourceSearchHeaderOffsetPx',
    'stickyHeader(key = "source_menu_search")',
]
for marker in required:
    if marker not in text:
        raise SystemExit(f'missing expected marker after rewrite: {marker}')

path.write_text(text, encoding='utf-8')
