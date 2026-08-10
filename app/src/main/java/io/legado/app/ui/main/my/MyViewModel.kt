package io.legado.app.ui.main.my

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.EventBus
import io.legado.app.service.WebService
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.main.MainRoute
import io.legado.app.ui.main.MainRouteAbout
import io.legado.app.ui.main.MainRouteAiChat
import io.legado.app.ui.main.MainRouteBookCacheManage
import io.legado.app.ui.main.MainRouteBookSourceManage
import io.legado.app.ui.main.MainRouteReadRecord
import io.legado.app.ui.main.MainRouteSettingsAi
import io.legado.app.ui.main.MainRouteSettingsBackup
import io.legado.app.ui.main.MainRouteSettingsCover
import io.legado.app.ui.main.MainRouteSettingsCustomConfig
import io.legado.app.ui.main.MainRouteSettingsDownloadCache
import io.legado.app.ui.main.MainRouteSettingsLabConfig
import io.legado.app.ui.main.MainRouteSettingsOther
import io.legado.app.ui.main.MainRouteSettingsRead
import io.legado.app.ui.main.MainRouteSettingsTheme
import io.legado.app.ui.main.MainRouteSettingsTranslation
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.utils.eventBus.FlowEventBus
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
    override val isSearch: Boolean = false,
    val searchResults: List<SettingSearchResult> = emptyList(),
    override val items: List<SettingSearchResult> = emptyList(),
    override val selectedIds: Set<Any> = emptySet(),
    override val searchKey: String = "",
    override val isLoading: Boolean = false,
) : ListUiState<SettingSearchResult>

data class SettingSearchResult(
    val title: String,
    val description: String? = null,
    val path: String,
    val action: SettingAction,
)

sealed interface SettingAction {
    data class Navigate(val route: MainRoute) : SettingAction
    data class ClickEvent(val event: PrefClickEvent) : SettingAction
    data object OpenSettings : SettingAction
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
    data class SearchQueryChanged(val query: String) : MyIntent
    data class ToggleSearch(val enabled: Boolean) : MyIntent
}

sealed interface MyEffect

