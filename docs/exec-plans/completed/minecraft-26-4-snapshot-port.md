# Minecraft 26.4 Snapshot Port

**Status:** Completed 2026-10-14 on branch `mc-26.4-snapshot`. The mod
builds, passes its checks and runs on `26.4-snapshot-3`; the runtime matrix
passed and mesh terrain matches vanilla terrain in same-camera comparisons.
A later snapshot or the release needs a plan of its own.

## Goal

Run the mod on the Minecraft 26.4 snapshots, starting with `26.4-snapshot-3`,
without losing the behaviour the 26.2 line has, and attach the terrain path to
Minecraft's own structures instead of rebuilding them.

## Constraints

- The 26.4 snapshots move the GPU abstraction and both backends out of
  `com.mojang.blaze3d` into a new library, `com.mojang.renderpearl`. Every
  Vulkan mixin target changed package and several changed shape.
- Windowing moved from GLFW to SDL. `org.lwjgl.glfw` is no longer on the
  classpath.
- Minecraft now has its own indirect terrain path
  (`LevelRenderer.prepareChunkRendersIndirect`, `RenderPass.drawIndexedIndirect`,
  the optional `EXT MultiDraw` feature set) next to the path that issues one
  draw per section layer. The mod extends these paths; it does not replace how
  a frame's sections are chosen.
- Minecraft records terrain inside a `VkRenderPass` object from
  `RenderPassCache`, not with dynamic rendering, and builds one Vulkan pipeline
  per compiled pipeline. The level renderer opens the render pass before it
  calls `ChunkSectionsToRender.renderGroup`.
- Loom `1.18` needs Gradle `9.7` or newer. The port began on Loom `1.17.13`
  with the `9.6.1` wrapper, which resolves the snapshot; on 2026-10-14 the
  build moved to Loom `1.18-SNAPSHOT` and the `9.8.1` wrapper.
- Fabric Loader reports a snapshot under a normalized name:
  `26.4-snapshot-3` is `26.4-alpha.3`. `gradle.properties` holds both
  (`minecraft_version`, `minecraft_dependency`).
- The 26.4 launcher metadata starts the game with `-XX:StackShadowPages=32`.
  Loom does not pass launcher JVM arguments on. Without it shader compilation
  in `shaderc` overflows the stack of the calling thread and the client dies at
  startup with exit code `0xC0000005` and no JVM error file, most often when
  the machine is busy or a second client runs. `build.gradle` adds the argument
  to every run configuration.

## Steps

1. Done: target `26.4-snapshot-3`, Fabric Loader `0.19.5`, Fabric API
   `0.162.2+26.4`, Loom `1.18-SNAPSHOT`, Gradle wrapper `9.8.1`.
2. Done: relocate the imports and mixin descriptors that only changed package
   (table below).
3. Done: device requirements. `VulkanFeatureRequirements` describes them as
   Minecraft `FeatureSet`s; `VulkanFeatureSetsMixin` adds them to
   `VulkanFeatureSets.requiredFeatureSets()`, so Minecraft checks, reports and
   enables them with its own. `VulkanBackendMixin` keeps what a feature set
   cannot express: the Vulkan 1.4 device API and the allocator flags.
4. Done: terrain path (section below).
5. Done: every mixin target re-verified with mcdev and recorded in
   `docs/references/minecraft-26-4-mixin-targets.md`.
6. Done: `ARCHITECTURE.md`, `docs/RELIABILITY.md` and the invariant checks
   that named 26.2 classes.
7. Done: the runtime matrix in Validation.
8. Done: vanilla parity of the mesh fragment path (section below).

### Package relocations

