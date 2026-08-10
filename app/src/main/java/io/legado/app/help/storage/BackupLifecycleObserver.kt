package io.legado.app.help.storage

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext
import splitties.init.appCtx
import java.util.concurrent.TimeUnit

object BackupLifecycleObserver : DefaultLifecycleObserver {

    private val backupSettingsGateway: BackupSettingsGateway
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
        val settings = backupSettingsGateway.currentSettings
        if (settings.autoBackupOnBackground) {
            backupCoroutine?.cancel()
            backupCoroutine = Coroutine.async {
                delay(TimeUnit.MINUTES.toMillis(settings.autoBackupOnBackgroundInterval.toLong()))
                if (isAppInBackground) {
                    Backup.autoBack(appCtx, force = true)
                }
            }
        }
    }
}
