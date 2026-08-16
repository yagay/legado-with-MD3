package io.legado.app.model.webBook

import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.addType
import io.legado.app.help.book.removeAllBookType
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.StrResponse
import io.legado.app.help.source.getBookType
import io.legado.app.help.source.getExploreInfoMap
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.RuleData
import io.legado.app.model.analyzeRule.RuleDataInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@Suppress("MemberVisibilityCanBePrivate")
object WebBook {

    /**
     * 搜索
     */
    fun searchBook(
        scope: CoroutineScope,
        bookSource: BookSource,
        key: String,
        page: Int? = 1,
        context: CoroutineContext = Dispatchers.IO,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        executeContext: CoroutineContext = Dispatchers.Main,
    ): Coroutine<ArrayList<SearchBook>> {
        return Coroutine.async(scope, context, start = start, executeContext = executeContext) {
            searchBookAwait(bookSource, key, page)
        }
    }

    suspend fun searchBookAwait(
        bookSource: BookSource,
        key: String,
        page: Int? = 1,
        filter: ((name: String, author: String, kind: String?) -> Boolean)? = null,
        shouldBreak: ((size: Int) -> Boolean)? = null
    ): ArrayList<SearchBook> {
        val searchUrl = bookSource.searchUrl
        if (searchUrl.isNullOrBlank()) {
            throw NoStackTraceException("搜索url不能为空")
        }
        val ruleData = RuleData()
        val analyzeUrl = AnalyzeUrl(
            mUrl = searchUrl,
            key = key,
            page = page,
            baseUrl = bookSource.bookSourceUrl,
            source = bookSource,
            ruleData = ruleData,
            coroutineContext = coroutineContext
        )
        val res = requestWithLoginCheck(analyzeUrl, bookSource)
        checkRedirect(bookSource, res)
        return BookList.analyzeBookList(
            bookSource = bookSource,
            ruleData = ruleData,
            analyzeUrl = analyzeUrl,
            baseUrl = res.url,
            body = res.body,
            isSearch = true,
            isRedirect = res.raw.priorResponse?.isRedirect == true,
            filter = filter,
            shouldBreak = shouldBreak
        )
    }

    /**
     * 发现
     */
    fun exploreBook(
        scope: CoroutineScope,
        bookSource: BookSource,
        url: String,
        page: Int? = 1,
        context: CoroutineContext = Dispatchers.IO,
    ): Coroutine<List<SearchBook>> {
        return Coroutine.async(scope, context) {
            exploreBookAwait(bookSource, url, page)
        }
    }

    suspend fun exploreBookAwait(
        bookSource: BookSource,
        url: String,
        page: Int? = 1,
        ruleData: RuleDataInterface = RuleData(),
        key: String? = null,
        isSearch: Boolean = false,
    ): ArrayList<SearchBook> {
        val sourceUrl = bookSource.bookSourceUrl
        val analyzeUrl = AnalyzeUrl(
            mUrl = url,
            key = key,
            page = page,
            baseUrl = sourceUrl,
            source = bookSource,
            ruleData = ruleData,
            coroutineContext = currentCoroutineContext(),
            infoMap = getExploreInfoMap(sourceUrl)
        )
        val res = requestWithLoginCheck(analyzeUrl, bookSource)
        checkRedirect(bookSource, res)
        val listRuleData = when (ruleData) {
            is RuleData -> ruleData
            is Book -> RuleData()
            else -> RuleData()
        }
        return BookList.analyzeBookList(
            bookSource = bookSource,
            ruleData = listRuleData,
            analyzeUrl = analyzeUrl,
            baseUrl = res.url,
            body = res.body,
            isSearch = isSearch
        )
    }

    suspend fun exploreBookSuspend(
        bookSource: BookSource,
        url: String,
        page: Int? = 1,
        ruleData: RuleDataInterface = RuleData(),
        key: String? = null,
        isSearch: Boolean = false,
    ): List<SearchBook> {
        return exploreBookAwait(bookSource, url, page, ruleData, key, isSearch)
    }

