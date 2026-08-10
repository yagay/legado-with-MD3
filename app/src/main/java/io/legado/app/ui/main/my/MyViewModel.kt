package io.legado.app.ui.main.my

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.EventBus
import io.legado.app.service.WebService
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.ui.main.MainRoute
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.main.MainRouteSettingsOther
import io.legado.app.ui.main.MainRouteSettingsRead
import io.legado.app.ui.main.MainRouteSettingsCover
import io.legado.app.ui.main.MainRouteSettingsTheme
import io.legado.app.ui.main.MainRouteSettingsBackup
import io.legado.app.ui.main.MainRouteSettingsCustomConfig
import io.legado.app.ui.main.MainRouteSettingsAi
import io.legado.app.ui.main.MainRouteSettingsDownloadCache
import io.legado.app.ui.main.MainRouteSettingsTranslation
import io.legado.app.ui.main.MainRouteSettingsLabConfig
import io.legado.app.ui.main.MainRouteAiChat
import io.legado.app.ui.main.MainRouteBookSourceManage
import io.legado.app.ui.main.MainRouteBookCacheManage
import io.legado.app.ui.main.MainRouteReadRecord
import io.legado.app.ui.main.MainRouteAbout
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.replace.ReplaceRuleActivity
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
data class MyUiState(
    val isWebServiceRun: Boolean = false,
    val webServiceAddress: String = "",
    val isSearch: Boolean = false,
    val searchKey: String = "",
    val searchResults: ImmutableList<SettingSearchResult> = persistentListOf()
)

@Stable
data class SettingSearchResult(
    val title: String,
    val description: String? = null,
    val action: SettingAction
)

sealed interface SettingAction {
    data class Navigate(val route: MainRoute) : SettingAction
    data class Activity(val destination: Class<*>) : SettingAction
    data class Event(val event: PrefClickEvent) : SettingAction
}

sealed class PrefClickEvent {
    data class OpenUrl(val url: String) : PrefClickEvent()
    data class CopyUrl(val url: String) : PrefClickEvent()
    data class ShowMd(val title: String, val path: String) : PrefClickEvent()
    data class StartActivity(val destination: Class<*>, val configTag: String? = null) : PrefClickEvent()
    object OpenReadRecord : PrefClickEvent()
    object OpenBookCacheManage : PrefClickEvent()
    object OpenBookSourceManage : PrefClickEvent()
    object OpenHighlightTagRule : PrefClickEvent()
    object OpenAbout : PrefClickEvent()
    object ToggleWebService : PrefClickEvent()
    object ExitApp : PrefClickEvent()
}

sealed interface MyIntent {
    data object ToggleWebService : MyIntent
    data class SetSearchMode(val isSearch: Boolean) : MyIntent
    data class SetSearchQuery(val query: String) : MyIntent
}

sealed interface MyEffect

class MyViewModel(
    application: Application
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(
        MyUiState(
            isWebServiceRun = WebService.isRun,
            webServiceAddress = WebService.hostAddress
        )
    )
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<MyEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val settingRegistry by lazy {
        listOf(
            SettingSearchResult(context.getString(R.string.book_source_manage), context.getString(R.string.book_source_manage_desc), SettingAction.Event(PrefClickEvent.OpenBookSourceManage)),
            SettingSearchResult(context.getString(R.string.replace_purify), context.getString(R.string.replace_purify_desc), SettingAction.Activity(ReplaceRuleActivity::class.java)),
            SettingSearchResult(context.getString(R.string.txt_toc_rule), null, SettingAction.Activity(TxtTocRuleActivity::class.java)),
            SettingSearchResult(context.getString(R.string.dict_rule), null, SettingAction.Activity(DictRuleActivity::class.java)),
            SettingSearchResult(context.getString(R.string.highlight_tag_config), null, SettingAction.Event(PrefClickEvent.OpenHighlightTagRule)),
            SettingSearchResult(context.getString(R.string.ai_chat), null, SettingAction.Navigate(MainRouteAiChat)),
            SettingSearchResult(context.getString(R.string.setting), context.getString(R.string.other), SettingAction.Navigate(MainRouteSettingsOther)),
            SettingSearchResult(context.getString(R.string.read_config), null, SettingAction.Navigate(MainRouteSettingsRead)),
            SettingSearchResult(context.getString(R.string.cover_config), null, SettingAction.Navigate(MainRouteSettingsCover)),
            SettingSearchResult(context.getString(R.string.theme_setting), context.getString(R.string.theme_setting_s), SettingAction.Navigate(MainRouteSettingsTheme)),
            SettingSearchResult(context.getString(R.string.backup_restore), null, SettingAction.Navigate(MainRouteSettingsBackup)),
            SettingSearchResult(context.getString(R.string.ai_config), null, SettingAction.Navigate(MainRouteSettingsAi)),
            SettingSearchResult(context.getString(R.string.custom_theme), null, SettingAction.Navigate(MainRouteSettingsCustomConfig)),
            SettingSearchResult(context.getString(R.string.translation_config), null, SettingAction.Navigate(MainRouteSettingsTranslation)),
            SettingSearchResult(context.getString(R.string.download_cache_config), null, SettingAction.Navigate(MainRouteSettingsDownloadCache)),
            SettingSearchResult(context.getString(R.string.lab_setting), null, SettingAction.Navigate(MainRouteSettingsLabConfig)),
            SettingSearchResult(context.getString(R.string.bookmark), null, SettingAction.Activity(AllBookmarkActivity::class.java)),
            SettingSearchResult(context.getString(R.string.read_record), null, SettingAction.Event(PrefClickEvent.OpenReadRecord)),
            SettingSearchResult(context.getString(R.string.cache_management), null, SettingAction.Event(PrefClickEvent.OpenBookCacheManage)),
            SettingSearchResult(context.getString(R.string.file_manage), null, SettingAction.Activity(FileManageActivity::class.java)),
            SettingSearchResult(context.getString(R.string.about), null, SettingAction.Event(PrefClickEvent.OpenAbout)),
        )
    }

    init {
        viewModelScope.launch {
            FlowEventBus.with<String>(EventBus.WEB_SERVICE)
                .collect { address ->
                    _uiState.update { state ->
                        state.copy(
                            isWebServiceRun = address.isNotEmpty(),
                            webServiceAddress = address
                        )
                    }
                }
        }
    }

    fun onIntent(intent: MyIntent) {
        when (intent) {
            MyIntent.ToggleWebService -> {
                val currentIsRun = _uiState.value.isWebServiceRun

                if (!currentIsRun) {
                    WebService.start(context)
                } else {
                    WebService.stop(context)
                    _uiState.update { it.copy(isWebServiceRun = false, webServiceAddress = "") }
                }

            }

            is MyIntent.SetSearchMode -> _uiState.update {
                it.copy(
                    isSearch = intent.isSearch,
                    searchKey = if (intent.isSearch) it.searchKey else "",
                    searchResults = if (intent.isSearch) it.searchResults else persistentListOf()
                )
            }

            is MyIntent.SetSearchQuery -> {
                _uiState.update { it.copy(searchKey = intent.query) }
                performSearch(intent.query)
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = persistentListOf()) }
            return
        }
        val results = settingRegistry.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.description?.contains(query, ignoreCase = true) == true
        }.toImmutableList()
        _uiState.update { it.copy(searchResults = results) }
    }

}
