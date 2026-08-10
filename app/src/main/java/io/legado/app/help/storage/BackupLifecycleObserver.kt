package io.legado.app.help.storage

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.legado.app.domain.gateway.CustomSettingsGateway
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext
import splitties.init.appCtx
import java.util.concurrent.TimeUnit

object BackupLifecycleObserver : DefaultLifecycleObserver {

    private val customSettingsGateway: CustomSettingsGateway
        get() = GlobalContext.get().get()

    private var backupCoroutine: Coroutine<Unit>? = null
    var isAppInBackground: Boolean = false
        private set

    override fun onStart(owner: LifecycleOwner) {
        isAppInBackground = false
        backupCoroutine?.cancel()
        backupCoroutine = null
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInBackground = true
        val settings = customSettingsGateway.currentSettings
        if (!settings.autoBackupOnBackground) return

        backupCoroutine?.cancel()
        backupCoroutine = Coroutine.async {
            delay(TimeUnit.MINUTES.toMillis(settings.autoBackupOnBackgroundIntervalMinutes.toLong()))
            if (isAppInBackground) {
                // 强制执行，不受原生“一天一次”自动备份限制。
                Backup.backupLocked(appCtx, AppConfig.backupPath)
            }
        }
    }
}