    suspend fun exploreBookWithResolvedUrl(
        bookSource: BookSource,
        url: String,
        page: Int? = 1,
        ruleData: RuleDataInterface = RuleData(),
    ): Pair<String, ArrayList<SearchBook>> {
        val sourceUrl = bookSource.bookSourceUrl
        val analyzeUrl = AnalyzeUrl(
            mUrl = url,
            page = page,
            baseUrl = sourceUrl,
            source = bookSource,
            ruleData = ruleData,
            coroutineContext = currentCoroutineContext(),
            infoMap = getExploreInfoMap(sourceUrl)
        )
        val res = requestWithLoginCheck(analyzeUrl, bookSource)
        checkRedirect(bookSource, res)
        val resolvedUrl = res.url
        val listRuleData = when (ruleData) {
            is RuleData -> ruleData
            is Book -> RuleData()
            else -> RuleData()
        }
        val books = BookList.analyzeBookList(
            bookSource = bookSource,
            ruleData = listRuleData,
            analyzeUrl = analyzeUrl,
            baseUrl = res.url,
            body = res.body,
            isSearch = false
        )
        return resolvedUrl to books
    }

    suspend fun exploreBookWithRuleData(
        bookSource: BookSource,
        url: String,
        page: Int? = 1,
    ): ArrayList<SearchBook> {
        val sourceUrl = bookSource.bookSourceUrl
        val ruleData = RuleData()
        val analyzeUrl = AnalyzeUrl(
            mUrl = url,
            page = page,
            baseUrl = sourceUrl,
            source = bookSource,
            ruleData = ruleData,
            coroutineContext = currentCoroutineContext(),
            infoMap = getExploreInfoMap(sourceUrl)
        )
        val res = requestWithLoginCheck(analyzeUrl, bookSource)
        checkRedirect(bookSource, res)
        return BookList.analyzeBookList(
            bookSource = bookSource,
            ruleData = ruleData,
            analyzeUrl = analyzeUrl,
            baseUrl = res.url,
            body = res.body,
            isSearch = false
        )
    }

    /**
     * 书籍信息
     */
    fun getBookInfo(
        scope: CoroutineScope,
        bookSource: BookSource,
        book: Book,
        context: CoroutineContext = Dispatchers.IO,
        canReName: Boolean = true,
    ): Coroutine<Book> {
        return Coroutine.async(scope, context) {
            getBookInfoAwait(bookSource, book, canReName)
        }
    }

    suspend fun getBookInfoAwait(
        bookSource: BookSource,
        book: Book,
        canReName: Boolean = true,
    ): Book {
        if (!book.config.fixedType) {
            book.removeAllBookType()
            book.addType(bookSource.getBookType())
        }
        if (!book.infoHtml.isNullOrEmpty()) {
            BookInfo.analyzeBookInfo(
                bookSource = bookSource,
                book = book,
                baseUrl = book.bookUrl,
                redirectUrl = book.bookUrl,
                body = book.infoHtml,
                canReName = canReName
            )
        } else {
            val analyzeUrl = AnalyzeUrl(
                mUrl = book.bookUrl,
                baseUrl = bookSource.bookSourceUrl,
                source = bookSource,
                ruleData = book,
                coroutineContext = coroutineContext
            )
            val res = requestWithLoginCheck(analyzeUrl, bookSource)
            checkRedirect(bookSource, res)
            BookInfo.analyzeBookInfo(
                bookSource = bookSource,
                book = book,
                baseUrl = book.bookUrl,
                redirectUrl = res.url,
                body = res.body,
                canReName = canReName
            )
        }
        return book
    }

    /**
     * 目录
     */
    fun getChapterList(
        scope: CoroutineScope,
        bookSource: BookSource,
        book: Book,
        runPerJs: Boolean = false,
        context: CoroutineContext = Dispatchers.IO,
        isFromBookInfo: Boolean = false
    ): Coroutine<List<BookChapter>> {
        return Coroutine.async(scope, context) {
            getChapterListAwait(bookSource, book, runPerJs, isFromBookInfo).getOrThrow()
        }
    }

