package io.legado.app.enhance.settingssearch

import androidx.annotation.StringRes
import io.legado.app.ui.main.MainRoute
import io.legado.app.ui.main.MainRouteSettingsAi
import io.legado.app.ui.main.MainRouteSettingsBackup
import io.legado.app.ui.main.MainRouteSettingsCover
import io.legado.app.ui.main.MainRouteSettingsCustomConfig
import io.legado.app.ui.main.MainRouteSettingsDownloadCache
import io.legado.app.ui.main.MainRouteSettingsLabConfig
import io.legado.app.ui.main.MainRouteSettingsOther
import io.legado.app.ui.main.MainRouteSettingsRead
import io.legado.app.ui.main.MainRouteSettingsTheme
import io.legado.app.ui.main.MainRouteSettingsTranslation

data class GeneratedSettingSpec(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int? = null,
    val groupIndex: Int = 0,
)

data class GeneratedSettingPage(
    val key: String,
    @StringRes val titleRes: Int,
    val destination: SettingDestination,
    val items: List<GeneratedSettingSpec>,
)

enum class SettingDestination {
    Other, Read, Cover, Theme, Backup, Custom, Ai, Translation, DownloadCache, Lab;

    fun route(searchTitle: String? = null): MainRoute = when (this) {
        Other -> MainRouteSettingsOther(searchTitle)
        Read -> MainRouteSettingsRead(searchTitle)
        Cover -> MainRouteSettingsCover(searchTitle)
        Theme -> MainRouteSettingsTheme(searchTitle)
        Backup -> MainRouteSettingsBackup(searchTitle)
        Custom -> MainRouteSettingsCustomConfig(searchTitle)
        Ai -> MainRouteSettingsAi(searchTitle)
        Translation -> MainRouteSettingsTranslation(searchTitle)
        DownloadCache -> MainRouteSettingsDownloadCache(searchTitle)
        Lab -> MainRouteSettingsLabConfig(searchTitle)
    }
}
