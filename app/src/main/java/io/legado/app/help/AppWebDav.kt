package io.legado.app.help

import android.net.Uri
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.BackupRestoreLock
import io.legado.app.help.storage.Restore
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.ObjectNotFoundException
import io.legado.app.lib.webdav.WebDav
import io.legado.app.lib.webdav.WebDavException
import io.legado.app.lib.webdav.WebDavFile
import io.legado.app.model.remote.RemoteBook
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.ui.config.backupConfig.BackupConfig
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJson
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.toastOnUi
import io.legado.app.model.localBook.LocalBook
import io.legado.app.constant.BookType
import io.legado.app.help.book.update
import io.legado.app.model.analyzeRule.CustomUrl
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

/**
 * webDav初始化会访问网络,不要放到主线程
 */
object AppWebDav {
    const val defaultWebDavUrl = "https://dav.jianguoyun.com/dav/"
    val bookProgressUrl get() = "${rootWebDavUrl}bookProgress/"
    val exportsWebDavUrl get() = "${rootWebDavUrl}books/"
    val bgWebDavUrl get() = "${rootWebDavUrl}background/"

    private val configMutex = Mutex()
    private var appliedConfig: AppliedWebDavConfig? = null

    @Volatile
    var authorization: Authorization? = null
        private set

    @Volatile
    var defaultBookWebDav: RemoteBookWebDav? = null

    val isOk get() = authorization != null

    val isJianGuoYun get() = rootWebDavUrl.startsWith(defaultWebDavUrl, true)

    private val rootWebDavUrl: String
        get() {
            val configUrl = BackupConfig.webDavUrl.trim()
            var url = if (configUrl.isEmpty()) defaultWebDavUrl else configUrl
            if (!url.endsWith("/")) url = "${url}/"
            AppConfig.webDavDir.trim().let {
                if (it.isNotEmpty()) {
                    url = "${url}${it}/"
                }
            }
            return url
        }

    suspend fun upConfig() {
        configMutex.withLock {
            val config = AppliedWebDavConfig(
                url = BackupConfig.webDavUrl,
                account = BackupConfig.webDavAccount,
                password = BackupConfig.webDavPassword,
                dir = BackupConfig.webDavDir,
            )
            if (appliedConfig == config) return

            kotlin.runCatching {
                authorization = null
                defaultBookWebDav = null
                if (config.account.isNotEmpty() && config.password.isNotEmpty()) {
                    val mAuthorization = Authorization(config.account, config.password)
                    checkAuthorization(mAuthorization)
                    WebDav(rootWebDavUrl, mAuthorization).makeAsDir()
                    WebDav(bookProgressUrl, mAuthorization).makeAsDir()
                    WebDav(exportsWebDavUrl, mAuthorization).makeAsDir()
                    WebDav(bgWebDavUrl, mAuthorization).makeAsDir()
                    val rootBooksUrl = "${rootWebDavUrl}books/"
                    defaultBookWebDav = RemoteBookWebDav(rootBooksUrl, mAuthorization)
                    authorization = mAuthorization
                }
                appliedConfig = config
            }
        }
    }

    private data class AppliedWebDavConfig(
        val url: String,
        val account: String,
        val password: String,
        val dir: String,
    )

    @Throws(WebDavException::class)
    private suspend fun checkAuthorization(authorization: Authorization) {
        if (!WebDav(rootWebDavUrl, authorization).check()) {
            //appCtx.removePref(PreferKey.webDavPassword)
            appCtx.toastOnUi(R.string.webdav_application_authorization_error)
            throw WebDavException(appCtx.getString(R.string.webdav_application_authorization_error))
        }
    }

    @Throws(Exception::class)
    suspend fun getBackupNames(): ArrayList<String> {
        val names = arrayListOf<String>()
        authorization?.let {
            var files = WebDav(rootWebDavUrl, it).listFiles()
            files = files.sortedWith { o1, o2 ->
                AlphanumComparator.compare(o1.displayName, o2.displayName)
            }.reversed()
            files.forEach { webDav ->
                val name = webDav.displayName
                if (name.startsWith("backup")) {
                    names.add(name)
                }
            }
        } ?: throw NoStackTraceException("webDav没有配置")
        return names
    }

    @Throws(WebDavException::class)
    suspend fun restoreWebDav(name: String) {
        authorization?.let {
            val webDav = WebDav(rootWebDavUrl + name, it)
            BackupRestoreLock.withLock {
                webDav.downloadTo(Backup.zipFilePath, true)
                FileUtils.delete(Backup.backupPath)
                ZipUtils.unZipToPath(File(Backup.zipFilePath), Backup.backupPath)
                Restore.restoreUnzipped(Backup.backupPath)
                LocalConfig.lastBackup = System.currentTimeMillis()
            }
        }
    }