| 26.2 | 26.4 snapshot |
|---|---|
| `com.mojang.blaze3d.vulkan.*` (with `checkpoints`, `init`) | `com.mojang.renderpearl.backend.vulkan.*` |
| `com.mojang.blaze3d.opengl.GlBackend` | `com.mojang.renderpearl.backend.opengl.GlBackend` |
| `com.mojang.blaze3d.systems.GpuDevice`, `BackendCreationException` | `com.mojang.renderpearl.api.device.*` |
| `com.mojang.blaze3d.shaders.GpuDebugOptions` | `com.mojang.renderpearl.api.device.GpuDebugOptions` |
| `com.mojang.blaze3d.systems.RenderPass` | `com.mojang.renderpearl.api.commands.RenderPass` |
| `com.mojang.blaze3d.buffers.GpuFence` | `com.mojang.renderpearl.api.commands.GpuFence` |
| `com.mojang.blaze3d.buffers.GpuBuffer`, `GpuBufferSlice` | `com.mojang.renderpearl.api.buffers.*` |
| `com.mojang.blaze3d.textures.GpuSampler`, `GpuTextureView` | `com.mojang.renderpearl.api.textures.*` |
| `com.mojang.blaze3d.IndexType` | `com.mojang.renderpearl.api.pipeline.IndexType` |
| `com.mojang.blaze3d.pipeline.BlendFunction`, `ColorTargetState`, `DepthStencilState` | `com.mojang.renderpearl.api.pipeline.*` |
| `com.mojang.blaze3d.shaders.ShaderSource` | `com.mojang.blaze3d.pipeline.ShaderSource` |
| `net.minecraft.client.renderer.DynamicUniforms` | `net.minecraft.client.renderer.DynamicGpuData` |

### Terrain path on 26.4

- **Which section layers.** `LevelRenderer.extractSectionDrawGroups` collects
  the frame's section layers for both Minecraft draw paths and asks
  `SectionRenderDispatcher.getRenderSectionSlice` for each one, in draw order.
  `SectionMeshletStore` remembers those layers for the frame. The 26.2 mapping
  from backend draws back to section layers is gone.
- **Coverage.** A layer is drawn by the mesh path only when every section
  layer Minecraft collected for it has meshlets on the GPU. Otherwise the
  layer is left to Minecraft for that frame.
- **Preparation.** Uploads and the compute step that writes mesh-task commands
  run when `extractSectionDrawGroups` returns, before a render pass is open.
  On 26.2 they ran inside `renderGroup`, which then still created the pass;
  doing so on 26.4 fails the frame with `Cannot execute command buffer while
  inside RenderPass`. Command generation also refuses when a pass is open.
- **Draw.** `ChunkSectionsToRender.DrawIndirect.render` and
  `DrawSeparate.render` are hooked after Minecraft binds the layer's pipeline.
  One dispatch per layer per frame, split only when a layer exceeds the
  mesh-task limit; 26.2 issued one per vanilla draw group.
- **Pipeline.** A Vulkan pipeline no longer keeps the state it was compiled
  with, so `VulkanRenderPipelineMixin` remembers it as `TerrainPipelineState`.
  The mesh pipeline is built from that state for the render pass Minecraft's
  pipeline uses.
- **Textures.** The atlas, its sampler and the lightmap are taken from the
  arguments of `renderGroup` instead of from a hook on texture binding.

### Vanilla parity of the mesh path

Found by comparing same-camera screenshots of vanilla and strict mesh terrain
with the game ticking frozen, against a noise floor of two vanilla screenshots
(mean difference 0.03 of 255 per channel):

- **Atlas sampling.** Fetching mip 0 was sharp up close and shimmered at a
  distance. The atlas is now sampled through Minecraft's sampler with the
  gradients of the unmodified coordinate, and the sample point is pulled to
  the texel centre while a texel covers more than a pixel.
- **Culling.** The mesh pipeline drew both faces of everything. A plant's
  crossed planes then show their mirrored back faces too, which reads as
  denser foliage. The pipeline now culls as the vanilla pipeline it stands in
  for does (`TerrainPipelineState.cull`).
- **Lighting.** The lightmap is sampled at texel centres and no longer has a
  floor of 0.35 under it, which made night and caves brighter than vanilla.
- **Fog.** `FogRendererMixin` remembers the fog Minecraft uploads for the
  frame; the fragment stage gets it as push constants and the mesh stage
  supplies the two distances Minecraft's fogs use. With improved transparency
  on, Minecraft fades terrain into the sky and cloud images instead of the fog
  colour; the mesh path reads the same three images, taken from the arguments
  of `renderGroup`. Improved transparency is on in the `fabulous` preset, so
  this is the path that preset's users see.
