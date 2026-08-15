package io.legado.app.enhance.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class CustomSettings(
    val masterSwitch: Boolean = false,
    val discoveryLayoutSwitcherEnabled: Boolean = true,
    val discoveryAutoCollapse: Boolean = true,
    val loginShowEarthIcon: Boolean = true,
    val autoExportBooksOnBackup: Boolean = false,
    val autoImportBooksOnRestore: Boolean = false,
    val exportGroupMask: Long = -10L // -10L means network books by default
)
