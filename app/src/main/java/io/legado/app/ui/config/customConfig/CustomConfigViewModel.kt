package io.legado.app.ui.config.customConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.CustomSettingsGateway
import io.legado.app.domain.model.settings.CustomSettings
import io.legado.app.help.storage.Backup
import io.legado.app.help.AppWebDav
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import splitties.init.appCtx

class CustomConfigViewModel(
    private val settingsGateway: CustomSettingsGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CustomConfigUiState(settings = settingsGateway.currentSettings)
    )
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsGateway.settings.collect { settings ->
                _uiState.value = CustomConfigUiState(settings)
            }
        }
    }

    fun onIntent(intent: CustomConfigIntent) {
        when (intent) {
            is CustomConfigIntent.SetDiscoveryLayoutMode -> {
                update { it.copy(discoveryLayoutMode = intent.value.coerceIn(0, 1)) }
            }
            is CustomConfigIntent.SetAutoBackupOnBackground -> {
                update { it.copy(autoBackupOnBackground = intent.value) }
            }
            is CustomConfigIntent.SetAutoBackupOnBackgroundInterval -> {
                update {
                    it.copy(autoBackupOnBackgroundIntervalMinutes = intent.value.coerceAtLeast(1))
                }
            }
            is CustomConfigIntent.SetAutoExportBooksOnBackup -> {
                update { it.copy(autoExportBooksOnBackup = intent.value) }
            }
            is CustomConfigIntent.SetAutoImportBooksOnRestore -> {
                update { it.copy(autoImportBooksOnRestore = intent.value) }
            }
            CustomConfigIntent.ExportAllToWebDav -> {
                Backup.exportAllCachedBooks(appCtx, force = true)
            }
            CustomConfigIntent.ImportAllFromWebDav -> {
                viewModelScope.launch { AppWebDav.importAllBooksFromWebDav() }
            }
        }
    }

    private fun update(transform: (CustomSettings) -> CustomSettings) {
        viewModelScope.launch { settingsGateway.update(transform) }
    }
}