class MyViewModel(
    application: Application,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(
        MyUiState(
            isWebServiceRun = WebService.isRun,
            webServiceAddress = WebService.hostAddress,
        )
    )
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<MyEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            FlowEventBus.with<String>(EventBus.WEB_SERVICE)
                .collect { address ->
                    _uiState.update { state ->
                        state.copy(
                            isWebServiceRun = address.isNotEmpty(),
                            webServiceAddress = address,
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

            is MyIntent.SearchQueryChanged -> {
                _uiState.update { it.copy(searchKey = intent.query) }
                performSearch(intent.query)
            }

            is MyIntent.ToggleSearch -> {
                _uiState.update {
                    it.copy(
                        isSearch = intent.enabled,
                        searchKey = if (!intent.enabled) "" else it.searchKey,
                        searchResults = if (!intent.enabled) emptyList() else it.searchResults,
                    )
                }
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        // Keep the uploaded-source behavior: path is display-only and does not expand matching.
        val results = settingRegistry.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.description?.contains(query, ignoreCase = true) == true
        }
        _uiState.update { it.copy(searchResults = results) }
    }

    private val settingRegistry by lazy {
        listOf(
            SettingSearchResult("书源管理", "管理、导入、导出、校验书源", "我的 > 规则 > 书源管理", SettingAction.Navigate(MainRouteBookSourceManage)),
            SettingSearchResult("替换净化", "阅读界面净化规则、内容替换", "我的 > 规则 > 替换净化", SettingAction.ClickEvent(PrefClickEvent.StartActivity(ReplaceRuleActivity::class.java))),
            SettingSearchResult("文本卷名", "TXT 书籍目录解析规则", "我的 > 规则 > 文本卷名", SettingAction.ClickEvent(PrefClickEvent.StartActivity(TxtTocRuleActivity::class.java))),
            SettingSearchResult("字典规则", "查字典、分词搜索规则", "我的 > 规则 > 字典规则", SettingAction.ClickEvent(PrefClickEvent.StartActivity(DictRuleActivity::class.java))),
            SettingSearchResult("AI 聊天", "与书源关联的 AI 智能对话", "我的 > 其他 > AI 聊天", SettingAction.Navigate(MainRouteAiChat)),
            SettingSearchResult("系统设置", "应用偏好设置总入口", "我的 > 其他 > 设置", SettingAction.OpenSettings),
            SettingSearchResult("书签管理", "查看所有书籍的个人书签", "我的 > 其他 > 书签管理", SettingAction.ClickEvent(PrefClickEvent.StartActivity(AllBookmarkActivity::class.java))),
            SettingSearchResult("阅读记录", "阅读历史、统计、时长报告", "我的 > 其他 > 阅读记录", SettingAction.Navigate(MainRouteReadRecord)),
            SettingSearchResult("离线缓存", "管理下载的章节数据、手动清理缓存", "我的 > 其他 > 离线缓存", SettingAction.Navigate(MainRouteBookCacheManage)),
            SettingSearchResult("文件管理", "本地书籍导入、系统文件浏览", "我的 > 其他 > 文件管理", SettingAction.ClickEvent(PrefClickEvent.StartActivity(FileManageActivity::class.java))),
            SettingSearchResult("退出应用", "完全关闭程序并停止服务", "我的 > 其他 > 退出应用", SettingAction.ClickEvent(PrefClickEvent.ExitApp)),
            SettingSearchResult("关于应用", "版本信息、更新检查、鸣谢", "我的 > 其他 > 关于应用", SettingAction.Navigate(MainRouteAbout)),

            SettingSearchResult("主题设置", "换色、调色盘、Material 3 风格", "设置 > 主题设置", SettingAction.Navigate(MainRouteSettingsTheme)),
            SettingSearchResult("其他设置", "语言、自启、搜索偏好、更新通道", "设置 > 其他设置", SettingAction.Navigate(MainRouteSettingsOther)),
            SettingSearchResult("阅读设置", "字号、间距、翻页动画、净化开关", "设置 > 阅读设置", SettingAction.Navigate(MainRouteSettingsRead)),
            SettingSearchResult("封面设置", "默认封面、Wi-Fi 下载封面、阴影边框", "设置 > 封面设置", SettingAction.Navigate(MainRouteSettingsCover)),
            SettingSearchResult("下载缓存设置", "下载线程数、预下载数量、网络代理", "设置 > 下载缓存设置", SettingAction.Navigate(MainRouteSettingsDownloadCache)),
            SettingSearchResult("备份与恢复", "本地备份、手动 WebDAV 同步、恢复忽略项", "设置 > 备份与恢复", SettingAction.Navigate(MainRouteSettingsBackup)),
            SettingSearchResult("自定义配置", "增强功能、发现页引擎、后台备份、智能导出", "设置 > 自定义配置", SettingAction.Navigate(MainRouteSettingsCustomConfig)),
            SettingSearchResult("AI 配置", "AI 提供商管理、模型参数、提示词配置", "设置 > AI 配置", SettingAction.Navigate(MainRouteSettingsAi)),
            SettingSearchResult("翻译设置", "全文翻译配置、目标语言、百度/谷歌翻译", "设置 > 翻译设置", SettingAction.Navigate(MainRouteSettingsTranslation)),
            SettingSearchResult("实验室", "实验性功能、开发者调试工具", "设置 > 实验室", SettingAction.Navigate(MainRouteSettingsLabConfig)),

            SettingSearchResult("发现页默认布局", "选择列表或瀑布流作为发现页默认布局", "设置 > 自定义配置 > 发现 > 发现页默认布局", SettingAction.Navigate(MainRouteSettingsCustomConfig)),
            SettingSearchResult("后台自动备份", "应用切到后台时静默执行备份", "设置 > 自定义配置 > 备份与恢复（增强） > 后台自动备份", SettingAction.Navigate(MainRouteSettingsCustomConfig)),
            SettingSearchResult("自动备份等待时长", "配置应用处于后台多久后开始备份", "设置 > 自定义配置 > 备份与恢复（增强） > 自动备份等待时长", SettingAction.Navigate(MainRouteSettingsCustomConfig)),
            SettingSearchResult("备份时导出书籍", "自动将缓存同步到 WebDAV", "设置 > 自定义配置 > 备份与恢复（增强） > 备份时导出书籍", SettingAction.Navigate(MainRouteSettingsCustomConfig)),
            SettingSearchResult("恢复时导入书籍", "恢复时扫描 WebDAV 中的缓存书籍并导入", "设置 > 自定义配置 > 备份与恢复（增强） > 恢复时导入书籍", SettingAction.Navigate(MainRouteSettingsCustomConfig)),
            SettingSearchResult("一键导出书籍到 WebDAV", "手动强制全量同步书籍文件", "设置 > 自定义配置 > 备份与恢复（增强） > 一键导出书籍到 WebDAV", SettingAction.Navigate(MainRouteSettingsCustomConfig)),
            SettingSearchResult("一键从 WebDAV 导入书籍", "从云端批量找回书籍文件", "设置 > 自定义配置 > 备份与恢复（增强） > 一键从 WebDAV 导入书籍", SettingAction.Navigate(MainRouteSettingsCustomConfig)),
            SettingSearchResult("WebDAV 设置", "配置坚果云等云盘同步账号", "设置 > 备份与恢复 > WebDAV 设置", SettingAction.Navigate(MainRouteSettingsBackup)),
            SettingSearchResult("同步书籍进度", "阅读进度云端自动同步", "设置 > 备份与恢复 > 同步书籍进度", SettingAction.Navigate(MainRouteSettingsBackup)),
            SettingSearchResult("翻页动画", "覆盖翻页、平滑滑动、无动画设置", "设置 > 阅读设置 > 翻页动画", SettingAction.Navigate(MainRouteSettingsRead)),
            SettingSearchResult("自动更新", "启动时自动检查新版本", "设置 > 其他设置 > 自动更新", SettingAction.Navigate(MainRouteSettingsOther)),
            SettingSearchResult("全屏阅读", "隐藏系统状态栏与导航栏开关", "设置 > 阅读设置 > 全屏阅读", SettingAction.Navigate(MainRouteSettingsRead)),
        )
    }
}
