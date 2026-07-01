# Minecraft 26.2 Dependency Update — Completed

## Outcome

Baseline advanced from `26.2-pre-4` to stable **Minecraft 26.2** (2026-06-23).

| Coordinate | Value |
|------------|-------|
| Minecraft | `26.2` (latest stable) |
| Fabric Loader | `0.19.3` (latest stable for 26.2) |
| Fabric API | `0.153.0+26.2` (latest release) |
| Loom | `1.17.13` (release) |

## Delivered

- `gradle.properties`, `build.gradle` (Loom plugin), and `fabric.mod.json` updated to stable 26.2 pins.
- `TerrainGpuLayout` constants declared `static final` so `checkTerrainLayoutManifest` passes without widening its regex.
- `build.gradle` adds `sourcesJar` → `compileTerrainSpirv` (Gradle 9.5 implicit-dependency validation; Loom refreshed to 1.17.13 during the 2026-07-01 dependency audit).
- `MeshTerrainRenderer` visible-path fix: build mesh dispatch from current draw batch (fixes wrong prepared-dispatch FIFO corruption).
- `docs/RELIABILITY.md` GPU matrix row for 26.2 / `0.153.0+26.2`.
- DebugBridge `debugbridge-26.2-2.0.0.jar` only in `run/mods`; mod loads from Loom classpath during `runClient`.

## Validation

- `.\gradlew.bat checkRepoDocs`, `check`, and `build` pass.
- mcdev static inspection at MC 26.2 for mixin targets (`mcdev-mc-inspection-final.log`).
- mcdev two-cycle dev-loop: snapshot, screenshot, `mc_record_video` in overworld (`mcdev-runtime-cycle*-final.log`).

## Follow-Up

See `tech-debt-tracker.md`: full cross-GPU runtime matrix, non-terrain domains, build-time SPIR-V pipeline.
