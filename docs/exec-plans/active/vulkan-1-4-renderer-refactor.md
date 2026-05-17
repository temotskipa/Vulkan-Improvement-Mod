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

## Current Baseline

- Current Gradle properties target `minecraft_version=26.2-snapshot-7`,
  `loader_version=0.19.2`, and `fabric_version=0.149.0+26.2`.
- Current Loom plugin is `net.fabricmc.fabric-loom` `1.17.0-alpha.8`.
- Current Gradle wrapper is `9.5.1`.
- The active command shell is PowerShell 7.6.1.
- `checkRepoDocs` passes.
- `build` passes after stabilizing the local work-in-progress API drift:
    - `MeshTerrainRenderer.recordSectionCapture` now accepts capture duration
      telemetry while keeping the older three-argument helper.
    - `MeshTerrainRenderer` now uses `TerrainRendererDebugConfig.replaceVanillaTerrain()`.
    - `MeshTerrainRenderer.shutdownNow()` coordinates immediate shader and terrain
      resource teardown for `VulkanDeviceMixin`.
    - `tryDrawMeshTerrain` now accepts the vanilla draw collection and routes it
      through the visible-meshlet subset path, while
      `vim.drawAllCapturedTerrainLayers` keeps the full captured-layer diagnostic
      path.
- `build` also passes after the `26.2-snapshot-7` dependency bump:
    - Vulkan result checks now pass the required `VulkanDevice` argument to
      `VulkanUtils.crashIfFailure`.
    - Video settings diagnostics now use
      `Options.isRestartRequiredToApplyVideoSettings()`.
    - `VulkanFeatureRequirements.missingRequiredCapabilities` now propagates
      Minecraft's `BackendCreationException` from `VulkanPhysicalDevice`
      construction.
- Dependency insight resolves Minecraft's transitive LWJGL stack to `3.4.1`,
  including `lwjgl-vulkan`, `lwjgl-vma`, `lwjgl-shaderc`, and `lwjgl-spvc`.
- `checkClientMixinConfig` now verifies that client mixin config entries and
  client mixin source files stay synchronized.
- `checkVulkanPackageBoundaries` now verifies that `client.vulkan` does not
  depend back on `mixin.client`.
- `MeshTerrainRenderer` now reports an explicit lifecycle state
  (`UNCONFIGURED`, `CONFIGURING`, `READY`, `FAILED`, `SHUTTING_DOWN`, or
  `DEVICE_LOST`) instead of only a loose configured boolean.
- `RendererDomainRegistry` now reports selected renderer paths for terrain,
  entities, block entities, items, particles, sky, clouds, weather,
  translucent effects, and diagnostics. Terrain is marked as
  `mesh-shader-terrain`; the other domains remain explicit `vanilla` paths
  until they are implemented.
- `VulkanRuntimeProfile` now groups capability diagnostics into hard
  requirements, preferred acceleration features, RT/PT-readiness features, and
  selected paths with disabled reasons. Capability JSON output now supports
  nested maps for that profile.
- Terrain indexed meshlets now use normalized index payloads in the mesh shader
  and render as bounded indexed-triangle batches, so layers with custom index
  buffers no longer force the broad custom-index fallback. Runtime diagnostics
  include successful indexed draw-call counters by layer.
- Terrain task, mesh, and fragment shaders now live under
  `src/client/resources/assets/vulkanimprovement/shaders/terrain/` and are
  loaded as runtime resources. `checkTerrainShaders` validates them with
  `glslangValidator` when available and can be required with
  `"-Pvim.requireShaderValidator=true"`.
- `TerrainGpuLayout` now centralizes terrain CPU/GPU layout constants.
  `checkTerrainLayoutManifest` verifies mesh output limits, indexed-triangle
  flags, meshlet header structure, visible meshlet record structure, push
  constant block fields, and key stride sizes against the terrain shaders.
- Terrain resources now allocate a GPU material table and write a default
  vanilla terrain material record from captured block-atlas and lightmap
  bindings. The table is exposed through diagnostics as a GPU address, layout
  size, write count, and missing-resource count. The terrain mesh shader now
  reads material flags through the meshlet material ID and forwards them to the
  fragment shader; model/resource-pack material classification still defaults
  every terrain meshlet to material `0`.
