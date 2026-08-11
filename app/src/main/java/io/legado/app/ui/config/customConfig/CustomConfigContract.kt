package io.legado.app.ui.config.customConfig

import androidx.compose.runtime.Immutable
import io.legado.app.domain.model.settings.BackupSettings
import io.legado.app.domain.model.settings.CustomSettings

@Immutable
data class CustomConfigUiState(
    val settings: CustomSettings = CustomSettings(),
    val backupSettings: BackupSettings = BackupSettings(),
    val bookGroups: List<Pair<String, Long>> = emptyList(), // Name to Mask
)

sealed interface CustomConfigIntent {
    data class SetAutoBackupOnBackground(val value: Boolean) : CustomConfigIntent
    data class SetAutoBackupOnBackgroundInterval(val value: Int) : CustomConfigIntent
    data class SetAutoExportBooksOnBackup(val value: Boolean) : CustomConfigIntent
    data class SetAutoImportBooksOnRestore(val value: Boolean) : CustomConfigIntent
    data class SetExportGroupMask(val value: Long) : CustomConfigIntent
    data object ExportAllToWebDav : CustomConfigIntent
    data object ImportAllFromWebDav : CustomConfigIntent
}
