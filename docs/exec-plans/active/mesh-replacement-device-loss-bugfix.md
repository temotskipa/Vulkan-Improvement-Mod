# Mesh Replacement Device-Loss Bugfix

## Goal

Fix or conclusively contain the visible mesh-terrain replacement device-loss
and texture-quality risks on Minecraft 26.2 while keeping vanilla terrain as
the safe default and capture/bootstrap as an explicit diagnostic opt-in.

## Evidence

- Device loss reproduced on 2026-06-29 with visible mesh replacement enabled:
  `run/crash-reports/crash-2026-06-29_08.25.58-client.txt`.
- Pre-fix record-first jitter showed slab-like terrain corruption:
  `run/debugbridge-recordings/req_17/recording.jpg`.
- Native pipeline restoration plus vanilla camera projection removed that slab
  corruption in the short jitter smoke:
  `run/debugbridge-recordings/req_34/recording.jpg`.
- The daylight sample still has a texture-quality caveat:
  `run/debugbridge-recordings/req_43/recording.jpg` looked blurred near the
  camera while `run/options.txt` recorded `textureFiltering:2` and
  `mipmapLevels:4`.
- Max-feature validation on 2026-07-01 still failed visually:
  `run/debugbridge-recordings/req_33/recording.jpg` showed voids/slabs/blur
  plus transparency and environment screen-effect failures with
  `vim.drawAllCapturedTerrainLayers=true`,
  `vim.enableMeshTranslucentTerrain=true`, GPU-generated commands, CPU
  fallback, FSR, and present pacing enabled. Runtime counters included
  `meshReplacementTasks=987097888`, `meshReplacementTaskTruncations=5431`,
  solid/cutout layer meshlet counts above the direct mesh-task limit, and
  visible translucent replacement draws.
- After the first containment pass, `run/debugbridge-recordings/req_41/recording.jpg`
  still showed large sky/void gaps and black terrain fragments. The counters
  proved segmentation and translucent fallback were active
  (`meshReplacementTaskTruncations=0`,
  `meshReplacementDrawCallsByLayer={solid=7860, cutout=7723, translucent=0}`),
  but `terrainWorkQueueRingWraps=1625` showed the full-layer diagnostic path
  was still churning the shared terrain work queue.
- After direct full-layer dispatch, `run/debugbridge-recordings/req_4/recording.jpg`
  was visually coherent and counters stayed at `meshReplacementTaskTruncations=0`,
  `meshReplacementDrawCallsByLayer={solid=2812, cutout=2721, translucent=0}`,
  `terrainWorkQueueUploads=0`, and `terrainWorkQueueRingWraps=0`. Same-camera
  nearest/no-mipmap screenshots still proved a VIM texture-quality bug:
  vanilla/capture-bootstrap
  `C:\Users\ttski\AppData\Local\Temp\debugbridge-screenshot-7625853700353120804.jpg`
  was sharp, while VIM mesh replacement
  `C:\Users\ttski\AppData\Local\Temp\debugbridge-screenshot-9545749426833000992.jpg`
  had soft nearby terrain and over-expanded cutout vegetation.
- After the 32-lane mesh shader and 128/32 meshlet granularity changes,
  `run/debugbridge-recordings/req_63/recording.jpg` still showed large nearby
  terrain holes when strict visible replacement canceled vanilla terrain.
  Counters ruled out the older overflow causes:
  `meshReplacementTaskTruncations=0`,
  `meshReplacementDrawCallsByLayer={solid=38963, cutout=38275, translucent=0}`,
  and `meshReplacementPreparedDispatchQueueLeaks=0`. Same-camera vanilla/capture
  screenshot
  `C:\Users\ttski\AppData\Local\Temp\debugbridge-screenshot-13566611726126501905.jpg`
  was intact, so visible mesh cancellation remains unsafe.
- Patched non-strict record-first jitter at
  `run/debugbridge-recordings/req_72/recording.jpg` kept vanilla terrain visible
  through the same camera-jitter class. Runtime mode was
  `mesh-overlay-vanilla-fallback` with `strictMeshTerrainReplacement=false`.
  Counters showed `meshReplacementTaskTruncations=0`,
  `meshReplacementDrawCallsByLayer={solid=16290, cutout=15672, translucent=0}`,
  `meshReplacementPreparedDispatchQueueLeaks=0`, `terrainWorkQueueUploads=31962`,
  and `terrainWorkQueueRingWraps=501`. This is containment only because overlay
  artifacts and per-frame work-queue cost remain.

## Constraints

