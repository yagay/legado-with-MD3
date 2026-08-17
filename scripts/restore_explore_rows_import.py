from pathlib import Path
p = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt')
s = p.read_text(encoding='utf-8')
anchor = 'import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem\n'
line = 'import io.legado.app.ui.widget.components.explore.calculateExploreKindRows\n'
if line not in s:
    if anchor not in s:
        raise SystemExit('anchor not found')
    s = s.replace(anchor, anchor + line, 1)
p.write_text(s, encoding='utf-8')
