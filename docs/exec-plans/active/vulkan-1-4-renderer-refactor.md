# Vulkan 1.4 Renderer Refactor

## Goal

Bring the client Vulkan renderer stack to a maintainable Vulkan 1.4 baseline
while continuing the current terrain meshlet, mesh shader, descriptor-buffer,
descriptor-heap, fragment-shading-rate, present-pacing, and video-option work
already in the repository.

The target outcome is a renderer that:

- builds and runs on the latest supported Minecraft/Fabric snapshot line;
- requires `VK_API_VERSION_1_4` for the new renderer paths;
- minimizes per-frame CPU work by making the CPU produce authoritative state
  deltas while the GPU owns visibility, meshing, work compaction, LOD traversal,
  material lookup, and draw generation wherever correctness allows;
- reports a clear runtime profile for required, preferred, and optional Vulkan
  features;
- owns capture, upload, draw scheduling, pipeline selection, synchronization,
  and shutdown through explicit renderer contracts;
- keeps Minecraft textures, atlases, baked models, render layers, tinting,
  animation, lightmaps, overlays, resource-pack reloads, and modded fallback
  behavior as first-class compatibility requirements;
- leaves a clear route for future ray tracing and path tracing by making RT/PT
  consume the same GPU world database, geometry pages, material IDs, texture
  descriptors, and dirty-event invalidation used by raster passes;
- falls back or fails closed with actionable diagnostics when required hardware,
  driver, or Minecraft backend support is missing;
- has repeatable build-time checks and a documented runtime validation matrix.

Terrain is the current proving ground, not the final scope. The refactor should
produce shared Vulkan services that can later support terrain, entities, block
entities, items, particles, sky, clouds, weather, translucent effects,
post-processing-adjacent passes, and renderer diagnostics without duplicating
feature negotiation, resource ownership, descriptor binding, shader compilation,
pipeline caching, synchronization, or fallback logic.

GPU-driven world data and rendering feasibility is tracked in
[../../design-docs/gpu-driven-worldgen-rendering.md](../../design-docs/gpu-driven-worldgen-rendering.md).
Use that decision record to keep authoritative Minecraft worldgen separate from
GPU-resident render data, LOD pages, visibility work queues, and optional
render-only distant terrain. The CPU-offload principle in that document is a
hard architectural goal for this refactor.

Radiance is a current external reference for Minecraft Vulkan hardware RT,
module-style post-processing, denoising/upscaling integration, and PBR/emission
texture handling. Use the Radiance notes in the design doc as design input, not
as an implementation dependency.

## Phase 1 Foundation – Complete (verified)

The following renderer contracts, build gates, and observability surfaces now exist and pass `.\gradlew.bat --no-daemon check`:

- `RendererLifecycleState` (`UNCONFIGURED` / `CONFIGURING` / `READY` / `FAILED` / `SHUTTING_DOWN` / `DEVICE_LOST`) on `MeshTerrainRenderer`.
- `RendererDomainRegistry` with explicit per-domain path + CPU/GPU work split diagnostics (terrain reports `mesh-shader-terrain`; all other domains default to `vanilla`).
- `VulkanRuntimeProfile` grouping hard requirements, preferred acceleration features, RT/PT-readiness features, selected paths, and disabled reasons (nested map output for diagnostics).
- `TerrainGpuLayout` as the single source of truth for CPU/GPU layout constants (meshlet headers, visible records, work queues, material table, push constants, indexed-triangle flags, etc.).
- `TerrainDrawContext`, `TerrainMeshTaskDispatch` (with `Source` variants + indirect command support), `TerrainVisibleMeshletRing` (wrap + fence-blocked reuse), `TerrainWorkQueueLayout`, and `TerrainMeshTaskCommandLayout`.
- Normal visible draw-list replacement now writes visible meshlet ranges into
  terrain work-queue records before the render pass, queues one prepared
  GPU-generated indirect mesh-task command per vanilla draw group, and keeps the
  older CPU-visible meshlet ring as a capacity/unavailable fallback. This moves
  the production visible terrain path onto the same record and command
  generation format used by full-layer diagnostics. CPU-prepared work queues
  use the command-generation push-count flag so multiple queued commands do not
  read a later upload's shared work-queue counter header.