- `vim.replaceVanillaTerrain` remains `false` by default until this plan has a
  longer runtime pass.
- `vim.enableTerrainCaptureBootstrap` remains `false` by default until capture
  cost is proven acceptable outside diagnostic runs.
- `vim.strictMeshTerrainReplacement` remains `false` by default until visible
  replacement coverage is proven; non-strict mesh draws are diagnostic overlays
  that restore vanilla state and leave vanilla terrain visible.
- VIM terrain capture, mesh replacement, and diagnostics must stay gated off
  when OpenGL is the active backend.
- Runtime validation must call `mc_record_video` first with a long interval and
  then jitter the camera while recording.
- Full-resolution nearest-filter texture captures are required before changing
  mesh UVs, sampler selection, or shader LOD behavior.
- Max-feature validation must enable the diagnostic VIM flags, but all-on
  validation must not visibly replace uncontracted translucent terrain,
  underwater overlays, or environment screen effects. Those stay on vanilla
  rendering until sorting, blending, fog, and effect ordering are contracted.
- Use mcdev static inspection before changing mixin targets or Minecraft method
  descriptors.

## Steps

1. Add a failing invariant for the exact runtime evidence expected from this
   bugfix plan.
   - Test file:
     `src/test/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/TerrainRuntimeValidationPlanInvariantCheck.java`.
   - Require the active plan text to mention `req_17`, `req_34`, `req_43`,
     `crash-2026-06-29_08.25.58-client.txt`, `mc_record_video`, and
     `vim.replaceVanillaTerrain`.
   - Wire it into `build.gradle` and run:
     `.\gradlew.bat checkTerrainRuntimeValidationPlanInvariants --console=plain`.

2. Capture a new full-resolution texture baseline with mesh replacement off.
   - Start from explicit Vulkan capture/bootstrap diagnostic mode:
     `vim.replaceVanillaTerrain=false`,
     `vim.enableTerrainCaptureBootstrap=true`,
     `vim.enableGpuGeneratedMeshTaskCommands=false`,
     `vim.validationCpuVisibleMeshletRing=false`.
   - Use Video Settings or `mc_execute` after confirming option setters with:
     `java.methods(mc.options, 'textureFiltering')` and
     `java.methods(mc.options, 'mipmap')`.
   - Capture frames mode for inspection:
     `mc_record_video(frames=60, interval=100, output="frames", downscale=1, quality=1.0)`.
   - Record `RendererDiagnostics.bugReport()` and verify
     `videoSettings.quality.textureFiltering`, `videoSettings.quality.mipmapLevels`,
     `terrainRenderer.descriptorHeap.textureBindings.Sampler0.maxAnisotropy`,
     `terrainRenderer.descriptorHeap.textureBindings.Sampler0.minFilter`,
     `terrainRenderer.descriptorHeap.textureBindings.Sampler0.magFilter`, and
     `terrainRenderer.descriptorHeap.textureBindings.Sampler0.maxLod`.

3. Reproduce the mesh replacement stress case with record-first jitter.
   - Before starting the recording, schedule jitter with `mc_execute`:

     ```groovy
     def Thread = java.type('java.lang.Thread')
     Thread.startVirtualThread({
         Thread.sleep(1000)
         sync {
             player.setYRot(player.getYRot() + 170.0f)
             player.setXRot(Math.max(-89.0f, Math.min(89.0f, player.getXRot() - 75.0f)))
         }
     } as Runnable)
     return 'large camera jitter scheduled'
     ```

   - Immediately call:
     `mc_record_video(frames=300, interval=100, output="grid", downscale=1, quality=1.0)`.
   - Run once with `vim.replaceVanillaTerrain=false` and
     `vim.enableTerrainCaptureBootstrap=true`, then once with
     `vim.replaceVanillaTerrain=true`.

4. If device loss reproduces, narrow the failing path before editing renderer
   code.
   - Toggle `vim.enableGpuGeneratedMeshTaskCommands`.
   - Toggle `vim.validationCpuVisibleMeshletRing`.
   - Toggle `vim.drawAllCapturedTerrainLayers` to separate visible draw-list
     replacement from full-layer diagnostic replacement.
   - Compare counters:
     `meshReplacementDrawCalls`, `meshReplacementWorkQueueDrawCalls`,
     `meshReplacementTasks`, `meshTaskCommandUploads`,
     `terrainWorkQueueRingWraps`, `terrainUploadFenceDeferrals`,
     `textureDescriptorBufferMissing`, `meshReplacementTaskTruncations`,
     `meshReplacementDrawCallsByLayer`, and GPU resource retirement counters.

