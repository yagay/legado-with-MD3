from pathlib import Path

path = Path('modules/legado-enhance/java/io/legado/app/enhance/explore/screen/DiscoverySuiteScreen.kt')
text = path.read_text()
old = '''            state.enhance.dynamicControls.forEachIndexed { index, kind ->
                item(key = "dynamic_native_${index}_${kind.type}_${kind.title}") {
                    ExploreKindMultiTypeItem(
                        kind = kind,
                        sourceUrl = suite.defaultSourceUrl,
                        onOpenUrl = { url ->
                            onOpenExploreShow(kind.title, suite.defaultSourceUrl.orEmpty(), url)
                        },
                        onRefreshKinds = { onIntent(ExploreIntent.RefreshSuite) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        isMiuix = false,
                        useCase = exploreKindUseCase
                    )
                }
            }
'''
new = '''            if (state.enhance.dynamicControls.isNotEmpty()) {
                item(key = "dynamic_native_controls") {
                    AdaptiveExploreControlRows(
                        controls = state.enhance.dynamicControls,
                        sourceUrl = suite.defaultSourceUrl,
                        useCase = exploreKindUseCase,
                        onOpenUrl = { kind, url ->
                            onOpenExploreShow(kind.title, suite.defaultSourceUrl.orEmpty(), url)
                        },
                        onRefreshKinds = { onIntent(ExploreIntent.RefreshSuite) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
'''
if old not in text:
    raise SystemExit('target block not found')
text = text.replace(old, new, 1)
# Remove import that is no longer used in this screen.
text = text.replace('import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem\n', '')
path.write_text(text)
