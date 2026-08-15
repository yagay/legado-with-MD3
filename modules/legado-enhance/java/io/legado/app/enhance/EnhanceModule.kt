package io.legado.app.enhance

import io.legado.app.enhance.model.CustomSettingsGateway
import io.legado.app.enhance.model.CustomSettingsRepository
import io.legado.app.ui.config.customConfig.CustomConfigViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val enhanceModule = module {
    singleOf(::CustomSettingsRepository)
    single<CustomSettingsGateway> { get<CustomSettingsRepository>() }
    viewModelOf(::CustomConfigViewModel)
}
