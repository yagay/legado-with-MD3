package io.legado.app.domain.model.settings

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class CustomSettings(
    val masterSwitch: Boolean = false,
    val discoveryLayoutMode: Int = 0,
    val discoveryLayoutSwitcherEnabled: Boolean = true,
    val discoveryAutoCollapse: Boolean = true,
    val loginShowEarthIcon: Boolean = true,
    val autoExportBooksOnBackup: Boolean = false,
    val autoImportBooksOnRestore: Boolean = false,
    val exportGroupMask: Long = -10L // -10L means network books by default
)
