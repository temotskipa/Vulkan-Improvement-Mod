# RT PT Acceleration Data

**Status:** Completed 2026-10-13.

## Goal

Define the renderer-owned acceleration-data contracts needed before RT/PT
experiments can consume VIM terrain or future non-terrain GPU world data.

## Constraints

- RT/PT readiness is optional and must not become a startup requirement.
- GPU world pages derived from Minecraft chunk state are render data, not
  gameplay-authoritative world state.
- Acceleration data must not share authority labels with optional
  accelerated-worldgen provider output.
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
   - Status: done. `VulkanRuntimeProfile` reports
     `selectedPaths.rtPtAccelerationAllocation=disabled` with a reason that
     either names what the device lacks or says that the device has everything
     and no allocation plan is active. `RtPtAccelerationDataRegistry` exposes
     the same gate as `allocationGate` and cannot schedule an allocation while
     it is closed.

6. Create a runtime validation plan before first Vulkan AS allocation.
   - Include validation layers, resource retirement counters, device-lost
     cleanup, world reload, dimension change, and renderer shutdown.
   - Keep this as a separate active plan if allocation work starts after the
     26.2 renderer extension closes.
   - Status: done as the section Validation Before The First Allocation below.
     The plan that introduces allocation starts from it.

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

## Validation Before The First Allocation

No Vulkan acceleration structure exists in the mod. The change that creates the
first one has to show all of the following, on a debug-options run with the
Vulkan validation layers enabled, before it can be merged:

- **Gate.** With the gate closed, no acceleration structure, scratch buffer or
  build command is created. `allocationGate.canScheduleAllocation` is the only
  switch, and it stays `false` on a device that lacks any requirement.
- **Validation layers.** A session that builds and rebuilds acceleration
  structures finishes with no validation message naming an acceleration
  structure, a build-input buffer or a device address.
- **Retirement.** Every structure that is replaced or dropped goes through the
  retirement service; after a quiet period the registry's live count equals
  what is referenced, and retired counts stop growing.
- **Invalidation.** A block change in a section invalidates exactly the pages
  whose source revision it replaces. The pending-rebuild count returns to zero
  and no page stays live on a stale revision.
- **World reload and dimension change.** Leaving a world, loading another and
  changing dimension each bring the live count to zero before anything of the
  new world is built.
- **Device loss.** A lost device clears the registry without destroying
  through a dead handle, and counts the clear.
- **Shutdown.** Closing the renderer destroys every structure before the
  Vulkan device, and the client exits normally.
- **Independence.** With the gate open and terrain replacement off, and the
  other way round, each path behaves as it does alone.

## Completion

Closed on 2026-10-13. The contracts, lifecycle diagnostics, revision
bookkeeping and the capability gate exist; allocation does not, by decision.

Runtime, `26.4-snapshot-3`, RTX 4070 Ti: every RT/PT readiness extension was
reported present, and
`allocationGate={deviceSupported=true, allocationEnabled=false,
canScheduleAllocation=false, selectedPath=disabled}` with the reason
`device supports it; no acceleration-structure allocation plan is active`.
RT/PT readiness is not among the hard requirements, so a device without it
still starts.
