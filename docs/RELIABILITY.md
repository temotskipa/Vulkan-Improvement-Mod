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
- `.\gradlew.bat checkShaders` validates the mod's GLSL with
  `glslangValidator` when it is available. Use
  `"-Pvim.requireShaderValidator=true"` to fail when the validator is missing,
  or `"-Pvim.releaseBuild=true"` for release builds. In PowerShell, quote the
  Gradle property exactly as shown so `-Pvim.releaseBuild=true` is not parsed
  as a PowerShell parameter.
- `.\gradlew.bat compileShaderSpirv` compiles packaged SPIR-V for every shader
  when `glslangValidator` is available. It remains best-effort for local
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
  renderer domain reset defaults, required diagnostic keys, per-domain
  compatibility contracts, and terrain path diagnostics.
- `.\gradlew.bat checkRendererCoreServiceInvariants` runs pure Java source
  checks that renderer-wide lifecycle, domain reset, present pacing, and
  fragment shading-rate controller ownership stay outside the terrain renderer.
- `.\gradlew.bat checkShaderBuildPipelineInvariants` runs pure Java source
  checks that release builds require terrain shader validation and SPIR-V
  generation through the shared `glslangValidator` gate.
- `.\gradlew.bat checkMaterialRecordInvariants` runs pure Java checks for GPU
  material record layout, default flags, and encoded texture dimensions.
- `.\gradlew.bat checkTerrainGpuRecordInvariants` runs pure Java checks that
  each record the CPU writes for the GPU declares the members of the terrain
  shaders' struct of that name, in order and of the same size, and is as large
  as its buffer's stride.
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
  authority labels, residency stats, visibility/command counters, and dirty
  update diagnostics.
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
- `.\gradlew.bat checkResidentTerrainPagesInvariants` runs pure Java checks
  for the range allocator and page bookkeeping behind resident terrain pages.
- `.\gradlew.bat checkTerrainShadersCompile` and
  `.\gradlew.bat checkModelShadersCompile` compile the terrain and model
  shaders with the compiler the game uses at startup, so a build without the
  Vulkan SDK still fails on a shader that does not compile.
- `.\gradlew.bat checkModelVertexInvariants` checks the model vertex shader's
  word counts against its records, and that its arithmetic over the records
  gives the vertices Minecraft's `Cube.compile` writes, byte for byte.
- `.\gradlew.bat checkVulkanVideoOptionsInvariants` runs pure Java source
  checks that the Video Settings integration keeps VIM options visible for an
  active Vulkan session even if the pending restart preference is OpenGL, and
  that OpenGL-active sessions keep terrain capture/reset services gated off.
- `.\gradlew.bat checkAcceleratedWorldgenProviderIntegrationInvariants` runs
  pure Java checks that optional accelerated-worldgen provider diagnostics stay
  diagnostics-only, default off, Video Settings-visible, and free of Fabric
  hard or soft dependency metadata.
- `.\gradlew.bat checkMinecraft26MixinTargetManifest` runs pure Java source
  checks that the mcdev-confirmed mixin target manifest for the Minecraft
  version in `gradle.properties`, the client mixin config, and source-level
  hook tokens stay aligned, and that no configured mixin is missing from it.
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
  count, material-table contract, page-kind authority labels, residency budget
  and usage stats, visibility compaction and indirect-command counters, the
  last dirty update, and sampled section diagnostics.
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
  replacement mirrors vanilla's per-layer draw-group order for replaceable
  solid/cutout terrain. Translucent terrain, water-like transparency, and
  environment screen effects remain vanilla-rendered until their
  blending/fog/effect-ordering contract exists; the older visible meshlet ring
  is retained as an unavailable/capacity fallback. With mesh replacement
  enabled, a layer's mesh-task command is written by the GPU visibility pass
  from the frame's visible page list; `vim.enableGpuGeneratedMeshTaskCommands=false`
  selects the older CPU-written work queue instead.
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

