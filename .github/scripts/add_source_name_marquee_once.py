from pathlib import Path

menu = Path('app/src/main/java/io/legado/app/ui/widget/components/menuItem/RoundDropdownMenuItem.kt')
s = menu.read_text(encoding='utf-8')
s = s.replace(
    'import androidx.compose.foundation.combinedClickable\n',
    'import androidx.compose.foundation.combinedClickable\nimport androidx.compose.foundation.basicMarquee\n',
    1,
)
s = s.replace(
    '    onLongClick: (() -> Unit)? = null,\n) {',
    '    onLongClick: (() -> Unit)? = null,\n    marquee: Boolean = false,\n) {',
    1,
)
s = s.replace(
    '    val hasCustomContentColor = color != Color.Unspecified\n',
    '    val hasCustomContentColor = color != Color.Unspecified\n    val textModifier = Modifier\n        .widthIn(max = 200.dp)\n        .then(if (marquee) Modifier.basicMarquee() else Modifier)\n',
    1,
)
s = s.replace(
    '                        modifier = Modifier.widthIn(max = 200.dp),\n                        text = text,',
    '                        modifier = textModifier,\n                        text = text,\n                        maxLines = if (marquee) 1 else Int.MAX_VALUE,',
    1,
)
s = s.replace(
    '                        modifier = Modifier.widthIn(max = 200.dp),\n                        text = text,\n                        style = LegadoTheme.typography.labelLargeEmphasized,',
    '                        modifier = textModifier,\n                        text = text,\n                        maxLines = if (marquee) 1 else Int.MAX_VALUE,\n                        style = LegadoTheme.typography.labelLargeEmphasized,',
    1,
)
if s.count('marquee: Boolean = false') != 1 or s.count('basicMarquee()') != 1:
    raise SystemExit('menu marquee transformation failed')
menu.write_text(s, encoding='utf-8')

screen = Path('app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt')
t = screen.read_text(encoding='utf-8')
needle = '''                        RoundDropdownMenuItem(\n                            text = source.bookSourceName,\n                            isSelected = source.bookSourceUrl == state.enhance.selectedSuite?.defaultSourceUrl,'''
replacement = '''                        RoundDropdownMenuItem(\n                            text = source.bookSourceName,\n                            marquee = true,\n                            isSelected = source.bookSourceUrl == state.enhance.selectedSuite?.defaultSourceUrl,'''
if needle not in t:
    raise SystemExit('source picker item not found')
t = t.replace(needle, replacement, 1)
screen.write_text(t, encoding='utf-8')