- **Section fade-in.** A section's visibility no longer scales the alpha that
  cutout terrain is tested on; it blends the section out of what is behind
  it, as vanilla does. Only the visible-list fallback carries visibility; the
  work-queue paths draw sections at full visibility (see Open).
- **Precision.** Positions were formed from float section coordinates minus a
  float camera position. The camera's block is now subtracted as integers
  first, so terrain three million blocks from the origin is as steady as
  vanilla's.

## Validation

Done on 2026-10-13, Windows 11, RTX 4070 Ti, driver 616.92, Vulkan 1.4.351:

- `.\gradlew.bat build` passes, with every repository check.
- `.\gradlew.bat runClient` reaches a world in the default mode. All 19
  client mixins were applied across the runs below; each applies when its
  target class loads, so `VulkanGpuSurfaceMixin` needed a run on the Vulkan
  swapchain and `ChunkSectionsToRenderDrawSeparateMixin` one without
  multi-draw-indirect. Device selection reports the mod's feature sets and
  rejects the integrated GPU for the extensions it lacks.
- Capture: 1,334 sections captured; every visible section layer covered.
- Overlay (`vim.replaceVanillaTerrain=true`): one mesh dispatch per layer per
  frame, `meshReplacementTaskTruncations=0`, two mesh pipelines compiled.
- Strict replacement with GPU-generated commands, record-first camera jitter:
  `run/debugbridge-recordings/26.4-snapshot-3/strict-gpu-commands-jitter.jpg`
  shows coherent terrain in all 24 frames. Counters after the run:
  `meshReplacementPreparedGpuCommands=2633`,
  `meshReplacementPreparedDispatchQueueLeaks=0`,
  `meshReplacementTaskTruncations=0`,
  `meshReplacementDrawCallsByLayer={solid=1355, cutout=1334, translucent=0}`,
  `meshReplacementPreparedGpuCommandRefusals=83` (frames in which a newly
  visible section layer had no meshlets yet; Minecraft drew those).
- Same-camera screenshots `vanilla.jpg`, `overlay.jpg` and `strict.jpg` in the
  same folder show the same scene.
- A second run on the Vulkan swapchain (`RENDERPEARL_DISABLE_DXGI_PRESENT=1`):
  present pacing reported `mode=present-id-wait`, `presentIdsSubmitted=888`,
  `presentWaits=888`, `presentWaitFailures=0`.
- Strict replacement without multi-draw-indirect (`Minecraft.multiDrawIndirect`
  set to `false`): `strict-draw-separate.jpg`, 519 dispatches per layer, no
  refusals; two more mesh pipelines were compiled for the pipelines that path
  uses.
- 100 frames at 100 ms with nine scheduled camera turns, strict replacement
  with GPU-generated commands: `strict-gpu-commands-jitter-100.jpg` shows
  coherent terrain in every frame; 8,164 dispatches,
  `meshReplacementRefusals=0`, `meshReplacementPreparedGpuCommandRefusals=0`,
  `meshReplacementPreparedDispatchQueueLeaks=0`,
  `meshReplacementTaskTruncations=0`.
- The Video Settings screen opens with its mixin applied.
- Record-first jitter, `mc_record_video(frames=300, interval=100)` with 27
  scheduled camera turns, in four modes: capture only, overlay, strict with
  GPU-generated commands, and every diagnostic flag on
  (`drawAllCapturedTerrainLayers`, `enableMeshTranslucentTerrain`,
  `allowCpuVisibleMeshletFallback`). Recordings `jitter-300-*.jpg`. In every
  mode `meshReplacementTaskTruncations=0`, no prepared-dispatch leaks, no
  draws for the translucent layer, and no device loss.
- Events in strict mode with GPU-generated commands: wireframe terrain on and
  off, render distance 6 and back to 16, a resource reload, the Nether and
  back. The client then quit normally. Wireframe first counted 226 prepared
  dispatches as leaked, because commands were prepared for terrain Minecraft
  draws itself; preparation now skips wireframe frames and the count stays 0.
