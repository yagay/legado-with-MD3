package io.legado.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.legado.app.help.storage.Backup

class BackupService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Implementation of manual backup service if needed
        return START_NOT_STICKY
    }
}
