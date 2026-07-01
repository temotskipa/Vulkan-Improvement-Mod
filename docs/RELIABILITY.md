# Reliability

Reliability work in this mod has two layers: build-time validation and runtime
validation on real Vulkan hardware.

## Build-Time Checks

- `.\gradlew.bat checkRepoDocs` validates repository organization docs.
- `.\gradlew.bat checkClientMixinConfig` validates that
  `vulkanimprovement.client.mixins.json` and client mixin source files stay in
  sync.
- `.\gradlew.bat checkVulkanPackageBoundaries` validates that renderer code
  stays in the allowed `client.vulkan.*` domain packages and does not depend
  back on `mixin.client`.
- `.\gradlew.bat checkTerrainShaders` validates terrain GLSL with
  `glslangValidator` when it is available. Use
  `"-Pvim.requireShaderValidator=true"` to fail when the validator is missing,
  or `"-Pvim.releaseBuild=true"` for release builds. In PowerShell, quote the
  Gradle property exactly as shown so `-Pvim.releaseBuild=true` is not parsed
  as a PowerShell parameter.
- `.\gradlew.bat compileTerrainSpirv` compiles packaged terrain SPIR-V when
  `glslangValidator` is available. It remains best-effort for local
  development builds, but fails without the validator when
  `"-Pvim.releaseBuild=true"` or `"-Pvim.requireShaderValidator=true"` is set.
- `.\gradlew.bat checkTerrainLayoutManifest` validates that terrain Java layout
  constants still match the task/mesh shader layout assumptions, including the
  material-table push constant, meshlet material ID, and default material
  record. It also rejects constant clip-space terrain depth in the mesh shader.
- `.\gradlew.bat checkTerrainMeshletInvariants` runs pure Java checks for
  terrain meshlet partitioning, capture payload validation, custom-index bounds,
  and default material IDs.
- `.\gradlew.bat checkRendererDomainInvariants` runs pure Java checks for
  renderer domain reset defaults, plan-required diagnostic keys, per-domain
  compatibility contracts, and terrain path diagnostics.
- `.\gradlew.bat checkRendererCoreServiceInvariants` runs pure Java source
  checks that renderer-wide lifecycle, domain reset, present pacing, and
  fragment shading-rate controller ownership stay outside the terrain renderer.
- `.\gradlew.bat checkShaderBuildPipelineInvariants` runs pure Java source
  checks that release builds require terrain shader validation and SPIR-V
  generation through the shared `glslangValidator` gate.
- `.\gradlew.bat checkMaterialRecordInvariants` runs pure Java checks for GPU
  material record layout, default flags, and encoded texture dimensions.
- `.\gradlew.bat checkTerrainMaterialInvariants` runs pure Java checks for
  terrain render-layer material IDs, alpha-mode classification, material-table
  records, and diagnostics.
- `.\gradlew.bat checkRuntimeProfileInvariants` runs pure Java checks for
  Vulkan runtime profile grouping, selected descriptor/dispatch/RT paths, and
  disabled reasons.
- `.\gradlew.bat checkRtPtAccelerationDataInvariants` runs pure Java checks
  that RT/PT readiness stays optional, acceleration-page diagnostics tie to GPU
  world revisions and page kinds, and RT/PT acceleration allocation remains
  disabled until a dedicated allocation plan exists.
- `.\gradlew.bat checkVulkanRequirementInvariants` runs pure Java checks for
  Vulkan version parsing, requirement groups, descriptor-buffer-only adapter
  behavior, and required extension/feature names.
- `.\gradlew.bat checkGpuResourceRetirementInvariants` runs pure Java checks
  for GPU resource retirement diagnostic aggregation.
- `.\gradlew.bat checkGpuWorldDatabaseInvariants` runs pure Java checks for GPU
  world section identity, revision rollover, material-table bounds, page-kind
  authority labels, and dirty update diagnostics.
- `.\gradlew.bat checkTerrainDispatchInvariants` runs pure Java checks for
  terrain mesh-task dispatch records, direct task limits, visible-list source
  selection, and diagnostic fields.
- `.\gradlew.bat checkVisibleMeshletRingInvariants` runs pure Java checks for
  visible meshlet ring allocation, wrap behavior, and fence-blocked reuse
  diagnostics.
- `.\gradlew.bat checkTerrainWorkQueueLayoutInvariants` runs pure Java checks
  for the terrain GPU work-queue counter and record layout.
- `.\gradlew.bat checkTerrainMeshTaskCommandLayoutInvariants` runs pure Java
  checks for the terrain `VkDrawMeshTasksIndirectCommandEXT` command layout.
