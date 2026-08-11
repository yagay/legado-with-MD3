package io.legado.app.ui.config.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiConfigRouteScreen(
    onBackClick: () -> Unit,
    onNavigateToProviderEdit: (providerId: String?) -> Unit,
    onNavigateToModelEdit: (providerId: String?, modelProfileId: String?) -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAiSummary: () -> Unit,
    onNavigateToAiPrompt: () -> Unit,
    searchKey: String? = null,
    viewModel: AiConfigViewModel = koinViewModel()
) {
    AiConfigScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onNavigateToProviderEdit = onNavigateToProviderEdit,
        onNavigateToModelEdit = onNavigateToModelEdit,
        onNavigateToTranslation = onNavigateToTranslation,
        onNavigateToAiSummary = onNavigateToAiSummary,
        onNavigateToAiPrompt = onNavigateToAiPrompt,
        searchKey = searchKey
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigScreen(
    state: AiConfigUiState,
    effects: Flow<AiConfigEffect>,
    onIntent: (AiConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToProviderEdit: (providerId: String?) -> Unit,
    onNavigateToModelEdit: (providerId: String?, modelProfileId: String?) -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAiSummary: () -> Unit,
    onNavigateToAiPrompt: () -> Unit,
    searchKey: String? = null,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var showModelSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val scrollInfo = remember(searchKey) {
        getSettingScrollInfo(context, SettingDestination.Ai, searchKey)
    }
    LaunchSettingScrollEffect(scrollInfo, listState)

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiConfigEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.ai_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_provider_database)) {
                    state.providers.forEach { provider ->
                        ClickableSettingItem(
                            title = provider.providerName,
                            description = provider.baseUrl,
                            option = "${provider.protocol} / ${provider.modelCount}",
                            highlightKey = searchKey,
                            onClick = { onNavigateToProviderEdit(provider.providerId) }
                        )
                    }
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_new_provider),
                        highlightKey = searchKey,
                        onClick = { onNavigateToProviderEdit(null) }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_model_database)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_current_model),
                        description = state.currentModelName.ifBlank { stringResource(R.string.ai_model_not_configured) },
                        highlightKey = searchKey,
                        onClick = {
                            if (state.models.isEmpty()) {
                                onNavigateToProviderEdit(null)
                            } else {
                                showModelSheet = true
                            }
                        }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_tasks)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.translation_config),
                        highlightKey = searchKey,
                        onClick = onNavigateToTranslation
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_chapter_summary),
                        highlightKey = searchKey,
                        onClick = onNavigateToAiSummary
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_prompt_config),
                        description = stringResource(R.string.ai_prompt_config_desc),
                        highlightKey = searchKey,
                        onClick = onNavigateToAiPrompt
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_skills)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_new_skill),
                        highlightKey = searchKey,
                        onClick = {}
                    )
                }
            }
        }
    }

    AppModalBottomSheet(
        show = showModelSheet,
        onDismissRequest = { showModelSheet = false },
        title = stringResource(R.string.ai_select_model)
    ) {
        LazyColumn {
            state.providers.forEach { provider ->
                item {
                    SplicedColumnGroup(title = provider.providerName) {
                        if (provider.models.isEmpty()) {
                            ClickableSettingItem(
                                title = stringResource(R.string.ai_no_models_imported),
                                description = stringResource(R.string.ai_fetch_and_save_models),
                                onClick = {
                                    showModelSheet = false
                                    onNavigateToProviderEdit(provider.providerId)
                                }
                            )
                        } else {
                            provider.models.forEach { model ->
                                ClickableSettingItem(
                                    title = model.modelName,
                                    description = model.modelId,
                                    trailingContent = if (model.isCurrent) {
                                        { TextCard(text = stringResource(R.string.ai_current)) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        onIntent(AiConfigIntent.SetDefaultModel(model.modelProfileId))
                                        showModelSheet = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
