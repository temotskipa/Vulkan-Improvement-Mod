# Reliability

Reliability work in this mod has two layers: build-time validation and runtime
validation on real Vulkan hardware.

## Build-Time Checks

- `.\gradlew.bat checkRepoDocs` validates repository organization docs.
- `.\gradlew.bat checkClientMixinConfig` validates that
  `vulkanimprovement.client.mixins.json` and client mixin source files stay in
  sync.
- `.\gradlew.bat checkVulkanPackageBoundaries` validates that reusable
  renderer code under `client.vulkan` does not depend back on `mixin.client`.
- `.\gradlew.bat checkTerrainShaders` validates terrain GLSL with
  `glslangValidator` when it is available. Use
  `"-Pvim.requireShaderValidator=true"` to fail when the validator is missing.
- `.\gradlew.bat checkTerrainLayoutManifest` validates that terrain Java layout
  constants still match the task/mesh shader layout assumptions, including the
  material-table push constant, meshlet material ID, and default material
  record.
- `.\gradlew.bat checkTerrainMeshletInvariants` runs pure Java checks for
  terrain meshlet partitioning and default material IDs.
- `.\gradlew.bat checkRendererDomainInvariants` runs pure Java checks for
  renderer domain reset defaults and terrain path diagnostics.
- `.\gradlew.bat checkMaterialRecordInvariants` runs pure Java checks for GPU
  material record layout, default flags, and encoded texture dimensions.
- `.\gradlew.bat checkTerrainMaterialInvariants` runs pure Java checks for
  terrain render-layer material IDs, alpha-mode classification, material-table
  records, and diagnostics.
- `.\gradlew.bat checkRuntimeProfileInvariants` runs pure Java checks for
  Vulkan runtime profile grouping, selected paths, and disabled reasons.
- `.\gradlew.bat checkVulkanRequirementInvariants` runs pure Java checks for
  Vulkan version parsing and required extension/feature names.
- `.\gradlew.bat checkGpuResourceRetirementInvariants` runs pure Java checks
  for GPU resource retirement diagnostic aggregation.
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
- `.\gradlew.bat build` compiles and packages the mod.
- `.\gradlew.bat runClient` starts a local Minecraft client for manual runtime
  validation.

## Runtime Signals

- `VulkanImprovementCapabilities` logs device capability snapshots when
  `vim.dumpCapabilities=true`; the snapshot includes a runtime profile grouped
  by hard requirements, preferred acceleration features, RT/PT-readiness
  features, and selected paths.
- `MeshTerrainRenderer.asMap()` exposes renderer counters and nested subsystem
  state, including lifecycle state, the last lifecycle reason, and per-domain
  renderer path diagnostics. It also reports the last terrain mesh-task
  dispatch record, including dispatch source, task count, task-limit truncation,
  whether a CPU-written visible meshlet list was used, and whether a CPU-filled
  terrain work queue was used. Normal visible draw-list replacement now prefers
  the work-queue source and consumes GPU-generated indirect commands prepared
  before the terrain render pass. It also reports whether the draw used a
  mesh-task indirect command and splits prepared command diagnostics between
  full-layer and visible draw-list preparation.
- `DescriptorHeapTerrainResources.asMap()` exposes terrain material-table
  address, allocation size, material record layout, write count, and
  missing-resource count. It also reports retired GPU resource-set, buffer, and
  byte counts so capacity growth and shutdown pressure can be inspected. Its
  visible meshlet diagnostics include ring wrap count and the last allocation's
  wrap/fence-blocked state. Terrain work-queue and mesh-task indirect-command
  diagnostics include upload, GPU-reservation, drop, wrap, and fence-deferral
  counters. The render-group head hook queues compute command buffers before the
  terrain render pass so the GPU writes indirect mesh-task commands from
  work-queue produced counts. Normal visible-list replacement mirrors vanilla's
  per-layer draw-group order and translucent reversal; the older visible
  meshlet ring is retained as an unavailable/capacity fallback. CPU-prepared
  work queues set the command-generation push-count flag so multiple queued
  command buffers do not race through the shared work-queue counter header. The
  terrain material table contains one default record per Minecraft chunk render layer with
  alpha-mode and render-layer metadata. The terrain mesh shader consumes
  material record flags through meshlet material IDs and forwards them to the
  fragment shader.
- `TerrainRendererDebugConfig.describe()` logs the active renderer mode and JVM
  flags at client initialization.

## Operational JVM Flags

| Flag                                   | Default     | Purpose                                               |
|----------------------------------------|-------------|-------------------------------------------------------|
| `vim.dumpCapabilities`                 | `true`      | Log captured Vulkan device capabilities.              |
| `vim.disableFragmentShadingRate`       | `false`     | Disable fragment shading-rate integration.            |
| `vim.disablePresentPacing`             | `false`     | Disable present pacing controls.                      |
| `vim.requireVulkanBackend`             | `false`     | Require Vulkan backend behavior where checked.        |
| `vim.validationDescriptorBufferOnly`   | `false`     | Validate descriptor-buffer-only mode.                 |
| `vim.waitIdleBeforeTerrainUpload`      | `false`     | Force device idle before terrain upload.              |
| `vim.replaceVanillaTerrain`            | `true`      | Replace vanilla terrain when mesh rendering is ready. |
| `vim.enableMeshTranslucentTerrain`     | `false`     | Enable experimental translucent mesh terrain.         |
| `vim.enableMeshletFrustumCulling`      | `false`     | Enable meshlet frustum culling.                       |
| `vim.strictMeshTerrainReplacement`     | `true`      | Prefer strict replacement semantics.                  |
| `vim.enableVanillaChunkVisibilityFade` | `true`      | Keep vanilla chunk visibility fade behavior.          |
| `vim.drawAllCapturedTerrainLayers`     | `false`     | Draw all captured terrain layers.                     |
| `vim.terrainFragmentShadingRate`       | `1`         | Terrain fragment shading-rate value.                  |
| `vim.initialSectionCapacity`           | `32768`     | Initial section storage capacity.                     |
| `vim.initialMeshletCapacity`           | `524288`    | Initial meshlet storage capacity.                     |
| `vim.initialVertexPayloadBytes`        | `536870912` | Initial vertex payload buffer size.                   |
| `vim.initialIndexPayloadBytes`         | `67108864`  | Initial index payload buffer size.                    |
| `vim.terrainMirrorStabilizationMillis` | `750`       | Delay before mirrored terrain is treated as stable.   |

Update this table when adding or removing flags.

## Manual Runtime Checklist

1. Start with `.\gradlew.bat runClient`.
2. Confirm the client logs the renderer debug config.
3. Confirm Vulkan capability output appears when `vim.dumpCapabilities=true`.
4. Load a world and verify terrain renders in the intended mode.
5. Toggle relevant video options or JVM flags and repeat the render-path check.
