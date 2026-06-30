# Mesh Replacement Device-Loss Bugfix

## Goal

Fix or conclusively contain the visible mesh-terrain replacement device-loss
and texture-quality risks on Minecraft 26.2 while keeping capture/bootstrap as
the safe default.

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

## Constraints

- `vim.replaceVanillaTerrain` remains `false` by default until this plan has a
  longer runtime pass.
- VIM terrain capture, mesh replacement, and diagnostics must stay gated off
  when OpenGL is the active backend.
- Runtime validation must call `mc_record_video` first with a long interval and
  then jitter the camera while recording.
- Full-resolution nearest-filter texture captures are required before changing
  mesh UVs, sampler selection, or shader LOD behavior.
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
   - Start from Vulkan capture/bootstrap mode:
     `vim.replaceVanillaTerrain=false`,
     `vim.enableGpuGeneratedMeshTaskCommands=false`,
     `vim.validationCpuVisibleMeshletRing=false`.
   - Use Video Settings or `mc_execute` after confirming option setters with:
     `java.methods(mc.options, 'textureFiltering')` and
     `java.methods(mc.options, 'mipmap')`.
   - Capture frames mode for inspection:
     `mc_record_video(frames=60, interval=100, output="frames", downscale=1, quality=1.0)`.
   - Record `RendererDiagnostics.bugReport()` and verify
     `videoSettings.quality.textureFiltering`, `videoSettings.quality.mipmapLevels`,
     and `terrainRenderer.descriptorHeap.textureBindings.Sampler0.maxAnisotropy`.

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
   - Run once with `vim.replaceVanillaTerrain=false` and once with
     `vim.replaceVanillaTerrain=true`.

4. If device loss reproduces, narrow the failing path before editing renderer
   code.
   - Toggle `vim.enableGpuGeneratedMeshTaskCommands`.
   - Toggle `vim.validationCpuVisibleMeshletRing`.
   - Compare counters:
     `meshReplacementDrawCalls`, `meshReplacementWorkQueueDrawCalls`,
     `meshReplacementTasks`, `meshTaskCommandUploads`,
     `terrainWorkQueueRingWraps`, `terrainUploadFenceDeferrals`,
     `textureDescriptorBufferMissing`, and GPU resource retirement counters.

5. Fix only the isolated failing path.
   - Work-queue/ring fixes belong in `SectionMeshletStore.java`,
     `TerrainGpuLayout.java`, and the matching layout invariant.
   - Descriptor or sampler fixes belong in `DescriptorHeapTerrainResources.java`
     and `MeshShaderTerrainProgram.java`.
   - Render-pass state fixes belong in `VulkanRenderPassMixin.java` and
     `TerrainRenderPassStateInvariantCheck.java`.
   - Resource lifetime fixes belong in `MeshTerrainRenderer.java`,
     `RendererCoreServices.java`, or the resource retirement diagnostics.

6. Keep mesh replacement opt-in until validation passes.
   - Do not change the default value of `vim.replaceVanillaTerrain`.
   - Add a reliability row only after a no-device-loss pass exceeds the previous
     crash window and includes texture-quality evidence.

## Validation

- `.\gradlew.bat checkTerrainRuntimeValidationPlanInvariants --console=plain`
- `.\gradlew.bat check --console=plain`
- `.\gradlew.bat build --console=plain`
- mcdev record-first jitter pass with `vim.replaceVanillaTerrain=false`
- mcdev record-first jitter pass with `vim.replaceVanillaTerrain=true`
- Full-resolution nearest-filter capture with diagnostics proving sampler and
  mipmap state

## Decisions

- Visible mesh replacement is still validation-gated.
- The current safe default is Vulkan capture/bootstrap, not vanilla terrain
  replacement.
- Texture blur in `req_43` is treated as unproven until a full-resolution
  nearest-filter capture rules out expected mipmapping or contact-sheet scaling.

## Follow-Up

- Promote mesh replacement from validation-gated to default only in a later
  change with a clean runtime matrix.