    suspend fun hasBackUp(backUpName: String): Boolean {
        authorization?.let {
            val url = "$rootWebDavUrl${backUpName}"
            return WebDav(url, it).exists()
        }
        return false
    }

    suspend fun lastBackUp(): Result<WebDavFile?> {
        return kotlin.runCatching {
            authorization?.let {
                var lastBackupFile: WebDavFile? = null
                WebDav(rootWebDavUrl, it).listFiles().reversed().forEach { webDavFile ->
                    if (webDavFile.displayName.startsWith("backup")) {
                        if (lastBackupFile == null
                            || webDavFile.lastModify > lastBackupFile.lastModify
                        ) {
                            lastBackupFile = webDavFile
                        }
                    }
                }
                lastBackupFile
            }
        }
    }

    suspend fun testWebDav(): Boolean {
        return kotlin.runCatching {
            val account = BackupConfig.webDavAccount
            val password = BackupConfig.webDavPassword
            if (account.isNullOrEmpty() || password.isNullOrEmpty()) {
                appCtx.toastOnUi("账号或密码为空")
                return false
            }

            val auth = Authorization(account, password)
            checkAuthorization(auth)

            appCtx.toastOnUi("WebDAV 服务可用")
            true
        }.getOrElse {
            it.printStackTrace()
            if (it !is WebDavException) {
                appCtx.toastOnUi(it.message ?: "未知错误")
            }
            false
        }
    }



    /**
     * webDav备份
     * @param fileName 备份文件名
     */
    @Throws(Exception::class)
    suspend fun backUpWebDav(fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        authorization?.let {
            val putUrl = "$rootWebDavUrl$fileName"
            WebDav(putUrl, it).upload(Backup.zipFilePath)
        }
    }

    /**
     * 获取云端所有背景名称
     */
    private suspend fun getAllBgWebDavFiles(): Result<List<WebDavFile>> {
        return kotlin.runCatching {
            if (!NetworkUtils.isAvailable())
                throw NoStackTraceException("网络未连接")
            authorization.let {
                it ?: throw NoStackTraceException("webDav未配置")
                WebDav(bgWebDavUrl, it).listFiles()
            }
        }
    }

    /**
     * 上传背景图片
     */
    suspend fun upBgs(files: Array<File>) {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bgWebDavFiles = getAllBgWebDavFiles().getOrThrow()
            .map { it.displayName }
            .toSet()
        files.forEach {
            if (!bgWebDavFiles.contains(it.name) && it.exists()) {
                WebDav("$bgWebDavUrl${it.name}", authorization)
                    .upload(it)
            }
        }
    }

    /**
     * 下载背景图片
     */
    suspend fun downBgs() {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bgWebDavFiles = getAllBgWebDavFiles().getOrThrow()
            .map { it.displayName }
            .toSet()
    }

    @Suppress("unused")
    suspend fun exportWebDav(byteArray: ByteArray, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            authorization?.let {
                // 如果导出的本地文件存在,开始上传
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl, it).upload(byteArray, "text/plain")
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

    suspend fun exportWebDav(uri: Uri, fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        try {
            authorization?.let {
                // 如果导出的本地文件存在,开始上传
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl, it).upload(uri, "text/plain")
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav导出失败\n${e.localizedMessage}", e, true)
        }
    }

    suspend fun exportWebDavSmart(uri: Uri, fileName: String, localSize: Long) {
        if (!NetworkUtils.isAvailable()) {
            LogUtils.d("AppWebDav", "网络不可用，跳过导出 $fileName")
            return
        }
        val auth = authorization
        if (auth == null) {
            LogUtils.d("AppWebDav", "authorization 为空，正在尝试更新配置...")
            upConfig()
        }
        val finalAuth = authorization ?: run {
            AppLog.put("WebDav导出失败：未配置账号信息", toast = true)
            return
        }
        try {
            val putUrl = exportsWebDavUrl + fileName
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
                val putUrl = exportsWebDavUrl + fileName
                WebDav(putUrl, finalAuth).upload(uri, "text/plain")
            } else {
                AppLog.put("WebDav智能导出失败\n${e.localizedMessage}", e, true)
            }
        }
    }

    suspend fun uploadBookProgress(
        book: Book,
        toast: Boolean = false,
        onSuccess: (() -> Unit)? = null
    ) {
        val authorization = authorization ?: return
        if (!AppConfig.syncBookProgress) return
        if (!NetworkUtils.isAvailable()) return
        try {
            val bookProgress = BookProgress(book)
            val json = GSON.toJson(bookProgress)
            val url = getProgressUrl(book.name, book.author)
            WebDav(url, authorization).upload(json.toByteArray(), "application/json")
            book.syncTime = System.currentTimeMillis()
            onSuccess?.invoke()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("上传进度失败\n${e.localizedMessage}", e, toast)
        }
    }