| Flag                                               | Default     | Purpose                                                                                                                                                                                                                                                                                           |
|----------------------------------------------------|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `vim.dumpCapabilities`                             | `true`      | Log captured Vulkan device capabilities.                                                                                                                                                                                                                                                          |
| `vim.disableFragmentShadingRate`                   | `false`     | Disable fragment shading-rate integration.                                                                                                                                                                                                                                                        |
| `vim.disablePresentPacing`                         | `false`     | Disable present pacing controls.                                                                                                                                                                                                                                                                  |
| `vim.requireVulkanBackend`                         | `false`     | Require Vulkan backend behavior where checked.                                                                                                                                                                                                                                                    |
| `vim.enableAcceleratedWorldgenProviderIntegration` | `false`     | Enable diagnostics for an optional accelerated worldgen provider; does not change renderer or worldgen authority.                                                                                                                                                                                 |
| `vim.validationDescriptorBufferOnly`               | `false`     | Validate descriptor-buffer-only mode.                                                                                                                                                                                                                                                             |
| `vim.waitIdleBeforeTerrainUpload`                  | `false`     | Force device idle before terrain upload.                                                                                                                                                                                                                                                          |
| `vim.replaceVanillaTerrain`                        | `false`     | Draw mesh-shader solid/cutout terrain diagnostics; vanilla remains visible unless strict cancellation is enabled.                                                                                                                                                                                 |
| `vim.enableTerrainCaptureBootstrap`                | `false`     | Mirror vanilla terrain into VIM GPU data without visible replacement. Heavy diagnostic opt-in.                                                                                                                                                                                                    |
| `vim.enableMeshTranslucentTerrain`                 | `false`     | Capture/upload experimental translucent terrain metadata; visible translucent replacement is safety-gated to vanilla.                                                                                                                                                                             |
| `vim.enableMeshletFrustumCulling`                  | `false`     | Enable meshlet frustum culling.                                                                                                                                                                                                                                                                   |
| `vim.enableGpuGeneratedMeshTaskCommands`           | `true`      | Let the GPU visibility pass cull a layer's visible pages and write its mesh-task command. Off selects the CPU-written per-meshlet work queue. Only matters with `vim.replaceVanillaTerrain`.                                                                                                      |
| `vim.strictMeshTerrainReplacement`                 | `false`     | With `vim.replaceVanillaTerrain`, take every solid/cutout section layer that has a page on the GPU out of Minecraft's draw lists and draw it with mesh shaders; Minecraft draws the rest.                                                                                                         |
| `vim.enableVanillaChunkVisibilityFade`             | `true`      | Keep vanilla chunk visibility fade behavior.                                                                                                                                                                                                                                                      |
| `vim.drawAllCapturedTerrainLayers`                 | `false`     | Draw all captured solid/cutout terrain layers; heavy diagnostic mode split above the mesh-task limit.                                                                                                                                                                                             |
| `vim.terrainFragmentShadingRate`                   | `1`         | Terrain fragment shading-rate value.                                                                                                                                                                                                                                                              |
| `vim.initialSectionCapacity`                       | `32768`     | Initial section storage capacity.                                                                                                                                                                                                                                                                 |
| `vim.initialMeshletCapacity`                       | `524288`    | Initial meshlet storage capacity.                                                                                                                                                                                                                                                                 |
| `vim.initialVertexPayloadBytes`                    | `536870912` | Initial vertex payload buffer size.                                                                                                                                                                                                                                                               |
| `vim.initialIndexPayloadBytes`                     | `67108864`  | Initial index payload buffer size.                                                                                                                                                                                                                                                                |
| `vim.terrainMirrorStabilizationMillis`             | `750`       | Delay before mirrored terrain is treated as stable.                                                                                                                                                                                                                                               |
| `vim.allowCpuVisibleMeshletFallback`               | `false`     | Allow the legacy CPU visible-meshlet ring when work-queue upload is unavailable.                                                                                                                                                                                                                  |
| `vim.taskStagePageCulling`                         | `true`      | Hand each layer's listed pages straight to the task stage in Minecraft's order, one task per 32 of a page's meshlets; the task stage drops what is out of view. Off runs the GPU visibility pass first, which culls and compacts the pages.                                                       |
| `vim.gpuDrivenTerrain`                             | `true`      | In strict replacement, let the GPU draw the solid/cutout terrain of every section in view from its own page table, told only which sections are in view; Minecraft collects only the sections that draw something else. Off lists the pages of the section layers Minecraft collects every frame. |
| `vim.gpuModelVertices`                             | `false`     | Let the GPU write the vertices of Minecraft's entity models into Minecraft's own draws (`client.vulkan.model`), for the model submits it writes exactly as Minecraft would. Off while another mod's mixins target a class it replaces. Can be switched between frames.                            |
| `vim.keepFramebuffers`                             | `true`      | Give each of Minecraft's render passes the framebuffer it had in an earlier frame (`client.vulkan.gpu.FramebufferCache`) instead of creating one per render pass and destroying it two submits later. A framebuffer goes, through Minecraft's destruction queue, when one of its views closes. Can be switched between frames. |
| `vim.barriersByResource`                           | `false`     | Hold back the barrier Minecraft records after each clear, upload and copy until a later command needs it (`client.vulkan.gpu.TransferBarriers`): a transfer that reads or writes what a held one wrote, or writes what a held one read, any other command, or the end of the command buffer. Off until a run with synchronization validation. Can be switched between frames. |
| `vim.gpuModelMeshShaders`                          | `false`     | With `vim.gpuModelVertices`, draw GPU model draws with a mesh stage that reads the cube and part records (`client.vulkan.model.ModelMeshPipelines`), so no vertices are written for them. Entity pipelines get a mesh pipeline only when Minecraft builds them with both flags on; turning the flags on later takes a resource reload. Switching it off goes back to the vertex pass between frames. |
| `vim.cullMeshlets`                                 | `true`      | Let the task stage drop a page's meshlets that face away from the camera or are out of view. Off draws every meshlet of every page the visibility pass kept.                                                                                                                                      |
| `vim.terrainGpuTimer`                              | `false`     | Write GPU timestamps around the mod's per-frame prepared work and each group of terrain layers, for `terrainRenderer.performance.gpu`. Measures vanilla terrain and the mesh path alike.                                                                                                          |

