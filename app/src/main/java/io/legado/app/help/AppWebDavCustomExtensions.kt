package io.legado.app.help

import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.webdav.ObjectNotFoundException
import io.legado.app.lib.webdav.WebDav
import io.legado.app.model.analyzeRule.CustomUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.config.backupConfig.BackupConfig
import io.legado.app.utils.LogUtils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import splitties.init.appCtx

/** Uploaded-source bulk WebDAV import kept as a thin extension around upstream AppWebDav. */
suspend fun AppWebDav.importAllBooksFromWebDav() {
    withContext(IO) {
        if (!NetworkUtils.isAvailable()) return@withContext
        val auth = AppWebDav.authorization ?: run {
            AppLog.put("导入失败：未配置 WebDAV 账号", toast = true)
            return@withContext
        }

        val root = BackupConfig.webDavUrl.trim().ifEmpty {
            "https://dav.jianguoyun.com/dav/"
        }.let { if (it.endsWith('/')) it else "$it/" }
        val dir = AppConfig.webDavDir.trim()
        val exportsUrl = buildString {
            append(root)
            if (dir.isNotEmpty()) append(dir).append('/')
            append("books/")
        }

        try {
            LogUtils.d("AppWebDav", "正在扫描 WebDAV 书籍目录: $exportsUrl")
            val files = try {
                WebDav(exportsUrl, auth).listFiles()
            } catch (_: ObjectNotFoundException) {
                LogUtils.d("AppWebDav", "WebDAV 导出目录不存在")
                emptyList()
            } catch (e: Exception) {
                if (e.message?.contains("404") == true) {
                    LogUtils.d("AppWebDav", "WebDAV 导出目录 (404) 不存在")
                    emptyList()
                } else {
                    throw e
                }
            }

            val bookFiles = files.filter { AppPattern.bookFileRegex.matches(it.displayName) }
            if (bookFiles.isEmpty()) {
                appCtx.toastOnUi("未在 WebDAV 找到可导入的书籍文件")
                return@withContext
            }

            appCtx.toastOnUi("开始导入 ${bookFiles.size} 本书籍...")
            bookFiles.forEach { file ->
                if (!LocalBook.isOnBookShelf(file.displayName)) {
                    LogUtils.d("AppWebDav", "正在从 WebDAV 下载并导入: ${file.displayName}")
                    try {
                        val downloadBookUri = WebDav(file.path, auth).downloadInputStream().use { inputStream ->
                            LocalBook.saveBookFile(inputStream, file.displayName)
                        }
                        LocalBook.importFiles(downloadBookUri).forEach { book ->
                            book.origin = BookType.webDavTag + CustomUrl(file.path)
                                .putAttribute("serverID", BackupConfig.webDavUrl)
                                .toString()
                            book.save()
                        }
                    } catch (e: Exception) {
                        LogUtils.e("AppWebDav", "导入书籍失败: ${file.displayName}\n${e.localizedMessage}")
                    }
                }
            }
            appCtx.toastOnUi("批量导入完成")
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav批量导入失败\n${e.localizedMessage}", e, true)
        }
    }
}
