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
   across frames, compute dispatch on the command encoder and more buffer
   usages.
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
3. `VulkanFeatureSetsMixin` offers the mod's extensions and features to
   Minecraft as one optional feature set, conditioned on the Vulkan 1.4 device
   API. Minecraft enables it on a device that supports all of it; on any other
   it draws with Vulkan without it, and the mod stays off as it does under
   OpenGL. `VulkanBackendMixin` records which (`VulkanImprovementSupport`) and
   what the device lacks, and on a supported device creates Minecraft's
   allocator for Vulkan 1.4 with buffer device addresses.
4. On a supported device `VulkanDeviceMixin` captures physical-device
   capabilities and configures the singletons `MeshTerrainRenderer`,
   `ModelGpuVertices`, `ParticleGpuVertices` and `TextGpuVertices`. The terrain
   mixins gate on `VulkanImprovementRuntime.isModAvailable()`.
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
8. Unless `vim.gpuModelVertices` is off, while Minecraft prepares the level's features,
   `ModelFeatureRendererMixin` offers each model submit to `ModelGpuVertices`,
   which takes those whose vertices it writes exactly as Minecraft would. The
   model's parts then hand their cubes to it instead of `Cube.compile`. A
   cube's faces are stored on the GPU once; a frame records a pose, colour and
   light per part and a record per cube, for draws it adds to Minecraft's draw
   group. Once Minecraft has uploaded its own vertices, one compute pass on
   Minecraft's command encoder writes the model vertices into a Minecraft
   vertex buffer, and Minecraft draws them with its render types. With
   `vim.gpuModelMeshShaders` as well, an entity pipeline Minecraft builds gets
   a mesh pipeline beside it: Minecraft's compiler compiles the pipeline's
   vertex shader a second time, wrapped in a mesh stage that reads the cube and
   part records, and the mesh pipeline shares the pipeline's layout, fragment
   module and state. Where Minecraft records a GPU model draw whose pipelines
   all have one, the mesh pipeline draws it, and no vertices are written.
9. Unless `vim.gpuItemVertices` is off, `ItemFeatureRendererMixin` offers each
   item to the same path. A `BakedQuad` is a face with four corners, so a baked
   model's quad list goes into the same store once, and a frame records a part
   and jobs for each run of quads that share a render type, tint and light
   emission, for the same compute pass and the same draws. Unless
   `vim.shareItemBoundingBoxes` is off, `ItemStackRenderStateMixin` shares an
   item's model bounding box, which Minecraft works out again from every corner
   of the model for every item in every frame, between items and frames with
   the same model, transforms and hand (`ItemBoundingBoxes`).
10. Unless `vim.gpuParticleVertices` is off, `QuadParticleFeatureRendererMixin`
    gives `ParticleGpuVertices` each layer of particles in place of writing its
    vertices. Minecraft's flat particle arrays are copied to the GPU whole, and a
    compute pass of its own writes the vertices its pipelines draw.
11. Unless `vim.gpuTextVertices` is off, `TextFeatureRendererMixin` offers each
    text to `TextGpuVertices`. The quads Minecraft's glyphs write under no pose
    are kept per string object in `TextGlyphStore`, from the second frame a string
    is drawn in, and a compute pass writes them through each text's pose. Text
    Minecraft sorts on upload stays on its path until improved transparency is
    on, which is all sign and display text under the Fast and Fancy presets;
    see-through, outlined, obfuscated and new text stay on it in any case.

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
  diagnostics, the bug report, backend-active checks, and Video
  Settings option construction. `VulkanBackendNotice` tells the player when
  the mod is off: a toast once Minecraft has loaded, saying why (OpenGL
  chosen, a launch option, Minecraft failing to start Vulkan, or a GPU that
  lacks what the mod needs, on which Minecraft draws with Vulkan without it),
  and a line under the title of Video Settings while OpenGL runs or is chosen
  or the GPU lacks what the mod needs. With Graphics API at Default,
  Minecraft tries Vulkan first (it picks the order at random since
  26.4-snapshot-7).
- `client.vulkan.terrain`: Owns terrain meshlet storage, GPU buffer layout,
  mesh shader dispatch, descriptor resources, terrain material records,
  fragment shading rate, and renderer debug flags.