5. Fix only the isolated failing path.
   - Work-queue/ring fixes belong in `SectionMeshletStore.java`,
     `TerrainGpuLayout.java`, and the matching layout invariant.
   - Full-layer diagnostic replacement must split any layer above
     `TerrainMeshTaskDispatch.MAX_DIRECT_TASKS` instead of truncating the
     task count.
   - Full-layer diagnostic replacement must use direct segmented mesh-task
     dispatches instead of terrain work-queue records; contiguous layer
     meshlets already encode the required meshlet IDs, and work-queue churn can
     corrupt later GPU reads under max-feature `drawAll` stress.
    - Prepared visible mesh-task commands must be consumed by the visible
      replacement path rather than enqueued and leaked.
    - Visible mesh draws must only cancel vanilla when
      `vim.strictMeshTerrainReplacement=true`; non-strict visible draws are
      overlay/fallback diagnostics because `req_63` proved a successful mesh
      dispatch is not sufficient coverage evidence.
    - The terrain fragment shader must fetch the block atlas with `texelFetch`
     so the replacement path cannot soften nearest-filter terrain via sampler
     filtering or derivative-selected LOD.
   - Alpha-masked cutout materials must use a strict cutout threshold and render
     opaque; visible translucent terrain remains on vanilla.
   - Visible translucent replacement must fall back to vanilla until the
     translucent/effect ordering contract is implemented.
   - Descriptor or sampler fixes belong in `DescriptorHeapTerrainResources.java`
     and `MeshShaderTerrainProgram.java`.
   - Render-pass state fixes belong in `VulkanRenderPassMixin.java` and
     `TerrainRenderPassStateInvariantCheck.java`.
   - Resource lifetime fixes belong in `MeshTerrainRenderer.java`,
     `RendererCoreServices.java`, or the resource retirement diagnostics.

6. Keep mesh replacement opt-in until validation passes.
    - Do not change the default value of `vim.replaceVanillaTerrain`.
    - Do not change the default value of `vim.enableTerrainCaptureBootstrap`.
    - Do not change the default value of `vim.strictMeshTerrainReplacement`.
    - Add a reliability row only after a no-device-loss pass exceeds the previous
      crash window and includes texture-quality evidence.

## Validation

- `.\gradlew.bat checkTerrainRuntimeValidationPlanInvariants --console=plain`
- `.\gradlew.bat check --console=plain`
- `.\gradlew.bat build --console=plain`
- mcdev record-first jitter pass with `vim.replaceVanillaTerrain=false` and
  `vim.enableTerrainCaptureBootstrap=true`
- mcdev record-first jitter pass with `vim.replaceVanillaTerrain=true`
- mcdev record-first jitter pass with `vim.replaceVanillaTerrain=true` and
  `vim.strictMeshTerrainReplacement=false`, proving vanilla terrain remains
  visible while VIM mesh diagnostics draw
- mcdev max-feature record-first jitter pass with
  `vim.replaceVanillaTerrain=true`, `vim.enableTerrainCaptureBootstrap=true`,
  `vim.enableMeshTranslucentTerrain=true`,
  `vim.strictMeshTerrainReplacement=false`,
  `vim.drawAllCapturedTerrainLayers=true`,
  `vim.enableGpuGeneratedMeshTaskCommands=true`, and
  `vim.allowCpuVisibleMeshletFallback=true`
- Runtime counters proving `meshReplacementTaskTruncations=0` after full-layer
  segmentation, no visible replacement draw calls for the translucent layer,
  and no terrain work-queue growth from full-layer `drawAll` dispatches
- Full-resolution nearest-filter capture with diagnostics proving sampler and
  mipmap state

## Decisions

- Visible mesh replacement is still validation-gated.
- The current safe default is vanilla terrain. Vulkan capture/bootstrap and
  mesh replacement are both explicit opt-in paths.
- Strict visible cancellation is a validation-only failure reproducer after
  `req_63`; the fallback/overlay path is the safe mesh diagnostic mode until
  visible coverage is proven.
- Texture blur in `req_43` is treated as unproven until a full-resolution
  nearest-filter capture rules out expected mipmapping or contact-sheet scaling.
- Enabling every VIM diagnostic flag for validation does not mean every
  renderer domain is replaceable. Translucent terrain, underwater overlays, and
  environment screen effects remain vanilla-rendered until their compatibility
  contract exists.

## Follow-Up

- Promote mesh replacement from validation-gated to default only in a later
  change with a clean runtime matrix.
