package io.legado.app.help

import io.legado.app.domain.gateway.CustomSettingsGateway
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object CustomHooks : KoinComponent {
    private val customSettingsGateway: CustomSettingsGateway by inject()

    fun isLoginShowEarthIcon(): Boolean {
        return customSettingsGateway.currentSettings.loginShowEarthIcon
    }

    fun onDiscoveryCategoryClick(block: () -> Unit) {
        if (customSettingsGateway.currentSettings.discoveryAutoCollapse) {
            block()
        }
    }
}