- Terrain draw scheduling now enters `MeshTerrainRenderer` through
  `TerrainDrawContext`, which snapshots the Vulkan command buffer, vanilla
  pipeline, depth state, terrain-pass state, current layer, draw collection, and
  draw-all diagnostic mode before the renderer decides between full-layer and
  visible-draw-list dispatch.
- `checkTerrainMeshletInvariants` now runs a pure Java test entrypoint that
  validates non-indexed meshlet vertex coverage, indexed-triangle index
  coverage, triangle alignment, per-meshlet budgets, and default material IDs.
- `checkRendererDomainInvariants` now runs a pure Java test entrypoint that
  validates default vanilla domain diagnostics and the terrain mesh-shader path
  report.
- The default terrain material table write now goes through `GpuMaterialRecord`
  instead of positional `putInt` calls. `checkMaterialRecordInvariants`
  validates the record byte size, default missing-texture indices, readiness
  flags, encoded texture dimensions, and material layout diagnostics.
- `checkRuntimeProfileInvariants` now validates runtime profile group structure,
  selected descriptor/present/fragment-shading-rate paths, RT/PT readiness
  fields, and disabled-reason behavior for empty and fully supported snapshots.
- `checkVulkanRequirementInvariants` now validates Vulkan version parsing plus
  required Vulkan extension and feature names for the Vulkan 1.4 renderer path.
- `DescriptorHeapTerrainResources` now reports retired GPU resource-set, buffer,
  and byte counts through diagnostics. `checkGpuResourceRetirementInvariants`
  validates the pure Java aggregation path for those counters.
- Terrain mesh-task dispatch now goes through `TerrainMeshTaskDispatch`, which
  records dispatch source, direct task cap, requested meshlets, visible-list
  address, work-queue address, indirect command buffer/offset, and truncation
  state for diagnostics. `checkTerrainDispatchInvariants` validates the pure
  Java dispatch contract.
- Visible draw-list uploads now use `TerrainVisibleMeshletRing` for pure
  allocation decisions. Diagnostics report ring wraps and fence-blocked reuse,
  and `checkVisibleMeshletRingInvariants` validates the wrap/capacity behavior.
- Full-layer terrain draws can now feed a CPU-filled terrain work queue consumed
  by the task shader. `TerrainWorkQueueLayout` records the queue counter and
  record layout and `checkTerrainWorkQueueLayoutInvariants` validates it.
- Terrain draws can now use a CPU-written
  `VkDrawMeshTasksIndirectCommandEXT` record through the mesh shader path.
  `TerrainMeshTaskCommandLayout` records the command layout and
  `checkTerrainMeshTaskCommandLayoutInvariants` validates it.
- A mesh-task command compute shader and pipeline now fill indirect command
  records from terrain work-queue counters in full-layer diagnostic mode. The
  safe hook is `ChunkSectionsToRender.renderGroup` HEAD, before Minecraft
  creates the terrain render pass. Visible-list replacement still uses
  CPU-written indirect commands.
- Terrain material records now include alpha mode, render-layer ordinal, and
  material-domain metadata while preserving the 64-byte table stride.
  `TerrainMaterialClassifier` assigns one default material ID per Minecraft
  chunk render layer and `checkTerrainMaterialInvariants` validates the mapping.
- Build validation used the project Java 25 toolchain. Java 26 may be usable
  locally, but the repository target remains Java 25.
- Existing implementation work is terrain-heavy. Treat those classes as the
  first renderer domain to stabilize, then extract renderer-wide services from
  them instead of letting terrain-specific abstractions become the global API.

## Dependency Update Targets

Verify these again at implementation time, but the latest values checked on
2026-05-17 were:

| Area                            | Current                          | Target                                                       | Source                                 |
|---------------------------------|----------------------------------|--------------------------------------------------------------|----------------------------------------|
| Minecraft latest snapshot       | `26.2-snapshot-7`                | `26.2-snapshot-7`                                            | Mojang version manifest                |
| Minecraft latest release        | n/a                              | `26.1.2`                                                     | Mojang version manifest                |
| Fabric game metadata            | `26.2-snapshot-7`                | `26.2-snapshot-7`                                            | Fabric metadata                        |
| Fabric Loader                   | `0.19.2`                         | `0.19.2`                                                     | Fabric metadata, already latest stable |
| Fabric API                      | `0.149.0+26.2`                   | `0.149.0+26.2`                                               | Fabric Maven metadata                  |
| Fabric Loom                     | `1.17.0-alpha.8`                 | keep `1.17.0-alpha.8`; latest 1.17 alpha present in metadata | Fabric Maven metadata                  |
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

