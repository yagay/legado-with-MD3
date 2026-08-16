from pathlib import Path

ui = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoReviewUi.kt')
text = ui.read_text()
old = '''        val meta = buildList {\n            item.likeCount?.takeIf { it > 0 }?.let { add("赞 $it") }\n            item.replyCount?.takeIf { it > 0 }?.let { add("回复 $it") }\n        }\n        if (meta.isNotEmpty()) {\n            Text(\n                meta.joinToString("  "),\n                style = MaterialTheme.typography.bodySmall,\n                modifier = Modifier.padding(start = 46.dp, top = 6.dp),\n            )\n        }\n        if (item.replies.isNotEmpty() || item.canLoadMoreReplies || item.repliesLoading) {\n'''
new = '''        val replyCount = item.replyCount?.takeIf { it > 0 }\n        item.likeCount?.takeIf { it > 0 }?.let { likeCount ->\n            Text(\n                "赞 $likeCount",\n                style = MaterialTheme.typography.bodySmall,\n                modifier = Modifier.padding(start = 46.dp, top = 6.dp),\n            )\n        }\n        val hasReplyEntry = replyCount != null || item.replies.isNotEmpty() || item.canLoadMoreReplies || item.repliesLoading\n        if (hasReplyEntry) {\n'''
if old not in text:
    raise SystemExit('ui meta pattern not found')
text = text.replace(old, new, 1)
old = '''                onClick = {\n                    val opening = !repliesExpanded\n                    repliesExpanded = opening\n                    if (opening && item.replies.isEmpty() && item.canLoadMoreReplies && !item.repliesLoading) {\n                        onLoadReplies(item.key)\n                    }\n                },\n                modifier = Modifier.padding(start = 34.dp),\n            ) {\n                val count = item.replyCount?.takeIf { it > 0 } ?: item.replies.size\n                Text(if (repliesExpanded) "收起回复" else "展开 $count 条回复")\n            }\n'''
new = '''                onClick = {\n                    val opening = !repliesExpanded\n                    repliesExpanded = opening\n                    if (opening && item.replies.isEmpty() && !item.repliesLoading) {\n                        onLoadReplies(item.key)\n                    }\n                },\n                modifier = Modifier.padding(start = 34.dp),\n            ) {\n                val count = replyCount ?: item.replies.size\n                Text(if (repliesExpanded) "收起回复" else "回复 $count  ·  查看")\n            }\n'''
if old not in text:
    raise SystemExit('ui reply button pattern not found')
text = text.replace(old, new, 1)
old = '''                        if (item.repliesLoading && item.replies.isEmpty()) {\n                            Text("正在加载回复…", style = MaterialTheme.typography.bodySmall)\n                        }\n                        item.replies.forEach { reply ->\n'''
new = '''                        if (item.repliesLoading && item.replies.isEmpty()) {\n                            Text("正在加载回复…", style = MaterialTheme.typography.bodySmall)\n                        } else if (!item.repliesLoading && item.replies.isEmpty()) {\n                            Text("暂无可显示的回复内容", style = MaterialTheme.typography.bodySmall)\n                        }\n                        item.replies.forEach { reply ->\n'''
if old not in text:
    raise SystemExit('ui loading pattern not found')
text = text.replace(old, new, 1)
ui.write_text(text)

vm = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt')
text = vm.read_text()
old = '''        val reviewId = item.reviewId ?: return\n        if (!item.canLoadMoreReplies || item.repliesLoading || bookReviewReplyJobs[itemKey]?.isActive == true) return\n        val page = item.replyPage + 1\n'''
new = '''        val reviewId = item.reviewId ?: return\n        val expectedReplyCount = item.replyCount ?: 0\n        val hasUnloadedReplies = expectedReplyCount > item.replies.size || item.canLoadMoreReplies\n        if (!hasUnloadedReplies || item.repliesLoading || bookReviewReplyJobs[itemKey]?.isActive == true) return\n        val page = item.replyPage + 1\n'''
if old not in text:
    raise SystemExit('vm reply guard pattern not found')
text = text.replace(old, new, 1)
vm.write_text(text)
print('fixed book review reply click')