- `client.vulkan.gpu`: Owns what every GPU-driven pass shares: Minecraft
  buffers kept mapped for its passes (`DeviceBuffer`), shader compilation,
  compute pipelines and debug names for Vulkan objects, and the mod's
  extensions of Minecraft's: the shader-storage and descriptor buffer usages
  and buffer device addresses (`ShaderStorage`, `DescriptorStorage`),
  compute dispatch on Minecraft's command encoder (`ComputeCommands`), and
  framebuffers kept across frames for its render passes (`FramebufferCache`)
  and barriers held back after its transfers until a command needs them
  (`TransferBarriers`).
- `client.vulkan.model`: Owns the GPU model path: model cubes' faces kept on
  the GPU, the records a frame's model submits make, the draws they go into in
  Minecraft's draw groups, the compute pass that writes their vertices, and
  the mesh pipelines that draw them from the records instead.
- `client.vulkan.gpuworld`: Owns the CPU-authoritative GPU-world mirror,
  section identity, revisions, page-kind metadata, residency-budget diagnostics,
  pipeline-stage contracts, and dirty-update contracts.
- `client.vulkan.rtpt`: Owns RT/PT acceleration-data diagnostics and fallback
  accounting for future acceleration structure work.
- `client.vulkan.presentation`: Owns Vulkan present pacing and present
  ID/wait diagnostics. With vsync on (a FIFO swapchain), pacing waits after
  each present until the frame `vim.presentPacingFrameLatency` presents before
  it is on the screen, so Minecraft runs at most that many frames ahead of the
  display and still builds a frame while the GPU draws the last. With vsync
  off it waits for nothing: a composed window shows a present at its next
  refresh, so any wait would hold the frame rate near the refresh rate. With
  Prefer DXGI Present, Minecraft presents through a DXGI swapchain instead,
  and pacing gives it a frame latency object when it is created, with a
  maximum latency of `vim.presentPacingFrameLatency` plus one, and waits on the
  object after every present. Such a swapchain no longer waits in its present,
  so the wait cannot be left out, and its latency is never changed while in
  use; a change of either applies from the next swapchain.
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
  (`SectionMeshletCaptureHolder`), and that record the page each layer
  has now, so collecting a frame needs no map lookup per section layer.
- Nothing is uploaded and no compute work is recorded while Minecraft's
  terrain render pass is open. Minecraft's command encoder fails the frame
  otherwise.
- `TerrainPipelineState` is the rasterization, depth, stencil and blend state
  Minecraft compiled a pipeline with, remembered at compile time because the
  Vulkan pipeline no longer keeps it. A mesh pipeline is built from it, for the
  render pass Minecraft's pipeline uses, and cached by it.
  `PipelineDepthStencil` turns the depth, depth bias and stencil state into
  Vulkan's for the terrain and model mesh pipelines alike.
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
- With improved transparency a GPU-driven frame is also culled by occlusion
  (`TerrainOcclusion`, `vim.terrainOcclusionCulling`), in two phases. The pass
  above leaves out the pages its page-table record marks as hidden in the
  frame before. Where Minecraft closes its opaque pass, at the head of
  `LevelRenderer.executeOit`, `depth_pyramid.comp.glsl` builds a depth
  pyramid from the depth image Minecraft wrote, read through a pushed
  descriptor, and a fourth step of the same pass tests every page in view
  against it, marks each for the next frame, and writes the draws of the pages
  seen now that were left out. A render pass of the mod's on Minecraft's main
  target draws them, with the pipeline state the first phase drew with, before
  the transparency passes read the depth. The CPU never reads a page's result;
  it reads only counters of how many pages were tested, hidden and drawn late.
  From those, a few frames late, it follows the share of tested pages found
  hidden: while that is under 12 %, where the second phase costs more than it
  saves, only one frame in 16 is tested and the others draw every page in view
  in one phase, until a test finds 20 % or more hidden
  (`vim.terrainOcclusionProbing`). A tested frame after untested ones leaves out
  the pages its last test found hidden, and its own second phase draws those
  seen now.
