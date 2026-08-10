# Upstream merge workflow

The custom layer is deliberately boring: custom implementations live in `app/src/custom/**`; only a
small list of upstream files acts as integration points.

## Recommended workflow

```bash
git fetch upstream
git checkout -b merge-upstream-YYYYMMDD
git merge upstream/main
python tools/custom/check_custom_layer.py
```

Then review the files in `CUSTOM_PATCHES.md` and compile:

```bash
./gradlew :app:compileAppDebugKotlin
```

On Windows:

```bat
gradlew.bat :app:compileAppDebugKotlin
```

## Conflict rules

- `app/src/custom/**`: keep the fork version unless you intentionally migrate/delete a custom
  feature.
- Upstream-owned files listed in `CUSTOM_PATCHES.md`: accept the upstream structural change first,
  then re-apply the smallest custom bridge needed.
- Other upstream files: normally accept upstream.
- Never copy a whole old upstream file back just to restore one custom function. Re-add only the
  custom call/field that is still required.

## Quick verification

1. Open Discover and switch list/waterfall layouts.
2. Restart the app and verify the chosen layout persists.
3. Open “自定义配置”.
4. Test background backup if enabled.
5. Test WebDAV book export with a local file larger and smaller than the remote file.
6. Run `python tools/custom/check_custom_layer.py` before committing the merge.
