from pathlib import Path

contract = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoContract.kt')
text = contract.read_text()
old = '''    val key: String,\n    val name: String,\n    val avatarUrl: String?,'''
new = '''    val key: String,\n    val reviewId: String? = null,\n    val name: String,\n    val avatarUrl: String?,'''
if old not in text: raise SystemExit('item id anchor missing')
text = text.replace(old,new,1)
old = '''    val replyCount: Int?,\n    val replies: List<BookReviewItemUi> = emptyList(),\n)'''
new = '''    val replyCount: Int?,\n    val replies: List<BookReviewItemUi> = emptyList(),\n    val repliesLoading: Boolean = false,\n    val replyPage: Int = 0,\n    val canLoadMoreReplies: Boolean = false,\n)'''
if old not in text: raise SystemExit('item reply state anchor missing')
text = text.replace(old,new,1)
old = '''    data class BookReviewAudioClick(val audioUrl: String) : BookInfoIntent\n    data object RemarkClick : BookInfoIntent'''
new = '''    data class BookReviewAudioClick(val audioUrl: String) : BookInfoIntent\n    data class LoadBookReviewReplies(val itemKey: String) : BookInfoIntent\n    data object RemarkClick : BookInfoIntent'''
if old not in text: raise SystemExit('reply intent anchor missing')
contract.write_text(text.replace(old,new,1))