## Steps

1. Stabilize the current work-in-progress.
    - Done: fixed the four compile-time API mismatches listed in Current
      Baseline.
    - Done: re-ran `.\gradlew.bat --no-daemon build`; this also ran
      `checkRepoDocs`.
    - Record any intentionally deferred behavior in this plan or in
      `docs/exec-plans/tech-debt-tracker.md`.

2. Update the dependency baseline.
    - Done: updated `gradle.properties` to `minecraft_version=26.2-snapshot-7` and
      `fabric_version=0.149.0+26.2`.
    - Done: kept `loader_version=0.19.2`; Fabric metadata still reports it as
      the latest stable loader.
    - Done: kept Loom `1.17.0-alpha.8`; the `26.2-snapshot-7` dry-run passes and
      Fabric Maven metadata does not list a newer 1.17 alpha.
    - Done: updated the Gradle wrapper to `9.5.1`.
    - Done: changed `fabric.mod.json` to express exact support for
      `${minecraft_version}` and removed the stale `26.2-alpha.6` entry.
    - Done: dependency insight resolves LWJGL, Vulkan, VMA, shaderc, and SPVC to
      `3.4.1` on `clientRuntimeClasspath`.

3. Re-map Minecraft integration points.
    - Use MC static analysis for `26.2-snapshot-7` before editing mixins.
    - Verify every mixin target method, descriptor, local capture, and injected
      cancellation point.
    - Split fragile injections into small adapter methods where a Minecraft
      class changed shape.
    - Done: added `checkClientMixinConfig`, a build check that verifies every
      listed client mixin class
      exists and every intended client mixin is listed.

4. Inventory the full renderer scope.
    - Map Minecraft's current Vulkan render flow for terrain, entities, block
      entities, items, particles, sky, clouds, weather, world borders, debug
      overlays, post-processing handoff points, and GUI boundaries.
    - Fold the GPU-driven world database and render-only worldgen boundaries from
      `docs/design-docs/gpu-driven-worldgen-rendering.md` into the renderer
      domain inventory.
    - Identify which domains can be replaced, accelerated, or only observed in
      the first Vulkan 1.4 cycle.
    - Classify every candidate domain by correctness risk: opaque world geometry,
      translucent world geometry, animated/skinned geometry, billboarded effects,
      full-screen effects, and UI.
    - Record the vanilla fallback mechanism for each domain before replacing it.
    - Keep terrain as Phase 1 because the repository already has capture,
      meshlet, descriptor, shader, and draw-scheduling work there.

5. Define a renderer-wide Vulkan runtime profile model.
    - In progress: added `VulkanRuntimeProfile`, an immutable runtime profile
      derived from captured capabilities. It separates hard requirements,
      preferred acceleration features, RT/PT-readiness features, and selected
      paths with disabled reasons while preserving the existing flat snapshot
      fields.
    - Keep the hard new-renderer requirements centered on Vulkan 1.4:
      `VK_API_VERSION_1_4`, buffer device address, scalar block layout,
      maintenance 4/5/6, dynamic rendering local read, mesh shaders, descriptor
      buffer or descriptor heap path as selected, fragment shading rate, and
      present id/wait where present pacing is enabled.
    - Treat mesh shader and descriptor heap as extension-backed features even in
      a Vulkan 1.4 renderer, because they are not guaranteed by core 1.4.
    - Emit one structured capability report with device, driver, API version,
      enabled extensions, selected renderer path, and reasons for disabled paths.
    - Expose domain-specific path decisions, such as `terrain=mesh`,
      `entities=vanilla`, `particles=vanilla`, or `sky=custom`, instead of one
      ambiguous global enabled flag.
    - Include CPU-offload diagnostics for each domain: CPU-built draw lists,
      GPU-built work queues, GPU mesh generation, CPU fallback reasons, and
      synchronous readback/idle events.