    suspend fun runPreUpdateJs(
        bookSource: BookSource,
        book: Book,
        isFromBookInfo: Boolean = false
    ): Result<Unit> {
        return kotlin.runCatching {
            val preUpdateJs = bookSource.ruleToc?.preUpdateJs
            if (!preUpdateJs.isNullOrBlank()) {
                AnalyzeRule(book, bookSource, true, isFromBookInfo)
                    .setCoroutineContext(coroutineContext)
                    .evalJS(preUpdateJs)
            }
        }.onFailure {
            coroutineContext.ensureActive()
            AppLog.put("执行preUpdateJs规则失败 书源:${bookSource.bookSourceName}", it)
        }
    }

    suspend fun getChapterListAwait(
        bookSource: BookSource,
        book: Book,
        runPerJs: Boolean = false,
        isFromBookInfo: Boolean = false
    ): Result<List<BookChapter>> {
        if (!book.config.fixedType) {
            book.removeAllBookType()
            book.addType(bookSource.getBookType())
        }
        return kotlin.runCatching {
            if (runPerJs) {
                runPreUpdateJs(bookSource, book, isFromBookInfo).getOrThrow()
            }
            if (book.bookUrl == book.tocUrl && !book.tocHtml.isNullOrEmpty()) {
                BookChapterList.analyzeChapterList(
                    bookSource = bookSource,
                    book = book,
                    baseUrl = book.tocUrl,
                    redirectUrl = book.tocUrl,
                    body = book.tocHtml,
                    isFromBookInfo = isFromBookInfo
                )
            } else {
                val analyzeUrl = AnalyzeUrl(
                    mUrl = book.tocUrl,
                    baseUrl = book.bookUrl,
                    source = bookSource,
                    ruleData = book,
                    coroutineContext = coroutineContext
                )
                val res = requestWithLoginCheck(analyzeUrl, bookSource)
                checkRedirect(bookSource, res)
                BookChapterList.analyzeChapterList(
                    bookSource = bookSource,
                    book = book,
                    baseUrl = book.tocUrl,
                    redirectUrl = res.url,
                    body = res.body,
                    isFromBookInfo = isFromBookInfo
                )
            }
        }.onFailure {
            coroutineContext.ensureActive()
        }
    }

    /**
     * 章节内容
     */
    fun getContent(
        scope: CoroutineScope,
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        nextChapterUrl: String? = null,
        needSave: Boolean = true,
        context: CoroutineContext = Dispatchers.IO,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        executeContext: CoroutineContext = Dispatchers.Main,
        semaphore: Semaphore? = null,
    ): Coroutine<String> {
        return Coroutine.async(
            scope,
            context,
            start = start,
            executeContext = executeContext,
            semaphore = semaphore
        ) {
            getContentAwait(bookSource, book, bookChapter, nextChapterUrl, needSave)
        }
    }

    suspend fun getContentAwait(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        nextChapterUrl: String? = null,
        needSave: Boolean = true
    ): String {
        if (bookSource.getContentRule().content.isNullOrEmpty()) {
            Debug.log(bookSource.bookSourceUrl, "⇒正文规则为空,使用章节链接:${bookChapter.url}")
            return bookChapter.url
        }
        if (bookChapter.isVolume && bookChapter.url.startsWith(bookChapter.title)) {
            Debug.log(bookSource.bookSourceUrl, "⇒一级目录正文不解析规则")
            return bookChapter.tag ?: ""
        }
        return if (bookChapter.url == book.bookUrl && !book.tocHtml.isNullOrEmpty()) {
            BookContent.analyzeContent(
                bookSource = bookSource,
                book = book,
                bookChapter = bookChapter,
                baseUrl = bookChapter.getAbsoluteURL(),
                redirectUrl = bookChapter.getAbsoluteURL(),
                body = book.tocHtml,
                nextChapterUrl = nextChapterUrl,
                needSave = needSave
            )
        } else {
            val analyzeUrl = AnalyzeUrl(
                mUrl = bookChapter.getAbsoluteURL(),
                baseUrl = book.tocUrl,
                source = bookSource,
                ruleData = book,
                chapter = bookChapter,
                coroutineContext = coroutineContext
            )
            val res = requestWithLoginCheck(
                analyzeUrl = analyzeUrl,
                bookSource = bookSource,
                jsStr = bookSource.getContentRule().webJs,
                sourceRegex = bookSource.getContentRule().sourceRegex
            )
            checkRedirect(bookSource, res)
            BookContent.analyzeContent(
                bookSource = bookSource,
                book = book,
                bookChapter = bookChapter,
                baseUrl = bookChapter.getAbsoluteURL(),
                redirectUrl = res.url,
                body = res.body,
                nextChapterUrl = nextChapterUrl,
                needSave = needSave
            )
        }
    }

