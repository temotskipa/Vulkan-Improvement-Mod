# Architecture

This repository is a Fabric client mod that experiments with replacing or
augmenting vanilla terrain rendering through modern Vulkan capabilities.

## Runtime Flow

1. Fabric loads `VulkanImprovement` from `src/main` and
   `VulkanImprovementClient` from `src/client`.
2. Client mixins patch Minecraft's Vulkan backend, device, render pass, chunk
   section, and video settings classes. Their targets are listed in
   [the mixin target manifest](docs/references/minecraft-26-4-mixin-targets.md).
3. `VulkanFeatureSetsMixin` adds the mod's required extensions and features to
   the feature sets Minecraft requires of a Vulkan device, and
   `VulkanBackendMixin` adds the Vulkan 1.4 API requirement, before device
   selection completes.
4. `VulkanDeviceMixin` captures physical-device capabilities and configures the
   singleton `MeshTerrainRenderer`.
5. Chunk-section mixins mirror vanilla chunk geometry into `SectionMeshletStore`
   and related GPU buffers.
6. Each frame Minecraft collects the section layers it will draw. With strict
   replacement, `LevelRendererMixin` takes every solid or cutout section layer
   that has a page on the GPU out of that collection for the mesh path, and
   Minecraft builds draws only for the rest. Otherwise
   `SectionRenderDispatcherMixin` records what Minecraft collected.
   `MeshTerrainRenderer.prepareVisibleFrame` then uploads terrain data and
   prepares mesh-task commands before Minecraft opens a render pass.
7. Inside Minecraft's terrain render pass, the layer mixins on both of its
   draw strategies call `MeshTerrainRenderer` once the layer's pipeline is
   bound, to draw mesh terrain when configured and safe; Minecraft's own draws
   for the layer then run, holding whatever the mesh path did not take.

## Source Sets

- `src/main/java`: shared mod entrypoint only. Keep renderer behavior out of
  this source set.
- `src/main/resources`: Fabric mod metadata and non-client mixin config.
- `src/client/java`: client initializer, Vulkan renderer code, and client
  mixins.
- `src/client/resources`: client mixin config, client assets, and terrain GLSL
  shader resources.

## Package Boundaries

- `client.vulkan.device`: Owns Vulkan feature requirements, capability
  snapshots, runtime profile grouping, and requirement scope metadata.
- `client.vulkan.runtime`: Owns renderer lifecycle services, domain registry
  diagnostics, bug-report serialization, backend-active checks, and Video
  Settings option construction.
- `client.vulkan.terrain`: Owns terrain meshlet storage, GPU buffer layout,
  mesh shader dispatch, descriptor resources, terrain material records,
  fragment shading rate, and renderer debug flags.
- `client.vulkan.gpuworld`: Owns the CPU-authoritative GPU-world mirror,
  section identity, revisions, page-kind metadata, residency-budget diagnostics,
  pipeline-stage contracts, and dirty-update contracts.
- `client.vulkan.rtpt`: Owns RT/PT acceleration-data diagnostics and fallback
  accounting for future acceleration structure work.
- `client.vulkan.presentation`: Owns Vulkan present pacing and present
  ID/wait diagnostics.
- `mixin.client`: Owns integration points into Minecraft and Mojang's Vulkan
  classes. Keep mixin classes thin; move reusable behavior into the
  `client.vulkan.*` packages.
- `client`: Owns Fabric client initialization and top-level logging.

The dependency direction should stay:

```text
mixin.client -> client.vulkan.* -> Minecraft/LWJGL/Fabric APIs
client       -> client.vulkan.runtime
main         -> Fabric initializer only
```

Renderer packages under `client.vulkan.*` must not depend on `mixin.client`.
They must also stay in one of the allowed renderer package directories so new
renderer code does not drift back into a catch-all root package.

## Important Runtime Contracts

- `VulkanFeatureRequirements` is the source of truth for required Vulkan API
  version, extensions, and features.