6. Refactor renderer lifecycle ownership.
    - In progress: introduced `RendererLifecycleState` with explicit
      `UNCONFIGURED`, `CONFIGURING`, `READY`, `FAILED`, `SHUTTING_DOWN`, and
      `DEVICE_LOST` states for `MeshTerrainRenderer` diagnostics and draw
      gating.
    - Split the current terrain renderer into focused renderer services for
      lifecycle, domain registration, capture accounting, draw scheduling,
      counters, and public diagnostics.
    - Add a domain registry so terrain, entity, particle, sky/weather, and later
      effects code can plug into the same frame lifecycle without each domain
      owning device setup or teardown.
        - In progress: `RendererDomainRegistry` now exposes current path
          diagnostics for terrain and the broader renderer domains.
    - Provide both deferred shutdown and immediate shutdown contracts, then make
      `VulkanDeviceMixin` call the correct one.
    - Ensure every Vulkan object has a single owner and a deterministic close or
      retire path.

7. Build shared GPU resource and memory services.
    - Create renderer-wide arenas for host-visible staging, shader-device-address
      buffers, descriptor buffers, descriptor heaps, visible-work rings, debug
      counters, and retire queues.
    - Move growth policy and dropped-data accounting out of descriptor-specific
      and terrain-specific code.
    - Add fence-aware retirement queues for old buffers and descriptor resources.
    - Use memory-budget, memory-priority, and pageable-device-local-memory
      extensions only as optional policy inputs.
    - Keep a diagnostic path that shows current allocation sizes, growth reason,
      retire queue length, per-domain memory use, and last upload timing.
        - In progress for terrain: retired resource diagnostics now expose queued
          terrain resource-set count, buffer count, and total bytes after capacity
          growth or deferred shutdown.

8. Build shared descriptor, texture, and material binding services.
    - Keep descriptor buffer as the primary Vulkan 1.4-era path.
    - Keep descriptor heap as an optional path behind capability and validation
      flags until driver support is proven across the validation matrix.
    - Separate global descriptor services from terrain texture descriptor layout.
    - Support domain-specific texture/material bindings for block atlases,
      lightmaps, entity/item textures, particles, sky/cloud textures, and any
      post-processing handoff resources.
    - Keep Minecraft texture atlases, baked model output, render layers, tint
      indices, animation metadata, lightmaps, overlays, and resource-pack
      revisions as source data for renderer-neutral material records.
    - Design material records so rasterization and future RT/PT acceleration
      paths consume the same IDs, texture descriptors, alpha modes, emission,
      and invalidation rules.
        - In progress for terrain: added a first 64-byte vanilla terrain material
          record with atlas/lightmap readiness and texture dimensions. The terrain
          mesh shader consumes record flags through a meshlet material ID and a
          buffer-device-address material table, then forwards those flags to the
          fragment stage.
        - In progress for terrain render-layer compatibility: the material table
          now writes one default record per `ChunkSectionLayer` with alpha mode,
          render-layer ordinal, and terrain-domain metadata.
    - Validate image layouts, sampler handles, descriptor offsets, descriptor
      sizes, and descriptor-buffer alignment before drawing.

9. Build shared shader and pipeline management.
    - Done for terrain: moved GLSL source out of Java string literals into
      client resource files.
    - In progress: `checkTerrainShaders` validates terrain GLSL for Vulkan 1.4
      when `glslangValidator` is available; runtime shaderc compilation remains
      the launch-time fallback.
    - Generate or validate push-constant, specialization-constant, descriptor,
      and buffer-layout manifests.
    - Cache pipelines by domain, render pipeline, depth state, color formats,
      dynamic rendering state, fragment shading rate state, blend mode,
      cull/raster state, and descriptor path.
    - Evaluate graphics pipeline library and shader object support as optional
      pipeline warmup improvements after the baseline renderer is correct.

10. Refactor terrain as the first domain.

- Turn section metadata, meshlet headers, visible meshlet records, debug
  counters, vertex payloads, and index payloads into versioned layout
  descriptors.
- Make layout constants shared by CPU writers and shader sources through a
  generated or checked manifest.
    - Done for current terrain mesh path: added `TerrainGpuLayout` and
      `checkTerrainLayoutManifest`; the manifest now covers the material-table
      push constant, material ID in the meshlet header, material record shape,
      and mesh-to-fragment material flag handoff.
- Replace heuristic vertex/index estimates with validated conversions from
  captured Minecraft section data.
