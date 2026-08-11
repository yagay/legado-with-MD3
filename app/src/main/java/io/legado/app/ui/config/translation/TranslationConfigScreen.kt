package io.legado.app.ui.config.translation

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
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.enhance.settingssearch.SettingDestination
import io.legado.app.enhance.settingssearch.getSettingScrollInfo
import io.legado.app.enhance.ui.LaunchSettingScrollEffect
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.androidx.compose.koinViewModel

@Composable
fun TranslationConfigRouteScreen(
    onBackClick: () -> Unit,
    onNavigateToAi: () -> Unit,
    searchKey: String? = null,
    viewModel: TranslationConfigViewModel = koinViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    TranslationConfigScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onNavigateToAi = onNavigateToAi,
        searchKey = searchKey,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationConfigScreen(
    state: TranslationConfigUiState,
    onIntent: (TranslationConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToAi: () -> Unit,
    searchKey: String? = null,
) {
    val settings = state.settings
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val scrollInfo = remember(searchKey) {
        getSettingScrollInfo(context, SettingDestination.Translation, searchKey)
    }
    LaunchSettingScrollEffect(scrollInfo, listState)

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.translation_config),
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
                SplicedColumnGroup(title = stringResource(R.string.translation_provider)) {
                    DropdownListSettingItem(
                        title = stringResource(R.string.llm_provider),
                        selectedValue = settings.provider,
                        displayEntries = TranslationConstants.providerDisplayNames.toTypedArray(),
                        entryValues = TranslationConstants.providerValues.toTypedArray(),
                        highlightKey = searchKey,
                        onValueChange = { onIntent(TranslationConfigIntent.SetProvider(it)) },
                    )
                }
            }
            item {
                SplicedColumnGroup(title = stringResource(R.string.translation_options)) {
                    DropdownListSettingItem(
                        title = stringResource(R.string.llm_target_language),
                        selectedValue = settings.targetLanguage,
                        displayEntries = TranslationConstants.targetLanguages.map { it.second }.toTypedArray(),
                        entryValues = TranslationConstants.targetLanguages.map { it.first }.toTypedArray(),
                        highlightKey = searchKey,
                        onValueChange = { onIntent(TranslationConfigIntent.SetTargetLanguage(it)) },
                    )
                    SliderSettingItem(
                        title = stringResource(R.string.llm_max_chars_per_chunk),
                        value = settings.maxCharsPerChunk.toFloat(),
                        defaultValue = 10000f,
                        valueRange = 1000f..10000f,
                        steps = 17,
                        highlightKey = searchKey,
                        onValueChange = {
                            onIntent(TranslationConfigIntent.SetMaxCharsPerChunk(it.toInt()))
                        },
                    )
                }
            }
        }
    }
}