- `.\gradlew.bat checkTerrainRenderPassStateInvariants` runs pure Java source
  checks that mesh terrain replacement restores vanilla Vulkan render-pass
  state and uses Minecraft's camera projection instead of a hand-rolled FOV
  projection.
- `.\gradlew.bat checkTerrainRuntimeValidationPlanInvariants` runs pure Java
  source checks that the mesh replacement bugfix plan keeps the device-loss
  crash, record-first jitter captures, texture-quality caveat, and safe default
  requirements documented.
- `.\gradlew.bat checkVulkanVideoOptionsInvariants` runs pure Java source
  checks that the Video Settings integration keeps VIM options visible for an
  active Vulkan session even if the pending restart preference is OpenGL, and
  that OpenGL-active sessions keep terrain capture/reset services gated off.
- `.\gradlew.bat checkMinecraft26MixinTargetManifest` runs pure Java source
  checks that the mcdev-confirmed Minecraft `26.2` mixin target manifest,
  client mixin config, and source-level hook tokens stay aligned.
- `.\gradlew.bat build` compiles and packages the mod.
- `.\gradlew.bat runClient` starts a local Minecraft client for manual runtime
  validation.

## Runtime Signals

- `VulkanImprovementCapabilities` logs device capability snapshots when
  `vim.dumpCapabilities=true`; the snapshot includes a runtime profile grouped
  by hard requirements, preferred acceleration features, RT/PT-readiness
  features, selected paths, and disabled reasons. Selected paths explicitly
  report descriptor buffer, descriptor heap, mesh-task dispatch, multi-draw
  fallback, device-generated commands, present pacing, fragment shading rate,
  and RT/PT readiness.
- `RendererCoreServices.asMap()` exposes renderer-wide lifecycle state,
  lifecycle reason, renderer-domain states and compatibility contracts, present
  pacing, and fragment shading-rate diagnostics separately from terrain
  diagnostics.
- `RendererDiagnostics.bugReport()` includes `videoSettings` so captures can be
  correlated with texture filtering, mipmap count, anisotropy, preferred
  backend, and VIM Video Settings visibility. Terrain descriptor diagnostics
  also expose captured `Sampler0`/`Sampler2` texture bindings, including image
  dimensions, mip levels, and sampler anisotropy.
- `MeshTerrainRenderer.asMap()` exposes renderer counters and nested subsystem
  state, including lifecycle state, the last lifecycle reason, and per-domain
  renderer path diagnostics. It also reports the last terrain mesh-task
  dispatch record, including dispatch source, task count, task-limit truncation,
  whether a CPU-written visible meshlet list was used, and whether a CPU-filled
  terrain work queue was used. Default Vulkan sessions keep vanilla terrain
  visible and leave VIM terrain capture/bootstrap disabled. Diagnostic terrain
  capture/bootstrap, visible draw-list replacement, work-queue mesh-task
  dispatch, and GPU-generated indirect-command preparation are validation-gated
  opt-ins until their runtime cost and device-loss risk are fixed.
- `RendererDiagnostics.bugReport()` exposes `gpuWorldDatabase`, a CPU-authority
  GPU-world mirror contract with live section count, update sequence, clear
  count, material-table contract, page-kind authority labels, the last dirty
  update, and sampled section diagnostics.
- `RendererDiagnostics.bugReport()` exposes `rtPtAccelerationData`, a
  diagnostics-only acceleration-data registry. Allocation is disabled, while
  registered/live/retired page counts, pending rebuilds, device-lost clears,
  and fallback reason counts are tracked for future RT/PT work.
- `DescriptorHeapTerrainResources.asMap()` exposes terrain material-table
  address, allocation size, material record layout, write count, and
  missing-resource count. It also reports retired GPU resource-set, buffer, and
  byte counts so capacity growth and shutdown pressure can be inspected. Its
  visible meshlet diagnostics include ring wrap count and the last allocation's
  wrap/fence-blocked state. Terrain work-queue and mesh-task indirect-command
  diagnostics include upload, GPU-reservation, drop, wrap, and fence-deferral
  counters. When mesh replacement is explicitly enabled, normal visible-list
  replacement mirrors vanilla's per-layer draw-group order and translucent
  reversal; the older visible meshlet ring is retained as an
  unavailable/capacity fallback. GPU-generated mesh-task command preparation is
  disabled by default behind `vim.enableGpuGeneratedMeshTaskCommands=false`.
  Mesh replacement restores the native vanilla Vulkan graphics pipeline and
  marks descriptors dirty after each cancelled vanilla draw, so later fallback
  draws in the same terrain render pass cannot continue under VIM's mesh
  pipeline state. The mesh shader consumes Minecraft's camera
  view-rotation-projection matrix rather than rebuilding projection from FOV.
  CPU-prepared work queues set the command-generation push-count flag so
  multiple queued command buffers do not race through the shared work-queue
  counter header. The
  terrain material table contains one default record per Minecraft chunk render layer with
  alpha-mode and render-layer metadata. The terrain mesh shader consumes
  material record flags through meshlet material IDs and forwards them to the
  fragment shader. Section capture diagnostics include rejected capture count
  and the last rejected `sectionNode`, layer, and reason so malformed vertex or
  index payloads can fall back to vanilla instead of feeding the mesh shader.