- Complete indexed meshlet support so custom index buffers no longer force
  vanilla fallback.
    - In progress: custom-index layers now upload normalized `uint` indices and
      the mesh shader emits indexed-triangle meshlets from that payload.
      `checkTerrainShaders` validates the shader resource path.
- Preserve per-layer ranges, translucent opt-in, chunk visibility fade, and
  section invalidation semantics.
- Move terrain section transcoding, meshlet generation, visibility
  compaction, and draw-record generation to GPU work queues after the CPU
  mirror path is correct.

1. Refactor world geometry draw scheduling and culling.
   - In progress: `tryDrawMeshTerrain` now consumes `TerrainDrawContext`
     instead of a growing positional argument list.
   - In progress: terrain mesh-task draws now use `TerrainMeshTaskDispatch`
     instead of loose offset/count/address arguments, establishing the record
     that later GPU-compacted and indirect mesh-task generation should emit.
   - In progress: full-layer terrain draws now try a CPU-filled terrain
     work-queue path before falling back to direct layer dispatch.
   - In progress: terrain mesh-task draws can consume a CPU-written indirect
     command record, so later GPU compaction can target the same command buffer
     without changing the draw submission contract.
   - In progress: added the compute shader and pipeline needed for
     work-queue-to-indirect-command generation. Full-layer diagnostic draws now
     queue that compute work from the render-group head hook before the terrain
     render pass starts.
   - Support whole-layer draw, draw-list filtered draw, and visible-meshlet
     filtered draw through one scheduler.
   - Move frustum-culling controls into a GPU/CPU policy object and surface
     candidate, culled, emitted, and fallback counters.
   - Keep duplicate-dispatch suppression but make its frame/layer key explicit.
   - Define the translucent terrain path separately from solid and cutout
     replacement because sorting and blending have different correctness
     requirements.
   - Add a GPU work-queue path for hierarchical traversal, HIZ/frustum culling,
     request queues, and compute-generated indirect or mesh-task records.
   - Design the scheduler so later entity, item, and particle domains can
     submit draw batches without coupling to terrain meshlet internals.

2. Add non-terrain renderer domains incrementally.
   - Entity and block entity pass: start by observing pipeline state and
     descriptor needs, then prototype replacement only for simple opaque models
     before handling animated, emissive, cutout, and translucent cases.
   - Item pass: share entity material and texture binding where possible, but
     keep GUI/inventory item rendering separate from world item rendering until
     both paths are mapped.

- Particle pass: map billboard data flow, sorting requirements, atlas
  binding, and blend modes before replacing the vanilla path.
- For every domain, prefer dirty-event ingestion plus persistent GPU state
  over per-frame CPU enumeration of visible objects.
- Sky, clouds, weather, and world border: treat these as separate low-geometry
  passes that can validate dynamic rendering, FSR, and pipeline-state reuse
  without depending on terrain meshlets.
- Full-screen and post-processing-adjacent work: integrate only at stable
  handoff points so the mod does not fight Minecraft's UI and final-present
  assumptions.
- GUI/HUD: keep out of the first replacement wave unless a specific Vulkan
  backend issue requires diagnostics or compatibility glue.

13. Preserve future RT/PT compatibility without implementing it prematurely.
    - Keep the GPU world database, geometry pages, material tables, and entity
      instance records renderer-neutral enough for acceleration-structure input.
    - Add optional export/debug buffers that can later feed BLAS/TLAS-style
      builds without a second asset conversion path.
    - Start future RT work with hybrid features such as shadows, reflections,
      ambient occlusion, or GI probes before attempting full path tracing.
    - Model RT/PT as explicit renderer modules with declared input/output images
      for radiance, albedo, normals, roughness, depth, motion vectors, emission,
      denoiser inputs, temporal accumulation, tone mapping, and upscalers.
    - Add PBR and emission sidecar-texture ingestion to the material pipeline as
      additive metadata over Minecraft's existing atlases and baked models.
    - Keep full path tracing behind explicit experimental capability, content,
      and performance limits.

