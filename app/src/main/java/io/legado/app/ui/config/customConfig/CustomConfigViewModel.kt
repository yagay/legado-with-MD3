package io.legado.app.ui.config.customConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.CustomSettingsGateway
import io.legado.app.domain.model.settings.BackupSettings
import io.legado.app.domain.model.settings.CustomSettings
import io.legado.app.help.storage.Backup
import io.legado.app.data.repository.BookGroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CustomConfigViewModel(
    private val settingsGateway: CustomSettingsGateway,
    private val backupSettingsGateway: BackupSettingsGateway,
    private val bookGroupRepository: BookGroupRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CustomConfigUiState(
            settings = settingsGateway.currentSettings,
            backupSettings = backupSettingsGateway.currentSettings,
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsGateway.settings,
                backupSettingsGateway.settings,
                bookGroupRepository.flowAll()
            ) { custom, backup, groups ->
                CustomConfigUiState(
                    custom, 
                    backup, 
                    listOf("全部" to -1L, "网络书籍" to -10L, "本地书籍" to -2L) + 
                            groups.map { it.groupName to it.groupId }
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun onIntent(intent: CustomConfigIntent) {
        when (intent) {
            is CustomConfigIntent.SetAutoExportBooksOnBackup -> {
                update { it.copy(autoExportBooksOnBackup = intent.value) }
            }
            is CustomConfigIntent.SetAutoImportBooksOnRestore -> {
                update { it.copy(autoImportBooksOnRestore = intent.value) }
            }
            is CustomConfigIntent.SetExportGroupMask -> {
                update { it.copy(exportGroupMask = intent.value) }
            }
            CustomConfigIntent.ExportAllToWebDav -> {
                io.legado.app.utils.LogUtils.d("CustomConfig", "触发一键导出书籍到 WebDAV")
                Backup.exportAllCachedBooks(
                    splitties.init.appCtx, 
                    force = true,
                    groupMask = _uiState.value.settings.exportGroupMask
                )
            }
            CustomConfigIntent.ImportAllFromWebDav -> {
                viewModelScope.launch {
                    io.legado.app.help.AppWebDav.importAllBooksFromWebDav()
                }
            }
        }
    }

    private fun update(transform: (CustomSettings) -> CustomSettings) {
        viewModelScope.launch { settingsGateway.update(transform) }
    }
}
