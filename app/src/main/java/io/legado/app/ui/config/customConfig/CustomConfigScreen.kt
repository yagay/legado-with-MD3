package io.legado.app.ui.config.customConfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.DynamicTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomConfigScreen(
    viewModel: CustomConfigViewModel = koinViewModel(),
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        topBar = {
            DynamicTopAppBar(
                title = "自定义配置",
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
                state = object : io.legado.app.ui.widget.components.list.ListUiState<Nothing> {
                    override val items: List<Nothing> = emptyList()
                    override val selectedIds: Set<Any> = emptySet()
                    override val searchKey: String = ""
                    override val isSearch: Boolean = false
                    override val isLoading: Boolean = false
                },
                onSearchToggle = {},
                onSearchQueryChange = {},
                onClearSelection = {},
                searchPlaceholder = ""
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            PillHeaderDivider(title = "发现")
            ClickableSettingItem(
                title = "发现页默认布局",
                description = if (settings.discoveryLayoutMode == 1) "瀑布流" else "列表",
                onClick = {
                    viewModel.onIntent(
                        CustomConfigIntent.SetDiscoveryLayoutMode(
                            if (settings.discoveryLayoutMode == 1) 0 else 1
                        )
                    )
                }
            )

            PillHeaderDivider(title = "备份与恢复 (增强)")
            SwitchSettingItem(
                title = "切到后台自动备份",
                description = "应用切到后台后等待指定时间自动执行备份",
                checked = settings.autoBackupOnBackground,
                onCheckedChange = {
                    viewModel.onIntent(CustomConfigIntent.SetAutoBackupOnBackground(it))
                },
            )

            if (settings.autoBackupOnBackground) {
                InputSettingItem(
                    title = "自动备份等待时长（分钟）",
                    value = settings.autoBackupOnBackgroundIntervalMinutes.toString(),
                    defaultValue = "1",
                    onConfirm = {
                        viewModel.onIntent(
                            CustomConfigIntent.SetAutoBackupOnBackgroundInterval(
                                it.toIntOrNull() ?: 1
                            )
                        )
                    },
                )
            }

            SwitchSettingItem(
                title = "备份时导出书籍",
                description = "执行备份时同步导出所有已缓存书籍到 WebDAV",
                checked = settings.autoExportBooksOnBackup,
                onCheckedChange = {
                    viewModel.onIntent(CustomConfigIntent.SetAutoExportBooksOnBackup(it))
                }
            )

            SwitchSettingItem(
                title = "恢复时导入书籍",
                description = "扫描 WebDAV 中的缓存书籍文件并导入所有书籍",
                checked = settings.autoImportBooksOnRestore,
                onCheckedChange = {
                    viewModel.onIntent(CustomConfigIntent.SetAutoImportBooksOnRestore(it))
                }
            )

            ClickableSettingItem(
                title = "一键导出书籍到 WebDAV",
                description = "将书架中所有已缓存章节的书籍上传至默认 WebDAV",
                onClick = { viewModel.onIntent(CustomConfigIntent.ExportAllToWebDav) }
            )

            ClickableSettingItem(
                title = "一键从 WebDAV 导入书籍",
                description = "扫描 WebDAV 中的书籍文件并导入所有书籍",
                onClick = { viewModel.onIntent(CustomConfigIntent.ImportAllFromWebDav) }
            )
        }
    }
}
