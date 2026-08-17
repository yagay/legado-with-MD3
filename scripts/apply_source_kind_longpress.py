from pathlib import Path


def replace(path: str, old: str, new: str, count: int = 1):
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"pattern not found in {path}: {old[:120]!r}")
    text = text.replace(old, new, count)
    p.write_text(text, encoding="utf-8")

# 1) ListScaffold: additive optional subtitle long-press callback.
path = "app/src/main/java/io/legado/app/ui/widget/components/list/ListScaffold.kt"
replace(path,
'''    subtitleMenuExpanded: Boolean? = null,\n    onSubtitleMenuExpandedChange: ((Boolean) -> Unit)? = null,\n    onBackClick: (() -> Unit)? = null,''',
'''    subtitleMenuExpanded: Boolean? = null,\n    onSubtitleMenuExpandedChange: ((Boolean) -> Unit)? = null,\n    onSubtitleLongClick: (() -> Unit)? = null,\n    onBackClick: (() -> Unit)? = null,''')
replace(path,
'''                subtitleMenuExpanded = subtitleMenuExpanded,\n                onSubtitleMenuExpandedChange = onSubtitleMenuExpandedChange,\n                state = state,''',
'''                subtitleMenuExpanded = subtitleMenuExpanded,\n                onSubtitleMenuExpandedChange = onSubtitleMenuExpandedChange,\n                onSubtitleLongClick = onSubtitleLongClick,\n                state = state,''')

# 2) DynamicTopAppBar: pass callback through without changing defaults.
path = "app/src/main/java/io/legado/app/ui/widget/components/topbar/DynamicTopAppBar.kt"
replace(path,
'''    subtitleMenuExpanded: Boolean? = null,\n    onSubtitleMenuExpandedChange: ((Boolean) -> Unit)? = null,\n    state: ListUiState<T>,''',
'''    subtitleMenuExpanded: Boolean? = null,\n    onSubtitleMenuExpandedChange: ((Boolean) -> Unit)? = null,\n    onSubtitleLongClick: (() -> Unit)? = null,\n    state: ListUiState<T>,''')
replace(path,
'''        subtitleMenuExpanded = subtitleMenuExpanded,\n        onSubtitleMenuExpandedChange = onSubtitleMenuExpandedChange,\n        navigationIcon = {''',
'''        subtitleMenuExpanded = subtitleMenuExpanded,\n        onSubtitleMenuExpandedChange = onSubtitleMenuExpandedChange,\n        onSubtitleLongClick = onSubtitleLongClick,\n        navigationIcon = {''')

# 3) GlassMediumFlexibleTopAppBar: subtitle click remains source picker; long-press is independent.
path = "app/src/main/java/io/legado/app/ui/widget/components/topbar/GlassMediumFlexibleTopAppBar.kt"
replace(path,
'''import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable''',
'''import androidx.compose.foundation.ExperimentalFoundationApi\nimport androidx.compose.foundation.background\nimport androidx.compose.foundation.combinedClickable''')
replace(path,
'''    ExperimentalMaterial3Api::class,\n    ExperimentalMaterial3ExpressiveApi::class,\n    ExperimentalLayoutApi::class,''',
'''    ExperimentalMaterial3Api::class,\n    ExperimentalMaterial3ExpressiveApi::class,\n    ExperimentalLayoutApi::class,\n    ExperimentalFoundationApi::class,''')
replace(path,
'''    subtitleMenuExpanded: Boolean? = null,\n    onSubtitleMenuExpandedChange: ((Boolean) -> Unit)? = null,\n    actions: @Composable RowScope.() -> Unit = {},''',
'''    subtitleMenuExpanded: Boolean? = null,\n    onSubtitleMenuExpandedChange: ((Boolean) -> Unit)? = null,\n    onSubtitleLongClick: (() -> Unit)? = null,\n    actions: @Composable RowScope.() -> Unit = {},''')
old = '''                                val rowModifier = if (subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null) {\n                                    Modifier.clickable { setSubtitleMenuExpanded(true) }\n                                } else Modifier'''
new = '''                                val hasSubtitleMenu = subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null\n                                val rowModifier = if (hasSubtitleMenu || onSubtitleLongClick != null) {\n                                    Modifier.combinedClickable(\n                                        onClick = { if (hasSubtitleMenu) setSubtitleMenuExpanded(true) },\n                                        onLongClick = onSubtitleLongClick,\n                                    )\n                                } else Modifier'''
replace(path, old, new, 1)
old2 = '''                                    val rowModifier = if (subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null) {\n                                        Modifier.clickable { setSubtitleMenuExpanded(true) }\n                                    } else Modifier'''
new2 = '''                                    val hasSubtitleMenu = subtitleDropdownMenu != null || subtitleDropdownMenuLazy != null\n                                    val rowModifier = if (hasSubtitleMenu || onSubtitleLongClick != null) {\n                                        Modifier.combinedClickable(\n                                            onClick = { if (hasSubtitleMenu) setSubtitleMenuExpanded(true) },\n                                            onLongClick = onSubtitleLongClick,\n                                        )\n                                    } else Modifier'''
replace(path, old2, new2, 1)

# 4) ExploreScreen: load raw upstream ExploreKind only for preview and render with the list-layout components.
path = "app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt"
replace(path,
'''import io.legado.app.help.source.getExploreInfoMap''',
'''import io.legado.app.help.source.exploreKinds\nimport io.legado.app.help.source.getExploreInfoMap''')

