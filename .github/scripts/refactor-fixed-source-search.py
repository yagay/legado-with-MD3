from pathlib import Path

# 1) RoundDropdownMenuLazy: add fixedHeader outside the scrollable list.
p = Path('app/src/main/java/io/legado/app/ui/widget/components/menuItem/RoundDropdownMenu.kt')
s = p.read_text(encoding='utf-8')
s = s.replace(
'''    state: LazyListState = rememberLazyListState(),\n    showFastScroll: Boolean = false,\n    content: LazyListScope.(dismiss: () -> Unit) -> Unit\n) {''',
'''    state: LazyListState = rememberLazyListState(),\n    showFastScroll: Boolean = false,\n    fixedHeader: (@Composable () -> Unit)? = null,\n    content: LazyListScope.(dismiss: () -> Unit) -> Unit\n) {''',
1)
old_miuix = '''                        val listModifier = Modifier\n                            .requiredSize(width = width, height = height)\n                            .background(popupContainerColor)\n                        if (showFastScroll) {\n                            FastScrollLazyColumn(\n                                modifier = listModifier,\n                                state = state,\n                                verticalArrangement = Arrangement.spacedBy(verticalSpacing)\n                            ) {\n                                item { Spacer(Modifier.height(12.dp)) }\n                                content(onDismissRequest)\n                                item { Spacer(Modifier.height(12.dp)) }\n                            }\n                        } else {\n                            LazyColumn(\n                                modifier = listModifier,\n                                state = state,\n                                verticalArrangement = Arrangement.spacedBy(verticalSpacing)\n                            ) {\n                                item { Spacer(Modifier.height(12.dp)) }\n                                content(onDismissRequest)\n                                item { Spacer(Modifier.height(12.dp)) }\n                            }\n                        }'''
new_miuix = '''                        Column(\n                            modifier = Modifier\n                                .requiredSize(width = width, height = height)\n                                .background(popupContainerColor)\n                        ) {\n                            fixedHeader?.invoke()\n                            val listModifier = Modifier\n                                .fillMaxWidth()\n                                .weight(1f)\n                            if (showFastScroll) {\n                                FastScrollLazyColumn(\n                                    modifier = listModifier,\n                                    state = state,\n                                    verticalArrangement = Arrangement.spacedBy(verticalSpacing)\n                                ) {\n                                    item { Spacer(Modifier.height(12.dp)) }\n                                    content(onDismissRequest)\n                                    item { Spacer(Modifier.height(12.dp)) }\n                                }\n                            } else {\n                                LazyColumn(\n                                    modifier = listModifier,\n                                    state = state,\n                                    verticalArrangement = Arrangement.spacedBy(verticalSpacing)\n                                ) {\n                                    item { Spacer(Modifier.height(12.dp)) }\n                                    content(onDismissRequest)\n                                    item { Spacer(Modifier.height(12.dp)) }\n                                }\n                            }\n                        }'''
if old_miuix not in s:
    raise SystemExit('miuix lazy block not found')
s = s.replace(old_miuix, new_miuix, 1)
old_m3 = '''                    val listModifier = Modifier.requiredSize(width = width, height = height)\n                    if (showFastScroll) {\n                        FastScrollLazyColumn(\n                            modifier = listModifier,\n                            state = state,\n                            verticalArrangement = Arrangement.spacedBy(verticalSpacing)\n                        ) {\n                            content(onDismissRequest)\n                        }\n                    } else {\n                        LazyColumn(\n                            modifier = listModifier,\n                            state = state,\n                            verticalArrangement = Arrangement.spacedBy(verticalSpacing)\n                        ) {\n                            content(onDismissRequest)\n                        }\n                    }'''
new_m3 = '''                    Column(\n                        modifier = Modifier.requiredSize(width = width, height = height)\n                    ) {\n                        fixedHeader?.invoke()\n                        val listModifier = Modifier\n                            .fillMaxWidth()\n                            .weight(1f)\n                        if (showFastScroll) {\n                            FastScrollLazyColumn(\n                                modifier = listModifier,\n                                state = state,\n                                verticalArrangement = Arrangement.spacedBy(verticalSpacing)\n                            ) {\n                                content(onDismissRequest)\n                            }\n                        } else {\n                            LazyColumn(\n                                modifier = listModifier,\n                                state = state,\n                                verticalArrangement = Arrangement.spacedBy(verticalSpacing)\n                            ) {\n                                content(onDismissRequest)\n                            }\n                        }\n                    }'''
if old_m3 not in s:
    raise SystemExit('material lazy block not found')
s = s.replace(old_m3, new_m3, 1)
p.write_text(s, encoding='utf-8')

# 2) GlassMediumFlexibleTopAppBar: pass optional fixed header through.
p = Path('app/src/main/java/io/legado/app/ui/widget/components/topbar/GlassMediumFlexibleTopAppBar.kt')
s = p.read_text(encoding='utf-8')
s = s.replace(
'''    subtitleDropdownMenuFastScroll: Boolean = false,\n    subtitleMenuExpanded: Boolean? = null,''',
'''    subtitleDropdownMenuFastScroll: Boolean = false,\n    subtitleDropdownMenuFixedHeader: (@Composable () -> Unit)? = null,\n    subtitleMenuExpanded: Boolean? = null,''',
1)
s = s.replace(
'''                state = subtitleDropdownMenuState,\n                showFastScroll = subtitleDropdownMenuFastScroll\n            ) {''',
'''                state = subtitleDropdownMenuState,\n                showFastScroll = subtitleDropdownMenuFastScroll,\n                fixedHeader = subtitleDropdownMenuFixedHeader,\n            ) {''',
1)
p.write_text(s, encoding='utf-8')

