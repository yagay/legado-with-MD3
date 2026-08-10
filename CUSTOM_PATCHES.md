# Custom patch list

This file is the merge checklist for fork-only features. The goal is simple: keep custom
implementations in `app/src/custom/**` and keep modifications to upstream-owned files small and
visible.

## Custom source-set integration

- `app/build.gradle.kts`
  - Adds `src/custom/java` and `src/custom/res` to the `main` source set.
  - This is the only build-system change required for the custom layer.

## Explore / waterfall integration

The implementation lives in `app/src/custom/**`. These upstream-owned integration files still need
to be reviewed after an upstream merge:

- `app/src/main/java/io/legado/app/data/repository/ExploreRepository.kt`
  - Calls the custom `ExploreSourceParser` before exposing explore kinds.
- `app/src/main/java/io/legado/app/ui/main/explore/ExploreScreen.kt`
  - Switches to `DiscoverySuiteScreen` for the custom waterfall/discovery layout.
- `app/src/main/java/io/legado/app/ui/main/explore/ExploreViewModel.kt`
  - Owns the current bridge state between upstream explore data and DiscoverySuite, including
    waterfall search and persisted layout selection.

## Custom configuration entry

The screen/ViewModel implementation lives in `app/src/custom/**`. Upstream-owned entry points:

- `app/src/main/java/io/legado/app/ui/config/ConfigNavScreen.kt`
  - Adds the “自定义配置” entry.
- `app/src/main/java/io/legado/app/ui/main/MainNavKey.kt`
  - Adds the custom-config route key.
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
  - Registers `CustomConfigScreen`.
- `app/src/main/java/io/legado/app/ui/main/my/MyViewModel.kt`
  - Adds custom-config items to settings search.
- `app/src/main/java/io/legado/app/di/appModule.kt`
  - Registers `CustomSettingsGateway`, `CustomSettingsRepository`, and `CustomConfigViewModel`.

## Background backup integration

- `app/src/main/java/io/legado/app/App.kt`
  - Registers `BackupLifecycleObserver` with `ProcessLifecycleOwner`.
- `app/src/main/java/io/legado/app/help/storage/Backup.kt`
  - Uses background-state checks and can trigger book export after backup.
- `app/src/main/java/io/legado/app/domain/model/settings/BackupSettings.kt`
- `app/src/main/java/io/legado/app/data/repository/FeatureSettingsRepositories.kt`
- `app/src/main/java/io/legado/app/constant/PreferKey.kt`
  - Currently contain the background-backup preference bridge. These are candidates for a later
    small cleanup into `CustomSettings`, but are intentionally left unchanged in this first
    separation pass to avoid behavioral regressions.

## WebDAV book export/import integration

- `app/src/main/java/io/legado/app/help/storage/Backup.kt`
  - Starts book export with `exportToWebDav=true`.
- `app/src/main/java/io/legado/app/help/storage/Restore.kt`
  - Optionally imports books from WebDAV after restore.
- `app/src/main/java/io/legado/app/service/ExportBookService.kt`
  - Bridges exported local files to WebDAV upload.
- `app/src/main/java/io/legado/app/help/AppWebDav.kt`
  - Contains the current book upload/import helper methods and remote-size protection logic.
- `app/src/main/java/io/legado/app/domain/model/settings/BookExportSettings.kt`
- `app/src/main/java/io/legado/app/data/repository/BookExportSettingsRepository.kt`
- `app/src/main/java/io/legado/app/constant/PreferKey.kt`
  - Persist export-related switches.

## Merge policy

When merging upstream, prefer upstream changes for ordinary upstream code, then manually re-check
only the files listed above. Do not resolve conflicts by replacing `app/src/custom/**` with upstream
content. Upstream currently has no ownership of this source set.