vm = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt')
text = vm.read_text()
old = '''    private var bookReviewLoadJob: Job? = null\n    private var currentBookReviewRule: ReviewRule? = null'''
new = '''    private var bookReviewLoadJob: Job? = null\n    private val bookReviewReplyJobs = mutableMapOf<String, Job>()\n    private var currentBookReviewRule: ReviewRule? = null'''
if old not in text: raise SystemExit('vm reply jobs anchor missing')
text = text.replace(old,new,1)
old = '''            is BookInfoIntent.BookReviewAudioClick -> bookSource?.let { source ->\n                emitEffect(BookInfoEffect.PlayBookReviewAudio(intent.audioUrl, source))\n            }\n            BookInfoIntent.RemarkClick ->'''
new = '''            is BookInfoIntent.BookReviewAudioClick -> bookSource?.let { source ->\n                emitEffect(BookInfoEffect.PlayBookReviewAudio(intent.audioUrl, source))\n            }\n            is BookInfoIntent.LoadBookReviewReplies -> loadBookReviewReplies(intent.itemKey)\n            BookInfoIntent.RemarkClick ->'''
if old not in text: raise SystemExit('vm reply intent anchor missing')
text = text.replace(old,new,1)
old = '''        bookReviewCountJob?.cancel()\n        bookReviewLoadJob?.cancel()\n        bookReviewPage = 0'''
new = '''        bookReviewCountJob?.cancel()\n        bookReviewLoadJob?.cancel()\n        bookReviewReplyJobs.values.forEach(Job::cancel)\n        bookReviewReplyJobs.clear()\n        bookReviewPage = 0'''
if old not in text: raise SystemExit('refresh cancel anchor missing')
text = text.replace(old,new,1)
old = '''                val mapped = result.items.mapIndexed { index, item -> item.toBookReviewItemUi(page, index) }'''
new = '''                val mapped = result.items.mapIndexed { index, item ->\n                    item.toBookReviewItemUi(page, index, result.hasReplyUrl)\n                }'''
if old not in text: raise SystemExit('mapped anchor missing')
text = text.replace(old,new,1)
old = '''    private fun io.legado.app.model.analyzeRule.ReviewRuleParser.DetailItem.toBookReviewItemUi(page: Int, index: Int): BookReviewItemUi {\n        val fallback = "$page:$index:${name.orEmpty()}:${content.orEmpty().hashCode()}"\n        return BookReviewItemUi(\n            key = id?.takeIf { it.isNotBlank() } ?: fallback,\n            name = name.orEmpty(),'''
new = '''    private fun io.legado.app.model.analyzeRule.ReviewRuleParser.DetailItem.toBookReviewItemUi(\n        page: Int,\n        index: Int,\n        hasReplyUrl: Boolean = false,\n    ): BookReviewItemUi {\n        val stableReviewId = id?.takeIf { it.isNotBlank() }\n        val fallback = "$page:$index:${name.orEmpty()}:${content.orEmpty().hashCode()}"\n        return BookReviewItemUi(\n            key = stableReviewId ?: fallback,\n            reviewId = stableReviewId,\n            name = name.orEmpty(),'''
if old not in text: raise SystemExit('mapper signature anchor missing')
text = text.replace(old,new,1)
old = '''            replyCount = replyCount,\n            replies = replies.mapIndexed { replyIndex, reply -> reply.toBookReviewItemUi(page, index * 1000 + replyIndex + 1) },\n        )\n    }\n\n    private fun dismissSheet()'''
new = '''            replyCount = replyCount,\n            replies = replies.mapIndexed { replyIndex, reply ->\n                reply.toBookReviewItemUi(page, index * 1000 + replyIndex + 1)\n            },\n            canLoadMoreReplies = hasReplyUrl && stableReviewId != null &&\n                (replyCount ?: 0) > replies.size,\n        )\n    }\n\n    private fun loadBookReviewReplies(itemKey: String) {\n        val source = bookSource ?: return\n        val book = currentBook ?: return\n        val rule = currentBookReviewRule ?: return\n        val item = _screenState.value.bookReview.items.firstOrNull { it.key == itemKey } ?: return\n        val reviewId = item.reviewId ?: return\n        if (!item.canLoadMoreReplies || item.repliesLoading || bookReviewReplyJobs[itemKey]?.isActive == true) return\n        val page = item.replyPage + 1\n        _screenState.update { state ->\n            state.copy(bookReview = state.bookReview.copy(items = state.bookReview.items.map { current ->\n                if (current.key == itemKey) current.copy(repliesLoading = true) else current\n            }))\n        }\n        val context = ReviewContext.BookReview(source, book)\n        val chapter = context.chapterForAnalyze()\n        val targetSource = source.getKey()\n        val targetBook = book.bookUrl\n        val targetRule = rule.hashCode()\n        bookReviewReplyJobs[itemKey] = viewModelScope.launch(IO) {\n            val result = runCatching {\n                ReviewLoader.loadReplies(\n                    ReviewLoader.ReplyRequest(\n                        source = source,\n                        book = book,\n                        chapter = chapter,\n                        paragraphIndex = -1,\n                        paragraphData = "",\n                        reviewId = reviewId,\n                        page = page,\n                        ruleHash = targetRule,\n                        ruleOverride = rule,\n                    ),\n                    coroutineContext,\n                )\n            }.getOrNull()\n            withContext(Main) {\n                bookReviewReplyJobs.remove(itemKey)\n                if (bookSource?.getKey() != targetSource || currentBook?.bookUrl != targetBook || currentBookReviewRule?.hashCode() != targetRule) return@withContext\n                _screenState.update { state ->\n                    state.copy(bookReview = state.bookReview.copy(items = state.bookReview.items.map { current ->\n                        if (current.key != itemKey) return@map current\n                        if (result == null) return@map current.copy(repliesLoading = false, canLoadMoreReplies = false)\n                        val mappedReplies = result.replies.mapIndexed { index, reply ->\n                            reply.toBookReviewItemUi(page, index)\n                        }\n                        val merged = (current.replies + mappedReplies).distinctBy { it.key }\n                        val total = current.replyCount ?: merged.size\n                        current.copy(\n                            replies = merged,\n                            repliesLoading = false,\n                            replyPage = page,\n                            canLoadMoreReplies = mappedReplies.isNotEmpty() && merged.size < total,\n                        )\n                    }))\n                }\n            }\n        }\n    }\n\n    private fun dismissSheet()'''
if old not in text: raise SystemExit('mapper tail anchor missing')
vm.write_text(text.replace(old,new,1))