replace(path,
'''    val scope = rememberCoroutineScope()\n\n    val composeEngine = ThemeResolver.isMiuixEngine(composeEngine)''',
'''    val scope = rememberCoroutineScope()\n    val previewExploreKindUseCase: ExploreKindUiUseCase = koinInject()\n    var sourceKindPreviewUrl by rememberSaveable { mutableStateOf<String?>(null) }\n    var sourceKindPreviewKinds by remember { mutableStateOf<List<ExploreKind>>(emptyList()) }\n    var sourceKindPreviewLoading by remember { mutableStateOf(false) }\n    val sourceKindPreviewSource = remember(sourceKindPreviewUrl, state.items) {\n        state.items.firstOrNull { it.bookSourceUrl == sourceKindPreviewUrl }\n    }\n    LaunchedEffect(sourceKindPreviewUrl, sourceKindPreviewSource) {\n        val source = sourceKindPreviewSource\n        if (sourceKindPreviewUrl == null || source == null) {\n            sourceKindPreviewKinds = emptyList()\n            sourceKindPreviewLoading = false\n            return@LaunchedEffect\n        }\n        sourceKindPreviewLoading = true\n        sourceKindPreviewKinds = runCatching { source.exploreKinds() }.getOrDefault(emptyList())\n        sourceKindPreviewLoading = false\n    }\n    val sourceKindPreviewRows = remember(sourceKindPreviewKinds) {\n        calculateExploreKindRows(sourceKindPreviewKinds, maxSpan = 6)\n    }\n\n    val composeEngine = ThemeResolver.isMiuixEngine(composeEngine)''')

replace(path,
'''        onSubtitleMenuExpandedChange = if (state.layoutMode == 1) {\n            { expanded ->\n                sourceMenuExpanded = expanded\n                if (!expanded) {\n                    sourceActionMenuUrl = null\n                    sourceMenuQuery = \"\"\n                }\n            }\n        } else null,\n        subtitleDropdownMenuLazy = if (state.layoutMode == 1) {''',
'''        onSubtitleMenuExpandedChange = if (state.layoutMode == 1) {\n            { expanded ->\n                sourceMenuExpanded = expanded\n                if (!expanded) {\n                    sourceActionMenuUrl = null\n                    sourceMenuQuery = \"\"\n                }\n            }\n        } else null,\n        onSubtitleLongClick = if (state.layoutMode == 1) {\n            {\n                val sourceUrl = state.enhance.selectedSuite?.defaultSourceUrl\n                    ?: state.items.firstOrNull()?.bookSourceUrl\n                if (sourceUrl != null) {\n                    sourceMenuExpanded = false\n                    sourceActionMenuUrl = null\n                    sourceMenuQuery = \"\"\n                    sourceKindPreviewUrl = sourceUrl\n                }\n            }\n        } else null,\n        subtitleDropdownMenuLazy = if (state.layoutMode == 1) {''')

replace(path,
'''    ExploreConfigEnhance(state, onIntent)\n}''',
'''    AppModalBottomSheet(\n        show = state.layoutMode == 1 && sourceKindPreviewUrl != null,\n        onDismissRequest = {\n            sourceKindPreviewUrl = null\n            sourceKindPreviewKinds = emptyList()\n        },\n        title = sourceKindPreviewSource?.bookSourceName ?: state.enhance.selectedSourceName,\n    ) {\n        when {\n            sourceKindPreviewLoading -> {\n                Box(\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .padding(vertical = 32.dp),\n                    contentAlignment = Alignment.Center,\n                ) {\n                    AppContainedLoadingIndicator()\n                }\n            }\n\n            sourceKindPreviewRows.isEmpty() -> {\n                Text(\n                    text = \"该书源没有发现分类\",\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .padding(vertical = 24.dp),\n                    color = MaterialTheme.colorScheme.onSurfaceVariant,\n                )\n            }\n\n            else -> {\n                Column(\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .heightIn(max = 560.dp)\n                        .verticalScroll(rememberScrollState())\n                ) {\n                    sourceKindPreviewRows.forEach { rowItems ->\n                        Row(\n                            modifier = Modifier\n                                .fillMaxWidth()\n                                .padding(vertical = 4.dp),\n                            horizontalArrangement = Arrangement.spacedBy(8.dp),\n                        ) {\n                            rowItems.forEach { (kind, span) ->\n                                ExploreKindMultiTypeItem(\n                                    kind = kind,\n                                    sourceUrl = sourceKindPreviewUrl,\n                                    onOpenUrl = { url ->\n                                        val sourceUrl = sourceKindPreviewUrl.orEmpty()\n                                        sourceKindPreviewUrl = null\n                                        sourceKindPreviewKinds = emptyList()\n                                        onOpenExploreShow(kind.title, sourceUrl, url)\n                                    },\n                                    onRefreshKinds = {\n                                        sourceKindPreviewUrl = null\n                                        sourceKindPreviewKinds = emptyList()\n                                        onIntent(ExploreIntent.RefreshSuite)\n                                    },\n                                    modifier = Modifier.weight(span.toFloat()),\n                                    isMiuix = composeEngine,\n                                    useCase = previewExploreKindUseCase,\n                                )\n                            }\n                            val totalSpan = rowItems.sumOf { it.second }\n                            if (totalSpan < 6) {\n                                Spacer(modifier = Modifier.weight((6 - totalSpan).toFloat()))\n                            }\n                        }\n                    }\n                }\n            }\n        }\n    }\n\n    ExploreConfigEnhance(state, onIntent)\n}''')

print("source kind long-press patch applied")