- `TerrainRendererDebugConfig.describe()` is included in capability snapshots
  and `RendererDiagnostics.bugReport()`. The client entrypoint itself stays
  lightweight so OpenGL-active sessions do not initialize VIM terrain services.
  Video Settings exposes VIM flags when Vulkan is the active backend or the
  selected pending backend; active Vulkan takes precedence over a pending
  OpenGL restart preference so live debugging options remain reachable until
  restart.

## Operational JVM Flags

| Flag                                   | Default     | Purpose                                               |
|----------------------------------------|-------------|-------------------------------------------------------|
| `vim.dumpCapabilities`                 | `true`      | Log captured Vulkan device capabilities.              |
| `vim.disableFragmentShadingRate`       | `false`     | Disable fragment shading-rate integration.            |
| `vim.disablePresentPacing`             | `false`     | Disable present pacing controls.                      |
| `vim.requireVulkanBackend`             | `false`     | Require Vulkan backend behavior where checked.        |
| `vim.validationDescriptorBufferOnly`   | `false`     | Validate descriptor-buffer-only mode.                 |
| `vim.waitIdleBeforeTerrainUpload`      | `false`     | Force device idle before terrain upload.              |
| `vim.replaceVanillaTerrain`            | `false`     | Replace vanilla terrain with mesh rendering. Validation-gated opt-in while visible work-queue draws can device-loss on long runs. |
| `vim.enableTerrainCaptureBootstrap`   | `false`     | Mirror vanilla terrain into VIM GPU data without visible replacement. Heavy diagnostic opt-in. |
| `vim.enableMeshTranslucentTerrain`     | `false`     | Enable experimental translucent mesh terrain.         |
| `vim.enableMeshletFrustumCulling`      | `false`     | Enable meshlet frustum culling.                       |
| `vim.enableGpuGeneratedMeshTaskCommands` | `false`   | Enable experimental GPU-generated mesh-task indirect commands. |
| `vim.strictMeshTerrainReplacement`     | `true`      | Prefer strict replacement semantics.                  |
| `vim.enableVanillaChunkVisibilityFade` | `true`      | Keep vanilla chunk visibility fade behavior.          |
| `vim.drawAllCapturedTerrainLayers`     | `false`     | Draw all captured terrain layers.                     |
| `vim.terrainFragmentShadingRate`       | `1`         | Terrain fragment shading-rate value.                  |
| `vim.initialSectionCapacity`           | `32768`     | Initial section storage capacity.                     |
| `vim.initialMeshletCapacity`           | `524288`    | Initial meshlet storage capacity.                     |
| `vim.initialVertexPayloadBytes`        | `536870912` | Initial vertex payload buffer size.                   |
| `vim.initialIndexPayloadBytes`         | `67108864`  | Initial index payload buffer size.                    |
| `vim.terrainMirrorStabilizationMillis` | `750`       | Delay before mirrored terrain is treated as stable.   |
| `vim.allowCpuVisibleMeshletFallback`   | `false`     | Allow the legacy CPU visible-meshlet ring when work-queue upload is unavailable. |

Press the **Dump Vulkan Improvement Diagnostics** key (default: `K` in the Debug category) to log a compact bug-report JSON blob via `RendererDiagnostics`.

Update this table when adding or removing flags.

`vim.waitIdleBeforeTerrainUpload` is a validation escape hatch only. Normal uploads wait on the terrain read fence created when mesh terrain draws are recorded.

## GPU Validation Matrix

Record manual validation results here before treating the mesh terrain path as generally usable.