Press the **Dump Vulkan Improvement Diagnostics** key (default: `K` in the Debug category) to log a compact bug-report JSON blob via `RendererDiagnostics`.

Update this table when adding or removing flags.

`vim.waitIdleBeforeTerrainUpload` is a validation escape hatch only. Normal uploads do not wait for the GPU: changed pages are copied in command order, after every draw recorded before them.

## GPU Validation Matrix

Record manual validation results here before treating the mesh terrain path as generally usable.

| Date       | OS         | GPU         | Driver           | Vulkan API | Minecraft       | Fabric API   | Result                                                        | Notes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
|------------|------------|-------------|------------------|------------|-----------------|--------------|---------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 2026-10-13 | Windows 11 | RTX 4070 Ti | 616.92 / 1.4.351 | 1.4.351    | 26.4-snapshot-3 | 0.162.2+26.4 | port: default, capture, overlay and strict pass in short runs | First runs of the 26.4 port. Default mode reaches a world on Minecraft's multi-draw-indirect path. Capture covered every visible section layer. Strict replacement with `vim.enableGpuGeneratedMeshTaskCommands=true` through record-first camera jitter: `run/debugbridge-recordings/26.4-snapshot-3/strict-gpu-commands-jitter-100.jpg` (100 frames at 100 ms, nine camera turns) shows coherent terrain; `meshReplacementTaskTruncations=0`, `meshReplacementPreparedDispatchQueueLeaks=0`, `meshReplacementRefusals=0`, `meshReplacementDrawCallsByLayer={solid=4082, cutout=4082, translucent=0}`. Also passes without multi-draw-indirect (`strict-draw-separate.jpg`). Present pacing on the Vulkan swapchain: `presentIdsSubmitted=888`, `presentWaitFailures=0`. This is ten seconds, not the long pass the mesh bugfix plan asks for, and texture quality was not compared at full resolution.                                                                                                                                                                                                                                                          |
| 2026-10-13 | Windows 11 | RTX 4070 Ti | 616.92 / 1.4.351 | 1.4.351    | 26.4-snapshot-3 | 0.162.2+26.4 | runtime matrix pass                                           | Record-first jitter, 300 frames at 100 ms with 27 camera turns, in four modes: capture only, overlay, strict with GPU-generated commands, and every diagnostic flag on. Recordings: `run/debugbridge-recordings/26.4-snapshot-3/jitter-300-*.jpg`. `meshReplacementTaskTruncations=0`, `meshReplacementPreparedDispatchQueueLeaks=0`, no translucent-layer draws and no device loss in any mode. Strict mode also survived wireframe on and off, a render-distance change, a resource reload and a trip to the Nether, and the client quit normally.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| 2026-10-13 | Windows 11 | RTX 4070 Ti | 616.92 / 1.4.351 | 1.4.351    | 26.4-snapshot-3 | 0.162.2+26.4 | vanilla parity pass                                           | Same-camera vanilla and strict screenshots with ticking frozen, chat and hotbar excluded; two vanilla screenshots differ by a mean of 0.03 of 255. Strict against vanilla: noon 1.03, render distance 4 (fog band in view) 0.86, midnight 0.83, blindness 0.17, at x = z = 3,000,000 0.42, and with improved transparency off 1.06 and 0.87; at most 0.06 % of pixels differ by more than 60 of 765, all on foliage edges. Before the mesh path culled back faces, sampled the mip chain and lightmap as vanilla does and applied fog, noon was 6.45 with 2.69 %. Images: `run/debugbridge-recordings/26.4-snapshot-3/parity/`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| 2026-10-14 | Windows 11 | RTX 4070 Ti | 616.92 / 1.4.351 | 1.4.351    | 26.4-snapshot-3 | 0.162.2+26.4 | resident pages and GPU visibility pass                        | Strict replacement drawn from GPU-written commands (`source=gpu-visible-pages`). One session: render distance 6 and back, block edits, a teleport to x = z = 3,000,000 and back, record-first jitter of 300 frames, `vim.drawAllCapturedTerrainLayers`, wireframe on and off, a resource reload; the client quit normally. 15,201 commands prepared, `meshReplacementPreparedDispatchQueueLeaks=0`, `meshReplacementTaskTruncations=0`, `terrainCapacityGrowths=0`, `allocationRefusals=0`; 14,398 pages handed out and 5,486 given back, with freed ranges reused. A page upload cost 46 to 134 microseconds of CPU. Same-camera vanilla against strict, chat and hotbar excluded: 1.03 of 255 at noon, after the render-distance change and after the reload; 1.44 at three million blocks; 0.10 looking away. With Minecraft's culling frustum frozen and the camera turned, the GPU dropped 1,136 of 1,156 listed pages and the picture still matched vanilla (0.39); in an ordinary view it drops about 7 %. Over the session it looked at 7,850,551 listed pages and dropped 3,499,361. Images: `run/debugbridge-recordings/26.4-snapshot-3/gpu-resident/`. |
| 2026-10-15 | Windows 11 | RTX 4070 Ti | 616.92 / 1.4.351 | 1.4.351    | 26.4-snapshot-4 | 0.163.0+26.4 | port: default, GPU model path and strict pass                 | Fabric Loader 0.20.0 and Loom 1.19.1; the loader reports the snapshot as `26.4-alpha.4`. Every mixin applied at startup and default mode reaches the world. The test world asked once for a backup, as Minecraft does for a world last saved by another snapshot. GPU model path (`vim.gpuModelVertices`): a dozen mobs at close range, ticks frozen, vignette and chat off; frames with the path on and off are identical. 400 animals and golems in view, render distance 12, three runs of each path in turn, medians: Minecraft 404 FPS, 2.48 ms a frame, 2.28 ms on the render thread; GPU path 749 FPS, 1.34 ms, 1.14 ms. Images: `run/debugbridge-recordings/26.4-snapshot-4/parity/models-*.jpg`.                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| 2026-10-15 | Windows 11 | RTX 4070 Ti | 616.92 / 1.4.351 | 1.4.351    | 26.4-snapshot-4 | 0.163.0+26.4 | vanilla parity pass                                           | Same-camera vanilla and strict screenshots with ticking frozen, render distance 16, chat and hotbar excluded. Strict against vanilla: noon 1.02, from high above 0.07, midnight 0.71; at most 0.05 % of pixels differ by more than 60 of 765. Two vanilla screenshots of each scene differ by 0.58, 0.04 and 0.82: this script leaves the vignette on and water animates. Images: `run/debugbridge-recordings/26.4-snapshot-4/parity/terrain-*.jpg`, vanilla, strict and vanilla again for each scene.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 2026-10-16 | Windows 11 | RTX 4070 Ti | 616.92 / 1.4.351 | 1.4.351    | 26.4-snapshot-4 | 0.163.0+26.4 | framebuffers kept across frames                               | `vim.keepFramebuffers`, in the entity scene on Minecraft's model path (400 mobs, render distance 12, ticks frozen): a frame opens 13 render passes, and with the framebuffers kept none is created after the first frame (209,000 reuses in the run). Resizing the window to 1600x900 and back released and recreated the 12 whose views Minecraft recreated. Frames with the framebuffers kept and not are identical, before and after the resize. Six runs of each in turn, 8 s each: 0.02 ms a frame shorter (about 1 %), inside the run-to-run spread.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| 2026-10-16 | Windows 11 | RTX 4070 Ti | 616.92 / 1.4.351 | 1.4.351    | 26.4-snapshot-4 | 0.163.0+26.4 | barriers by resource                                          | `vim.barriersByResource`, GPU time from Minecraft's own timer query (the GPU utilization debug entry on). A frame records about 7 transfers; holding their barriers leaves 3 (1 for a conflicting transfer, 2 before other commands). Entity scene on Minecraft's model path, five runs of each in turn: 0.676 ms of GPU time a frame either way. The overlook without mobs while every section reloads (`LevelExtractor.allChanged`), four runs of each in turn: 0.58 to 0.53 ms of GPU time and 961 to 987 FPS (medians), better in every pair. Frames with the barriers held and not are identical. No synchronization validation: the Vulkan validation layer is not installed here.                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| 2026-10-16 | Windows 11 | RTX 4070 Ti | 616.92 / 1.4.351 | 1.4.351    | 26.4-snapshot-4 | 0.163.0+26.4 | GPU model mesh shaders                                        | `vim.gpuModelVertices` with `vim.gpuModelMeshShaders`, the entity scene (400 mobs, render distance 12, ticks frozen) after a resource reload with both on. Minecraft built 41 entity pipelines with a mesh pipeline beside each; its 8 item pipelines got none, their vertex shader not being one the mesh stage takes. All 18 GPU model draws of a frame (4,354 cubes) were drawn by the mesh stage, none left out. Frames on Minecraft's path, the vertex pass and the mesh stage are identical below the debug overlay. Four runs of each in turn, medians: Minecraft 437 FPS, 2.29 ms a frame, 0.70 ms of GPU time; vertex pass 650 FPS, 1.54 ms, 0.54 ms; mesh stage 647 FPS, 1.54 ms, 0.52 ms, less GPU time than the vertex pass in every pair. In three of the first five clients of the day's runs the GPU model path took every model submit and wrote no vertices, so no mob was drawn on either path; the ten clients after that drew them all, and the cause is not found. `framesLeftOut` in the model diagnostics counts such frames, and the first is logged.                                                                                     |

