package io.legado.app.enhance.webdav

import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.update
import io.legado.app.lib.webdav.ObjectNotFoundException
import io.legado.app.lib.webdav.WebDav
import io.legado.app.model.analyzeRule.CustomUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.remote.RemoteBook
import io.legado.app.utils.toastOnUi
import io.legado.app.constant.AppConst
import io.legado.app.R
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import java.io.File
import java.net.URL

object WebDavEnhance {

    /**
     * 复杂的 URL 编码逻辑，从 WebDav.kt 移至此处
     */
    fun getHttpUrl(url: URL): String? {
        val raw = url.toString()
            .replace("davs://", "https://")
            .replace("dav://", "http://")
        val isHttps = raw.startsWith("https")
        val content = raw.substringAfter("://")
        val host = content.substringBefore("/")
        val pathSegments = content.substringAfter("/", "")

        return kotlin.runCatching {
            val builder = okhttp3.HttpUrl.Builder()
                .scheme(if (isHttps) "https" else "http")

            if (host.contains(":")) {
                builder.host(host.substringBefore(":"))
                builder.port(host.substringAfter(":").toInt())
            } else {
                builder.host(host)
            }

            if (pathSegments.isNotEmpty()) {
                pathSegments.split("/").forEach {
                    if (it.isNotEmpty()) builder.addPathSegment(it)
                }
                if (pathSegments.endsWith("/")) {
                    builder.addPathSegment("")
                }
            }
            builder.build().toString()
        }.getOrNull()
    }

    /**
     * WebDAV 智能导出：比对大小，跳过重复上传
     */
    suspend fun exportWebDavSmart(uri: android.net.Uri, fileName: String, localSize: Long) {
        if (!io.legado.app.utils.NetworkUtils.isAvailable()) {
            LogUtils.d("AppWebDav", "网络不可用，跳过导出 $fileName")
            return
        }
        val auth = AppWebDav.authorization
        if (auth == null) {
            LogUtils.d("AppWebDav", "authorization 为空，正在尝试更新配置...")
            AppWebDav.upConfig()
        }
        val finalAuth = AppWebDav.authorization ?: run {
            AppLog.put("WebDAV 导出失败：未配置账号信息", toast = true)
            return
        }
        try {
            val putUrl = AppWebDav.exportsWebDavUrl + fileName
            val webDav = WebDav(putUrl, finalAuth)
            val cloudFile = webDav.getWebDavFile()
            if (cloudFile != null && cloudFile.size >= localSize) {
                LogUtils.d("AppWebDav", "跳过上传 $fileName: 云端大小 ${cloudFile.size} >= 本地大小 $localSize")
                return
            }
            LogUtils.d("AppWebDav", "开始上传 $fileName 到 WebDAV...")
            webDav.upload(uri, "text/plain")
            LogUtils.d("AppWebDav", "上传完成 $fileName")
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (e is io.legado.app.lib.webdav.ObjectNotFoundException) {
                LogUtils.d("AppWebDav", "云端文件不存在，直接上传 $fileName")
                val putUrl = AppWebDav.exportsWebDavUrl + fileName
                WebDav(putUrl, finalAuth).upload(uri, "text/plain")
            } else {
                AppLog.put("WebDAV 智能导出失败\n${e.localizedMessage}", e, true)
            }
        }
    }

    /**
     * 从 WebDAV 导入所有书籍，从 AppWebDav.kt 移至此处
     */
    suspend fun importAllBooksFromWebDav() = withContext(IO) {
        val auth = AppWebDav.authorization ?: return@withContext
        try {
            appCtx.toastOnUi("开始从 WebDAV 批量同步书籍...")
            AppLog.put("WebDAV: 正在列出目录 ${AppWebDav.exportsWebDavUrl}")
            val files = WebDav(AppWebDav.exportsWebDavUrl, auth).listFiles()
            if (files.isEmpty()) {
                AppLog.put("WebDAV: 目录为空或不存在")
                return@withContext
            }

            var successCount = 0
            var skipCount = 0
            files.forEachIndexed { index, webDavFile ->
                if (!webDavFile.isDir && (webDavFile.displayName.endsWith(".epub", true) || webDavFile.displayName.endsWith(".txt", true))) {
                    if (LocalBook.isOnBookShelf(webDavFile.displayName)) {
                        skipCount++
                        AppLog.put("WebDAV: 跳过已存在书籍: ${webDavFile.displayName}")
                    } else {
                        try {
                            appCtx.toastOnUi("同步中(${index + 1}/${files.size}): ${webDavFile.displayName}")
                            val remoteBook = RemoteBook(webDavFile)
                            val bookUrl = AppWebDav.defaultBookWebDav?.downloadRemoteBook(remoteBook)
                            if (bookUrl != null) {
                                val book = LocalBook.importFile(bookUrl)
                                book.originName = webDavFile.displayName
                                book.origin = BookType.webDavTag + CustomUrl(webDavFile.path)
                                    .putAttribute("serverID", AppWebDav.defaultBookWebDav?.serverID)
                                    .toString()
                                book.update()
                                successCount++
                            }
                        } catch (e: Exception) {
                            AppLog.put("WebDAV: 同步书籍 ${webDavFile.displayName} 失败", e)
                        }
                    }
                }
            }
            appCtx.toastOnUi("WebDAV 同步完成：成功 $successCount，跳过 $skipCount")
            val notification = NotificationCompat.Builder(appCtx, AppConst.channelIdDownload)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle("WebDAV 书籍导入完成")
                .setContentText("成功: $successCount, 跳过: $skipCount")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(1001, notification)
        } catch (e: Exception) {
            if (e is ObjectNotFoundException || e.message?.contains("404") == true) {
                AppLog.put("WebDAV: 导出目录不存在，跳过批量导入")
            } else {
                AppLog.put("WebDAV 批量导入书籍失败\n${e.localizedMessage}", e, true)
            }
        }
    }
}