| Date       | OS           | GPU   | Driver        | Vulkan API | Minecraft     | Fabric API      | Result | Notes |
|------------|--------------|-------|---------------|------------|---------------|-----------------|--------|-------|
| 2026-06-04 | Windows 11   | RTX 4070 Ti | 596.49 / 1.4.329 | 1.4.329 | 26.2-pre-3 | 0.150.2+26.2 | build pass | `.\gradlew.bat check` and `build` pass on JDK 25; runtime smoke pending |
| 2026-06-06 | Windows      | not exercised | not exercised | not exercised | 26.2-pre-4 | 0.150.3+26.2 | build pass | `.\gradlew.bat build` passes with Fabric Loader 0.19.3 and Loom 1.17.0-alpha.19; runtime smoke pending |
| 2026-06-23 | Windows 11   | RTX 4070 Ti | 610.62 / 1.4.341 | 1.4.341 | 26.2 | 0.153.0+26.2 | runtime pass | mcdev two-cycle dev-loop after MeshTerrainRenderer visible-dispatch fix; snapshot/screenshot/record in overworld; retest VIM x≈15974 manually |
| 2026-06-29 | Windows 11   | RTX 4070 Ti | 610.62 / 1.4.341 | 1.4.341 | 26.2 | 0.153.0+26.2 | mesh replacement fail | `vim.replaceVanillaTerrain=true`, `vim.enableGpuGeneratedMeshTaskCommands=false`; device lost after visible work-queue mesh draws. Crash: `run/crash-reports/crash-2026-06-29_08.25.58-client.txt`. |
| 2026-06-29 | Windows 11   | RTX 4070 Ti | 610.62 / 1.4.341 | 1.4.341 | 26.2 | 0.153.0+26.2 | capture/bootstrap pass | Historical implicit capture/bootstrap with `vim.replaceVanillaTerrain=false`; survived past the prior crash window with zero mesh-task dispatches and vanilla terrain visible. Recording: `run/debugbridge-recordings/req_31/recording.jpg`. As of 2026-07-01, repeat this mode with `vim.enableTerrainCaptureBootstrap=true`. |
| 2026-06-29 | Windows 11   | RTX 4070 Ti | 610.62 / 1.4.341 | 1.4.341 | 26.2 | 0.153.0+26.2 | mesh replacement visual fail | Record-first camera jitter with `vim.replaceVanillaTerrain=true` reproduced dark/green slab-like terrain corruption before the render-pass/projection fixes. Recording: `run/debugbridge-recordings/req_17/recording.jpg`. |
| 2026-06-29 | Windows 11   | RTX 4070 Ti | 610.62 / 1.4.341 | 1.4.341 | 26.2 | 0.153.0+26.2 | mesh replacement jitter smoke pass with texture-quality caveat | After native pipeline restoration and vanilla camera-projection push constants, record-first jitter no longer showed the slab corruption. Counters reached 37,257 mesh draw calls and 46.0M mesh tasks with zero descriptor misses; recordings: `run/debugbridge-recordings/req_34/recording.jpg`, `run/debugbridge-recordings/req_43/recording.jpg`. `req_43` close textures look blurred, but the run used `textureFiltering:2` / `mipmapLevels:4` and the artifact is a downscaled contact sheet. Keep mesh replacement opt-in until a longer soak and full-resolution nearest-filter texture capture validate device-loss and texture-quality risk. |

Required JVM flags to record per run: `vim.replaceVanillaTerrain`, `vim.enableTerrainCaptureBootstrap`, `vim.enableMeshTranslucentTerrain`, `vim.drawAllCapturedTerrainLayers`, `vim.disableFragmentShadingRate`, `vim.validationDescriptorBufferOnly`, `vim.allowCpuVisibleMeshletFallback`, `vim.enableGpuGeneratedMeshTaskCommands`.

Required runtime counters to inspect: `meshReplacementDrawCalls`, `meshReplacementWorkQueueDrawCalls`, `meshReplacementTasks`, `meshReplacementPreparedGpuCommands`, `meshReplacementPreparedGpuCommandDisabled`, `meshReplacementPreparedDispatchQueueLeaks`, `meshReplacementVisibleMeshletFallbackRefusals`, `meshReplacementPreparedGpuCommandRefusals`.

Required texture-quality diagnostics to inspect when evaluating close terrain:
`videoSettings.quality.textureFiltering`, `videoSettings.quality.mipmapLevels`,
`videoSettings.quality.maxAnisotropyValue`, and
`terrainRenderer.descriptorHeap.textureBindings.Sampler0.maxAnisotropy`.

## Manual Runtime Checklist

1. Start with `.\gradlew.bat runClient`.
2. Confirm the client preserves the selected graphics API; OpenGL-active
   sessions should not expose VIM renderer options or terrain capture hooks,
   while active Vulkan sessions keep VIM renderer options visible even after
   the pending restart preference is changed to OpenGL.
3. Confirm Vulkan capability output appears when `vim.dumpCapabilities=true`.
4. Load a world and verify terrain renders in the intended mode.
5. Toggle relevant video options or JVM flags and repeat the render-path check.
