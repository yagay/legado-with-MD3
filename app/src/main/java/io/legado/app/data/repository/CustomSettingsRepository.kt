package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.domain.gateway.CustomSettingsGateway
import io.legado.app.enhance.model.CustomSettings
import io.legado.app.enhance.model.CustomPreferKey
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class CustomSettingsRepository : CustomSettingsGateway {
    override val currentSettings: CustomSettings
        get() = AppConfigStore.preferences.toCustomSettings()

    override val settings: Flow<CustomSettings> = AppConfigStore.preferencesFlow
        .map(Preferences::toCustomSettings)
        .distinctUntilChanged()

    override suspend fun update(transform: (CustomSettings) -> CustomSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toCustomSettings,
            toPrefMap = CustomSettings::toPrefMap,
            transform = transform,
        )
    }
}

internal fun Preferences.toCustomSettings(): CustomSettings = CustomSettings(
    masterSwitch = compatDsBoolean(CustomPreferKey.masterSwitch) ?: false,
    discoveryLayoutSwitcherEnabled = compatDsBoolean(CustomPreferKey.discoveryLayoutSwitcherEnabled) ?: true,
    discoveryAutoCollapse = compatDsBoolean(CustomPreferKey.discoveryAutoCollapse) ?: true,
    loginShowEarthIcon = compatDsBoolean(CustomPreferKey.loginShowEarthIcon) ?: true,
    autoExportBooksOnBackup = compatDsBoolean(CustomPreferKey.autoExportBooksOnBackup) ?: false,
    autoImportBooksOnRestore = compatDsBoolean(CustomPreferKey.autoImportBooksOnRestore) ?: false,
    exportGroupMask = compatDsLong(CustomPreferKey.exportGroupMask) ?: -10L
)

internal fun CustomSettings.toPrefMap(): Map<String, Any?> = mapOf(
    CustomPreferKey.masterSwitch to masterSwitch,
    CustomPreferKey.discoveryLayoutSwitcherEnabled to discoveryLayoutSwitcherEnabled,
    CustomPreferKey.discoveryAutoCollapse to discoveryAutoCollapse,
    CustomPreferKey.loginShowEarthIcon to loginShowEarthIcon,
    CustomPreferKey.autoExportBooksOnBackup to autoExportBooksOnBackup,
    CustomPreferKey.autoImportBooksOnRestore to autoImportBooksOnRestore,
    CustomPreferKey.exportGroupMask to exportGroupMask
)
