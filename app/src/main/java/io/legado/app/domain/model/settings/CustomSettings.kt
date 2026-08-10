package io.legado.app.domain.model.settings

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class CustomSettings(
    val masterSwitch: Boolean = true,
    val discoveryLayoutMode: Int = 0,
    val discoveryLayoutSwitcherEnabled: Boolean = true,
    val discoveryAutoCollapse: Boolean = true,
    val loginShowEarthIcon: Boolean = true,
    val autoBackupOnBackground: Boolean = false,
    val autoBackupOnBackgroundIntervalMinutes: Int = 1,
    val autoExportBooksOnBackup: Boolean = false,
    val autoImportBooksOnRestore: Boolean = false
)