    /**
     * 与当前 Legado 对齐：请求失败时也把错误包装成 StrResponse 交给 loginCheckJs，
     * 允许书源自行处理 401/403/登录失效等响应；未处理的 500 继续抛原异常。
     */
    private suspend fun requestWithLoginCheck(
        analyzeUrl: AnalyzeUrl,
        bookSource: BookSource,
        jsStr: String? = null,
        sourceRegex: String? = null
    ): StrResponse {
        val checkJs = bookSource.loginCheckJs
        return kotlin.runCatching {
            analyzeUrl.getStrResponseAwait(jsStr = jsStr, sourceRegex = sourceRegex).let { response ->
                if (!checkJs.isNullOrBlank()) {
                    analyzeUrl.evalJS(checkJs, response) as StrResponse
                } else {
                    response
                }
            }
        }.getOrElse { throwable ->
            if (checkJs.isNullOrBlank()) throw throwable
            val errResponse = analyzeUrl.getErrStrResponse(throwable)
            try {
                (analyzeUrl.evalJS(checkJs, errResponse) as StrResponse).also { response ->
                    if (response.code() == 500) throw throwable
                }
            } catch (_: Throwable) {
                throw throwable
            }
        }
    }

    /**
     * 精准搜索
     */
    fun preciseSearch(
        scope: CoroutineScope,
        bookSourceParts: List<BookSourcePart>,
        name: String,
        author: String,
        context: CoroutineContext = Dispatchers.IO,
        semaphore: Semaphore? = null,
    ): Coroutine<Pair<Book, BookSource>> {
        return Coroutine.async(scope, context, semaphore = semaphore) {
            for (s in bookSourceParts) {
                val source = s.getBookSource() ?: continue
                val book = preciseSearchAwait(source, name, author).getOrNull()
                if (book != null) {
                    return@async Pair(book, source)
                }
            }
            throw NoStackTraceException("没有搜索到<$name>$author")
        }
    }

    suspend fun preciseSearchAwait(
        bookSource: BookSource,
        name: String,
        author: String,
    ): Result<Book> {
        return kotlin.runCatching {
            coroutineContext.ensureActive()
            searchBookAwait(
                bookSource, name,
                filter = { fName, fAuthor, _ -> fName == name && fAuthor == author },
                shouldBreak = { it > 0 }
            ).firstOrNull()?.let { searchBook ->
                coroutineContext.ensureActive()
                return@runCatching searchBook.toBook()
            }
            throw NoStackTraceException("未搜索到 $name($author) 书籍")
        }.onFailure {
            coroutineContext.ensureActive()
        }
    }

    /**
     * 检测重定向
     */
    private fun checkRedirect(bookSource: BookSource, response: StrResponse) {
        response.raw.priorResponse?.let {
            if (it.isRedirect) {
                Debug.log(bookSource.bookSourceUrl, "≡检测到重定向(${it.code})")
                Debug.log(bookSource.bookSourceUrl, "┌重定向后地址")
                Debug.log(bookSource.bookSourceUrl, "└${response.url}")
            }
        }
    }

}