    suspend fun uploadBookProgress(
        bookProgress: BookProgress,
        onSuccess: (() -> Unit)? = null
    ): Boolean {
        try {
            val authorization = authorization ?: return false
            if (!AppConfig.syncBookProgress) return false
            if (!NetworkUtils.isAvailable()) return false
            val json = GSON.toJson(bookProgress)
            val url = getProgressUrl(bookProgress.name, bookProgress.author)
            WebDav(url, authorization).upload(json.toByteArray(), "application/json")
            onSuccess?.invoke()
            return true
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("上传进度失败\n${e.localizedMessage}", e)
            return false
        }
    }

    private fun getProgressUrl(name: String, author: String): String {
        return bookProgressUrl + getProgressFileName(name, author)
    }

    private fun getProgressFileName(name: String, author: String): String {
        return UrlUtil.replaceReservedChar("${name}_${author}".normalizeFileName()) + ".json"
    }

    /**
     * 获取书籍进度
     */
    suspend fun getBookProgress(book: Book): BookProgress? {
        return getBookProgress(book.name, book.author)
    }

    /**
     * 获取书籍进度
     */
    suspend fun getBookProgress(name: String, author: String): BookProgress? {
        val url = getProgressUrl(name, author)
        kotlin.runCatching {
            val authorization = authorization ?: return null
            WebDav(url, authorization).download().let { byteArray ->
                val json = String(byteArray)
                if (json.isJson()) {
                    return GSON.fromJsonObject<BookProgress>(json).getOrNull()

                }



            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("获取书籍进度失败\n${it.localizedMessage}", it)
        }
        return null
    }

    suspend fun downloadAllBookProgress() {
        val authorization = authorization ?: return
        if (!NetworkUtils.isAvailable()) return
        val bookProgressFiles = WebDav(bookProgressUrl, authorization).listFiles()
        val map = hashMapOf<String, WebDavFile>()
        bookProgressFiles.forEach {
            map[it.displayName] = it
        }
        appDb.bookDao.all.forEach { book ->
            val progressFileName = getProgressFileName(book.name, book.author)
            val webDavFile = map[progressFileName]
            webDavFile ?: return@forEach
            if (webDavFile.lastModify <= book.syncTime) {
                //本地同步时间大于上传时间不用同步
                return@forEach
            }
            getBookProgress(book)?.let { bookProgress ->
                if (bookProgress.durChapterIndex > book.durChapterIndex
                    || (bookProgress.durChapterIndex == book.durChapterIndex
                            && bookProgress.durChapterPos > book.durChapterPos)
                ) {
                    book.durChapterIndex = bookProgress.durChapterIndex
                    book.durChapterPos = bookProgress.durChapterPos
                    book.durChapterTitle = bookProgress.durChapterTitle
                    book.durChapterTime = bookProgress.durChapterTime
                    book.syncTime = System.currentTimeMillis()
                    appDb.bookDao.update(book)
                }
            }
        }
    }

    /**
     * 从 WebDAV 导入所有书籍
     */
    suspend fun importAllBooksFromWebDav() = withContext(IO) {
        val auth = authorization ?: return@withContext
        try {
            appCtx.toastOnUi("开始从 WebDAV 批量同步书籍...")
            AppLog.put("WebDAV: 正在列出目录 $exportsWebDavUrl")
            val files = WebDav(exportsWebDavUrl, auth).listFiles()
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
                        AppLog.put("WebDAV: 跳过已存在书籍 ${webDavFile.displayName}")
                    } else {
                        try {
                            appCtx.toastOnUi("同步中 (${index + 1}/${files.size}): ${webDavFile.displayName}")
                            val remoteBook = RemoteBook(webDavFile)
                            val bookUrl = defaultBookWebDav?.downloadRemoteBook(remoteBook)
                            if (bookUrl != null) {
                                val book = LocalBook.importFile(bookUrl)
                                book.originName = webDavFile.displayName
                                book.origin = BookType.webDavTag + CustomUrl(webDavFile.path)
                                    .putAttribute("serverID", defaultBookWebDav?.serverID)
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
        } catch (e: Exception) {
            if (e is ObjectNotFoundException || e.message?.contains("404") == true) {
                AppLog.put("WebDAV: 导出目录不存在，跳过批量导入")
            } else {
                AppLog.put("WebDAV 批量导入书籍失败\n${e.localizedMessage}", e, true)
            }
        }
    }

}