# 3) DynamicTopAppBar: pass through.
p = Path('app/src/main/java/io/legado/app/ui/widget/components/topbar/DynamicTopAppBar.kt')
s = p.read_text(encoding='utf-8')
s = s.replace(
'''    subtitleDropdownMenuFastScroll: Boolean = false,\n    subtitleMenuExpanded: Boolean? = null,''',
'''    subtitleDropdownMenuFastScroll: Boolean = false,\n    subtitleDropdownMenuFixedHeader: (@Composable () -> Unit)? = null,\n    subtitleMenuExpanded: Boolean? = null,''',
1)
s = s.replace(
'''        subtitleDropdownMenuFastScroll = subtitleDropdownMenuFastScroll,\n        subtitleMenuExpanded = subtitleMenuExpanded,''',
'''        subtitleDropdownMenuFastScroll = subtitleDropdownMenuFastScroll,\n        subtitleDropdownMenuFixedHeader = subtitleDropdownMenuFixedHeader,\n        subtitleMenuExpanded = subtitleMenuExpanded,''',
1)
p.write_text(s, encoding='utf-8')

# 4) ListScaffold: pass through.
p = Path('app/src/main/java/io/legado/app/ui/widget/components/list/ListScaffold.kt')
s = p.read_text(encoding='utf-8')
s = s.replace(
'''    subtitleDropdownMenuFastScroll: Boolean = false,\n    subtitleMenuExpanded: Boolean? = null,''',
'''    subtitleDropdownMenuFastScroll: Boolean = false,\n    subtitleDropdownMenuFixedHeader: (@Composable () -> Unit)? = null,\n    subtitleMenuExpanded: Boolean? = null,''',
1)
s = s.replace(
'''                subtitleDropdownMenuFastScroll = subtitleDropdownMenuFastScroll,\n                subtitleMenuExpanded = subtitleMenuExpanded,''',
'''                subtitleDropdownMenuFastScroll = subtitleDropdownMenuFastScroll,\n                subtitleDropdownMenuFixedHeader = subtitleDropdownMenuFixedHeader,\n                subtitleMenuExpanded = subtitleMenuExpanded,''',
1)
p.write_text(s, encoding='utf-8')

# 5) ExploreScreen: remove density/offset coupling and move search out of LazyListScope.
p = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('import androidx.compose.ui.platform.LocalDensity\n', '', 1)
s = s.replace(
'''    val sourceMenuListState = rememberLazyListState()\n    val sourceMenuDensity = LocalDensity.current\n    val sourceSearchHeaderOffsetPx = with(sourceMenuDensity) { 68.dp.roundToPx() }\n    var sourceMenuExpanded by rememberSaveable { mutableStateOf(false) }''',
'''    val sourceMenuListState = rememberLazyListState()\n    var sourceMenuExpanded by rememberSaveable { mutableStateOf(false) }''',
1)
s = s.replace(
'''            sourceMenuListState.scrollToItem(\n                index = defaultSourceIndex + menuPrefixCount,\n                scrollOffset = -sourceSearchHeaderOffsetPx,\n            )''',
'''            sourceMenuListState.scrollToItem(defaultSourceIndex + menuPrefixCount)''',
1)
# insert fixed header argument after fast scroll
needle = '''        subtitleDropdownMenuFastScroll = state.layoutMode == 1,\n        subtitleMenuExpanded = if (state.layoutMode == 1) sourceMenuExpanded else null,'''
replacement = '''        subtitleDropdownMenuFastScroll = state.layoutMode == 1,\n        subtitleDropdownMenuFixedHeader = if (state.layoutMode == 1 && sourceActionMenuSource == null) {\n            {\n                Box(\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .background(MaterialTheme.colorScheme.surface)\n                ) {\n                    OutlinedTextField(\n                        value = sourceMenuQuery,\n                        onValueChange = { sourceMenuQuery = it },\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .padding(horizontal = 12.dp, vertical = 6.dp),\n                        placeholder = { Text(stringResource(R.string.search)) },\n                        leadingIcon = {\n                            Icon(\n                                imageVector = Icons.Default.Search,\n                                contentDescription = null\n                            )\n                        },\n                        singleLine = true\n                    )\n                }\n            }\n        } else null,\n        subtitleMenuExpanded = if (state.layoutMode == 1) sourceMenuExpanded else null,'''
if needle not in s:
    raise SystemExit('ListScaffold fixed-header insertion point not found')
s = s.replace(needle, replacement, 1)
# remove the old sticky search block entirely
start = s.find('                    stickyHeader(key = "source_menu_search") {')
if start == -1:
    raise SystemExit('old sticky search block not found')
end_marker = '                    items(\n                        items = filteredSourceMenuItems,'
end = s.find(end_marker, start)
if end == -1:
    raise SystemExit('items marker after sticky search not found')
s = s[:start] + s[end:]
p.write_text(s, encoding='utf-8')

print('fixed source search refactor complete')
