#!/usr/bin/env python3
"""Static sanity checks for the fork-only source layer.

This intentionally does not require Gradle or Android SDK. Run it immediately after an upstream
merge before doing the normal Kotlin compile.
"""
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"
CUSTOM = ROOT / "app/src/custom"
BUILD = ROOT / "app/build.gradle.kts"
PATCHES = ROOT / "CUSTOM_PATCHES.md"

REQUIRED_PATCH_FILES = [
    "app/src/main/java/io/legado/app/App.kt",
    "app/src/main/java/io/legado/app/di/appModule.kt",
    "app/src/main/java/io/legado/app/data/repository/ExploreRepository.kt",
    "app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt",
    "app/src/main/java/io/legado/app/ui/main/explore/ExploreViewModel.kt",
    "app/src/main/java/io/legado/app/ui/config/ConfigNavScreen.kt",
    "app/src/main/java/io/legado/app/ui/main/MainNavKey.kt",
    "app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt",
    "app/src/main/java/io/legado/app/ui/main/my/MyViewModel.kt",
    "app/src/main/java/io/legado/app/help/storage/Backup.kt",
    "app/src/main/java/io/legado/app/help/storage/Restore.kt",
    "app/src/main/java/io/legado/app/service/ExportBookService.kt",
    "app/src/main/java/io/legado/app/help/AppWebDav.kt",
]

errors: list[str] = []
warnings: list[str] = []

if not CUSTOM.exists():
    errors.append("app/src/custom is missing")

build_text = BUILD.read_text(encoding="utf-8", errors="ignore") if BUILD.exists() else ""
for needle in ('src/custom/java', 'src/custom/res'):
    if needle not in build_text:
        errors.append(f"app/build.gradle.kts no longer includes {needle}")

# Same relative source path in both roots will usually produce duplicate Kotlin/resource definitions.
for kind in ("java", "res"):
    main_root = MAIN / kind
    custom_root = CUSTOM / kind
    if not custom_root.exists():
        continue
    for custom_file in custom_root.rglob("*"):
        if not custom_file.is_file():
            continue
        rel = custom_file.relative_to(custom_root)
        if (main_root / rel).exists():
            errors.append(f"duplicate custom/upstream path: {kind}/{rel.as_posix()}")

for rel in REQUIRED_PATCH_FILES:
    if not (ROOT / rel).exists():
        warnings.append(f"integration file moved/removed by upstream: {rel}")

if not PATCHES.exists():
    errors.append("CUSTOM_PATCHES.md is missing")

custom_kt = list((CUSTOM / "java").rglob("*.kt")) if (CUSTOM / "java").exists() else []
custom_res = [p for p in (CUSTOM / "res").rglob("*") if p.is_file()] if (CUSTOM / "res").exists() else []

print(f"custom Kotlin files : {len(custom_kt)}")
print(f"custom resource files: {len(custom_res)}")
print(f"integration files   : {len(REQUIRED_PATCH_FILES)}")

for item in warnings:
    print(f"WARNING: {item}")
for item in errors:
    print(f"ERROR: {item}")

if errors:
    sys.exit(1)
print("Custom layer static check: OK")
