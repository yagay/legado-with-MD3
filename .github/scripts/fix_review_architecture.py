#!/usr/bin/env python3
from pathlib import Path
p = Path('app/src/main/java/io/legado/app/ui/book/read/ReadBookController.kt')
s = p.read_text(encoding='utf-8')
old1 = '''                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: return@runCatching null\n                if (chapter.isVolume) return@runCatching null\n                val analyzeUrl = AnalyzeUrl(\n                    summaryUrl,'''
new1 = '''                val chapter = ReadBook.curTextChapter?.chapter\n                    ?.takeIf { it.index == chapterIndex }\n                    ?: return@runCatching null\n                if (chapter.isVolume) return@runCatching null\n                val analyzeUrl = AnalyzeUrl(\n                    summaryUrl,'''
old2 = '''                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: return@runCatching null\n                val paraIndex = paragraphNum.toString()\n                val analyzeUrl = AnalyzeUrl(\n                    detailUrl,'''
new2 = '''                val chapter = ReadBook.curTextChapter?.chapter\n                    ?.takeIf { it.index == chapterIndex }\n                    ?: return@runCatching null\n                val paraIndex = paragraphNum.toString()\n                val analyzeUrl = AnalyzeUrl(\n                    detailUrl,'''
if old1 not in s:
    raise SystemExit('summary DAO anchor not found')
if old2 not in s:
    raise SystemExit('detail DAO anchor not found')
s = s.replace(old1, new1, 1).replace(old2, new2, 1)
p.write_text(s, encoding='utf-8')
print('removed review-only UI DAO access')