- Entities and block entities can be culled against the same pyramid
  (`EntityOcclusion`, `vim.entityOcclusionCulling`, off by default), in every
  frame with improved transparency, whoever draws the terrain. As Minecraft
  extracts a frame it notes the box each entity renderer tests against the
  view (`EntityRenderer.shouldRender`) and, for each block entity of a section
  in view, its block grown by one. A fifth step of the same pass tests them
  against the pyramid and writes one word each into host-visible memory, a
  ring of four frames. The CPU reads a frame's words once its submit has
  finished, two or three frames later, and leaves out of the frames after
  that what they found hidden, so this culling lags where terrain's does not.
  Results are used only while younger than 200 ms and while the camera is
  within a block of where they were taken, a box reaching past the edge of the
  screen is never found hidden, and an entity's box grows by four ticks of its
  motion and by its shadow. A glowing entity, one whose name tag shows, a
  fishing hook and the camera's entity are always drawn.
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
  visibility pass's `gpu-visible-pages`. The older sources remain as fallback and diagnostics: a CPU-written work queue with one record per visible meshlet,
  carrying its section's fade-in (`cpu-work-queue`, used when `vim.enableGpuGeneratedMeshTaskCommands=false`
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
- Terrain's buffers are Minecraft's: `DeviceBuffer` makes each through
  `GpuDevice.createBuffer`, or through Minecraft's Vulkan device while
  Minecraft is still making the device and the frontend has none yet, with
  Minecraft's usages and the mod's, and keeps it mapped through Minecraft's
  `GpuBuffer.map` until it gives it back to Minecraft's destruction queue.
  `DeviceBufferPlacement` says where one lives, which it asks of Minecraft
  through the mapping usages. What the pages hold is in device memory and
  written by copies; what the CPU writes each frame and the GPU reads or
  counts up in the same frame (visible page lists, mesh-task commands, the
  material table, counters, descriptors) is in device memory the CPU maps for
  sequential writes; host memory is for staging and what the CPU reads back.
  Minecraft's mappable memory is host-coherent, so nothing is flushed. The mesh
  stage counts nothing per meshlet.
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
  section buffers. `DeviceBuffer` turns the mapping Minecraft hands over into a
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
  gets the lit colour. `TerrainPipelineState` carries the vanilla pipeline's
  polygon mode, culling, depth, stencil and blend state; the fragment stage
  samples the atlas and the lightmap as vanilla terrain does, applies the
  frame's fog from `TerrainFogState`, and with improved transparency fades into
  the sky and cloud images Minecraft binds for the group. A change to any of
  these is checked against same-camera vanilla screenshots, as described under
  Vanilla Parity Check in `docs/RELIABILITY.md`.
- `checkTerrainShadersCompile` and `checkModelShadersCompile` compile the
  mod's shaders with the compiler the game uses at startup, so a development
  build without the Vulkan SDK still fails on a shader that does not compile.
- The model vertex pass writes Minecraft's own vertices to the byte: positions
  are computed as JOML computes them, with `precise` arithmetic, and
  everything else is worked out on the CPU as Minecraft works it out.
  `checkModelVertexInvariants` runs the pass's arithmetic over its records and
  compares the result with Minecraft's `Cube.compile` for a range of cubes and
  poses.
- `TerrainRendererDebugConfig` exposes JVM system-property flags. Treat these as
  operational controls and keep docs synchronized when adding flags.
- `vulkanimprovement.client.mixins.json` must list every active client mixin
  class by simple name.
- `MeshTerrainRenderer.shutdownNow()` must release renderer resources when the
  Vulkan device closes.

## Design Decisions

Decisions measured while building the GPU-driven paths, with what decided them.
The measurements are in the GPU Validation Matrix of `docs/RELIABILITY.md` and
in git history.

- Terrain meshlets hold 64 vertices, and a page is drawn by one task per 32 of
  its meshlets. With 128 vertices the mesh stage's outputs are twice as large
  and its GPU time on the same terrain about a twentieth higher; one task per
  page kept one workgroup busy with a large page while the others idled.
- Solid terrain's mesh pipeline has no alpha test, as Minecraft's solid shader
  has none.
- Terrain's textures stay in a descriptor buffer. Pushing them with each draw
  instead made no difference in any scene.