Required JVM flags to record per run: `vim.replaceVanillaTerrain`, `vim.enableTerrainCaptureBootstrap`, `vim.enableMeshTranslucentTerrain`, `vim.strictMeshTerrainReplacement`, `vim.drawAllCapturedTerrainLayers`, `vim.disableFragmentShadingRate`, `vim.validationDescriptorBufferOnly`, `vim.allowCpuVisibleMeshletFallback`, `vim.enableGpuGeneratedMeshTaskCommands`.

Required runtime counters to inspect: `meshReplacementDrawCalls`, `meshReplacementWorkQueueDrawCalls`, `meshReplacementTasks`, `meshReplacementTaskTruncations`, `meshReplacementDrawCallsByLayer`, `meshReplacementPreparedGpuCommands`, `meshReplacementPreparedGpuCommandDisabled`, `meshReplacementPreparedDispatchQueueLeaks`, `meshReplacementVisibleMeshletFallbackRefusals`, `meshReplacementPreparedGpuCommandRefusals`.

Required GPU-world counters to inspect when validating the resident terrain
path: `gpuWorldDatabase.residentTerrain.livePages`,
`gpuWorldDatabase.residentTerrain.usedBytes`,
`gpuWorldDatabase.residentTerrain.allocationRefusals`,
`terrainRenderer.sectionStore.residentPages`,
`terrainRenderer.descriptorHeap.terrainCapacityGrowths`,
`gpuWorldDatabase.visibilityCommandStats.culledSectionCount`,
`gpuWorldDatabase.residencyStats.livePageCount`,
`gpuWorldDatabase.residencyStats.overBudgetRefusals`,
`gpuWorldDatabase.visibilityCommandStats.inputSectionCount`,
`gpuWorldDatabase.visibilityCommandStats.visibleSectionCount`,
`gpuWorldDatabase.visibilityCommandStats.emittedCommandCount`,
`gpuWorldDatabase.visibilityCommandStats.overflowedCommandCount`, and
`gpuWorldDatabase.visibilityCommandStats.fallbackCommandCount`.

