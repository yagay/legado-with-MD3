#!/usr/bin/env python3
from pathlib import Path
p = Path('app/src/main/java/io/legado/app/ui/book/read/ReadBookController.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('result.items.joinToString("\n\n")', 'result.items.joinToString("\\n\\n")')
s = s.replace('append("\n$it")', 'append("\\n$it")')
s = s.replace('append("\n  ↳ ${reply.name.orEmpty().ifBlank { "匿名" }}: ${reply.content.orEmpty()}")', 'append("\\n  ↳ ${reply.name.orEmpty().ifBlank { "匿名" }}: ${reply.content.orEmpty()}")')
p.write_text(s, encoding='utf-8')
print('fixed review dialog escaped newlines')