- Vanilla against strict mesh terrain, same camera, 854x480, the chat and
  hotbar excluded. Mean difference per channel of 255, and the share of pixels
  differing by more than 60 of 765: noon 1.03 and 0.06 %; render distance 4,
  where the fog band is in view, 0.86 and 0.06 %; midnight 0.83 and 0.00 %;
  blindness, which brings environmental fog to five blocks, 0.17 and 0.00 %;
  at x = z = 3,000,000, 0.42 and 0.01 %; and with improved transparency off,
  noon 1.06 and 0.06 %, render distance 4 0.87 and 0.06 %. Before the parity
  work the noon figures were 6.45 and 2.69 %. What is left is single pixels on
  foliage edges. Images in `parity/`.

Done on 2026-10-14, same machine:

- The resident pages and the GPU visibility pass of the
  [GPU-resident terrain plan](gpu-resident-terrain-architecture.md), whose
  completion note and the matrix row of that day hold the figures.
- `vim.validationDescriptorBufferOnly=true`, set when the game starts: the
  profile reports `descriptorPath=descriptor-buffer-only`, strict replacement
  draws through the GPU visibility pass, and the picture differs from vanilla
  by 1.03 of 255 as it does with the descriptor heap.
- `vim.enableMeshletFrustumCulling=true` on the CPU work-queue path: the same
  1.03, no holes.
- An OpenGL session (`preferredGraphicsBackend:"opengl"`): Minecraft reports
  the OpenGL backend, the mod reports `lifecycle=UNCONFIGURED` and no active
  renderer, and with every terrain switch on nothing is captured, no page is
  made and nothing is drawn. The client quit normally.
- The Video Settings screen lists the mod's header and 22 controls after
  Minecraft's own under Vulkan, 58 widgets in all, and only Minecraft's 35
  under OpenGL.

Not done:

- A run with the Vulkan validation layers.
- Any GPU or driver other than the one above.
- The CPU work-queue fallback still draws sections at full visibility while
  they fade in. The GPU visibility path, which is the default, fades them.

## Decisions

- Terrain replacement builds on Minecraft's draw paths. The mod takes the
  frame's visible set from Minecraft and draws at Minecraft's per-layer draw
  point; it does not choose sections itself. Decided by the maintainer on
  2026-10-13: the mod extends Mojang's renderer and does not write a new one,
  unless that conflicts with the modern-feature goals.
- The mod's startup requirements stay required, now as feature sets. A device
  without them fails Vulkan device selection as on 26.2.
- Feature names follow the Vulkan struct fields (`meshShader`), as Minecraft's
  do, instead of the `vim.` names; the feature set name identifies the mod.
- Present pacing covers `VulkanGpuSurface` only. Minecraft presents through
  `D3D12GpuSurface` when the `DXGI Surface` feature set is enabled, which it is
  on Windows with current drivers, and present-id/present-wait have no
  swapchain to act on there. See Follow-Up.

## Follow-Up

- Decide what present pacing does under DXGI presentation: report itself
  inactive, or ask for the Vulkan swapchain while pacing is enabled
  (`RENDERPEARL_DISABLE_DXGI_PRESENT=1` is Minecraft's own switch).
- The GPU visibility pass of the
  [GPU-resident terrain plan](gpu-resident-terrain-architecture.md) takes a
  list the mod uploads, eight bytes per visible section layer. Minecraft's
  indirect command buffer holds one record per visible section layer too and
  could replace that upload on the multi-draw-indirect path.
- Pin Loom to a release once `1.18` has one; a snapshot version can change
  under the build.
- Rename what still says 26.2 once the 26.2 line is retired
  (`26-2-vulkan-renderer-extension.md`).

## Completion

Closed on 2026-10-14. Everything the port set out to do is done and checked
at runtime, and the plans it inherited from the 26.2 line are closed with it:
the renderer extension, the non-terrain contracts, the RT/PT data contracts,
the worldgen provider diagnostics, the mesh-replacement bugfix and the
GPU-resident terrain architecture.

What the port leaves for later is under Not done and Follow-Up above, and in
the technical debt tracker.