Required texture-quality diagnostics to inspect when evaluating close terrain:
`videoSettings.quality.textureFiltering`, `videoSettings.quality.mipmapLevels`,
`videoSettings.quality.maxAnisotropyValue`, and
`terrainRenderer.descriptorHeap.textureBindings.Sampler0.maxAnisotropy`,
`terrainRenderer.descriptorHeap.textureBindings.Sampler0.minFilter`,
`terrainRenderer.descriptorHeap.textureBindings.Sampler0.magFilter`, and
`terrainRenderer.descriptorHeap.textureBindings.Sampler0.maxLod`.

## Vanilla Parity Check

Mesh terrain is meant to look exactly like vanilla terrain. To check a change
to the mesh path: freeze ticking (`tick freeze`), pose the camera, take a
screenshot with vanilla terrain and one with strict mesh replacement, and
compare them outside the chat and hotbar area. Two vanilla screenshots differ
by a mean of about 0.03 of 255 per channel; mesh against vanilla is about 1.0,
with single pixels differing on foliage edges. Compare at noon, at midnight, at
a short render distance (fog), with blindness (environmental fog), with
improved transparency on and off, and far from the world origin.

## Performance Check

The mesh path has to be faster than vanilla terrain, not only look like it.
`TerrainPerformanceMonitor` (in the bug report under
`terrainRenderer.performance`) times every frame in a world and, with
`vim.terrainGpuTimer`, the GPU time of the mod's prepared work (`prepareMs`)
and of each group of terrain layers (`opaqueMs`, `translucentMs`). Its
`reset()` starts a new sample. `meanCpuMs` is the time Minecraft's render
thread spent on a frame without waiting for the GPU; when the frame takes
longer than that in one mode and not the other, the GPU sets that mode's pace.

