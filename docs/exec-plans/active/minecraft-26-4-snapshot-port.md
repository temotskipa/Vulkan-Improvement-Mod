# Minecraft 26.4 Snapshot Port

**Status:** Active. Started 2026-10-13 on branch `mc-26.4-snapshot`. The mod
builds, passes its checks and runs on `26.4-snapshot-3`; the default mode and
the mesh modes were exercised once each. The longer runtime matrix is open.

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
- Loom `1.18` needs Gradle `9.7`; the wrapper is `9.6.1`. Loom `1.17.13`
  resolves the snapshot, so the wrapper is unchanged.
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
   `0.162.2+26.4`, Loom `1.17.13`, Gradle wrapper `9.6.1`.
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
7. Open: the runtime matrix in Validation.

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
- The Video Settings screen opens with its mixin applied. That the mod's
  controls are listed in it was not looked at.

Open:

- A long record-first jitter run (`mc_record_video(frames=300, interval=100)`)
  in each mode, exceeding the 26.2 crash window.
- Full-resolution nearest-filter texture comparison; the 26.2 cutout and
  softness caveats are not re-examined on 26.4.
- `vim.drawAllCapturedTerrainLayers`, `vim.allowCpuVisibleMeshletFallback`,
  `vim.validationDescriptorBufferOnly` and `vim.enableMeshletFrustumCulling`.
- Wireframe terrain, resource reload, dimension change and render-distance
  change with capture enabled.
- An OpenGL-active session.

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
- Use Minecraft's indirect command buffer as the input of GPU visibility
  compaction for the
  [GPU-resident terrain plan](gpu-resident-terrain-architecture.md): it
  already holds one record per visible section layer.
- Move the wrapper to Gradle `9.7` or newer when Loom `1.18` is wanted.
- Rename what still says 26.2 once the 26.2 line is retired
  (`26-2-vulkan-renderer-extension.md`).
