package io.legado.app.ui.config.labConfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.enhance.settingssearch.SettingDestination
import io.legado.app.enhance.settingssearch.getSettingScrollInfo
import io.legado.app.enhance.ui.LaunchSettingScrollEffect
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.androidx.compose.koinViewModel

@Composable
fun LabConfigRouteScreen(
    onBackClick: () -> Unit,
    searchKey: String? = null,
    viewModel: LabConfigViewModel = koinViewModel(),
) {
    LabConfigScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        searchKey = searchKey,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabConfigScreen(
    state: LabConfigUiState,
    onIntent: (LabConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    searchKey: String? = null,
) {
    val settings = state.settings
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val scrollInfo = remember(searchKey) {
        getSettingScrollInfo(context, SettingDestination.Lab, searchKey)
    }
    LaunchSettingScrollEffect(scrollInfo, listState)

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.lab_setting),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
        ) {
            item {
                SplicedColumnGroup {
                    SwitchSettingItem(
                        title = stringResource(R.string.lab_enabled_title),
                        description = stringResource(R.string.lab_enabled_summary),
                        checked = settings.enabled,
                        highlightKey = searchKey,
                        onCheckedChange = { onIntent(LabConfigIntent.SetEnabled(it)) },
                    )
                }
            }
            item {
                AnimatedVisibility(visible = settings.enabled) {
                    SplicedColumnGroup(title = stringResource(R.string.lab_display)) {
                        SwitchSettingItem(
                            title = stringResource(R.string.lab_eink_display_title),
                            description = stringResource(R.string.lab_eink_display_summary),
                            checked = settings.eInkDisplay,
                            highlightKey = searchKey,
                            onCheckedChange = { onIntent(LabConfigIntent.SetEInkDisplay(it)) },
                        )
                    }
                }
            }
            item {
                SplicedColumnGroup(title = stringResource(R.string.lab_diagnostics)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.lab_page_estimate_diagnostics_title),
                        description = stringResource(
                            R.string.lab_page_estimate_diagnostics_summary
                        ),
                        option = stringResource(
                            R.string.lab_page_estimate_diagnostics_count,
                            state.pageEstimateDiagnosticCount,
                        ),
                        highlightKey = searchKey,
                        onClick = {
                            // On original it might have been different
                        },
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.lab_page_estimate_diagnostics_share_title),
                        highlightKey = searchKey,
                        onClick = { onIntent(LabConfigIntent.ExportPageEstimateDiagnostics) },
                    )
                }
            }
        }
    }
}