- `VulkanImprovementCapabilities` captures device limits and optional extension
  availability for diagnostics and feature decisions.
- `VulkanRuntimeProfile` groups captured capabilities into hard requirements,
  preferred acceleration features, RT/PT-readiness features, and selected paths.
- `RendererLifecycleState` records whether the renderer is unconfigured,
  configuring, ready, failed, shutting down, or device-lost.
- `RendererDomainRegistry` records the selected path and CPU/GPU work split for
  terrain and non-terrain renderer domains.
- The mod extends Minecraft's terrain drawing and does not replace how a
  frame's sections are chosen: the visible set is the one
  `LevelRenderer.extractSectionDrawGroups` collects. With strict replacement
  the mesh path takes each section layer of that set that it can draw, decided
  per section layer as Minecraft looks it up, and Minecraft never looks up or
  builds a draw for a taken one; a section layer captured this frame, or
  without a page yet, stays Minecraft's for the frame. Whether a frame takes
  anything is decided before Minecraft collects it
  (`MeshTerrainRenderer.takesOverSectionLayers`), because a taken section layer
  is drawn by nobody else. Without strict replacement nothing is taken and the
  mesh path draws over Minecraft's terrain.
- A compiled section mesh carries the mod's record of it
  (`SectionMeshletStore.CaptureHolder`), and that record the page each layer
  has now, so collecting a frame needs no map lookup per section layer.
- Nothing is uploaded and no compute work is recorded while Minecraft's
  terrain render pass is open. Minecraft's command encoder fails the frame
  otherwise.
- `TerrainPipelineState` is the rasterization, depth and blend state Minecraft
  compiled a pipeline with, remembered at compile time because the Vulkan
  pipeline no longer keeps it. A mesh pipeline is built from it, for the
  render pass Minecraft's pipeline uses, and cached by it.
- `TerrainDrawContext` snapshots one terrain layer about to be drawn inside
  Minecraft's render pass: command buffer, vanilla pipeline state, layer
  ordinal, and debug draw-all mode.
- Terrain lives on the GPU in pages. `ResidentTerrainPages` gives every
  captured section layer a page: a slot in the page table and a range of the
  vertex payload, the index payload and the meshlet headers, handed out by
  `TerrainRangeAllocator`. A page stays where it is until its section layer is
  captured again or released, so a change to one section uploads one page.
  `DescriptorHeapTerrainResources.uploadDirtyTerrainData` writes changed pages
  to a staging buffer and records copies into the device-local terrain buffers.
  The copies run in command order, after every draw recorded before them, so
  nothing waits for the GPU and a freed range can be handed out again at once.
  When a page does not fit, the buffer that was short is grown and the pages
  are uploaded again from the copies `SectionMeshletStore` keeps.
- A frame uploads one short list per layer: the page slot and fade-in of every
  section layer Minecraft collected for it. The visibility pass
  (`mesh_task_command.comp.glsl`) reads that list and the page table on the
  GPU, drops pages whose section is outside the view, appends the rest to the
  layer's draw list, and counts up the mesh-task command that draws them. The
  task stage then draws one page per task and emits a mesh task per meshlet.
  The CPU never learns the count and does not need it: whether a layer may
  replace Minecraft's draws is decided beforehand, by whether every listed
  section layer has a page.
