package io.legado.app.enhance.model

import io.legado.app.enhance.model.CustomSettings
import kotlinx.coroutines.flow.Flow

interface CustomSettingsGateway {
    val settings: Flow<CustomSettings>
    val currentSettings: CustomSettings
    suspend fun update(transform: (CustomSettings) -> CustomSettings)
}