14. Keep gameplay lighting authority separate from visual RT/PT.
    - Preserve vanilla/server light fields for default gameplay behavior.
    - Add diagnostics that compare vanilla sky/block/raw brightness with any
      RT-derived illuminance or probe fields.
    - Treat realistic mob spawning, plant growth, freezing/melting, sensor
      behavior, or other light-driven gameplay as an explicit experimental
      world/server mode.
    - Never let client-only RT/PT output decide multiplayer gameplay.
    - If gameplay lighting is added later, expose a stable quantized light field
      with hysteresis, bounded update cadence, save/debug hooks, and mod/datapack
      query APIs.

15. Refactor synchronization and submit safety.
    - Replace implicit upload safety with explicit domain read fences or
      timeline-style tracking when Minecraft exposes suitable primitives.
    - Keep `vim.waitIdleBeforeTerrainUpload` only as a validation escape hatch.
    - Ensure visible meshlet ring reuse, metadata upload, payload growth,
      per-domain staging reuse, descriptor rewrites, and renderer shutdown
      cannot race an in-flight draw.
        - In progress for terrain: visible meshlet ring allocation is now a pure
          helper with explicit wrap-blocked state, and diagnostics count ring
          wraps separately from fence deferrals.
    - Add diagnostics for fence waits, deferrals, failures, and forced idle
      calls.

16. Update user-facing controls and diagnostics.
    - Keep existing video options mapped to `TerrainRendererDebugConfig` until a
      broader renderer config type replaces it.
    - Add clear labels for active renderer domains, mesh replacement,
      translucent mesh terrain, meshlet frustum culling, vanilla visibility
      fade, fragment shading rate, descriptor path, and strict fallback
      behavior.
    - Keep JVM flags and UI controls consistent; changes through UI should
      update runtime state and system properties as they do today.
    - Add a compact diagnostic dump for bug reports with per-domain path,
      resource, pipeline, descriptor, and synchronization state.

17. Add mechanical quality gates.
    - Done: added `checkClientMixinConfig` for client mixin config/source
      synchronization.
    - Done: added `checkVulkanPackageBoundaries` to prevent `client.vulkan`
      from importing or directly referencing `mixin.client`.
    - Done: added `checkTerrainShaders` for terrain GLSL syntax validation
      when `glslangValidator` is available.
    - Done: added `checkTerrainLayoutManifest` for CPU/shader terrain layout
      drift.
    - In progress: added `checkTerrainMeshletInvariants` for terrain meshlet
      partitioning and default material ID checks.
    - In progress: added `checkRendererDomainInvariants` for renderer-domain
      default and terrain path diagnostics.
    - In progress: added `checkMaterialRecordInvariants` for GPU material
      record layout and default terrain material encoding.
    - In progress: added `checkRuntimeProfileInvariants` for runtime profile
      grouping, selected paths, and disabled reasons.
    - In progress: added `checkVulkanRequirementInvariants` for Vulkan version
      parsing and required extension/feature names.
    - In progress: added `checkGpuResourceRetirementInvariants` for retired GPU
      resource diagnostic aggregation.
    - In progress: added `checkTerrainDispatchInvariants` for terrain
      mesh-task dispatch source, direct-limit, visible-list, and diagnostic
      invariants.
    - In progress: added `checkVisibleMeshletRingInvariants` for visible
      meshlet ring capacity, wrap, and fence-blocked reuse invariants.
    - In progress: added `checkTerrainMaterialInvariants` for per-render-layer
      terrain material IDs, alpha classification, table records, and
      diagnostics.
    - In progress: added `checkTerrainWorkQueueLayoutInvariants` for terrain
      GPU work-queue counter and record layout.
    - In progress: added `checkTerrainMeshTaskCommandLayoutInvariants` for
      terrain mesh-task indirect command layout.
    - Add pure-Java tests for meshlet partitioning, indexed index normalization,
      layout sizing, capability profile decisions, renderer-domain path
      selection, version parsing, and JSON diagnostics.
    - Add a shader compilation or shader syntax validation task if tooling is
      available locally.

18. Update documentation.
    - Update `ARCHITECTURE.md` after lifecycle, resource ownership, or Vulkan
      requirement changes.
    - Update `docs/RELIABILITY.md` for flags, runtime checklist, validation
      matrix, and Vulkan SDK requirements.
    - Update `docs/QUALITY_SCORE.md` after checks/tests are added.
    - Keep completed decisions in this plan, then move it to
      `docs/exec-plans/completed/` when done.

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
