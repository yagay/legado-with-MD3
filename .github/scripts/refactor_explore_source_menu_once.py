from pathlib import Path
import re

path = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt')
s = path.read_text(encoding='utf-8')

for imp in [
    'import androidx.compose.material.icons.automirrored.filled.Login\n',
    'import androidx.compose.material.icons.filled.ArrowBack\n',
    'import androidx.compose.material.icons.filled.Delete\n',
    'import androidx.compose.material.icons.filled.Edit\n',
    'import androidx.compose.material.icons.filled.VerticalAlignTop\n',
    'import androidx.compose.ui.platform.LocalDensity\n',
    'import androidx.compose.ui.text.AnnotatedString\n',
    'import androidx.compose.ui.text.rememberTextMeasurer\n',
]:
    s = s.replace(imp, '')

s, n = re.subn(
    r'''    val sourceMenuTextStyle = MaterialTheme\.typography\.labelLarge.*?    val sourcePopupWidth = remember\(sourceMenuWidth, sourceActionMenuSource\) \{\n        if \(sourceActionMenuSource != null\) maxOf\(sourceMenuWidth, 240\.dp\) else sourceMenuWidth\n    \}\n''',
    '''    val configuration = LocalConfiguration.current\n    val sourceMenuMaxHeight = remember(configuration.screenHeightDp) {\n        (configuration.screenHeightDp.dp - 96.dp).coerceAtLeast(124.dp)\n    }\n    val sourceActionRowCount = remember(sourceActionMenuSource) {\n        sourceActionMenuSource?.let { exploreSourceActionRowCount(it, includeBack = true) } ?: 0\n    }\n    val sourceMenuHeight = remember(filteredSourceMenuItems.size, sourceActionRowCount, sourceMenuMaxHeight) {\n        val rowCount = if (sourceActionRowCount > 0) sourceActionRowCount else filteredSourceMenuItems.size\n        val baseHeight = if (sourceActionRowCount > 0) 68 else 132\n        (baseHeight + rowCount * 56).dp.coerceIn(124.dp, sourceMenuMaxHeight)\n    }\n    // Fixed width avoids the visible resize flash while long names remain single-line in menu items.\n    val sourcePopupWidth = 280.dp\n''',
    s,
    count=1,
    flags=re.S,
)
if n != 1:
    raise SystemExit(f'width/count block replacement count={n}')

modern_start = '''                } else {\n                    item(key = "source_action_header_${actionSource.bookSourceUrl}") {'''
modern_end = '''                    item(key = "source_action_delete") {\n                        RoundDropdownMenuItem(\n                            leadingIcon = {\n                                MenuItemIcon(\n                                    Icons.Default.Delete,\n                                    tint = MaterialTheme.colorScheme.error\n                                )\n                            },\n                            text = stringResource(R.string.delete),\n                            color = LegadoTheme.colorScheme.error,\n                            onClick = {\n                                sourceToDeleteUrl = actionSource.bookSourceUrl\n                                sourceActionMenuUrl = null\n                                dismiss()\n                            }\n                        )\n                    }\n                }'''
start = s.find(modern_start)
end = s.find(modern_end, start)
if start < 0 or end < 0:
    raise SystemExit('modern action block not found')
end += len(modern_end)
modern_new = '''                } else {\n                    item(key = "source_action_menu_${actionSource.bookSourceUrl}") {\n                        ExploreSourceActionMenuContent(\n                            source = actionSource,\n                            onTop = { onIntent(ExploreIntent.TopSource(actionSource)) },\n                            onEdit = { onIntent(ExploreIntent.OpenEdit(actionSource)) },\n                            onSearch = { onIntent(ExploreIntent.OpenSearch(actionSource)) },\n                            onLogin = { onIntent(ExploreIntent.OpenLogin(actionSource)) },\n                            onSetHomeSource = { onIntent(ExploreIntent.SetSuiteDefaultSource(actionSource.bookSourceUrl)) },\n                            onRefresh = { onIntent(ExploreIntent.RefreshKinds(actionSource)) },\n                            onDelete = { sourceToDeleteUrl = actionSource.bookSourceUrl },\n                            onDismiss = {\n                                sourceActionMenuUrl = null\n                                dismiss()\n                            },\n                            onBack = { sourceActionMenuUrl = null },\n                        )\n                    }\n                }'''
s = s[:start] + modern_new + s[end:]

default_start = '                RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {'
default_end = '''                    RoundDropdownMenuItem(\n                        leadingIcon = {\n                            MenuItemIcon(\n                                Icons.Default.Delete,\n                                tint = MaterialTheme.colorScheme.error\n                            )\n                        },\n                        text = stringResource(R.string.delete),\n                        color = LegadoTheme.colorScheme.error,\n                        onClick = { onDelete(); showMenu = false }\n                    )\n                }'''
start = s.find(default_start)
end = s.find(default_end, start)
if start < 0 or end < 0:
    raise SystemExit('default action block not found')
end += len(default_end)
default_new = '''                RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {\n                    ExploreSourceActionMenuContent(\n                        source = item,\n                        onTop = onTop,\n                        onEdit = onEdit,\n                        onSearch = onSearch,\n                        onLogin = onLogin,\n                        onSetHomeSource = onSetHomeSource,\n                        onRefresh = onRefresh,\n                        onDelete = onDelete,\n                        onDismiss = { showMenu = false },\n                    )\n                }'''
s = s[:start] + default_new + s[end:]

path.write_text(s, encoding='utf-8')