screen = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoScreen.kt')
text = screen.read_text()
old = '''            onImageClick = { onIntent(BookInfoIntent.BookReviewImageClick(it)) },\n            onAudioClick = { onIntent(BookInfoIntent.BookReviewAudioClick(it)) },\n        )'''
new = '''            onImageClick = { onIntent(BookInfoIntent.BookReviewImageClick(it)) },\n            onAudioClick = { onIntent(BookInfoIntent.BookReviewAudioClick(it)) },\n            onLoadReplies = { onIntent(BookInfoIntent.LoadBookReviewReplies(it)) },\n        )'''
if old not in text: raise SystemExit('screen replies callback anchor missing')
screen.write_text(text.replace(old,new,1))

ui = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoReviewUi.kt')
text = ui.read_text()
old = '''    onImageClick: (String) -> Unit,\n    onAudioClick: (String) -> Unit,\n) {'''
new = '''    onImageClick: (String) -> Unit,\n    onAudioClick: (String) -> Unit,\n    onLoadReplies: (String) -> Unit,\n) {'''
if old not in text: raise SystemExit('ui sheet replies args missing')
text = text.replace(old,new,1)
text = text.replace('BookReviewItem(item = item, onImageClick = onImageClick, onAudioClick = onAudioClick)', 'BookReviewItem(item = item, onImageClick = onImageClick, onAudioClick = onAudioClick, onLoadReplies = onLoadReplies)', 1)
old = '''    onImageClick: (String) -> Unit,\n    onAudioClick: (String) -> Unit,\n) {\n    var repliesExpanded'''
new = '''    onImageClick: (String) -> Unit,\n    onAudioClick: (String) -> Unit,\n    onLoadReplies: (String) -> Unit,\n) {\n    var repliesExpanded'''
if old not in text: raise SystemExit('ui item replies args missing')
text = text.replace(old,new,1)
old = '''        if (item.replies.isNotEmpty()) {\n            TextButton(\n                onClick = { repliesExpanded = !repliesExpanded },\n                modifier = Modifier.padding(start = 34.dp),\n            ) {\n                Text(if (repliesExpanded) "收起回复" else "展开 ${item.replies.size} 条回复")\n            }\n            if (repliesExpanded) {'''
new = '''        if (item.replies.isNotEmpty() || item.canLoadMoreReplies || item.repliesLoading) {\n            TextButton(\n                onClick = {\n                    val opening = !repliesExpanded\n                    repliesExpanded = opening\n                    if (opening && item.replies.isEmpty() && item.canLoadMoreReplies && !item.repliesLoading) {\n                        onLoadReplies(item.key)\n                    }\n                },\n                modifier = Modifier.padding(start = 34.dp),\n            ) {\n                val count = item.replyCount?.takeIf { it > 0 } ?: item.replies.size\n                Text(if (repliesExpanded) "收起回复" else "展开 $count 条回复")\n            }\n            if (repliesExpanded) {'''
if old not in text: raise SystemExit('ui expand block anchor missing')
text = text.replace(old,new,1)
old = '''                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {\n                        item.replies.forEach { reply ->\n                            ReviewReplyItem(reply, onImageClick, onAudioClick)\n                        }\n                    }'''
new = '''                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {\n                        if (item.repliesLoading && item.replies.isEmpty()) {\n                            Text("正在加载回复…", style = MaterialTheme.typography.bodySmall)\n                        }\n                        item.replies.forEach { reply ->\n                            ReviewReplyItem(reply, onImageClick, onAudioClick)\n                        }\n                        if (item.canLoadMoreReplies && item.replies.isNotEmpty()) {\n                            TextButton(\n                                onClick = { onLoadReplies(item.key) },\n                                enabled = !item.repliesLoading,\n                            ) {\n                                Text(if (item.repliesLoading) "正在加载…" else "加载更多回复")\n                            }\n                        }\n                    }'''
if old not in text: raise SystemExit('ui reply body anchor missing')
ui.write_text(text.replace(old,new,1))
print('upgraded remote book review replies')