- The set of sections drawn stays Minecraft's (`extractSectionDrawGroups`); the
  GPU only leaves out what it finds out of view or hidden. Minecraft's section
  walk is kept for drawing, not only for scheduling compiles: a set the GPU
  chose would differ from Minecraft's in fade-in and in what Minecraft's
  occlusion graph leaves out.
- Translucent terrain stays Minecraft's. With improved transparency Minecraft
  draws it order-independently in three stages (depth bounds, transmittance,
  accumulation), so the mesh path would need no sorting but a mesh pipeline
  and fragment work for each stage. Over an ocean at render distance 32 the
  render thread spends 0.71 ms of a 2.2 ms frame, with the GPU setting the
  pace, and walking the water's sections is about a fifth of those 0.71 ms, so
  taking it would not raise the frame rate on the test machine (see the
  technical debt tracker).
- Occlusion culling needs a point between opaque terrain and everything that
  reads its depth. Minecraft has one only with improved transparency, where it
  closes the opaque pass before the transparency passes; without it terrain and
  translucency share one pass, and the mod does not build for that mode.
- Entities and block entities are culled from results a few frames old, as
  Minecraft decides on the CPU, before the frame is drawn, which of them it
  draws, and testing them in the same frame would have the CPU wait for the
  GPU. That breaks the rule that nothing newly visible is missing for a frame,
  so it is off by default and chosen in Video Settings.
- Model mesh draws have no task stage. Minecraft already leaves out every
  entity whose box is out of view before extracting it (in the entity scene 377
  of 400 mobs were wholly in view, 20 partly and 3 not), so a task stage could
  cull only parts of the partly visible ones, and the depth pyramid is built
  after the pass that draws entities, too late to test them in the same frame.
- The mod's page uploads and terrain passes are recorded into the command
  buffer Minecraft records the frame into. Executing a command buffer of the
  mod's instead ends Minecraft's open ones, the object-init one behind a
  barrier on all work before it.
- Terrain's compute passes record their own dispatches and barriers into that
  command buffer; `ComputeCommands` serves the passes that read and write only
  buffers (models, particles, text). The depth pyramid reads Minecraft's depth
  image through a pushed descriptor, and the GPU-driven pass orders its steps
  with barriers of its own, neither of which Minecraft's encoder has a concept
  of.
- The mod compiles its own compute shaders and builds its own compute
  pipelines: Minecraft's `GlslCompiler` compiles only vertex and fragment
  shaders, numbers bindings for its descriptor model and targets Vulkan 1.2,
  and its pipelines have no compute or mesh stages. The model mesh stage is the
  exception: Minecraft compiles it, as a wrapper of the pipeline's own vertex
  shader, with the pipeline's defines and includes.
- `VK_KHR_push_descriptor` is among the required extensions, for the depth
  pyramid. Vulkan 1.4 makes push descriptors a feature of
  `VkPhysicalDeviceVulkan14Features`, which may not be chained beside the
  maintenance 5 and 6 and local-read structs the mod's feature set already
  asks for; the extension needs no feature struct.
- Minecraft's buffer mappings are not kept. `GpuBuffer.map` maps and unmaps the
  allocation for every view; keeping each buffer mapped saved nothing
  measurable (1,124 against 1,127 FPS, 0.346 against 0.347 ms of render thread
  over 24 runs at the ground), as Minecraft 26.4 batches its uniform writes.
- A hook that must run when a Minecraft method ends injects at every return
  (`@At("RETURN")`), not at the last (`@At("TAIL")`), unless the method's
  bytecode has one return: the decompiled source can hide early returns, as it
  did in `StagedVertexBuffer.upload()`.

## Build

Use `.\gradlew.bat build` for the full build, `.\gradlew.bat checkRepoDocs`
for repository-organization checks, `.\gradlew.bat checkClientMixinConfig` for
client mixin config/source synchronization, and
`.\gradlew.bat checkVulkanPackageBoundaries` for renderer package layout and
direction.
`.\gradlew.bat checkShaders` validates the mod's GLSL when
`glslangValidator` is available. `.\gradlew.bat checkTerrainLayoutManifest`
validates CPU/shader terrain layout assumptions, including the default terrain
material record and material-table push constant. The Gradle build targets
Java 25.
