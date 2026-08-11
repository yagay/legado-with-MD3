package io.legado.app.help.storage

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import splitties.init.appCtx

object BackupLifecycleObserver : DefaultLifecycleObserver {

    var isAppInBackground: Boolean = false
        private set

    override fun onStart(owner: LifecycleOwner) {
        isAppInBackground = false
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInBackground = true
    }
}
