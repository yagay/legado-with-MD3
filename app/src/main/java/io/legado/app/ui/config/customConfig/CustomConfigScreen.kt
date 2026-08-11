package io.legado.app.ui.config.customConfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.topbar.DynamicTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomConfigScreen(
    viewModel: CustomConfigViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    searchKey: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val scrollState = rememberScrollState()

    AppScaffold(
        topBar = {
            DynamicTopAppBar(
                title = stringResource(R.string.custom_config_title),
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
                .verticalScroll(scrollState)
        ) {
            PillHeaderDivider(title = stringResource(R.string.enhanced_backup_restore))

            SwitchSettingItem(
                title = stringResource(R.string.backup_export_books),
                description = "执行备份时同步导出所有已缓存书籍到 WebDAV",
                checked = uiState.settings.autoExportBooksOnBackup,
                highlightKey = searchKey,
                onCheckedChange = { viewModel.onIntent(CustomConfigIntent.SetAutoExportBooksOnBackup(it)) }
            )

            SwitchSettingItem(
                title = stringResource(R.string.restore_import_books),
                description = "扫描 WebDav 中的缓存书籍文件并导入所有书籍",
                checked = uiState.settings.autoImportBooksOnRestore,
                highlightKey = searchKey,
                onCheckedChange = { viewModel.onIntent(CustomConfigIntent.SetAutoImportBooksOnRestore(it)) }
            )

            DropdownListSettingItem(
                title = stringResource(R.string.export_group_title),
                description = stringResource(R.string.export_group_summary),
                selectedValue = uiState.settings.exportGroupMask.toString(),
                displayEntries = uiState.bookGroups.map { it.first }.toTypedArray(),
                entryValues = uiState.bookGroups.map { it.second.toString() }.toTypedArray(),
                highlightKey = searchKey,
                onValueChange = { viewModel.onIntent(CustomConfigIntent.SetExportGroupMask(it.toLong())) }
            )

            ClickableSettingItem(
                title = stringResource(R.string.export_books_to_webdav),
                description = "将书架中所有已缓存章节的书籍上传至默认 WebDAV",
                highlightKey = searchKey,
                onClick = { viewModel.onIntent(CustomConfigIntent.ExportAllToWebDav) }
            )

            ClickableSettingItem(
                title = stringResource(R.string.import_books_from_webdav),
                description = "扫描 WebDav 中的书籍文件并导入所有书籍",
                highlightKey = searchKey,
                onClick = { viewModel.onIntent(CustomConfigIntent.ImportAllFromWebDav) }
            )
        }
    }
}
