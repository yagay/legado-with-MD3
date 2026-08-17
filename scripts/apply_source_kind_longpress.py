from pathlib import Path

path = Path("app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt")
text = path.read_text(encoding="utf-8")
old = "import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.padding"
new = "import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.layout.padding"
if old not in text:
    raise SystemExit("ExploreScreen heightIn import anchor not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("heightIn import added")