- `GpuMaterialRecord` + `TerrainMaterialClassifier` (one default material per `ChunkSectionLayer` carrying alpha mode, render-layer ordinal, and domain metadata; 64-byte stride preserved).
- `DescriptorHeapTerrainResources` with GPU material table, retirement accounting (resource-set / buffer / byte counts), and upload/ring/work-queue/command diagnostics.
- 15+ mechanical verification tasks wired into the `check` lifecycle and all passing:
  - Repository & hygiene: `checkRepoDocs`, `checkClientMixinConfig`, `checkVulkanPackageBoundaries`
  - Shaders & layout: `checkTerrainShaders`, `checkTerrainLayoutManifest`
  - Pure-Java invariants (11+): terrain meshlet partitioning, renderer domains, material records, terrain material classification, runtime profile, Vulkan requirements, GPU retirement stats, terrain dispatch, visible meshlet ring, work queue layout, mesh-task command layout.
- GlBackendMixin updated for the 26.2-pre-2 `createDevice` signature (`Runnable criticalShaderLoader`).
- All client mixins listed in `vulkanimprovement.client.mixins.json` exist and the config stays in sync with source.

See `docs/exec-plans/tech-debt-tracker.md` for remaining follow-up (GPU validation matrix, active plan maintenance, runtime matrix coverage).

## Dependency Update Targets

Verified against Mojang, Fabric, Gradle, and LunarG metadata on 2026-05-30:

| Area                            | Current                          | Target                                                       | Source                                 |
|---------------------------------|----------------------------------|--------------------------------------------------------------|----------------------------------------|
| Minecraft latest snapshot       | `26.2-pre-2`                     | `26.2-pre-2`                                                 | Mojang version manifest / Fabric meta  |
| Minecraft latest release        | n/a                              | `26.1.2`                                                     | Mojang version manifest                |
| Fabric game metadata            | `26.2-pre-2`                     | `26.2-pre-2`                                                 | Fabric metadata                        |
| Fabric Loader                   | `0.19.2`                         | `0.19.2`                                                     | Fabric metadata, already latest stable for the snapshot |
| Fabric API                      | `0.150.1+26.2`                   | `0.150.1+26.2`                                               | Fabric Maven metadata                  |
| Fabric Loom                     | `1.17.0-alpha.13`                | `1.17.0-alpha.13`                                            | Fabric Maven metadata                  |
| Gradle wrapper                  | `9.5.1`                          | `9.5.1`                                                      | Gradle current-version service         |
| Java target                     | `25`                             | keep `25` until Minecraft/Fabric requires more               | Local build contract                   |
| Java runtime                    | JDK `25.0.3` used for validation | Java 26 allowed for local runs if installed correctly        | Local harness                          |
| LWJGL                           | Minecraft transitive `3.4.1`     | avoid direct override unless required                        | Dependency insight                     |
| Vulkan SDK and validation tools | local environment                | Vulkan SDK `1.4.350.0`                                       | Khronos/LunarG release                 |

Do not blindly downgrade Loom from the current alpha to the latest stable release
number. First run a compatibility dry-run against `26.2-snapshot-7`; if stable
Loom supports the target snapshot and mappings, use the stable release in a
separate dependency-only change. Otherwise keep the newest compatible alpha.

Useful verification endpoints:

- `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`
- `https://meta.fabricmc.net/v2/versions/game`
- `https://meta.fabricmc.net/v2/versions/loader`
- `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`
- `https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml`
- `https://services.gradle.org/versions/current`
- `https://www.lunarg.com/lunarg-releases-vulkan-sdk-1-4-350-0/`

## Constraints

- Preserve the source-set boundary: renderer behavior stays in `src/client`;
  `src/main` remains entrypoint and metadata only.
- Keep mixins thin. Minecraft and Mojang-Vulkan adaptation belongs in
  `mixin.client`; reusable renderer behavior belongs in `client.vulkan`.
- Keep `vulkanimprovement.client.mixins.json` synchronized with every mixin add,
  rename, or delete.
- Keep `VulkanFeatureRequirements`, `VulkanImprovementCapabilities`, and
  `ARCHITECTURE.md` aligned whenever Vulkan requirements or runtime contracts
  change.
- Treat `TerrainRendererDebugConfig` JVM flags as operational API; update
  `docs/RELIABILITY.md` for any additions, removals, or semantic changes.
- Do not replace Minecraft's Vulkan backend wholesale. Integrate through narrow
  adapter points and preserve a controlled fallback path during bootstrap.
- Do not directly override Minecraft's LWJGL stack unless the target snapshot
  lacks required Vulkan 1.4 bindings and the override has been validated across
  launch, shader compilation, and native library loading.

