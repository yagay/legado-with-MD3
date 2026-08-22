package io.legado.app.domain.usecase

import androidx.appcompat.app.AppCompatActivity
import com.script.rhino.runScriptWithContext
import io.legado.app.constant.AppLog
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.getExploreInfoMap
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.utils.InfoMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ExploreKindUiUseCase(
    private val bookSourceDao: BookSourceDao
) {

    private val sourceCache: ConcurrentHashMap<String, BookSource?> = ConcurrentHashMap()

    suspend fun warmUp(sourceUrl: String?) {
        sourceUrl?.takeIf { it.isNotBlank() }?.let { getOrLoadBookSource(it) }
    }

    suspend fun resolveDisplayName(
        kind: ExploreKind,
        sourceUrl: String?,
        infoMap: InfoMap?
    ): String {
        val viewName = kind.viewName
        if (viewName.isNullOrBlank()) return kind.title
        parseLiteralViewName(viewName)?.let { return it }

        val effectiveSourceUrl = sourceUrl ?: return kind.title
        val effectiveInfoMap = infoMap ?: return kind.title

        return runCatching {
            evalUiJs(viewName, effectiveSourceUrl, effectiveInfoMap)
                .takeUnless { it.isNullOrEmpty() } ?: "null"
        }.getOrElse {
            "err"
        }
    }

    suspend fun executeAction(
        action: String?,
        title: String,
        sourceUrl: String?,
        activity: AppCompatActivity?,
        onRefreshKinds: () -> Unit,
        onOpenLogin: (() -> Boolean)? = null,
        onShowBrowser: ((url: String, html: String?, preloadJs: String?, config: String?) -> Boolean)? = null,
    ) {
        val effectiveSourceUrl = sourceUrl ?: return
        val infoMap = getExploreInfoMap(effectiveSourceUrl)
        executeAction(
            action,
            title,
            effectiveSourceUrl,
            infoMap,
            activity,
            onRefreshKinds,
            onOpenLogin,
            onShowBrowser,
        )
    }

    suspend fun executeAction(
        action: String?,
        title: String,
        sourceUrl: String?,
        infoMap: InfoMap?,
        activity: AppCompatActivity?,
        onRefreshKinds: () -> Unit,
        onOpenLogin: (() -> Boolean)? = null,
        onShowBrowser: ((url: String, html: String?, preloadJs: String?, config: String?) -> Boolean)? = null,
    ) {
        val actionText = action?.takeIf { it.isNotBlank() } ?: return
        val effectiveSourceUrl = sourceUrl ?: return
        val effectiveInfoMap = infoMap ?: return
        val source = getOrLoadBookSource(effectiveSourceUrl) ?: return
        val sourceJsExtensions = SourceLoginJsExtensions(
            activity = activity,
            source = source,
            callback = object : SourceLoginJsExtensions.Callback {
                override fun upUiData(data: Map<String, Any?>?) = Unit
                override fun reUiView(deltaUp: Boolean) = onRefreshKinds()
                override fun openLogin(
                    url: String?,
                    title: String?,
                    origin: String?,
                ): Boolean = onOpenLogin?.invoke() == true

                override fun showBrowser(
                    url: String,
                    html: String?,
                    preloadJs: String?,
                    config: String?,
                ): Boolean = onShowBrowser?.invoke(url, html, preloadJs, config) == true

                override fun startBrowser(
                    url: String,
                    title: String,
                    html: String?,
                ): Boolean = onShowBrowser?.invoke(url, html, null, null) == true
            }
        )
        withContext(Dispatchers.IO) {
            evalButtonClick(actionText, source, effectiveInfoMap, title, sourceJsExtensions)
        }
    }

    private fun parseLiteralViewName(viewName: String?): String? {
        if (viewName.isNullOrEmpty()) return null
        return if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
            viewName.substring(1, viewName.length - 1)
        } else {
            null
        }
    }

    private suspend fun getOrLoadBookSource(sourceUrl: String): BookSource? {
        sourceCache[sourceUrl]?.let { return it }
        return withContext(Dispatchers.IO) {
            bookSourceDao.getBookSource(sourceUrl)
        }?.also { source ->
            sourceCache[sourceUrl] = source
        }
    }

    private suspend fun evalUiJs(jsStr: String, sourceUrl: String, infoMap: InfoMap): String? =
        withContext(Dispatchers.IO) {
            val source = getOrLoadBookSource(sourceUrl) ?: return@withContext null
            runScriptWithContext {
            source.evalJS(jsStr) {
                put("infoMap", infoMap)
            }?.toString()
        }
    }

    private suspend fun evalButtonClick(
        jsStr: String,
        source: BaseSource?,
        infoMap: InfoMap,
        name: String,
        java: SourceLoginJsExtensions
    ) {
        val source = source ?: return
        try {
            runScriptWithContext {
                source.evalJS(jsStr) {
                    put("java", java)
                    put("infoMap", infoMap)
                }
            }
        } catch (e: Exception) {
            AppLog.put("ExploreUI Button $name JavaScript error", e)
        }
    }
}
