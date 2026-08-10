package io.legado.app.help.storage

import android.content.Context
import android.content.Intent
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.data.repository.CustomSettingsRepository
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.service.ExportBookService
import io.legado.app.utils.LogUtils
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.toastOnUi

/**
 * Uploaded-source enhancement kept outside the upstream Backup object.
 */
fun Backup.exportAllCachedBooks(
    context: Context,
    force: Boolean = false,
    isAuto: Boolean = false,
) {
    val settings = CustomSettingsRepository().currentSettings
    if (!force && !settings.autoExportBooksOnBackup) return

    if (isAuto && !BackupLifecycleObserver.isAppInBackground) {
        LogUtils.d("Backup", "App在前台，取消自动导出书籍")
        return
    }

    val books = appDb.bookDao.all.filter {
        it.isLocal || BookHelp.countCachedChapters(it) > 0
    }

    if (books.isEmpty()) {
        if (!isAuto) {
            context.toastOnUi("没有需要导出的书籍 (仅导出本地书籍或有缓存的书籍)")
        }
        LogUtils.d("Backup", "没有需要导出的书籍")
        return
    }

    if (!isAuto) context.toastOnUi("开始导出 ${books.size} 本书籍")
    LogUtils.d("Backup", "开始导出 ${books.size} 本书籍")

    books.forEach { book ->
        if (isAuto && !BackupLifecycleObserver.isAppInBackground) {
            LogUtils.d("Backup", "App在前台，中止自动导出书籍")
            return
        }
        val intent = Intent(context, ExportBookService::class.java).apply {
            action = IntentAction.start
            putExtra("bookUrl", book.bookUrl)
            putExtra("exportPath", AppConfig.backupPath ?: Backup.backupPath)
            putExtra("exportType", if (AppConfig.exportType == 0) "txt" else "epub")
            // Current service compatibility hook will consume this after its minimal adapter is restored.
            putExtra("exportToWebDav", true)
        }
        context.startForegroundServiceCompat(intent)
    }
}