- `TerrainMeshTaskDispatch` records where a dispatch's tasks come from. The
  path above is `gpu-visible-pages`. The older sources remain as fallback and
  diagnostics: a CPU-written work queue with one record per visible meshlet
  (`cpu-work-queue`, used when `vim.enableGpuGeneratedMeshTaskCommands=false`
  or a frame's command could not be prepared), the visible meshlet ring, and
  direct per-page draws for `vim.drawAllCapturedTerrainLayers`.
- `TerrainVisibleMeshletRing` owns the pure allocation rules for the CPU-written
  visible meshlet ring, including wrap decisions and fence-blocked reuse
  diagnostics.
- `TerrainMeshTaskCommandLayout` owns the CPU/GPU layout for terrain
  mesh-task indirect command records and the push constants of the visibility
  pass that writes them.
- `GpuWorldResidencyBudget` and `GpuWorldPipelineStage` describe the
  GPU-resident terrain contract: persistent section pages, visibility
  compaction and indirect command generation. `GpuWorldDatabase` reports what
  the resident pages occupy (`residentTerrain`) and what the visibility pass
  did (`visibilityCommandStats`).
- `GpuWorldTerrainLayerDirectory` is the current resident terrain index. It is
  keyed by section node and chunk layer, not by vanilla draw objects, so later
  GPU visibility work can consume resident section-layer records without
  depending on transient render-pass draw slices.
- `TerrainGpuBuffer.Placement` says where a terrain buffer lives. What the
  pages hold is in device memory and written by copies; what the CPU writes
  each frame and the GPU reads or counts up in the same frame (visible page
  lists, mesh-task commands, the material table, counters, descriptors) is in
  device memory the CPU maps for sequential writes; host memory is for staging
  only. The mesh stage counts nothing per meshlet.
- `TerrainPerformanceMonitor` times frames and, with `vim.terrainGpuTimer`,
  the GPU time of the mod's prepared work and of each group of terrain layers,
  through Minecraft's own timestamp queries. It measures vanilla terrain and
  the mesh path alike; the Performance Check in `docs/RELIABILITY.md` says how
  to use it.
- `TerrainGpuLayout` is the CPU-side source of truth for terrain buffer,
  meshlet, visible-list, and push-constant layout constants that shaders depend
  on.
- `DescriptorHeapTerrainResources` owns the first GPU material table: a vanilla
  terrain material record backed by captured block-atlas and lightmap texture
  bindings. `TerrainMaterialClassifier` assigns one material ID per Minecraft
  chunk render layer and records alpha mode, render-layer ordinal, and material
  domain metadata. Terrain meshlet headers carry a material ID, and the terrain
  mesh shader reads that table through buffer device address before forwarding
  material flags to the fragment shader. Its diagnostics also expose retired GPU
  resource-set, buffer, and byte counts so terrain capacity growth and shutdown
  pressure are visible.
- The mesh terrain pipeline stands in for a vanilla terrain pipeline and is
  meant to be indistinguishable from it. As in Minecraft's terrain shader, the
  lightmap is sampled per vertex, in the mesh stage, and the fragment stage
  gets the lit colour. `TerrainPipelineState` carries the
  vanilla pipeline's polygon mode, culling, depth and blend state; the fragment
  stage samples the atlas and the lightmap as vanilla terrain does, applies the
  frame's fog from `TerrainFogState`, and with improved transparency fades into
  the sky and cloud images Minecraft binds for the group. A change to any of
  these is checked against same-camera vanilla screenshots, as described under
  Vanilla Parity Check in `docs/RELIABILITY.md`.
- `checkTerrainShadersCompile` compiles the terrain shaders with the compiler
  the game uses at startup, so a development build without the Vulkan SDK still
  fails on a shader that does not compile.
- `TerrainRendererDebugConfig` exposes JVM system-property flags. Treat these as
  operational controls and keep docs synchronized when adding flags.
- `vulkanimprovement.client.mixins.json` must list every active client mixin
  class by simple name.
- `MeshTerrainRenderer.shutdown()` must release renderer resources when the
  Vulkan device closes.

## Build

Use `.\gradlew.bat build` for the full build, `.\gradlew.bat checkRepoDocs`
for repository-organization checks, `.\gradlew.bat checkClientMixinConfig` for
client mixin config/source synchronization, and
`.\gradlew.bat checkVulkanPackageBoundaries` for renderer package layout and
direction.
`.\gradlew.bat checkTerrainShaders` validates terrain GLSL when
`glslangValidator` is available. `.\gradlew.bat checkTerrainLayoutManifest`
validates CPU/shader terrain layout assumptions, including the default terrain
material record and material-table push constant. The Gradle build targets Java
25.
