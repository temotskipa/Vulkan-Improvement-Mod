# Architecture

This repository is a Fabric client mod that extends Minecraft's Vulkan
renderer with modern Vulkan capabilities: GPU-driven terrain first, then the
rest of what a frame draws.

## Design Principles

The mod extends Minecraft's renderer; it does not run a second renderer beside
it. Other mods address Minecraft's rendering APIs as if they were vanilla, so
the mod keeps its work inside them.

1. Shared contracts stay Minecraft's, extended by mixins: the device and its
   features, buffers and textures, command buffers and submission, render
   targets, render types and pipelines, and the submit system. Where one lacks
   something the mod needs, a mixin adds it, such as a buffer usage or a
   command.
2. Where Minecraft's Vulkan backend is slow or lacks a feature, the mod
   improves the backend in place, so vanilla rendering and other mods gain
   too: barriers that wait only for what a resource needs, framebuffers kept
   across frames, compute dispatch on the command encoder, more buffer usages.
3. GPU-driven execution (compute passes, mesh shading, visibility on the GPU)
   is the mod's own code, built on Minecraft's objects: its buffers come from
   `GpuDevice.createBuffer`, its commands go into Minecraft's command buffer,
   and its draws use Minecraft's render types where they can.
4. Every replacement checks whether another mod changed what it replaces and,
   where one did, leaves that work to Minecraft's path, decided per submit.

Hooks follow how often their target runs. `@WrapOperation` and `@WrapMethod`
chain with other mods' hooks and suit calls made once per object drawn; a call
made for every section and layer uses `@Redirect`, which costs nothing while
the mod is idle (see `LevelRendererMixin` in
[the mixin target manifest](docs/references/minecraft-26-4-mixin-targets.md)).

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
   Minecraft builds draws only for the rest. Otherwise it records what
   Minecraft collected.
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
- `client.vulkan.gpu`: Owns what every GPU-driven pass shares: the mod's
  buffers (`DeviceBuffer`), shader compilation, compute pipelines and debug
  names for Vulkan objects.
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
  configuring, ready, failed, or shutting down. Minecraft crashes when the
  Vulkan device is lost, so there is no state for that.
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
- In strict replacement a frame is GPU-driven by default
  (`vim.gpuDrivenTerrain`). Before Minecraft collects the frame, the mod walks
  Minecraft's `visibleSections` once and uploads one bit per section of the
  box around them, a few kilobytes, and nothing about pages. The GPU-driven
  pass (`visible_section_pages.comp.glsl`) reads the page table itself: every
  page of a section in view gets one task per 32 of its meshlets, its fade-in
  worked out from the time its section was first shown, and each layer's
  mesh-task command counts them. Three steps sort a layer's tasks by the
  distance of their section from the camera's, nearest first, so the GPU
  rejects hidden terrain by depth about as early as in Minecraft's order.
  Minecraft then walks only the visible sections that draw something the GPU
  does not, such as translucent terrain or a layer without a page yet: each
  compiled mesh keeps a role, worked out when its pages change, saying whether
  the GPU draws its section whole. A mesh's layers get pages only once
  Minecraft shows the mesh, so a section has the pages of one mesh only.
  A frame falls back to the listed path below when a layer has more tasks than
  one draw may launch, the box is too large, or the visible list has no room.
- Otherwise a frame uploads one short list per layer: the page slot and fade-in of every
  section layer Minecraft collected for it, in Minecraft's order, nearest
  first. By default (`vim.taskStagePageCulling`) that list is the draw: one
  task per 32 of a page's meshlets (`TerrainGpuLayout.MESHLETS_PER_TASK`), its
  record naming the page and which 32, so the draw keeps Minecraft's order and
  the GPU rejects hidden terrain by depth as early as it does for vanilla.
  Nothing runs before it. A task hands the mesh stage at most 32 meshlets, so
  what it passes on stays small and a large page is spread over many
  workgroups.
  Otherwise the visibility pass (`mesh_task_command.comp.glsl`) first drops
  pages out of view and compacts the rest into the mesh-task command that
  draws them; all layers' passes are recorded into one command buffer behind
  one barrier, and the command's starting value is written by the CPU.
- A captured section layer's quads are grouped by the way they face
  (`TerrainQuadMeshlets`): the six axis directions, and one group for faces
  that do not lie across an axis. Each meshlet holds quads of one group and
  the box around them. A task drops its share whole when the page is out of
  view, else tests its meshlets one per invocation and hands the mesh stage,
  in their order, those that may be seen: a group whose faces all point away from the camera
  is dropped when the pipeline culls back faces, and a meshlet out of view
  always (`vim.cullMeshlets`).
- `TerrainMeshTaskDispatch` records where a dispatch's tasks come from. The
  GPU-driven path is `gpu-driven-pages`, the listed path `listed-pages`, the
  visibility pass's `gpu-visible-pages`. The older sources remain as fallback and diagnostics: a CPU-written work queue with one record per visible meshlet
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
- `DeviceBuffer.Placement` says where a buffer of the mod's lives. What the
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
  on. `TerrainGpuRecords` declares the records the CPU writes for the GPU (page
  records, meshlet headers, material records, task records, work-queue records
  and mesh-task commands) as foreign-memory struct layouts named as the shaders
  name them, and the mod writes them, and captured section data, through
  `MemorySegment`s. Only what LWJGL or Minecraft hands over or takes as a
  `ByteBuffer` stays one: push constants, SPIR-V, descriptors and Minecraft's
  section buffers. `DeviceBuffer` turns a buffer's mapped address into a
  segment of the buffer's size once, the one unchecked step; every access after
  it is checked against that size and fails once the buffer is given back.
  Offsets into the payload buffers are 64 bits wide, so those buffers may grow
  as large as the device lets one buffer be (`maxBufferSize` and
  `maxMemoryAllocationSize`).
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
- `MeshTerrainRenderer.shutdownNow()` must release renderer resources when the
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
material record and material-table push constant. The Gradle build targets
Java 25.