To compare: run the client at 1920x1080 with the frame limit and vsync off and
`inactivityFpsLimit` not `afk` (Minecraft caps an unattended client at 30 FPS
after a minute otherwise), freeze ticking, pose the camera, wait until
Minecraft has no section left to compile, then sample vanilla terrain and strict mesh replacement in turn, a few
seconds each and three times over, in the same session, and compare the
medians. Replacement can be switched on and off without capturing again while
ticking is frozen. Sessions differ from each other by up to ten percent on
this machine, more than most single changes are worth.
Compare `meanFps`, `onePercentLowFps` and the GPU group times. Look at more
than one view: on the ground, high above the terrain, and along the horizon.
A CPU profile (`jcmd <pid> JFR.start`) shows whether the render thread waits
for the GPU (swapchain acquire and submit) or is busy itself.

Any buffer the GPU reads or updates every frame lives in device memory
(`DeviceBuffer.Placement.DEVICE_MAPPED`); host memory is for staging and
read-back. Two atomics per meshlet on a counter in host memory once made the
opaque layers take 2.4 ms instead of 0.4 ms.

## Before The First RT/PT Allocation

No Vulkan acceleration structure exists in the mod; `RtPtAccelerationDataRegistry`
keeps the gate closed. The change that creates the first one has to show, on a
debug-options run with the Vulkan validation layers:

- With the gate closed, no acceleration structure, scratch buffer or build
  command is created, and the gate stays closed on a device missing any
  requirement.
- No validation message names an acceleration structure, a build-input buffer
  or a device address while structures are built and rebuilt.
- Replaced and dropped structures go through the retirement service, and the
  live count settles to what is referenced.
- A block change invalidates exactly the pages whose source revision it
  replaces, and no page stays live on a stale revision.
- Leaving a world, loading another and changing dimension bring the live count
  to zero before anything of the new world is built.
- A lost device clears the registry without destroying through a dead handle;
  closing the renderer destroys every structure before the Vulkan device.
- The RT/PT path and terrain replacement each behave as they do alone when the
  other is on.

## Manual Runtime Checklist

1. Start with `.\gradlew.bat runClient`. On Windows Minecraft 26.4 presents
   through DXGI by default; set `RENDERPEARL_DISABLE_DXGI_PRESENT=1` to
   validate present pacing, which acts on the Vulkan swapchain only.
2. Confirm the client preserves the selected graphics API; OpenGL-active
   sessions should not expose VIM renderer options or terrain capture hooks,
   while active Vulkan sessions keep VIM renderer options visible even after
   the pending restart preference is changed to OpenGL.
3. Confirm Vulkan capability output appears when `vim.dumpCapabilities=true`.
4. Load a world and verify terrain renders in the intended mode.
5. Toggle relevant video options or JVM flags and repeat the render-path check.