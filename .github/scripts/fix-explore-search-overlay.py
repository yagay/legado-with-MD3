from pathlib import Path

path = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt')
text = path.read_text(encoding='utf-8')
old = '                    stickyHeader(key = "source_menu_search") {'
new = '                    item(key = "source_menu_search") {'
if old not in text:
    raise SystemExit('target sticky search header not found')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
