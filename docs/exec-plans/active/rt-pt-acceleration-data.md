# RT PT Acceleration Data

## Goal

Define the renderer-owned acceleration-data contracts needed before RT/PT
experiments can consume VIM terrain or future non-terrain GPU world data.

## Constraints

- RT/PT readiness is optional and must not become a startup requirement.
- GPU world pages derived from Minecraft chunk state are render data, not
  gameplay-authoritative world state.
- Acceleration data must not share authority labels with optional C2ME-OCL
  world-generation output.
- Device support must come from `VulkanRuntimeProfile` selected and disabled
  states, not ad hoc extension checks.
- No RT/PT draw path starts until diagnostics prove the acceleration-data
  lifecycle is independent from terrain mesh replacement stability.

## Steps

1. Add a pure invariant for RT/PT readiness remaining optional.
   - Extend `VulkanRuntimeProfileInvariantCheck.java`.
   - Assert `rt-pt-readiness` is not startup-required.
   - Assert missing RT/PT extensions produce disabled readiness reasons rather
     than backend creation failure reasons.
   - Status: done through `RtPtAccelerationDataInvariantCheck`, which verifies
     RT/PT readiness stays outside `hardRequirements` and reports `not-ready`
     with a disabled reason when support is absent.

2. Define acceleration-data ownership types.
   - Create `RtPtAccelerationDomain.java` with values `TERRAIN`,
     `BLOCK_ENTITIES`, `ENTITIES`, and `RENDER_ONLY_LOD`.
   - Create `RtPtAccelerationPage.java` with fields:
     `RtPtAccelerationDomain domain`, `GpuWorldSectionId sectionId`,
     `GpuWorldRevision sourceRevision`, `GpuWorldPageKind sourcePageKind`,
     `long blasHandle`, `long tlasInstanceHandle`, and `String fallbackReason`.
   - Keep Vulkan handles as opaque longs until a concrete Vulkan wrapper owns
     destruction.
   - Status: done for diagnostics-only contracts. `RtPtAccelerationPage` keeps
     BLAS/TLAS identifiers as opaque longs and records source GPU-world
     revision and page kind without claiming gameplay authority.

3. Add lifecycle diagnostics before allocation.
   - Create `RtPtAccelerationDataRegistry.java`.
   - Track registered page count, retired page count, pending rebuild count,
     device-lost clear count, and fallback reason counts.
   - Expose `asMap()` and include it in `RendererDiagnostics.bugReport()`.
   - Status: done. `RtPtAccelerationDataRegistry` exposes allocation disabled
     state, live/registered/retired/pending/device-lost counters, and fallback
     reason counts in bug reports.

4. Connect acceleration pages to GPU world revisions.
   - Add a failing invariant in `GpuWorldDatabaseInvariantCheck.java` proving
     canonical section updates can invalidate acceleration pages by
     `GpuWorldSectionId` and `GpuWorldRevision`.
   - Implement only invalidation bookkeeping first; do not build Vulkan AS
     objects in the same change.
   - Status: done for bookkeeping. `RtPtAccelerationDataRegistry` now accepts
     `GpuWorldSectionUpdate` source invalidations, removes stale live pages for
     the same section and page kind when their source revision no longer
     matches, counts affected pages as pending rebuilds, and exposes the last
     source invalidation in diagnostics. No Vulkan acceleration-structure
     allocation is introduced.

5. Add capability gates for future allocation.
   - Use `VulkanRuntimeProfile` to require acceleration-structure,
     ray-tracing-pipeline, buffer-device-address, and descriptor support before
     any allocation task can be scheduled.
   - Diagnostics must report the selected path as `disabled` with a reason when
     any requirement is absent.

6. Create a runtime validation plan before first Vulkan AS allocation.
   - Include validation layers, resource retirement counters, device-lost
     cleanup, world reload, dimension change, and renderer shutdown.
   - Keep this as a separate active plan if allocation work starts after the
     26.2 renderer extension closes.

## Validation

- `.\gradlew.bat checkRtPtAccelerationDataInvariants --console=plain`
- `.\gradlew.bat checkRuntimeProfileInvariants --console=plain`
- `.\gradlew.bat checkGpuWorldDatabaseInvariants --console=plain`
- `.\gradlew.bat checkRendererCoreServiceInvariants --console=plain`
- `.\gradlew.bat check --console=plain`
- Runtime diagnostics proving RT/PT readiness can be absent without blocking
  Vulkan capture/bootstrap

## Decisions

- RT/PT readiness remains a diagnostic and future-path capability, not a hard
  renderer startup dependency.
- Acceleration data is tied to GPU world revisions, not directly to mutable
  Minecraft chunk objects.

## Follow-Up

- Open a separate allocation plan when Vulkan acceleration-structure objects
  are introduced.