## Remaining Work

After Phase 1 foundation (contracts, layout manifests, material table, dispatch records, ring/work-queue abstractions, all mechanical checks and pure-Java invariants), the concrete next increments are:

- Stabilize the GPU-driven terrain path: validate the work-queue +
  GPU-generated indirect mesh-task command path across solid/cutout/translucent
  layers with proper truncation, fencing, and diagnostics.
- Harden per-draw-group visible work queues now that they are prepared before
  the terrain render pass: add stronger runtime validation for draw-group
  ordering, command-buffer submission ordering, and fallback behavior.
- Remove or clearly gate the broad CPU fallback paths once the GPU compaction path is proven correct.
- Begin non-terrain domain observation and incremental replacement (entities, block entities, particles, sky/clouds/weather) using the existing `RendererDomainRegistry` and lifecycle contracts. Prefer dirty-event + persistent GPU state over per-frame CPU enumeration.
- Synchronization hardening: replace implicit upload safety with explicit domain read fences or timeline-style tracking; remove `vim.waitIdleBeforeTerrainUpload` except as a validation escape hatch.
- User-facing controls and diagnostics: surface active renderer domains, descriptor path, FSR state, and a compact bug-report dump; keep JVM flags and video options consistent.
- Execute the full Validation matrix below (runClient + smoke tests + Vulkan validation layers + multi-GPU coverage) before declaring the mesh terrain path generally usable.
- Document the GPU validation matrix in `docs/RELIABILITY.md`.
- Maintain this plan: condense historical narrative or move to `completed/` and spawn smaller focused successor plans (see tech-debt-tracker).

## Validation

Run these gates in order:

1. `.\gradlew.bat --no-daemon checkRepoDocs`
2. `.\gradlew.bat --no-daemon build`
3. `.\gradlew.bat --no-daemon dependencies --configuration clientRuntimeClasspath`
4. `.\gradlew.bat --no-daemon runClient`
5. Runtime smoke test on a world with:
    - mesh capture bootstrap mode;
    - strict mesh replacement mode;
    - solid and cutout terrain replacement;
    - translucent terrain disabled and enabled;
    - entity, block entity, item, particle, sky, cloud, and weather paths in
      their selected vanilla/custom mode;
    - fragment shading rate enabled and disabled;
    - descriptor buffer path;
    - descriptor heap path if the driver advertises support;
    - chunk rebuilds, world reload, resource-pack reload, video-option toggles,
      and client shutdown.
6. Vulkan validation-layer run with Vulkan SDK `1.4.350.0`.
7. GPU matrix entries for at least one NVIDIA, AMD, and Intel Windows system
   before treating the renderer as generally usable; add Linux coverage when a
   Vulkan-capable test machine is available.

Validation must record:

- Minecraft, Fabric Loader, Fabric API, Loom, Gradle, Java runtime, LWJGL, and
  Vulkan SDK versions;
- GPU, driver version, OS, and Vulkan API version;
- enabled device extensions and selected renderer path for each renderer domain;
- JVM flags and video options;
- pass/fail result, artifacts, and known issues.

## Decisions

- Java source/bytecode target stays at 25 for now, even though Java 26 works
  locally, because the repository currently documents Java 25 as the supported
  target.
- Vulkan 1.4 is a hard requirement for new renderer paths. Compatibility mode
  may still capture data, observe vanilla behavior, or fall back to vanilla, but
  it must not silently run a partial replacement path on insufficient hardware.
- Descriptor buffer remains the primary texture/resource binding path. Descriptor
  heap remains optional until validation proves it stable on current drivers.
- Shader source should move out of Java strings during the refactor; runtime
  shaderc compilation can remain as a fallback until a build-time shader pipeline
  is proven.
- Translucent terrain replacement is not part of the first correctness gate; it
  remains an explicit experimental mode until sorting and blending are validated.
- Terrain remains Phase 1 because it has the most current implementation work,
  but shared services must be named and designed for the renderer as a whole.

## Follow-Up

- Decide whether stable Loom can replace the current alpha after the
  `26.2-snapshot-7` dry-run.
- Decide whether to support the latest stable Minecraft release `26.1.2` in
  parallel with the latest snapshot line.
- Decide whether to add a structured renderer diagnostics command or only keep
  log/JSON output.
- Decide whether to add cross-platform Vulkan SDK setup docs for Linux and
  Windows.
- Decide whether GPU capture tooling should standardize on RenderDoc,
  GFXReconstruct, or both.
