package io.legado.app.ui.main.my

import android.app.Application
import androidx.annotation.StringRes
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

private data class SettingSearchNode(
    val title: String,
    val description: String? = null,
    val action: SettingAction? = null,
    val children: List<SettingSearchNode> = emptyList()
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
    data class NavigateToRoute(val route: MainRoute) : PrefClickEvent()
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

    private val settingTree by lazy {
        listOf(
            searchNode(R.string.book_source_manage, R.string.book_source_manage_desc, SettingAction.Event(PrefClickEvent.OpenBookSourceManage)),
            searchNode(R.string.replace_purify, R.string.replace_purify_desc, SettingAction.Activity(ReplaceRuleActivity::class.java)),
            searchNode(R.string.txt_toc_rule, action = SettingAction.Activity(TxtTocRuleActivity::class.java)),
            searchNode(R.string.dict_rule, action = SettingAction.Activity(DictRuleActivity::class.java)),
            searchNode(R.string.highlight_tag_config, action = SettingAction.Event(PrefClickEvent.OpenHighlightTagRule)),
            searchNode(R.string.ai_chat, action = SettingAction.Navigate(MainRouteAiChat)),
            settingPage(
                R.string.setting, MainRouteSettingsOther,
                R.string.language, R.string.auto_check_update_on_start_title,
                R.string.web_port_title, R.string.web_service_auto_start,
                R.string.web_service_wake_lock, R.string.set_local_password,
                R.string.background_permission, R.string.notification_permission,
                R.string.book_tree_uri_t, R.string.source_edit_text_max_line,
                R.string.show_add_to_shelf_alert_title, R.string.auto_clear_expired,
                R.string.clear_webview_data, R.string.direct_link_upload_rule,
                R.string.read_aloud_by_media_button_title, R.string.ignore_audio_focus_title,
                R.string.media_button_on_exit_title, R.string.replace_enable_default_t,
                R.string.record_debug_log, R.string.record_heap_dump_t,
                R.string.show_manga_ui, R.string.privacy
            ),
            settingPage(
                R.string.read_config, MainRouteSettingsRead,
                R.string.screen_settings, R.string.screen_direction, R.string.keep_light,
                R.string.pt_hide_status_bar, R.string.pt_hide_navigation_bar,
                R.string.padding_display_cutouts, R.string.title_bar_mode,
                R.string.menu_alpha, R.string.read_body_to_lh, R.string.text_full_justify,
                R.string.text_bottom_justify, R.string.adapt_special_style,
                R.string.use_zh_layout, R.string.eye_protection, R.string.show_brightness_view,
                R.string.brightness_bar_position, R.string.use_underline, R.string.page_control,
                R.string.read_slider_mode, R.string.double_page_horizontal,
                R.string.progress_bar_behavior, R.string.mouse_wheel_page,
                R.string.volume_key_page, R.string.volume_key_page_on_play,
                R.string.key_page_on_long_press, R.string.page_touch_slop_title,
                R.string.enable_select_vibrator, R.string.enable_slider_vibrator,
                R.string.use_new_toc_sheet, R.string.auto_change_source,
                R.string.auto_switch_theme_reminder_title, R.string.selectText,
                R.string.no_anim_scroll_page, R.string.click_image_way,
                R.string.enable_optimize_render, R.string.click_regional_config,
                R.string.custom_page_key, R.string.disable_return_key,
                R.string.show_read_title_addition, R.string.show_menu_icon
            ),
            settingPage(
                R.string.cover_config, MainRouteSettingsCover,
                R.string.only_wifi, R.string.cover_rule, R.string.default_cover,
                R.string.use_default_cover, R.string.cover_show_shadow,
                R.string.cover_show_stroke, R.string.default_color,
                R.string.cover_info_orientation, R.string.network_book_badge_setting,
                R.string.text_color, R.string.text_shadow_color,
                R.string.cover_show_name, R.string.cover_show_author,
                R.string.cover_album_day_night_count
            ),
            settingPage(
                R.string.theme_setting, MainRouteSettingsTheme,
                R.string.theme_mode, R.string.dark_mode, R.string.pure_black,
                R.string.theme_pack, R.string.custom_theme_colors, R.string.background_image,
                R.string.font_setting, R.string.font_scale, R.string.tabletInterface,
                R.string.main_navigation_settings, R.string.top_bottom_bar_settings,
                R.string.change_icon, R.string.show_status, R.string.show_divider_line,
                R.string.blur_effects, R.string.is_blur_enable,
                R.string.is_blur_progressive_enable, R.string.color_temperature,
                R.string.eye_protection_enabled, R.string.eye_protection_schedule,
                R.string.eye_protection_start_time, R.string.eye_protection_end_time,
                R.string.book_info_page, R.string.book_info_follow_cover_color,
                R.string.base_card_corner_radius, R.string.border_width,
                R.string.container_opacity, R.string.predictive_back,
                R.string.show_swipe_animation, R.string.compose_engine
            ),
            settingPage(
                R.string.backup_restore, MainRouteSettingsBackup,
                R.string.backup, R.string.restore, R.string.backup_path,
                R.string.select_backup_path, R.string.select_restore_file,
                R.string.web_dav_set, R.string.web_dav_url, R.string.web_dav_account,
                R.string.web_dav_pw, R.string.webdav_device_name, R.string.sub_dir,
                R.string.backup_sync_mode, R.string.sync_book_progress_t,
                R.string.sync_book_progress_plus_t, R.string.auto_check_new_backup_t,
                R.string.only_latest_backup_t, R.string.backup_ignore,
                R.string.restore_ignore, R.string.menu_import_old_version
            ),
            settingPage(
                R.string.ai_config, MainRouteSettingsAi,
                R.string.ai_current_model, R.string.ai_select_model,
                R.string.ai_provider_database, R.string.ai_model_database,
                R.string.ai_new_provider, R.string.ai_fetch_and_save_models,
                R.string.ai_prompt_config, R.string.ai_skills, R.string.ai_tasks,
                R.string.ai_chapter_summary, R.string.translation_config
            ),
            settingPage(
                R.string.custom_theme, MainRouteSettingsCustomConfig,
                R.string.auto_backup_on_background,
                R.string.auto_backup_on_background_interval
            ),
            settingPage(
                R.string.translation_config, MainRouteSettingsTranslation,
                R.string.translation_provider, R.string.translation_options,
                R.string.llm_provider, R.string.llm_target_language,
                R.string.llm_max_chars_per_chunk, R.string.translation_app_ai_provider
            ),
            settingPage(
                R.string.download_cache_config, MainRouteSettingsDownloadCache,
                R.string.download_setting, R.string.cache_book_threads_num_title,
                R.string.pre_download, R.string.network, R.string.user_agent,
                R.string.http_cache, R.string.image_cache, R.string.cover_cache,
                R.string.manga_cache, R.string.bitmap_cache_size,
                R.string.image_retain_number, R.string.clear_cache,
                R.string.shrink_database
            ),
            settingPage(
                R.string.lab_setting, MainRouteSettingsLabConfig,
                R.string.lab_enabled_title, R.string.lab_display,
                R.string.lab_eink_display_title, R.string.lab_diagnostics,
                R.string.lab_page_estimate_diagnostics_title,
                R.string.lab_page_estimate_diagnostics_share_title
            ),
            searchNode(R.string.bookmark, action = SettingAction.Activity(AllBookmarkActivity::class.java)),
            searchNode(R.string.read_record, action = SettingAction.Event(PrefClickEvent.OpenReadRecord)),
            searchNode(R.string.cache_management, action = SettingAction.Event(PrefClickEvent.OpenBookCacheManage)),
            searchNode(R.string.file_manage, action = SettingAction.Activity(FileManageActivity::class.java)),
            searchNode(R.string.about, action = SettingAction.Event(PrefClickEvent.OpenAbout)),
        )
    }

    private fun searchNode(
        @StringRes titleRes: Int,
        @StringRes descriptionRes: Int? = null,
        action: SettingAction? = null,
        children: List<SettingSearchNode> = emptyList()
    ) = SettingSearchNode(
        title = context.getString(titleRes),
        description = descriptionRes?.let(context::getString),
        action = action,
        children = children
    )

    private fun settingPage(
        @StringRes titleRes: Int,
        route: MainRoute,
        vararg childTitleRes: Int
    ): SettingSearchNode = searchNode(
        titleRes = titleRes,
        action = SettingAction.Navigate(route),
        children = childTitleRes.distinct().map { searchNode(it) }
    )

    private fun SettingSearchNode.toSearchResults(
        parentAction: SettingAction? = null,
        parentPath: List<String> = emptyList()
    ): List<SettingSearchResult> {
        val targetAction = action ?: parentAction
        val path = parentPath + title
        val self = targetAction?.let {
            listOf(
                SettingSearchResult(
                    title = title,
                    description = if (parentPath.isEmpty()) description
                    else path.joinToString(" > "),
                    action = it
                )
            )
        }.orEmpty()
        return self + children.flatMap { it.toSearchResults(targetAction, path) }
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
        val results = settingTree
            .flatMap { it.toSearchResults() }
            .filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.description?.contains(query, ignoreCase = true) == true
            }
            .distinctBy { Triple(it.title, it.description, it.action) }
            .toImmutableList()
        _uiState.update { it.copy(searchResults = results) }
    }

}
